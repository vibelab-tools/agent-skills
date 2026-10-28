#!/usr/bin/env python3
"""Retry idle Codex capacity errors in explicitly registered tmux panes."""

from dataclasses import dataclass
import fcntl
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import time


OPTION = "@codex-capacity-retry"
ERROR = re.compile(
    r"^\s*■\s+(?:Error\s+running\s+remote\s+compact\s+task:\s+)?"
    r"Selected\s+model\s+is\s+at\s+capacity\.\s+"
    r"Please\s+try\s+a\s+different\s+model\.", re.MULTILINE,
)
COMPLETED = re.compile(r"• Completed `/root/[^`\n]+`")
BACKGROUND = re.compile(r"\d+ background terminals? running · /ps to view · /stop to close")
BUSY = re.compile(r"\b(?:working|thinking|reconnecting|compacting)\b|"
                  r"\besc\s+(?:to\s+)?interrupt\b", re.IGNORECASE)


def tmux(socket, *arguments):
    return subprocess.run(["tmux", "-S", socket, *arguments], check=True,
                          text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, timeout=5).stdout


def eligible(screen, cursor_x, cursor_y, in_mode=False, dead=False,
             current_command="node", enabled=True):
    """Return an error signature only for a native error and empty idle composer."""
    if not enabled or in_mode or dead or current_command not in {"node", "codex"}:
        return None
    lines = screen.splitlines()
    if not 0 <= cursor_y < len(lines):
        return None
    composer = re.fullmatch(r"( *)› ?(.*)", lines[cursor_y])
    if not composer or cursor_x != len(composer[1]) + 2:
        return None
    if composer[2].strip() not in {"", "Ask Codex to do anything"}:
        return None
    if BUSY.search("\n".join(lines[cursor_y + 1:])):
        return None
    preceding = "\n".join(lines[:cursor_y])
    matches = list(ERROR.finditer(preceding))
    if not matches:
        return None
    following = preceding[matches[-1].end():].splitlines()
    if any(line.strip() and not (COMPLETED.fullmatch(line.strip())
                                or BACKGROUND.fullmatch(line.strip())) for line in following):
        return None
    return hashlib.sha256(preceding.encode()).hexdigest()


@dataclass
class RetryState:
    signature: str | None = None
    attempts: int = 0
    sent: bool = False

    def observe(self, signature):
        if signature is None:
            self.signature = None
            self.sent = False
            return False
        if signature != self.signature:
            self.signature = signature
            self.sent = False
        return not self.sent

    def mark_sent(self):
        self.attempts += 1
        self.sent = True


def capture(socket, pane):
    fields = "\t".join(["#{cursor_x}", "#{cursor_y}", "#{pane_in_mode}",
                        "#{pane_dead}", "#{pane_current_command}", "#{" + OPTION + "}"])
    output = tmux(socket, "display-message", "-p", "-t", pane, fields,
                  ";", "capture-pane", "-p", "-t", pane)
    header, _, screen = output.partition("\n")
    x, y, in_mode, dead, command, enabled = header.split("\t")
    return screen, int(x), int(y), in_mode == "1", dead == "1", command, enabled == "on"


def send_continue(socket, pane):
    # One tmux command queue keeps the literal text and Enter together.
    tmux(socket, "send-keys", "-t", pane, "-l", "请继续",
         ";", "send-keys", "-t", pane, "Enter")


def poll_pane(socket, pane, state):
    signature = eligible(*capture(socket, pane))
    if not state.observe(signature):
        return False
    confirmed = eligible(*capture(socket, pane))
    if confirmed != signature:
        state.observe(confirmed)
        return False
    send_continue(socket, pane)
    state.mark_sent()
    return True


def watch(socket):
    directory = Path.home() / ".codex" / "run"
    directory.mkdir(parents=True, exist_ok=True)
    key = hashlib.sha256(socket.encode()).hexdigest()[:16]
    with (directory / ("tmux-capacity-retry-" + key + ".lock")).open("a+") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            return
        lock.seek(0)
        lock.truncate()
        lock.write(str(os.getpid()) + "\n")
        lock.flush()
        states = {}
        while True:
            try:
                output = tmux(socket, "list-panes", "-a", "-F",
                              "#{pane_id}\t#{" + OPTION + "}")
            except (subprocess.SubprocessError, OSError):
                return
            panes = [line.split("\t")[0] for line in output.splitlines()
                     if line.endswith("\ton")]
            if not panes:
                return
            states = {pane: states.get(pane, RetryState()) for pane in panes}
            for pane, state in states.items():
                try:
                    if poll_pane(socket, pane, state):
                        print(time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                              "pane=" + pane, "capacity_retry=" + str(state.attempts),
                              flush=True)
                except (subprocess.SubprocessError, OSError, ValueError):
                    # A pane may close between listing and capture.
                    state.observe(None)
            time.sleep(1)


def register():
    context, pane = os.environ.get("TMUX"), os.environ.get("TMUX_PANE")
    if not context or not pane or not re.fullmatch(r"%\d+", pane):
        return
    socket = context.rsplit(",", 2)[0]
    tmux(socket, "set-option", "-p", "-t", pane, OPTION, "on")
    directory = Path.home() / ".codex" / "log"
    directory.mkdir(parents=True, exist_ok=True)
    fd = os.open(directory / "tmux-capacity-retry.log",
                 os.O_WRONLY | os.O_APPEND | os.O_CREAT, 0o600)
    with os.fdopen(fd, "a") as log:
        subprocess.Popen([sys.executable, str(Path(__file__).resolve()), "watch", socket],
                         stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.DEVNULL,
                         start_new_session=True, close_fds=True)


if __name__ == "__main__":
    if sys.argv[1:] == ["register"]:
        register()
    elif len(sys.argv) == 3 and sys.argv[1] == "watch":
        watch(sys.argv[2])
    else:
        raise SystemExit("Usage: codex-capacity-retry.py register | watch SOCKET")

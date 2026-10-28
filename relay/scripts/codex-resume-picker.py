#!/usr/bin/env python3
"""Select local Codex sessions across providers without changing stored history."""

import curses
from dataclasses import dataclass, field
import json
import locale
import os
from pathlib import Path
import queue
import re
import subprocess
import sys
import threading
import time
import tomllib
import unicodedata


SOURCES = ["cli", "vscode", "exec", "appServer", "subAgent", "subAgentReview",
           "subAgentCompact", "subAgentThreadSpawn", "subAgentOther", "unknown"]
VALUE_OPTIONS = {
    "-c", "--config", "-m", "--model", "-p", "--profile", "-s", "--sandbox",
    "-a", "--ask-for-approval", "-C", "--cd", "--add-dir", "-i", "--image",
    "--enable", "--disable",
}
BOOLEAN_OPTIONS = {
    "--strict-config", "--approve-for-me", "--dangerously-bypass-approvals-and-sandbox",
    "--dangerously-bypass-hook-trust", "--search", "--no-alt-screen", "--no-daemon",
}
PICKER_OPTIONS = {"--last", "--all", "--include-non-interactive"}


@dataclass
class Selection:
    arguments: list[str] = field(default_factory=list)
    overrides: list[str] = field(default_factory=list)
    cwd: str = field(default_factory=os.getcwd)
    profile: str | None = None
    model: str | None = None
    last: bool = False
    all_directories: bool = False
    non_interactive: bool = False
    no_daemon: bool = False


def parse_resume(arguments):
    """Leave explicit sessions, remote invocations, and ambiguous arguments native."""
    result = Selection()
    command_seen = False
    index = 0
    while index < len(arguments):
        arg = arguments[index]
        if arg in PICKER_OPTIONS:
            if not command_seen:
                return None
            result.last |= arg == "--last"
            result.all_directories |= arg == "--all"
            result.non_interactive |= arg == "--include-non-interactive"
            index += 1
            continue
        if arg == "resume" and not command_seen:
            command_seen = True
            result.arguments.append(arg)
            index += 1
            continue
        option, separator, value = arg.partition("=")
        if option in VALUE_OPTIONS:
            if not separator:
                if index + 1 >= len(arguments):
                    return None
                value = arguments[index + 1]
                result.arguments.extend([arg, value])
                index += 2
            else:
                result.arguments.append(arg)
                index += 1
        elif len(arg) > 2 and arg[:2] in {"-c", "-m", "-p", "-C"} and not arg.startswith("--"):
            option, value = arg[:2], arg[2:].removeprefix("=")
            result.arguments.append(arg)
            index += 1
        elif arg in BOOLEAN_OPTIONS:
            result.arguments.append(arg)
            result.no_daemon |= arg == "--no-daemon"
            index += 1
            continue
        else:
            return None
        if option in {"-C", "--cd"}:
            result.cwd = os.path.abspath(os.path.expanduser(value))
        elif option in {"-p", "--profile"}:
            if not re.fullmatch(r"[A-Za-z0-9_-]+", value):
                return None
            result.profile = value
        elif option in {"-m", "--model"}:
            result.model = value
        elif option in {"-c", "--config"}:
            result.overrides.append(value)
    return result if command_seen else None


class AppServer:
    def __init__(self, codex_bin, selection):
        arguments = [codex_bin]
        for override in selection.overrides:
            key = override.partition("=")[0].strip()
            if key not in {"model", "model_provider"} and not key.startswith("model_providers."):
                arguments.extend(["-c", override])
        arguments.extend(["-c", "features.apps=false", "-c", "features.plugins=false", "app-server"])
        self.process = subprocess.Popen(arguments, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                        stderr=subprocess.DEVNULL, text=True, bufsize=1,
                                        cwd=selection.cwd)
        self.messages = queue.Queue()
        self.request_id = 0

        def read():
            for line in self.process.stdout:
                try:
                    self.messages.put(json.loads(line))
                except ValueError:
                    continue
            self.messages.put(None)

        threading.Thread(target=read, daemon=True).start()

    def send(self, message):
        self.process.stdin.write(json.dumps(message) + "\n")
        self.process.stdin.flush()

    def request(self, method, params):
        self.request_id += 1
        rid = self.request_id
        self.send({"id": rid, "method": method, "params": params})
        deadline = time.monotonic() + 20
        while True:
            message = self.messages.get(timeout=max(.01, deadline - time.monotonic()))
            if message is None:
                raise RuntimeError("Codex app-server exited while reading local sessions")
            if message.get("id") == rid:
                if "error" in message:
                    detail = message["error"].get("message") or "Codex could not read local session metadata"
                    for key, value in os.environ.items():
                        if value and any(part in key.upper() for part in ("KEY", "TOKEN", "SECRET", "PASSWORD")):
                            detail = detail.replace(value, "[redacted]")
                    raise RuntimeError(detail)
                return message["result"]
            if "id" in message and "method" in message:
                self.send({"id": message["id"], "error": {
                    "code": -32601, "message": "Session picker does not handle server requests",
                }})

    def close(self):
        self.process.terminate()
        try:
            self.process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()


def intended_model(selection, effective):
    config = effective["config"]
    model = config.get("model")
    provider = config.get("model_provider") or "openai"
    if selection.profile:
        codex_home = Path(os.environ.get("CODEX_HOME", "~/.codex")).expanduser()
        profile = tomllib.loads((codex_home / (selection.profile + ".config.toml")).read_text())
        origin = effective.get("origins", {}).get("model", {}).get("name", {}).get("type")
        if origin != "project":
            model = profile.get("model", model)
        provider = profile.get("model_provider") or provider
    for override in selection.overrides:
        key, separator, value = override.partition("=")
        key = key.strip()
        if not separator or key not in {"model", "model_provider"}:
            continue
        try:
            value = tomllib.loads("value=" + value)["value"]
        except tomllib.TOMLDecodeError:
            value = value.strip()
        if not isinstance(value, str) or not value:
            raise RuntimeError("Cannot determine the requested model and provider")
        if key == "model":
            model = value
        else:
            provider = value
    model = selection.model or model
    if not model:
        raise RuntimeError("Set --model or a default model before using the shared session picker")
    return provider, model


def list_sessions(server, selection):
    parameters = {"modelProviders": [], "useStateDbOnly": True, "limit": 100,
                  "sortKey": "updated_at", "sortDirection": "desc",
                  "sourceKinds": SOURCES if selection.non_interactive else ["cli", "vscode"]}
    if not selection.all_directories:
        parameters["cwd"] = selection.cwd
    threads = []
    while True:
        response = server.request("thread/list", parameters)
        threads.extend(response["data"])
        cursor = response.get("nextCursor")
        if not cursor:
            return threads
        if cursor == parameters.get("cursor"):
            raise RuntimeError("Codex returned a repeated session-list cursor")
        parameters["cursor"] = cursor


def clipped(text, width):
    result = []
    used = 0
    for char in str(text):
        if not char.isprintable():
            char = " "
        size = 2 if unicodedata.east_asian_width(char) in {"W", "F"} else 1
        if used + size > width:
            break
        result.append(char)
        used += size
    return "".join(result)


def choose(screen, threads, selection, provider, model):
    screen.keypad(True)
    try:
        curses.curs_set(0)
    except curses.error:
        pass
    query = ""
    selected = 0
    while True:
        rows = [thread for thread in threads if query.casefold() in " ".join(str(thread.get(key) or "")
                for key in ("name", "title", "preview", "id", "modelProvider", "cwd")).casefold()]
        selected = min(selected, max(0, len(rows) - 1))
        height, width = screen.getmaxyx()
        screen.erase()

        def line(number, text, attribute=0):
            if 0 <= number < height:
                try:
                    screen.addstr(number, 0, clipped(text, max(0, width - 1)), attribute)
                except curses.error:
                    pass

        line(0, f"Resume with {provider} / {model}   ({len(rows)} sessions)", curses.A_BOLD)
        line(1, "Search: " + query)
        visible = max(1, height - 5)
        start = max(0, selected - visible + 1)
        for offset, thread in enumerate(rows[start:start + visible]):
            updated = time.strftime("%m-%d %H:%M", time.localtime(thread.get("updatedAt") or 0))
            title = thread.get("name") or thread.get("title") or thread.get("preview") or "(untitled)"
            text = f"{updated} [{thread.get('modelProvider', '?')}] {thread['id'][:8]} {title}"
            if selection.all_directories:
                text += "  " + thread.get("cwd", "")
            line(offset + 3, text, curses.A_REVERSE if start + offset == selected else 0)
        if rows:
            line(height - 2, "Session: " + rows[selected]["id"])
        line(height - 1, "Up/Down select | type to search | Enter resume | Esc/Ctrl-C cancel")
        screen.refresh()
        key = screen.get_wch()
        if key in ("\x1b", "\x03"):
            return None
        if key in (curses.KEY_ENTER, "\n", "\r") and rows:
            return rows[selected]["id"]
        if key == curses.KEY_UP:
            selected = max(0, selected - 1)
        elif key == curses.KEY_DOWN:
            selected = min(max(0, len(rows) - 1), selected + 1)
        elif key in (curses.KEY_BACKSPACE, "\b", "\x7f"):
            query = query[:-1]
            selected = 0
        elif isinstance(key, str) and key.isprintable():
            query += key
            selected = 0


def main(arguments):
    if len(arguments) < 2 or arguments[0] != "--codex-bin" or not os.path.isabs(arguments[1]):
        raise SystemExit("Usage: codex-resume-picker.py --codex-bin /path/to/codex -- [codex arguments]")
    codex_bin = arguments[1]
    native = arguments[2:]
    if native[:1] == ["--"]:
        native = native[1:]
    if sys.stdin.isatty() and sys.stdout.isatty() and os.environ.get("TMUX_PANE"):
        try:
            subprocess.run([sys.executable, str(Path(__file__).with_name("codex-capacity-retry.py")),
                            "register"], stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                           stderr=subprocess.DEVNULL, timeout=2, check=False)
        except (OSError, subprocess.TimeoutExpired):
            pass
    selection = parse_resume(native)
    if selection is None or not sys.stdin.isatty() or not sys.stdout.isatty():
        os.execv(codex_bin, [codex_bin, *native])
    server = AppServer(codex_bin, selection)
    try:
        server.request("initialize", {"clientInfo": {"name": "local_resume_picker", "version": "1"}})
        server.send({"method": "initialized"})
        effective = server.request("config/read", {"cwd": selection.cwd, "includeLayers": False})
        provider, model = intended_model(selection, effective)
        threads = list_sessions(server, selection)
    finally:
        server.close()
    if not threads:
        print("No saved sessions found for this directory." if not selection.all_directories else "No saved sessions found.")
        return
    locale.setlocale(locale.LC_ALL, "")
    session_id = threads[0]["id"] if selection.last else curses.wrapper(choose, threads, selection, provider, model)
    if session_id:
        launch = [*selection.arguments]
        if not selection.no_daemon:
            launch.append("--no-daemon")
        launch.extend(["-c", "model_provider=" + json.dumps(provider)])
        if selection.model is None:
            launch.extend(["--model", model])
        os.execv(codex_bin, [codex_bin, *launch, session_id])


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except KeyboardInterrupt:
        pass
    except (OSError, RuntimeError, queue.Empty, tomllib.TOMLDecodeError) as error:
        message = str(error) if not isinstance(error, queue.Empty) else "Timed out reading local Codex sessions"
        print("Codex session picker: " + message, file=sys.stderr)
        raise SystemExit(1)

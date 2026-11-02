#!/usr/bin/env python3
"""Read-only tmux view of one Codex thread and its descendants."""

import argparse
import curses
from datetime import datetime
import json
import os
from pathlib import Path
import re
import shlex
import sqlite3
import subprocess
import sys
import time
import unicodedata


def clean(value):
    text = str(value)
    for key, secret in os.environ.items():
        if len(secret) >= 8 and any(word in key.upper() for word in ("KEY", "TOKEN", "SECRET", "PASSWORD")):
            text = text.replace(secret, "[redacted]")
    text = re.sub(r"(?i)(bearer\s+)[\w.\-]+", r"\1[redacted]", text)
    text = re.sub(r"(?i)((?:[\w]*API_KEY|[\w]*TOKEN|[\w]*SECRET|[\w]*PASSWORD)\s*[=:]\s*)[^\s,;]+",
                  r"\1[redacted]", text)
    return " ".join("".join(c if not unicodedata.category(c).startswith("C") else " " for c in text).split())


def visible(value):
    return isinstance(value, str) and not value.startswith("gAAAA")


def command_text(command):
    if isinstance(command, list):
        if len(command) >= 3 and command[1] in ("-c", "-lc", "-ic"):
            return clean(command[2])
        return clean(shlex.join(str(part) for part in command))
    return clean(command)


def tool_text(item):
    name = item.get("name", "tool")
    value = item.get("arguments", item.get("input", ""))
    if not visible(value):
        return name + "（参数未公开）"
    try:
        args = json.loads(value)
    except ValueError:
        # exec contains JavaScript. Extract a literal command without evaluating it.
        match = re.search(r'["\']?cmd["\']?\s*:\s*("(?:[^"\\]|\\.)*")', value)
        if match:
            return command_text(json.loads(match[1]))
        return name
    if not isinstance(args, dict):
        return name
    return command_text(args.get("cmd", args.get("command", name)))


def duration(seconds):
    seconds = max(0, int(seconds))
    if seconds < 60:
        return f"{seconds}s"
    if seconds < 3600:
        return f"{seconds // 60}m{seconds % 60:02d}s"
    return f"{seconds // 3600}h{seconds % 3600 // 60:02d}m"


class Activity:
    def __init__(self, row):
        self.row = row
        self.offset = 0
        self.inode = None
        self.status = "状态未记录"
        self.started = None
        self.updated = 0
        self.pending = set()
        self.operation = "等待可读的操作记录"
        self.output = ""

    def update(self):
        path = Path(self.row["rollout_path"])
        try:
            stat = path.stat()
            if self.inode != stat.st_ino or stat.st_size < self.offset:
                self.__init__(self.row)
                self.inode = stat.st_ino
                # Limit initial reads; subsequent refreshes read appended bytes only.
                self.offset = max(0, stat.st_size - 768 * 1024)
                if self.offset:
                    with path.open("rb") as stream:
                        stream.seek(self.offset)
                        self.offset += len(stream.readline())
            with path.open("rb") as stream:
                stream.seek(self.offset)
                while True:
                    line = stream.readline()
                    if not line.endswith(b"\n"):
                        break  # Retry a partial trailing record on the next refresh.
                    self.offset = stream.tell()
                    try:
                        self.consume(json.loads(line))
                    except (ValueError, TypeError, KeyError):
                        continue
        except FileNotFoundError:
            self.status = "记录文件不可用"

    def consume(self, record):
        kind, data = record.get("type"), record.get("payload", {})
        event = data.get("type")
        stamp = record.get("timestamp")
        if stamp:
            try:
                self.updated = datetime.fromisoformat(stamp.replace("Z", "+00:00")).timestamp()
            except ValueError:
                pass
        if kind == "event_msg":
            if event == "task_started":
                self.status, self.started = "处理中", self.updated
                self.pending.clear()
            elif event in ("task_complete", "turn_aborted"):
                self.status = "本轮完成" if event == "task_complete" else "已中断"
                self.pending.clear()
            elif event == "exec_command_begin":
                self.pending.add(data["call_id"])
                self.operation = "执行：" + command_text(data.get("command", ""))
            elif event == "exec_command_end":
                self.pending.discard(data["call_id"])
                self.output = clean(data.get("aggregated_output", ""))
            elif event == "item_completed":
                item = data.get("item", {})
                if item.get("type") == "CommandExecution":
                    code = item.get("exit_code")
                    self.operation = f"命令结束({code if code is not None else item.get('status', '?')})：" + command_text(item.get("command", ""))
                    output = item.get("aggregated_output", "")
                    self.output = clean(" | ".join(output.strip().splitlines()[-2:]))
                elif item.get("type") == "FileChange":
                    self.operation = "修改文件：" + clean(", ".join(item.get("changes", {}).keys()))
                elif item.get("type") == "McpToolCall":
                    self.operation = "工具：" + clean(item.get("server", "") + "/" + item.get("tool", ""))
                elif item.get("type") == "ContextCompaction":
                    self.operation = "已压缩上下文"
        elif kind == "response_item":
            if event in ("function_call", "custom_tool_call"):
                self.pending.add(data.get("call_id"))
                # Coordination notifications should not obscure the last real operation.
                if data.get("name") not in ("send_message", "wait", "wait_agent", "sleep"):
                    self.operation = "调用：" + tool_text(data)
            elif event in ("function_call_output", "custom_tool_call_output"):
                self.pending.discard(data.get("call_id"))
            elif event == "message" and data.get("role") == "assistant" and data.get("phase") == "commentary":
                parts = [part.get("text", "") for part in data.get("content", []) if visible(part.get("text"))]
                if parts:
                    self.output = clean(" ".join(parts))


def owned_threads(owner_pid, home):
    """Identify a standalone CLI's session by open writer locks, never by cwd."""
    processes = subprocess.check_output(["ps", "-axo", "pid=,ppid="], text=True)
    children = {}
    for line in processes.splitlines():
        pid, parent = map(int, line.split())
        children.setdefault(parent, []).append(pid)
    pids = [owner_pid]
    for parent in pids:
        pids.extend(children.get(parent, []))
    names = []
    if sys.platform.startswith("linux"):
        for pid in pids:
            try:
                for fd in Path(f"/proc/{pid}/fd").iterdir():
                    try:
                        names.append(os.readlink(fd))
                    except OSError:
                        pass
            except OSError:
                pass
    else:
        result = subprocess.run(["lsof", "-n", "-p", ",".join(map(str, pids)), "-Fn"],
                                capture_output=True, text=True, timeout=3)
        names = [line[1:] for line in result.stdout.splitlines() if line.startswith("n")]
    lock_dir = str(home / "thread-writer-locks") + os.sep
    return {Path(name).stem for name in names if name.startswith(lock_dir) and name.endswith(".lock")}


class Monitor:
    def __init__(self, home, thread, owner_pid=None):
        self.home, self.thread, self.owner_pid = home, thread, owner_pid
        self.activities = {}

    def refresh(self):
        db = self.home / "state_5.sqlite"
        with sqlite3.connect(db.as_uri() + "?mode=ro", uri=True, timeout=1) as connection:
            connection.row_factory = sqlite3.Row
            if self.owner_pid:
                roots = []
                for thread in owned_threads(self.owner_pid, self.home):
                    row = connection.execute("SELECT id,source FROM threads WHERE id=?", (thread,)).fetchone()
                    if row and not row["source"].startswith("{"):
                        roots.append(row["id"])
                if len(roots) > 1:
                    raise RuntimeError("等待会话切换完成")
                if not roots and not self.thread:
                    raise RuntimeError("等待 Codex 创建或载入会话")
                if roots and self.thread != roots[0]:
                    self.thread = roots[0]
                    self.activities.clear()
            root = connection.execute("SELECT * FROM threads WHERE id=?", (self.thread,)).fetchone()
            if root is None:
                raise RuntimeError("找不到指定会话；未改选其他会话")
            # Discover lineage from metadata, then read only that family's rollouts.
            parents = {}
            for row in connection.execute("SELECT id,source FROM threads WHERE source LIKE '%parent_thread_id%'"):
                try:
                    parent = json.loads(row["source"])["subagent"]["thread_spawn"]["parent_thread_id"]
                    parents.setdefault(parent, []).append(row["id"])
                except (ValueError, TypeError, KeyError):
                    continue
            ids = [self.thread]
            for parent in ids:
                ids.extend(child for child in parents.get(parent, []) if child not in ids)
            rows = [root]
            for child in ids[1:]:
                rows.append(connection.execute("SELECT * FROM threads WHERE id=?", (child,)).fetchone())
        for row in rows:
            if row["id"] not in self.activities:
                self.activities[row["id"]] = Activity(dict(row))
            self.activities[row["id"]].update()
        return [self.activities[row["id"]] for row in rows]


def cell_width(char):
    if unicodedata.combining(char):
        return 0
    return 2 if unicodedata.east_asian_width(char) in ("W", "F") else 1


def wrap_lines(text, width, limit):
    """Wrap terminal cells correctly for Chinese text and combining characters."""
    lines, line, cells = [], "", 0
    for char in clean(text):
        size = cell_width(char)
        if cells + size > width:
            lines.append(line)
            line, cells = "", 0
            if len(lines) == limit:
                last = lines[-1]
                while last and sum(map(cell_width, last)) > width - 1:
                    last = last[:-1]
                lines[-1] = last + "…"
                return lines
        line += char
        cells += size
    if line:
        lines.append(line)
    return lines


def draw(screen, monitor):
    screen.timeout(2000)
    curses.curs_set(0)
    accent = running = waiting = curses.A_BOLD
    if curses.has_colors():
        curses.start_color()
        curses.use_default_colors()
        for number, color in enumerate((curses.COLOR_CYAN, curses.COLOR_GREEN, curses.COLOR_YELLOW), 1):
            curses.init_pair(number, color, -1)
        accent, running, waiting = (curses.color_pair(i) | curses.A_BOLD for i in (1, 2, 3))
    while True:
        if monitor.owner_pid:
            try:
                os.kill(monitor.owner_pid, 0)
            except ProcessLookupError:
                return
        screen.erase()
        height, width = screen.getmaxyx()
        content_width = max(1, width - 2)

        def put(y, text, style=0):
            if 0 <= y < height:
                try:
                    lines = wrap_lines(text, content_width, 1)
                    screen.addstr(y, 1, lines[0] if lines else "", style)
                except curses.error:
                    pass

        put(1, "CODEX · 实时进度", accent)
        put(2, time.strftime("%H:%M:%S") + "  ·  每 2 秒刷新", curses.A_DIM)
        put(3, "─" * content_width, curses.A_DIM)
        try:
            activities = monitor.refresh()
            now = time.time()
            unfinished = [a for a in activities if a.status not in ("本轮完成", "已中断")]
            children = sorted((a for a in unfinished if a.row["id"] != monitor.thread),
                              key=lambda a: (a.status == "处理中", a.updated), reverse=True)
            parent = [a for a in unfinished if a.row["id"] == monitor.thread]
            shown = 0
            y = 5
            if not unfinished:
                put(y, "暂无运行中的任务", running)
                put(y + 2, "任务开始后自动更新", curses.A_DIM)
            for activity in [*parent, *children]:
                row = activity.row
                name = "主会话" if row["id"] == monitor.thread else (row.get("agent_path") or row.get("agent_nickname") or row["id"][:8]).removeprefix("/root/")
                state = "工具调用中" if activity.pending else activity.status
                age = duration(now - activity.updated) + "前更新" if activity.updated else "等待读取记录"
                operation = wrap_lines(activity.operation, content_width, 3)
                output = wrap_lines(activity.output or "暂无输出", content_width, 2)
                elapsed = "本轮 " + duration(now - activity.started) if activity.status == "处理中" and activity.started else "本轮用时尚未记录"
                card = [("● " + name, accent),
                        (state + " · " + age, waiting if activity.pending else running),
                        (elapsed, curses.A_DIM), ("", 0), ("最近操作", curses.A_DIM)]
                card.extend((line, 0) for line in operation)
                card.extend([("", 0), ("最近输出", curses.A_DIM)])
                card.extend((line, 0) for line in output)
                card.extend([("", 0), ("─" * content_width, curses.A_DIM), ("", 0)])
                if y + len(card) > height - 3:
                    break
                for line, style in card:
                    put(y, line, style)
                    y += 1
                shown += 1
            hidden = len(unfinished) - shown
            put(height - 2, f"{len(children)} 个未结束子代理" + (f" · 另有 {hidden} 项" if hidden else ""), curses.A_DIM)
            put(height - 1, "只读记录 · q 关闭", curses.A_DIM)
        except (OSError, sqlite3.Error, RuntimeError, subprocess.SubprocessError) as error:
            put(5, "等待读取记录：" + str(error))
        screen.refresh()
        if screen.getch() in (ord("q"), 27):
            return


def tmux(*args, check=True):
    return subprocess.run(["tmux", *args], check=check, capture_output=True, text=True).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("show", "watch", "snapshot"))
    parser.add_argument("--thread", help="Exact root Codex thread ID")
    parser.add_argument("--owner-pid", type=int, help="Follow the standalone CLI owning these writer locks")
    parser.add_argument("--pane", default=os.environ.get("TMUX_PANE"))
    args = parser.parse_args()
    home = Path(os.environ.get("CODEX_HOME", "~/.codex")).expanduser().resolve()
    if not args.thread and not args.owner_pid:
        parser.error("需要 --thread 或 --owner-pid")
    monitor = Monitor(home, args.thread, args.owner_pid)
    if args.action == "snapshot":
        for activity in monitor.refresh():
            print(json.dumps({"thread": activity.row["id"], "agent": activity.row.get("agent_path") or "主会话",
                              "status": "工具调用中" if activity.pending else activity.status,
                              "operation": activity.operation, "output": activity.output}, ensure_ascii=False))
    elif args.action == "watch":
        curses.wrapper(draw, monitor)
    else:
        if not args.pane or not os.environ.get("TMUX"):
            parser.error("show 需要在 tmux 内运行，或设置 TMUX 并指定 --pane")
        if not args.owner_pid:
            monitor.refresh()  # Validate explicitly selected threads before changing the layout.
        watch = [sys.executable, str(Path(__file__).resolve()), "watch"]
        if args.owner_pid:
            watch.extend(["--owner-pid", str(args.owner_pid)])
        if args.thread:
            watch.extend(["--thread", args.thread])
        command = shlex.join(watch)
        existing = tmux("show-options", "-p", "-v", "-t", args.pane, "@codex-progress-pane", check=False)
        if existing and tmux("display-message", "-p", "-t", existing, "#{pane_id}", check=False) == existing:
            if args.owner_pid:
                tmux("respawn-pane", "-k", "-t", existing, command)
            print("进度窗格已打开：" + existing)
            return
        pane = tmux("split-window", "-h", "-d", "-p", "20", "-P", "-F", "#{pane_id}", "-t", args.pane, command)
        tmux("set-option", "-p", "-t", args.pane, "@codex-progress-pane", pane)
        print("已打开进度窗格：" + pane)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
    except (OSError, sqlite3.Error, RuntimeError, subprocess.CalledProcessError) as error:
        print("Codex progress: " + clean(error), file=sys.stderr)
        raise SystemExit(1)

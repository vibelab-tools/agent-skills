import { createHash } from "node:crypto";
import { closeSync, fstatSync, openSync, readSync } from "node:fs";

export interface CodexCursor {
  offset: number;
  identity?: string;
  turnId?: string;
  status?: string;
  updatedAt?: string;
  latestText?: string;
  /** Last final in the active turn, retained across polling/restarts. */
  finalOffset?: number;
}

export interface CodexEvent {
  id: string;
  kind: "progress" | "final" | "terminal" | "started" | "waiting";
  text?: string;
  timestamp?: string;
  turnId?: string;
  status?: string;
}

type RecordData = { [key: string]: unknown };

function object(value: unknown): RecordData | undefined {
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as RecordData
    : undefined;
}

function messageText(content: unknown): string | undefined {
  if (!Array.isArray(content)) return undefined;
  const text = content.flatMap((value) => {
    const part = object(value);
    return part && (part.type === "output_text" || part.type === "text") && typeof part.text === "string"
      ? [part.text] : [];
  }).join("");
  return text.trim() ? text : undefined;
}

function waitingText(argumentsJson: unknown): string | undefined {
  if (typeof argumentsJson !== "string") return undefined;
  let args: RecordData | undefined;
  try {
    args = object(JSON.parse(argumentsJson));
  } catch {
    return undefined;
  }
  if (!Array.isArray(args?.questions)) return undefined;
  const text = args.questions.flatMap((value) => {
    const question = object(value);
    const title = question?.question ?? question?.title;
    if (typeof title !== "string" || !title.trim()) return [];
    const options = Array.isArray(question?.options) ? question.options.flatMap((value) => {
      if (typeof value === "string") return [`- ${value}`];
      const option = object(value);
      return typeof option?.label === "string" ? [`- ${option.label}${typeof option.description === "string" ? `：${option.description}` : ""}`] : [];
    }) : [];
    return [[title, ...options].join("\n")];
  }).join("\n\n");
  return text || undefined;
}

/**
 * Read only this exact rollout path. Offsets are bytes and commit at newlines;
 * trailing partial records (including split UTF-8 characters) are retried.
 * A replaced or truncated file is rebaselined at EOF without historical replay.
 * Its existing partial last line is also skipped when it eventually completes.
 */
export function readCodexRollout(filePath: string, cursor: CodexCursor): CodexEvent[] {
  let fd: number;
  try {
    fd = openSync(filePath, "r");
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code === "ENOENT") return [];
    throw error;
  }

  try {
    const stat = fstatSync(fd);
    const fileIdentity = `${stat.dev}:${stat.ino}:${stat.birthtimeMs}`;
    const sameFile = cursor.identity === fileIdentity || cursor.identity?.startsWith(`${fileIdentity}|`);
    if ((cursor.identity && !sameFile) || stat.size < cursor.offset) {
      // A truncation may retain its inode. Include the reset generation so new
      // byte offsets cannot collide with previously delivered event IDs.
      cursor.identity = `${fileIdentity}|${stat.ctimeMs}`;
      cursor.offset = stat.size;
      delete cursor.turnId;
      delete cursor.status;
      delete cursor.updatedAt;
      delete cursor.latestText;
      delete cursor.finalOffset;
      return [];
    }
    cursor.identity ??= fileIdentity;

    const events: CodexEvent[] = [];
    const emit = (kind: CodexEvent["kind"], offset: number, timestamp?: string, text?: string) => {
      const id = createHash("sha256").update(`${filePath}\0${cursor.identity}\0${offset}\0${kind}`).digest("hex");
      events.push({ id, kind, ...(text !== undefined && { text }), ...(timestamp && { timestamp }), ...(cursor.turnId && { turnId: cursor.turnId }), ...(kind === "terminal" && { status: cursor.status }) });
      if (timestamp) cursor.updatedAt = timestamp;
      if (text !== undefined) cursor.latestText = text;
      if (kind === "final") cursor.finalOffset = offset;
    };
    const consume = (line: Buffer, offset: number) => {
      let record: RecordData | undefined;
      try {
        record = object(JSON.parse(line.toString("utf8")));
      } catch {
        return; // A malformed complete line must not block subsequent records.
      }
      const payload = object(record?.payload);
      if (!record || !payload) return;
      const timestamp = typeof record.timestamp === "string" ? record.timestamp : undefined;
      const turnId = typeof payload.turn_id === "string" ? payload.turn_id : undefined;
      if (turnId && turnId !== cursor.turnId) {
        const changedTurn = cursor.turnId !== undefined;
        cursor.turnId = turnId;
        if (changedTurn) {
          delete cursor.status;
          delete cursor.latestText;
          delete cursor.finalOffset;
        }
      }
      if (record.type === "event_msg") {
        if (payload.type === "task_started") {
          cursor.status = "started";
          delete cursor.latestText;
          delete cursor.finalOffset;
          emit("started", offset, timestamp);
        } else if (payload.type === "task_complete") {
          if (cursor.finalOffset === undefined && typeof payload.last_agent_message === "string" && payload.last_agent_message.trim()) {
            emit("final", offset, timestamp, payload.last_agent_message);
          }
          cursor.status = "complete";
          emit("terminal", offset, timestamp);
        } else if (payload.type === "turn_aborted") {
          cursor.status = "aborted";
          emit("terminal", offset, timestamp);
        }
        // agent_message duplicates response_item text and is never forwarded.
      } else if (record.type === "response_item") {
        if (payload.type === "message" && payload.role === "assistant") {
          const text = messageText(payload.content);
          if (!text) return;
          if (payload.phase === "commentary" || payload.phase === "partial_answer") {
            cursor.status = "progress";
            emit("progress", offset, timestamp, text);
          } else if (payload.phase === "final_answer" || payload.phase === "final") {
            cursor.status = "final";
            emit("final", offset, timestamp, text);
          }
        } else if (payload.type === "function_call" && typeof payload.name === "string" && /^(?:functions\.)?request_user_input(?:_async)?$/.test(payload.name)) {
          const text = waitingText(payload.arguments);
          if (text) {
            cursor.status = "waiting";
            emit("waiting", offset, timestamp, text);
          }
        }
      }
    };

    let position = cursor.offset;
    let lineStart = position;
    let pending = Buffer.alloc(0);
    let skipLine = false;
    if (position > 0) {
      const previous = Buffer.alloc(1);
      skipLine = readSync(fd, previous, 0, 1, position - 1) === 1 && previous[0] !== 10;
    }
    const chunk = Buffer.alloc(64 * 1024);
    while (position < stat.size) {
      const length = readSync(fd, chunk, 0, Math.min(chunk.length, stat.size - position), position);
      if (!length) break;
      position += length;
      const bytes = pending.length ? Buffer.concat([pending, chunk.subarray(0, length)]) : chunk.subarray(0, length);
      let start = 0;
      let newline: number;
      while ((newline = bytes.indexOf(10, start)) !== -1) {
        if (!skipLine) consume(bytes.subarray(start, newline), lineStart);
        skipLine = false;
        lineStart += newline - start + 1;
        cursor.offset = lineStart;
        start = newline + 1;
      }
      pending = Buffer.from(bytes.subarray(start));
    }
    return events;
  } finally {
    closeSync(fd);
  }
}

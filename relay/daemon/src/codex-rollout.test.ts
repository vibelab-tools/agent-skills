import assert from "node:assert/strict";
import { appendFileSync, mkdtempSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test, { TestContext } from "node:test";
import { CodexCursor, readCodexRollout } from "./codex-rollout";

function fixture(t: TestContext) {
  const directory = mkdtempSync(join(tmpdir(), "relay-codex-rollout-"));
  const path = join(directory, "rollout.jsonl");
  writeFileSync(path, "");
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  return path;
}

const line = (type: string, payload: object) => JSON.stringify({ type, timestamp: "2026-11-09T00:00:00Z", payload }) + "\n";
const message = (text: string, phase = "commentary") => line("response_item", {
  type: "message", role: "assistant", phase, content: [{ type: "output_text", text }],
});
const lifecycle = (type: string, turn_id: string, extra = {}) => line("event_msg", { type, turn_id, ...extra });

test("retries a partial UTF-8 record without advancing or corrupting its text", (t) => {
  const path = fixture(t);
  const first = message("开始");
  const partial = Buffer.from(message("继续处理你好"));
  const split = partial.indexOf(Buffer.from("你")) + 1;
  writeFileSync(path, Buffer.concat([Buffer.from(first), partial.subarray(0, split)]));
  const cursor: CodexCursor = { offset: 0 };
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.text), ["开始"]);
  assert.equal(cursor.offset, Buffer.byteLength(first));
  assert.deepEqual(readCodexRollout(path, cursor), []);
  appendFileSync(path, partial.subarray(split));
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.text), ["继续处理你好"]);
  assert.equal(cursor.offset, statSync(path).size);
  assert.deepEqual(readCodexRollout(path, cursor), []);
});

test("forwards assistant progress/final only and ignores duplicate event text and private/tool records", (t) => {
  const path = fixture(t);
  writeFileSync(path, [
    lifecycle("task_started", "turn-one"),
    line("event_msg", { type: "agent_message", message: "duplicate progress" }),
    message("progress"),
    message("partial", "partial_answer"),
    message("private analysis", "analysis"),
    message("unclassified", "unknown"),
    line("response_item", { type: "reasoning", summary: [{ text: "private reasoning" }] }),
    line("response_item", { type: "function_call", name: "exec_command", arguments: '{"cmd":"secret"}' }),
    line("response_item", { type: "function_call_output", output: "private tool output" }),
    line("response_item", { type: "message", role: "user", phase: "commentary", content: [{ type: "text", text: "user" }] }),
    message("done", "final_answer"),
    line("event_msg", { type: "agent_message", message: "duplicate final" }),
    lifecycle("task_complete", "turn-one", { last_agent_message: "done" }),
  ].join(""));
  const cursor: CodexCursor = { offset: 0 };
  const events = readCodexRollout(path, cursor);
  assert.deepEqual(events.map(({ kind, text }) => [kind, text]), [
    ["started", undefined], ["progress", "progress"], ["progress", "partial"],
    ["final", "done"], ["terminal", undefined],
  ]);
  assert.ok(events.every((event) => event.turnId === "turn-one"));
  assert.equal(cursor.status, "complete");
  assert.equal(cursor.latestText, "done");
  assert.equal(cursor.updatedAt, "2026-11-09T00:00:00Z");
});

test("keeps identical messages distinct and IDs stable when replaying the same byte offsets", (t) => {
  const path = fixture(t);
  writeFileSync(path, message("same") + message("same"));
  const events = readCodexRollout(path, { offset: 0 });
  assert.equal(events.length, 2);
  assert.notEqual(events[0].id, events[1].id);
  assert.deepEqual(readCodexRollout(path, { offset: 0 }), events);
});

test("persists final deduplication across restart, then resets it for the next turn", (t) => {
  const path = fixture(t);
  writeFileSync(path, lifecycle("task_started", "one") + message("same", "final"));
  let cursor: CodexCursor = { offset: 0 };
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.kind), ["started", "final"]);
  cursor = JSON.parse(JSON.stringify(cursor));
  appendFileSync(path, lifecycle("task_complete", "one", { last_agent_message: "same" }));
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.kind), ["terminal"]);
  appendFileSync(path, lifecycle("task_started", "two") + lifecycle("task_complete", "two", { last_agent_message: "same" }));
  const next = readCodexRollout(path, cursor);
  assert.deepEqual(next.map(({ kind, text }) => [kind, text]), [["started", undefined], ["final", "same"], ["terminal", undefined]]);
  assert.ok(next.every((event) => event.turnId === "two"));
  appendFileSync(path, lifecycle("turn_aborted", "three"));
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.kind), ["terminal"]);
  assert.equal(cursor.status, "aborted");
  assert.equal(cursor.latestText, undefined);
});

test("skips replaced-file history and resumes only complete new records", (t) => {
  const path = fixture(t);
  writeFileSync(path, message("old"));
  const cursor: CodexCursor = { offset: 0 };
  const originalId = readCodexRollout(path, cursor)[0].id;
  renameSync(path, `${path}.old`);
  const replacement = message("replacement history") + message("partial replacement").slice(0, -2);
  writeFileSync(path, replacement);
  assert.deepEqual(readCodexRollout(path, cursor), []);
  assert.equal(cursor.offset, Buffer.byteLength(replacement));
  assert.equal(cursor.latestText, undefined);
  appendFileSync(path, '}\n' + message("old"));
  const events = readCodexRollout(path, cursor);
  assert.deepEqual(events.map((event) => event.text), ["old"]);
  assert.notEqual(events[0].id, originalId);
});

test("rebaselines truncation without replay and survives a missing exact path", (t) => {
  const path = fixture(t);
  writeFileSync(path, message("old ".repeat(100)));
  const cursor: CodexCursor = { offset: 0 };
  readCodexRollout(path, cursor);
  const originalIdentity = cursor.identity;
  writeFileSync(path, message("short replacement"));
  assert.deepEqual(readCodexRollout(path, cursor), []);
  assert.notEqual(cursor.identity, originalIdentity);
  assert.equal(cursor.offset, statSync(path).size);
  appendFileSync(path, message("new append"));
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.text), ["new append"]);
  rmSync(path);
  const checkpoint = { ...cursor };
  assert.deepEqual(readCodexRollout(path, cursor), []);
  assert.deepEqual(cursor, checkpoint);
});

test("accepts caller EOF seeding and consumes malformed complete lines without blocking", (t) => {
  const path = fixture(t);
  writeFileSync(path, message("history"));
  const cursor: CodexCursor = { offset: statSync(path).size };
  assert.deepEqual(readCodexRollout(path, cursor), []);
  appendFileSync(path, "{invalid json}\n" + message("new"));
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.text), ["new"]);
  assert.equal(cursor.offset, statSync(path).size);
});

test("handles records larger than read chunks and preserves multiline output", (t) => {
  const path = fixture(t);
  const text = "多行\n" + "文".repeat(50_000);
  writeFileSync(path, message(text) + message("next"));
  const cursor: CodexCursor = { offset: 0 };
  assert.deepEqual(readCodexRollout(path, cursor).map((event) => event.text), [text, "next"]);
  assert.equal(cursor.offset, statSync(path).size);
});

test("waiting events expose user-facing questions and options without internal IDs", (t) => {
  const path = fixture(t);
  writeFileSync(path, line("response_item", {
    type: "function_call", name: "request_user_input",
    arguments: JSON.stringify({ private: "never relay", questions: [
      { id: "internal-id", header: "internal-header", question: "Which target?", options: [{ label: "A", description: "Use target A" }] },
      { question: "When?" },
    ] }),
  }) + line("response_item", { type: "function_call", name: "request_user_input", arguments: "malformed private argument" }) + line("response_item", { type: "function_call", name: "functions.request_user_input_async", arguments: JSON.stringify({ questions: [{ title: "Continue?", options: ["Yes", "No"] }] }) }));
  const cursor: CodexCursor = { offset: 0 };
  const events = readCodexRollout(path, cursor);
  assert.deepEqual(events.map(({ kind, text }) => [kind, text]), [["waiting", "Which target?\n- A：Use target A\n\nWhen?"], ["waiting", "Continue?\n- Yes\n- No"]]);
  assert.equal(cursor.status, "waiting");
});

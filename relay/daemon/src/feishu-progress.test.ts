// ABOUTME: Exercises durable Feishu progress through HTTP, SDK, JSONL, and real tmux input.
// ABOUTME: Only the external Feishu API and Codex's stdin consumer are simulated.

import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { appendFileSync, existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test, { TestContext } from "node:test";
import * as lark from "@larksuiteoapi/node-sdk";
import axios from "axios";
import { FeishuProgress } from "./feishu-progress";
import { fingerprintPrompt, PromptOriginTracker } from "./prompt-origin";
import { FeishuProvider } from "./providers/feishu";
import { Server } from "./server";
import { SessionManager } from "./session-manager";
import { DaemonConfig } from "./types";

interface Reply { root: string; text: string; uuid?: string; }

async function waitFor(check: () => boolean, description = "real tmux input", timeout = 5_000): Promise<void> {
  const deadline = Date.now() + timeout;
  while (!check()) {
    assert.ok(Date.now() < deadline, `timed out waiting for ${description}`);
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
}

async function fixture(t: TestContext) {
  const directory = mkdtempSync(join(tmpdir(), "relay-progress-"));
  const socketDirectory = join(directory, `tmux-${process.getuid!()}`);
  mkdirSync(socketDirectory, { mode: 0o700 });
  const socket = join(socketDirectory, "default");
  const statePath = join(directory, "progress.json");
  const codexHome = join(directory, "codex-home");
  const sessions = ["codex-progress-a", "codex-progress-b"];
  const roots = ["om_progress_a", "om_progress_b"];
  const inputs = sessions.map((_, index) => join(directory, `input-${index}.jsonl`));
  const rollouts = sessions.map((_, index) => join(directory, `rollout-${index}.jsonl`));
  const ready = sessions.map((_, index) => join(directory, `ready-${index}`));
  const locks = sessions.map((_, index) => join(codexHome, "thread-writer-locks", `progress-thread-${index}.lock`));
  mkdirSync(join(codexHome, "thread-writer-locks"), { recursive: true });
  const source = join(directory, "codex-stdin.c");
  const executable = join(directory, "codex");
  // A real executable named codex exercises pane ownership checks as well as
  // injection; a Python script would expose python as pane_current_command.
  writeFileSync(source, [
    "#include <stdio.h>",
    "#include <stdlib.h>",
    "#include <unistd.h>",
    "int main(int argc, char **argv) {",
    "  if (argc != 4) return 1;",
    "  FILE *writer_lock = fopen(argv[3], \"a\");",
    "  if (!writer_lock) return 1;",
    "  FILE *ready = fopen(argv[2], \"w\");",
    "  if (!ready) return 1;",
    "  fclose(ready);",
    "  char *line = NULL; size_t capacity = 0; int count = 0;",
    "  while (getline(&line, &capacity, stdin) != -1) {",
    "    FILE *output = fopen(argv[1], \"a\");",
    "    if (!output) return 1;",
    "    fputs(line, output); fclose(output);",
    "    if (count++ == 0) {",
    "      char busy[4096]; snprintf(busy, sizeof(busy), \"%s.busy\", argv[2]);",
    "      ready = fopen(busy, \"w\"); if (!ready) return 1; fclose(ready);",
    "      usleep(1200000);",
    "    }",
    "  }",
    "  free(line); return 0;",
    "}",
  ].join("\n"));
  inputs.forEach((path) => writeFileSync(path, ""));
  rollouts.forEach((path) => writeFileSync(path, ""));
  writeFileSync(rollouts[0], message("historical reply must stay private"));
  const oldTmux = process.env.TMUX;
  const oldTmuxPane = process.env.TMUX_PANE;
  const oldTmuxTmpdir = process.env.TMUX_TMPDIR;
  const oldCodexHome = process.env.CODEX_HOME;
  const localeKeys = ["LANG", "LC_ALL", "LC_CTYPE"] as const;
  const oldLocale = Object.fromEntries(localeKeys.map((key) => [key, process.env[key]]));
  const accepted: Reply[] = [];
  const attempts: Reply[] = [];
  const apiErrors: unknown[] = [];
  let failText: string | undefined;
  let now = 1_000_000;
  let server: Server | undefined;
  let provider: FeishuProvider;
  let progress!: FeishuProgress;
  let manager: SessionManager;
  let baseUrl = "";
  const config = {
    port: 0,
    hostname: "progress-test",
    bindingsPath: join(directory, "bindings.json"),
    feishuChatId: "oc_progress_test",
    feishuAppId: "progress-test-app",
    feishuAppSecret: "progress-test-secret",
    feishuUserTokenPath: join(directory, "absent-user-token.json"),
  } as DaemonConfig;

  const api = createServer(async (req, res) => {
    try {
      const chunks: Buffer[] = [];
      for await (const chunk of req) chunks.push(Buffer.from(chunk));
      const body = JSON.parse(Buffer.concat(chunks).toString() || "{}");
      let response: object;
      const reply = req.url?.match(/^\/open-apis\/im\/v1\/messages\/([^/]+)\/reply$/);
      if (req.url === "/open-apis/auth/v3/tenant_access_token/internal") {
        response = { code: 0, tenant_access_token: "progress-test-token", expire: 7200 };
      } else if (reply) {
        assert.equal(req.headers.authorization, "Bearer progress-test-token");
        assert.equal(body.reply_in_thread, true);
        if (body.uuid !== undefined) {
          assert.equal(typeof body.uuid, "string");
          assert.ok(body.uuid.length <= 50, "Feishu rejects uuid values longer than 50 characters");
        }
        const message = { root: reply[1], text: JSON.parse(body.content).text, uuid: body.uuid };
        attempts.push(message);
        if (failText && message.text.includes(failText)) {
          response = { code: 999_001, msg: "temporary Feishu outage" };
        } else {
          accepted.push(message);
          response = { code: 0, data: { message_id: `om_reply_${accepted.length}` } };
        }
      } else {
        throw new Error(`unexpected external API request ${req.method} ${req.url}`);
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(response));
    } catch (error) {
      apiErrors.push(error);
      res.writeHead(500);
      res.end();
    }
  });

  t.after(async () => {
    progress?.stop();
    provider?.disconnect();
    server?.stop();
    api.closeAllConnections();
    await new Promise<void>((resolve) => api.close(() => resolve()));
    try {
      execFileSync("tmux", ["-S", socket, "kill-server"], { stdio: "ignore" });
    } finally {
      if (oldTmux === undefined) delete process.env.TMUX;
      else process.env.TMUX = oldTmux;
      if (oldTmuxPane === undefined) delete process.env.TMUX_PANE;
      else process.env.TMUX_PANE = oldTmuxPane;
      if (oldTmuxTmpdir === undefined) delete process.env.TMUX_TMPDIR;
      else process.env.TMUX_TMPDIR = oldTmuxTmpdir;
      if (oldCodexHome === undefined) delete process.env.CODEX_HOME;
      else process.env.CODEX_HOME = oldCodexHome;
      for (const key of localeKeys) {
        if (oldLocale[key] === undefined) delete process.env[key];
        else process.env[key] = oldLocale[key];
      }
      rmSync(directory, { recursive: true, force: true });
    }
    assert.deepEqual(apiErrors, []);
  });

  // Match launchd: no locale or inherited tmux UTF-8 client flags. A private
  // default socket also keeps implicit service tmux commands off user servers.
  localeKeys.forEach((key) => delete process.env[key]);
  delete process.env.TMUX;
  delete process.env.TMUX_PANE;
  process.env.TMUX_TMPDIR = directory;
  process.env.CODEX_HOME = codexHome;
  execFileSync("python3", ["-c", [
    "import json, sqlite3, sys",
    "connection = sqlite3.connect(sys.argv[1])",
    "connection.execute('CREATE TABLE threads (id TEXT PRIMARY KEY, source TEXT, rollout_path TEXT)')",
    "connection.executemany('INSERT INTO threads VALUES (?, ?, ?)', json.loads(sys.argv[2]))",
    "connection.commit()",
    "connection.close()",
  ].join("\n"), join(codexHome, "state_5.sqlite"), JSON.stringify(sessions.map((_, index) => [
    `progress-thread-${index}`, "cli", rollouts[index],
  ]))], { stdio: "pipe" });
  execFileSync("cc", [source, "-o", executable], { stdio: "pipe" });
  for (const [index, session] of sessions.entries()) {
    execFileSync("tmux", ["-f", "/dev/null", "-S", socket, "new-session", "-d", "-s", session,
      executable, inputs[index], ready[index], locks[index]], { stdio: "ignore" });
  }
  assert.deepEqual(execFileSync("tmux", ["list-sessions", "-F", "#{session_name}"], { encoding: "utf8" }).trim().split("\n").sort(), sessions);
  await waitFor(() => ready.every(existsSync));
  for (const session of sessions) {
    assert.equal(execFileSync("tmux", ["list-panes", "-t", `=${session}`, "-F", "#{pane_current_command}"], { encoding: "utf8" }).trim(), "codex");
  }
  await new Promise<void>((resolve) => api.listen(0, "127.0.0.1", resolve));
  const apiPort = (api.address() as AddressInfo).port;

  async function restart() {
    progress?.stop();
    provider?.disconnect();
    server?.stop();
    manager = new SessionManager(config);
    if (!manager.getAll().length) sessions.forEach((session, index) => manager.bindFeishu(session, roots[index]));
    const origins = new PromptOriginTracker(() => now);
    provider = new FeishuProvider(config);
    const httpInstance = axios.create({ proxy: false });
    httpInstance.interceptors.request.use((request) => {
      request.url = `http://127.0.0.1:${apiPort}${new URL(request.url!).pathname}`;
      return request;
    });
    httpInstance.interceptors.response.use((response) => response.data);
    (provider as any).client = new lark.Client({
      appId: config.feishuAppId,
      appSecret: config.feishuAppSecret,
      httpInstance: httpInstance as any,
      loggerLevel: lark.LoggerLevel.error,
    });
    progress = new FeishuProgress(statePath, manager, origins, provider, () => now);
    provider.onMessage((message, root) => progress.receive(message, root));
    server = new Server(config, manager, origins, null, null, provider, progress);
    await server.start();
    baseUrl = `http://127.0.0.1:${((server as any).httpServer.address() as AddressInfo).port}`;
  }
  await restart();
  progress.start();
  await waitFor(() => {
    const journal = JSON.parse(readFileSync(statePath, "utf8"));
    return roots.every((root, index) => journal.topics[root]?.transcript === rollouts[index] && journal.topics[root]?.ownerConfirmed === true);
  }, "real Codex writer-lock and SQLite transcript discovery", 10_000);
  progress.stop();
  assert.equal(JSON.parse(readFileSync(statePath, "utf8")).outbox.length, 0);
  assert.equal(accepted.length, 0);

  return {
    accepted, attempts, sessions, roots, rollouts, statePath,
    get progress() { return progress; },
    get now() { return now; },
    set now(value: number) { now = value; },
    fail(text?: string) { failText = text; },
    restart,
    captured(index: number): string[] {
      return readFileSync(inputs[index], "utf8").trim().split("\n").filter(Boolean);
    },
    busy(index: number) { return existsSync(`${ready[index]}.busy`); },
    async replaceWithShell(index: number) {
      const shellReady = `${ready[index]}.shell`;
      const pane = execFileSync("tmux", ["-S", socket, "list-panes", "-t", `=${sessions[index]}`, "-F", "#{pane_id}"], { encoding: "utf8" }).trim();
      execFileSync("tmux", ["-S", socket, "respawn-pane", "-k", "-t", pane,
        "/bin/sh", "-c", 'printf ready > "$2"; while IFS= read -r line; do printf \'%s\\n\' "$line" >> "$1"; done',
        "relay-test-shell", inputs[index], shellReady], { stdio: "pipe" });
      await waitFor(() => existsSync(shellReady));
      assert.match(execFileSync("tmux", ["list-panes", "-t", `=${sessions[index]}`, "-F", "#{pane_current_command}"], { encoding: "utf8" }).trim(), /^(?:sh|bash)$/);
    },
    async restoreCodex(index: number) {
      rmSync(ready[index], { force: true });
      const pane = execFileSync("tmux", ["-S", socket, "list-panes", "-t", `=${sessions[index]}`, "-F", "#{pane_id}"], { encoding: "utf8" }).trim();
      execFileSync("tmux", ["-S", socket, "respawn-pane", "-k", "-t", pane,
        executable, inputs[index], ready[index], locks[index]], { stdio: "pipe" });
      await waitFor(() => existsSync(ready[index]));
      assert.equal(execFileSync("tmux", ["list-panes", "-t", `=${sessions[index]}`, "-F", "#{pane_current_command}"], { encoding: "utf8" }).trim(), "codex");
    },
    async extraCodexWindow(index: number) {
      const input = join(directory, `extra-input-${index}`);
      const windowReady = join(directory, `extra-ready-${index}`);
      writeFileSync(input, "");
      const window = execFileSync("tmux", ["-S", socket, "new-window", "-d", "-t", `${sessions[index]}:`,
        "-n", "second-codex", "-P", "-F", "#{window_id}", executable, input, windowReady, locks[index]], { encoding: "utf8" }).trim();
      await waitFor(() => existsSync(windowReady));
      assert.deepEqual(execFileSync("tmux", ["list-panes", "-s", "-t", `=${sessions[index]}`, "-F", "#{pane_current_command}"], { encoding: "utf8" }).trim().split("\n"), ["codex", "codex"]);
      return {
        captured: () => readFileSync(input, "utf8").trim().split("\n").filter(Boolean),
        close: () => execFileSync("tmux", ["-S", socket, "kill-window", "-t", window], { stdio: "pipe" }),
      };
    },
    async incoming(index: number, id: string, text: string) {
      const handled = await (provider as any).handleIncomingMessage({
        sender: { sender_type: "user", sender_id: { open_id: "ou_progress_test" } },
        message: {
          message_id: id, chat_id: config.feishuChatId, chat_type: "group", message_type: "text",
          root_id: roots[index], content: JSON.stringify({ text }), create_time: String(now),
        },
      }, "websocket");
      assert.equal(handled, true);
    },
    async post(endpoint: string, body: object) {
      const response = await fetch(`${baseUrl}${endpoint}`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      assert.equal(response.status, 200, await response.clone().text());
      return await response.json() as any;
    },
    async status() {
      const response = await fetch(`${baseUrl}/status`);
      assert.equal(response.status, 200);
      return await response.json() as any;
    },
    async pump(count: number) {
      for (let index = 0; index < count; index++) {
        await progress.tick();
        now += 1_000;
      }
    },
  };
}

const record = (type: string, payload: object) => JSON.stringify({ type, timestamp: "2026-11-09T01:00:00Z", payload }) + "\n";
const message = (text: string, phase = "commentary") => record("response_item", {
  type: "message", role: "assistant", phase, content: [{ type: "output_text", text }],
});

test("routes busy-session inputs and coalesced progress across two topics, with local status and final hook deduplication", async (t) => {
  const f = await fixture(t);
  await f.incoming(0, "input-a-1", "first live prompt");
  await waitFor(() => f.busy(0));
  await f.incoming(0, "input-a-2", "second prompt while busy");
  await f.incoming(1, "input-b-1", "other topic prompt");
  await f.incoming(0, "input-a-1", "first live prompt");
  await waitFor(() => f.captured(0).length === 2 && f.captured(1).length === 1);
  assert.deepEqual(f.captured(0), ["first live prompt", "second prompt while busy"]);
  assert.deepEqual(f.captured(1), ["other topic prompt"]);
  for (const prompt of ["first live prompt", "second prompt while busy"]) {
    assert.deepEqual(await f.post("/prompt-origin", {
      tmuxSession: f.sessions[0], fingerprint: fingerprintPrompt(prompt), transcriptPath: f.rollouts[0],
    }), { origin: "im" });
  }
  assert.deepEqual(await f.post("/prompt-origin", {
    tmuxSession: f.sessions[1], fingerprint: fingerprintPrompt("first live prompt"), transcriptPath: f.rollouts[1],
  }), { origin: "local" });

  appendFileSync(f.rollouts[0], record("event_msg", { type: "task_started", turn_id: "turn-a" }) +
    message("A: inspecting files") + message("private reasoning", "analysis") +
    record("response_item", { type: "function_call_output", output: "private tool output" }) +
    record("event_msg", { type: "agent_message", message: "duplicate progress" }));
  appendFileSync(f.rollouts[1], record("event_msg", { type: "task_started", turn_id: "turn-b" }) + message("B: isolated work"));
  const began = f.now;
  await f.pump(3);
  assert.equal(f.accepted.filter((reply) => reply.text.includes("已收到这条消息") && reply.root === f.roots[0]).length, 2);
  assert.equal(f.accepted.filter((reply) => reply.text.includes("已收到这条消息") && reply.root === f.roots[1]).length, 1);
  assert.equal(f.accepted.some((reply) => reply.text.includes("A:") || reply.text.includes("B:")), false);
  f.now = began + 10_000;
  appendFileSync(f.rollouts[0], message("A: preparing changes", "partial_answer"));
  await f.progress.tick();
  f.now = began + 29_999;
  await f.progress.tick();
  assert.equal(f.accepted.some((reply) => reply.text.includes("A:") || reply.text.includes("B:")), false);
  f.now = began + 30_000;
  await f.pump(2);
  assert.deepEqual(f.accepted.filter((reply) => reply.text.includes("A:") || reply.text.includes("B:")).map(({ root, text }) => ({ root, text })), [
    { root: f.roots[0], text: "A: inspecting files\n\nA: preparing changes" },
    { root: f.roots[1], text: "B: isolated work" },
  ]);
  assert.equal(f.accepted.some((reply) => /private|duplicate/.test(reply.text)), false);

  const topicStatus = f.progress.status(f.roots[0]);
  assert.ok(topicStatus.includes("A: preparing changes"));
  assert.equal(topicStatus.includes("B:"), false);
  await f.incoming(0, "local-status", "/relay status");
  const status = await f.status();
  assert.equal(status.bindings.length, 2);
  appendFileSync(f.rollouts[0], message("A: final result", "final_answer") + record("event_msg", {
    type: "task_complete", turn_id: "turn-a", last_agent_message: "A: final result",
  }));
  await f.progress.tick();
  await f.post("/notify", {
    type: "stop", tmuxSession: f.sessions[0], text: "A: final result", transcriptPath: f.rollouts[0], turnId: "turn-a",
  });
  await f.post("/notify", {
    type: "stop", tmuxSession: f.sessions[0], text: "A: final", transcriptPath: f.rollouts[0], turnId: "turn-a",
  });
  await f.pump(4);
  assert.equal(f.accepted.filter((reply) => reply.text.includes("A: final result")).length, 1);
  assert.equal(f.accepted.some((reply) => reply.text.endsWith("A: final")), false);
  assert.ok(f.accepted.some((reply) => reply.root === f.roots[0] && reply.text.includes("会话：") && reply.text.includes("A: preparing changes")));
  assert.deepEqual(f.captured(0), ["first live prompt", "second prompt while busy"]);
  assert.deepEqual(f.captured(1), ["other topic prompt"]);
});

test("recovers an actual failed Feishu final delivery from disk after fresh service instances without reinjecting inputs", async (t) => {
  const f = await fixture(t);
  await f.incoming(0, "durable-input", "once across restart");
  await waitFor(() => f.captured(0).length === 1);
  assert.deepEqual(await f.post("/prompt-origin", {
    tmuxSession: f.sessions[0], fingerprint: fingerprintPrompt("once across restart"), transcriptPath: f.rollouts[0],
  }), { origin: "im" });
  await f.pump(1); // Receipt reaches the real SDK/API before the outage.
  appendFileSync(f.rollouts[0], record("event_msg", { type: "task_started", turn_id: "durable-turn" }) +
    message("durable final", "final_answer") + record("event_msg", {
      type: "task_complete", turn_id: "durable-turn", last_agent_message: "durable final",
    }));
  f.fail("durable final");
  await f.progress.tick();
  assert.equal(f.attempts.filter((reply) => reply.text.includes("durable final")).length, 1);
  assert.equal(f.accepted.some((reply) => reply.text.includes("durable final")), false);
  const journal = JSON.parse(readFileSync(f.statePath, "utf8"));
  assert.equal(journal.outbox.length, 1);
  assert.equal(journal.outbox[0].attempts, 1);
  assert.equal(journal.topics[f.roots[0]].cursor.offset, Buffer.byteLength(readFileSync(f.rollouts[0])));
  const firstUuid = f.attempts.find((reply) => reply.text.includes("durable final"))!.uuid;
  assert.ok(firstUuid);

  await f.restart(); // Recreates SessionManager, tracker, provider, progress, and HTTP server.
  await f.incoming(0, "durable-input", "once across restart");
  await f.post("/notify", {
    type: "stop", tmuxSession: f.sessions[0], text: "durable final", transcriptPath: f.rollouts[0], turnId: "durable-turn",
  });
  f.fail();
  await f.progress.tick();
  assert.equal(f.attempts.filter((reply) => reply.text.includes("durable final")).length, 1, "must retain retry backoff across restart");
  f.now = journal.outbox[0].due;
  await f.pump(2);
  const finalReplies = f.accepted.filter((reply) => reply.text.includes("durable final"));
  assert.equal(finalReplies.length, 1);
  assert.equal(finalReplies[0].root, f.roots[0]);
  assert.equal(finalReplies[0].uuid, firstUuid);
  assert.deepEqual(f.captured(0), ["once across restart"]);
  assert.deepEqual(f.captured(1), []);
  assert.equal(JSON.parse(readFileSync(f.statePath, "utf8")).outbox.length, 0);
  assert.ok(f.progress.status(f.roots[0]).includes("本轮已结束"));
});

test("holds inputs away from replacement shells and preserves batched progress and final ordering when attaching mid-turn", async (t) => {
  const f = await fixture(t);
  await f.replaceWithShell(0);
  await f.incoming(0, "shell-recovery-input", "preserved until Codex returns");
  await f.pump(2);
  assert.deepEqual(f.captured(0), [], "a live shell must never receive the model prompt");
  assert.ok(f.accepted.some((reply) => reply.root === f.roots[0] && reply.text.includes("输入已保存")));
  assert.ok(f.progress.status(f.roots[0]).includes("待提交输入：1"));
  await f.restoreCodex(0);
  const extraWindow = await f.extraCodexWindow(0);
  await f.pump(2);
  assert.deepEqual(f.captured(0), [], "multiple Codex windows must keep the target ambiguous");
  assert.deepEqual(extraWindow.captured(), []);
  assert.equal(JSON.parse(readFileSync(f.statePath, "utf8")).inputs["shell-recovery-input"].state, "received");
  extraWindow.close();
  await f.pump(1);
  await waitFor(() => f.captured(0).length === 1);
  assert.deepEqual(f.captured(0), ["preserved until Codex returns"]);
  await f.pump(1);
  assert.ok(f.accepted.some((reply) => reply.root === f.roots[0] && reply.text.includes("已提交到会话")));

  // Attach after the final already exists, then let task_complete arrive.
  f.rollouts[0] = `${f.rollouts[0]}.attached`;
  writeFileSync(f.rollouts[0], record("event_msg", { type: "task_started", turn_id: "attached-final" }) +
    message("attached final result", "final_answer"));
  await f.post("/prompt-origin", {
    tmuxSession: f.sessions[0], fingerprint: fingerprintPrompt("preserved until Codex returns"), transcriptPath: f.rollouts[0],
  });
  assert.equal(f.accepted.some((reply) => reply.text.includes("attached final result")), false);
  appendFileSync(f.rollouts[0], record("event_msg", {
    type: "task_complete", turn_id: "attached-final", last_agent_message: "attached final result",
  }));
  await f.post("/notify", {
    type: "stop", tmuxSession: f.sessions[0], transcriptPath: f.rollouts[0], turnId: "attached-final", text: "attached final result",
  });

  // The Stop hook can instead persist before the response_item is flushed.
  writeFileSync(f.rollouts[1], record("event_msg", { type: "task_started", turn_id: "stop-first" }));
  await f.post("/notify", {
    type: "stop", tmuxSession: f.sessions[1], transcriptPath: f.rollouts[1], turnId: "stop-first", text: "early final",
  });
  assert.ok(JSON.parse(readFileSync(f.statePath, "utf8")).outbox.some((item: { text: string }) => item.text === "early final"));
  appendFileSync(f.rollouts[1], message("early final\n", "final_answer") + record("event_msg", {
    type: "task_complete", turn_id: "stop-first", last_agent_message: "early final\n",
  }));
  await f.pump(4);
  assert.equal(f.accepted.filter((reply) => reply.text.includes("attached final result")).length, 1);
  assert.equal(f.accepted.filter((reply) => reply.text.includes("early final")).length, 1);
  assert.ok(f.accepted.find((reply) => reply.text.includes("early final"))!.text.endsWith("early final"));

  // All records below arrive in one poll: the two progress groups need distinct
  // durable IDs despite sharing the poll's final cursor offset.
  appendFileSync(f.rollouts[0], record("event_msg", { type: "task_started", turn_id: "batched-work" }) +
    message("batch progress A") + record("response_item", {
      type: "function_call", name: "request_user_input", arguments: JSON.stringify({ questions: [{ question: "Which batch target?" }] }),
    }) + message("batch progress B") + message("batch final result", "final_answer") +
    record("event_msg", { type: "task_complete", turn_id: "batched-work", last_agent_message: "batch final result" }) +
    record("event_msg", { type: "task_started", turn_id: "aborted-last" }) +
    record("event_msg", { type: "turn_aborted", turn_id: "aborted-last" }));
  // Reverse the terminal ordering in the other topic: an aborted turn still
  // needs its notice even when the last record in the same poll is complete.
  appendFileSync(f.rollouts[1], record("event_msg", { type: "task_started", turn_id: "aborted-first" }) +
    record("event_msg", { type: "turn_aborted", turn_id: "aborted-first" }) +
    record("event_msg", { type: "task_started", turn_id: "complete-last" }) +
    record("event_msg", { type: "task_complete", turn_id: "complete-last" }));
  await f.pump(8);
  assert.deepEqual(f.accepted.filter((reply) => /batch progress|Which batch|batch final/.test(reply.text)).map((reply) => reply.text.replace(/^<at[^>]+>[^<]+<\/at> /, "")), [
    "batch progress A", "Which batch target?", "batch progress B", "batch final result",
  ]);
  for (const root of f.roots) {
    assert.equal(f.accepted.filter((reply) => reply.root === root && reply.text.includes("本轮已中断")).length, 1);
  }
  assert.deepEqual(f.captured(0), ["preserved until Codex returns"]);
  assert.deepEqual(extraWindow.captured(), []);
  assert.deepEqual(f.captured(1), []);
});

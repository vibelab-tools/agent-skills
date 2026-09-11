// ABOUTME: Regression tests for relay notification routing.
// ABOUTME: Verifies Feishu notifications reuse one session topic with selective alerts.

import assert from "node:assert/strict";
import { appendFileSync, chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { createServer } from "node:http";
import * as lark from "@larksuiteoapi/node-sdk";
import axios from "axios";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import test from "node:test";
import { SessionManager } from "./session-manager";
import { Server } from "./server";
import { DaemonConfig } from "./types";
import { PromptOriginTracker } from "./prompt-origin";
import { FeishuProvider } from "./providers/feishu";
import { writeTokenState } from "./feishu-user-auth";

const USER_PROMPT_HOOK = join(
  __dirname,
  "../../plugins/relay/scripts/hook-user-prompt.sh"
);

test("uses concise Feishu topic titles, reuses topics, and alerts only attention events", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-server-test-"));
  const config = {
    port: 0,
    hostname: "gpu.example.com",
    feishuChatId: "oc_test",
    bindingsPath: join(testDir, "bindings.json"),
  } as DaemonConfig;
  const sessionManager = new SessionManager(config);
  const created: Array<{ chatId: string; title: string }> = [];
  const sent: Array<{
    topicId?: string;
    text: string;
    mentionAll?: boolean;
    sendAsUser?: boolean;
  }> = [];
  const feishuProvider = {
    name: "feishu",
    sendNewRootMessage: async (chatId: string, title: string) => {
      created.push({ chatId, title });
      return created.length === 1 ? "om_session" : "om_claude";
    },
    send: async (options: {
      topicId?: string;
      text: string;
      mentionAll?: boolean;
      sendAsUser?: boolean;
    }) => {
      sent.push(options);
      return true;
    },
    getRecoveryStatus: () => ({
      websocketState: "connected",
      schedulerIntervalMs: 15_000,
      activeBindings: 1,
      requestsSinceStart: 3,
    }),
  };
  const server = new Server(
    config,
    sessionManager,
    new PromptOriginTracker(),
    null,
    null,
    feishuProvider as any
  );

  try {
    await server.start();
    const address = (server as any).httpServer.address() as AddressInfo;

    for (const notification of [
      { type: "stop", text: "first completion" },
      { type: "user_prompt", text: "continue" },
      { type: "ask_user", text: "need input" },
    ]) {
      const response = await fetch(`http://127.0.0.1:${address.port}/notify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: notification.type,
          tmuxSession: "codex-demo-abc123",
          text: notification.text,
        }),
      });
      assert.equal(response.status, 200);
    }

    assert.deepEqual(created, [
      { chatId: "oc_test", title: "🔗 demo" },
    ]);
    assert.deepEqual(sent, [
      {
        topicId: "om_session",
        text: "first completion",
        mentionAll: true,
        sendAsUser: false,
      },
      {
        topicId: "om_session",
        text: "continue",
        mentionAll: false,
        sendAsUser: true,
      },
      {
        topicId: "om_session",
        text: "need input",
        mentionAll: true,
        sendAsUser: false,
      },
    ]);
    assert.equal(
      sessionManager.findByTmuxSession("codex-demo-abc123")?.feishuRootMessageId,
      "om_session"
    );

    const statusResponse = await fetch(`http://127.0.0.1:${address.port}/status`);
    const status = await statusResponse.json() as any;
    assert.deepEqual(status.feishuRecovery, {
      total: 1,
      missing: 0,
      websocketState: "connected",
      schedulerIntervalMs: 15_000,
      activeBindings: 1,
      requestsSinceStart: 3,
    });
    assert.equal(status.bindings[0].feishuRootMessageId, "om_session");
    assert.equal(status.bindings[0].feishuThreadId, undefined);
    assert.equal(status.bindings[0].feishuLastMessageAt, undefined);
    assert.equal(status.bindings[0].feishuLastMessageIds, undefined);
    assert.equal(status.bindings[0].feishuRecentMessageIds, undefined);
    assert.equal(status.bindings[0].feishuMissingSince, undefined);

    const claudeResponse = await fetch(`http://127.0.0.1:${address.port}/notify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        type: "user_prompt",
        tmuxSession: "claude-editor-def456",
        text: "edit this",
      }),
    });
    assert.equal(claudeResponse.status, 200);
    assert.deepEqual(created[1], { chatId: "oc_test", title: "🔗 editor" });
    assert.deepEqual(sent[3], {
      topicId: "om_claude",
      text: "edit this",
      mentionAll: false,
      sendAsUser: true,
    });
  } finally {
    server.stop();
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("relays local Codex prompts but not prompts injected from IM", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-prompt-origin-test-"));
  const homeDir = join(testDir, "home");
  const binDir = join(testDir, "bin");
  const tmuxSession = "codex-demo-abc123";
  const config = {
    port: 0,
    hostname: "gpu.example.com",
    bindingsPath: join(testDir, "bindings.json"),
  } as DaemonConfig;
  const sessionManager = new SessionManager(config);
  sessionManager.bind(tmuxSession, "topic-1");
  const promptOrigins = new PromptOriginTracker();
  const sent: Array<{ topicId?: string; text: string; sendAsUser?: boolean }> = [];
  const telegramProvider = {
    name: "telegram",
    send: async (options: {
      topicId?: string;
      text: string;
      sendAsUser?: boolean;
    }) => {
      sent.push(options);
      return true;
    },
  };
  const server = new Server(
    config,
    sessionManager,
    promptOrigins,
    telegramProvider,
    null,
    null
  );

  try {
    await server.start();
    const address = (server as any).httpServer.address() as AddressInfo;
    const relayHome = join(homeDir, ".vibelab-tools/agent-skills/relay");
    mkdirSync(relayHome, { recursive: true });
    writeFileSync(
      join(relayHome, "config.json"),
      JSON.stringify({ daemon: { port: address.port } })
    );
    mkdirSync(binDir, { recursive: true });
    const tmuxPath = join(binDir, "tmux");
    writeFileSync(tmuxPath, `#!/bin/sh\nprintf '%s' '${tmuxSession}'\n`);
    chmodSync(tmuxPath, 0o755);

    const remotePrompt = "sent from Feishu";
    promptOrigins.record(tmuxSession, remotePrompt);
    await runUserPromptHook(remotePrompt, homeDir, binDir);
    assert.deepEqual(sent, []);

    await runUserPromptHook("typed directly in Codex", homeDir, binDir);
    assert.deepEqual(sent, [
      { topicId: "topic-1", text: "🧑‍💻 typed directly in Codex" },
    ]);
  } finally {
    server.stop();
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("relays the current same-turn prompt's four images as the user without echoing them", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-prompt-image-test-"));
  const homeDir = join(testDir, "home");
  const binDir = join(testDir, "bin");
  const tmuxSession = "codex-demo-abc123";
  const turnId = "turn-image";
  const transcriptPath = join(testDir, "rollout.jsonl");
  const imagePaths = [1, 2, 3, 4].map((number) => join(testDir, `${number}.png`));
  const imageBytes = [
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGOwYWAAAAC4AD3NkQIBAAAAAElFTkSuQmCC",
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGOoYGAAAAFsAHlVrp32AAAAAElFTkSuQmCC",
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGPYwsAAAAIgALXzKThtAAAAAElFTkSuQmCC",
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4wMAAAALUAPHwgeM2AAAAAElFTkSuQmCC",
  ].map((value) => Buffer.from(value, "base64"));
  // These prompts differ only after the hook's 3000-byte display limit.
  const prefix = "[Image #1] [Image #2] " + "菜单".repeat(600);
  const previousPrompt = prefix + " previous submission";
  const currentPrompt = prefix + " [Image #3] [Image #4] current submission";
  const transcriptRecord = (prompt: string, paths: string[], recordTurnId = turnId) =>
    JSON.stringify({
      type: "event_msg",
      payload: {
        type: "item_completed",
        turn_id: recordTurnId,
        item: {
          type: "UserMessage",
          content: [
            ...paths.map((path) => ({ type: "local_image", path })),
            { type: "text", text: prompt },
          ],
        },
      },
    }) + "\n";
  const tokenPath = join(testDir, "feishu-user-token.json");
  writeTokenState(tokenPath, {
    access_token: "test-user-token",
    refresh_token: "test-refresh-token",
    expires_at: Date.now() + 60 * 60 * 1000,
  });
  const config = {
    port: 0,
    hostname: "gpu.example.com",
    feishuChatId: "oc_test",
    feishuUserTokenPath: tokenPath,
    bindingsPath: join(testDir, "bindings.json"),
  } as DaemonConfig;
  const sessionManager = new SessionManager(config);
  sessionManager.bindFeishu(tmuxSession, "om_session");
  const feishuProvider = new FeishuProvider(config);
  const received: string[] = [];
  feishuProvider.onMessage((message) => { received.push(message.id); });
  const uploaded: number[] = [];
  const replies: number[] = [];
  const echoedEvents: any[] = [];
  const texts: string[] = [];
  const failures: unknown[] = [];
  let downloads = 0;
  // Only the external Feishu service is simulated. The hook, HTTP server,
  // transcript reader, provider, user-token store, and SDK run normally.
  const feishuApi = createServer(async (req, res) => {
    try {
      const chunks: Buffer[] = [];
      for await (const chunk of req) chunks.push(Buffer.from(chunk));
      const body = Buffer.concat(chunks);
      let result: unknown;
      if (req.url === "/open-apis/auth/v3/tenant_access_token/internal") {
        result = { code: 0, tenant_access_token: "test-app-token", expire: 7200 };
      } else if (req.url === "/open-apis/im/v1/images") {
        assert.equal(req.headers.authorization, "Bearer test-app-token");
        const number = imageBytes.findIndex((image) => body.includes(image)) + 1;
        assert.ok(number > 0, "upload must contain an actual fixture PNG");
        uploaded.push(number);
        result = { code: 0, data: { image_key: `img_${number}` } };
      } else if (req.url === "/open-apis/im/v1/messages/om_session/reply") {
        assert.equal(req.headers.authorization, "Bearer test-user-token");
        const data = JSON.parse(body.toString());
        assert.equal(data.reply_in_thread, true);
        const content = JSON.parse(data.content);
        if (data.msg_type === "text") {
          texts.push(content.text);
          result = { code: 0, data: { message_id: "om_prompt" } };
        } else {
          assert.equal(data.msg_type, "post");
          const rows = content.zh_cn.content;
          const number = Number(rows[1][0].image_key.replace("img_", ""));
          assert.equal(rows[0][0].text, `[Image #${number}]`);
          const event = {
            sender: { sender_type: "user", sender_id: { open_id: "ou_test" } },
            message: {
              message_id: `om_image_${number}`,
              chat_id: "oc_test",
              chat_type: "group",
              message_type: "post",
              content: data.content,
              root_id: "om_session",
            },
          };
          // Feishu can deliver the event before its reply API responds.
          await (feishuProvider as any).handleIncomingMessage(event, "websocket");
          echoedEvents.push(event);
          replies.push(number);
          result = { code: 0, data: { message_id: event.message.message_id } };
        }
      } else {
        if (req.url?.includes("/resources/")) downloads += 1;
        throw new Error(`unexpected Feishu request: ${req.method} ${req.url}`);
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify(result));
    } catch (err) {
      failures.push(err);
      res.writeHead(500);
      res.end();
    }
  });
  const server = new Server(
    config,
    sessionManager,
    new PromptOriginTracker(),
    null,
    null,
    feishuProvider
  );

  try {
    await new Promise<void>((resolve) => feishuApi.listen(0, "127.0.0.1", resolve));
    const apiAddress = feishuApi.address() as AddressInfo;
    const httpInstance = axios.create({ proxy: false });
    httpInstance.interceptors.request.use((request) => {
      request.url = `http://127.0.0.1:${apiAddress.port}${new URL(request.url!).pathname}`;
      return request;
    });
    httpInstance.interceptors.response.use((response) => response.data);
    (feishuProvider as any).client = new lark.Client({
      appId: "test-app",
      appSecret: "test-app-secret",
      httpInstance: httpInstance as any,
      loggerLevel: lark.LoggerLevel.error,
    });
    await server.start();
    const address = (server as any).httpServer.address() as AddressInfo;
    const relayHome = join(homeDir, ".vibelab-tools/agent-skills/relay");
    mkdirSync(relayHome, { recursive: true });
    writeFileSync(
      join(relayHome, "config.json"),
      JSON.stringify({ daemon: { port: address.port } })
    );
    mkdirSync(binDir, { recursive: true });
    const tmuxPath = join(binDir, "tmux");
    writeFileSync(tmuxPath, `#!/bin/sh\nprintf '%s' '${tmuxSession}'\n`);
    chmodSync(tmuxPath, 0o755);
    imagePaths.forEach((path, index) => writeFileSync(path, imageBytes[index]));
    writeFileSync(transcriptPath,
      transcriptRecord(previousPrompt, imagePaths.slice(0, 2)) +
      transcriptRecord(currentPrompt, imagePaths.slice(2), "other-turn")
    );

    await runUserPromptHook(
      currentPrompt,
      homeDir,
      binDir,
      { transcript_path: transcriptPath, turn_id: turnId }
    );
    assert.deepEqual(texts, [Buffer.from(currentPrompt).subarray(0, 3000).toString()]);
    await new Promise((resolve) => setTimeout(resolve, 180));
    assert.deepEqual(uploaded, [], "must wait for this submission's transcript event");
    appendFileSync(transcriptPath, transcriptRecord(currentPrompt, imagePaths));
    await waitFor(() => replies.length === 4 || failures.length > 0);
    assert.deepEqual(failures, []);
    assert.deepEqual(uploaded, [1, 2, 3, 4]);
    assert.deepEqual(replies, [1, 2, 3, 4]);
    await waitFor(() => (feishuProvider as any).pendingUserImages.size === 0);
    for (const event of echoedEvents) {
      await (feishuProvider as any).handleIncomingMessage(event, "poll");
    }
    assert.equal(downloads, 0);
    assert.deepEqual(received, []);

    // An absent matching record must exhaust the bounded wait without using
    // the previous prompt's images.
    await runUserPromptHook(currentPrompt + " unavailable", homeDir, binDir,
      { transcript_path: transcriptPath, turn_id: turnId });
    await new Promise((resolve) => setTimeout(resolve, 1800));
    assert.deepEqual(uploaded, [1, 2, 3, 4]);
    assert.deepEqual(failures, []);
  } finally {
    server.stop();
    feishuProvider.disconnect();
    feishuApi.closeAllConnections();
    await new Promise<void>((resolve) => feishuApi.close(() => resolve()));
    rmSync(testDir, { recursive: true, force: true });
  }
});

function runUserPromptHook(
  prompt: string,
  homeDir: string,
  binDir: string,
  extraInput: Record<string, string> = {}
): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn("bash", [USER_PROMPT_HOOK], {
      env: {
        ...process.env,
        HOME: homeDir,
        PATH: `${binDir}:${process.env.PATH || ""}`,
        TMUX: "/tmp/tmux-test/default,1,0",
      },
      stdio: ["pipe", "ignore", "pipe"],
    });
    let stderr = "";
    child.stderr.setEncoding("utf8");
    child.stderr.on("data", (chunk) => {
      stderr += chunk;
    });
    child.on("error", reject);
    child.on("close", (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`hook exited ${code}: ${stderr}`));
      }
    });
    child.stdin.end(JSON.stringify({ prompt, ...extraInput }));
  });
}

async function waitFor(predicate: () => boolean): Promise<void> {
  const deadline = Date.now() + 3000;
  while (!predicate()) {
    if (Date.now() >= deadline) throw new Error("timed out waiting for prompt images");
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
}

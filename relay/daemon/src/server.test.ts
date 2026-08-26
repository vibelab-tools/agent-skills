// ABOUTME: Regression tests for relay notification routing.
// ABOUTME: Verifies Feishu notifications reuse one session topic with selective alerts.

import assert from "node:assert/strict";
import { chmodSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { spawn } from "node:child_process";
import test from "node:test";
import { SessionManager } from "./session-manager";
import { Server } from "./server";
import { DaemonConfig } from "./types";
import { PromptOriginTracker } from "./prompt-origin";

const USER_PROMPT_HOOK = join(
  __dirname,
  "../../plugins/relay/scripts/hook-user-prompt.sh"
);

test("reuses one Feishu topic and alerts only attention events", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-server-test-"));
  const config = {
    port: 0,
    hostname: "gpu.example.com",
    feishuChatId: "oc_test",
    bindingsPath: join(testDir, "bindings.json"),
  } as DaemonConfig;
  const sessionManager = new SessionManager(config);
  const created: Array<{ chatId: string; title: string }> = [];
  const sent: Array<{ topicId?: string; text: string; mentionAll?: boolean }> = [];
  const feishuProvider = {
    name: "feishu",
    sendNewRootMessage: async (chatId: string, title: string) => {
      created.push({ chatId, title });
      return "om_session";
    },
    send: async (options: { topicId?: string; text: string; mentionAll?: boolean }) => {
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
      { chatId: "oc_test", title: "🔗 gpu:codex:demo" },
    ]);
    assert.deepEqual(sent, [
      { topicId: "om_session", text: "first completion", mentionAll: true },
      { topicId: "om_session", text: "continue", mentionAll: false },
      { topicId: "om_session", text: "need input", mentionAll: true },
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
  const sent: Array<{ topicId?: string; text: string }> = [];
  const telegramProvider = {
    name: "telegram",
    send: async (options: { topicId?: string; text: string }) => {
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
      { topicId: "topic-1", text: "👤 typed directly in Codex" },
    ]);
  } finally {
    server.stop();
    rmSync(testDir, { recursive: true, force: true });
  }
});

function runUserPromptHook(
  prompt: string,
  homeDir: string,
  binDir: string
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
    child.stdin.end(JSON.stringify({ prompt }));
  });
}

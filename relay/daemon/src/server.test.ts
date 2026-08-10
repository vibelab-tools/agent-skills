// ABOUTME: Regression tests for relay notification routing.
// ABOUTME: Verifies Feishu notifications reuse one session topic with selective alerts.

import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { SessionManager } from "./session-manager";
import { Server } from "./server";
import { DaemonConfig } from "./types";

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
  };
  const server = new Server(
    config,
    sessionManager,
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
  } finally {
    server.stop();
    rmSync(testDir, { recursive: true, force: true });
  }
});

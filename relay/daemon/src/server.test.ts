// ABOUTME: Regression tests for relay notification routing.
// ABOUTME: Verifies each Feishu notification rotates to a fresh visible topic.

import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { AddressInfo } from "node:net";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { SessionManager } from "./session-manager";
import { Server } from "./server";
import { DaemonConfig } from "./types";

test("creates a fresh Feishu topic for every notification", async () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-server-test-"));
  const config = {
    port: 0,
    hostname: "gpu.example.com",
    feishuChatId: "oc_test",
    bindingsPath: join(testDir, "bindings.json"),
  } as DaemonConfig;
  const sessionManager = new SessionManager(config);
  const roots = ["om_new_1", "om_new_2"];
  const created: Array<{ chatId: string; title: string }> = [];
  const sent: Array<{ topicId?: string; text: string }> = [];
  const feishuProvider = {
    name: "feishu",
    sendNewRootMessage: async (chatId: string, title: string) => {
      created.push({ chatId, title });
      return roots[created.length - 1];
    },
    send: async (options: { topicId?: string; text: string }) => {
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

  sessionManager.bindFeishu("codex-demo-abc123", "om_old");

  try {
    await server.start();
    const address = (server as any).httpServer.address() as AddressInfo;

    for (const text of ["first completion", "second completion"]) {
      const response = await fetch(`http://127.0.0.1:${address.port}/notify`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: "stop",
          tmuxSession: "codex-demo-abc123",
          text,
        }),
      });
      assert.equal(response.status, 200);
    }

    assert.deepEqual(created, [
      { chatId: "oc_test", title: "🔗 gpu:codex:demo" },
      { chatId: "oc_test", title: "🔗 gpu:codex:demo" },
    ]);
    assert.deepEqual(sent, [
      { topicId: "om_new_1", text: "first completion" },
      { topicId: "om_new_2", text: "second completion" },
    ]);
    assert.equal(
      sessionManager.findByTmuxSession("codex-demo-abc123")?.feishuRootMessageId,
      "om_new_2"
    );
  } finally {
    server.stop();
    rmSync(testDir, { recursive: true, force: true });
  }
});

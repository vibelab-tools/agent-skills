// ABOUTME: Verifies persisted relay binding lifecycle and Feishu recovery cursors.
// ABOUTME: Covers live-session filtering, delayed cleanup, and restart-safe deduplication state.

import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { SessionManager } from "./session-manager";
import { DaemonConfig } from "./types";

const DAY_MS = 24 * 60 * 60 * 1000;

test("skips missing Feishu bindings and removes them only after the grace period", () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-session-test-"));
  const config = { bindingsPath: join(testDir, "bindings.json") } as DaemonConfig;

  try {
    const manager = new SessionManager(config);
    manager.bindFeishu("live", "om_live", 500);
    manager.bindFeishu("missing", "om_missing", 500);
    manager.initializeFeishuRecovery(1000);

    assert.deepEqual(
      manager.reconcileFeishuBindings(new Set(["live"]), 2000).map((item) => item.rootMessageId),
      ["om_live"]
    );
    assert.equal(manager.findByTmuxSession("missing")?.feishuMissingSince, 2000);

    manager.reconcileFeishuBindings(new Set(["live"]), 2000 + DAY_MS - 1);
    assert.ok(manager.findByTmuxSession("missing"));

    manager.reconcileFeishuBindings(new Set(["live"]), 2000 + DAY_MS);
    assert.equal(manager.findByTmuxSession("missing"), undefined);
    assert.ok(manager.findByTmuxSession("live"));
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("persists Feishu thread and cursor state across manager restarts", () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-session-test-"));
  const config = { bindingsPath: join(testDir, "bindings.json") } as DaemonConfig;

  try {
    const manager = new SessionManager(config);
    manager.bindFeishu("live", "om_live", 500);
    manager.initializeFeishuRecovery(1000);
    manager.setFeishuThread("om_live", "omt_thread");
    manager.rememberFeishuMessage("om_live", "om_first");
    manager.rememberFeishuMessage("om_live", "om_second");
    manager.advanceFeishuCursor("om_live", "om_first", 2000);
    manager.advanceFeishuCursor("om_live", "om_second", 2000);

    const reloaded = new SessionManager(config);
    const [target] = reloaded.reconcileFeishuBindings(new Set(["live"]), 3000);

    assert.equal(target.threadId, "omt_thread");
    assert.equal(target.lastMessageAt, 2000);
    assert.deepEqual(target.lastMessageIds, ["om_first", "om_second"]);
    assert.equal(reloaded.hasSeenFeishuMessage("om_live", "om_first"), true);
    assert.equal(reloaded.hasSeenFeishuMessage("om_live", "om_new"), false);
    assert.equal(reloaded.hasSeenFeishuMessage("om_live", "om_old"), false);
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});

test("expires only Feishu state from a binding shared with other providers", () => {
  const testDir = mkdtempSync(join(tmpdir(), "relay-session-test-"));
  const config = { bindingsPath: join(testDir, "bindings.json") } as DaemonConfig;

  try {
    const manager = new SessionManager(config);
    manager.bind("shared", "telegram-topic");
    manager.bindDingTalk("shared", "dingtalk-conversation");
    manager.bindFeishu("shared", "om_shared", 500);

    manager.reconcileFeishuBindings(new Set(), 1000);
    manager.reconcileFeishuBindings(new Set(), 1000 + DAY_MS);

    const binding = manager.findByTmuxSession("shared");
    assert.equal(binding?.topicId, "telegram-topic");
    assert.equal(binding?.dingtalkConversationId, "dingtalk-conversation");
    assert.equal(binding?.feishuRootMessageId, undefined);
  } finally {
    rmSync(testDir, { recursive: true, force: true });
  }
});

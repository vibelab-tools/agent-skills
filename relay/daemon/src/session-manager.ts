// ABOUTME: Manages tmux session to IM channel bindings.
// ABOUTME: Persists bindings to disk and provides lookup methods.

// 2026-03-17: Implement session binding management with file persistence
// 2026-03-18: Add DingTalk conversation binding support

import * as fs from "fs";
import * as path from "path";
import {
  SessionBinding,
  BindingsFile,
  DaemonConfig,
  FeishuRecoveryTarget,
} from "./types";

const FEISHU_BINDING_GRACE_MS = 24 * 60 * 60 * 1000;
const FEISHU_RECENT_MESSAGE_LIMIT = 500;

export class SessionManager {
  private bindings: SessionBinding[] = [];
  private config: DaemonConfig;

  constructor(config: DaemonConfig) {
    this.config = config;
    this.load();
  }

  /** Load bindings from disk */
  private load(): void {
    try {
      const data = fs.readFileSync(this.config.bindingsPath, "utf-8");
      const file: BindingsFile = JSON.parse(data);
      this.bindings = file.bindings || [];
    } catch {
      this.bindings = [];
    }
  }

  /** Persist bindings to disk */
  private save(): void {
    const dir = path.dirname(this.config.bindingsPath);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
    const file: BindingsFile = { bindings: this.bindings };
    fs.writeFileSync(this.config.bindingsPath, JSON.stringify(file, null, 2));
  }

  /** Bind a tmux session to a Telegram topic */
  bind(tmuxSession: string, topicId: string): void {
    const existing = this.bindings.find((b) => b.tmuxSession === tmuxSession);
    if (existing) {
      existing.topicId = topicId;
    } else {
      this.bindings.push({ tmuxSession, topicId, createdAt: Date.now() });
    }
    this.save();
  }

  // 2026-03-18: Bind DingTalk conversation to an existing tmux session binding
  /** Bind a DingTalk conversation ID to a tmux session */
  bindDingTalk(tmuxSession: string, conversationId: string): void {
    // 2026-03-18: Clear this conversationId from any other bindings first
    for (const b of this.bindings) {
      if (b.dingtalkConversationId === conversationId && b.tmuxSession !== tmuxSession) {
        delete b.dingtalkConversationId;
      }
    }
    const existing = this.bindings.find((b) => b.tmuxSession === tmuxSession);
    if (existing) {
      existing.dingtalkConversationId = conversationId;
    } else {
      this.bindings.push({
        tmuxSession,
        topicId: "",
        dingtalkConversationId: conversationId,
        createdAt: Date.now(),
      });
    }
    this.save();
  }

  /** Unbind a tmux session */
  unbind(tmuxSession: string): boolean {
    const before = this.bindings.length;
    this.bindings = this.bindings.filter((b) => b.tmuxSession !== tmuxSession);
    if (this.bindings.length < before) {
      this.save();
      return true;
    }
    return false;
  }

  /** Find binding by tmux session name */
  findByTmuxSession(tmuxSession: string): SessionBinding | undefined {
    return this.bindings.find((b) => b.tmuxSession === tmuxSession);
  }

  /** Find binding by Telegram topic ID */
  findByTopicId(topicId: string): SessionBinding | undefined {
    return this.bindings.find((b) => b.topicId === topicId);
  }

  // 2026-03-18: Lookup by DingTalk conversation ID
  /** Find binding by DingTalk conversation ID */
  findByDingtalkConversation(conversationId: string): SessionBinding | undefined {
    return this.bindings.find((b) => b.dingtalkConversationId === conversationId);
  }

  // 2026-03-18: Feishu root-message binding
  /** Bind a Feishu root message ID to a tmux session */
  bindFeishu(tmuxSession: string, rootMessageId: string, now = Date.now()): void {
    // Clear this rootMessageId from other bindings
    for (const b of this.bindings) {
      if (b.feishuRootMessageId === rootMessageId && b.tmuxSession !== tmuxSession) {
        delete b.feishuRootMessageId;
      }
    }
    const existing = this.bindings.find((b) => b.tmuxSession === tmuxSession);
    if (existing) {
      if (existing.feishuRootMessageId !== rootMessageId) {
        delete existing.feishuThreadId;
        existing.feishuLastMessageAt = now;
        existing.feishuLastMessageIds = [];
        existing.feishuRecentMessageIds = [];
      }
      existing.feishuRootMessageId = rootMessageId;
      delete existing.feishuMissingSince;
    } else {
      this.bindings.push({
        tmuxSession,
        topicId: "",
        feishuRootMessageId: rootMessageId,
        feishuLastMessageAt: now,
        feishuLastMessageIds: [],
        feishuRecentMessageIds: [],
        createdAt: now,
      });
    }
    this.save();
  }

  /** Initialize legacy Feishu bindings without replaying pre-upgrade history. */
  initializeFeishuRecovery(now = Date.now()): void {
    let changed = false;
    for (const binding of this.bindings) {
      if (!binding.feishuRootMessageId) continue;
      if (binding.feishuLastMessageAt === undefined) {
        binding.feishuLastMessageAt = now;
        changed = true;
      }
      if (!binding.feishuLastMessageIds) {
        binding.feishuLastMessageIds = [];
        changed = true;
      }
      if (!binding.feishuRecentMessageIds) {
        binding.feishuRecentMessageIds = [];
        changed = true;
      }
    }
    if (changed) this.save();
  }

  /** Return live Feishu bindings and age out continuously missing sessions. */
  reconcileFeishuBindings(
    activeSessions: Set<string>,
    now = Date.now(),
    graceMs = FEISHU_BINDING_GRACE_MS
  ): FeishuRecoveryTarget[] {
    const targets: FeishuRecoveryTarget[] = [];
    const retained: SessionBinding[] = [];
    let changed = false;

    for (const binding of this.bindings) {
      if (!binding.feishuRootMessageId) {
        retained.push(binding);
        continue;
      }

      if (activeSessions.has(binding.tmuxSession)) {
        if (binding.feishuMissingSince !== undefined) {
          delete binding.feishuMissingSince;
          changed = true;
        }
        targets.push({
          rootMessageId: binding.feishuRootMessageId,
          threadId: binding.feishuThreadId,
          lastMessageAt: binding.feishuLastMessageAt ?? now,
          lastMessageIds: [...(binding.feishuLastMessageIds || [])],
        });
        retained.push(binding);
        continue;
      }

      if (binding.feishuMissingSince === undefined) {
        binding.feishuMissingSince = now;
        changed = true;
      }
      if (now - binding.feishuMissingSince < graceMs) {
        retained.push(binding);
      } else {
        changed = true;
        delete binding.feishuRootMessageId;
        delete binding.feishuThreadId;
        delete binding.feishuLastMessageAt;
        delete binding.feishuLastMessageIds;
        delete binding.feishuRecentMessageIds;
        delete binding.feishuMissingSince;
        if (binding.topicId || binding.dingtalkConversationId) {
          retained.push(binding);
        }
      }
    }

    if (changed) {
      this.bindings = retained;
      this.save();
    }
    return targets;
  }

  setFeishuThread(rootMessageId: string, threadId: string): void {
    const binding = this.findByFeishuRootMessage(rootMessageId);
    if (!binding || binding.feishuThreadId === threadId) return;
    binding.feishuThreadId = threadId;
    this.save();
  }

  rememberFeishuMessage(rootMessageId: string, messageId: string): void {
    const binding = this.findByFeishuRootMessage(rootMessageId);
    if (!binding || !messageId) return;

    const ids = binding.feishuRecentMessageIds || [];
    if (ids.includes(messageId)) return;
    binding.feishuRecentMessageIds = [...ids, messageId].slice(-FEISHU_RECENT_MESSAGE_LIMIT);
    this.save();
  }

  advanceFeishuCursor(rootMessageId: string, messageId: string, createdAt: number): void {
    const binding = this.findByFeishuRootMessage(rootMessageId);
    if (!binding || !messageId || !Number.isFinite(createdAt)) return;

    const lastAt = binding.feishuLastMessageAt ?? 0;
    if (createdAt < lastAt) return;
    if (createdAt > lastAt) {
      binding.feishuLastMessageAt = createdAt;
      binding.feishuLastMessageIds = [messageId];
      this.save();
      return;
    }

    const ids = binding.feishuLastMessageIds || [];
    if (!ids.includes(messageId)) {
      binding.feishuLastMessageIds = [...ids, messageId];
      this.save();
    }
  }

  hasSeenFeishuMessage(rootMessageId: string, messageId: string): boolean {
    const binding = this.findByFeishuRootMessage(rootMessageId);
    if (!binding || !messageId) return false;
    return (binding.feishuRecentMessageIds || []).includes(messageId);
  }

  getFeishuBindingSummary(): { total: number; missing: number } {
    const feishu = this.bindings.filter((binding) => binding.feishuRootMessageId);
    return {
      total: feishu.length,
      missing: feishu.filter((binding) => binding.feishuMissingSince !== undefined).length,
    };
  }

  /** Find binding by Feishu root message ID */
  findByFeishuRootMessage(rootMessageId: string): SessionBinding | undefined {
    return this.bindings.find((b) => b.feishuRootMessageId === rootMessageId);
  }

  /** Get all bindings */
  getAll(): SessionBinding[] {
    return [...this.bindings];
  }
}

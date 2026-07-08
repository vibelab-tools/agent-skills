// ABOUTME: Manages tmux session to IM channel bindings.
// ABOUTME: Persists bindings to disk and provides lookup methods.

// 2026-03-17: Implement session binding management with file persistence
// 2026-03-18: Add DingTalk conversation binding support

import * as fs from "fs";
import * as path from "path";
import { SessionBinding, BindingsFile, DaemonConfig } from "./types";

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

  // 2026-03-18: Feishu thread binding
  /** Bind a Feishu root message ID to a tmux session */
  bindFeishu(tmuxSession: string, rootMessageId: string): void {
    // Clear this rootMessageId from other bindings
    for (const b of this.bindings) {
      if (b.feishuRootMessageId === rootMessageId && b.tmuxSession !== tmuxSession) {
        delete b.feishuRootMessageId;
      }
    }
    const existing = this.bindings.find((b) => b.tmuxSession === tmuxSession);
    if (existing) {
      existing.feishuRootMessageId = rootMessageId;
    } else {
      this.bindings.push({
        tmuxSession,
        topicId: "",
        feishuRootMessageId: rootMessageId,
        createdAt: Date.now(),
      });
    }
    this.save();
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

// ABOUTME: Shared type definitions for the VibeLab Relay daemon.
// ABOUTME: Covers configuration, session bindings, and message structures.

// 2026-03-17: Define core types for relay daemon

/** Daemon configuration loaded from environment variables */
export interface ProxyConfig {
  enabled: boolean;
  url: string;
}

export interface DaemonConfig {
  port: number;
  relayHome: string;
  workerUrl: string;
  machineId: string;
  hostname: string;
  telegramBotToken: string;
  telegramChatId: string;
  pollIntervalMs: number;
  bindingsPath: string;
  pidPath: string;
  logPath: string;
  // 2026-03-17: DingTalk enterprise app credentials for Stream API
  dingtalkClientId: string;
  dingtalkClientSecret: string;
  // 2026-03-18: Feishu enterprise app credentials for WebSocket API
  feishuAppId: string;
  feishuAppSecret: string;
  feishuChatId: string;
  telegramProxy: ProxyConfig;
  dingtalkProxy: ProxyConfig;
  feishuProxy: ProxyConfig;
}

/** A binding between a tmux session and IM channels */
export interface SessionBinding {
  tmuxSession: string;
  topicId: string;
  // 2026-03-18: Add DingTalk conversation ID for dual-platform binding
  dingtalkConversationId?: string;
  // 2026-03-18: Feishu thread root message ID for topic isolation
  feishuRootMessageId?: string;
  createdAt: number;
}

/** Persisted bindings file structure */
export interface BindingsFile {
  bindings: SessionBinding[];
}

// 2026-03-20: Attachment metadata for downloaded Feishu resources
export interface Attachment {
  filePath: string;
  fileName: string;
  mimeType?: string;
}

/** Message received from Worker poll endpoint */
export interface PollMessage {
  id: string;
  topicId: string;
  text: string;
  // 2026-03-20: Support file attachments from Feishu image/file/audio/media messages
  attachments?: Attachment[];
  from: {
    id: number;
    is_bot: boolean;
    first_name: string;
    username?: string;
  };
  timestamp: number;
}

/** POST /notify request from hook scripts */
export interface NotifyRequest {
  type: "stop" | "ask_user" | "user_prompt" | "info";
  tmuxSession?: string;
  text: string;
  transcriptPath?: string;
}

/** POST /bind request */
export interface BindRequest {
  tmuxSession: string;
  topicId?: string;
  // 2026-03-18: Support binding DingTalk conversation
  dingtalkConversationId?: string;
}

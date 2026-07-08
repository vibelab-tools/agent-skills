// ABOUTME: Shared type definitions for the VibeLab Relay Worker.
// ABOUTME: Covers KV schemas, API request/response shapes, and Telegram types.

// 2026-03-17: Define core types for relay Worker

export interface Env {
  RELAY_KV: KVNamespace;
}

/** Stored in KV under key `pending:{machineId}` */
export interface PendingMessages {
  messages: PendingMessage[];
}

export interface PendingMessage {
  id: string;
  topicId: string;
  text: string;
  from: TelegramFrom;
  timestamp: number;
}

/** Stored in KV under key `topic:{topicId}` */
export interface TopicBinding {
  machineId: string;
  tmuxSession: string;
  hostname: string;
}

/** Stored in KV under key `machine:{machineId}` */
export interface MachineState {
  hostname: string;
  sessions: string[];
  lastHeartbeat: number;
}

/** POST /api/notify request body */
export interface NotifyRequest {
  machineId: string;
  topicId: string;
  text: string;
  botToken: string;
  chatId: string;
  parseMode?: string;
}

/** POST /api/register request body */
export interface RegisterRequest {
  machineId: string;
  hostname: string;
  sessions: string[];
}

/** Telegram webhook update (partial) */
export interface TelegramUpdate {
  update_id: number;
  message?: TelegramMessage;
}

export interface TelegramMessage {
  message_id: number;
  message_thread_id?: number;
  from?: TelegramFrom;
  chat: TelegramChat;
  text?: string;
  date: number;
}

export interface TelegramFrom {
  id: number;
  is_bot: boolean;
  first_name: string;
  username?: string;
}

export interface TelegramChat {
  id: number;
  type: string;
  title?: string;
}

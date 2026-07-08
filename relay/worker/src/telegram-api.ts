// ABOUTME: Telegram Bot API wrapper for sending messages.
// ABOUTME: Handles message formatting and thread-based replies.

// 2026-03-17: Implement Telegram sendMessage via Bot API

const TELEGRAM_API_BASE = "https://api.telegram.org";

export interface SendMessageParams {
  botToken: string;
  chatId: string | number;
  text: string;
  messageThreadId?: number;
  parseMode?: string;
}

export interface TelegramApiResponse {
  ok: boolean;
  description?: string;
  result?: unknown;
}

/**
 * Send a text message via Telegram Bot API.
 * Supports thread-based replies via message_thread_id.
 */
export async function sendMessage(
  params: SendMessageParams
): Promise<TelegramApiResponse> {
  const { botToken, chatId, text, messageThreadId, parseMode } = params;
  const url = `${TELEGRAM_API_BASE}/bot${botToken}/sendMessage`;

  const body: Record<string, unknown> = {
    chat_id: chatId,
    text,
  };

  if (messageThreadId !== undefined) {
    body.message_thread_id = messageThreadId;
  }

  if (parseMode) {
    body.parse_mode = parseMode;
  }

  const resp = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  return resp.json() as Promise<TelegramApiResponse>;
}

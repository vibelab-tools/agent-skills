// ABOUTME: Telegram webhook handler for incoming messages.
// ABOUTME: Parses message_thread_id, looks up topic binding, queues to pending.

// 2026-03-17: Implement Telegram webhook ingestion

import { Env, TelegramUpdate, PendingMessages, PendingMessage, TopicBinding } from "../types";

/**
 * Handle POST /webhook/telegram
 * Receives Telegram updates, extracts topic info, appends to pending queue.
 */
export async function handleTelegramWebhook(
  request: Request,
  env: Env
): Promise<Response> {
  let update: TelegramUpdate;
  try {
    update = await request.json() as TelegramUpdate;
  } catch {
    return new Response(JSON.stringify({ error: "invalid JSON" }), { status: 400 });
  }

  const message = update.message;
  if (!message || !message.text) {
    // Ignore non-text messages (stickers, photos, etc.)
    return new Response(JSON.stringify({ ok: true, skipped: true }));
  }

  // Skip bot commands that start with /
  if (message.text.startsWith("/")) {
    return new Response(JSON.stringify({ ok: true, skipped: "command" }));
  }

  // Determine topic ID from message_thread_id
  const topicId = message.message_thread_id?.toString();
  if (!topicId) {
    // Messages outside topics are ignored
    return new Response(JSON.stringify({ ok: true, skipped: "no_topic" }));
  }

  // Look up which machine/session this topic is bound to
  const bindingJson = await env.RELAY_KV.get(`topic:${topicId}`);
  if (!bindingJson) {
    // Unbound topic, ignore
    return new Response(JSON.stringify({ ok: true, skipped: "unbound_topic" }));
  }

  const binding: TopicBinding = JSON.parse(bindingJson);
  const { machineId } = binding;

  // Build pending message
  const pendingMsg: PendingMessage = {
    id: `${update.update_id}`,
    topicId,
    text: message.text,
    from: message.from || { id: 0, is_bot: false, first_name: "Unknown" },
    timestamp: message.date,
  };

  // Append to pending queue (read-modify-write with KV)
  const pendingKey = `pending:${machineId}`;
  const existingJson = await env.RELAY_KV.get(pendingKey);
  const pending: PendingMessages = existingJson
    ? JSON.parse(existingJson)
    : { messages: [] };

  pending.messages.push(pendingMsg);

  await env.RELAY_KV.put(pendingKey, JSON.stringify(pending), {
    expirationTtl: 3600, // 1 hour TTL
  });

  return new Response(JSON.stringify({ ok: true, queued: true }));
}

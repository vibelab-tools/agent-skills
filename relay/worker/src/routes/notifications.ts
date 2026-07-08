// ABOUTME: Notification endpoint for daemon to send messages to Telegram.
// ABOUTME: Proxies message through Telegram Bot API with topic thread support.

// 2026-03-17: Implement notification relay to Telegram

import { Env, NotifyRequest } from "../types";
import { sendMessage } from "../telegram-api";

/**
 * Handle POST /api/notify
 * Sends a notification message to Telegram via Bot API.
 * Bot token is passed by daemon in the request (Worker stores no secrets).
 */
export async function handleNotify(
  request: Request,
  env: Env
): Promise<Response> {
  let body: NotifyRequest;
  try {
    body = await request.json() as NotifyRequest;
  } catch {
    return new Response(
      JSON.stringify({ error: "invalid JSON" }),
      { status: 400 }
    );
  }

  const { botToken, chatId, topicId, text, parseMode } = body;

  if (!botToken || !chatId || !text) {
    return new Response(
      JSON.stringify({ error: "botToken, chatId, and text are required" }),
      { status: 400 }
    );
  }

  const result = await sendMessage({
    botToken,
    chatId,
    text,
    messageThreadId: topicId ? parseInt(topicId, 10) : undefined,
    parseMode,
  });

  if (!result.ok) {
    return new Response(
      JSON.stringify({ error: "telegram API error", details: result.description }),
      { status: 502 }
    );
  }

  return new Response(JSON.stringify({ ok: true, result: result.result }));
}

/**
 * Handle POST /api/register
 * Daemon heartbeat registration - stores machine state.
 */
export async function handleRegister(
  request: Request,
  env: Env
): Promise<Response> {
  let body: { machineId: string; hostname: string; sessions: string[] };
  try {
    body = await request.json() as typeof body;
  } catch {
    return new Response(
      JSON.stringify({ error: "invalid JSON" }),
      { status: 400 }
    );
  }

  const { machineId, hostname, sessions } = body;
  if (!machineId || !hostname) {
    return new Response(
      JSON.stringify({ error: "machineId and hostname are required" }),
      { status: 400 }
    );
  }

  await env.RELAY_KV.put(
    `machine:${machineId}`,
    JSON.stringify({ hostname, sessions: sessions || [], lastHeartbeat: Date.now() }),
    { expirationTtl: 86400 } // 24h TTL
  );

  return new Response(JSON.stringify({ ok: true }));
}

/**
 * Handle POST /api/bind
 * Binds a Telegram topic to a machine/tmux session.
 */
export async function handleBind(
  request: Request,
  env: Env
): Promise<Response> {
  let body: { topicId: string; machineId: string; tmuxSession: string; hostname: string };
  try {
    body = await request.json() as typeof body;
  } catch {
    return new Response(
      JSON.stringify({ error: "invalid JSON" }),
      { status: 400 }
    );
  }

  const { topicId, machineId, tmuxSession, hostname } = body;
  if (!topicId || !machineId || !tmuxSession) {
    return new Response(
      JSON.stringify({ error: "topicId, machineId, and tmuxSession are required" }),
      { status: 400 }
    );
  }

  await env.RELAY_KV.put(
    `topic:${topicId}`,
    JSON.stringify({ machineId, tmuxSession, hostname: hostname || "" }),
    { expirationTtl: 604800 } // 7 day TTL
  );

  return new Response(JSON.stringify({ ok: true }));
}

/**
 * Handle DELETE /api/bind/:topicId
 * Unbinds a Telegram topic.
 */
export async function handleUnbind(
  topicId: string,
  env: Env
): Promise<Response> {
  if (!topicId) {
    return new Response(
      JSON.stringify({ error: "topicId is required" }),
      { status: 400 }
    );
  }

  await env.RELAY_KV.delete(`topic:${topicId}`);
  return new Response(JSON.stringify({ ok: true }));
}

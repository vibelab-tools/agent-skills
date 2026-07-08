// ABOUTME: Message polling endpoint for daemon to fetch pending messages.
// ABOUTME: Returns queued messages and clears the pending queue atomically.

// 2026-03-17: Implement poll endpoint for daemon message retrieval

import { Env, PendingMessages } from "../types";

/**
 * Handle GET /api/poll/:machineId
 * Returns pending messages for the given machine, then clears the queue.
 */
export async function handlePoll(
  machineId: string,
  env: Env
): Promise<Response> {
  if (!machineId) {
    return new Response(
      JSON.stringify({ error: "machineId is required" }),
      { status: 400 }
    );
  }

  const pendingKey = `pending:${machineId}`;
  const existingJson = await env.RELAY_KV.get(pendingKey);

  if (!existingJson) {
    return new Response(JSON.stringify({ messages: [] }));
  }

  const pending: PendingMessages = JSON.parse(existingJson);

  // Clear the queue after reading
  await env.RELAY_KV.delete(pendingKey);

  return new Response(JSON.stringify({ messages: pending.messages }));
}

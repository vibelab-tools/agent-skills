// ABOUTME: Cloudflare Worker entry point and request router for VibeLab Relay.
// ABOUTME: Dispatches to webhook, poll, notify, register, and bind endpoints.

// 2026-03-17: Implement main router for relay Worker

import { Env } from "./types";
import { handleTelegramWebhook } from "./routes/telegram-webhook";
import { handlePoll } from "./routes/messages";
import { handleNotify, handleRegister, handleBind, handleUnbind } from "./routes/notifications";

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const { pathname } = url;
    const method = request.method;

    // CORS headers for local daemon requests
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, POST, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
    };

    if (method === "OPTIONS") {
      return new Response(null, { headers: corsHeaders });
    }

    let response: Response;

    try {
      // POST /webhook/telegram
      if (method === "POST" && pathname === "/webhook/telegram") {
        response = await handleTelegramWebhook(request, env);
      }
      // GET /api/poll/:machineId
      else if (method === "GET" && pathname.startsWith("/api/poll/")) {
        const machineId = pathname.slice("/api/poll/".length);
        response = await handlePoll(machineId, env);
      }
      // POST /api/notify
      else if (method === "POST" && pathname === "/api/notify") {
        response = await handleNotify(request, env);
      }
      // POST /api/register
      else if (method === "POST" && pathname === "/api/register") {
        response = await handleRegister(request, env);
      }
      // POST /api/bind
      else if (method === "POST" && pathname === "/api/bind") {
        response = await handleBind(request, env);
      }
      // DELETE /api/bind/:topicId
      else if (method === "DELETE" && pathname.startsWith("/api/bind/")) {
        const topicId = pathname.slice("/api/bind/".length);
        response = await handleUnbind(topicId, env);
      }
      // Health check
      else if (method === "GET" && (pathname === "/" || pathname === "/health")) {
        response = new Response(
          JSON.stringify({ status: "ok", service: "vibelab-relay" })
        );
      }
      // 404
      else {
        response = new Response(
          JSON.stringify({ error: "not found" }),
          { status: 404 }
        );
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : "internal error";
      response = new Response(
        JSON.stringify({ error: message }),
        { status: 500 }
      );
    }

    // Apply CORS and JSON content type to all responses
    const newHeaders = new Headers(response.headers);
    newHeaders.set("Content-Type", "application/json");
    for (const [key, value] of Object.entries(corsHeaders)) {
      newHeaders.set(key, value);
    }

    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: newHeaders,
    });
  },
} satisfies ExportedHandler<Env>;

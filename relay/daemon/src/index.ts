// ABOUTME: Entry point for the VibeLab Relay daemon process.
// ABOUTME: Initializes server, poller, session manager, and handles graceful shutdown.

// 2026-03-17: Implement daemon entry point with lifecycle management

// 2026-03-17: Force IPv4 DNS resolution to avoid IPv6 timeout with Cloudflare
import * as dns from "dns";
dns.setDefaultResultOrder("ipv4first");

// 2026-04-21: Scrub ambient proxy env vars in-process. Providers may still use
// their own explicit provider proxy config, but shell proxy state must not leak into
// Feishu/DingTalk/Telegram by accident.
for (const key of ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "all_proxy"]) {
  delete process.env[key];
}

import * as fs from "fs";
import * as path from "path";
import { loadConfig } from "./config";
import { SessionManager } from "./session-manager";
import { Server } from "./server";
import { Poller } from "./poller";
import { TelegramProvider } from "./providers/telegram";
import { DingTalkProvider } from "./providers/dingtalk";
import { FeishuProvider } from "./providers/feishu";
import { IMProvider } from "./providers/base";
import { injectText, sessionExists } from "./tmux-injector";
import { requestJson } from "./http";
import { summarizeError } from "./proxy";
// 2026-03-20: Use pino for structured logging
import { createLogger } from "./logger";
import { Attachment } from "./types";

const log = createLogger("daemon");

async function main(): Promise<void> {
  const config = loadConfig();

  // Ensure relay directory exists
  const relayDir = path.dirname(config.pidPath);
  if (!fs.existsSync(relayDir)) {
    fs.mkdirSync(relayDir, { recursive: true });
  }

  // Write PID file
  fs.writeFileSync(config.pidPath, process.pid.toString());
  log.info({ pid: process.pid, pidPath: config.pidPath }, "PID file written");

  // Initialize components
  const sessionManager = new SessionManager(config);

  // 2026-03-17: Initialize all configured IM providers
  const providers: IMProvider[] = [];

  // Telegram provider (via Worker relay)
  const telegramProvider = new TelegramProvider(config);
  providers.push(telegramProvider);

  // DingTalk provider (via Stream API, direct connection)
  let dingtalkProvider: DingTalkProvider | null = null;
  if (config.dingtalkClientId && config.dingtalkClientSecret) {
    dingtalkProvider = new DingTalkProvider(config);
    // 2026-03-18: Route incoming DingTalk messages to tmux sessions via conversation binding
    dingtalkProvider.onMessage((msg, conversationId) => {
      const binding = sessionManager.findByDingtalkConversation(conversationId);
      if (!binding) {
        log.warn({ conversationId }, "No binding for DingTalk conversation");
        return;
      }
      if (!sessionExists(binding.tmuxSession)) {
        log.warn({ tmuxSession: binding.tmuxSession }, "tmux session not found");
        return;
      }
      log.info({ tmuxSession: binding.tmuxSession }, "Injecting DingTalk message");
      injectText(binding.tmuxSession, msg.text);
    });
    await dingtalkProvider.connect();
    providers.push(dingtalkProvider);
  }

  // 2026-03-18: Feishu provider (via WebSocket, thread-based topic isolation)
  let feishuProvider: FeishuProvider | null = null;
  if (config.feishuAppId && config.feishuAppSecret) {
    feishuProvider = new FeishuProvider(config);
    feishuProvider.onMessage((msg, rootMessageId) => {
      const binding = sessionManager.findByFeishuRootMessage(rootMessageId);
      if (!binding) {
        log.warn({ rootMessageId }, "No binding for Feishu thread");
        return;
      }
      if (!sessionExists(binding.tmuxSession)) {
        log.warn({ tmuxSession: binding.tmuxSession }, "tmux session not found");
        return;
      }
      // 2026-03-20: Build injection text with attachment file paths for agent sessions
      const injectionText = buildInjectionText(msg.text, msg.attachments);
      log.info({ tmuxSession: binding.tmuxSession }, "Injecting Feishu message");
      injectText(binding.tmuxSession, injectionText);
    });
    await feishuProvider.connect();
    providers.push(feishuProvider);
  }

  // Initialize server with all providers
  const server = new Server(config, sessionManager, telegramProvider, dingtalkProvider, feishuProvider);
  const poller = new Poller(config, sessionManager);

  // Start server and poller
  await server.start();
  poller.start();

  // Register with Worker
  registerWithWorker(config).catch((err) => {
    log.error({ err: summarizeError(err) }, "Worker registration failed");
  });

  // Graceful shutdown
  const shutdown = (): void => {
    log.info("Shutting down...");
    poller.stop();
    server.stop();
    if (dingtalkProvider) {
      dingtalkProvider.disconnect();
    }
    if (feishuProvider) {
      feishuProvider.disconnect();
    }
    cleanupPid(config.pidPath);
    process.exit(0);
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);

  log.info("Ready");
}

// 2026-03-20: Build tmux injection text that includes file paths for attachments
// NOTE: Must be single-line because tmux send-keys -l treats \n as Enter key
function buildInjectionText(text: string, attachments?: Attachment[]): string {
  const parts: string[] = [];
  if (text) {
    parts.push(text);
  }
  if (attachments && attachments.length > 0) {
    const fileList = attachments
      .map((a) => `${a.fileName} (${a.mimeType || "unknown type"}): ${a.filePath}`)
      .join(", ");
    parts.push(`[Attached files: ${fileList}]`);
  }
  return parts.join(" ") || "";
}

/** Register this machine with the Worker for heartbeat tracking */
async function registerWithWorker(config: ReturnType<typeof loadConfig>): Promise<void> {
  try {
    await requestJson(`${config.workerUrl}/api/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: {
        machineId: config.machineId,
        hostname: config.hostname,
        sessions: [],
      },
      proxy: config.telegramProxy,
    });
    log.info("Registered with Worker");
  } catch (err) {
    log.error({ err: summarizeError(err) }, "Worker registration error");
  }
}

/** Remove PID file on shutdown */
function cleanupPid(pidPath: string): void {
  try {
    fs.unlinkSync(pidPath);
  } catch {
    // Ignore if already removed
  }
}

main().catch((err) => {
  log.fatal({ err: summarizeError(err) }, "Fatal error");
  process.exit(1);
});

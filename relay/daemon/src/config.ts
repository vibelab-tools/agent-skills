// ABOUTME: Configuration loader for the relay daemon.
// ABOUTME: Reads from environment variables with sensible defaults.

// 2026-03-17: Implement config loading from environment

import * as os from "os";
import * as path from "path";
import * as crypto from "crypto";
import { DaemonConfig, ProxyConfig } from "./types";

/**
 * Generate a stable machine ID based on hostname.
 * Uses a hash to avoid exposing raw hostname in network requests.
 */
function generateMachineId(hostname: string): string {
  return crypto.createHash("sha256").update(hostname).digest("hex").slice(0, 16);
}

function parseBoolean(value: string | undefined, defaultValue = false): boolean {
  if (value === undefined || value === "") return defaultValue;
  return ["1", "true", "yes", "on"].includes(value.toLowerCase());
}

function loadProxyConfig(prefix: "TELEGRAM" | "DINGTALK" | "FEISHU"): ProxyConfig {
  const explicitUrl = process.env[`${prefix}_PROXY_URL`] || process.env[`${prefix}_PROXY`] || "";
  const host = process.env[`${prefix}_PROXY_HOST`] || "";
  const protocol = process.env[`${prefix}_PROXY_PROTOCOL`] || "http";
  const port = process.env[`${prefix}_PROXY_PORT`] || "";
  const username = process.env[`${prefix}_PROXY_USERNAME`] || "";
  const password = process.env[`${prefix}_PROXY_PASSWORD`] || "";

  let url = explicitUrl;
  if (!url && host) {
    const auth = username
      ? `${encodeURIComponent(username)}${password ? `:${encodeURIComponent(password)}` : ""}@`
      : "";
    url = `${protocol}://${auth}${host}${port ? `:${port}` : ""}`;
  }

  return {
    enabled: parseBoolean(process.env[`${prefix}_PROXY_ENABLED`]),
    url,
  };
}

export function loadConfig(): DaemonConfig {
  const hostname = os.hostname();
  const homeDir = os.homedir();
  const relayDir = process.env.RELAY_HOME || path.join(homeDir, ".vibe-coding-skill", "relay", "runtime");

  return {
    port: parseInt(process.env.RELAY_DAEMON_PORT || "3580", 10),
    relayHome: relayDir,
    workerUrl: process.env.RELAY_WORKER_URL || process.env.CLAUDE_RELAY_WORKER_URL || "",
    machineId: process.env.RELAY_MACHINE_ID || generateMachineId(hostname),
    hostname,
    telegramBotToken: process.env.TELEGRAM_BOT_TOKEN || "",
    telegramChatId: process.env.TELEGRAM_CHAT_ID || "",
    pollIntervalMs: parseInt(process.env.RELAY_POLL_INTERVAL_MS || "1000", 10),
    bindingsPath: path.join(relayDir, "bindings.json"),
    pidPath: path.join(relayDir, "daemon.pid"),
    logPath: path.join(relayDir, "daemon.log"),
    // 2026-03-17: DingTalk enterprise app credentials
    dingtalkClientId: process.env.DINGTALK_CLIENT_ID || "",
    dingtalkClientSecret: process.env.DINGTALK_CLIENT_SECRET || "",
    // 2026-03-18: Feishu enterprise app credentials
    feishuAppId: process.env.FEISHU_APP_ID || "",
    feishuAppSecret: process.env.FEISHU_APP_SECRET || "",
    feishuChatId: process.env.FEISHU_CHAT_ID || "",
    telegramProxy: loadProxyConfig("TELEGRAM"),
    dingtalkProxy: loadProxyConfig("DINGTALK"),
    feishuProxy: loadProxyConfig("FEISHU"),
  };
}

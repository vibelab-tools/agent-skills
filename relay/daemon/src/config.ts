// ABOUTME: Configuration loader for the relay daemon.
// ABOUTME: Reads the service-local structured config with legacy migration fallbacks.

// 2026-03-17: Implement config loading from environment

import * as os from "os";
import * as path from "path";
import * as crypto from "crypto";
import * as fs from "fs";
import { AlertConfig, DaemonConfig, ProxyConfig } from "./types";

/**
 * Generate a stable machine ID based on hostname.
 * Uses a hash to avoid exposing raw hostname in network requests.
 */
function generateMachineId(hostname: string): string {
  return crypto.createHash("sha256").update(hostname).digest("hex").slice(0, 16);
}

type RawProxyConfig = {
  enabled?: boolean | string;
  url?: string;
  protocol?: string;
  host?: string;
  port?: string | number;
  username?: string;
  password?: string;
};

type RawRelayConfig = {
  alerts?: unknown;
  daemon?: {
    port?: number | string;
    machine_id?: string;
    poll_interval_ms?: number | string;
  };
  worker?: {
    url?: string;
  };
  telegram?: {
    bot_token?: string;
    chat_id?: string;
    proxy?: RawProxyConfig;
  };
  dingtalk?: {
    client_id?: string;
    client_secret?: string;
    proxy?: RawProxyConfig;
  };
  feishu?: {
    app_id?: string;
    app_secret?: string;
    chat_id?: string;
    proxy?: RawProxyConfig;
  };
  env?: Record<string, string>;
};

function parseBoolean(value: boolean | string | undefined, defaultValue = false): boolean {
  if (value === undefined || value === "") return defaultValue;
  if (typeof value === "boolean") return value;
  return ["1", "true", "yes", "on"].includes(value.toLowerCase());
}

function parseNumber(value: number | string | undefined, defaultValue: number): number {
  if (value === undefined || value === "") return defaultValue;
  const parsed = typeof value === "number" ? value : parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : defaultValue;
}

function readJson(filePath: string): RawRelayConfig {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    return {};
  }
}

function serviceRoot(): string {
  return path.resolve(__dirname, "..", "..");
}

function loadRawConfig(): RawRelayConfig {
  return readJson(path.join(serviceRoot(), "config.json"));
}

function legacyEnv(raw: RawRelayConfig, key: string): string {
  return raw.env?.[key] || "";
}

function loadProxyConfig(rawProxy: RawProxyConfig | undefined, legacy: RawRelayConfig, prefix: "TELEGRAM" | "DINGTALK" | "FEISHU"): ProxyConfig {
  const explicitUrl = rawProxy?.url || legacyEnv(legacy, `${prefix}_PROXY_URL`) || legacyEnv(legacy, `${prefix}_PROXY`) || "";
  const host = rawProxy?.host || legacyEnv(legacy, `${prefix}_PROXY_HOST`) || "";
  const protocol = rawProxy?.protocol || legacyEnv(legacy, `${prefix}_PROXY_PROTOCOL`) || "http";
  const port = rawProxy?.port?.toString() || legacyEnv(legacy, `${prefix}_PROXY_PORT`) || "";
  const username = rawProxy?.username || legacyEnv(legacy, `${prefix}_PROXY_USERNAME`) || "";
  const password = rawProxy?.password || legacyEnv(legacy, `${prefix}_PROXY_PASSWORD`) || "";

  let url = explicitUrl;
  if (!url && host) {
    const auth = username
      ? `${encodeURIComponent(username)}${password ? `:${encodeURIComponent(password)}` : ""}@`
      : "";
    url = `${protocol}://${auth}${host}${port ? `:${port}` : ""}`;
  }

  return {
    enabled: parseBoolean(rawProxy?.enabled ?? legacyEnv(legacy, `${prefix}_PROXY_ENABLED`)),
    url,
  };
}

export function loadConfig(): DaemonConfig {
  const raw = loadRawConfig();
  const hostname = os.hostname();
  const relayDir = path.join(serviceRoot(), "runtime");

  return {
    alerts: parseAlertConfig(raw.alerts),
    port: parseNumber(raw.daemon?.port ?? legacyEnv(raw, "RELAY_DAEMON_PORT"), 3580),
    relayHome: relayDir,
    workerUrl: raw.worker?.url || legacyEnv(raw, "RELAY_WORKER_URL") || legacyEnv(raw, "CLAUDE_RELAY_WORKER_URL") || "",
    machineId: raw.daemon?.machine_id || legacyEnv(raw, "RELAY_MACHINE_ID") || generateMachineId(hostname),
    hostname,
    telegramBotToken: raw.telegram?.bot_token || legacyEnv(raw, "TELEGRAM_BOT_TOKEN") || "",
    telegramChatId: raw.telegram?.chat_id || legacyEnv(raw, "TELEGRAM_CHAT_ID") || "",
    pollIntervalMs: parseNumber(raw.daemon?.poll_interval_ms ?? legacyEnv(raw, "RELAY_POLL_INTERVAL_MS"), 1000),
    bindingsPath: path.join(relayDir, "bindings.json"),
    pidPath: path.join(relayDir, "daemon.pid"),
    logPath: path.join(relayDir, "daemon.log"),
    // 2026-03-17: DingTalk enterprise app credentials
    dingtalkClientId: raw.dingtalk?.client_id || legacyEnv(raw, "DINGTALK_CLIENT_ID") || "",
    dingtalkClientSecret: raw.dingtalk?.client_secret || legacyEnv(raw, "DINGTALK_CLIENT_SECRET") || "",
    // 2026-03-18: Feishu enterprise app credentials
    feishuAppId: raw.feishu?.app_id || legacyEnv(raw, "FEISHU_APP_ID") || "",
    feishuAppSecret: raw.feishu?.app_secret || legacyEnv(raw, "FEISHU_APP_SECRET") || "",
    feishuChatId: raw.feishu?.chat_id || legacyEnv(raw, "FEISHU_CHAT_ID") || "",
    feishuUserTokenPath: path.join(relayDir, "feishu-user-token.json"),
    telegramProxy: loadProxyConfig(raw.telegram?.proxy, raw, "TELEGRAM"),
    dingtalkProxy: loadProxyConfig(raw.dingtalk?.proxy, raw, "DINGTALK"),
    feishuProxy: loadProxyConfig(raw.feishu?.proxy, raw, "FEISHU"),
  };
}

/** An explicit alert block must be complete; never silently enable broad defaults. */
export function parseAlertConfig(raw: unknown): AlertConfig | undefined {
  if (raw === undefined) return undefined;
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) throw new Error("alerts must be an object");
  const value = raw as Record<string, unknown>;
  const keys = ["host", "port", "signing_secret", "tmux_session", "environment", "base_url", "policy"];
  if (Object.keys(value).some((key) => !keys.includes(key))) throw new Error("Unknown alerts configuration field");
  for (const key of keys.filter((key) => key !== "port")) {
    if (typeof value[key] !== "string" || !(value[key] as string).trim()) throw new Error(`alerts.${key} is required`);
  }
  if (!Number.isInteger(value.port) || (value.port as number) < 1 || (value.port as number) > 65535) throw new Error("Invalid alerts.port");
  // Bind only literal loopback/private addresses, never wildcard/public/DNS names.
  const host = value.host as string;
  const octets = host.split(".");
  const ipv4 = octets.length === 4 && octets.every((part) => /^\d{1,3}$/.test(part) && Number(part) <= 255);
  if (!ipv4 || !(octets[0] === "127" || octets[0] === "10" || (octets[0] === "172" && Number(octets[1]) >= 16 && Number(octets[1]) <= 31) || (octets[0] === "192" && octets[1] === "168"))) throw new Error("alerts.host must be a literal private or loopback IPv4 address");
  if ((value.signing_secret as string).length < 32 || (value.signing_secret as string).length > 1024) throw new Error("alerts.signing_secret must contain 32 to 1024 characters");
  if (!/^[a-zA-Z0-9_-]{1,128}$/.test(value.tmux_session as string)) throw new Error("Invalid alerts.tmux_session");
  if ((value.environment as string).length > 64 || /[\p{Cc}\p{Cf}]/u.test(value.environment as string)) throw new Error("Invalid alerts.environment");
  if ((value.policy as string).length > 16000) throw new Error("alerts.policy is too long");
  let url: URL;
  try { url = new URL(value.base_url as string); } catch { throw new Error("Invalid alerts.base_url"); }
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash || url.pathname !== "/") throw new Error("alerts.base_url must be an HTTP(S) origin");
  return { ...value, base_url: url.origin } as unknown as AlertConfig;
}

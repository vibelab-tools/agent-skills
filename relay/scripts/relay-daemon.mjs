#!/usr/bin/env node
// ABOUTME: Cross-tool daemon launcher for the relay service.
// ABOUTME: Loads relay env from Codex/Claude settings, scrubs ambient proxies, then execs the daemon.

import { spawn } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const binDir = path.dirname(scriptPath);
const serviceRoot = path.dirname(binDir);
const daemonDir = process.env.RELAY_DAEMON_DIR || path.join(serviceRoot, "daemon");
const runtimeDir = process.env.RELAY_HOME || path.join(serviceRoot, "runtime");

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    return {};
  }
}

function envFromJson(filePath) {
  const data = readJson(filePath);
  return data.env && typeof data.env === "object" ? data.env : data;
}

const homeDir = os.homedir();
const settingsEnv = {
  ...envFromJson(path.join(homeDir, ".claude", "settings.json")),
  ...envFromJson(path.join(homeDir, ".codex", "relay-settings.json")),
  ...process.env,
};

const proxySuffixes = ["ENABLED", "URL", "PROTOCOL", "HOST", "PORT", "USERNAME", "PASSWORD"];
const proxyKeys = proxySuffixes.flatMap((suffix) =>
  ["TELEGRAM", "DINGTALK", "FEISHU"].map((provider) => `${provider}_PROXY_${suffix}`)
);
const relayKeys = [
  "CLAUDE_RELAY_WORKER_URL",
  "RELAY_WORKER_URL",
  "RELAY_DAEMON_PORT",
  "RELAY_HOME",
  "RELAY_MACHINE_ID",
  "RELAY_POLL_INTERVAL_MS",
  "LOG_LEVEL",
  "TELEGRAM_BOT_TOKEN",
  "TELEGRAM_CHAT_ID",
  "DINGTALK_CLIENT_ID",
  "DINGTALK_CLIENT_SECRET",
  "FEISHU_APP_ID",
  "FEISHU_APP_SECRET",
  "FEISHU_CHAT_ID",
  ...proxyKeys,
];

const relayEnv = {};
for (const key of relayKeys) {
  if (settingsEnv[key]) {
    relayEnv[key] = settingsEnv[key];
  }
}
relayEnv.RELAY_HOME = runtimeDir;
if (!relayEnv.CLAUDE_RELAY_WORKER_URL && relayEnv.RELAY_WORKER_URL) {
  relayEnv.CLAUDE_RELAY_WORKER_URL = relayEnv.RELAY_WORKER_URL;
}

const env = { ...process.env, ...relayEnv };
for (const key of ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "all_proxy"]) {
  delete env[key];
}

fs.mkdirSync(runtimeDir, { recursive: true });

const daemonEntry = path.join(daemonDir, "dist", "index.js");
if (!fs.existsSync(daemonEntry)) {
  console.error(`relay daemon entry not found: ${daemonEntry}`);
  process.exit(2);
}

const child = spawn(process.execPath, ["--dns-result-order=ipv4first", daemonEntry], {
  cwd: daemonDir,
  env,
  stdio: "inherit",
});

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => {
    child.kill(signal);
  });
}

child.on("exit", (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal);
    return;
  }
  process.exit(code ?? 0);
});

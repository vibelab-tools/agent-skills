#!/usr/bin/env node
// ABOUTME: Merges relay install defaults into the service-local structured config.
// ABOUTME: Migrates legacy env-style relay settings without printing secrets.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const serviceRoot = process.argv[2];
if (!serviceRoot) {
  console.error("usage: merge-relay-config.mjs <service-root>");
  process.exit(2);
}

const configPath = path.join(serviceRoot, "config.json");
const homeDir = os.homedir();

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

function truthy(value) {
  if (value === undefined || value === "") return undefined;
  if (typeof value === "boolean") return value;
  return ["1", "true", "yes", "on"].includes(String(value).toLowerCase());
}

function compact(value) {
  if (Array.isArray(value)) {
    return value.map(compact);
  }
  if (!value || typeof value !== "object") {
    return value;
  }
  const result = {};
  for (const [key, child] of Object.entries(value)) {
    const compacted = compact(child);
    if (compacted === undefined || compacted === "") continue;
    if (compacted && typeof compacted === "object" && !Array.isArray(compacted) && Object.keys(compacted).length === 0) {
      continue;
    }
    result[key] = compacted;
  }
  return result;
}

function mergeDeep(...objects) {
  const result = {};
  for (const object of objects) {
    if (!object || typeof object !== "object") continue;
    for (const [key, value] of Object.entries(object)) {
      if (
        value &&
        typeof value === "object" &&
        !Array.isArray(value) &&
        result[key] &&
        typeof result[key] === "object" &&
        !Array.isArray(result[key])
      ) {
        result[key] = mergeDeep(result[key], value);
      } else if (value !== undefined && value !== "") {
        result[key] = value;
      }
    }
  }
  return result;
}

function proxyFromEnv(env, prefix) {
  const splitProxy = {
    protocol: env[`${prefix}_PROXY_PROTOCOL`],
    host: env[`${prefix}_PROXY_HOST`],
    port: env[`${prefix}_PROXY_PORT`],
    username: env[`${prefix}_PROXY_USERNAME`],
    password: env[`${prefix}_PROXY_PASSWORD`],
  };
  return compact({
    enabled: truthy(env[`${prefix}_PROXY_ENABLED`]),
    url: env[`${prefix}_PROXY_URL`] || env[`${prefix}_PROXY`],
    ...splitProxy,
  });
}

function fromEnv(env) {
  return compact({
    daemon: {
      port: env.RELAY_DAEMON_PORT,
      machine_id: env.RELAY_MACHINE_ID,
      poll_interval_ms: env.RELAY_POLL_INTERVAL_MS,
    },
    worker: {
      url: env.RELAY_WORKER_URL || env.CLAUDE_RELAY_WORKER_URL,
    },
    telegram: {
      bot_token: env.TELEGRAM_BOT_TOKEN,
      chat_id: env.TELEGRAM_CHAT_ID,
      proxy: proxyFromEnv(env, "TELEGRAM"),
    },
    dingtalk: {
      client_id: env.DINGTALK_CLIENT_ID,
      client_secret: env.DINGTALK_CLIENT_SECRET,
      proxy: proxyFromEnv(env, "DINGTALK"),
    },
    feishu: {
      app_id: env.FEISHU_APP_ID,
      app_secret: env.FEISHU_APP_SECRET,
      chat_id: env.FEISHU_CHAT_ID,
      proxy: proxyFromEnv(env, "FEISHU"),
    },
  });
}

function normalizeConfig(data) {
  if (data.env && typeof data.env === "object") {
    return fromEnv(data.env);
  }
  return compact(data);
}

const config = compact(
  mergeDeep(
    {
      daemon: {
        port: 3580,
      },
      telegram: {
        proxy: { enabled: false },
      },
      dingtalk: {
        proxy: { enabled: false },
      },
      feishu: {
        proxy: { enabled: false },
      },
    },
    fromEnv(envFromJson(path.join(homeDir, ".codex", "relay-settings.json"))),
    fromEnv(envFromJson(path.join(homeDir, ".claude", "settings.json"))),
    normalizeConfig(readJson(configPath))
  )
);

if (config.daemon && typeof config.daemon === "object") {
  delete config.daemon.runtime_dir;
  if (typeof config.daemon.port === "string" && /^\d+$/.test(config.daemon.port)) {
    config.daemon.port = Number(config.daemon.port);
  }
  if (typeof config.daemon.poll_interval_ms === "string" && /^\d+$/.test(config.daemon.poll_interval_ms)) {
    config.daemon.poll_interval_ms = Number(config.daemon.poll_interval_ms);
  }
}

fs.mkdirSync(serviceRoot, { recursive: true, mode: 0o700 });
fs.writeFileSync(configPath, JSON.stringify(config, null, 2) + "\n", { mode: 0o600 });
fs.chmodSync(configPath, 0o600);

function configuredPaths(object, prefix = "") {
  return Object.entries(object).flatMap(([key, value]) => {
    const next = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === "object" && !Array.isArray(value)) {
      return configuredPaths(value, next);
    }
    return value === undefined || value === "" ? [] : [next];
  });
}

console.log(
  JSON.stringify(
    {
      written: configPath,
      keysPresent: configuredPaths(config).sort(),
    },
    null,
    2
  )
);

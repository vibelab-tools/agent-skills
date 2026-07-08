#!/usr/bin/env node
// ABOUTME: Merges relay install defaults into ~/.codex/relay-settings.json.
// ABOUTME: Preserves existing secrets and only sets runtime path metadata.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const runtimeDir = process.argv[2];
if (!runtimeDir) {
  console.error("usage: merge-codex-relay-settings.mjs <runtime-dir>");
  process.exit(2);
}

const codexDir = path.join(os.homedir(), ".codex");
const settingsPath = path.join(codexDir, "relay-settings.json");

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    return {};
  }
}

const settings = readJson(settingsPath);
const env = { ...(settings.env || {}) };
env.RELAY_HOME = runtimeDir;
env.TELEGRAM_PROXY_ENABLED ??= "false";
env.DINGTALK_PROXY_ENABLED ??= "false";
env.FEISHU_PROXY_ENABLED ??= "false";
delete env.FEISHU_PROXY;
delete env.DINGTALK_PROXY;

fs.mkdirSync(codexDir, { recursive: true, mode: 0o700 });
fs.writeFileSync(settingsPath, JSON.stringify({ ...settings, env }, null, 2) + "\n", {
  mode: 0o600,
});
fs.chmodSync(settingsPath, 0o600);

console.log(
  JSON.stringify(
    {
      written: settingsPath,
      keysPresent: Object.keys(env)
        .filter((key) => Boolean(env[key]))
        .sort(),
    },
    null,
    2
  )
);

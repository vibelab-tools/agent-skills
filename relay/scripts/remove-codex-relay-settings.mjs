#!/usr/bin/env node
// ABOUTME: Removes installer-owned relay defaults from ~/.codex/relay-settings.json.
// ABOUTME: Preserves existing relay secrets and other user-provided configuration.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const runtimeDir = process.argv[2];
if (!runtimeDir) {
  console.error("usage: remove-codex-relay-settings.mjs <runtime-dir>");
  process.exit(2);
}

const settingsPath = path.join(os.homedir(), ".codex", "relay-settings.json");

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch {
    return null;
  }
}

const settings = readJson(settingsPath);
if (!settings || typeof settings !== "object") {
  console.log(JSON.stringify({ changed: false, reason: "missing settings" }, null, 2));
  process.exit(0);
}

const env = settings.env && typeof settings.env === "object" ? settings.env : {};
const removed = env.RELAY_HOME === runtimeDir;
if (removed) {
  delete env.RELAY_HOME;
  fs.writeFileSync(settingsPath, JSON.stringify({ ...settings, env }, null, 2) + "\n", {
    mode: 0o600,
  });
  fs.chmodSync(settingsPath, 0o600);
}

console.log(
  JSON.stringify(
    {
      changed: removed,
      removedKeys: removed ? ["RELAY_HOME"] : [],
      preservedKeys: Object.keys(env)
        .filter((key) => Boolean(env[key]))
        .sort(),
    },
    null,
    2
  )
);

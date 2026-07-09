#!/usr/bin/env node
// ABOUTME: Removes the legacy Codex relay settings file after service-local migration.
// ABOUTME: Keeps relay configuration from being split across tool-specific homes.

import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const settingsPath = path.join(os.homedir(), ".codex", "relay-settings.json");

if (!fs.existsSync(settingsPath)) {
  console.log(JSON.stringify({ changed: false, reason: "missing legacy settings" }, null, 2));
  process.exit(0);
}

fs.rmSync(settingsPath, { force: true });
console.log(
  JSON.stringify(
    {
      changed: true,
      removed: settingsPath,
    },
    null,
    2
  )
);

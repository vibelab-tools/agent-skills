#!/usr/bin/env node
// ABOUTME: Cross-tool daemon launcher for the relay service.
// ABOUTME: Scrubs ambient proxies, then execs the daemon against service-local config.

import { spawn } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptPath = fileURLToPath(import.meta.url);
const binDir = path.dirname(scriptPath);
const serviceRoot = path.dirname(binDir);
const daemonDir = path.join(serviceRoot, "daemon");

const env = { ...process.env };
for (const key of ["http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "all_proxy"]) {
  delete env[key];
}

fs.mkdirSync(path.join(serviceRoot, "runtime"), { recursive: true });

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

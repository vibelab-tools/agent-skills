#!/usr/bin/env node
// ABOUTME: Installs and controls the relay daemon as a platform-native user service.
// ABOUTME: Uses launchd on macOS, systemd --user on Linux, and Task Scheduler on Windows.

import { execFileSync, spawnSync } from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";

const action = process.argv[2] || "status";
const serviceRoot = path.resolve(process.argv[3] || path.join(os.homedir(), ".vibelab-tools", "agent-skills", "relay"));
const label = "tools.vibelab.agent-skills.relay";
const systemdUnit = "vibelab-agent-skills-relay.service";
const windowsTaskName = "VibeLabAgentSkillsRelay";
const runtimeDir = path.join(serviceRoot, "runtime");
const binPath = path.join(serviceRoot, "bin", "relay-daemon.mjs");
const nodePath = process.execPath;
const darwinBundleId = label;
const darwinAppName = "VibeLab Relay";

function ensureDirs() {
  fs.mkdirSync(runtimeDir, { recursive: true });
  fs.mkdirSync(path.join(serviceRoot, "bin"), { recursive: true });
}

function run(command, args, options = {}) {
  return execFileSync(command, args, { stdio: "inherit", ...options });
}

function tryRun(command, args, options = {}) {
  return spawnSync(command, args, { stdio: "inherit", ...options });
}

function sleepMs(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}

function isDarwinLoaded(uid) {
  const result = spawnSync("launchctl", ["print", `gui/${uid}/${label}`], { stdio: "ignore" });
  return result.status === 0;
}

function waitForDarwinUnload(uid) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (!isDarwinLoaded(uid)) {
      return true;
    }
    sleepMs(100);
  }
  return !isDarwinLoaded(uid);
}

function xmlEscape(value) {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function writeDarwinAppLauncher() {
  const appRoot = path.join(serviceRoot, `${darwinAppName}.app`);
  const contentsDir = path.join(appRoot, "Contents");
  const macOSDir = path.join(contentsDir, "MacOS");
  const resourcesDir = path.join(contentsDir, "Resources");
  const executablePath = path.join(macOSDir, darwinAppName);
  fs.mkdirSync(macOSDir, { recursive: true });
  fs.mkdirSync(resourcesDir, { recursive: true });

  const infoPlist = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDisplayName</key>
  <string>${darwinAppName}</string>
  <key>CFBundleExecutable</key>
  <string>${darwinAppName}</string>
  <key>CFBundleIdentifier</key>
  <string>${darwinBundleId}</string>
  <key>CFBundleName</key>
  <string>${darwinAppName}</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
</dict>
</plist>
`;
  fs.writeFileSync(path.join(contentsDir, "Info.plist"), infoPlist, { mode: 0o644 });

  const launcher = `#!/bin/sh
exec ${JSON.stringify(nodePath)} ${JSON.stringify(binPath)}
`;
  fs.writeFileSync(executablePath, launcher, { mode: 0o755 });
  tryRun("codesign", ["--force", "--deep", "--sign", "-", appRoot], { stdio: "ignore" });
  return executablePath;
}

function installDarwin() {
  ensureDirs();
  const uid = process.getuid();
  const agentsDir = path.join(os.homedir(), "Library", "LaunchAgents");
  const plistPath = path.join(agentsDir, `${label}.plist`);
  const launcherPath = writeDarwinAppLauncher();
  fs.mkdirSync(agentsDir, { recursive: true });
  const plist = `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${label}</string>
  <key>AssociatedBundleIdentifiers</key>
  <array>
    <string>${darwinBundleId}</string>
  </array>
  <key>ProgramArguments</key>
  <array>
    <string>${xmlEscape(launcherPath)}</string>
  </array>
  <key>WorkingDirectory</key>
  <string>${xmlEscape(path.join(serviceRoot, "daemon"))}</string>
  <key>RunAtLoad</key>
  <true/>
  <key>KeepAlive</key>
  <true/>
  <key>StandardOutPath</key>
  <string>${xmlEscape(path.join(runtimeDir, "daemon.log"))}</string>
  <key>StandardErrorPath</key>
  <string>${xmlEscape(path.join(runtimeDir, "daemon-error.log"))}</string>
</dict>
</plist>
  `;
  fs.writeFileSync(plistPath, plist, { mode: 0o644 });
  tryRun("launchctl", ["bootout", `gui/${uid}/${label}`], { stdio: "ignore" });
  tryRun("launchctl", ["bootout", `gui/${uid}`, plistPath], { stdio: "ignore" });
  waitForDarwinUnload(uid);
  tryRun("launchctl", ["enable", `gui/${uid}/${label}`], { stdio: "ignore" });
  const bootstrap = tryRun("launchctl", ["bootstrap", `gui/${uid}`, plistPath]);
  if (bootstrap.status !== 0 && !isDarwinLoaded(uid)) {
    process.exit(bootstrap.status ?? 1);
  }
  run("launchctl", ["enable", `gui/${uid}/${label}`]);
  run("launchctl", ["kickstart", "-k", `gui/${uid}/${label}`]);
  console.log(`Installed launchd service ${label}`);
}

function startDarwin() {
  const uid = process.getuid();
  run("launchctl", ["kickstart", "-k", `gui/${uid}/${label}`]);
}

function stopDarwin() {
  const uid = process.getuid();
  tryRun("launchctl", ["bootout", `gui/${uid}/${label}`], { stdio: "ignore" });
}

function statusDarwin() {
  const uid = process.getuid();
  const result = tryRun("launchctl", ["print", `gui/${uid}/${label}`]);
  process.exitCode = result.status ?? 0;
}

function uninstallDarwin() {
  const uid = process.getuid();
  stopDarwin();
  fs.rmSync(path.join(os.homedir(), "Library", "LaunchAgents", `${label}.plist`), { force: true });
  tryRun("launchctl", ["disable", `gui/${uid}/${label}`]);
  console.log(`Uninstalled launchd service ${label}`);
}

function installLinux() {
  ensureDirs();
  if (tryRun("systemctl", ["--user", "--version"]).status !== 0) {
    throw new Error("systemctl --user is not available; install the daemon manually or run it in WSL/systemd.");
  }
  const unitDir = path.join(os.homedir(), ".config", "systemd", "user");
  const unitPath = path.join(unitDir, systemdUnit);
  fs.mkdirSync(unitDir, { recursive: true });
  const unit = `[Unit]
Description=VibeLab Agent Skills Relay
After=network-online.target

[Service]
Type=simple
WorkingDirectory=${serviceRoot}/daemon
ExecStart=${nodePath} ${binPath}
Restart=always
RestartSec=5
StandardOutput=append:${runtimeDir}/daemon.log
StandardError=append:${runtimeDir}/daemon-error.log

[Install]
WantedBy=default.target
`;
  fs.writeFileSync(unitPath, unit, { mode: 0o644 });
  run("systemctl", ["--user", "daemon-reload"]);
  run("systemctl", ["--user", "enable", "--now", systemdUnit]);
  console.log(`Installed systemd user service ${systemdUnit}`);
}

function startLinux() {
  run("systemctl", ["--user", "start", systemdUnit]);
}

function stopLinux() {
  tryRun("systemctl", ["--user", "stop", systemdUnit]);
}

function statusLinux() {
  const result = tryRun("systemctl", ["--user", "status", systemdUnit, "--no-pager"]);
  process.exitCode = result.status ?? 0;
}

function uninstallLinux() {
  stopLinux();
  tryRun("systemctl", ["--user", "disable", systemdUnit]);
  fs.rmSync(path.join(os.homedir(), ".config", "systemd", "user", systemdUnit), { force: true });
  tryRun("systemctl", ["--user", "daemon-reload"]);
  console.log(`Uninstalled systemd user service ${systemdUnit}`);
}

function installWindows() {
  ensureDirs();
  const taskCommand = `"${nodePath}" "${binPath}"`;
  run("schtasks.exe", ["/Create", "/TN", windowsTaskName, "/SC", "ONLOGON", "/TR", taskCommand, "/F"]);
  run("schtasks.exe", ["/Run", "/TN", windowsTaskName]);
  console.log(`Installed Windows scheduled task ${windowsTaskName}`);
}

function startWindows() {
  run("schtasks.exe", ["/Run", "/TN", windowsTaskName]);
}

function stopWindows() {
  const pidPath = path.join(runtimeDir, "daemon.pid");
  if (fs.existsSync(pidPath)) {
    const pid = fs.readFileSync(pidPath, "utf8").trim();
    if (pid) {
      tryRun("taskkill.exe", ["/PID", pid, "/F"]);
    }
  }
}

function statusWindows() {
  const result = tryRun("schtasks.exe", ["/Query", "/TN", windowsTaskName, "/V"]);
  process.exitCode = result.status ?? 0;
}

function uninstallWindows() {
  stopWindows();
  tryRun("schtasks.exe", ["/Delete", "/TN", windowsTaskName, "/F"]);
  console.log(`Uninstalled Windows scheduled task ${windowsTaskName}`);
}

function dispatch(platform) {
  const handlers = {
    darwin: { install: installDarwin, start: startDarwin, stop: stopDarwin, restart: () => { stopDarwin(); installDarwin(); }, status: statusDarwin, uninstall: uninstallDarwin },
    linux: { install: installLinux, start: startLinux, stop: stopLinux, restart: () => { stopLinux(); installLinux(); }, status: statusLinux, uninstall: uninstallLinux },
    win32: { install: installWindows, start: startWindows, stop: stopWindows, restart: () => { stopWindows(); startWindows(); }, status: statusWindows, uninstall: uninstallWindows },
  }[platform];
  if (!handlers || !handlers[action]) {
    console.error(`usage: relay-service.mjs <install|start|stop|restart|status|uninstall> [service-root]`);
    process.exit(2);
  }
  handlers[action]();
}

dispatch(process.platform);

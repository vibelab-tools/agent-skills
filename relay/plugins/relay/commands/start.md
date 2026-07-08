---
allowed-tools: Bash(node:*), Bash(curl:*), Bash(test:*)
description: Start the relay daemon service
---

## Context

- Service root: `~/.vibe-coding-skill/relay`
- Service controller: `~/.vibe-coding-skill/relay/bin/relay-service.mjs`

## Your task

Start the relay daemon using the platform service controller. Follow these steps:

1. Check if daemon is already running:
   ```bash
   curl -s http://127.0.0.1:${RELAY_DAEMON_PORT:-3580}/status
   ```
   If it responds, tell the user it is already running.

2. Start the platform service:
   ```bash
   node ~/.vibe-coding-skill/relay/bin/relay-service.mjs start ~/.vibe-coding-skill/relay
   ```

3. If the service script does not exist, tell the user to run:
   ```bash
   cd ~/agent-skills/relay && make install
   ```

4. Verify:
   ```bash
   curl -s http://127.0.0.1:${RELAY_DAEMON_PORT:-3580}/status
   ```
   Report success or failure.

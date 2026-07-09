---
allowed-tools: Bash(node:*), Bash(curl:*), Bash(test:*), Bash(jq:*)
description: Start the relay daemon service
---

## Context

- Service root: `~/.vibelab-tools/agent-skills/relay`
- Service controller: `~/.vibelab-tools/agent-skills/relay/bin/relay-service.mjs`

## Your task

Start the relay daemon using the platform service controller. Follow these steps:

1. Check if daemon is already running:
   ```bash
   PORT=$(jq -r '.daemon.port // 3580' ~/.vibelab-tools/agent-skills/relay/config.json 2>/dev/null)
   curl -s "http://127.0.0.1:${PORT:-3580}/status"
   ```
   If it responds, tell the user it is already running.

2. Start the platform service:
   ```bash
   node ~/.vibelab-tools/agent-skills/relay/bin/relay-service.mjs start ~/.vibelab-tools/agent-skills/relay
   ```

3. If the service script does not exist, tell the user to run:
   ```bash
   cd ~/agent-skills/relay && make install
   ```

4. Verify:
   ```bash
   PORT=$(jq -r '.daemon.port // 3580' ~/.vibelab-tools/agent-skills/relay/config.json 2>/dev/null)
   curl -s "http://127.0.0.1:${PORT:-3580}/status"
   ```
   Report success or failure.

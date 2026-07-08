---
allowed-tools: Bash(node:*)
description: Stop the VibeLab Relay daemon
---

## Your task

Stop the relay daemon:

1. Run: `node ~/.vibelab-tools/agent-skills/relay/bin/relay-service.mjs stop ~/.vibelab-tools/agent-skills/relay`
2. If the service reports an error because it is not loaded, tell user "Daemon is not running"
3. Confirm: "Daemon stopped"

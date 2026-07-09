---
allowed-tools: Bash(node:*), Bash(curl:*)
description: Show relay daemon status and bindings
---

## Your task

Check relay daemon status:

1. Run `node ~/.vibelab-tools/agent-skills/relay/bin/relay-service.mjs status ~/.vibelab-tools/agent-skills/relay` to check platform service status.
2. If not running, report "Daemon is not running. Use `/relay:start` to start."
3. If running, query daemon using `daemon.port` from `~/.vibelab-tools/agent-skills/relay/config.json`, defaulting to `3580`.
4. Display:
   - service status
   - Active IM providers
   - Session bindings (tmux session → topic/conversation/thread)
5. If no bindings, suggest using `/relay:bind` to create one.

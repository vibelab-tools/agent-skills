---
allowed-tools: Bash(node:*), Bash(curl:*)
description: Show relay daemon status and bindings
---

## Your task

Check relay daemon status:

1. Run `node ~/.vibe-coding-skill/relay/bin/relay-service.mjs status ~/.vibe-coding-skill/relay` to check platform service status.
2. If not running, report "Daemon is not running. Use `/relay:start` to start."
3. If running, query daemon: `curl -s http://127.0.0.1:${RELAY_DAEMON_PORT:-3580}/status`
4. Display:
   - service status
   - Active IM providers
   - Session bindings (tmux session → topic/conversation/thread)
5. If no bindings, suggest using `/relay:bind` to create one.

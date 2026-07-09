---
allowed-tools: Bash(curl:*), Bash(tmux:*), Bash(echo:*), Bash(jq:*)
description: Unbind current tmux session from Telegram topic
---

## Context

- Current tmux session: !`tmux display-message -p '#{session_name}' 2>/dev/null || echo 'NOT_IN_TMUX'`

## Your task

Unbind the current tmux session from its Telegram topic.

1. Detect current tmux session from context. If "NOT_IN_TMUX", tell user they must run inside a tmux session.
2. Call daemon unbind API:
   ```bash
   PORT=$(jq -r '.daemon.port // 3580' ~/.vibelab-tools/agent-skills/relay/config.json 2>/dev/null)
   curl -s -X DELETE "http://127.0.0.1:${PORT:-3580}/bind/<session>"
   ```
3. Report result: "Unbound tmux session `<session>`"

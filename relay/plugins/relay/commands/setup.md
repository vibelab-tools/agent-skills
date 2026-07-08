---
allowed-tools: Bash(cat:*), Bash(echo:*), Bash(test:*), Bash(jq:*), Bash(curl:*), Bash(mkdir:*)
description: Interactive setup for VibeLab Relay environment configuration
---

## Your task

Guide the user through VibeLab Relay setup. Check and configure required environment variables.

### Required variables

| Variable | Description | Example |
|----------|-------------|---------|
| `TELEGRAM_BOT_TOKEN` | Telegram Bot API token | `8365603919:AAG...` |
| `TELEGRAM_CHAT_ID` | Telegram group chat ID | `-100xxxxxxxxxx` |
| `CLAUDE_RELAY_WORKER_URL` | Worker URL | `https://relay.example.com` |
| `RELAY_DAEMON_PORT` | Daemon port (optional) | `3580` |

### Steps

1. Read `~/.claude/settings.json` and check which variables are already set in the `env` section.
2. For each missing variable, ask the user to provide the value.
3. Once all values are collected, show the user what will be added and ask for confirmation.
4. Update `~/.claude/settings.json` by adding the variables to the `env` section (preserve existing settings).
5. Remind user:
   - They need to restart Claude Code for env changes to take effect
   - They should create a Telegram group with a bot and note the chat ID
   - Topics must be enabled in the Telegram group
   - Run `/relay:start` after restarting to start the daemon

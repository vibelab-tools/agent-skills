---
allowed-tools: Bash(cat:*), Bash(echo:*), Bash(test:*), Bash(jq:*), Bash(curl:*), Bash(mkdir:*)
description: Interactive setup for VibeLab Relay configuration
---

## Your task

Guide the user through VibeLab Relay setup. Check and configure the service-local structured config.

### Required variables

| Field | Description | Example |
|----------|-------------|---------|
| `telegram.bot_token` | Telegram Bot API token | `8365603919:AAG...` |
| `telegram.chat_id` | Telegram group chat ID | `-100xxxxxxxxxx` |
| `worker.url` | Worker URL | `https://relay.example.com` |
| `daemon.port` | Daemon port (optional) | `3580` |

### Steps

1. Read `~/.vibelab-tools/agent-skills/relay/config.json` and check which fields are already set.
2. For each missing field, ask the user to provide the value.
3. Once all values are collected, show the user what will be added and ask for confirmation.
4. Update `~/.vibelab-tools/agent-skills/relay/config.json` by adding structured fields such as `telegram.bot_token`, `telegram.chat_id`, `worker.url`, and `daemon.port` (preserve existing settings).
5. Remind user:
   - They need to restart the relay service for config changes to take effect
   - They should create a Telegram group with a bot and note the chat ID
   - Topics must be enabled in the Telegram group
   - Run `/relay:start` or `make restart` after updating the config

# Claude Code Setup Guide

This guide explains how Claude Code uses VibeLab Relay for Telegram, DingTalk,
and Feishu messaging through the shared local daemon.

## How It Works

Claude Code uses three relay surfaces:

- Claude plugin marketplace:
  `~/.vibelab-tools/agent-skills/relay/claude-marketplace`
- Claude plugin hooks, `UserPromptSubmit`, `PreToolUse`, and `Stop`, call the
  shared hook scripts under `plugins/relay/scripts/`.
- The local daemon sends IM notifications, polls replies, and injects remote
  replies back into tmux with `tmux send-keys`.

Claude Code project bindings are stored in `.claude/relay.json`. Codex project
bindings are stored in `.codex/relay.json`, so both agents can run in the same
project without competing for the same Telegram Topic or Feishu thread.

## Environment

Configure the IM providers you need in
`~/.vibelab-tools/agent-skills/relay/config.json`:

```json
{
  "daemon": { "port": 3580 },
  "worker": { "url": "https://<worker-domain>" },
  "telegram": {
    "bot_token": "<token>",
    "chat_id": "<chat-id>",
    "proxy": { "enabled": true, "url": "http://127.0.0.1:50170" }
  },
  "dingtalk": {
    "client_id": "<client-id>",
    "client_secret": "<client-secret>",
    "proxy": { "enabled": false }
  },
  "feishu": {
    "app_id": "<app-id>",
    "app_secret": "<app-secret>",
    "chat_id": "<chat-id>",
    "proxy": { "enabled": false }
  }
}
```

Legacy env-style config is still migrated for compatibility. Prefer the
structured fields above for all new installs.

## Install

From the relay project directory:

```bash
make install-claude
```

This installs:

- daemon files: `~/.vibelab-tools/agent-skills/relay/daemon`
- shared relay config: `~/.vibelab-tools/agent-skills/relay/config.json`
- runtime state and logs: `~/.vibelab-tools/agent-skills/relay/runtime`
- Claude plugin marketplace: `~/.vibelab-tools/agent-skills/relay/claude-marketplace`
- platform user service: launchd on macOS, `systemd --user` on Linux, or Task
  Scheduler on Windows

`make install` installs both Codex and Claude Code plugin surfaces.

The Windows service controller is implemented by `relay-service.mjs`. The hook
scripts are currently Bash scripts, so Windows Native usage should run them via
WSL or Git Bash until PowerShell hook equivalents are added.

## Service Control

Claude Code can use slash commands:

```text
/relay:start
/relay:stop
/relay:status
```

The same controls are available as Make targets:

```bash
make start
make stop
make restart
make status
curl -s http://127.0.0.1:3580/status
```

## Usage

1. Start or enter Claude Code.
2. When you submit a prompt, the `UserPromptSubmit` hook syncs the local input
   to IM.
3. When Claude Code triggers `AskUserQuestion`, the `PreToolUse` hook syncs the
   question to IM.
4. When a Claude Code turn ends, the `Stop` hook sends the final assistant
   reply.
5. Replies in a Telegram Topic, DingTalk group, or Feishu thread are injected
   into the matching `claude-...` tmux session.

# Claude Code Setup Guide

This guide explains how Claude Code uses VibeLab Relay for Telegram, DingTalk,
and Feishu messaging through the shared local daemon.

## How It Works

Claude Code uses three relay surfaces:

- Claude plugin marketplace:
  `~/.vibe-coding-skill/relay/claude-marketplace`
- Claude plugin hooks, `UserPromptSubmit`, `PreToolUse`, and `Stop`, call the
  shared hook scripts under `plugins/relay/scripts/`.
- The local daemon sends IM notifications, polls replies, and injects remote
  replies back into tmux with `tmux send-keys`.

Claude Code project bindings are stored in `.claude/relay.json`. Codex project
bindings are stored in `.codex/relay.json`, so both agents can run in the same
project without competing for the same Telegram Topic or Feishu thread.

## Environment

Configure the IM providers you need in the `env` section of
`~/.claude/settings.json`:

```json
{
  "env": {
    "TELEGRAM_BOT_TOKEN": "<token>",
    "TELEGRAM_CHAT_ID": "<chat-id>",
    "CLAUDE_RELAY_WORKER_URL": "https://<worker-domain>",
    "DINGTALK_CLIENT_ID": "<client-id>",
    "DINGTALK_CLIENT_SECRET": "<client-secret>",
    "FEISHU_APP_ID": "<app-id>",
    "FEISHU_APP_SECRET": "<app-secret>",
    "FEISHU_CHAT_ID": "<chat-id>",
    "TELEGRAM_PROXY_ENABLED": "true",
    "TELEGRAM_PROXY_URL": "http://127.0.0.1:50170",
    "DINGTALK_PROXY_ENABLED": "false",
    "FEISHU_PROXY_ENABLED": "false",
    "RELAY_DAEMON_PORT": "3580"
  }
}
```

`RELAY_WORKER_URL` is accepted as a provider-neutral alias for
`CLAUDE_RELAY_WORKER_URL`.

## Install

From the relay project directory:

```bash
make install-claude
```

This installs:

- daemon files: `~/.vibe-coding-skill/relay/daemon`
- runtime state and logs: `~/.vibe-coding-skill/relay/runtime`
- Claude plugin marketplace: `~/.vibe-coding-skill/relay/claude-marketplace`
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
curl -s http://127.0.0.1:${RELAY_DAEMON_PORT:-3580}/status
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

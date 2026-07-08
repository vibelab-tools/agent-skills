# Codex Setup Guide

This guide explains how Codex CLI uses VibeLab Relay for Telegram, DingTalk,
and Feishu messaging through the shared local daemon.

## How It Works

Codex uses three relay surfaces:

- `scripts/codex-tmux.sh` creates or reuses a `codex-<project>-<hash>` tmux
  session for the current project directory.
- Codex lifecycle hooks, `UserPromptSubmit` and `Stop`, call the shared hook
  scripts under `plugins/relay/scripts/`.
- The local daemon sends IM notifications, polls replies, and injects remote
  replies back into the tmux session with `tmux send-keys`.

Codex project bindings are stored in `.codex/relay.json`. Claude Code project
bindings stay in `.claude/relay.json`, so both agents can run in the same
project without competing for the same Telegram Topic or Feishu thread.

## Environment

If relay variables are already configured in `~/.claude/settings.json`, Codex
can reuse them. For Codex-only setups, create `~/.codex/relay-settings.json`:

```json
{
  "env": {
    "TELEGRAM_BOT_TOKEN": "<token>",
    "TELEGRAM_CHAT_ID": "<chat-id>",
    "RELAY_WORKER_URL": "https://<worker-domain>",
    "DINGTALK_CLIENT_ID": "<client-id>",
    "DINGTALK_CLIENT_SECRET": "<client-secret>",
    "FEISHU_APP_ID": "<app-id>",
    "FEISHU_APP_SECRET": "<app-secret>",
    "FEISHU_CHAT_ID": "<chat-id>",
    "TELEGRAM_PROXY_ENABLED": "true",
    "TELEGRAM_PROXY_PROTOCOL": "http",
    "TELEGRAM_PROXY_HOST": "127.0.0.1",
    "TELEGRAM_PROXY_PORT": "50170",
    "DINGTALK_PROXY_ENABLED": "false",
    "FEISHU_PROXY_ENABLED": "false",
    "RELAY_DAEMON_PORT": "3580",
    "RELAY_HOME": "/Users/you/.vibe-coding-skill/relay/runtime"
  }
}
```

`CLAUDE_RELAY_WORKER_URL` is still supported for compatibility. Prefer
`RELAY_WORKER_URL` for new shared Codex and Claude Code setups.

## Proxy Settings

Proxy settings are configured per IM provider and must be explicitly enabled:

```json
{
  "env": {
    "TELEGRAM_PROXY_ENABLED": "true",
    "TELEGRAM_PROXY_URL": "http://127.0.0.1:50170",
    "DINGTALK_PROXY_ENABLED": "false",
    "FEISHU_PROXY_ENABLED": "false"
  }
}
```

You can also split a proxy URL into individual fields:

```json
{
  "env": {
    "TELEGRAM_PROXY_ENABLED": "true",
    "TELEGRAM_PROXY_PROTOCOL": "socks5",
    "TELEGRAM_PROXY_HOST": "127.0.0.1",
    "TELEGRAM_PROXY_PORT": "50170"
  }
}
```

Supported prefixes are `TELEGRAM_`, `DINGTALK_`, and `FEISHU_`. By default no
provider uses a proxy. A common setup is to proxy Telegram only and keep
DingTalk and Feishu direct.

## Install

From the relay project directory:

```bash
make install-codex
```

This installs:

- daemon files: `~/.vibe-coding-skill/relay/daemon`
- Codex wrapper: `~/.vibe-coding-skill/relay/bin/codex-tmux`
- runtime state and logs: `~/.vibe-coding-skill/relay/runtime`
- Codex plugin marketplace: `~/.vibe-coding-skill/relay/codex-marketplace`
- platform user service: launchd on macOS, `systemd --user` on Linux, or Task
  Scheduler on Windows

The Windows service controller is implemented by `relay-service.mjs`. The hook
scripts are currently Bash scripts, so Windows Native usage should run them via
WSL or Git Bash until PowerShell hook equivalents are added.

## Start Codex

Run Codex through the tmux wrapper:

```bash
~/.vibe-coding-skill/relay/bin/codex-tmux
```

Optional shell alias:

```bash
alias codex-tmux="$HOME/.vibe-coding-skill/relay/bin/codex-tmux"
```

## Hook Trust

`make install-codex` installs the Codex marketplace and plugin source under the
shared relay service directory, then registers the plugin with Codex.

After opening Codex, run `/hooks` and trust the relay `UserPromptSubmit` and
`Stop` hooks. Codex skips new hooks until they are trusted.

You can also avoid plugin installation and place a project-local
`.codex/hooks.json` that points directly to the repository or installed
`plugins/relay/scripts/*.sh` files.

## Service Control

`make install-codex` installs and starts the platform service automatically.
Manual controls are available:

```bash
make start
make stop
make restart
make status
curl -s http://127.0.0.1:${RELAY_DAEMON_PORT:-3580}/status
```

Uninstall the Codex relay surface:

```bash
make uninstall-codex
```

Clean development build outputs:

```bash
make clean
```

## Usage

1. Start Codex with `codex-tmux`.
2. When you submit a prompt, the `UserPromptSubmit` hook syncs the local input
   to the configured IM channels.
3. When a Codex turn ends, the `Stop` hook sends the final assistant reply.
4. Replies in a Telegram Topic, DingTalk group, or Feishu thread are injected
   back into the matching `codex-...` tmux session.

Telegram Topic names use `<hostname>:codex:<project>`. Claude Code keeps
`<hostname>:<project>`.

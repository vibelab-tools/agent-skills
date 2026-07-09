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

Relay variables are shared by Codex and Claude Code in the service-local config
file, `~/.vibelab-tools/agent-skills/relay/config.json`:

```json
{
  "daemon": { "port": 3580 },
  "worker": { "url": "https://<worker-domain>" },
  "telegram": {
    "bot_token": "<token>",
    "chat_id": "<chat-id>",
    "proxy": {
      "enabled": true,
      "protocol": "http",
      "host": "127.0.0.1",
      "port": 50170
    }
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
structured fields above for all new Codex and Claude Code setups.

## Proxy Settings

Proxy settings are configured per IM provider and must be explicitly enabled:

```json
{
  "telegram": {
    "proxy": {
      "enabled": true,
      "url": "http://127.0.0.1:50170"
    }
  },
  "dingtalk": { "proxy": { "enabled": false } },
  "feishu": { "proxy": { "enabled": false } }
}
```

You can also split a proxy URL into individual fields:

```json
{
  "telegram": {
    "proxy": {
      "enabled": true,
      "protocol": "socks5",
      "host": "127.0.0.1",
      "port": 50170
    }
  }
}
```

Each provider has its own `proxy` object. By default no provider uses a proxy.
A common setup is to proxy Telegram only and keep DingTalk and Feishu direct.

## Install

From the relay project directory:

```bash
make install-codex
```

This installs:

- daemon files: `~/.vibelab-tools/agent-skills/relay/daemon`
- Codex wrapper: `~/.vibelab-tools/agent-skills/relay/bin/codex-tmux`
- shared relay config: `~/.vibelab-tools/agent-skills/relay/config.json`
- runtime state and logs: `~/.vibelab-tools/agent-skills/relay/runtime`
- Codex plugin marketplace: `~/.vibelab-tools/agent-skills/relay/codex-marketplace`
- platform user service: launchd on macOS, `systemd --user` on Linux, or Task
  Scheduler on Windows

The Windows service controller is implemented by `relay-service.mjs`. The hook
scripts are currently Bash scripts, so Windows Native usage should run them via
WSL or Git Bash until PowerShell hook equivalents are added.

## Start Codex

Run Codex through the tmux wrapper:

```bash
~/.vibelab-tools/agent-skills/relay/bin/codex-tmux
```

Optional shell alias:

```bash
alias codex-tmux="$HOME/.vibelab-tools/agent-skills/relay/bin/codex-tmux"
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
curl -s http://127.0.0.1:3580/status
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

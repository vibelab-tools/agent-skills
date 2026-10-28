# Codex Setup Guide

This guide explains how Codex CLI uses VibeLab Relay for Telegram, DingTalk,
and Feishu messaging through the shared local daemon.

## How It Works

Codex uses three relay surfaces:

- `scripts/codex-tmux.sh` creates or reuses a tmux session for the current
  project directory and explicit profile. Its sibling Python helpers provide
  a shared session picker and capacity-error recovery.
- Codex lifecycle hooks, `UserPromptSubmit` and `Stop`, call the shared hook
  scripts under `plugins/relay/scripts/`.
- The local daemon sends IM notifications, polls replies, and injects remote
  replies back into the tmux session with `tmux send-keys`.

Codex project bindings are stored in `.codex/relay.json`. Claude Code project
bindings stay in `.claude/relay.json`, so both agents can run in the same
project without competing for the same Telegram Topic or Feishu topic binding.

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

The Codex launchers require Bash, tmux, Codex CLI, and Python 3.11 or later
with the standard-library `curses` module. The shell launcher works on macOS
and Linux; on Windows use WSL or another environment that provides these tools.

From the relay project directory:

```bash
make install-codex
```

This installs:

- daemon files: `~/.vibelab-tools/agent-skills/relay/daemon`
- Codex tools in `~/.vibelab-tools/agent-skills/relay/bin`: `codex-tmux`,
  `codex-direct`, `codex-resume-picker.py`, and `codex-capacity-retry.py`
- shared relay config: `~/.vibelab-tools/agent-skills/relay/config.json`
- runtime state and logs: `~/.vibelab-tools/agent-skills/relay/runtime`
- Codex plugin marketplace: `~/.vibelab-tools/agent-skills/relay/codex-marketplace`
- platform user service: launchd on macOS, `systemd --user` on Linux, or Task
  Scheduler on Windows

The Windows service controller is implemented by `relay-service.mjs`. The hook
scripts are currently Bash scripts, so Windows Native usage should run them via
WSL or Git Bash until PowerShell hook equivalents are added.

To update only the Codex tools from this checkout:

```bash
make install-codex-wrapper
# Optional installation prefix:
make install-codex-wrapper SERVICE_ROOT="$HOME/.vibelab-tools/agent-skills/relay"
```

This target copies all four tools together, without building or restarting
the Relay daemon, registering plugins, or changing credentials, profiles,
model catalogs, or session history. Keep shell aliases and any legacy local
entrypoints pointed at this installed `bin` directory so another script copy
does not drift from the repository version. New launcher invocations use the
updated files; an already-running capacity monitor retains its loaded code
until that monitor restarts.

## Start Codex

Run Codex through the tmux wrapper:

```bash
~/.vibelab-tools/agent-skills/relay/bin/codex-tmux
```

Optional shell alias:

```bash
alias codex-tmux="$HOME/.vibelab-tools/agent-skills/relay/bin/codex-tmux"
```

The launcher keeps separate tmux sessions for each directory and explicit
profile. `-C`/`--cd` selects the project directory. An existing tmux session is
reused; inside tmux, Codex runs in the current pane. Put `--no-tmux` first to
skip creating or attaching to a tmux session. Non-interactive calls and
management commands keep their ordinary CLI behavior.

The normal launcher preserves the invoking shell's proxy variables and
explicitly unsets missing proxy variables in a newly created pane, avoiding
stale proxy settings from the tmux server. `codex-direct` removes the HTTP,
HTTPS, and SOCKS proxy variables and adds `--no-daemon` so an existing
proxy-backed Codex daemon is not reused. Direct sessions have their own tmux
session names and use the same Codex session storage.

### Profiles and shared history

Profiles remain user-owned files under `~/.codex` (or the existing
`CODEX_HOME`). After configuring profiles named `kimi` and `deepseek`, aliases
can select them explicitly:

```bash
alias codex-kimi="$HOME/.vibelab-tools/agent-skills/relay/bin/codex-direct --profile kimi"
alias codex-deepseek="$HOME/.vibelab-tools/agent-skills/relay/bin/codex-direct --profile deepseek"
```

The launcher does not assign a profile based on the project directory. Keep
`CODEX_HOME` unchanged to share the original session storage across providers.
Provider definitions needed by saved sessions should remain available in the
base user configuration as well as any profile-specific selection.

Interactive `resume` without an explicit session ID uses a shared list across
providers, filtered to the current directory. `resume --all` includes other
directories, and `resume --last` selects the latest matching session. Select
with the arrow keys and Enter, type to search, or press Esc to cancel. The
selected session opens with the current invocation's provider and model, using
an independent Codex process to release the session writer when it exits.
Explicit session IDs, remote invocations, and unsupported CLI combinations
continue through the native CLI; Codex's in-session `/resume` keeps its native
filtering. Sharing storage does not convert provider-specific historical tool
records into another provider's format.

### Capacity-error continuation

Interactive launches register their tmux pane with one capacity monitor per
tmux server. It checks about once per second and sends `请继续` plus Enter
immediately when it detects an idle, empty composer after Codex's
`Selected model is at capacity. Please try a different model.` error. It also
recognizes this error during remote compaction and permits subsequent
subagent-completion notifications or the background-terminal status line.

There is no retry delay or exponential backoff. An unchanged visible error is
sent once; a fresh capacity error can trigger another continuation. Draft
input, active work, copy mode, and other errors are skipped, and the monitor
checks the pane again immediately before sending. Logs contain the timestamp,
pane ID, and retry count in `~/.codex/log/tmux-capacity-retry.log`.

Disable automatic continuation for the current pane:

```bash
tmux set-option -p @codex-capacity-retry off
```

A later launcher invocation registers its pane again. To enable it manually
in an existing tmux pane, run the installed helper there:

```bash
~/.vibelab-tools/agent-skills/relay/bin/codex-capacity-retry.py register
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
   to the configured IM channels. Images included in a Codex prompt are also
   uploaded into the matching Feishu Topic and sent as the authorized user,
   like the prompt text. Images are matched to the submitted prompt as well as
   its turn ID so follow-up input during an active turn keeps its own images.
3. When a Codex turn ends, the `Stop` hook sends the final assistant reply.
4. Replies in a Telegram Topic, DingTalk group, or the latest Feishu relay
   Topic are injected back into the matching `codex-...` tmux session.

Telegram Topic names use `<project>` for both Codex and Claude Code.

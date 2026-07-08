# VibeLab Relay

Bidirectional IM relay for [Claude Code](https://docs.anthropic.com/en/docs/claude-code) and Codex CLI. Get notified on Telegram, DingTalk, and Feishu when an agent needs attention, reply remotely, or send new instructions — with per-session isolation.

## Features

- **Bidirectional communication**: agent → IM notifications, IM → tmux input injection
- **Multi-platform**: Telegram (Topics), DingTalk (Stream API), and Feishu (WebSocket + Threads) supported simultaneously
- **Auto-binding**: Telegram Topics are created and bound automatically on first use
- **Unified daemon**: Single background process manages all sessions and IM connections
- **Claude + Codex support**: Claude Code plugin hooks and Codex lifecycle hooks reuse the same daemon
- **Per-project isolation**: Each agent/project gets its own Telegram Topic and/or DingTalk group

## How It Works

```
┌─────────────┐     Webhook      ┌──────────────────┐     Poll      ┌──────────────┐
│  Telegram   │ ───────────────→ │ Cloudflare Worker │ ←──────────→ │              │
│  (Topics)   │ ←─────────────── │   (KV Storage)   │              │ Local Daemon │
└─────────────┘    Bot API       └──────────────────┘              │ (Port 3580)  │
                                                                    │              │
┌─────────────┐   Stream API (WebSocket)                           │              │
│  DingTalk   │ ←─────────────────────────────────────────────────→│              │
│  (@bot)     │                                                    │              │
└─────────────┘                                                    │              │
                                                                    │              │
┌─────────────┐   SDK WSClient (WebSocket)                         │              │
│   Feishu    │ ←─────────────────────────────────────────────────→│              │
│  (Threads)  │                                                    └──────┬───────┘
└─────────────┘                                                           │
                                                               tmux send-keys
                                                                          │
                                                                   ┌──────▼───────┐
                                                                   │ Claude/Codex  │
                                                                   │  (in tmux)    │
                                                                   └──────────────┘
```

## Quick Start

### Prerequisites

- Node.js 18+
- [Cloudflare Workers](https://workers.cloudflare.com/) account (for Telegram)
- Telegram Bot and/or DingTalk enterprise app

### Setup Guides

- **[Telegram Setup](docs/telegram-setup.md)** — Bot creation, Topics, Worker deployment
- **[DingTalk Setup](docs/dingtalk-setup.md)** — Enterprise app, Stream API, group binding
- **[Feishu Setup](docs/feishu-setup.md)** — Enterprise app, WebSocket, Thread-based isolation
- **[Claude Setup](docs/claude-setup.md)** — Claude Code plugin marketplace and hooks
- **[Codex Setup](docs/codex-setup.md)** — Codex tmux wrapper, hooks, and plugin install
- **[Usage Manual](docs/usage.md)** — Daily usage, multi-project, troubleshooting

### Quick Setup

1. Configure environment in `~/.claude/settings.json` for Claude Code, or
   `~/.codex/relay-settings.json` for Codex-only setups:

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
    "TELEGRAM_PROXY_URL": "http://127.0.0.1:50170",
    "DINGTALK_PROXY_ENABLED": "false",
    "FEISHU_PROXY_ENABLED": "false",
    "RELAY_DAEMON_PORT": "3580"
  }
}
```

`CLAUDE_RELAY_WORKER_URL` is accepted as a compatibility alias for
`RELAY_WORKER_URL`.

Proxy settings are per IM channel and opt-in. Use `TELEGRAM_PROXY_ENABLED=true` plus either `TELEGRAM_PROXY_URL` or `TELEGRAM_PROXY_PROTOCOL/HOST/PORT`; keep `DINGTALK_PROXY_ENABLED=false` and `FEISHU_PROXY_ENABLED=false` when those services should connect directly.

2. Install the plugin and start the daemon:

```bash
make install   # install both Codex and Claude Code plugin surfaces and start the service
```

The system auto-creates Telegram Topics and binds sessions on first use.

### Local Install

Install both Codex and Claude Code plugin surfaces:

```bash
make install
```

Install one side only:

```bash
make install-codex
make install-claude
```

Useful maintenance targets:

```bash
make          # build
make status   # inspect the platform service
make restart  # restart the platform service
make clean    # remove development build outputs
make uninstall
```

## Slash Commands

| Command | Description |
|---------|-------------|
| `/relay:start` | Start the background daemon |
| `/relay:stop` | Stop the daemon |
| `/relay:status` | Show daemon status and active bindings |
| `/relay:bind [topic_id]` | Bind current tmux session to a topic (auto-creates if omitted) |
| `/relay:unbind` | Unbind current tmux session |
| `/relay:setup` | Interactive environment configuration guide |

## Project Structure

```
relay/
├── .claude-plugin/          # Root plugin manifest
├── codex-marketplace/       # Local Codex marketplace template
├── plugins/relay/           # Claude Code plugin
│   ├── .claude-plugin/      # Plugin manifest with hooks
│   ├── .codex-plugin/       # Codex plugin manifest
│   ├── commands/            # Slash command definitions
│   ├── hooks/               # Codex lifecycle hook config
│   └── scripts/             # Hook scripts (bash)
├── daemon/                  # Local Node.js daemon
│   └── src/
│       ├── index.ts         # Entry point
│       ├── server.ts        # HTTP API for hooks
│       ├── poller.ts        # Worker poll loop
│       ├── session-manager.ts
│       ├── tmux-injector.ts
│       └── providers/       # IM provider abstraction
│           ├── base.ts
│           ├── telegram.ts  # Via Worker relay
│           ├── dingtalk.ts  # Via Stream SDK
│           └── feishu.ts    # Via SDK WSClient
├── worker/                  # Cloudflare Worker (Telegram only)
│   └── src/
│       ├── index.ts         # Router
│       ├── telegram-api.ts  # Bot API wrapper
│       └── routes/
├── scripts/                 # tmux wrappers
├── docs/                    # Setup guides and usage manual
└── README.md
```

Installed runtime layout:

```text
~/.vibe-coding-skill/relay/
├── daemon/              # compiled daemon and production Node dependencies
├── runtime/             # bindings, pid, and logs
├── bin/                 # codex-tmux and service controller scripts
├── codex-marketplace/   # Codex marketplace source
└── claude-marketplace/  # Claude Code marketplace source
```

## Platform Notes

- Skill/plugin metadata is portable across Codex and Claude Code, but executable
  scripts still need to match the host shell.
- The relay daemon service controller supports macOS launchd, Linux
  `systemd --user`, and Windows Task Scheduler.
- The current relay hooks in `plugins/relay/scripts/` are Bash scripts. On
  Windows Native Codex/Claude Code, run them through WSL/Git Bash or add
  PowerShell hook equivalents before treating the hooks as fully native.

## Platform Comparison

| Feature | Telegram | DingTalk | Feishu |
|---------|----------|----------|--------|
| Session isolation | Topic per project | Group per project | Thread per project |
| Message relay | Via Cloudflare Worker | Direct Stream API | Direct WebSocket |
| Auto-create channel | Yes (Topic) | No (manual group) | Yes (Thread) |
| Reply method | Direct message in Topic | @mention bot in group | Reply in thread |
| Requires Worker | Yes | No | No |

## License

MIT

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

Install-time dependencies:

- Node.js 18+.
- `pnpm` on `PATH`; the Makefile runs `pnpm install` and `pnpm run build`.
- `rsync` for installing daemon files and plugin marketplace sources.
- `jq` for Claude marketplace checks and for runtime hook/command scripts.
- Codex CLI when running `make install-codex` or `make install`.
- Claude Code CLI with plugin support when running `make install-claude` or
  `make install`.

Runtime dependencies:

- `tmux` for bidirectional reply injection. Codex should be launched through
  `~/.vibelab-tools/agent-skills/relay/bin/codex-tmux`; Claude Code sessions
  should also run inside tmux.
- `curl` and `jq` for hook scripts and slash-command helpers.
- A platform user-service manager:
  - macOS: `launchctl`
  - Linux: `systemd --user`
  - Windows: Task Scheduler through `schtasks.exe`
- IM credentials for each enabled channel:
  - Telegram: bot token, group chat ID, and a Cloudflare Worker URL.
  - DingTalk: enterprise app client ID and client secret.
  - Feishu: enterprise app ID, app secret, and target chat ID.

Optional dependencies:

- [Cloudflare Workers](https://workers.cloudflare.com/) account and Wrangler for
  deploying the Telegram Worker. DingTalk and Feishu connect directly and do not
  require the Worker.
- Python 3 with Pillow on macOS for polished LaunchAgent app icons with
  transparent padding and rounded corners. If Pillow is unavailable, service
  installation still works with a simpler `sips`-generated icon.

### Setup Guides

- **[Telegram Setup](docs/telegram-setup.md)** — Bot creation, Topics, Worker deployment
- **[DingTalk Setup](docs/dingtalk-setup.md)** — Enterprise app, Stream API, group binding
- **[Feishu Setup](docs/feishu-setup.md)** — Enterprise app, WebSocket, Thread-based isolation
- **[Claude Setup](docs/claude-setup.md)** — Claude Code plugin marketplace and hooks
- **[Codex Setup](docs/codex-setup.md)** — Codex tmux wrapper, hooks, and plugin install
- **[Usage Manual](docs/usage.md)** — Daily usage, multi-project, troubleshooting

### Quick Setup

1. Configure relay environment in the service-local config file:
   `~/.vibelab-tools/agent-skills/relay/config.json`.

```json
{
  "daemon": { "port": 3580 },
  "worker": { "url": "https://<worker-domain>" },
  "telegram": {
    "bot_token": "<token>",
    "chat_id": "<chat-id>",
    "proxy": {
      "enabled": true,
      "url": "http://127.0.0.1:50170"
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

Legacy env-style config is migrated during install, but new configuration
should use the structured fields above. Proxy settings are per IM channel and
opt-in. Keep `dingtalk.proxy.enabled=false` and `feishu.proxy.enabled=false`
when those services should connect directly.

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
make uninstall # stop service and remove installed files; preserve config.json
make purge     # run uninstall and remove config.json
```

## Configuration Reference

Relay runtime configuration lives at:

```text
~/.vibelab-tools/agent-skills/relay/config.json
```

`make install` creates the file with restrictive permissions when it does not
exist and preserves existing values on later installs. Legacy env-style config
from `~/.codex/relay-settings.json` or `~/.claude/settings.json` is migrated into
this service-local structured config. New installs should edit only
`~/.vibelab-tools/agent-skills/relay/config.json`.

### Core Fields

| Field | Required | Default | Description |
| --- | --- | --- | --- |
| `daemon.port` | No | `3580` | Local HTTP daemon port bound to `127.0.0.1`. Slash commands and hooks read this value. |
| `daemon.machine_id` | No | Hash of hostname | Stable machine identifier sent to the Telegram Worker. Set only when several machines must use a controlled ID. |
| `daemon.poll_interval_ms` | No | `1000` | Telegram Worker polling interval in milliseconds. |
| `worker.url` | Telegram only | Empty | Base URL of the Cloudflare Worker used for Telegram notification delivery, polling, registration, and topic binding. Do not include a trailing API path. |

Runtime paths such as `runtime/`, `bindings.json`, PID files, and logs are
derived from the install root. They are not user configuration fields.

### Telegram Fields

| Field | Required | Description |
| --- | --- | --- |
| `telegram.bot_token` | Yes, for Telegram | Bot API token from BotFather. Used by the Worker and topic creation helpers. |
| `telegram.chat_id` | Yes, for Telegram | Telegram group or supergroup chat ID, usually a negative numeric ID. The group must have Topics enabled for per-session isolation. |
| `telegram.proxy` | No | Per-channel proxy config for Telegram HTTP calls. Useful when Telegram is blocked on the local network. |

Telegram also requires `worker.url`. The local daemon sends notifications through
the Worker and polls the Worker for replies. Project topic bindings are stored in
`.claude/relay.json` or `.codex/relay.json`, not in the global runtime config.

### DingTalk Fields

| Field | Required | Description |
| --- | --- | --- |
| `dingtalk.client_id` | Yes, for DingTalk | DingTalk enterprise app client ID / app key. Used for Stream API and robot message APIs. |
| `dingtalk.client_secret` | Yes, for DingTalk | DingTalk enterprise app secret. |
| `dingtalk.proxy` | No | Per-channel proxy config. Keep disabled when DingTalk should connect directly. |

DingTalk does not use the Cloudflare Worker. It connects through DingTalk Stream
API and routes replies by conversation binding.

### Feishu Fields

| Field | Required | Description |
| --- | --- | --- |
| `feishu.app_id` | Yes, for Feishu | Feishu/Lark app ID. |
| `feishu.app_secret` | Yes, for Feishu | Feishu/Lark app secret. |
| `feishu.chat_id` | Yes, for Feishu | Target chat ID where relay creates root messages and receives thread replies. |
| `feishu.proxy` | No | Per-channel proxy config. Keep disabled when Feishu should connect directly. |

Feishu does not use the Cloudflare Worker. It connects through Feishu WebSocket
and uses thread root message IDs for per-session isolation.

### Proxy Fields

Each IM channel supports the same optional proxy object:

```json
{
  "proxy": {
    "enabled": false,
    "url": "http://127.0.0.1:50170"
  }
}
```

Supported proxy fields:

| Field | Description |
| --- | --- |
| `enabled` | Boolean or truthy string. Proxying is disabled unless this is set to `true`, `1`, `yes`, or `on`. |
| `url` | Full proxy URL such as `http://127.0.0.1:50170`, `socks5://127.0.0.1:1080`, or an authenticated URL. |
| `protocol` | Optional split-field protocol when `url` is not used. Defaults to `http`. |
| `host` | Optional split-field proxy host when `url` is not used. |
| `port` | Optional split-field proxy port when `url` is not used. |
| `username` | Optional split-field proxy username. |
| `password` | Optional split-field proxy password. |

Proxy settings are per channel. The daemon clears ambient proxy environment
variables on startup, so shell-level `HTTP_PROXY`, `HTTPS_PROXY`, or `ALL_PROXY`
values do not leak into Telegram, DingTalk, or Feishu by accident.

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
~/.vibelab-tools/agent-skills/relay/
├── daemon/              # compiled daemon and production Node dependencies
├── runtime/             # bindings, pid, and logs
├── bin/                 # codex-tmux and service controller scripts
├── config.json          # local daemon configuration and IM credentials
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

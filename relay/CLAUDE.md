# VibeLab Relay

Multi-platform IM relay for Claude Code and Codex CLI. Bidirectional real-time communication with Telegram, DingTalk, and Feishu, session isolation, unified daemon management.

## Architecture

```
Telegram ←→ Cloudflare Worker (KV) ←→ Local Daemon ←→ tmux (Claude Code/Codex)
DingTalk ←→ Stream API (WebSocket) ──→ Local Daemon ←→ tmux (Claude Code/Codex)
Feishu   ←→ SDK WSClient (WebSocket) → Local Daemon ←→ tmux (Claude Code/Codex)
```

Three components:
- **Worker** (`worker/`): Cloudflare Worker for Telegram webhook relay and message queue
- **Daemon** (`daemon/`): Local Node.js process, manages sessions, polls Worker, connects DingTalk Stream and Feishu WebSocket
- **Plugin** (`plugins/relay/`): Claude Code plugin with hooks and slash commands, plus Codex hook/plugin metadata

## Key Files

- `daemon/src/index.ts` — Entry point, initializes all providers, must use `dns.setDefaultResultOrder("ipv4first")`
- `daemon/src/server.ts` — Local HTTP API (port 3580), sends to all configured providers
- `daemon/src/poller.ts` — 1s poll loop for Telegram messages via Worker
- `daemon/src/session-manager.ts` — tmux↔topic/conversation/thread bindings, persisted to `~/.vibelab-tools/agent-skills/relay/runtime/bindings.json`
- `daemon/src/providers/telegram.ts` — Telegram provider via Worker relay
- `daemon/src/providers/dingtalk.ts` — DingTalk provider via Stream SDK
- `daemon/src/providers/feishu.ts` — Feishu provider via SDK WSClient, reply_in_thread for topic isolation
- `scripts/relay-service.mjs` — platform service installer/controller for launchd, systemd user services, and Windows Task Scheduler
- `scripts/relay-daemon.mjs` — daemon launcher that reads the service-local relay config
- `plugins/relay/scripts/ensure-binding.sh` — Auto-creates Telegram topic and binds on first hook trigger
- `plugins/relay/scripts/hook-stop.sh` — Extracts `last_assistant_message` from Claude/Codex hook input and sends to IM
- `scripts/codex-tmux.sh` — Codex tmux wrapper, creates `codex-<project>-<hash>` sessions

## Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `telegram.bot_token` | Telegram Bot API token | For Telegram |
| `telegram.chat_id` | Telegram group chat ID (`-100xxx`) | For Telegram |
| `worker.url` | Cloudflare Worker URL | For Telegram |
| `dingtalk.client_id` | DingTalk app Client ID (AppKey) | For DingTalk |
| `dingtalk.client_secret` | DingTalk app Client Secret (AppSecret) | For DingTalk |
| `feishu.app_id` | Feishu app ID (`cli_xxxx`) | For Feishu |
| `feishu.app_secret` | Feishu app secret | For Feishu |
| `feishu.chat_id` | Feishu group chat ID (`oc_xxxx`) | For Feishu |
| `daemon.port` | Daemon port (default: 3580) | Optional |

## Per-Project Config

Claude projects store bindings in `.claude/relay.json`; Codex projects store bindings in `.codex/relay.json`:
```json
{"topicId": "10", "dingtalkConversationId": "cidXXX==", "createdAt": "..."}
```

Feishu `feishuRootMessageId` is auto-created on first notification and stored in `bindings.json`.

## Documentation

- `docs/telegram-setup.md` — Telegram setup guide
- `docs/dingtalk-setup.md` — DingTalk setup guide
- `docs/feishu-setup.md` — Feishu setup guide
- `docs/codex-setup.md` — Codex wrapper and hook setup
- `docs/usage.md` — Usage manual and troubleshooting

## Build & Run

```bash
make build
cd worker && npm install && npx wrangler deploy

# Local: use slash commands
/relay:start   /relay:stop   /relay:status   /relay:bind   /relay:unbind

# Or service controls directly
make start
make status
```

## Remote Deployment

```bash
# 1. Rsync this repository
rsync -avz ./ user@host:~/agent-skills/relay/

# 2. Install plugin surfaces and platform service
ssh user@host "cd ~/agent-skills/relay && make install"

# 3. Update ~/.vibelab-tools/agent-skills/relay/config.json with daemon and IM settings

# 4. Restart Claude Code/Codex sessions
```

## Known Issues

- Node.js IPv6 timeout with Cloudflare — solved with `dns.setDefaultResultOrder("ipv4first")`
- DingTalk: the bot must be added from group settings, not during group creation
- DingTalk: `sessionWebhook` expires in ~60s, falls back to OpenAPI
- Feishu: long connection config requires app published first AND SDK connected before saving
- Feishu: one WebSocket connection per app (multiple daemons cannot share one app)
- Daemon scrubs ambient proxy variables; use provider-specific `proxy` config instead

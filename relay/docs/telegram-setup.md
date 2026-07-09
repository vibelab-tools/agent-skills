# Telegram Setup Guide

This guide explains how to configure Telegram as a VibeLab Relay channel.

## Prerequisites

- A Telegram account
- Access to the Telegram Bot API, with a proxy if your network requires one
- A Cloudflare Workers account for the Telegram webhook and queue Worker

## Create A Bot

1. Open Telegram and start a chat with [@BotFather](https://t.me/BotFather).
2. Send `/newbot` and follow the prompts for bot name and username.
3. Save the returned **Bot Token**. It looks like `123456:ABC-DEF...`.

## Disable Group Privacy

Telegram bots only receive commands and mentions by default. Disable privacy
mode so the bot can receive regular Topic messages:

1. Open [@BotFather](https://t.me/BotFather).
2. Send `/mybots`, select your bot, then open **Bot Settings**.
3. Choose **Group Privacy** and set it to **Turn off**.

After changing privacy mode, remove the bot from the group and add it again so
the new setting takes effect.

## Create A Group With Topics

1. Create a Telegram group and add the bot.
2. Open group settings, edit the group, and enable **Topics**.
3. Make the bot an admin with at least the **Manage Topics** permission.

If the Topics option is missing, update the group description first. Telegram
may then convert the group into a supergroup and expose Topics.

## Get The Chat ID

Temporarily remove any webhook and use `getUpdates`:

```bash
curl "https://api.telegram.org/bot<TOKEN>/deleteWebhook"
```

Send a message in the group, then query updates:

```bash
curl "https://api.telegram.org/bot<TOKEN>/getUpdates" | jq '.result[] | .message.chat'
```

Group chat IDs usually look like `-100xxxxxxxxxx`.

Restore the webhook after the Worker is deployed:

```bash
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<WORKER_DOMAIN>/webhook/telegram"
```

## Deploy The Worker

```bash
cd relay/worker
npm install

# Create a KV namespace and copy its ID into wrangler.toml.
npx wrangler kv:namespace create RELAY_KV

npx wrangler deploy
```

Use your own KV namespace ID and custom domain. The committed
`wrangler.toml` intentionally uses placeholders.

## Configure The Webhook

```bash
curl "https://api.telegram.org/bot<TOKEN>/setWebhook?url=https://<WORKER_DOMAIN>/webhook/telegram"
```

## Configure The Daemon

Add the required variables to
`~/.vibelab-tools/agent-skills/relay/config.json`:

```json
{
  "worker": { "url": "https://<worker-domain>" },
  "telegram": {
    "bot_token": "<your-bot-token>",
    "chat_id": "<your-group-chat-id>"
  }
}
```

Use `telegram.proxy.enabled=true` with `telegram.proxy.url` or split proxy
fields when Telegram needs a proxy.

## How It Works

```text
User replies in Topic -> Telegram Webhook -> Worker -> KV pending queue
Daemon polls Worker -> receives reply -> injects text into tmux

Agent stops or asks -> daemon -> Worker /api/notify -> Telegram Bot API
```

Each project gets its own Topic, usually named `<hostname>:<project>` for
Claude Code and `<hostname>:codex:<project>` for Codex. The Topic ID is stored
in `.claude/relay.json` or `.codex/relay.json`.

## Known Issues

- Node.js fetch may time out on IPv6 routes to Cloudflare. The daemon sets
  `dns.setDefaultResultOrder("ipv4first")` to reduce that failure mode.
- The bot must be a group admin with "Manage Topics" permission.
- Topic auto-creation fails if Topics are disabled for the group.

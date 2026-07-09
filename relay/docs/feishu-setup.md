# Feishu Setup Guide

This guide explains how to configure Feishu as a VibeLab Relay channel.

## Prerequisites

- A Feishu organization account or developer permissions
- Access to the [Feishu Open Platform](https://open.feishu.cn/app)

## Create An Internal App

1. Open the [Feishu Open Platform](https://open.feishu.cn/app) and sign in.
2. Create an internal enterprise app.
3. Fill in the app name, for example `VibeLab Relay`, and a short description.
4. Open the app detail page.

## Add Bot Capability

1. In the app detail page, open **Add App Capability**.
2. Add **Bot**.

## Save Credentials

From **Credentials and Basic Info**, save:

- **App ID**, such as `cli_xxxx`
- **App Secret**

## Configure Permissions

Open **Permissions Management** and enable the required message permissions:

| Permission | Identifier | Purpose |
| --- | --- | --- |
| Read and send direct/group messages | `im:message` | Message read/write |
| Send messages as bot | `im:message:send_as_bot` | Bot replies |
| Receive group @bot events | `im:message.group_at_msg:readonly` | Group mentions |
| Receive direct bot messages | `im:message.p2p_msg:readonly` | Direct messages |

It is often simpler to enable all needed `im:message.*` permissions during
initial setup, then narrow them later if required.

## Publish The First Version

You must publish an app version before long-connection settings can be saved:

1. Open **Version Management and Release**.
2. Create a version, for example `1.0.0`.
3. Submit and publish it.

## Configure Event Subscription

After publishing, briefly start an SDK WebSocket client. Feishu requires a
successful connection before saving long-connection event settings.

Temporary local connection:

```bash
node - <<'NODE'
const lark = require('@larksuiteoapi/node-sdk');
const ws = new lark.WSClient({ appId: '<APP_ID>', appSecret: '<APP_SECRET>' });
ws.start({ eventDispatcher: new lark.EventDispatcher({}).register({}) });
console.log('ws client ready');
NODE
```

Keep the script running, then:

1. Open **Events and Callbacks** in the Feishu app.
2. Choose long connection event receiving.
3. Save the setting.
4. Add event `im.message.receive_v1`.
5. Publish a new app version, for example `1.1.0`.

## Get The Chat ID

The daemon can discover the Feishu `chat_id` when the bot receives a message:

1. Create a Feishu group and add the relay bot.
2. Start the daemon.
3. Mention the bot in the group.
4. Inspect the daemon log:

```bash
grep "\[feishu\] Message in chat" ~/.vibelab-tools/agent-skills/relay/runtime/daemon.log
```

The log includes a value like `oc_xxxx`; save it as `feishu.chat_id`.

## Configure The Daemon

Add the required variables to
`~/.vibelab-tools/agent-skills/relay/config.json`:

```json
{
  "feishu": {
    "app_id": "<your-app-id>",
    "app_secret": "<your-app-secret>",
    "chat_id": "<your-chat-id>",
    "proxy": { "enabled": false }
  }
}
```

## How It Works

```text
Agent notification -> daemon -> Feishu provider -> root message on first use -> reply_in_thread
User replies in thread -> Feishu WebSocket -> daemon -> tmux
```

## Thread Isolation

Feishu uses `reply_in_thread` to isolate project sessions:

1. On first notification, the daemon sends a root message to the group.
2. Later notifications are sent as threaded replies to that root message.
3. Replies in that thread are routed back to the matching tmux session.
4. `feishuRootMessageId` is stored in the relay runtime bindings.

No manual thread creation is required. When Claude Code or Codex triggers the
first relay hook in a new project, the daemon creates the Feishu root message
and stores the binding.

## Platform Comparison

| Feature | Telegram | DingTalk | Feishu |
| --- | --- | --- | --- |
| Isolation | Forum Topic | Group | Thread |
| Worker required | Yes | No | No |
| Connection | Webhook -> Worker -> Poll | Stream SDK | SDK WSClient |
| Bot mention required | No | Yes | Depends on permissions |
| Auto-create channel | Yes | No | Yes |

## Known Issues

- Long-connection configuration can be saved only after at least one app
  version is published and an SDK connection succeeds.
- Feishu defaults to direct network access. To use a proxy, set
  `feishu.proxy.enabled=true` with `feishu.proxy.url` or split proxy fields.
- A Feishu app supports one active WebSocket connection at a time. Multiple
  daemon instances should not share the same app.
- The bot cannot create a thread without first sending a root message.

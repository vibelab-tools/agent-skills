# DingTalk Setup Guide

This guide explains how to configure DingTalk as a VibeLab Relay channel.

## Prerequisites

- A DingTalk organization or developer account
- Access to the DingTalk Open Platform

## Create An Internal App

1. Open the [DingTalk Open Platform](https://open-dev.dingtalk.com/) and sign in.
2. Open **Application Development** and create an app.
3. Fill in the app name and description.
4. Open the app detail page after creation.

## Add Bot Capability

1. In the app detail page, open **Add App Capability**.
2. Add the **Bot** capability.
3. Configure the bot:
   - Bot name: choose any clear name.
   - Message receiving mode: choose **Stream Mode**. Do not choose HTTP mode.
4. Publish the bot capability.

## Save Credentials

From **Credentials and Basic Info**, save:

- **Client ID**, formerly AppKey
- **Client Secret**, formerly AppSecret

## Publish An App Version

1. Open **Version Management and Release**.
2. Create a new version.
3. Save and publish it.

The bot configuration will not take effect until an app version is published.

## Create A Group And Add The Bot

1. Create a DingTalk group.
2. Open group settings, then add the app bot from the bot settings page.
3. Confirm the bot appears in the group.

If the bot was only added during group creation, remove it and add it again from
group settings. Stream Mode may not receive messages otherwise.

## Configure The Daemon

Add the required variables to
`~/.vibelab-tools/agent-skills/relay/config.json`:

```json
{
  "dingtalk": {
    "client_id": "<your-client-id>",
    "client_secret": "<your-client-secret>",
    "proxy": { "enabled": false }
  }
}
```

## Get The Conversation ID

DingTalk group binding needs a `conversationId`. The daemon logs it when the bot
receives the first mention:

1. Start the daemon with `/relay:start` or `make start`.
2. Mention the bot in the DingTalk group.
3. Inspect the daemon log:

```bash
grep "\[dingtalk\] Message" ~/.vibelab-tools/agent-skills/relay/runtime/daemon.log
```

The log output includes a value like `cidXXXXXX==`; save it as the
`conversationId`.

## Bind A Session

Save the conversation ID in the project binding file:

```json
{
  "dingtalkConversationId": "cidXXXXXX==",
  "createdAt": "2026-07-08T00:00:00.000Z"
}
```

You can also bind through the local API:

```bash
curl -s -X POST http://127.0.0.1:3580/bind \
  -H 'content-type: application/json' \
  -d '{"sessionId":"<tmux-session>","dingtalkConversationId":"cidXXXXXX=="}'
```

## How It Works

```text
Agent notification -> daemon -> DingTalk provider -> cached sessionWebhook or OpenAPI -> DingTalk group
User mentions bot in group -> DingTalk Stream API -> daemon -> tmux
```

Differences from Telegram:

- DingTalk does not need a Cloudflare Worker.
- DingTalk has no Topic equivalent; use one group per project when isolation is
  required.
- DingTalk group messages must mention the bot.

## Running With Other Channels

VibeLab Relay can enable Telegram, DingTalk, and Feishu at the same time. The
same tmux session can notify multiple channels, and replies from any configured
channel are injected back into the matching session.

Example project binding:

```json
{
  "topicId": "10",
  "dingtalkConversationId": "cidXXXXXX==",
  "createdAt": "2026-07-08T00:00:00.000Z"
}
```

## Known Issues

- Bots added only during group creation may not receive Stream messages.
- `sessionWebhook` expires quickly, so the daemon falls back to OpenAPI when
  needed.
- DingTalk defaults to direct network access. To use a proxy, set
  `dingtalk.proxy.enabled=true` with `dingtalk.proxy.url` or split proxy fields.

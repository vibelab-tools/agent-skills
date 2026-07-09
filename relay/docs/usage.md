# VibeLab Relay Usage Manual

VibeLab Relay provides bidirectional communication between Claude Code or Codex
sessions and IM platforms.

## Core Concepts

- **Agent to IM**: when Claude Code or Codex stops, asks a question, or submits
  a local prompt, relay can send the event to Telegram, DingTalk, or Feishu.
- **IM to Agent**: replies sent from IM are injected into the matching tmux
  session.
- **Project isolation**: each project can bind to its own Telegram Topic,
  DingTalk group, or Feishu thread.

## First Use

### Start The Daemon

```bash
make install
make status
```

The daemon is a long-running background process. Start it once after install or
after a machine reboot; individual Claude Code or Codex sessions do not need to
start it again.

### Automatic Binding

Manual binding is usually unnecessary. On the first stop/question event, the
hook scripts:

1. Check whether the current tmux session already has a binding.
2. Read `.claude/relay.json` for Claude Code or `.codex/relay.json` for Codex.
3. Create a Telegram Topic automatically when Telegram is configured and no
   topic exists yet.
4. Save the binding and finish the notification flow.

### Manual Binding

Use manual binding when a project must use a specific Topic or group:

```text
/relay:bind 123
```

Or edit the project binding file directly:

```json
{
  "topicId": "123",
  "dingtalkConversationId": "cidXXXX==",
  "feishuRootMessageId": "om_xxxx",
  "createdAt": "2026-07-08T00:00:00.000Z"
}
```

## Daily Commands

Show daemon status and active bindings:

```bash
make status
curl -s http://127.0.0.1:3580/status
```

Unbind the current session:

```text
/relay:unbind
```

Stop the daemon:

```text
/relay:stop
```

## Sending Replies

For Telegram, reply directly in the bound Topic.

For DingTalk, mention the bot in the bound group:

```text
@RelayBot please check the latest test result
```

DingTalk group messages must mention the bot; otherwise relay will ignore the
message.

For Feishu, reply in the thread created by relay.

## Multiple Projects

Each project stores its binding in a project-local file:

- Claude Code: `.claude/relay.json`
- Codex: `.codex/relay.json`

Switching directories creates or reuses a separate binding. Telegram creates a
new Topic automatically. DingTalk requires a preconfigured
`dingtalkConversationId`. Feishu creates a root message and uses replies in
thread for isolation.

## Multiple Machines

Each machine has its own daemon and machine ID. The same Telegram group can host
multiple machine/project Topics.

DingTalk and Feishu generally need separate app instances per machine when
multiple daemons should run at the same time, because their WebSocket delivery
is not designed for multiple consumers sharing one app.

## File Reference

| File | Location | Purpose |
| --- | --- | --- |
| `relay.json` | `<project>/.claude/relay.json` | Claude Code project binding |
| `relay.json` | `<project>/.codex/relay.json` | Codex project binding |
| `config.json` | `~/.vibelab-tools/agent-skills/relay/config.json` | Shared daemon configuration and IM credentials |
| `bindings.json` | `~/.vibelab-tools/agent-skills/relay/runtime/bindings.json` | Runtime binding state |
| `relay-service.mjs` | `~/.vibelab-tools/agent-skills/relay/bin/relay-service.mjs` | Service controller |
| `daemon.log` | `~/.vibelab-tools/agent-skills/relay/runtime/daemon.log` | Daemon log |

## Remote Deployment

1. Copy the repository to the target server.
2. Run `make install` on the server.
3. Configure `daemon.port` and IM credentials in
   `~/.vibelab-tools/agent-skills/relay/config.json`.
4. Confirm service status with `make status`.
5. Restart Claude Code or Codex sessions on the server.

## Troubleshooting

### Daemon Does Not Start

```bash
make -C /path/to/agent-skills/relay status
tail -n 30 ~/.vibelab-tools/agent-skills/relay/runtime/daemon.log
lsof -i :3580
```

### Messages Are Not Sent

```bash
curl -s http://127.0.0.1:3580/status | jq
curl -s -X POST http://127.0.0.1:3580/send \
  -H 'content-type: application/json' \
  -d '{"text":"relay test"}'
```

Check that the relevant provider credentials are set. Telegram also requires a
configured `worker.url`.

### DingTalk Does Not Receive Mentions

1. Confirm the bot was added from group settings, not only during group
   creation.
2. Confirm the app version has been published.
3. Check daemon logs for `[dingtalk] Stream connected` and `Socket open`.

### Telegram Topic Creation Fails

1. Confirm the bot is a group admin.
2. Confirm the bot has the "Manage Topics" permission.
3. Confirm Topics are enabled for the group.
4. Confirm `telegram.bot_token`, `telegram.chat_id`, and `worker.url` settings.

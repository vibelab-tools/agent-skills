---
allowed-tools: Bash(curl:*), Bash(tmux:*), Bash(echo:*), Bash(jq:*), Bash(cat:*), Bash(mkdir:*), Bash(hostname:*)
description: Bind current tmux session to a Telegram topic (auto-creates if needed)
---

## Context

- Current tmux session: !`tmux display-message -p '#{session_name}' 2>/dev/null || echo 'NOT_IN_TMUX'`
- Project relay config: !`cat .claude/relay.json 2>/dev/null || echo 'NOT_FOUND'`

## Your task

Bind the current tmux session to a Telegram topic.

### Logic

1. If current tmux session is "NOT_IN_TMUX", tell user they must run inside a tmux session and stop.

2. Determine the topic_id:
   - If user provided a topic_id argument, use that
   - Else if `.claude/relay.json` exists and has a `topicId`, use that
   - Else create a new topic automatically:
     ```bash
     HOSTNAME=$(hostname -s)
     PROJECT_NAME=$(basename "$PWD")
     TOPIC_NAME="${HOSTNAME}:${PROJECT_NAME}"
     CONFIG="$HOME/.vibelab-tools/agent-skills/relay/config.json"
     BOT_TOKEN=$(jq -r '.telegram.bot_token // empty' "$CONFIG")
     CHAT_ID=$(jq -r '.telegram.chat_id // empty' "$CONFIG")
     curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/createForumTopic" \
       -H "Content-Type: application/json" \
       -d "{\"chat_id\": \"${CHAT_ID}\", \"name\": \"${TOPIC_NAME}\"}"
     ```
     Extract `message_thread_id` from the result.

3. Save topic_id to `.claude/relay.json`:
   ```bash
   mkdir -p .claude
   echo '{"topicId": "<id>", "topicName": "<name>", "createdAt": "<ISO date>"}' > .claude/relay.json
   ```

4. Call daemon bind API:
   ```bash
   PORT=$(jq -r '.daemon.port // 3580' ~/.vibelab-tools/agent-skills/relay/config.json 2>/dev/null)
   curl -s -X POST "http://127.0.0.1:${PORT:-3580}/bind" \
     -H "Content-Type: application/json" \
     -d '{"tmuxSession": "<session>", "topicId": "<topic_id>"}'
   ```

5. Report: "Bound tmux session `<session>` to Telegram topic `<topic_name>` (ID: `<topic_id>`)"

Note: This command requires `telegram.bot_token` and `telegram.chat_id` when creating a new topic. If Telegram needs a proxy, configure `telegram.proxy.enabled=true` with `telegram.proxy.url` or split proxy fields.

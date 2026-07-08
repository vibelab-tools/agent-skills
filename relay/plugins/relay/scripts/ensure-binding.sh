#!/bin/bash
# ABOUTME: Auto-ensure tmux session is bound to Telegram topic and DingTalk group.
# ABOUTME: Creates topic and saves config if needed, skips if already bound.

# 2026-03-17: Implement auto-binding with per-project topic persistence
# 2026-03-18: Add DingTalk conversation binding support

SCRIPT_DIR="$(dirname "$0")"
# shellcheck source=plugins/relay/scripts/common.sh
. "$SCRIPT_DIR/common.sh"

DAEMON_URL="$(relay_daemon_url)"
BOT_TOKEN="$(relay_env_value TELEGRAM_BOT_TOKEN)"
CHAT_ID="$(relay_env_value TELEGRAM_CHAT_ID)"

TMUX_SESSION="$(relay_detect_tmux_session)"
if [ -z "$TMUX_SESSION" ]; then
    exit 0
fi
AGENT="$(relay_agent_from_session "$TMUX_SESSION")"

# Check if daemon is running
if ! curl -s --connect-timeout 1 "${DAEMON_URL}/status" >/dev/null 2>&1; then
    exit 0
fi

# Check if already bound (has topicId or dingtalkConversationId)
STATUS=$(curl -s "${DAEMON_URL}/status" 2>/dev/null)
BOUND_TOPIC=$(echo "$STATUS" | jq -r --arg tmuxSession "$TMUX_SESSION" '.bindings[] | select(.tmuxSession == $tmuxSession) | .topicId // empty' 2>/dev/null)
BOUND_DT=$(echo "$STATUS" | jq -r --arg tmuxSession "$TMUX_SESSION" '.bindings[] | select(.tmuxSession == $tmuxSession) | .dingtalkConversationId // empty' 2>/dev/null)

# Find project relay config file
PROJECT_DIR="$(relay_project_dir)"
RELAY_CONFIG="$(relay_config_path "$AGENT" "$PROJECT_DIR")"

TOPIC_ID=""
DT_CONVERSATION_ID=""

# Read existing config
if [ -f "$RELAY_CONFIG" ]; then
    TOPIC_ID=$(jq -r '.topicId // empty' "$RELAY_CONFIG" 2>/dev/null)
    DT_CONVERSATION_ID=$(jq -r '.dingtalkConversationId // empty' "$RELAY_CONFIG" 2>/dev/null)
fi

NEED_BIND=false

# Create Telegram topic if not bound and not in config
if [ -z "$BOUND_TOPIC" ]; then
    if [ -z "$TOPIC_ID" ] && [ -n "$BOT_TOKEN" ] && [ -n "$CHAT_ID" ]; then
        TOPIC_NAME="$(relay_topic_name "$AGENT" "$PROJECT_DIR")"
        TELEGRAM_PROXY_ARGS=()
        if TELEGRAM_PROXY_URL="$(relay_proxy_url TELEGRAM)"; then
            TELEGRAM_PROXY_ARGS=(--proxy "$TELEGRAM_PROXY_URL")
        fi

        RESULT=$(curl -s "${TELEGRAM_PROXY_ARGS[@]}" -X POST "https://api.telegram.org/bot${BOT_TOKEN}/createForumTopic" \
            -H "Content-Type: application/json" \
            -d "$(jq -n --arg chatId "$CHAT_ID" --arg name "$TOPIC_NAME" \
                '{chat_id: $chatId, name: $name}')" 2>/dev/null)

        TOPIC_ID=$(echo "$RESULT" | jq -r '.result.message_thread_id // empty' 2>/dev/null)
        if [ -n "$TOPIC_ID" ]; then
            echo "Created topic: ${TOPIC_NAME} (ID: ${TOPIC_ID})" >&2
        fi
    fi
    if [ -n "$TOPIC_ID" ]; then
        NEED_BIND=true
    fi
fi

# 2026-03-18: Check DingTalk conversation binding
if [ -z "$BOUND_DT" ] && [ -n "$DT_CONVERSATION_ID" ]; then
    NEED_BIND=true
fi

# Bind if needed
if [ "$NEED_BIND" = true ]; then
    # Build bind request JSON with available IDs
    BIND_JSON=$(jq -n \
        --arg tmuxSession "$TMUX_SESSION" \
        --arg topicId "${TOPIC_ID:-}" \
        --arg dingtalkConversationId "${DT_CONVERSATION_ID:-}" \
        '{tmuxSession: $tmuxSession} + (if $topicId != "" then {topicId: $topicId} else {} end) + (if $dingtalkConversationId != "" then {dingtalkConversationId: $dingtalkConversationId} else {} end)')

    curl -s -X POST "${DAEMON_URL}/bind" \
        -H "Content-Type: application/json" \
        -d "$BIND_JSON" >/dev/null 2>&1

    # Save to project config
    mkdir -p "$(dirname "$RELAY_CONFIG")"
    jq -n \
        --arg topicId "${TOPIC_ID:-}" \
        --arg dingtalkConversationId "${DT_CONVERSATION_ID:-}" \
        --arg createdAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
        --arg agent "$AGENT" \
        '{agent: $agent, topicId: $topicId, dingtalkConversationId: $dingtalkConversationId, createdAt: $createdAt}' > "$RELAY_CONFIG"

    echo "Bound ${TMUX_SESSION} -> topic:${TOPIC_ID} dingtalk:${DT_CONVERSATION_ID}" >&2
fi

exit 0

#!/bin/bash
# ABOUTME: AskUserQuestion hook handler for relay.
# ABOUTME: Sends the agent's question to IM via local daemon HTTP API.

# 2026-03-17: Implement AskUserQuestion hook that notifies via local daemon

SCRIPT_DIR="$(dirname "$0")"
# shellcheck source=plugins/relay/scripts/common.sh
. "$SCRIPT_DIR/common.sh"

DAEMON_URL="$(relay_daemon_url)"

# 2026-03-17: Auto-ensure binding before sending notification
"$SCRIPT_DIR/ensure-binding.sh" 2>/dev/null

# Read hook input from stdin
if [ -t 0 ]; then
    HOOK_INPUT="$CLAUDE_TOOL_INPUT"
else
    HOOK_INPUT=$(cat)
fi

if [ -z "$HOOK_INPUT" ]; then
    exit 0
fi

# Extract question text from tool input
QUESTION=$(echo "$HOOK_INPUT" | jq -r '.tool_input.question // .question // empty' 2>/dev/null)

if [ -z "$QUESTION" ]; then
    # Try to get full text representation
    QUESTION=$(echo "$HOOK_INPUT" | jq -r '.tool_input // . | tostring' 2>/dev/null)
fi

if [ -z "$QUESTION" ]; then
    exit 0
fi

TMUX_SESSION="$(relay_detect_tmux_session)"

# POST to daemon
curl -s -X POST "${DAEMON_URL}/notify" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg type "ask_user" \
        --arg text "🤖 $QUESTION" \
        --arg tmuxSession "$TMUX_SESSION" \
        '{type: $type, text: $text, tmuxSession: $tmuxSession}'
    )" >/dev/null 2>&1

exit 0

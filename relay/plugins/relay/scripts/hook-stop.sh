#!/bin/bash
# ABOUTME: Stop hook handler for relay.
# ABOUTME: Sends the agent's last response to IM via local daemon.

# 2026-03-17: Implement Stop hook that notifies via local daemon HTTP API
# 2026-03-17: Use last_assistant_message from hook input instead of parsing transcript

SCRIPT_DIR="$(dirname "$0")"
# shellcheck source=plugins/relay/scripts/common.sh
. "$SCRIPT_DIR/common.sh"

DAEMON_URL="$(relay_daemon_url)"

# 2026-03-17: Auto-ensure binding before sending notification
"$SCRIPT_DIR/ensure-binding.sh" 2>/dev/null

# Read hook input from stdin
if [ -t 0 ]; then
    HOOK_INPUT=""
else
    HOOK_INPUT=$(cat)
fi

# 2026-03-17: Extract last_assistant_message directly from hook input
SUMMARY=$(echo "$HOOK_INPUT" | jq -r '.last_assistant_message // .message // .text // empty' 2>/dev/null | head -c 3000)

# Skip notification if no meaningful content
if [ -z "$SUMMARY" ] || [ ${#SUMMARY} -lt 5 ]; then
    exit 0
fi

TMUX_SESSION="$(relay_detect_tmux_session)"

# POST to daemon
curl -s -X POST "${DAEMON_URL}/notify" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg type "stop" \
        --arg text "🤖 $SUMMARY" \
        --arg tmuxSession "$TMUX_SESSION" \
        '{type: $type, text: $text, tmuxSession: $tmuxSession}'
    )" >/dev/null 2>&1

exit 0

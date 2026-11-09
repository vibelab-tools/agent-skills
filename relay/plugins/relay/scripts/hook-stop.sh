#!/bin/bash
# ABOUTME: Stop hook handler for relay.
# ABOUTME: Sends the agent's last response to IM via local daemon.

# 2026-03-17: Implement Stop hook that notifies via local daemon HTTP API
# 2026-03-17: Use last_assistant_message from hook input instead of parsing transcript

SCRIPT_DIR="$(dirname "$0")"
# shellcheck source=plugins/relay/scripts/common.sh
. "$SCRIPT_DIR/common.sh"

DAEMON_URL="$(relay_daemon_url)"

# Read hook input from stdin
if [ -t 0 ]; then
    HOOK_INPUT=""
else
    HOOK_INPUT=$(cat)
fi

# 2026-03-17: Extract last_assistant_message directly from hook input
SUMMARY=$(echo "$HOOK_INPUT" | jq -r '.last_assistant_message // .message // .text // empty' 2>/dev/null)

TRANSCRIPT_PATH=$(echo "$HOOK_INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)
TURN_ID=$(echo "$HOOK_INPUT" | jq -r '.turn_id // empty' 2>/dev/null)

# Skip notification if no meaningful content
if [ -z "$SUMMARY" ] || [ ${#SUMMARY} -lt 5 ]; then
    exit 0
fi

TMUX_SESSION="$(relay_detect_tmux_session "$HOOK_INPUT")"
[ -n "$TMUX_SESSION" ] || exit 0

"$SCRIPT_DIR/ensure-binding.sh" "$TMUX_SESSION" 2>/dev/null

# POST to daemon
curl -s -X POST "${DAEMON_URL}/notify" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg type "stop" \
        --arg text "$SUMMARY" \
        --arg tmuxSession "$TMUX_SESSION" \
        --arg transcriptPath "$TRANSCRIPT_PATH" \
        --arg turnId "$TURN_ID" \
        '{type: $type, text: $text, tmuxSession: $tmuxSession, transcriptPath: $transcriptPath, turnId: $turnId}'
    )" >/dev/null 2>&1

exit 0

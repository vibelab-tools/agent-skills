#!/bin/bash
# ABOUTME: UserPromptSubmit hook handler for relay.
# ABOUTME: Relays user prompt to IM and cancels any pending remote replies.

# 2026-03-17: Implement UserPromptSubmit hook to signal local user activity
# 2026-04-03: Also relay user's prompt text to IM for full conversation visibility

SCRIPT_DIR="$(dirname "$0")"
# shellcheck source=plugins/relay/scripts/common.sh
. "$SCRIPT_DIR/common.sh"

DAEMON_URL="$(relay_daemon_url)"

# 2026-04-03: Auto-ensure binding before sending
"$SCRIPT_DIR/ensure-binding.sh" 2>/dev/null

# Read hook input from stdin
if [ -t 0 ]; then
    HOOK_INPUT=""
else
    HOOK_INPUT=$(cat)
fi

# Signal daemon that user submitted a prompt locally
curl -s -X POST "${DAEMON_URL}/cancel-pending" >/dev/null 2>&1

# 2026-04-03: Extract user prompt and relay to IM
PROMPT=$(echo "$HOOK_INPUT" | jq -r '.prompt // empty' 2>/dev/null)

if [ -z "$PROMPT" ] || [ ${#PROMPT} -lt 1 ]; then
    exit 0
fi

# Truncate long prompts to avoid flooding IM
PROMPT=$(echo "$PROMPT" | head -c 3000)

TMUX_SESSION="$(relay_detect_tmux_session)"

# 2026-04-03: Prefix with sender indicator so IM readers can distinguish user vs assistant
TEXT="👤 ${PROMPT}"

# POST to daemon
curl -s -X POST "${DAEMON_URL}/notify" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg type "user_prompt" \
        --arg text "$TEXT" \
        --arg tmuxSession "$TMUX_SESSION" \
        '{type: $type, text: $text, tmuxSession: $tmuxSession}'
    )" >/dev/null 2>&1

exit 0

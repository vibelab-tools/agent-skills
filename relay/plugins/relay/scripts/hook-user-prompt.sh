#!/bin/bash
# ABOUTME: UserPromptSubmit hook handler for relay.
# ABOUTME: Relays user prompt to IM and cancels any pending remote replies.

# 2026-03-17: Implement UserPromptSubmit hook to signal local user activity
# 2026-04-03: Also relay user's prompt text to IM for full conversation visibility

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

# Signal daemon that a user prompt was submitted.
curl -s -X POST "${DAEMON_URL}/cancel-pending" >/dev/null 2>&1

# 2026-04-03: Extract user prompt and relay to IM
PROMPT=$(echo "$HOOK_INPUT" | jq -r '.prompt // empty' 2>/dev/null)
TRANSCRIPT_PATH=$(echo "$HOOK_INPUT" | jq -r '.transcript_path // empty' 2>/dev/null)
TURN_ID=$(echo "$HOOK_INPUT" | jq -r '.turn_id // empty' 2>/dev/null)

if [ -z "$PROMPT" ] || [ ${#PROMPT} -lt 1 ]; then
    exit 0
fi

TMUX_SESSION="$(relay_detect_tmux_session)"

# Prompts injected from IM are already visible there. Consume their short-lived
# origin marker so only prompts typed directly in Codex are relayed back.
if command -v sha256sum >/dev/null 2>&1; then
    PROMPT_FINGERPRINT=$(printf '%s' "$PROMPT" | sha256sum | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
    PROMPT_FINGERPRINT=$(printf '%s' "$PROMPT" | shasum -a 256 | awk '{print $1}')
else
    PROMPT_FINGERPRINT=""
fi

if [ -n "$TMUX_SESSION" ] && [ -n "$PROMPT_FINGERPRINT" ]; then
    ORIGIN=$(curl -s -X POST "${DAEMON_URL}/prompt-origin" \
        -H "Content-Type: application/json" \
        -d "$(jq -n \
            --arg tmuxSession "$TMUX_SESSION" \
            --arg fingerprint "$PROMPT_FINGERPRINT" \
            '{tmuxSession: $tmuxSession, fingerprint: $fingerprint}'
        )" 2>/dev/null | jq -r '.origin // "local"' 2>/dev/null)
    if [ "$ORIGIN" = "im" ]; then
        exit 0
    fi
fi

# Auto-ensure a binding only when a local prompt will be relayed.
"$SCRIPT_DIR/ensure-binding.sh" 2>/dev/null

# Truncate long prompts to avoid flooding IM
PROMPT=$(echo "$PROMPT" | head -c 3000)

# 2026-04-03: Prefix with sender indicator so IM readers can distinguish user vs assistant
TEXT="🧑‍💻 ${PROMPT}"

# POST to daemon
curl -s -X POST "${DAEMON_URL}/notify" \
    -H "Content-Type: application/json" \
    -d "$(jq -n \
        --arg type "user_prompt" \
        --arg text "$TEXT" \
        --arg tmuxSession "$TMUX_SESSION" \
        --arg transcriptPath "$TRANSCRIPT_PATH" \
        --arg turnId "$TURN_ID" \
        '{type: $type, text: $text, tmuxSession: $tmuxSession}
          + (if $transcriptPath != "" then {transcriptPath: $transcriptPath} else {} end)
          + (if $turnId != "" then {turnId: $turnId} else {} end)'
    )" >/dev/null 2>&1

exit 0

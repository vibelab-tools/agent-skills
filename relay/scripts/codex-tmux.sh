#!/bin/bash
# ABOUTME: Codex CLI tmux wrapper that reuses sessions per directory.
# ABOUTME: Attaches to existing session if one exists, otherwise creates new.

case "$1" in
    -h|--help|--version|-V)
        exec codex "$@"
        ;;
esac

hash_path() {
    if command -v md5sum >/dev/null 2>&1; then
        printf '%s' "$1" | md5sum | cut -c1-6
    else
        printf '%s' "$1" | md5 -q | cut -c1-6
    fi
}

DIR_NAME=$(basename "$(pwd)")
DIR_HASH=$(hash_path "$(pwd)")
SESSION_NAME="codex-${DIR_NAME}-${DIR_HASH}"

if tmux has-session -t "$SESSION_NAME" 2>/dev/null; then
    exec tmux attach-session -t "$SESSION_NAME"
fi

if [[ "$1" == "--no-tmux" ]]; then
    shift
    exec codex "$@"
fi

if [ -n "$TMUX" ]; then
    exec codex "$@"
fi

ESCAPED_ARGS=""
for arg in "$@"; do
    escaped_arg="${arg//\'/\'\\\'\'}"
    ESCAPED_ARGS="$ESCAPED_ARGS '$escaped_arg'"
done

CURRENT_DIR=$(pwd)
escaped_dir="${CURRENT_DIR//\'/\'\\\'\'}"

tmux new-session -s "$SESSION_NAME" "cd '$escaped_dir'; codex $ESCAPED_ARGS"

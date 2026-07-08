#!/bin/bash
# ABOUTME: Shared shell helpers for relay hook scripts.
# ABOUTME: Detects tmux sessions, agent type, and per-agent project config paths.

relay_daemon_url() {
    local port="${RELAY_DAEMON_PORT:-}"
    if [ -z "$port" ]; then
        port="$(relay_env_value RELAY_DAEMON_PORT)"
    fi
    [ -n "$port" ] || port="3580"
    printf 'http://127.0.0.1:%s' "$port"
}

relay_env_value() {
    local key="$1"
    local value="${!key:-}"
    local file

    if [ -n "$value" ]; then
        printf '%s' "$value"
        return
    fi

    for file in "$HOME/.codex/relay-settings.json" "$HOME/.claude/settings.json"; do
        if [ -f "$file" ]; then
            value=$(jq -r --arg key "$key" '(.env // .)[$key] // empty' "$file" 2>/dev/null)
            if [ -n "$value" ]; then
                printf '%s' "$value"
                return
            fi
        fi
    done
}

relay_truthy() {
    case "$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')" in
        1|true|yes|on) return 0 ;;
        *) return 1 ;;
    esac
}

relay_proxy_url() {
    local prefix="$1"
    local enabled
    local url
    local protocol
    local host
    local port
    local username
    local password
    local auth=""

    enabled="$(relay_env_value "${prefix}_PROXY_ENABLED")"
    relay_truthy "$enabled" || return 1

    url="$(relay_env_value "${prefix}_PROXY_URL")"
    if [ -z "$url" ]; then
        url="$(relay_env_value "${prefix}_PROXY")"
    fi
    if [ -n "$url" ]; then
        printf '%s' "$url"
        return 0
    fi

    protocol="$(relay_env_value "${prefix}_PROXY_PROTOCOL")"
    host="$(relay_env_value "${prefix}_PROXY_HOST")"
    port="$(relay_env_value "${prefix}_PROXY_PORT")"
    username="$(relay_env_value "${prefix}_PROXY_USERNAME")"
    password="$(relay_env_value "${prefix}_PROXY_PASSWORD")"

    [ -n "$protocol" ] || protocol="http"
    [ -n "$host" ] || return 1
    if [ -n "$username" ]; then
        auth="$username"
        if [ -n "$password" ]; then
            auth="$auth:$password"
        fi
        auth="$auth@"
    fi

    printf '%s://%s%s%s' "$protocol" "$auth" "$host" "${port:+:$port}"
}

relay_detect_tmux_session() {
    if [ -n "$TMUX" ]; then
        tmux display-message -p '#{session_name}' 2>/dev/null
        return
    fi

    tmux list-sessions -F '#{session_name}' 2>/dev/null |
        grep -E '^(claude|codex)-' |
        head -1
}

relay_agent_from_session() {
    case "$1" in
        codex-*) echo "codex" ;;
        claude-*) echo "claude" ;;
        *) echo "${RELAY_AGENT:-claude}" ;;
    esac
}

relay_project_dir() {
    if [ -n "${CLAUDE_PROJECT_DIR:-}" ]; then
        printf '%s' "$CLAUDE_PROJECT_DIR"
        return
    fi
    if [ -n "${CODEX_PROJECT_DIR:-}" ]; then
        printf '%s' "$CODEX_PROJECT_DIR"
        return
    fi
    printf '%s' "$PWD"
}

relay_config_path() {
    local agent="$1"
    local project_dir="$2"
    case "$agent" in
        codex) printf '%s/.codex/relay.json' "$project_dir" ;;
        *) printf '%s/.claude/relay.json' "$project_dir" ;;
    esac
}

relay_topic_name() {
    local agent="$1"
    local project_dir="$2"
    local hostname
    local project_name

    hostname=$(hostname -s 2>/dev/null || echo "unknown")
    project_name=$(basename "$project_dir")

    if [ "$agent" = "codex" ]; then
        printf '%s:codex:%s' "$hostname" "$project_name"
    else
        printf '%s:%s' "$hostname" "$project_name"
    fi
}

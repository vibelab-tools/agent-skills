#!/bin/bash
# ABOUTME: Shared shell helpers for relay hook scripts.
# ABOUTME: Detects tmux sessions, agent type, and per-agent project config paths.

relay_daemon_url() {
    local port
    port="$(relay_env_value RELAY_DAEMON_PORT)"
    [ -n "$port" ] || port="3580"
    printf 'http://127.0.0.1:%s' "$port"
}

relay_env_value() {
    local key="$1"
    local value
    local file
    local query

    case "$key" in
        RELAY_DAEMON_PORT) query='(.daemon.port // .env.RELAY_DAEMON_PORT // empty)' ;;
        TELEGRAM_BOT_TOKEN) query='(.telegram.bot_token // .env.TELEGRAM_BOT_TOKEN // empty)' ;;
        TELEGRAM_CHAT_ID) query='(.telegram.chat_id // .env.TELEGRAM_CHAT_ID // empty)' ;;
        TELEGRAM_PROXY_ENABLED) query='(.telegram.proxy.enabled // .env.TELEGRAM_PROXY_ENABLED // empty)' ;;
        TELEGRAM_PROXY_URL) query='(.telegram.proxy.url // .env.TELEGRAM_PROXY_URL // .env.TELEGRAM_PROXY // empty)' ;;
        TELEGRAM_PROXY_PROTOCOL) query='(.telegram.proxy.protocol // .env.TELEGRAM_PROXY_PROTOCOL // empty)' ;;
        TELEGRAM_PROXY_HOST) query='(.telegram.proxy.host // .env.TELEGRAM_PROXY_HOST // empty)' ;;
        TELEGRAM_PROXY_PORT) query='(.telegram.proxy.port // .env.TELEGRAM_PROXY_PORT // empty)' ;;
        TELEGRAM_PROXY_USERNAME) query='(.telegram.proxy.username // .env.TELEGRAM_PROXY_USERNAME // empty)' ;;
        TELEGRAM_PROXY_PASSWORD) query='(.telegram.proxy.password // .env.TELEGRAM_PROXY_PASSWORD // empty)' ;;
        DINGTALK_PROXY_ENABLED) query='(.dingtalk.proxy.enabled // .env.DINGTALK_PROXY_ENABLED // empty)' ;;
        DINGTALK_PROXY_URL) query='(.dingtalk.proxy.url // .env.DINGTALK_PROXY_URL // .env.DINGTALK_PROXY // empty)' ;;
        DINGTALK_PROXY_PROTOCOL) query='(.dingtalk.proxy.protocol // .env.DINGTALK_PROXY_PROTOCOL // empty)' ;;
        DINGTALK_PROXY_HOST) query='(.dingtalk.proxy.host // .env.DINGTALK_PROXY_HOST // empty)' ;;
        DINGTALK_PROXY_PORT) query='(.dingtalk.proxy.port // .env.DINGTALK_PROXY_PORT // empty)' ;;
        DINGTALK_PROXY_USERNAME) query='(.dingtalk.proxy.username // .env.DINGTALK_PROXY_USERNAME // empty)' ;;
        DINGTALK_PROXY_PASSWORD) query='(.dingtalk.proxy.password // .env.DINGTALK_PROXY_PASSWORD // empty)' ;;
        FEISHU_PROXY_ENABLED) query='(.feishu.proxy.enabled // .env.FEISHU_PROXY_ENABLED // empty)' ;;
        FEISHU_PROXY_URL) query='(.feishu.proxy.url // .env.FEISHU_PROXY_URL // .env.FEISHU_PROXY // empty)' ;;
        FEISHU_PROXY_PROTOCOL) query='(.feishu.proxy.protocol // .env.FEISHU_PROXY_PROTOCOL // empty)' ;;
        FEISHU_PROXY_HOST) query='(.feishu.proxy.host // .env.FEISHU_PROXY_HOST // empty)' ;;
        FEISHU_PROXY_PORT) query='(.feishu.proxy.port // .env.FEISHU_PROXY_PORT // empty)' ;;
        FEISHU_PROXY_USERNAME) query='(.feishu.proxy.username // .env.FEISHU_PROXY_USERNAME // empty)' ;;
        FEISHU_PROXY_PASSWORD) query='(.feishu.proxy.password // .env.FEISHU_PROXY_PASSWORD // empty)' ;;
        *) return ;;
    esac

    for file in \
        "$HOME/.vibelab-tools/agent-skills/relay/config.json" \
        "$HOME/.codex/relay-settings.json" \
        "$HOME/.claude/settings.json"; do
        [ -n "$file" ] || continue
        if [ -f "$file" ]; then
            value=$(jq -r "$query" "$file" 2>/dev/null)
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

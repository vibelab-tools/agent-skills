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
    local socket pane_session hook_cwd panes matches session pane_dir
    local tmux_args=()

    socket="${TMUX%%,*}"
    [ -z "$socket" ] || tmux_args=(-S "$socket")
    hook_cwd=$(printf '%s' "${1:-}" | jq -r '.cwd | select(type == "string" and length > 0)' 2>/dev/null)

    # A shared Codex app-server inherits its own (possibly stale) tmux pane.
    # The hook's session cwd is authoritative; only a unique session can match.
    if [ -n "$hook_cwd" ]; then
        hook_cwd=$(cd -- "$hook_cwd" 2>/dev/null && pwd -P) || return 1
        panes=$(tmux "${tmux_args[@]}" list-panes -a -F $'#{session_name}\t#{pane_current_path}' 2>/dev/null) || return 1
        matches=$(
            while IFS=$'\t' read -r session pane_dir; do
                case "$session" in codex-*|claude-*) ;; *) continue ;; esac
                pane_dir=$(cd -- "$pane_dir" 2>/dev/null && pwd -P) || continue
                [ "$pane_dir" != "$hook_cwd" ] || printf '%s\n' "$session"
            done <<< "$panes" | sort -u
        )
        if [ -n "$matches" ] && [[ "$matches" != *$'\n'* ]]; then
            printf '%s' "$matches"
            return 0
        fi
        printf 'Relay skipped notification: no unique tmux session for hook cwd %s\n' "$hook_cwd" >&2
        if [ -d "$HOME/.vibelab-tools/agent-skills/relay/runtime" ]; then
            (umask 077; printf '%s WARN no unique tmux session for hook cwd %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$hook_cwd" >> "$HOME/.vibelab-tools/agent-skills/relay/runtime/hook-routing.log")
        fi
        return 1
    fi

    [ -n "${TMUX:-}" ] || return 1
    [[ "${TMUX_PANE:-}" =~ ^%[0-9]+$ ]] || return 1
    pane_session=$(tmux "${tmux_args[@]}" display-message -p -t "$TMUX_PANE" '#{pane_id}:#{session_name}' 2>/dev/null) || return 1
    case "$pane_session" in
        "$TMUX_PANE":?*) printf '%s' "${pane_session#"$TMUX_PANE":}" ;;
        *) return 1 ;;
    esac
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
    local project_dir="$2"
    local project_name

    project_name=$(basename "$project_dir")
    printf '%s' "$project_name"
}

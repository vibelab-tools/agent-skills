#!/usr/bin/env bash
# Keep interactive Codex running across SSH disconnects, one session per directory/profile.
set -e
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
codex_bin=$(type -P codex) || { printf 'Codex CLI is not on PATH.\n' >&2; exit 127; }

no_tmux=false
if [[ ${1-} == --no-tmux ]]; then
    shift
    no_tmux=true
fi

# Keep explicit directory/profile selections when creating a tmux session.
cwd=$PWD
requested_cwd=
profile=
no_daemon=false
expect_value=
for arg in "$@"; do
    if [[ -n $expect_value ]]; then
        if [[ $expect_value == cwd ]]; then requested_cwd=$arg; fi
        if [[ $expect_value == profile ]]; then profile=$arg; fi
        expect_value=
        continue
    fi
    case "$arg" in
        --) break ;;
        --no-daemon) no_daemon=true ;;
        -C|--cd) expect_value=cwd ;;
        --cd=*) requested_cwd=${arg#*=} ;;
        -C?*) requested_cwd=${arg#-C}; requested_cwd=${requested_cwd#=} ;;
        -p|--profile) expect_value=profile ;;
        --profile=*) profile=${arg#*=} ;;
        -p?*) profile=${arg#-p}; profile=${profile#=} ;;
        -c|--config|-m|--model|-s|--sandbox|-a|--ask-for-approval|--add-dir|-i|--image|--remote|--remote-auth-token-env|--local-provider|--enable|--disable|--output-schema|-o|--output-last-message|--color)
            expect_value=other ;;
    esac
done
if [[ ${CODEX_TMUX_DIRECT-} == 1 ]] && ! $no_daemon; then
    set -- --no-daemon "$@"
fi
if [[ -n $requested_cwd ]]; then
    if resolved_cwd=$(cd -- "$requested_cwd" 2>/dev/null && pwd -P); then cwd=$resolved_cwd; fi
fi
case "$profile" in
    kimi) direct_host=api.moonshot.cn ;;
    deepseek) direct_host=api.deepseek.com ;;
    *) direct_host= ;;
esac
if [[ -n $direct_host ]]; then
    export NO_PROXY="${NO_PROXY:+$NO_PROXY,}$direct_host"
    export no_proxy="${no_proxy:+$no_proxy,}$direct_host"
fi

codex_command=("$codex_bin")
if [[ -t 0 && -t 1 ]]; then
    codex_command=(python3 "$script_dir/codex-resume-picker.py" --codex-bin "$codex_bin" --)
fi

if $no_tmux || [[ -n ${TMUX-} || ! -t 0 || ! -t 1 ]]; then
    exec "${codex_command[@]}" "$@"
fi

# Management commands and automation must keep their ordinary CLI behavior.
expect_value=false
for arg in "$@"; do
    if $expect_value; then expect_value=false; continue; fi
    case "$arg" in
        -h|--help|-V|--version) exec "$codex_bin" "$@" ;;
        -c|--config|-m|--model|-p|--profile|-s|--sandbox|-a|--ask-for-approval|-C|--cd|--add-dir|-i|--image|--remote|--remote-auth-token-env|--local-provider|--enable|--disable)
            expect_value=true ;;
        --*=*|-*) ;;
        agents|exec|e|review|login|logout|mcp|plugin|app-server|remote-control|app|completion|update|doctor|sandbox|debug|apply|a|queue|archive|delete|migrate-rollouts|migrate-nfs-db|unarchive|cloud|exec-server|features|help)
            exec "$codex_bin" "$@" ;;
        *) break ;;
    esac
done

tmux_bin=$(type -P tmux) || { printf 'tmux is not on PATH.\n' >&2; exit 127; }
name=${cwd##*/}
name=${name//./_}
name=${name//:/_}
if command -v md5sum >/dev/null 2>&1; then
    hash=$(printf '%s' "$cwd" | md5sum)
else
    hash=$(printf '%s' "$cwd" | md5 -q)
fi
session_prefix=codex
if [[ ${CODEX_TMUX_DIRECT-} == 1 ]]; then session_prefix=codex-direct; fi
session="$session_prefix-${name:-root}-${hash:0:6}"
if [[ -n $profile ]]; then session+="-$profile"; fi

if "$tmux_bin" has-session -t "=$session" 2>/dev/null; then
    exec "$tmux_bin" attach-session -t "=$session"
fi

# argv is passed directly to tmux, so spaces and quotes remain intact.
# Copy only the invoking shell's proxy settings and executable search path.
pane=(/usr/bin/env)
proxy_values=("PATH=$PATH")
for key in http_proxy https_proxy all_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY NO_PROXY no_proxy; do
    if [[ ${!key+x} ]]; then
        proxy_values+=("$key=${!key}")
    else
        pane+=(-u "$key")
    fi
done
exec "$tmux_bin" new-session -A -s "$session" -c "$PWD" "${pane[@]}" "${proxy_values[@]}" "${codex_command[@]}" "$@"

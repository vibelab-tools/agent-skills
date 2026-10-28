#!/usr/bin/env bash
# Run Codex in tmux without proxy variables or a proxy-backed shared daemon.
set -e
unset http_proxy https_proxy all_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY
export CODEX_TMUX_DIRECT=1
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
launcher="$script_dir/codex-tmux"
if [[ ! -f $launcher ]]; then launcher="$script_dir/codex-tmux.sh"; fi
exec "$launcher" "$@"

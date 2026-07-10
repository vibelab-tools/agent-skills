#!/usr/bin/env python3
"""Merge missing default config keys without overwriting user values."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def merge_missing(current: Any, defaults: Any) -> Any:
    if isinstance(current, dict) and isinstance(defaults, dict):
        changed = False
        merged = dict(current)
        for key, value in defaults.items():
            if key not in merged:
                merged[key] = value
                changed = True
            else:
                next_value = merge_missing(merged[key], value)
                if next_value is not merged[key]:
                    merged[key] = next_value
                    changed = True
        return merged if changed else current
    return current


def apply_compat_migrations(config: dict[str, Any]) -> bool:
    changed = False
    providers = (
        config.get("search", {}).get("providers", {})
        if isinstance(config.get("search"), dict)
        else {}
    )
    if not isinstance(providers, dict):
        return changed
    hackernews = providers.get("hackernews")
    if isinstance(hackernews, dict) and hackernews.get("tags") == "story,comment":
        hackernews["tags"] = ""
        changed = True
    gemini = providers.get("gemini")
    if isinstance(gemini, dict):
        if gemini.get("endpoint") == "https://generativelanguage.googleapis.com/v1beta/interactions":
            gemini["endpoint"] = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
            changed = True
        old_gemini_models = [
            "gemini-3.5-flash",
            "gemini-3.1-pro-preview",
            "gemini-3-flash-preview",
            "gemini-3.1-flash-lite",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite",
        ]
        if gemini.get("models") == old_gemini_models:
            gemini["models"] = [
                "gemini-3.1-pro-preview",
                "gemini-3-flash-preview",
                "gemini-2.5-pro",
                "gemini-2.5-flash",
                "gemini-3.5-flash",
                "gemini-2.5-flash-lite",
            ]
            changed = True
    kimi = providers.get("kimi")
    if isinstance(kimi, dict):
        if kimi.get("endpoint") == "https://api.moonshot.ai/v1/chat/completions":
            kimi["endpoint"] = "https://api.moonshot.cn/v1/chat/completions"
            changed = True
        if kimi.get("model") == "kimi-k2-0905-preview":
            kimi["model"] = "moonshot-v1-auto"
            changed = True
    deepseek = providers.get("deepseek")
    if isinstance(deepseek, dict) and deepseek.get("model") == "deepseek-chat":
        deepseek["model"] = "deepseek-v4-pro"
        changed = True
    qwen = providers.get("qwen")
    if isinstance(qwen, dict):
        if qwen.get("model") in {"qwen-plus", "qwen3.7-plus"}:
            qwen["model"] = "qwen-plus-latest"
            changed = True
        search_options = qwen.get("search_options")
        if search_options == {}:
            qwen["search_options"] = {"search_strategy": "max"}
            changed = True
    for obsolete in ("gdelt", "semantic_scholar", "spiderfoot", "wayback"):
        if obsolete in providers:
            del providers[obsolete]
        changed = True
    return changed


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=Path)
    parser.add_argument("defaults", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    config_path = args.config.expanduser()
    defaults_path = args.defaults.expanduser()
    current = json.loads(config_path.read_text(encoding="utf-8"))
    defaults = json.loads(defaults_path.read_text(encoding="utf-8"))
    merged = merge_missing(current, defaults)
    changed = merged is not current
    if isinstance(merged, dict) and apply_compat_migrations(merged):
        changed = True
    if changed:
        config_path.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        config_path.chmod(0o600)
        print(f"Merged missing defaults into {config_path}")
    else:
        print(f"No missing defaults in {config_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

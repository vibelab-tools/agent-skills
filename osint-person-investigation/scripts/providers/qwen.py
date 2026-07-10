"""Qwen OpenAI-compatible Chat Completions provider."""

from __future__ import annotations

from typing import Any

from .common import configured_api_key, extract_openai_chat_text, normalize_result, openai_chat_provider


def configured(provider: dict[str, Any]) -> bool:
    return configured_api_key(provider)


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del max_pages, count_override
    overrides: dict[str, Any] = {}
    if provider.get("enable_search") is True:
        overrides["enable_search"] = True
    if isinstance(provider.get("search_options"), dict):
        overrides["search_options"] = provider["search_options"]
    data = openai_chat_provider(
        "qwen", query, provider, global_config, overrides, use_proxy=False
    )
    return [
        normalize_result(
            provider="qwen",
            query=query,
            page=1,
            rank=1,
            title="Qwen search-enabled answer",
            url="",
            snippet=extract_openai_chat_text(data),
            source="llm_search",
            extra={"raw_keys": sorted(data.keys())},
        )
    ]

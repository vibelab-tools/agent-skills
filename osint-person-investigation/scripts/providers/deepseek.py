"""DeepSeek-compatible Chat Completions provider."""

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
    data = openai_chat_provider(
        "deepseek", query, provider, global_config, use_proxy=False
    )
    return [
        normalize_result(
            provider="deepseek",
            query=query,
            page=1,
            rank=1,
            title="DeepSeek answer",
            url="",
            snippet=extract_openai_chat_text(data),
            source="llm_lead_generation",
            extra={
                "note": (
                    "Use as lead generation unless the configured endpoint "
                    "returns verifiable web citations."
                ),
                "raw_keys": sorted(data.keys()),
            },
        )
    ]

"""Kimi/Moonshot Chat Completions provider."""

from __future__ import annotations

from typing import Any

from .common import (
    SearchError,
    configured_api_key,
    extract_openai_chat_text,
    float_config,
    int_config,
    llm_search_prompt,
    merge_extra_body,
    normalize_result,
    openai_chat_provider,
    request_json,
    timeout,
)


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
    if provider.get("web_search") is True:
        overrides["tools"] = [
            {"type": "builtin_function", "function": {"name": "$web_search"}}
        ]
    data = openai_chat_provider(
        "kimi", query, provider, global_config, overrides, use_proxy=False
    )
    max_rounds = int_config(provider.get("max_tool_rounds"), 3, 0, 8)
    rounds = 0
    while rounds < max_rounds:
        choices = data.get("choices")
        if not isinstance(choices, list) or not choices:
            break
        message = choices[0].get("message") if isinstance(choices[0], dict) else None
        tool_calls = message.get("tool_calls") if isinstance(message, dict) else None
        if not isinstance(tool_calls, list) or not tool_calls:
            break
        original_messages = [
            {
                "role": "system",
                "content": (
                    "You support lawful public-source OSINT research. Return "
                    "public web sources with URLs and uncertainty notes. Do not "
                    "produce private personal data."
                ),
            },
            {"role": "user", "content": llm_search_prompt(query)},
            message,
        ]
        for call in tool_calls:
            if not isinstance(call, dict):
                continue
            function = call.get("function") if isinstance(call.get("function"), dict) else {}
            original_messages.append(
                {
                    "role": "tool",
                    "tool_call_id": call.get("id"),
                    "name": function.get("name") or "$web_search",
                    "content": function.get("arguments") or "{}",
                }
            )
        body = {
            "model": provider.get("model"),
            "messages": original_messages,
            "temperature": float_config(provider.get("temperature"), 0.2),
            "max_tokens": int_config(provider.get("max_tokens"), 4096, 1, 200000),
            "tools": overrides.get("tools", []),
        }
        merge_extra_body(body, provider)
        data = request_json(
            "POST",
            str(provider.get("endpoint")),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {provider.get('api_key')}",
            },
            body=body,
            timeout=timeout(global_config),
            use_proxy=False,
        )
        if not isinstance(data, dict):
            raise SearchError("unexpected kimi response")
        rounds += 1
    return [
        normalize_result(
            provider="kimi",
            query=query,
            page=1,
            rank=1,
            title="Kimi search-enabled answer",
            url="",
            snippet=extract_openai_chat_text(data),
            source="llm_search",
            extra={"raw_keys": sorted(data.keys())},
        )
    ]

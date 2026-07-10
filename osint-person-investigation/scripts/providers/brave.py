"""Brave Search API provider."""

from __future__ import annotations

from typing import Any

from .common import (
    SearchError,
    configured_api_key,
    int_config,
    normalize_result,
    request_json,
    stop_when_no_new_urls,
    timeout,
    user_agent,
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
    api_key = provider.get("api_key")
    if not api_key:
        raise SearchError("brave.api_key is empty")
    count = int_config(count_override or provider.get("count"), 20, 1, 20)
    endpoint = str(
        provider.get("endpoint") or "https://api.search.brave.com/res/v1/web/search"
    )
    headers = {
        "Accept": "application/json",
        "X-Subscription-Token": str(api_key),
        "User-Agent": user_agent(global_config),
    }
    results: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    stop_when_no_new = stop_when_no_new_urls(global_config)
    for page in range(1, min(max_pages, 10) + 1):
        params = {
            "q": query,
            "count": count,
            "offset": page - 1,
            "country": provider.get("country"),
            "search_lang": provider.get("search_lang"),
            "ui_lang": provider.get("ui_lang"),
            "safesearch": provider.get("safesearch"),
            "extra_snippets": str(bool(provider.get("extra_snippets"))).lower(),
        }
        data = request_json(
            "GET", endpoint, headers=headers, params=params, timeout=timeout(global_config)
        )
        if not isinstance(data, dict):
            raise SearchError("unexpected brave response")
        web = data.get("web", {})
        page_items = web.get("results", []) if isinstance(web, dict) else []
        if not page_items:
            break
        page_new_urls = 0
        for index, item in enumerate(page_items, start=1):
            if not isinstance(item, dict):
                continue
            url = item.get("url")
            if isinstance(url, str) and url:
                if url not in seen_urls:
                    page_new_urls += 1
                seen_urls.add(url)
            snippets = [item.get("description") or ""]
            extra_snippets = item.get("extra_snippets")
            if isinstance(extra_snippets, list):
                snippets.extend(str(value) for value in extra_snippets if value)
            profile = item.get("profile")
            results.append(
                normalize_result(
                    provider="brave",
                    query=query,
                    page=page,
                    rank=(page - 1) * count + index,
                    title=item.get("title"),
                    url=url,
                    snippet=" ".join(snippet for snippet in snippets if snippet),
                    source=profile.get("name") if isinstance(profile, dict) else "",
                    extra={"age": item.get("age")},
                )
            )
        if stop_when_no_new and page_new_urls == 0:
            break
    return results

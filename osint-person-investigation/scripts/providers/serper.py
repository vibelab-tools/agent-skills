"""Serper.dev Google Search API provider."""

from __future__ import annotations

from typing import Any

from .common import SearchError, configured_api_key, int_config, normalize_result, request_json, stop_when_no_new_urls, timeout


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
        raise SearchError("serper.api_key is empty")
    num = int_config(count_override or provider.get("num"), 10, 1, 100)
    endpoint = str(provider.get("endpoint") or "https://google.serper.dev/search")
    headers = {
        "Content-Type": "application/json",
        "X-API-KEY": str(api_key),
    }
    results: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    stop_when_no_new = stop_when_no_new_urls(global_config)
    for page in range(1, max_pages + 1):
        body = {
            "q": query,
            "num": num,
            "page": page,
            "gl": provider.get("gl"),
            "hl": provider.get("hl"),
        }
        if provider.get("location"):
            body["location"] = provider.get("location")
        data = request_json(
            "POST", endpoint, headers=headers, body=body, timeout=timeout(global_config)
        )
        if not isinstance(data, dict):
            raise SearchError("unexpected serper response")
        page_items = data.get("organic", [])
        if not page_items:
            break
        page_new_urls = 0
        for index, item in enumerate(page_items, start=1):
            if not isinstance(item, dict):
                continue
            url = item.get("link")
            if isinstance(url, str) and url:
                if url not in seen_urls:
                    page_new_urls += 1
                seen_urls.add(url)
            results.append(
                normalize_result(
                    provider="serper",
                    query=query,
                    page=page,
                    rank=(page - 1) * num + index,
                    title=item.get("title"),
                    url=url,
                    snippet=item.get("snippet"),
                    source=item.get("source"),
                    extra={"date": item.get("date"), "position": item.get("position")},
                )
            )
        if stop_when_no_new and page_new_urls == 0:
            break
    return results

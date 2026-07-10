"""Google Custom Search JSON API provider."""

from __future__ import annotations

from typing import Any

from .common import SearchError, int_config, normalize_result, request_json, stop_when_no_new_urls, timeout


def configured(provider: dict[str, Any]) -> bool:
    keys = provider.get("keys")
    return isinstance(keys, list) and any(
        isinstance(item, dict) and item.get("api_key") and item.get("cx")
        for item in keys
    )


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    keys = provider.get("keys")
    if not isinstance(keys, list):
        raise SearchError("google_cse.keys must be a list")
    key_pairs = [
        key
        for key in keys
        if isinstance(key, dict) and key.get("api_key") and key.get("cx")
    ]
    if not key_pairs:
        raise SearchError("google_cse.keys has no configured api_key/cx pair")
    num = int_config(count_override or provider.get("num"), 10, 1, 10)
    endpoint = str(
        provider.get("endpoint") or "https://customsearch.googleapis.com/customsearch/v1"
    )
    results: list[dict[str, Any]] = []
    seen_urls: set[str] = set()
    stop_when_no_new = stop_when_no_new_urls(global_config)
    for page in range(1, min(max_pages, 10) + 1):
        start = ((page - 1) * num) + 1
        if start + num - 1 > 100:
            break
        key_pair = key_pairs[(page - 1) % len(key_pairs)]
        params = {
            "key": key_pair.get("api_key"),
            "cx": key_pair.get("cx"),
            "q": query,
            "num": num,
            "start": start,
            "safe": provider.get("safe"),
            "hl": provider.get("hl"),
            "gl": provider.get("gl"),
            "lr": provider.get("lr"),
            "dateRestrict": provider.get("date_restrict"),
            "filter": provider.get("filter"),
        }
        data = request_json("GET", endpoint, params=params, timeout=timeout(global_config))
        if not isinstance(data, dict):
            raise SearchError("unexpected google_cse response")
        page_items = data.get("items", [])
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
                    provider="google_cse",
                    query=query,
                    page=page,
                    rank=start + index - 1,
                    title=item.get("title"),
                    url=url,
                    snippet=item.get("snippet"),
                    source=item.get("displayLink"),
                    extra={"formatted_url": item.get("formattedUrl")},
                )
            )
        if stop_when_no_new and page_new_urls == 0:
            break
    return results

"""Hacker News Algolia public search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def configured(provider: dict[str, Any]) -> bool:
    return bool(provider.get("endpoint", "https://hn.algolia.com/api/v1/search"))


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    endpoint = str(provider.get("endpoint") or "https://hn.algolia.com/api/v1/search")
    hits_per_page = int_config(count_override or provider.get("hits_per_page"), 20, 1, 1000)
    results: list[dict[str, Any]] = []
    for page in range(0, max_pages):
        params: dict[str, Any] = {
            "query": query,
            "hitsPerPage": hits_per_page,
            "page": page,
        }
        tags = str(provider.get("tags") or "").strip()
        if tags:
            params["tags"] = tags
        data = request_json(
            "GET",
            endpoint,
            headers={"User-Agent": user_agent(global_config)},
            params=params,
            timeout=timeout(global_config),
        )
        hits = data.get("hits") if isinstance(data, dict) else []
        if not isinstance(hits, list) or not hits:
            break
        for hit in hits:
            if not isinstance(hit, dict):
                continue
            item_id = hit.get("objectID") or hit.get("story_id")
            hn_url = f"https://news.ycombinator.com/item?id={item_id}" if item_id else ""
            external_url = hit.get("url") or hit.get("story_url")
            snippet = "; ".join(
                str(part)
                for part in (
                    hit.get("author"),
                    hit.get("created_at"),
                    hit.get("points"),
                )
                if part is not None and part != ""
            )
            results.append(
                normalize_result(
                    provider="hackernews",
                    query=query,
                    page=page + 1,
                    rank=len(results) + 1,
                    title=str(hit.get("title") or hit.get("story_title") or hit.get("comment_text") or "Hacker News result"),
                    url=str(external_url or hn_url),
                    snippet=snippet,
                    source="hackernews_result",
                    extra={"hn_url": hn_url, "object_id": item_id, "author": hit.get("author")},
                )
            )
        if len(hits) < hits_per_page:
            break
    return results

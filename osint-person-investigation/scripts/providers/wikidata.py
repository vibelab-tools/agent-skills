"""Wikidata entity search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del max_pages
    limit = int_config(count_override or provider.get("limit"), 10, 1, 50)
    endpoint = str(provider.get("endpoint") or "https://www.wikidata.org/w/api.php")
    languages = provider.get("languages")
    if not isinstance(languages, list):
        languages = ["en"]
    results = []
    headers = {"User-Agent": user_agent(global_config)}
    for language in languages:
        params = {
            "action": "wbsearchentities",
            "search": query,
            "language": language,
            "uselang": language,
            "type": "item",
            "limit": limit,
            "format": "json",
            "origin": "*",
        }
        data = request_json(
            "GET", endpoint, headers=headers, params=params, timeout=timeout(global_config)
        )
        items = data.get("search", []) if isinstance(data, dict) else []
        for item in items:
            if not isinstance(item, dict):
                continue
            qid = item.get("id")
            results.append(
                normalize_result(
                    provider="wikidata",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=item.get("label") or qid,
                    url=f"https://www.wikidata.org/wiki/{qid}" if qid else "",
                    snippet=item.get("description"),
                    source="wikidata",
                    extra={"id": qid, "language": language, "aliases": item.get("aliases")},
                )
            )
    return results

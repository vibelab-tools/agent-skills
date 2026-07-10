"""DBLP publication search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def configured(provider: dict[str, Any]) -> bool:
    return bool(provider.get("endpoint", "https://dblp.org/search/publ/api"))


def _authors(info: dict[str, Any]) -> str:
    value = info.get("authors", {}).get("author") if isinstance(info.get("authors"), dict) else None
    if isinstance(value, list):
        names = [str(item.get("text") or item) if isinstance(item, dict) else str(item) for item in value]
    elif isinstance(value, dict):
        names = [str(value.get("text") or "")]
    elif value:
        names = [str(value)]
    else:
        names = []
    return ", ".join(name for name in names[:5] if name)


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    endpoint = str(provider.get("endpoint") or "https://dblp.org/search/publ/api")
    per_page = int_config(count_override or provider.get("per_page"), 20, 1, 1000)
    results: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        offset = (page - 1) * per_page
        data = request_json(
            "GET",
            endpoint,
            headers={"User-Agent": user_agent(global_config)},
            params={"q": query, "format": "json", "h": per_page, "f": offset},
            timeout=timeout(global_config),
        )
        hits = (((data or {}).get("result") or {}).get("hits") or {}).get("hit") or []
        if isinstance(hits, dict):
            hits = [hits]
        if not isinstance(hits, list) or not hits:
            break
        for hit in hits:
            if not isinstance(hit, dict):
                continue
            info = hit.get("info") if isinstance(hit.get("info"), dict) else {}
            venue = info.get("venue") or info.get("publisher") or ""
            year = info.get("year") or ""
            authors = _authors(info)
            snippet = "; ".join(str(part) for part in (year, venue, authors) if part)
            results.append(
                normalize_result(
                    provider="dblp",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=str(info.get("title") or "DBLP publication"),
                    url=str(info.get("url") or info.get("ee") or ""),
                    snippet=snippet,
                    source="dblp_publication",
                    extra={"type": info.get("type"), "year": year, "venue": venue},
                )
            )
        if len(hits) < per_page:
            break
    return results

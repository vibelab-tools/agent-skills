"""Crossref works search provider."""

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
    rows = int_config(count_override or provider.get("rows"), 20, 1, 100)
    endpoint = str(provider.get("endpoint") or "https://api.crossref.org/works")
    headers = {"User-Agent": user_agent(global_config)}
    results = []
    for page in range(1, max_pages + 1):
        params = {"query": query, "rows": rows, "offset": (page - 1) * rows}
        if provider.get("mailto"):
            params["mailto"] = provider.get("mailto")
        data = request_json(
            "GET", endpoint, headers=headers, params=params, timeout=timeout(global_config)
        )
        items = data.get("message", {}).get("items", []) if isinstance(data, dict) else []
        if not items:
            break
        for item in items:
            if not isinstance(item, dict):
                continue
            title = item.get("title")
            title_text = title[0] if isinstance(title, list) and title else item.get("DOI")
            author = item.get("author")
            authors = []
            if isinstance(author, list):
                for person in author[:3]:
                    if isinstance(person, dict):
                        authors.append(" ".join(str(person.get(k, "")) for k in ("given", "family")).strip())
            results.append(
                normalize_result(
                    provider="crossref",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=title_text,
                    url=item.get("URL"),
                    snippet=", ".join(authors),
                    source=item.get("container-title", [""])[0] if isinstance(item.get("container-title"), list) else "",
                    extra={"doi": item.get("DOI"), "published": item.get("published-print") or item.get("published-online")},
                )
            )
    return results

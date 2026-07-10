"""Wikipedia search provider."""

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
    limit = int_config(count_override or provider.get("limit"), 10, 1, 50)
    languages = provider.get("languages")
    if not isinstance(languages, list):
        languages = ["en"]
    results = []
    headers = {"User-Agent": user_agent(global_config)}
    for language in languages:
        endpoint = f"https://{language}.wikipedia.org/w/api.php"
        for page in range(1, max_pages + 1):
            params = {
                "action": "query",
                "list": "search",
                "srsearch": query,
                "srlimit": limit,
                "sroffset": (page - 1) * limit,
                "format": "json",
                "origin": "*",
            }
            data = request_json(
                "GET", endpoint, headers=headers, params=params, timeout=timeout(global_config)
            )
            items = data.get("query", {}).get("search", []) if isinstance(data, dict) else []
            if not items:
                break
            for item in items:
                if not isinstance(item, dict):
                    continue
                title = item.get("title")
                url_title = str(title or "").replace(" ", "_")
                results.append(
                    normalize_result(
                        provider="wikipedia",
                        query=query,
                        page=page,
                        rank=len(results) + 1,
                        title=title,
                        url=f"https://{language}.wikipedia.org/wiki/{url_title}",
                        snippet=item.get("snippet"),
                        source=f"{language}.wikipedia.org",
                        extra={"pageid": item.get("pageid")},
                    )
                )
    return results

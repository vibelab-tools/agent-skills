"""Internet Archive metadata search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    rows = int_config(count_override or provider.get("rows"), 50, 1, 100)
    endpoint = str(provider.get("endpoint") or "https://archive.org/advancedsearch.php")
    fields = provider.get("fields")
    if not isinstance(fields, list):
        fields = ["identifier", "title", "creator", "date", "mediatype", "collection"]
    results = []
    for page in range(1, max_pages + 1):
        params: dict[str, Any] = {
            "q": query,
            "output": "json",
            "rows": rows,
            "page": page,
            "fl[]": fields,
        }
        data = request_json("GET", endpoint, params=params, timeout=timeout(global_config))
        docs = data.get("response", {}).get("docs", []) if isinstance(data, dict) else []
        if not docs:
            break
        for index, item in enumerate(docs, start=1):
            if not isinstance(item, dict):
                continue
            identifier = item.get("identifier")
            url = f"https://archive.org/details/{identifier}" if identifier else ""
            creator = item.get("creator")
            if isinstance(creator, list):
                creator = ", ".join(str(value) for value in creator[:3])
            description = item.get("description")
            if isinstance(description, list):
                description = " ".join(str(value) for value in description[:2])
            results.append(
                normalize_result(
                    provider="internet_archive",
                    query=query,
                    page=page,
                    rank=(page - 1) * rows + index,
                    title=item.get("title") or identifier,
                    url=url,
                    snippet=str(description or ""),
                    source=str(creator or item.get("mediatype") or ""),
                    extra={"date": item.get("date"), "collection": item.get("collection")},
                )
            )
    return results

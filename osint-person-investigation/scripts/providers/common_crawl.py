"""Common Crawl CDX provider for URL and domain patterns."""

from __future__ import annotations

import json
import re
from typing import Any

from .common import int_config, normalize_result, request_json, request_text, timeout


def _pattern(query: str) -> str | None:
    stripped = query.strip()
    if re.search(r"\s", stripped) and "://" not in stripped:
        return None
    if "." not in stripped and "://" not in stripped:
        return None
    return stripped if "*" in stripped else stripped.rstrip("/") + "/*"


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del max_pages
    pattern = provider.get("url") or _pattern(query)
    if not pattern:
        return []
    limit = int_config(count_override or provider.get("limit"), 50, 1, 1000)
    collinfo = str(provider.get("collinfo_endpoint") or "https://index.commoncrawl.org/collinfo.json")
    indexes = request_json("GET", collinfo, timeout=timeout(global_config))
    if not isinstance(indexes, list) or not indexes:
        return []
    latest = indexes[0]
    api = latest.get("cdx-api") if isinstance(latest, dict) else None
    if not api:
        return []
    raw = request_text(
        "GET",
        str(api),
        params={"url": pattern, "output": "json", "limit": limit},
        timeout=timeout(global_config),
    )
    results = []
    for line in raw.splitlines():
        try:
            item = json.loads(line)
        except json.JSONDecodeError:
            continue
        if not isinstance(item, dict):
            continue
        results.append(
            normalize_result(
                provider="common_crawl",
                query=query,
                page=1,
                rank=len(results) + 1,
                title=item.get("url"),
                url=item.get("url"),
                snippet=f"Common Crawl capture {item.get('timestamp')} status {item.get('status')}",
                source="common_crawl_cdx",
                extra=item,
            )
        )
    return results

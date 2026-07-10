"""arXiv API provider."""

from __future__ import annotations

import xml.etree.ElementTree as ET
from typing import Any

from .common import int_config, normalize_result, request_text, timeout, user_agent


ATOM = "{http://www.w3.org/2005/Atom}"


def _text(parent: ET.Element, name: str) -> str:
    child = parent.find(ATOM + name)
    return child.text.strip() if child is not None and child.text else ""


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    max_results = int_config(count_override or provider.get("max_results"), 20, 1, 100)
    endpoint = str(provider.get("endpoint") or "https://export.arxiv.org/api/query")
    headers = {"User-Agent": user_agent(global_config)}
    results = []
    for page in range(1, max_pages + 1):
        params = {
            "search_query": f"all:{query}",
            "start": (page - 1) * max_results,
            "max_results": max_results,
            "sortBy": provider.get("sort_by") or "relevance",
            "sortOrder": provider.get("sort_order") or "descending",
        }
        raw = request_text(
            "GET", endpoint, headers=headers, params=params, timeout=timeout(global_config)
        )
        root = ET.fromstring(raw)
        entries = root.findall(ATOM + "entry")
        if not entries:
            break
        for entry in entries:
            authors = [
                _text(author, "name")
                for author in entry.findall(ATOM + "author")
                if _text(author, "name")
            ]
            results.append(
                normalize_result(
                    provider="arxiv",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=_text(entry, "title"),
                    url=_text(entry, "id"),
                    snippet=_text(entry, "summary"),
                    source=", ".join(authors[:3]),
                    extra={"published": _text(entry, "published"), "updated": _text(entry, "updated")},
                )
            )
    return results

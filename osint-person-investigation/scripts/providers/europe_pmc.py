"""Europe PMC literature search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def configured(provider: dict[str, Any]) -> bool:
    return bool(provider.get("endpoint", "https://www.ebi.ac.uk/europepmc/webservices/rest/search"))


def _url_for(item: dict[str, Any]) -> str:
    doi = item.get("doi")
    if doi:
        return "https://doi.org/" + str(doi)
    pmcid = item.get("pmcid")
    if pmcid:
        return "https://europepmc.org/article/PMC/" + str(pmcid).removeprefix("PMC")
    pmid = item.get("pmid")
    if pmid:
        return "https://europepmc.org/article/MED/" + str(pmid)
    return ""


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    endpoint = str(provider.get("endpoint") or "https://www.ebi.ac.uk/europepmc/webservices/rest/search")
    page_size = int_config(count_override or provider.get("page_size"), 25, 1, 1000)
    cursor = "*"
    results: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        data = request_json(
            "GET",
            endpoint,
            headers={"User-Agent": user_agent(global_config)},
            params={
                "query": query,
                "format": "json",
                "pageSize": page_size,
                "cursorMark": cursor,
            },
            timeout=timeout(global_config),
        )
        items = (((data or {}).get("resultList") or {}).get("result")) or []
        if not isinstance(items, list) or not items:
            break
        for item in items:
            if not isinstance(item, dict):
                continue
            snippet = "; ".join(
                str(part)
                for part in (
                    item.get("pubYear"),
                    item.get("journalTitle"),
                    item.get("authorString"),
                )
                if part
            )
            results.append(
                normalize_result(
                    provider="europe_pmc",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=str(item.get("title") or "Europe PMC record"),
                    url=_url_for(item),
                    snippet=snippet,
                    source="europe_pmc_record",
                    extra={
                        "pmid": item.get("pmid"),
                        "pmcid": item.get("pmcid"),
                        "doi": item.get("doi"),
                        "source": item.get("source"),
                    },
                )
            )
        next_cursor = data.get("nextCursorMark") if isinstance(data, dict) else None
        if not next_cursor or next_cursor == cursor or len(items) < page_size:
            break
        cursor = str(next_cursor)
    return results

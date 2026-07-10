"""OpenAlex works search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def configured(provider: dict[str, Any]) -> bool:
    return bool(provider.get("endpoint", "https://api.openalex.org/works"))


def _url_for(work: dict[str, Any]) -> str:
    primary = work.get("primary_location")
    if isinstance(primary, dict):
        landing = primary.get("landing_page_url")
        if landing:
            return str(landing)
    doi = work.get("doi")
    if doi:
        return str(doi)
    return str(work.get("id") or "")


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    endpoint = str(provider.get("endpoint") or "https://api.openalex.org/works")
    per_page = int_config(count_override or provider.get("per_page"), 25, 1, 200)
    results: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        data = request_json(
            "GET",
            endpoint,
            headers={"User-Agent": user_agent(global_config)},
            params={
                "search": query,
                "per-page": per_page,
                "page": page,
                "mailto": provider.get("mailto") or None,
            },
            timeout=timeout(global_config),
        )
        items = data.get("results") if isinstance(data, dict) else []
        if not isinstance(items, list) or not items:
            break
        for item in items:
            if not isinstance(item, dict):
                continue
            authors = []
            for authorship in item.get("authorships") or []:
                if isinstance(authorship, dict):
                    author = authorship.get("author")
                    if isinstance(author, dict) and author.get("display_name"):
                        authors.append(str(author["display_name"]))
            year = item.get("publication_year") or ""
            venue = ""
            primary = item.get("primary_location")
            if isinstance(primary, dict):
                source = primary.get("source")
                if isinstance(source, dict):
                    venue = str(source.get("display_name") or "")
            snippet = "; ".join(part for part in [str(year), venue, ", ".join(authors[:5])] if part)
            results.append(
                normalize_result(
                    provider="openalex",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=str(item.get("display_name") or item.get("title") or "OpenAlex work"),
                    url=_url_for(item),
                    snippet=snippet,
                    source="openalex_work",
                    extra={
                        "openalex_id": item.get("id"),
                        "doi": item.get("doi"),
                        "publication_year": year,
                        "cited_by_count": item.get("cited_by_count"),
                    },
                )
            )
        if len(items) < per_page:
            break
    return results

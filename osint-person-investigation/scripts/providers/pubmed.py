"""PubMed E-utilities provider."""

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
    retmax = int_config(count_override or provider.get("retmax"), 20, 1, 100)
    base = str(provider.get("endpoint") or "https://eutils.ncbi.nlm.nih.gov/entrez/eutils").rstrip("/")
    headers = {"User-Agent": user_agent(global_config)}
    results = []
    for page in range(1, max_pages + 1):
        params = {
            "db": "pubmed",
            "term": query,
            "retmode": "json",
            "retmax": retmax,
            "retstart": (page - 1) * retmax,
            "tool": provider.get("tool"),
            "email": provider.get("email"),
        }
        data = request_json(
            "GET", f"{base}/esearch.fcgi", headers=headers, params=params, timeout=timeout(global_config)
        )
        ids = data.get("esearchresult", {}).get("idlist", []) if isinstance(data, dict) else []
        if not ids:
            break
        summary = request_json(
            "GET",
            f"{base}/esummary.fcgi",
            headers=headers,
            params={"db": "pubmed", "id": ",".join(ids), "retmode": "json"},
            timeout=timeout(global_config),
        )
        result = summary.get("result", {}) if isinstance(summary, dict) else {}
        for pmid in ids:
            item = result.get(pmid, {})
            if not isinstance(item, dict):
                continue
            authors = item.get("authors")
            author_names = []
            if isinstance(authors, list):
                author_names = [
                    str(author.get("name"))
                    for author in authors[:3]
                    if isinstance(author, dict) and author.get("name")
                ]
            results.append(
                normalize_result(
                    provider="pubmed",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=item.get("title"),
                    url=f"https://pubmed.ncbi.nlm.nih.gov/{pmid}/",
                    snippet=item.get("source"),
                    source=", ".join(author_names),
                    extra={"pmid": pmid, "pubdate": item.get("pubdate")},
                )
            )
    return results

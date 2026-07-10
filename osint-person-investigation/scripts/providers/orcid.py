"""ORCID public record search provider."""

from __future__ import annotations

from typing import Any

from .common import int_config, normalize_result, request_json, timeout, user_agent


def configured(provider: dict[str, Any]) -> bool:
    return bool(provider.get("endpoint", "https://pub.orcid.org/v3.0/search/"))


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    endpoint = str(provider.get("endpoint") or "https://pub.orcid.org/v3.0/search/")
    rows = int_config(count_override or provider.get("rows"), 20, 1, 200)
    results: list[dict[str, Any]] = []
    for page in range(1, max_pages + 1):
        start = (page - 1) * rows
        data = request_json(
            "GET",
            endpoint,
            headers={
                "User-Agent": user_agent(global_config),
                "Accept": "application/json",
            },
            params={"q": query, "rows": rows, "start": start},
            timeout=timeout(global_config),
        )
        items = data.get("result") if isinstance(data, dict) else []
        if not isinstance(items, list) or not items:
            break
        for item in items:
            if not isinstance(item, dict):
                continue
            identifier = item.get("orcid-identifier")
            if not isinstance(identifier, dict):
                continue
            path = str(identifier.get("path") or "")
            uri = str(identifier.get("uri") or ("https://orcid.org/" + path if path else ""))
            results.append(
                normalize_result(
                    provider="orcid",
                    query=query,
                    page=page,
                    rank=len(results) + 1,
                    title=f"ORCID record {path}" if path else "ORCID record",
                    url=uri,
                    snippet="Public ORCID search result. Open and verify the record before linking identities.",
                    source="orcid_record",
                    extra={"orcid": path},
                )
            )
        if len(items) < rows:
            break
    return results

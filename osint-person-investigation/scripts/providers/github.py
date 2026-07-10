"""GitHub public search provider."""

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
    per_page = int_config(count_override or provider.get("per_page"), 20, 1, 100)
    endpoint = str(provider.get("endpoint") or "https://api.github.com").rstrip("/")
    headers = {
        "Accept": "application/vnd.github+json",
        "User-Agent": user_agent(global_config),
    }
    if provider.get("token"):
        headers["Authorization"] = f"Bearer {provider.get('token')}"
    results = []
    kinds = []
    if provider.get("search_users", True):
        kinds.append(("users", "/search/users"))
    if provider.get("search_repositories", True):
        kinds.append(("repositories", "/search/repositories"))
    for kind, path in kinds:
        for page in range(1, min(max_pages, 10) + 1):
            data = request_json(
                "GET",
                endpoint + path,
                headers=headers,
                params={"q": query, "per_page": per_page, "page": page},
                timeout=timeout(global_config),
            )
            items = data.get("items", []) if isinstance(data, dict) else []
            if not items:
                break
            for item in items:
                if not isinstance(item, dict):
                    continue
                title = item.get("full_name") if kind == "repositories" else item.get("login")
                results.append(
                    normalize_result(
                        provider="github",
                        query=query,
                        page=page,
                        rank=len(results) + 1,
                        title=title,
                        url=item.get("html_url"),
                        snippet=item.get("description") or item.get("type"),
                        source=f"github_{kind}",
                        extra={"id": item.get("id"), "kind": kind},
                    )
                )
    return results

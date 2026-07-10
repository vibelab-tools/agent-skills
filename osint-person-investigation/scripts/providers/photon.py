"""Optional Photon crawler integration for user-supplied public URLs/domains."""

from __future__ import annotations

import re
import subprocess
from typing import Any

from .common import SearchError, int_config, normalize_result, resolve_command


URL_RE = re.compile(r"https?://[^\s\"'<>]+", re.IGNORECASE)
DOMAIN_RE = re.compile(r"\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b", re.IGNORECASE)


def configured(provider: dict[str, Any]) -> bool:
    return resolve_command(provider, "photon") is not None


def _target_from_query(query: str, provider: dict[str, Any]) -> str | None:
    configured_target = provider.get("target")
    if configured_target:
        return str(configured_target)
    match = URL_RE.search(query)
    if match:
        return match.group(0)
    match = DOMAIN_RE.search(query)
    if match:
        return "https://" + match.group(0)
    return None


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del global_config, max_pages
    executable = resolve_command(provider, "photon")
    if executable is None:
        raise SearchError(
            "photon command not found. Run make install-runtime for this skill."
        )
    target = _target_from_query(query, provider)
    if not target:
        return []
    timeout_seconds = int_config(provider.get("timeout_seconds"), 300, 1, 3600)
    limit = int_config(count_override or provider.get("max_results"), 200, 1, 5000)
    args = provider.get("args")
    command_line = [executable, "-u", target]
    if isinstance(args, list) and args:
        command_line.extend(str(item) for item in args)
    else:
        command_line.extend(["--only-urls", "--stdout", "internal"])
    try:
        completed = subprocess.run(
            command_line,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout_seconds,
        )
    except subprocess.TimeoutExpired as exc:
        raise SearchError(f"photon timed out after {timeout_seconds}s") from exc
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout).strip()[:500]
        raise SearchError(f"photon exited with {completed.returncode}: {detail}")
    seen: set[str] = set()
    results: list[dict[str, Any]] = []
    for line in completed.stdout.splitlines():
        for url in URL_RE.findall(line):
            if url in seen:
                continue
            seen.add(url)
            results.append(
                normalize_result(
                    provider="photon",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title="Photon discovered URL",
                    url=url,
                    snippet=f"Discovered while crawling {target}",
                    source="photon_crawl_result",
                    extra={"target": target},
                )
            )
            if len(results) >= limit:
                return results
    return results

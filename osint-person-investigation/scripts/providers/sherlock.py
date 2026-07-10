"""Sherlock external username search provider."""

from __future__ import annotations

import re
import subprocess
from typing import Any

from .common import SearchError, int_config, normalize_result, resolve_command


FOUND_RE = re.compile(r"^\[\+\]\s+([^:]+):\s+(https?://\S+)\s*$")


def configured(provider: dict[str, Any]) -> bool:
    return resolve_command(provider, "sherlock") is not None


def _username(query: str) -> str | None:
    stripped = query.strip().lstrip("@")
    if re.fullmatch(r"[A-Za-z0-9_.-]{3,40}", stripped):
        return stripped
    handles = re.findall(r"@([A-Za-z0-9_.-]{3,40})", query)
    return handles[0] if handles else None


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del global_config, max_pages, count_override
    username = provider.get("username") or _username(query)
    if not username:
        return []
    command = resolve_command(provider, "sherlock")
    if command is None:
        raise SearchError(
            "sherlock command not found. Run make install-runtime for this skill."
        )
    cmd = [
        command,
        "--print-found",
        "--no-color",
        "--timeout",
        str(int_config(provider.get("request_timeout_seconds"), 15, 1, 120)),
    ]
    if provider.get("include_nsfw") is True:
        cmd.append("--nsfw")
    if provider.get("ignore_exclusions") is True:
        cmd.append("--ignore-exclusions")
    sites = provider.get("sites")
    if isinstance(sites, list):
        for site in sites:
            if site:
                cmd.extend(["--site", str(site)])
    cmd.append(str(username))
    try:
        completed = subprocess.run(
            cmd,
            check=False,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=int_config(provider.get("timeout_seconds"), 240, 10, 3600),
        )
    except subprocess.TimeoutExpired as exc:
        raise SearchError(f"sherlock timed out after {exc.timeout} seconds") from exc
    if completed.returncode not in (0, 1):
        raise SearchError(completed.stderr.strip() or f"sherlock exited {completed.returncode}")
    results = []
    for line in completed.stdout.splitlines():
        match = FOUND_RE.match(line.strip())
        if not match:
            continue
        site, url = match.groups()
        results.append(
            normalize_result(
                provider="sherlock",
                query=query,
                page=1,
                rank=len(results) + 1,
                title=f"{site} username match",
                url=url,
                snippet=f"Sherlock found username {username} on {site}.",
                source="sherlock",
                extra={"site": site, "username": username},
            )
        )
    return results

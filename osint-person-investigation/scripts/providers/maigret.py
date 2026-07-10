"""Maigret username search provider."""

from __future__ import annotations

import json
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Any

from .common import SearchError, int_config, normalize_result, resolve_command


USERNAME_RE = re.compile(r"^[A-Za-z0-9_.-]{3,40}$")


def configured(provider: dict[str, Any]) -> bool:
    return resolve_command(provider, "maigret") is not None


def _username(query: str) -> str | None:
    stripped = query.strip().lstrip("@")
    if USERNAME_RE.fullmatch(stripped):
        return stripped
    handles = re.findall(r"@([A-Za-z0-9_.-]{3,40})", query)
    return handles[0] if handles else None


def _site_args(provider: dict[str, Any]) -> list[str]:
    sites = provider.get("sites")
    if not isinstance(sites, list) or not sites:
        return []
    args: list[str] = []
    for site in sites:
        name = str(site).strip()
        if name:
            args.extend(["--site", name])
    return args


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del global_config, max_pages
    username = _username(query)
    if not username:
        return []
    command = resolve_command(provider, "maigret")
    if command is None:
        raise SearchError("maigret command not found. Run make install-runtime for this skill.")

    timeout_seconds = int_config(provider.get("timeout_seconds"), 300, 30, 3600)
    request_timeout = int_config(provider.get("request_timeout_seconds"), 10, 1, 120)
    limit = int_config(count_override or provider.get("max_results"), 50, 1, 1000)
    with tempfile.TemporaryDirectory(prefix="osint-maigret-") as output_dir:
        cmd = [
            command,
            username,
            "--timeout",
            str(request_timeout),
            "--no-recursion",
            "--no-extracting",
            "--no-autoupdate",
            "-J",
            "simple",
            "--folderoutput",
            output_dir,
            "--no-color",
            "--no-progressbar",
        ]
        cmd.extend(_site_args(provider))
        top_sites = provider.get("top_sites")
        if not _site_args(provider) and top_sites:
            cmd.extend(["--top-sites", str(int_config(top_sites, 200, 1, 3166))])
        try:
            completed = subprocess.run(
                cmd,
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=timeout_seconds,
            )
        except subprocess.TimeoutExpired as exc:
            raise SearchError(f"Maigret timed out after {exc.timeout} seconds") from exc
        if completed.returncode not in (0, 1):
            detail = (completed.stderr or completed.stdout).strip()[:500]
            raise SearchError(f"Maigret exited {completed.returncode}: {detail}")
        report = Path(output_dir) / f"report_{username}_simple.json"
        if not report.exists():
            return []
        try:
            data = json.loads(report.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise SearchError(f"Maigret wrote invalid JSON: {exc}") from exc

    results: list[dict[str, Any]] = []
    if not isinstance(data, dict):
        return results
    for site_name, item in data.items():
        if not isinstance(item, dict):
            continue
        status = item.get("status") if isinstance(item.get("status"), dict) else {}
        if status.get("status") != "Claimed":
            continue
        url = status.get("url") or item.get("url_user") or item.get("url_probe")
        tags = status.get("tags") or []
        results.append(
            normalize_result(
                provider="maigret",
                query=query,
                page=1,
                rank=len(results) + 1,
                title=f"Maigret profile candidate: {status.get('site_name') or site_name}",
                url=str(url or ""),
                snippet="Open and verify before treating as an identity match.",
                source="maigret_username_result",
                extra={"username": username, "site": site_name, "tags": tags},
            )
        )
        if len(results) >= limit:
            break
    return results

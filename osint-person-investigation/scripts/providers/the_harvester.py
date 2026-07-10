"""theHarvester external tool provider."""

from __future__ import annotations

import json
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Any

from .common import SearchError, int_config, normalize_result, resolve_command


DOMAIN_RE = re.compile(r"\b(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}\b")
EMAIL_RE = re.compile(r"\b[A-Za-z0-9._%+-]+@(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}\b")
URL_RE = re.compile(r"https?://[^\s\"'<>]+")


def configured(provider: dict[str, Any]) -> bool:
    return resolve_command(provider, "theHarvester") is not None


def _domain(query: str, provider: dict[str, Any]) -> str | None:
    if provider.get("domain"):
        return str(provider["domain"]).strip()
    match = DOMAIN_RE.search(query)
    return match.group(0) if match else None


def _load_json(path: Path) -> Any:
    if not path.exists():
        return None
    try:
        with path.open("r", encoding="utf-8") as handle:
            return json.load(handle)
    except (OSError, json.JSONDecodeError):
        return None


def _add_value(
    results: list[dict[str, Any]],
    query: str,
    kind: str,
    value: str,
    include_emails: bool,
) -> None:
    if not value:
        return
    if kind == "email" and not include_emails:
        local, _, domain = value.partition("@")
        value = f"{local[:2]}***@{domain}" if domain else "[redacted email]"
    results.append(
        normalize_result(
            provider="the_harvester",
            query=query,
            page=1,
            rank=len(results) + 1,
            title=f"theHarvester {kind}",
            url=value if value.startswith("http") else "",
            snippet=value,
            source=kind,
            extra={"kind": kind, "redacted": kind == "email" and not include_emails},
        )
    )


def _parse_json(data: Any, query: str, include_emails: bool) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    if not isinstance(data, dict):
        return results
    for key, kind in (
        ("hosts", "host"),
        ("ips", "ip"),
        ("urls", "url"),
        ("asns", "asn"),
        ("interesting_urls", "url"),
        ("people", "person"),
        ("emails", "email"),
    ):
        values = data.get(key)
        if isinstance(values, list):
            for value in values:
                if isinstance(value, dict):
                    value = value.get("host") or value.get("url") or value.get("email") or value.get("name")
                _add_value(results, query, kind, str(value or ""), include_emails)
    return results


def _parse_stdout(stdout: str, query: str, include_emails: bool) -> list[dict[str, Any]]:
    results: list[dict[str, Any]] = []
    for url in sorted(set(URL_RE.findall(stdout))):
        _add_value(results, query, "url", url, include_emails)
    for email in sorted(set(EMAIL_RE.findall(stdout))):
        _add_value(results, query, "email", email, include_emails)
    return results


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del global_config, max_pages, count_override
    domain = _domain(query, provider)
    if not domain:
        return []
    command = resolve_command(provider, "theHarvester")
    if command is None:
        raise SearchError(
            "theHarvester command not found. Run make install-runtime for this skill."
        )
    include_emails = provider.get("include_emails") is True
    with tempfile.TemporaryDirectory(prefix="osint-theharvester-") as tmp:
        output_prefix = str(Path(tmp) / "results")
        cmd = [
            command,
            "-d",
            domain,
            "-b",
            str(provider.get("sources") or "all"),
            "-f",
            output_prefix,
        ]
        try:
            completed = subprocess.run(
                cmd,
                check=False,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=int_config(provider.get("timeout_seconds"), 300, 30, 7200),
            )
        except subprocess.TimeoutExpired as exc:
            raise SearchError(f"theHarvester timed out after {exc.timeout} seconds") from exc
        if completed.returncode not in (0, 1):
            raise SearchError(
                completed.stderr.strip() or f"theHarvester exited {completed.returncode}"
            )
        parsed = _parse_json(_load_json(Path(output_prefix + ".json")), query, include_emails)
        if parsed:
            return parsed
        return _parse_stdout(completed.stdout, query, include_emails)

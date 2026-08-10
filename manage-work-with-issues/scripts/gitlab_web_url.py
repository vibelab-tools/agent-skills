#!/usr/bin/env python3
"""Build version-compatible GitLab issue and commit web URLs."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from typing import Optional, Tuple
from urllib.parse import urlsplit, urlunsplit


SCOPED_PROJECT_ROUTES_MAJOR = 12


def parse_major(version: Optional[str]) -> Optional[int]:
    if not version:
        return None
    match = re.match(r"\s*(\d+)", version)
    return int(match.group(1)) if match else None


def split_project_reference(project_url: str) -> Tuple[str, str, Optional[str]]:
    value = project_url.strip()
    parsed = urlsplit(value)
    if parsed.scheme in {"http", "https", "ssh"} and parsed.hostname:
        path = parsed.path
        scheme = parsed.scheme if parsed.scheme in {"http", "https"} else None
        return parsed.hostname, path, scheme

    match = re.match(r"^(?:[^@/:]+@)?([^:/]+):(.+)$", value)
    if match:
        return match.group(1), match.group(2), None
    raise ValueError("project URL must be an HTTP(S), SSH, or scp-style Git URL")


def normalize_project_url(
    project_url: str, preferred_scheme: Optional[str] = None
) -> str:
    hostname, path, input_scheme = split_project_reference(project_url)
    scheme = preferred_scheme or input_scheme or "https"
    if scheme not in {"http", "https"}:
        raise ValueError("web scheme must be http or https")

    path = path.rstrip("/")
    if path.endswith(".git"):
        path = path[:-4]
    if not path:
        raise ValueError("project URL must include a project path")
    return urlunsplit((scheme, hostname, f"/{path.lstrip('/')}", "", ""))


def normalize_provider_web_url(
    provider_web_url: str, preferred_scheme: Optional[str]
) -> str:
    parsed = urlsplit(provider_web_url.strip())
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("provider web URL must be an absolute HTTP(S) URL")
    scheme = preferred_scheme or parsed.scheme
    if scheme not in {"http", "https"}:
        raise ValueError("web scheme must be http or https")
    return urlunsplit((scheme, parsed.netloc, parsed.path, parsed.query, parsed.fragment))


def detect_gitlab_version(hostname: str) -> Optional[str]:
    try:
        result = subprocess.run(
            ["glab", "api", "--hostname", hostname, "version"],
            check=True,
            capture_output=True,
            text=True,
            timeout=15,
        )
        value = json.loads(result.stdout).get("version")
        return value if isinstance(value, str) else None
    except (FileNotFoundError, subprocess.SubprocessError, json.JSONDecodeError):
        return None


def detect_gitlab_web_scheme(hostname: str) -> Optional[str]:
    try:
        result = subprocess.run(
            ["glab", "config", "get", "api_protocol", "--host", hostname],
            check=True,
            capture_output=True,
            text=True,
            timeout=15,
        )
        value = result.stdout.strip()
        return value if value in {"http", "https"} else None
    except (FileNotFoundError, subprocess.SubprocessError):
        return None


def build_web_url(
    project_url: str,
    kind: str,
    identifier: str,
    version: Optional[str],
    provider_web_url: Optional[str] = None,
    web_scheme: Optional[str] = None,
) -> str:
    if provider_web_url:
        return normalize_provider_web_url(provider_web_url, web_scheme)

    base = normalize_project_url(project_url, web_scheme)
    major = parse_major(version)
    scoped = major is not None and major >= SCOPED_PROJECT_ROUTES_MAJOR
    resource = "commit" if kind == "commit" else "issues"
    route = f"-/{resource}" if scoped else resource
    return f"{base}/{route}/{identifier}"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Build a GitLab web URL using the instance route version."
    )
    parser.add_argument("--project-url", required=True)
    parser.add_argument("--kind", required=True, choices=("commit", "issue"))
    parser.add_argument("--id", required=True, dest="identifier")
    parser.add_argument(
        "--version",
        help="GitLab version override. By default the script queries glab api version.",
    )
    parser.add_argument(
        "--provider-web-url",
        help="Prefer a non-empty URL returned by glab or the GitLab API.",
    )
    parser.add_argument(
        "--web-scheme",
        choices=("http", "https"),
        help="Web scheme override. By default the script checks glab configuration.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        hostname, _, input_scheme = split_project_reference(args.project_url)
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    provider_scheme = (
        urlsplit(args.provider_web_url).scheme if args.provider_web_url else None
    )
    web_scheme = args.web_scheme
    if not web_scheme and "https" not in {input_scheme, provider_scheme}:
        web_scheme = detect_gitlab_web_scheme(hostname)

    version = args.version
    if not version and not args.provider_web_url:
        version = detect_gitlab_version(hostname)
        if not version:
            print(
                "warning: GitLab version unavailable; using legacy redirect-compatible route",
                file=sys.stderr,
            )

    try:
        print(
            build_web_url(
                args.project_url,
                args.kind,
                args.identifier,
                version,
                args.provider_web_url,
                web_scheme,
            )
        )
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

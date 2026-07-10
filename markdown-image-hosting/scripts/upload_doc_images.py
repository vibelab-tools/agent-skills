#!/usr/bin/env python3
"""Upload local Markdown image references and rewrite them to hosted URLs."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import mimetypes
import re
import sys
import unicodedata
from pathlib import Path
from typing import Any
from urllib.parse import urlsplit

from image_hosts import create_image_host


DEFAULT_CONFIG = (
    Path.home() / ".vibelab-tools" / "agent-skills" / "markdown-image-hosting" / "config.json"
)
IMAGE_RE = re.compile(r"!\[([^\]]*)\]\(([^)\n]+)\)")


def main() -> int:
    args = parse_args()
    markdown_path = Path(args.markdown_file).expanduser().resolve()
    config = load_config(Path(args.config).expanduser())
    host_name = args.host or str(config.get("active_host") or "")
    host_config = resolve_host_config(config, host_name)
    markdown = markdown_path.read_text(encoding="utf-8")
    replacements = collect_replacements(markdown, markdown_path, host_config)
    dry_run = not args.write and not args.output
    summary: dict[str, Any] = {
        "ok": True,
        "dry_run": dry_run,
        "markdown_file": str(markdown_path),
        "host": host_name,
        "images": [],
    }
    if dry_run:
        for item in replacements:
            summary["images"].append(item.summary(uploaded=False))
        print(json.dumps(summary, ensure_ascii=False, indent=2))
        return 0

    host = create_image_host(host_config)
    updated = markdown
    for item in replacements:
        public_url = host.upload_file(item.path, item.object_key, item.content_type)
        updated = updated.replace(item.original, item.render(public_url))
        summary["images"].append(item.summary(uploaded=True, public_url=public_url))

    if args.output:
        Path(args.output).expanduser().write_text(updated, encoding="utf-8")
        summary["output"] = str(Path(args.output).expanduser())
    elif args.write:
        markdown_path.write_text(updated, encoding="utf-8")
        summary["output"] = str(markdown_path)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("markdown_file", help="Markdown file to process.")
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG),
        help=f"Config JSON path. Default: {DEFAULT_CONFIG}",
    )
    parser.add_argument("--host", help="Image host name from config.")
    parser.add_argument("--write", action="store_true", help="Overwrite the Markdown file.")
    parser.add_argument("--output", help="Write updated Markdown to another file.")
    return parser.parse_args()


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SystemExit(f"config not found: {path}")
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SystemExit(f"invalid config JSON at {path}: {exc}") from exc
    if not isinstance(data, dict):
        raise SystemExit("config root must be a JSON object")
    return data


def resolve_host_config(config: dict[str, Any], host_name: str) -> dict[str, Any]:
    hosts = config.get("image_hosts")
    if not isinstance(hosts, dict):
        raise SystemExit("config.image_hosts must be an object")
    if not host_name:
        raise SystemExit("active_host is required")
    host_config = hosts.get(host_name)
    if not isinstance(host_config, dict):
        raise SystemExit(f"image host not found: {host_name}")
    return host_config


class ImageReplacement:
    def __init__(
        self,
        *,
        original: str,
        alt: str,
        suffix: str,
        path: Path,
        object_key: str,
        content_type: str,
    ) -> None:
        self.original = original
        self.alt = alt
        self.suffix = suffix
        self.path = path
        self.object_key = object_key
        self.content_type = content_type

    def render(self, public_url: str) -> str:
        return f"![{self.alt}]({public_url}{self.suffix})"

    def summary(self, *, uploaded: bool, public_url: str | None = None) -> dict[str, Any]:
        return {
            "source": str(self.path),
            "object_key": self.object_key,
            "content_type": self.content_type,
            "uploaded": uploaded,
            "public_url": public_url or "",
        }


def collect_replacements(
    markdown: str, markdown_path: Path, host_config: dict[str, Any]
) -> list[ImageReplacement]:
    results: list[ImageReplacement] = []
    for match in IMAGE_RE.finditer(markdown):
        original = match.group(0)
        alt = match.group(1)
        target, suffix = split_destination(match.group(2).strip())
        if not target or is_remote_or_special(target):
            continue
        path = resolve_image_path(markdown_path.parent, target)
        if not path.exists() or not path.is_file():
            continue
        object_key = make_object_key(path, str(host_config.get("prefix") or "docs/{date}"))
        content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        results.append(
            ImageReplacement(
                original=original,
                alt=alt,
                suffix=suffix,
                path=path,
                object_key=object_key,
                content_type=content_type,
            )
        )
    return results


def split_destination(raw: str) -> tuple[str, str]:
    if raw.startswith("<"):
        end = raw.find(">")
        if end != -1:
            return raw[1:end], raw[end + 1 :]
    parts = raw.split(None, 1)
    if len(parts) == 1:
        return parts[0], ""
    return parts[0], " " + parts[1]


def is_remote_or_special(target: str) -> bool:
    parsed = urlsplit(target)
    if parsed.scheme in {"http", "https", "data", "mailto"}:
        return True
    return target.startswith("#")


def resolve_image_path(base_dir: Path, target: str) -> Path:
    path = Path(target)
    if not path.is_absolute():
        path = base_dir / path
    return path.expanduser().resolve()


def make_object_key(path: Path, prefix_template: str) -> str:
    today = dt.date.today()
    prefix = prefix_template.format(
        year=f"{today.year:04d}",
        month=f"{today.month:02d}",
        day=f"{today.day:02d}",
        date=today.isoformat(),
    ).strip("/")
    digest = hashlib.sha256(path.read_bytes()).hexdigest()[:8]
    stem = slugify(path.stem) or "image"
    extension = path.suffix.lower() or ".bin"
    filename = f"{stem}-{digest}{extension}"
    return "/".join(part for part in (prefix, filename) if part)


def slugify(value: str) -> str:
    normalized = unicodedata.normalize("NFKD", value)
    ascii_value = normalized.encode("ascii", "ignore").decode("ascii").lower()
    ascii_value = re.sub(r"[^a-z0-9]+", "-", ascii_value).strip("-")
    return ascii_value


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except BrokenPipeError:
        raise SystemExit(1)

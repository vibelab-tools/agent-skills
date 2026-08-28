#!/usr/bin/env python3
"""Compare exact factual anchors in a source and rewrite.

This dependency-free checker catches added or missing numbers, URLs, Markdown
link targets, code spans and blocks, path-like tokens, acronyms, citations, and
quoted text. It cannot verify semantic claims, names in ordinary prose, or
whether a rewrite preserves implication and claim strength.
"""

from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys
from typing import Iterable


FENCED_CODE_RE = re.compile(r"(?ms)^(```|~~~)[^\n]*\n(.*?)^\1[ \t]*$")
INLINE_CODE_RE = re.compile(r"(?<!`)`([^`\n]+)`(?!`)")
MARKDOWN_TARGET_RE = re.compile(r"\]\(([^)\s]+)(?:\s+['\"][^)]*['\"])?\)")
URL_RE = re.compile(r"https?://[^\s<>\]\[)]+", re.IGNORECASE)
PATH_RE = re.compile(
    r"(?<![\w])(?:~?/|\.{1,2}/)[\w.@%+~/-]+"
    r"|(?<![\w])(?:[\w.@%+~-]+/)+(?:[\w.@%+~-]+\.[A-Za-z0-9]{1,12}|[\w.@%+~-]+)(?![\w])"
)
VERSION_RE = re.compile(r"(?<![\w])v?\d+(?:\.\d+){1,}(?:[-+][0-9A-Za-z.-]+)?(?![\w])")
NUMBER_RE = re.compile(
    r"(?<![\w])(?:[$€£¥]\s*)?[+-]?(?:\d{1,3}(?:,\d{3})+|\d+)(?:\.\d+)?"
    r"(?:\s?(?:%|‰|ms|s|min|h|KB|MB|GB|TB|KiB|MiB|GiB|Hz|kHz|MHz|GHz|°C|°F))?(?![\w])",
    re.IGNORECASE,
)
ACRONYM_RE = re.compile(r"(?<![A-Za-z0-9])[A-Z][A-Z0-9_-]{1,}(?![A-Za-z0-9])")
CITATION_RE = re.compile(r"\[(?:\^?[A-Za-z0-9][A-Za-z0-9_.:-]*|\d+(?:\s*[-,]\s*\d+)*)\]")
QUOTED_RES = (
    re.compile(r'“([^”\n]+)”'),
    re.compile(r'‘([^’\n]+)’'),
    re.compile(r'「([^」\n]+)」'),
    re.compile(r'『([^』\n]+)』'),
    re.compile(r'(?<![\w])"([^"\n]+)"(?![\w])'),
)


@dataclass(frozen=True, order=True)
class Anchor:
    kind: str
    value: str


def _mask_spans(text: str, spans: Iterable[tuple[int, int]]) -> str:
    chars = list(text)
    for start, end in spans:
        chars[start:end] = " " * (end - start)
    return "".join(chars)


def extract_anchors(text: str) -> Counter[Anchor]:
    """Return a multiset of mechanically detectable exact anchors."""
    anchors: Counter[Anchor] = Counter()
    protected_spans: list[tuple[int, int]] = []

    for match in FENCED_CODE_RE.finditer(text):
        anchors[Anchor("code block", match.group(2).strip("\n"))] += 1
        protected_spans.append(match.span())
    for match in INLINE_CODE_RE.finditer(text):
        anchors[Anchor("inline code", match.group(1))] += 1
        protected_spans.append(match.span())

    remaining = _mask_spans(text, protected_spans)

    def collect(kind: str, pattern: re.Pattern[str], group: int = 0) -> None:
        nonlocal remaining
        spans: list[tuple[int, int]] = []
        for match in pattern.finditer(remaining):
            anchors[Anchor(kind, match.group(group))] += 1
            spans.append(match.span())
        remaining = _mask_spans(remaining, spans)

    collect("link target", MARKDOWN_TARGET_RE, 1)
    collect("URL", URL_RE)
    collect("path", PATH_RE)
    collect("version", VERSION_RE)
    collect("citation", CITATION_RE)
    for pattern in QUOTED_RES:
        collect("quoted text", pattern, 1)

    for match in NUMBER_RE.finditer(remaining):
        anchors[Anchor("number", match.group(0))] += 1
    for match in ACRONYM_RE.finditer(remaining):
        anchors[Anchor("acronym", match.group(0))] += 1

    return anchors


def compare(source: str, rewrite: str) -> tuple[Counter[Anchor], Counter[Anchor]]:
    source_anchors = extract_anchors(source)
    rewrite_anchors = extract_anchors(rewrite)
    return source_anchors - rewrite_anchors, rewrite_anchors - source_anchors


def _entries(counter: Counter[Anchor]) -> list[dict[str, object]]:
    return [
        {"kind": anchor.kind, "value": anchor.value, "count": count}
        for anchor, count in sorted(counter.items())
    ]


def _print_entries(label: str, entries: list[dict[str, object]]) -> None:
    if not entries:
        return
    print(f"{label}:")
    for entry in entries:
        value = str(entry["value"]).replace("\n", "\\n")
        print(f"  - {entry['kind']}: {value!r} (x{entry['count']})")


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path, help="original text file")
    parser.add_argument("rewrite", type=Path, help="rewritten text file")
    parser.add_argument("--json", action="store_true", help="emit a JSON report")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    source = args.source.read_text(encoding="utf-8")
    rewrite = args.rewrite.read_text(encoding="utf-8")
    missing, added = compare(source, rewrite)
    missing_entries = _entries(missing)
    added_entries = _entries(added)
    passed = not missing_entries and not added_entries

    if args.json:
        print(
            json.dumps(
                {
                    "pass": passed,
                    "missing": missing_entries,
                    "added": added_entries,
                    "limitations": (
                        "Exact-anchor comparison only; semantic claims, ordinary names, "
                        "implications, and claim strength still require review."
                    ),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    elif passed:
        print("Anchor check passed.")
    else:
        print("Anchor check failed.", file=sys.stderr)
        _print_entries("Missing anchors", missing_entries)
        _print_entries("Added anchors", added_entries)

    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())

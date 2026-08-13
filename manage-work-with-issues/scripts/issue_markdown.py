#!/usr/bin/env python3
"""Validate issue Markdown and pass it through unchanged."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Optional, Sequence


def validate_markdown(text: str, allow_literal_newlines: bool = False) -> None:
    if not text.strip():
        raise ValueError("issue Markdown must not be empty")
    if not allow_literal_newlines and r"\n" in text:
        raise ValueError(
            r"issue Markdown contains literal \n; use real line breaks instead"
        )


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Reject escaped newlines before posting issue Markdown."
    )
    parser.add_argument(
        "file",
        nargs="?",
        default="-",
        help="Markdown file to read, or - for standard input (default).",
    )
    parser.add_argument(
        "--allow-literal-newlines",
        action="store_true",
        help=r"Allow literal \n when it is intentional content, not formatting.",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        text = (
            sys.stdin.read()
            if args.file == "-"
            else Path(args.file).read_text(encoding="utf-8")
        )
        validate_markdown(text, args.allow_literal_newlines)
    except (OSError, UnicodeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

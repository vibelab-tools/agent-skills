"""Command-line interface for the WeChat article reader."""

from __future__ import annotations

import argparse
import logging
from pathlib import Path

from .core import ReaderError, read_article
from .storage import save_article

logger = logging.getLogger(__name__)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="weixin-article",
        description="Read public WeChat Official Account articles into Markdown and local images.",
    )
    parser.add_argument("urls", nargs="+", help="Public https://mp.weixin.qq.com/s/... article URL")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        help="Output root. Defaults to a newly created temporary directory.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    _configure_logging()
    args = build_parser().parse_args(argv)
    failures = 0
    for url in args.urls:
        try:
            article = read_article(url)
            article_dir = save_article(article, args.output)
        except ReaderError as exc:
            failures += 1
            logger.error(
                "Article read failed reason=%s next=verify_public_url_or_open_in_browser",
                exc,
            )
            continue
        print(article_dir)
    return 1 if failures else 0


def _configure_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    logging.getLogger("httpx").setLevel(logging.WARNING)


if __name__ == "__main__":
    raise SystemExit(main())

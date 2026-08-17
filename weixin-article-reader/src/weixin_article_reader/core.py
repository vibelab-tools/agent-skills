"""Fetch and parse public WeChat Official Account articles."""

from __future__ import annotations

import logging
import os
import re
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from html import unescape
from urllib.parse import parse_qs, urlparse

import httpx
from bs4 import BeautifulSoup, Tag
from markdownify import MarkdownConverter

logger = logging.getLogger(__name__)

_BROWSER_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/139.0.0.0 Safari/537.36"
)
_ARTICLE_TIMEOUT_SECONDS = 30.0
_IMAGE_TIMEOUT_SECONDS = 20.0
_MAX_IMAGE_BYTES = 8 * 1024 * 1024
_MAX_TOTAL_IMAGE_BYTES = 64 * 1024 * 1024
_MAX_IMAGE_COUNT = 50
_ALLOWED_IMAGE_HOSTS = {
    "mmbiz.qpic.cn",
    "mmbiz.qlogo.cn",
    "wx.qlogo.cn",
}
_SUPPORTED_IMAGE_TYPES = {
    "image/jpeg": ("jpeg", "jpg"),
    "image/png": ("png", "png"),
    "image/webp": ("webp", "webp"),
}
_PUBLISH_TIME_RE = re.compile(r"var\s+ct\s*=\s*['\"](\d+)['\"]")
_BLOCK_MARKERS = ("环境异常", "请输入验证码", "完成验证后即可继续访问")
_request_lock = threading.Lock()
_last_article_request = 0.0


class ReaderError(RuntimeError):
    """A public article could not be read safely."""


@dataclass(frozen=True)
class ImageAsset:
    """Downloaded article image evidence."""

    source_url: str
    data: bytes
    mime_type: str
    format: str
    extension: str


@dataclass(frozen=True)
class Article:
    """Parsed public WeChat article and its downloaded image evidence."""

    source_url: str
    title: str
    account: str
    published_at: str
    cover_url: str
    body_markdown: str
    image_urls: tuple[str, ...]
    images: tuple[ImageAsset, ...]


class _WeixinMarkdownConverter(MarkdownConverter):
    def convert_img(self, el, text, parent_tags):  # noqa: ARG002
        src = el.get("data-src") or el.get("src") or ""
        alt = el.get("alt") or "image"
        return f"![{alt}]({unescape(src)})" if src else ""

    def convert_iframe(self, el, text, parent_tags):  # noqa: ARG002
        src = el.get("data-src") or el.get("src") or ""
        return f"\n\n[embedded media]({unescape(src)})\n\n" if src else ""


def validate_article_url(url: str) -> str:
    """Return a normalized public article URL or raise ReaderError."""
    candidate = url.strip()
    parsed = urlparse(candidate)
    if parsed.scheme != "https" or parsed.hostname != "mp.weixin.qq.com":
        raise ReaderError("URL must use https://mp.weixin.qq.com")
    if parsed.path != "/s" and not parsed.path.startswith("/s/"):
        raise ReaderError("URL must identify a public WeChat article under /s")
    if parsed.path == "/s" and not parsed.query:
        raise ReaderError("WeChat article URL is missing its article identifier")
    if parsed.path == "/s/":
        raise ReaderError("WeChat article URL is missing its article identifier")
    return candidate


def read_article(url: str) -> Article:
    """Fetch one public WeChat article and download safe inline images."""
    article_url = validate_article_url(url)
    html = _fetch_article_html(article_url)
    parsed = _parse_article(article_url, html)
    images = _download_images(parsed.image_urls, article_url)
    article = Article(
        source_url=parsed.source_url,
        title=parsed.title,
        account=parsed.account,
        published_at=parsed.published_at,
        cover_url=parsed.cover_url,
        body_markdown=parsed.body_markdown,
        image_urls=parsed.image_urls,
        images=tuple(images),
    )
    logger.info(
        "Article read successfully article_id=%s images=%d",
        _article_id(article_url),
        len(article.images),
    )
    return article


def _fetch_article_html(url: str) -> str:
    _wait_for_article_request_slot()
    headers = {
        "User-Agent": _BROWSER_USER_AGENT,
        "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    }
    try:
        with httpx.Client(
            follow_redirects=True,
            timeout=_ARTICLE_TIMEOUT_SECONDS,
            headers=headers,
        ) as client:
            response = client.get(url)
            response.raise_for_status()
    except httpx.HTTPError as exc:
        raise ReaderError(f"article request failed: {exc}") from exc

    html = response.text
    if any(marker in html for marker in _BLOCK_MARKERS):
        raise ReaderError("WeChat returned an anti-bot verification page")
    if "text/html" not in response.headers.get("content-type", "").lower():
        raise ReaderError("WeChat returned a non-HTML response")
    return html


def _wait_for_article_request_slot() -> None:
    global _last_article_request
    raw_interval = os.environ.get("WEIXIN_ARTICLE_MIN_INTERVAL_S", "1.0")
    try:
        interval = max(float(raw_interval), 0.5)
    except ValueError:
        interval = 1.0

    with _request_lock:
        remaining = interval - (time.monotonic() - _last_article_request)
        if remaining > 0:
            time.sleep(remaining)
        _last_article_request = time.monotonic()


def _parse_article(url: str, html: str) -> Article:
    soup = BeautifulSoup(html, "lxml")
    body = soup.select_one("#js_content")
    title = _text(soup.select_one("#activity-name"))
    if body is None or not title:
        raise ReaderError("response does not contain a readable WeChat article")

    account = _text(soup.select_one("#js_name"))
    cover = _attribute(soup.select_one('meta[property="og:image"]'), "content")
    image_urls = tuple(_extract_image_urls(body))
    body_markdown = _html_to_markdown(str(body))
    return Article(
        source_url=url,
        title=title,
        account=account,
        published_at=_extract_publish_time(html),
        cover_url=cover,
        body_markdown=body_markdown,
        image_urls=image_urls,
        images=(),
    )


def _download_images(urls: tuple[str, ...], article_url: str) -> list[ImageAsset]:
    assets: list[ImageAsset] = []
    total_bytes = 0
    headers = {
        "User-Agent": _BROWSER_USER_AGENT,
        "Referer": article_url,
    }
    with httpx.Client(
        follow_redirects=True,
        timeout=_IMAGE_TIMEOUT_SECONDS,
        headers=headers,
    ) as client:
        for index, url in enumerate(urls):
            if index >= _MAX_IMAGE_COUNT:
                logger.warning(
                    "Remaining article images skipped reason=image_count_limit limit=%d",
                    _MAX_IMAGE_COUNT,
                )
                break
            if not _is_allowed_image_url(url):
                logger.warning(
                    "Article image skipped reason=unsupported_host host=%s",
                    urlparse(url).hostname or "",
                )
                continue
            try:
                response = client.get(url)
                response.raise_for_status()
            except httpx.HTTPError as exc:
                logger.warning("Article image download failed reason=%s", exc)
                continue

            mime_type = response.headers.get("content-type", "").split(";", 1)[0].lower()
            image_type = _SUPPORTED_IMAGE_TYPES.get(mime_type)
            if image_type is None:
                logger.warning(
                    "Article image skipped reason=unsupported_type type=%s",
                    mime_type or "unknown",
                )
                continue
            data = response.content
            if len(data) > _MAX_IMAGE_BYTES:
                logger.warning("Article image skipped reason=image_too_large bytes=%d", len(data))
                continue
            if total_bytes + len(data) > _MAX_TOTAL_IMAGE_BYTES:
                logger.warning(
                    "Remaining article images skipped reason=total_size_limit limit_bytes=%d",
                    _MAX_TOTAL_IMAGE_BYTES,
                )
                break
            image_format, extension = image_type
            assets.append(
                ImageAsset(
                    source_url=url,
                    data=data,
                    mime_type=mime_type,
                    format=image_format,
                    extension=extension,
                )
            )
            total_bytes += len(data)
    return assets


def _extract_image_urls(body: Tag) -> list[str]:
    seen: set[str] = set()
    urls: list[str] = []
    for image in body.find_all("img"):
        raw_url = image.get("data-src") or image.get("src") or ""
        url = unescape(raw_url.strip())
        if not url or url.startswith("data:") or url in seen:
            continue
        seen.add(url)
        urls.append(url)
    return urls


def _is_allowed_image_url(url: str) -> bool:
    parsed = urlparse(url)
    if parsed.scheme != "https" or parsed.hostname not in _ALLOWED_IMAGE_HOSTS:
        return False
    image_format = parse_qs(parsed.query).get("wx_fmt", [""])[0].lower()
    return image_format not in {"gif", "svg"}


def _html_to_markdown(html: str) -> str:
    converter = _WeixinMarkdownConverter(
        heading_style="ATX",
        bullets="-",
        strong_em_symbol="*",
        escape_asterisks=False,
        escape_underscores=False,
    )
    markdown = converter.convert(html)
    return re.sub(r"\n{3,}", "\n\n", markdown).strip()


def _extract_publish_time(html: str) -> str:
    match = _PUBLISH_TIME_RE.search(html)
    if match is None:
        return ""
    try:
        return datetime.fromtimestamp(int(match.group(1)), tz=timezone.utc).isoformat()
    except (OSError, ValueError):
        return ""


def _text(tag: Tag | None) -> str:
    return tag.get_text(strip=True) if tag is not None else ""


def _attribute(tag: Tag | None, name: str) -> str:
    return str(tag.get(name, "")) if tag is not None else ""


def _article_id(url: str) -> str:
    parsed = urlparse(url)
    if parsed.path.startswith("/s/"):
        return parsed.path.removeprefix("/s/")[:12]
    return "query-style"

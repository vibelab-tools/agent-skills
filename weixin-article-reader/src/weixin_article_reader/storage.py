"""Persist article Markdown, image evidence, and a machine-readable manifest."""

from __future__ import annotations

import hashlib
import json
import re
import tempfile
import unicodedata
from pathlib import Path

from .core import Article, ReaderError


def save_article(article: Article, output_root: Path | None = None) -> Path:
    """Write one article to a new directory and return that directory."""
    root = output_root or Path(tempfile.mkdtemp(prefix="weixin-articles-"))
    root.mkdir(parents=True, exist_ok=True)
    article_dir = root / _article_directory_name(article)
    try:
        article_dir.mkdir()
    except FileExistsError as exc:
        raise ReaderError(f"output directory already exists: {article_dir}") from exc

    assets_dir = article_dir / "assets"
    assets_dir.mkdir()
    local_links: dict[str, str] = {}
    manifest_images: list[dict[str, object]] = []
    for index, image in enumerate(article.images, 1):
        filename = f"image-{index:03d}.{image.extension}"
        relative_path = f"assets/{filename}"
        (assets_dir / filename).write_bytes(image.data)
        local_links[image.source_url] = relative_path
        manifest_images.append(
            {
                "source_url": image.source_url,
                "path": relative_path,
                "mime_type": image.mime_type,
                "bytes": len(image.data),
            }
        )

    markdown = render_article_markdown(article, local_links)
    (article_dir / "article.md").write_text(markdown, encoding="utf-8")
    manifest = {
        "source_url": article.source_url,
        "title": article.title,
        "account": article.account,
        "published_at": article.published_at,
        "cover_url": article.cover_url,
        "discovered_image_count": len(article.image_urls),
        "downloaded_image_count": len(article.images),
        "images": manifest_images,
    }
    (article_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return article_dir


def render_article_markdown(
    article: Article,
    local_links: dict[str, str] | None = None,
) -> str:
    """Render metadata and body text, optionally rewriting downloaded images."""
    body = article.body_markdown
    for source_url, local_path in (local_links or {}).items():
        body = body.replace(f"]({source_url})", f"]({local_path})")

    metadata = [
        f"# {article.title}",
        "",
        f"- Account: {article.account or '(unknown)'}",
        f"- Published: {article.published_at or '(unknown)'}",
        f"- Source: {article.source_url}",
        f"- Cover: {article.cover_url or '(none)'}",
        f"- Images: {len(article.images)} downloaded / {len(article.image_urls)} discovered",
        "",
        "---",
        "",
        body,
        "",
    ]
    return "\n".join(metadata)


def _article_directory_name(article: Article) -> str:
    normalized = unicodedata.normalize("NFKC", article.title)
    slug = re.sub(r"[^\w\u3400-\u9fff-]+", "-", normalized, flags=re.UNICODE)
    slug = re.sub(r"-+", "-", slug).strip("-_")[:72] or "weixin-article"
    digest = hashlib.sha256(article.source_url.encode("utf-8")).hexdigest()[:8]
    return f"{slug}-{digest}"

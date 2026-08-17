from __future__ import annotations

import asyncio
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from mcp.types import ImageContent, TextContent

from weixin_article_reader import core
from weixin_article_reader.core import Article, ImageAsset, ReaderError
from weixin_article_reader.mcp_server import server
from weixin_article_reader.storage import save_article


FIXTURE_DIR = Path(__file__).parent / "fixtures"
ARTICLE_URL = "https://mp.weixin.qq.com/s/fixture-token"
IMAGE_URL = "https://mmbiz.qpic.cn/a/640?wx_fmt=png&from=appmsg"


class FakeResponse:
    def __init__(self, *, text: str = "", content: bytes = b"", content_type: str = "text/html"):
        self.text = text
        self.content = content
        self.headers = {"content-type": content_type}

    def raise_for_status(self) -> None:
        return None


class FakeClient:
    def __init__(self, responses: dict[str, FakeResponse], **kwargs):  # noqa: ARG002
        self.responses = responses
        self.requests: list[str] = []

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):  # noqa: ARG002
        return False

    def get(self, url: str) -> FakeResponse:
        self.requests.append(url)
        return self.responses[url]


def sample_article() -> Article:
    image = ImageAsset(
        source_url=IMAGE_URL,
        data=b"\x89PNG\r\n\x1a\nfixture",
        mime_type="image/png",
        format="png",
        extension="png",
    )
    return Article(
        source_url=ARTICLE_URL,
        title="Fixture Article",
        account="Fixture Account",
        published_at="2023-11-14T22:13:20+00:00",
        cover_url="https://mmbiz.qpic.cn/cover.jpg",
        body_markdown=f"Hello.\n\n![first]({IMAGE_URL})",
        image_urls=(IMAGE_URL,),
        images=(image,),
    )


class UrlValidationTests(unittest.TestCase):
    def test_accepts_short_and_query_style_public_article_urls(self):
        self.assertEqual(core.validate_article_url(ARTICLE_URL), ARTICLE_URL)
        query_url = "https://mp.weixin.qq.com/s?__biz=abc&mid=1"
        self.assertEqual(core.validate_article_url(query_url), query_url)

    def test_rejects_non_weixin_hosts_and_missing_article_ids(self):
        for url in (
            "http://mp.weixin.qq.com/s/token",
            "https://example.com/s/token",
            "https://mp.weixin.qq.com/",
            "https://mp.weixin.qq.com/s/",
        ):
            with self.subTest(url=url), self.assertRaises(ReaderError):
                core.validate_article_url(url)


class ParsingTests(unittest.TestCase):
    def test_extracts_metadata_markdown_and_deduplicated_images(self):
        html = (FIXTURE_DIR / "article.html").read_text(encoding="utf-8")
        article = core._parse_article(ARTICLE_URL, html)

        self.assertEqual(article.title, "Fixture Article")
        self.assertEqual(article.account, "Fixture Account")
        self.assertEqual(article.published_at, "2023-11-14T22:13:20+00:00")
        self.assertIn("**WeChat**", article.body_markdown)
        self.assertEqual(
            article.image_urls,
            (IMAGE_URL, "https://example.com/not-downloaded.jpg"),
        )

    def test_rejects_verification_pages_before_parsing(self):
        fake_client = FakeClient(
            {ARTICLE_URL: FakeResponse(text="<h1>环境异常</h1>", content_type="text/html")}
        )
        with (
            patch.object(core, "_wait_for_article_request_slot"),
            patch.object(core.httpx, "Client", return_value=fake_client),
            self.assertRaisesRegex(ReaderError, "anti-bot"),
        ):
            core._fetch_article_html(ARTICLE_URL)


class ImageTests(unittest.TestCase):
    def test_downloads_allowed_images_and_skips_untrusted_hosts(self):
        fake_client = FakeClient(
            {IMAGE_URL: FakeResponse(content=b"png", content_type="image/png")}
        )
        with patch.object(core.httpx, "Client", return_value=fake_client):
            images = core._download_images(
                (IMAGE_URL, "https://example.com/private.jpg"),
                ARTICLE_URL,
            )

        self.assertEqual(len(images), 1)
        self.assertEqual(images[0].format, "png")
        self.assertEqual(fake_client.requests, [IMAGE_URL])


class StorageTests(unittest.TestCase):
    def test_writes_markdown_manifest_and_local_image(self):
        with tempfile.TemporaryDirectory() as directory:
            article_dir = save_article(sample_article(), Path(directory))
            markdown = (article_dir / "article.md").read_text(encoding="utf-8")
            manifest = json.loads((article_dir / "manifest.json").read_text(encoding="utf-8"))

            self.assertIn("![first](assets/image-001.png)", markdown)
            self.assertEqual(manifest["downloaded_image_count"], 1)
            self.assertEqual(
                (article_dir / "assets/image-001.png").read_bytes(),
                b"\x89PNG\r\n\x1a\nfixture",
            )


class McpContractTests(unittest.TestCase):
    def test_tool_returns_text_then_native_image_content(self):
        with patch("weixin_article_reader.mcp_server.read_article", return_value=sample_article()):
            result = asyncio.run(server.call_tool("read_weixin_article", {"url": ARTICLE_URL}))

        self.assertFalse(result.is_error)
        self.assertIsInstance(result.content[0], TextContent)
        self.assertIsInstance(result.content[1], ImageContent)
        self.assertIn("Fixture Article", result.content[0].text)
        self.assertEqual(result.content[1].mime_type, "image/png")


if __name__ == "__main__":
    unittest.main()

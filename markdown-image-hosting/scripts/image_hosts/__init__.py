"""Image host provider registry."""

from __future__ import annotations

from typing import Any

from .s3_compatible import S3CompatibleImageHost


def create_image_host(config: dict[str, Any]):
    host_type = config.get("type")
    if host_type == "s3-compatible":
        return S3CompatibleImageHost(config)
    raise ValueError(f"unsupported image host type: {host_type}")

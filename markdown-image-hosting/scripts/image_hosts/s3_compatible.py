"""S3-compatible image host implementation backed by boto3."""

from __future__ import annotations

import os
import urllib.parse
from pathlib import Path
from typing import Any


class S3CompatibleImageHost:
    def __init__(self, config: dict[str, Any]) -> None:
        self.endpoint_url = str(config.get("endpoint_url") or "").rstrip("/")
        self.bucket = str(config.get("bucket") or "")
        self.region = str(config.get("region") or "auto")
        self.access_key_id = _secret(config, "access_key_id", "access_key_id_env")
        self.secret_access_key = _secret(config, "secret_access_key", "secret_access_key_env")
        self.public_base_url = str(config.get("public_base_url") or "").rstrip("/")
        self.force_path_style = bool(config.get("force_path_style", True))
        self.acl = str(config.get("acl") or "")
        self.extra_args = config.get("extra_args") if isinstance(config.get("extra_args"), dict) else {}
        if not self.endpoint_url:
            raise ValueError("endpoint_url is required for s3-compatible image host")
        if not self.bucket:
            raise ValueError("bucket is required for s3-compatible image host")
        if not self.access_key_id or not self.secret_access_key:
            raise ValueError("S3 credentials are required; configure env-backed credentials")

    def upload_file(self, path: Path, object_key: str, content_type: str) -> str:
        client = self._client()
        extra_args = dict(self.extra_args)
        extra_args["ContentType"] = content_type
        if self.acl:
            extra_args["ACL"] = self.acl
        client.upload_file(
            Filename=str(path),
            Bucket=self.bucket,
            Key=object_key,
            ExtraArgs=extra_args,
        )
        return self.public_url(object_key)

    def public_url(self, object_key: str) -> str:
        quoted_key = "/".join(
            urllib.parse.quote(part, safe="") for part in object_key.split("/")
        )
        if self.public_base_url:
            return f"{self.public_base_url}/{quoted_key}"
        return f"{self.endpoint_url}/{self.bucket}/{quoted_key}"

    def _client(self):
        try:
            import boto3
            from botocore.config import Config
        except ImportError as exc:
            raise RuntimeError(
                "boto3 is required for s3-compatible uploads. Run make install-runtime."
            ) from exc
        addressing_style = "path" if self.force_path_style else "virtual"
        return boto3.client(
            "s3",
            endpoint_url=self.endpoint_url,
            aws_access_key_id=self.access_key_id,
            aws_secret_access_key=self.secret_access_key,
            region_name=self.region,
            config=Config(signature_version="s3v4", s3={"addressing_style": addressing_style}),
        )


def _secret(config: dict[str, Any], value_key: str, env_key: str) -> str:
    value = str(config.get(value_key) or "")
    if value:
        return value
    env_name = str(config.get(env_key) or "")
    return os.environ.get(env_name, "") if env_name else ""

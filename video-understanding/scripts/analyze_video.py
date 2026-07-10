#!/usr/bin/env python3
"""Analyze local video files through sampled frames or configurable vision APIs."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import shutil
import subprocess
import time
from typing import Any
from urllib import error, request
from urllib.parse import quote
import zlib


VIDEO_EXTENSIONS = {
    ".3gp",
    ".avi",
    ".flv",
    ".m4v",
    ".mkv",
    ".mov",
    ".mp4",
    ".mpeg",
    ".mpg",
    ".webm",
    ".wmv",
}

DEFAULT_RUNTIME_ROOT = Path.home() / ".vibelab-tools" / "agent-skills" / "video-understanding"
DEFAULT_CONFIG_PATH = DEFAULT_RUNTIME_ROOT / "config.json"
DEFAULT_FRAME_OUTPUT_ROOT = DEFAULT_RUNTIME_ROOT / "frames"
DEFAULT_QUESTION = (
    "Summarize this video. Describe visible scenes, people, objects, text, "
    "actions, timing, and any uncertainty. Do not invent content that is not visible."
)
PROXY_ENV_VARS = (
    "http_proxy",
    "https_proxy",
    "all_proxy",
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "ALL_PROXY",
)
MODE_ALIASES = {
    "multimodal": "vision-frames",
}
ALLOWED_MODES = {"auto", "frames", "vision-frames", "video-native", "multimodal"}
NORMALIZED_MODES = {"auto", "frames", "vision-frames", "video-native"}


DEFAULT_CONFIG: dict[str, Any] = {
    "mode": "auto",
    "provider": "openai-compatible",
    "clear_proxy_env": True,
    "frame": {
        "interval_seconds": 1.0,
        "max_frames": 240,
        "max_side": 720,
        "jpeg_quality": 3,
        "output_root": str(DEFAULT_FRAME_OUTPUT_ROOT),
        "keep_frames": True,
        "ffmpeg": "ffmpeg",
        "ffprobe": "ffprobe",
        "timeout_seconds": 1800,
    },
    "vision_frames": {
        "fallback_to_frames": True,
        "batch_size": 8,
        "request_timeout_seconds": 180,
        "openai_compatible": {
            "endpoint": "https://api.openai.com/v1/chat/completions",
            "api_key": "",
            "api_key_env": "OPENAI_API_KEY",
            "model": "",
            "temperature": 0.2,
            "max_tokens": 2048,
            "max_tokens_field": "max_tokens",
            "headers": {},
            "image_detail": "low",
        },
        "gemini": {
            "endpoint": "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            "api_key": "",
            "api_key_env": "GOOGLE_AI_API_KEY",
            "model": "",
            "temperature": 0.2,
            "max_output_tokens": 2048,
            "headers": {},
        },
    },
    "video_native": {
        "fallback_to_vision_frames": True,
        "request_timeout_seconds": 600,
        "openai_compatible": {
            "enabled": True,
            "input_method": "auto",
            "video_url": "",
            "fps": 2.0,
            "max_inline_bytes": 7340032,
            "min_pixels": None,
            "max_pixels": None,
            "total_pixels": None,
        },
        "gemini": {
            "enabled": True,
            "input_method": "file-api",
            "upload_endpoint": "https://generativelanguage.googleapis.com/upload/v1beta/files",
            "generate_endpoint": "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
            "files_endpoint": "https://generativelanguage.googleapis.com/v1beta",
            "poll_interval_seconds": 2,
            "poll_timeout_seconds": 600,
            "delete_uploaded_file": True,
            "max_inline_bytes": 104857600,
        },
    },
    "video_upload": {
        "enabled": False,
        "active_host": "oss",
        "hosts": {
            "oss": {
                "type": "s3-compatible",
                "endpoint_url": "",
                "bucket": "",
                "region": "auto",
                "access_key_id": "",
                "secret_access_key": "",
                "access_key_id_env": "",
                "secret_access_key_env": "",
                "public_base_url": "",
                "prefix": "videos/{year}/{month}/{day}",
                "force_path_style": False,
                "extra_args": {},
            }
        },
    },
}


class VideoError(Exception):
    def __init__(self, message: str, details: dict[str, Any] | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.details = details or {}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Analyze a local video file.")
    parser.add_argument("video_path", help="Local video path.")
    parser.add_argument("--question", default=DEFAULT_QUESTION, help="Question about the video.")
    parser.add_argument(
        "--config",
        default=str(DEFAULT_CONFIG_PATH),
        help="JSON config path. Default: ~/.vibelab-tools/agent-skills/video-understanding/config.json.",
    )
    parser.add_argument(
        "--mode",
        choices=tuple(sorted(ALLOWED_MODES)),
        default="",
        help="Override configured analysis mode.",
    )
    parser.add_argument(
        "--provider",
        choices=("openai-compatible", "gemini"),
        default="",
        help="Override configured provider-backed analysis provider.",
    )
    parser.add_argument(
        "--frame-interval-seconds",
        type=float,
        default=None,
        help="Override frame sampling interval. Default from config, normally 1 second.",
    )
    parser.add_argument(
        "--max-frames",
        type=int,
        default=None,
        help="Override maximum extracted frames. 0 means no cap.",
    )
    parser.add_argument(
        "--frame-output-dir",
        default="",
        help="Write extracted frames to this exact directory.",
    )
    parser.add_argument(
        "--print-config",
        action="store_true",
        help="Print effective config with API keys redacted and exit.",
    )
    return parser.parse_args()


def deep_merge(base: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    result = dict(base)
    for key, value in override.items():
        if isinstance(value, dict) and isinstance(result.get(key), dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result


def read_json_file(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise VideoError("config file is not valid JSON", {"path": str(path), "error": str(exc)})
    if not isinstance(data, dict):
        raise VideoError("config file root must be a JSON object", {"path": str(path)})
    return data


def load_config(args: argparse.Namespace) -> dict[str, Any]:
    raw_config = read_json_file(Path(args.config).expanduser())
    config = deep_merge(DEFAULT_CONFIG, raw_config)
    if "multimodal" in raw_config and "vision_frames" not in raw_config:
        config["vision_frames"] = deep_merge(config["vision_frames"], raw_config["multimodal"])

    if args.mode:
        config["mode"] = args.mode
    if args.provider:
        config["provider"] = args.provider
    if args.frame_interval_seconds is not None:
        config["frame"]["interval_seconds"] = args.frame_interval_seconds
    if args.max_frames is not None:
        config["frame"]["max_frames"] = args.max_frames
    if args.frame_output_dir:
        config["frame"]["output_dir"] = args.frame_output_dir

    validate_config(config)
    return config


def validate_config(config: dict[str, Any]) -> None:
    mode = normalize_mode(config.get("mode"))
    config["mode"] = mode
    provider = config.get("provider")
    if mode not in NORMALIZED_MODES:
        raise VideoError("invalid mode", {"mode": mode, "allowed": sorted(NORMALIZED_MODES)})
    if provider not in {"openai-compatible", "gemini"}:
        raise VideoError(
            "invalid provider",
            {"provider": provider, "allowed": ["openai-compatible", "gemini"]},
        )

    frame = config["frame"]
    if float(frame["interval_seconds"]) <= 0:
        raise VideoError("frame.interval_seconds must be greater than 0")
    if int(frame["max_frames"]) < 0:
        raise VideoError("frame.max_frames must be greater than or equal to 0")
    if int(frame["max_side"]) <= 0:
        raise VideoError("frame.max_side must be greater than 0")
    if int(frame["jpeg_quality"]) < 1 or int(frame["jpeg_quality"]) > 31:
        raise VideoError("frame.jpeg_quality must be between 1 and 31")
    if int(frame["timeout_seconds"]) <= 0:
        raise VideoError("frame.timeout_seconds must be greater than 0")
    if int(config["vision_frames"]["batch_size"]) <= 0:
        raise VideoError("vision_frames.batch_size must be greater than 0")
    if int(config["vision_frames"]["request_timeout_seconds"]) <= 0:
        raise VideoError("vision_frames.request_timeout_seconds must be greater than 0")
    if int(config["video_native"]["request_timeout_seconds"]) <= 0:
        raise VideoError("video_native.request_timeout_seconds must be greater than 0")
    native = native_settings(config, str(provider))
    if int(native.get("max_inline_bytes", 1)) <= 0:
        raise VideoError("video_native max_inline_bytes must be greater than 0")
    if str(native.get("input_method", "auto")) not in {
        "auto",
        "base64",
        "file-api",
        "url",
        "inline",
        "upload",
    }:
        raise VideoError(
            "invalid video_native input_method",
            {
                "provider": provider,
                "input_method": native.get("input_method"),
                "allowed": ["auto", "base64", "file-api", "url", "inline", "upload"],
            },
        )


def redact_config(config: dict[str, Any]) -> dict[str, Any]:
    def redact(value: Any, key: str = "") -> Any:
        if isinstance(value, dict):
            return {k: redact(v, k) for k, v in value.items()}
        lowered = key.lower()
        if any(marker in lowered for marker in ("api_key", "access_key", "secret", "authorization")) and value:
            return "<redacted>"
        return value

    return redact(config)


def clear_proxy_env() -> list[str]:
    removed: list[str] = []
    for name in PROXY_ENV_VARS:
        if name in os.environ:
            os.environ.pop(name, None)
            removed.append(name)
    return removed


def fail(message: str, details: dict[str, Any] | None = None, code: int = 1) -> None:
    payload: dict[str, Any] = {"ok": False, "error": message}
    if details:
        payload["details"] = details
    print(json.dumps(payload, ensure_ascii=False, indent=2))
    raise SystemExit(code)


def run_command(command: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            check=False,
            capture_output=True,
            text=True,
            timeout=timeout,
        )
    except FileNotFoundError as exc:
        raise VideoError("required executable is not available", {"executable": command[0]}) from exc
    except subprocess.TimeoutExpired as exc:
        raise VideoError("command timed out", {"command": command, "timeout_seconds": timeout}) from exc


def _float_or_none(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _int_or_none(value: Any) -> int | None:
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def run_ffprobe(path: Path, ffprobe: str) -> dict[str, Any]:
    try:
        result = run_command(
            [
                ffprobe,
                "-v",
                "error",
                "-print_format",
                "json",
                "-show_format",
                "-show_streams",
                str(path),
            ],
            timeout=30,
        )
    except VideoError as exc:
        return {"ffprobe_available": False, "ffprobe_error": exc.message, **exc.details}

    if result.returncode != 0:
        return {
            "ffprobe_available": True,
            "ffprobe_error": (result.stderr or result.stdout).strip(),
        }

    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError:
        return {"ffprobe_available": True, "ffprobe_error": "invalid ffprobe JSON"}

    video_stream = next(
        (stream for stream in data.get("streams", []) if stream.get("codec_type") == "video"),
        {},
    )
    audio_streams = [
        stream for stream in data.get("streams", []) if stream.get("codec_type") == "audio"
    ]
    fmt = data.get("format", {})
    metadata: dict[str, Any] = {
        "ffprobe_available": True,
        "format_name": fmt.get("format_name"),
        "duration_seconds": _float_or_none(fmt.get("duration")),
        "bit_rate": _int_or_none(fmt.get("bit_rate")),
        "video_codec": video_stream.get("codec_name"),
        "width": video_stream.get("width"),
        "height": video_stream.get("height"),
        "avg_frame_rate": video_stream.get("avg_frame_rate"),
        "audio_stream_count": len(audio_streams),
    }
    return {key: value for key, value in metadata.items() if value not in (None, "", [])}


def validate_video(path: Path, config: dict[str, Any]) -> dict[str, Any]:
    if not path.exists():
        raise VideoError("video file does not exist", {"path": str(path)})
    if not path.is_file():
        raise VideoError("video path is not a regular file", {"path": str(path)})

    suffix = path.suffix.lower()
    mime_type = mimetypes.guess_type(path.name)[0]
    if suffix not in VIDEO_EXTENSIONS and not (mime_type or "").startswith("video/"):
        raise VideoError(
            "file does not look like a supported video",
            {"path": str(path), "suffix": suffix, "mime_type": mime_type},
        )

    metadata = run_ffprobe(path, str(config["frame"]["ffprobe"]))
    size_bytes = path.stat().st_size
    metadata.update(
        {
            "path": str(path),
            "size_bytes": size_bytes,
            "size_mb": round(size_bytes / 1024 / 1024, 3),
            "mime_type": mime_type,
        }
    )
    return metadata


def output_dir_for_video(video_path: Path, config: dict[str, Any]) -> Path:
    frame = config["frame"]
    explicit = frame.get("output_dir")
    if explicit:
        return Path(str(explicit)).expanduser()
    digest_source = f"{video_path.resolve()}:{video_path.stat().st_size}:{video_path.stat().st_mtime_ns}"
    digest = f"{zlib.crc32(digest_source.encode('utf-8')) & 0xFFFFFFFF:08x}"
    timestamp = dt.datetime.now(dt.UTC).strftime("%Y%m%dT%H%M%SZ")
    safe_name = "".join(ch if ch.isalnum() or ch in ("-", "_") else "-" for ch in video_path.stem)
    return Path(str(frame["output_root"])).expanduser() / f"{timestamp}-{safe_name}-{digest}"


def effective_fps(metadata: dict[str, Any], config: dict[str, Any]) -> float:
    frame = config["frame"]
    requested_fps = 1.0 / float(frame["interval_seconds"])
    max_frames = int(frame["max_frames"])
    duration = metadata.get("duration_seconds")
    if not isinstance(duration, (float, int)) or duration <= 0 or max_frames == 0:
        return requested_fps
    estimated = duration * requested_fps
    if estimated <= max_frames:
        return requested_fps
    return max_frames / duration


def extract_frames(video_path: Path, metadata: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    frame = config["frame"]
    frames_dir = output_dir_for_video(video_path, config)
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    fps = effective_fps(metadata, config)
    max_side = int(frame["max_side"])
    scale_filter = (
        f"fps={fps:.8f},"
        f"scale='if(gt(iw,ih),min({max_side},iw),-2)':"
        f"'if(gt(iw,ih),-2,min({max_side},ih))'"
    )
    output_pattern = frames_dir / "frame_%06d.jpg"
    command = [
        str(frame["ffmpeg"]),
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
        "-i",
        str(video_path),
        "-vf",
        scale_filter,
        "-q:v",
        str(frame["jpeg_quality"]),
        str(output_pattern),
    ]
    result = run_command(command, timeout=int(frame["timeout_seconds"]))
    if result.returncode != 0:
        raise VideoError(
            "ffmpeg frame extraction failed",
            {"stderr": result.stderr.strip(), "video_path": str(video_path)},
        )

    frame_paths = sorted(frames_dir.glob("frame_*.jpg"))
    max_frames = int(frame["max_frames"])
    if max_frames and len(frame_paths) > max_frames:
        for extra in frame_paths[max_frames:]:
            extra.unlink(missing_ok=True)
        frame_paths = frame_paths[:max_frames]
    if not frame_paths:
        raise VideoError("frame extraction produced no images", {"video_path": str(video_path)})

    frames = []
    for index, path in enumerate(frame_paths):
        timestamp = index / fps if fps else index
        frames.append(
            {
                "index": index + 1,
                "timestamp_seconds": round(timestamp, 3),
                "path": str(path),
                "mime_type": "image/jpeg",
                "size_bytes": path.stat().st_size,
            }
        )

    return {
        "mode": "frames",
        "source_path": str(video_path),
        "output_dir": str(frames_dir),
        "requested_interval_seconds": float(frame["interval_seconds"]),
        "effective_interval_seconds": round(1.0 / fps, 6) if fps else None,
        "effective_fps": round(fps, 6),
        "max_frames": max_frames,
        "frame_count": len(frames),
        "max_side": max_side,
        "frames": frames,
        "sampling_note": (
            "frame interval was increased to respect max frame cap"
            if fps < (1.0 / float(frame["interval_seconds"]))
            else ""
        ),
    }


def normalize_mode(mode: Any) -> str:
    value = str(mode or "")
    return MODE_ALIASES.get(value, value)


def select_mode(config: dict[str, Any]) -> str:
    mode = normalize_mode(config["mode"])
    if mode != "auto":
        return mode
    provider = str(config["provider"])
    provider_config = provider_settings(config, provider)
    native_config = native_settings(config, provider)
    if native_config.get("enabled") and provider_config.get("model") and resolve_api_key(provider_config):
        return "video-native"
    if provider_config.get("model") and resolve_api_key(provider_config):
        return "vision-frames"
    return "frames"


def provider_settings(config: dict[str, Any], provider: str) -> dict[str, Any]:
    if provider == "openai-compatible":
        return config["vision_frames"]["openai_compatible"]
    if provider == "gemini":
        return config["vision_frames"]["gemini"]
    raise VideoError("unsupported provider", {"provider": provider})


def native_settings(config: dict[str, Any], provider: str) -> dict[str, Any]:
    if provider == "openai-compatible":
        return config["video_native"]["openai_compatible"]
    if provider == "gemini":
        return config["video_native"]["gemini"]
    raise VideoError("unsupported provider", {"provider": provider})


def resolve_api_key(provider_config: dict[str, Any]) -> str:
    explicit = str(provider_config.get("api_key") or "")
    if explicit:
        return explicit
    env_name = str(provider_config.get("api_key_env") or "")
    return os.environ.get(env_name, "") if env_name else ""


def frame_prompt(question: str, frames: list[dict[str, Any]], batch_number: int, batch_count: int) -> str:
    timestamps = "\n".join(
        f"- frame {item['index']}: t={item['timestamp_seconds']}s" for item in frames
    )
    return (
        "These images are sampled frames from one video in chronological order. "
        f"This is batch {batch_number}/{batch_count}.\n"
        "Extract visible scenes, people, objects, text, UI, watermarks, actions, "
        "and timeline clues. Stay literal and note uncertainty.\n\n"
        f"User question: {question}\n\n"
        f"Frame timestamps:\n{timestamps}"
    )


def final_prompt(question: str, metadata: dict[str, Any], batch_summaries: list[dict[str, Any]]) -> str:
    observations = "\n\n".join(
        f"Batch {item['batch']} ({item['start_seconds']}s-{item['end_seconds']}s):\n{item['answer']}"
        for item in batch_summaries
    )
    return (
        "You analyzed sampled frames from a video. Combine the observations into "
        "one answer to the original user question. Explain visible evidence and "
        "uncertainty. Do not invent audio or motion that the frames do not support.\n\n"
        f"User question: {question}\n\n"
        f"Video metadata:\n{json.dumps(metadata, ensure_ascii=False)}\n\n"
        f"Frame observations:\n{observations}"
    )


def image_data_url(frame: dict[str, Any]) -> str:
    data = Path(frame["path"]).read_bytes()
    encoded = base64.b64encode(data).decode("ascii")
    return f"data:{frame.get('mime_type', 'image/jpeg')};base64,{encoded}"


def image_base64(frame: dict[str, Any]) -> str:
    return base64.b64encode(Path(frame["path"]).read_bytes()).decode("ascii")


def video_base64_data_url(video_path: Path, mime_type: str) -> str:
    encoded = base64.b64encode(video_path.read_bytes()).decode("ascii")
    return f"data:{mime_type};base64,{encoded}"


def format_object_key(prefix: str, video_path: Path) -> str:
    now = dt.datetime.now(dt.UTC)
    digest = hashlib.sha256(video_path.read_bytes()).hexdigest()[:16]
    safe_stem = "".join(ch if ch.isalnum() or ch in ("-", "_") else "-" for ch in video_path.stem)
    suffix = video_path.suffix.lower() or ".mp4"
    filename = f"{safe_stem}-{digest}{suffix}"
    formatted_prefix = prefix.format(
        year=now.strftime("%Y"),
        month=now.strftime("%m"),
        day=now.strftime("%d"),
        date=now.strftime("%Y%m%d"),
    ).strip("/")
    return f"{formatted_prefix}/{filename}" if formatted_prefix else filename


def resolve_secret(config: dict[str, Any], value_name: str, env_name: str) -> str:
    explicit = str(config.get(value_name) or "")
    if explicit:
        return explicit
    env_var = str(config.get(env_name) or "")
    return os.environ.get(env_var, "") if env_var else ""


def upload_video(video_path: Path, metadata: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    upload_config = dict(config.get("video_upload") or {})
    if not upload_config.get("enabled"):
        raise VideoError("video_upload is not enabled")
    host_name = str(upload_config.get("active_host") or "")
    host = dict((upload_config.get("hosts") or {}).get(host_name) or {})
    if not host:
        raise VideoError("video_upload active_host is not configured", {"active_host": host_name})
    if str(host.get("type") or "") != "s3-compatible":
        raise VideoError("unsupported video_upload host type", {"type": host.get("type")})

    endpoint_url = str(host.get("endpoint_url") or "")
    bucket = str(host.get("bucket") or "")
    public_base_url = str(host.get("public_base_url") or "")
    access_key_id = resolve_secret(host, "access_key_id", "access_key_id_env")
    secret_access_key = resolve_secret(host, "secret_access_key", "secret_access_key_env")
    if not endpoint_url or not bucket or not public_base_url:
        raise VideoError(
            "video_upload host is incomplete",
            {
                "missing": [
                    name
                    for name, value in {
                        "endpoint_url": endpoint_url,
                        "bucket": bucket,
                        "public_base_url": public_base_url,
                    }.items()
                    if not value
                ]
            },
        )
    if not access_key_id or not secret_access_key:
        raise VideoError("video_upload credentials are not configured")

    try:
        import boto3  # type: ignore[import-not-found]
        from botocore.config import Config  # type: ignore[import-not-found]
    except ImportError as exc:
        raise VideoError(
            "boto3 is required for video_upload s3-compatible hosts",
            {"hint": "run make install-runtime for video-understanding"},
        ) from exc

    region = str(host.get("region") or "auto")
    client_kwargs: dict[str, Any] = {
        "endpoint_url": endpoint_url,
        "aws_access_key_id": access_key_id,
        "aws_secret_access_key": secret_access_key,
    }
    if region and region != "auto":
        client_kwargs["region_name"] = region
    addressing_style = "path" if bool(host.get("force_path_style")) else "virtual"
    client_kwargs["config"] = Config(
        s3={"addressing_style": addressing_style},
        request_checksum_calculation="when_required",
        response_checksum_validation="when_required",
    )

    key = format_object_key(str(host.get("prefix") or ""), video_path)
    mime_type = str(metadata.get("mime_type") or mimetypes.guess_type(video_path.name)[0] or "video/mp4")
    extra_args = dict(host.get("extra_args") or {})
    extra_args.setdefault("ContentType", mime_type)

    client = boto3.client("s3", **client_kwargs)
    client.upload_file(str(video_path), bucket, key, ExtraArgs=extra_args)
    public_url = f"{public_base_url.rstrip('/')}/{quote(key)}"
    return {
        "backend": "s3-compatible",
        "host": host_name,
        "bucket": bucket,
        "key": key,
        "url": public_url,
        "mime_type": mime_type,
    }


def http_json(
    url: str,
    payload: dict[str, Any],
    headers: dict[str, str],
    timeout: int,
) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    req = request.Request(url, data=body, headers=headers, method="POST")
    try:
        with request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw)
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        raise VideoError(
            "provider HTTP request failed",
            {"status": exc.code, "body": text[:4000], "url": url},
        ) from exc
    except error.URLError as exc:
        raise VideoError("provider request failed", {"reason": str(exc.reason), "url": url}) from exc
    except json.JSONDecodeError as exc:
        raise VideoError("provider returned invalid JSON", {"url": url, "error": str(exc)}) from exc


def http_binary(
    url: str,
    body: bytes,
    headers: dict[str, str],
    timeout: int,
    *,
    method: str = "POST",
) -> tuple[dict[str, Any], Any]:
    data = None if method in {"GET", "DELETE"} and not body else body
    req = request.Request(url, data=data, headers=headers, method=method)
    try:
        with request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw.strip() else {}, response.headers
    except error.HTTPError as exc:
        text = exc.read().decode("utf-8", errors="replace")
        raise VideoError(
            "provider HTTP request failed",
            {"status": exc.code, "body": text[:4000], "url": url},
        ) from exc
    except error.URLError as exc:
        raise VideoError("provider request failed", {"reason": str(exc.reason), "url": url}) from exc
    except json.JSONDecodeError as exc:
        raise VideoError("provider returned invalid JSON", {"url": url, "error": str(exc)}) from exc


def call_openai_compatible(
    provider_config: dict[str, Any],
    *,
    prompt: str,
    frames: list[dict[str, Any]] | None,
    timeout: int,
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    model = str(provider_config.get("model") or "")
    if not api_key:
        raise VideoError("OpenAI-compatible api_key is not configured")
    if not model:
        raise VideoError("OpenAI-compatible model is not configured")

    endpoint = str(provider_config.get("endpoint") or "").rstrip("/")
    if not endpoint:
        raise VideoError("OpenAI-compatible endpoint is not configured")
    content: list[dict[str, Any]] = [{"type": "text", "text": prompt}]
    for frame in frames or []:
        image_url: dict[str, Any] = {"url": image_data_url(frame)}
        detail = provider_config.get("image_detail")
        if detail:
            image_url["detail"] = detail
        content.append({"type": "image_url", "image_url": image_url})

    max_tokens_field = str(provider_config.get("max_tokens_field") or "max_tokens")
    payload: dict[str, Any] = {
        "model": model,
        "messages": [{"role": "user", "content": content}],
        "temperature": provider_config.get("temperature", 0.2),
        max_tokens_field: provider_config.get("max_tokens", 2048),
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    headers.update({str(k): str(v) for k, v in dict(provider_config.get("headers") or {}).items()})
    data = http_json(endpoint, payload, headers, timeout)
    choices = data.get("choices") or []
    if not choices:
        raise VideoError("OpenAI-compatible response has no choices", {"response": data})
    message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
    content_value = message.get("content")
    if isinstance(content_value, str):
        answer = content_value
    elif isinstance(content_value, list):
        answer = "\n".join(
            part.get("text", "") if isinstance(part, dict) else str(part)
            for part in content_value
        ).strip()
    else:
        answer = ""
    return {"answer": answer, "raw": data, "usage": data.get("usage")}


def call_gemini(
    provider_config: dict[str, Any],
    *,
    prompt: str,
    frames: list[dict[str, Any]] | None,
    timeout: int,
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    model = str(provider_config.get("model") or "")
    if not api_key:
        raise VideoError("Gemini api_key is not configured")
    if not model:
        raise VideoError("Gemini model is not configured")

    endpoint_template = str(provider_config.get("endpoint") or "")
    endpoint = endpoint_template.format(model=model if model.startswith("models/") else f"models/{model}")
    parts: list[dict[str, Any]] = [{"text": prompt}]
    for frame in frames or []:
        parts.append(
            {
                "inlineData": {
                    "mimeType": frame.get("mime_type", "image/jpeg"),
                    "data": image_base64(frame),
                }
            }
        )
    payload: dict[str, Any] = {
        "contents": [{"role": "user", "parts": parts}],
        "generationConfig": {
            "temperature": provider_config.get("temperature", 0.2),
            "maxOutputTokens": provider_config.get("max_output_tokens", 2048),
        },
    }
    headers = {"Content-Type": "application/json", "x-goog-api-key": api_key}
    headers.update({str(k): str(v) for k, v in dict(provider_config.get("headers") or {}).items()})
    data = http_json(endpoint, payload, headers, timeout)
    candidates = data.get("candidates") or []
    if not candidates:
        raise VideoError("Gemini response has no candidates", {"response": data})
    parts_out = candidates[0].get("content", {}).get("parts", [])
    answer = "\n".join(part.get("text", "") for part in parts_out if isinstance(part, dict)).strip()
    return {"answer": answer, "raw": data, "usage": data.get("usageMetadata")}


def call_provider(
    provider: str,
    provider_config: dict[str, Any],
    *,
    prompt: str,
    frames: list[dict[str, Any]] | None,
    timeout: int,
) -> dict[str, Any]:
    if provider == "openai-compatible":
        return call_openai_compatible(provider_config, prompt=prompt, frames=frames, timeout=timeout)
    if provider == "gemini":
        return call_gemini(provider_config, prompt=prompt, frames=frames, timeout=timeout)
    raise VideoError("unsupported provider", {"provider": provider})


def native_video_prompt(question: str, metadata: dict[str, Any]) -> str:
    return (
        "Analyze the attached video directly. Describe visible scenes, people, objects, text, UI, "
        "watermarks, actions, timing, and uncertainty. Do not invent content that is not visible or "
        "audible in the supplied video.\n\n"
        f"User question: {question}\n\n"
        f"Video metadata:\n{json.dumps(metadata, ensure_ascii=False)}"
    )


def openai_native_video_url(
    video_path: Path,
    metadata: dict[str, Any],
    native_config: dict[str, Any],
    config: dict[str, Any],
) -> tuple[dict[str, Any], dict[str, Any]]:
    method = str(native_config.get("input_method") or "auto")
    explicit_url = str(native_config.get("video_url") or "")
    max_inline_bytes = int(native_config.get("max_inline_bytes") or 7340032)
    size_bytes = int(metadata.get("size_bytes") or video_path.stat().st_size)
    mime_type = str(metadata.get("mime_type") or mimetypes.guess_type(video_path.name)[0] or "video/mp4")

    if method == "url":
        if not explicit_url:
            raise VideoError("video_native.openai_compatible.video_url is required for input_method=url")
        url = explicit_url
        source = {"type": "configured-url", "url": url}
    elif method == "upload" or (method == "auto" and not explicit_url and config.get("video_upload", {}).get("enabled")):
        upload = upload_video(video_path, metadata, config)
        url = str(upload["url"])
        source = {"type": "uploaded-url", **upload}
    elif method == "auto" and explicit_url:
        url = explicit_url
        source = {"type": "configured-url", "url": url}
    elif method in {"auto", "base64", "inline"}:
        if size_bytes > max_inline_bytes:
            raise VideoError(
                "video is too large for inline video-native input",
                {
                    "size_bytes": size_bytes,
                    "max_inline_bytes": max_inline_bytes,
                    "hint": "enable video_upload or set video_native.openai_compatible.video_url",
                },
            )
        url = video_base64_data_url(video_path, mime_type)
        source = {"type": "inline-base64", "mime_type": mime_type, "size_bytes": size_bytes}
    else:
        raise VideoError("unsupported OpenAI-compatible video-native input method", {"input_method": method})

    video_url: dict[str, Any] = {"url": url}
    for key in ("fps", "min_pixels", "max_pixels", "total_pixels"):
        value = native_config.get(key)
        if value not in (None, ""):
            video_url[key] = value
    return video_url, source


def call_openai_compatible_video_native(
    provider_config: dict[str, Any],
    native_config: dict[str, Any],
    config: dict[str, Any],
    *,
    question: str,
    metadata: dict[str, Any],
    video_path: Path,
    timeout: int,
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    model = str(provider_config.get("model") or "")
    if not api_key:
        raise VideoError("OpenAI-compatible api_key is not configured")
    if not model:
        raise VideoError("OpenAI-compatible model is not configured")

    endpoint = str(provider_config.get("endpoint") or "").rstrip("/")
    if not endpoint:
        raise VideoError("OpenAI-compatible endpoint is not configured")

    video_url, source = openai_native_video_url(video_path, metadata, native_config, config)
    content = [
        {"type": "text", "text": native_video_prompt(question, metadata)},
        {"type": "video_url", "video_url": video_url},
    ]
    max_tokens_field = str(provider_config.get("max_tokens_field") or "max_tokens")
    payload: dict[str, Any] = {
        "model": model,
        "messages": [{"role": "user", "content": content}],
        "temperature": provider_config.get("temperature", 0.2),
        max_tokens_field: provider_config.get("max_tokens", 2048),
    }
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    headers.update({str(k): str(v) for k, v in dict(provider_config.get("headers") or {}).items()})
    data = http_json(endpoint, payload, headers, timeout)
    choices = data.get("choices") or []
    if not choices:
        raise VideoError("OpenAI-compatible response has no choices", {"response": data})
    message = choices[0].get("message", {}) if isinstance(choices[0], dict) else {}
    content_value = message.get("content")
    answer = content_value if isinstance(content_value, str) else str(content_value or "")
    return {"answer": answer, "raw": data, "usage": data.get("usage"), "video_input": source}


def gemini_file_url(files_endpoint: str, file_name: str, api_key: str) -> str:
    base = files_endpoint.rstrip("/")
    name = file_name if file_name.startswith("files/") else f"files/{file_name}"
    return f"{base}/{name}?key={quote(api_key)}"


def upload_gemini_file(
    provider_config: dict[str, Any],
    native_config: dict[str, Any],
    video_path: Path,
    metadata: dict[str, Any],
    timeout: int,
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    if not api_key:
        raise VideoError("Gemini api_key is not configured")

    upload_endpoint = str(native_config.get("upload_endpoint") or "").rstrip("/")
    if not upload_endpoint:
        raise VideoError("Gemini upload_endpoint is not configured")
    upload_start_url = f"{upload_endpoint}?key={quote(api_key)}"
    mime_type = str(metadata.get("mime_type") or mimetypes.guess_type(video_path.name)[0] or "video/mp4")
    size_bytes = int(metadata.get("size_bytes") or video_path.stat().st_size)
    start_payload = json.dumps({"file": {"display_name": video_path.name}}).encode("utf-8")
    start_headers = {
        "Content-Type": "application/json",
        "X-Goog-Upload-Protocol": "resumable",
        "X-Goog-Upload-Command": "start",
        "X-Goog-Upload-Header-Content-Length": str(size_bytes),
        "X-Goog-Upload-Header-Content-Type": mime_type,
    }
    _, headers = http_binary(upload_start_url, start_payload, start_headers, timeout)
    upload_url = headers.get("x-goog-upload-url") or headers.get("X-Goog-Upload-URL")
    if not upload_url:
        raise VideoError("Gemini upload start did not return an upload URL")

    upload_headers = {
        "Content-Length": str(size_bytes),
        "X-Goog-Upload-Offset": "0",
        "X-Goog-Upload-Command": "upload, finalize",
        "Content-Type": mime_type,
    }
    data, _ = http_binary(str(upload_url), video_path.read_bytes(), upload_headers, timeout)
    file_info = data.get("file") if isinstance(data.get("file"), dict) else data
    if not isinstance(file_info, dict) or not file_info.get("uri"):
        raise VideoError("Gemini upload response does not contain a file uri", {"response": data})
    return file_info


def poll_gemini_file(
    provider_config: dict[str, Any],
    native_config: dict[str, Any],
    file_info: dict[str, Any],
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    files_endpoint = str(native_config.get("files_endpoint") or "").rstrip("/")
    if not files_endpoint:
        raise VideoError("Gemini files_endpoint is not configured")
    file_name = str(file_info.get("name") or "")
    if not file_name:
        raise VideoError("Gemini file name is missing", {"file": file_info})

    deadline = time.time() + int(native_config.get("poll_timeout_seconds") or 600)
    interval = float(native_config.get("poll_interval_seconds") or 2)
    current = file_info
    while time.time() < deadline:
        state = str(current.get("state") or "")
        if state in {"ACTIVE", "SUCCEEDED"} or not state:
            return current
        if state in {"FAILED", "ERROR"}:
            raise VideoError("Gemini file processing failed", {"file": current})
        time.sleep(interval)
        data, _ = http_binary(
            gemini_file_url(files_endpoint, file_name, api_key),
            b"",
            {},
            timeout=30,
            method="GET",
        )
        current = data.get("file") if isinstance(data.get("file"), dict) else data
    raise VideoError("Gemini file processing timed out", {"file": current})


def delete_gemini_file(provider_config: dict[str, Any], native_config: dict[str, Any], file_info: dict[str, Any]) -> None:
    api_key = resolve_api_key(provider_config)
    files_endpoint = str(native_config.get("files_endpoint") or "").rstrip("/")
    file_name = str(file_info.get("name") or "")
    if not api_key or not files_endpoint or not file_name:
        return
    try:
        http_binary(gemini_file_url(files_endpoint, file_name, api_key), b"", {}, timeout=30, method="DELETE")
    except VideoError:
        return


def call_gemini_video_native(
    provider_config: dict[str, Any],
    native_config: dict[str, Any],
    *,
    question: str,
    metadata: dict[str, Any],
    video_path: Path,
    timeout: int,
) -> dict[str, Any]:
    api_key = resolve_api_key(provider_config)
    model = str(provider_config.get("model") or "")
    if not api_key:
        raise VideoError("Gemini api_key is not configured")
    if not model:
        raise VideoError("Gemini model is not configured")

    endpoint_template = str(native_config.get("generate_endpoint") or provider_config.get("endpoint") or "")
    endpoint = endpoint_template.format(model=model if model.startswith("models/") else f"models/{model}")
    endpoint = f"{endpoint}?key={quote(api_key)}" if "key=" not in endpoint else endpoint
    file_info = upload_gemini_file(provider_config, native_config, video_path, metadata, timeout)
    try:
        file_info = poll_gemini_file(provider_config, native_config, file_info)
        mime_type = str(file_info.get("mimeType") or metadata.get("mime_type") or "video/mp4")
        payload: dict[str, Any] = {
            "contents": [
                {
                    "role": "user",
                    "parts": [
                        {"text": native_video_prompt(question, metadata)},
                        {"fileData": {"mimeType": mime_type, "fileUri": file_info["uri"]}},
                    ],
                }
            ],
            "generationConfig": {
                "temperature": provider_config.get("temperature", 0.2),
                "maxOutputTokens": provider_config.get("max_output_tokens", 2048),
            },
        }
        headers = {"Content-Type": "application/json"}
        headers.update({str(k): str(v) for k, v in dict(provider_config.get("headers") or {}).items()})
        data = http_json(endpoint, payload, headers, timeout)
    finally:
        if bool(native_config.get("delete_uploaded_file", True)):
            delete_gemini_file(provider_config, native_config, file_info)

    candidates = data.get("candidates") or []
    if not candidates:
        raise VideoError("Gemini response has no candidates", {"response": data})
    parts_out = candidates[0].get("content", {}).get("parts", [])
    answer = "\n".join(part.get("text", "") for part in parts_out if isinstance(part, dict)).strip()
    return {"answer": answer, "raw": data, "usage": data.get("usageMetadata"), "video_input": {"type": "gemini-file-api", "file": file_info}}


def analyze_video_native(
    provider: str,
    provider_config: dict[str, Any],
    native_config: dict[str, Any],
    *,
    question: str,
    metadata: dict[str, Any],
    video_path: Path,
    config: dict[str, Any],
) -> dict[str, Any]:
    timeout = int(config["video_native"]["request_timeout_seconds"])
    if provider == "openai-compatible":
        return call_openai_compatible_video_native(
            provider_config,
            native_config,
            config,
            question=question,
            metadata=metadata,
            video_path=video_path,
            timeout=timeout,
        )
    if provider == "gemini":
        return call_gemini_video_native(
            provider_config,
            native_config,
            question=question,
            metadata=metadata,
            video_path=video_path,
            timeout=timeout,
        )
    raise VideoError("unsupported provider", {"provider": provider})


def analyze_with_provider(
    provider: str,
    provider_config: dict[str, Any],
    *,
    question: str,
    metadata: dict[str, Any],
    frame_manifest: dict[str, Any],
    config: dict[str, Any],
) -> dict[str, Any]:
    frames = list(frame_manifest["frames"])
    batch_size = int(config["vision_frames"]["batch_size"])
    timeout = int(config["vision_frames"]["request_timeout_seconds"])
    batches = [frames[index : index + batch_size] for index in range(0, len(frames), batch_size)]
    batch_summaries: list[dict[str, Any]] = []

    for batch_index, batch in enumerate(batches, start=1):
        prompt = frame_prompt(question, batch, batch_index, len(batches))
        result = call_provider(
            provider,
            provider_config,
            prompt=prompt,
            frames=batch,
            timeout=timeout,
        )
        batch_summaries.append(
            {
                "batch": batch_index,
                "start_frame": batch[0]["index"],
                "end_frame": batch[-1]["index"],
                "start_seconds": batch[0]["timestamp_seconds"],
                "end_seconds": batch[-1]["timestamp_seconds"],
                "answer": result.get("answer", ""),
                "usage": result.get("usage"),
            }
        )

    final = call_provider(
        provider,
        provider_config,
        prompt=final_prompt(question, metadata, batch_summaries),
        frames=None,
        timeout=timeout,
    )
    return {
        "answer": final.get("answer", ""),
        "usage": final.get("usage"),
        "batch_summaries": batch_summaries,
    }


def frame_mode_payload(
    *,
    question: str,
    metadata: dict[str, Any],
    frame_manifest: dict[str, Any],
    reason: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "ok": True,
        "analysis_mode": "frames",
        "requires_agent_frame_analysis": True,
        "question": question,
        "answer": "",
        "video_metadata": metadata,
        "frame_manifest": frame_manifest,
        "agent_instruction": (
            "Inspect the extracted frames directly and answer the question from visible evidence. "
            "Use the frame timestamps in frame_manifest.frames for timeline references."
        ),
    }
    if reason:
        payload["fallback_reason"] = reason
    return payload


def vision_frames_payload(
    *,
    provider: str,
    provider_config: dict[str, Any],
    question: str,
    metadata: dict[str, Any],
    video_path: Path,
    config: dict[str, Any],
    reason: dict[str, Any] | None = None,
) -> dict[str, Any]:
    frame_manifest = extract_frames(video_path, metadata, config)
    try:
        provider_result = analyze_with_provider(
            provider,
            provider_config,
            question=question,
            metadata=metadata,
            frame_manifest=frame_manifest,
            config=config,
        )
    except VideoError as exc:
        if bool(config["vision_frames"].get("fallback_to_frames", True)):
            return frame_mode_payload(
                question=question,
                metadata=metadata,
                frame_manifest=frame_manifest,
                reason={"error": exc.message, "details": exc.details},
            )
        raise

    if not bool(config["frame"].get("keep_frames", True)):
        shutil.rmtree(frame_manifest["output_dir"], ignore_errors=True)
        frame_manifest["frames"] = []
        frame_manifest["output_dir_removed"] = True

    payload: dict[str, Any] = {
        "ok": True,
        "analysis_mode": "vision-frames",
        "provider": provider,
        "model": provider_config.get("model"),
        "requires_agent_frame_analysis": False,
        "question": question,
        "answer": provider_result.get("answer", ""),
        "video_metadata": metadata,
        "frame_manifest": frame_manifest,
        "batch_summaries": provider_result.get("batch_summaries"),
        "usage": provider_result.get("usage"),
    }
    if reason:
        payload["fallback_reason"] = reason
    return payload


def main() -> int:
    args = parse_args()
    try:
        config = load_config(args)
        if config.get("clear_proxy_env", True):
            clear_proxy_env()
        if args.print_config:
            print(json.dumps({"ok": True, "config": redact_config(config)}, indent=2))
            return 0

        video_path = Path(args.video_path).expanduser().resolve()
        metadata = validate_video(video_path, config)
        selected_mode = select_mode(config)

        if selected_mode == "frames":
            frame_manifest = extract_frames(video_path, metadata, config)
            print(
                json.dumps(
                    frame_mode_payload(
                        question=args.question,
                        metadata=metadata,
                        frame_manifest=frame_manifest,
                    ),
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        provider = str(config["provider"])
        provider_config = provider_settings(config, provider)
        if selected_mode == "video-native":
            native_config = native_settings(config, provider)
            try:
                provider_result = analyze_video_native(
                    provider,
                    provider_config,
                    native_config,
                    question=args.question,
                    metadata=metadata,
                    video_path=video_path,
                    config=config,
                )
            except VideoError as exc:
                if bool(config["video_native"].get("fallback_to_vision_frames", True)):
                    print(
                        json.dumps(
                            vision_frames_payload(
                                provider=provider,
                                provider_config=provider_config,
                                question=args.question,
                                metadata=metadata,
                                video_path=video_path,
                                config=config,
                                reason={"error": exc.message, "details": exc.details},
                            ),
                            ensure_ascii=False,
                            indent=2,
                        )
                    )
                    return 0
                raise

            print(
                json.dumps(
                    {
                        "ok": True,
                        "analysis_mode": "video-native",
                        "provider": provider,
                        "model": provider_config.get("model"),
                        "requires_agent_frame_analysis": False,
                        "question": args.question,
                        "answer": provider_result.get("answer", ""),
                        "video_metadata": metadata,
                        "video_input": provider_result.get("video_input"),
                        "usage": provider_result.get("usage"),
                    },
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        if selected_mode == "vision-frames":
            print(
                json.dumps(
                    vision_frames_payload(
                        provider=provider,
                        provider_config=provider_config,
                        question=args.question,
                        metadata=metadata,
                        video_path=video_path,
                        config=config,
                    ),
                    ensure_ascii=False,
                    indent=2,
                )
            )
            return 0

        raise VideoError("unsupported selected mode", {"mode": selected_mode})
    except VideoError as exc:
        fail(exc.message, exc.details)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

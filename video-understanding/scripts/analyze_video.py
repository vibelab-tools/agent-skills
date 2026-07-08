#!/usr/bin/env python3
"""Analyze local video files through sampled frames or configurable vision APIs."""

from __future__ import annotations

import argparse
import base64
import datetime as dt
import json
import mimetypes
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from typing import Any
from urllib import error, request
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

DEFAULT_RUNTIME_ROOT = Path.home() / ".vibe-coding-skill" / "video-understanding"
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
    "multimodal": {
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
            "api_key_env": "GEMINI_API_KEY",
            "model": "",
            "temperature": 0.2,
            "max_output_tokens": 2048,
            "headers": {},
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
        default=os.environ.get("VIDEO_UNDERSTANDING_CONFIG", str(DEFAULT_CONFIG_PATH)),
        help="JSON config path. Default: ~/.vibe-coding-skill/video-understanding/config.json.",
    )
    parser.add_argument(
        "--mode",
        choices=("auto", "frames", "multimodal"),
        default="",
        help="Override configured analysis mode.",
    )
    parser.add_argument(
        "--provider",
        choices=("openai-compatible", "gemini"),
        default="",
        help="Override configured multimodal provider.",
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
    config = deep_merge(DEFAULT_CONFIG, read_json_file(Path(args.config).expanduser()))

    env_mode = os.environ.get("VIDEO_UNDERSTANDING_MODE", "")
    env_provider = os.environ.get("VIDEO_UNDERSTANDING_PROVIDER", "")
    if env_mode:
        config["mode"] = env_mode
    if env_provider:
        config["provider"] = env_provider
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
    mode = config.get("mode")
    provider = config.get("provider")
    if mode not in {"auto", "frames", "multimodal"}:
        raise VideoError("invalid mode", {"mode": mode, "allowed": ["auto", "frames", "multimodal"]})
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
    if int(config["multimodal"]["batch_size"]) <= 0:
        raise VideoError("multimodal.batch_size must be greater than 0")
    if int(config["multimodal"]["request_timeout_seconds"]) <= 0:
        raise VideoError("multimodal.request_timeout_seconds must be greater than 0")


def redact_config(config: dict[str, Any]) -> dict[str, Any]:
    def redact(value: Any, key: str = "") -> Any:
        if isinstance(value, dict):
            return {k: redact(v, k) for k, v in value.items()}
        if key.lower() in {"api_key", "authorization", "x-goog-api-key"} and value:
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


def select_mode(config: dict[str, Any]) -> str:
    mode = str(config["mode"])
    if mode != "auto":
        return mode
    provider = str(config["provider"])
    provider_config = provider_settings(config, provider)
    if provider_config.get("model") and resolve_api_key(provider_config):
        return "multimodal"
    return "frames"


def provider_settings(config: dict[str, Any], provider: str) -> dict[str, Any]:
    if provider == "openai-compatible":
        return config["multimodal"]["openai_compatible"]
    if provider == "gemini":
        return config["multimodal"]["gemini"]
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
    batch_size = int(config["multimodal"]["batch_size"])
    timeout = int(config["multimodal"]["request_timeout_seconds"])
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
        frame_manifest = extract_frames(video_path, metadata, config)

        if selected_mode == "frames":
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
        try:
            provider_result = analyze_with_provider(
                provider,
                provider_config,
                question=args.question,
                metadata=metadata,
                frame_manifest=frame_manifest,
                config=config,
            )
        except VideoError as exc:
            if bool(config["multimodal"].get("fallback_to_frames", True)):
                print(
                    json.dumps(
                        frame_mode_payload(
                            question=args.question,
                            metadata=metadata,
                            frame_manifest=frame_manifest,
                            reason={"error": exc.message, "details": exc.details},
                        ),
                        ensure_ascii=False,
                        indent=2,
                    )
                )
                return 0
            raise

        if not bool(config["frame"].get("keep_frames", True)):
            shutil.rmtree(frame_manifest["output_dir"], ignore_errors=True)
            frame_manifest["frames"] = []
            frame_manifest["output_dir_removed"] = True

        print(
            json.dumps(
                {
                    "ok": True,
                    "analysis_mode": "multimodal",
                    "provider": provider,
                    "model": provider_config.get("model"),
                    "requires_agent_frame_analysis": False,
                    "question": args.question,
                    "answer": provider_result.get("answer", ""),
                    "video_metadata": metadata,
                    "frame_manifest": frame_manifest,
                    "batch_summaries": provider_result.get("batch_summaries"),
                    "usage": provider_result.get("usage"),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0
    except VideoError as exc:
        fail(exc.message, exc.details)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Prepare local video frames and optional URL subtitles for agent analysis."""

from __future__ import annotations

import argparse
import datetime as dt
import html
import json
import math
import mimetypes
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
from typing import Any
from urllib.parse import urlparse
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
DEFAULT_OUTPUT_ROOT = DEFAULT_RUNTIME_ROOT / "runs"
DEFAULT_QUESTION = (
    "Summarize this video. Describe visible scenes, people, objects, on-screen text, "
    "UI, actions, timing, and uncertainty."
)

# Ordered exactly by the desired download preference: 720p, 540p-class,
# 1080p, 2K/4K, low resolution, then a final compatibility fallback.
DEFAULT_FORMAT_SELECTOR = "/".join(
    (
        "bv[height=720]+ba/b[height=720]",
        "bv[height<=540][height>360]+ba/b[height<=540][height>360]",
        "bv[height=1080]+ba/b[height=1080]",
        "bv[height>=1440]+ba/b[height>=1440]",
        "bv[height<=360]+ba/b[height<=360]",
        "bv*+ba/b",
    )
)

DEFAULT_CONFIG: dict[str, Any] = {
    "frame": {
        "interval_seconds": 1.0,
        "max_frames": 0,
        "max_side": 720,
        "jpeg_quality": 3,
        "output_root": "",
        "ffmpeg": "ffmpeg",
        "ffprobe": "ffprobe",
        "timeout_seconds": 1800,
    },
    "download": {
        "yt_dlp": "auto",
        "format_selector": DEFAULT_FORMAT_SELECTOR,
        "cookies_from_browser": "auto",
        "proxy": "auto",
        "socket_timeout_seconds": 30,
        "timeout_seconds": 3600,
    },
}

VTT_TIMESTAMP_RE = re.compile(
    r"(?P<start>(?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{3})\s*-->\s*"
    r"(?P<end>(?:\d{1,2}:)?\d{2}:\d{2}[.,]\d{3})"
)
VTT_TAG_RE = re.compile(r"<[^>]+>")
PROXY_ENV_ORDER = (
    "HTTPS_PROXY",
    "https_proxy",
    "ALL_PROXY",
    "all_proxy",
    "HTTP_PROXY",
    "http_proxy",
)
BROWSER_PRIORITY = ("chrome", "firefox", "edge", "brave", "chromium", "safari", "vivaldi", "opera")


class VideoError(Exception):
    def __init__(self, message: str, details: dict[str, Any] | None = None):
        super().__init__(message)
        self.message = message
        self.details = details or {}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Download or open a video, extract timestamped frames, and return local evidence "
            "for Codex or Claude to analyze."
        )
    )
    parser.add_argument("source", nargs="?", help="Local video path or a URL supported by yt-dlp.")
    parser.add_argument("--question", default=DEFAULT_QUESTION, help="Question to answer from the video evidence.")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG_PATH), help="Runtime JSON config path.")
    parser.add_argument("--output-dir", help="Use this directory for the downloaded video, subtitles, and frames.")
    parser.add_argument("--frame-interval-seconds", type=float, help="Requested interval between uniform frames.")
    parser.add_argument("--max-frames", type=int, help="Maximum number of frames to extract. Use 0 for no cap.")
    parser.add_argument("--start", help="Analysis start time as seconds, MM:SS, or HH:MM:SS.")
    parser.add_argument("--end", help="Analysis end time as seconds, MM:SS, or HH:MM:SS.")
    parser.add_argument(
        "--cookies-from-browser",
        help="Browser name, 'auto' for local detection, or 'none' to disable browser cookies.",
    )
    parser.add_argument("--proxy", help="Proxy URL, 'auto' for environment/system detection, or 'none' for direct.")
    parser.add_argument("--print-config", action="store_true", help="Print the effective config and exit.")
    parser.add_argument("--migrate-config", action="store_true", help=argparse.SUPPRESS)
    return parser.parse_args()


def merge_known(defaults: dict[str, Any], override: dict[str, Any]) -> dict[str, Any]:
    """Merge only keys declared in defaults so obsolete provider secrets stay out."""
    merged: dict[str, Any] = {}
    for key, default_value in defaults.items():
        override_value = override.get(key)
        if isinstance(default_value, dict):
            nested_override = override_value if isinstance(override_value, dict) else {}
            merged[key] = merge_known(default_value, nested_override)
        elif key in override:
            merged[key] = override_value
        else:
            merged[key] = default_value
    return merged


def migrate_legacy_defaults(raw: dict[str, Any], config: dict[str, Any]) -> dict[str, Any]:
    legacy_schema = any(
        key in raw for key in ("mode", "provider", "vision_frames", "video_native", "video_upload")
    )
    raw_frame = raw.get("frame")
    legacy_frame: dict[str, Any] = raw_frame if isinstance(raw_frame, dict) else {}
    if legacy_schema and legacy_frame.get("max_frames") == 240:
        config["frame"]["max_frames"] = DEFAULT_CONFIG["frame"]["max_frames"]
    return config


def read_json_file(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise VideoError("could not read config JSON", {"path": str(path), "error": str(exc)}) from exc
    if not isinstance(value, dict):
        raise VideoError("config root must be a JSON object", {"path": str(path)})
    return value


def load_config(args: argparse.Namespace) -> dict[str, Any]:
    raw = read_json_file(Path(args.config).expanduser())
    config = migrate_legacy_defaults(raw, merge_known(DEFAULT_CONFIG, raw))
    if args.frame_interval_seconds is not None:
        config["frame"]["interval_seconds"] = args.frame_interval_seconds
    if args.max_frames is not None:
        config["frame"]["max_frames"] = args.max_frames
    if args.cookies_from_browser is not None:
        config["download"]["cookies_from_browser"] = args.cookies_from_browser
    if args.proxy is not None:
        config["download"]["proxy"] = args.proxy
    validate_config(config)
    return config


def validate_config(config: dict[str, Any]) -> None:
    frame = config["frame"]
    download = config["download"]
    if float(frame["interval_seconds"]) <= 0:
        raise VideoError("frame.interval_seconds must be greater than 0")
    if int(frame["max_frames"]) < 0:
        raise VideoError("frame.max_frames must be greater than or equal to 0")
    if int(frame["max_side"]) <= 0:
        raise VideoError("frame.max_side must be greater than 0")
    if not 1 <= int(frame["jpeg_quality"]) <= 31:
        raise VideoError("frame.jpeg_quality must be between 1 and 31")
    if int(frame["timeout_seconds"]) <= 0:
        raise VideoError("frame.timeout_seconds must be greater than 0")
    if int(download["socket_timeout_seconds"]) <= 0:
        raise VideoError("download.socket_timeout_seconds must be greater than 0")
    if int(download["timeout_seconds"]) <= 0:
        raise VideoError("download.timeout_seconds must be greater than 0")
    if not str(download["format_selector"]).strip():
        raise VideoError("download.format_selector must not be empty")


def write_migrated_config(path: Path, config: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_suffix(path.suffix + ".tmp")
    temp_path.write_text(json.dumps(config, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.chmod(temp_path, 0o600)
    temp_path.replace(path)


def run_command(command: list[str], timeout: int) -> subprocess.CompletedProcess[str]:
    try:
        return subprocess.run(
            command,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            timeout=timeout,
            check=False,
        )
    except FileNotFoundError as exc:
        raise VideoError("required command was not found", {"command": command[0]}) from exc
    except subprocess.TimeoutExpired as exc:
        raise VideoError("command timed out", {"command": command[0], "timeout_seconds": timeout}) from exc


def resolve_command(value: str, name: str) -> str:
    configured = str(value or "auto")
    if configured not in {"", "auto"}:
        path = Path(configured).expanduser()
        if path.is_file() and os.access(path, os.X_OK):
            return str(path.resolve())
        resolved = shutil.which(configured)
        if resolved:
            return resolved
        raise VideoError("configured command was not found", {"command": configured})

    # Keep the venv path intact; resolving the Python symlink would jump to the
    # base interpreter and could select an unrelated global yt-dlp.
    sibling = Path(sys.executable).parent / name
    if sibling.is_file() and os.access(sibling, os.X_OK):
        return str(sibling)
    resolved = shutil.which(name)
    if resolved:
        return resolved
    raise VideoError("required command was not found", {"command": name})


def is_url(source: str) -> bool:
    if source.startswith("-"):
        return False
    parsed = urlparse(source)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


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
    if result.returncode != 0:
        raise VideoError(
            "ffprobe could not inspect the video",
            {"path": str(path), "stderr": result.stderr.strip()[-2000:]},
        )
    try:
        data = json.loads(result.stdout)
    except json.JSONDecodeError as exc:
        raise VideoError("ffprobe returned invalid JSON", {"path": str(path)}) from exc

    video_stream = next(
        (stream for stream in data.get("streams", []) if stream.get("codec_type") == "video"),
        {},
    )
    if not video_stream:
        raise VideoError("file has no video stream", {"path": str(path)})
    audio_streams = [stream for stream in data.get("streams", []) if stream.get("codec_type") == "audio"]
    fmt = data.get("format", {})
    metadata: dict[str, Any] = {
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


def validate_local_video(path: Path, ffprobe: str) -> dict[str, Any]:
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
    metadata = run_ffprobe(path, ffprobe)
    size_bytes = path.stat().st_size
    metadata.update(
        {
            "path": str(path),
            "size_bytes": size_bytes,
            "size_mb": round(size_bytes / 1024 / 1024, 3),
            "mime_type": mime_type or "application/octet-stream",
        }
    )
    return metadata


def safe_source_name(source: str) -> str:
    parsed = urlparse(source)
    raw = Path(parsed.path).stem if parsed.scheme else Path(source).stem
    raw = raw or parsed.netloc or "video"
    return "".join(ch if ch.isalnum() or ch in ("-", "_") else "-" for ch in raw)[:80] or "video"


def create_run_dir(source: str, config: dict[str, Any], explicit: str | None) -> Path:
    if explicit:
        run_dir = Path(explicit).expanduser().resolve()
    else:
        configured_root = str(config["frame"].get("output_root") or "")
        output_root = Path(configured_root).expanduser() if configured_root else DEFAULT_OUTPUT_ROOT
        timestamp = dt.datetime.now(dt.UTC).strftime("%Y%m%dT%H%M%SZ")
        digest = f"{zlib.crc32(source.encode('utf-8')) & 0xFFFFFFFF:08x}"
        run_dir = output_root / f"{timestamp}-{safe_source_name(source)}-{digest}"
    run_dir.mkdir(parents=True, exist_ok=True)
    return run_dir


def parse_time(value: str | None) -> float | None:
    if value is None:
        return None
    text = value.strip()
    if not text:
        raise VideoError("time value must not be empty")
    parts = text.split(":")
    if len(parts) > 3:
        raise VideoError("invalid time value", {"value": value})
    try:
        numbers = [float(part) for part in parts]
    except ValueError as exc:
        raise VideoError("invalid time value", {"value": value}) from exc
    if any(not math.isfinite(number) for number in numbers):
        raise VideoError("time value must be finite", {"value": value})
    if any(number < 0 for number in numbers):
        raise VideoError("time value must be non-negative", {"value": value})
    if len(numbers) >= 2 and numbers[-1] >= 60:
        raise VideoError("seconds component must be less than 60", {"value": value})
    if len(numbers) == 3 and numbers[-2] >= 60:
        raise VideoError("minutes component must be less than 60", {"value": value})
    if len(numbers) == 1:
        return numbers[0]
    if len(numbers) == 2:
        return numbers[0] * 60 + numbers[1]
    return numbers[0] * 3600 + numbers[1] * 60 + numbers[2]


def resolve_analysis_range(
    metadata: dict[str, Any],
    start_value: str | None,
    end_value: str | None,
) -> dict[str, Any]:
    full_duration = metadata.get("duration_seconds")
    known_duration = (
        float(full_duration)
        if isinstance(full_duration, (float, int)) and full_duration > 0
        else None
    )
    start = parse_time(start_value) or 0.0
    requested_end = parse_time(end_value)
    if known_duration is not None and start >= known_duration:
        raise VideoError(
            "analysis start is at or past the end of the video",
            {"start_seconds": start, "duration_seconds": known_duration},
        )
    end = requested_end if requested_end is not None else known_duration
    if known_duration is not None and end is not None:
        end = min(end, known_duration)
    if end is not None and end <= start:
        raise VideoError(
            "analysis end must be greater than analysis start",
            {"start_seconds": start, "end_seconds": end},
        )
    duration = end - start if end is not None else None
    return {
        "start_seconds": round(start, 3),
        "end_seconds": round(end, 3) if end is not None else None,
        "duration_seconds": round(duration, 3) if duration is not None else None,
        "is_full_video": start_value is None and end_value is None,
    }


def effective_fps(
    metadata: dict[str, Any],
    config: dict[str, Any],
    duration_seconds: float | None = None,
) -> float:
    frame = config["frame"]
    requested_fps = 1.0 / float(frame["interval_seconds"])
    max_frames = int(frame["max_frames"])
    duration = duration_seconds if duration_seconds is not None else metadata.get("duration_seconds")
    if not isinstance(duration, (float, int)) or duration <= 0 or max_frames == 0:
        return requested_fps
    estimated = duration * requested_fps
    return requested_fps if estimated <= max_frames else max_frames / duration


def extract_frames(
    video_path: Path,
    metadata: dict[str, Any],
    config: dict[str, Any],
    frames_dir: Path,
    analysis_range: dict[str, Any],
) -> dict[str, Any]:
    frame = config["frame"]
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    frames_dir.mkdir(parents=True, exist_ok=True)

    range_duration = analysis_range.get("duration_seconds")
    fps = effective_fps(
        metadata,
        config,
        float(range_duration) if isinstance(range_duration, (float, int)) else None,
    )
    max_side = int(frame["max_side"])
    scale_filter = (
        f"fps={fps:.8f},"
        f"scale='if(gt(iw,ih),min({max_side},iw),-2)':"
        f"'if(gt(iw,ih),-2,min({max_side},ih))'"
    )
    output_pattern = frames_dir / "frame_%06d.jpg"
    command = [
        resolve_command(str(frame["ffmpeg"]), "ffmpeg"),
        "-hide_banner",
        "-loglevel",
        "error",
        "-y",
    ]
    start_seconds = float(analysis_range["start_seconds"])
    if start_seconds > 0:
        command.extend(["-ss", f"{start_seconds:.3f}"])
    command.extend(["-i", str(video_path)])
    if not analysis_range["is_full_video"] and isinstance(range_duration, (float, int)):
        command.extend(["-t", f"{float(range_duration):.3f}"])
    command.extend(
        [
            "-vf",
            scale_filter,
            "-q:v",
            str(frame["jpeg_quality"]),
            str(output_pattern),
        ]
    )
    result = run_command(command, timeout=int(frame["timeout_seconds"]))
    if result.returncode != 0:
        raise VideoError(
            "ffmpeg frame extraction failed",
            {"stderr": result.stderr.strip()[-3000:], "video_path": str(video_path)},
        )

    frame_paths = sorted(frames_dir.glob("frame_*.jpg"))
    max_frames = int(frame["max_frames"])
    if max_frames and len(frame_paths) > max_frames:
        for extra in frame_paths[max_frames:]:
            extra.unlink(missing_ok=True)
        frame_paths = frame_paths[:max_frames]
    if not frame_paths:
        raise VideoError("frame extraction produced no images", {"video_path": str(video_path)})

    frames = [
        {
            "index": index + 1,
            "timestamp_seconds": round(
                start_seconds + (index / fps if fps else float(index)),
                3,
            ),
            "path": str(path),
            "mime_type": "image/jpeg",
            "size_bytes": path.stat().st_size,
            "reason": "uniform-sample",
        }
        for index, path in enumerate(frame_paths)
    ]
    requested_fps = 1.0 / float(frame["interval_seconds"])
    return {
        "output_dir": str(frames_dir),
        "requested_interval_seconds": float(frame["interval_seconds"]),
        "effective_interval_seconds": round(1.0 / fps, 6) if fps else None,
        "effective_fps": round(fps, 6),
        "max_frames": max_frames,
        "frame_count": len(frames),
        "max_side": max_side,
        "analysis_range": analysis_range,
        "frames": frames,
        "sampling_note": (
            "frame interval was increased to respect max frame cap" if fps < requested_fps else ""
        ),
    }


def detect_js_runtimes() -> list[dict[str, str]]:
    runtimes: list[dict[str, str]] = []
    for name in ("deno", "node", "bun", "qjs"):
        path = shutil.which(name)
        if path:
            yt_name = "quickjs" if name == "qjs" else name
            runtimes.append({"name": yt_name, "path": path})
    return runtimes


def js_runtime_args(runtimes: list[dict[str, str]]) -> list[str]:
    if not runtimes:
        return []
    args = ["--no-js-runtimes"]
    for runtime in runtimes:
        args.extend(["--js-runtimes", f"{runtime['name']}:{runtime['path']}"])
    return args


def browser_profile_exists(browser: str) -> bool:
    home = Path.home()
    if sys.platform == "darwin":
        paths = {
            "chrome": (Path("/Applications/Google Chrome.app"), home / "Library/Application Support/Google/Chrome"),
            "firefox": (Path("/Applications/Firefox.app"), home / "Library/Application Support/Firefox/Profiles"),
            "edge": (Path("/Applications/Microsoft Edge.app"), home / "Library/Application Support/Microsoft Edge"),
            "brave": (Path("/Applications/Brave Browser.app"), home / "Library/Application Support/BraveSoftware/Brave-Browser"),
            "chromium": (Path("/Applications/Chromium.app"), home / "Library/Application Support/Chromium"),
            "safari": (Path("/Applications/Safari.app"), home / "Library/Cookies"),
            "vivaldi": (Path("/Applications/Vivaldi.app"), home / "Library/Application Support/Vivaldi"),
            "opera": (Path("/Applications/Opera.app"), home / "Library/Application Support/com.operasoftware.Opera"),
        }
        return any(path.exists() for path in paths.get(browser, ()))
    if sys.platform.startswith("linux"):
        paths = {
            "chrome": (home / ".config/google-chrome",),
            "firefox": (home / ".mozilla/firefox",),
            "edge": (home / ".config/microsoft-edge",),
            "brave": (home / ".config/BraveSoftware/Brave-Browser",),
            "chromium": (home / ".config/chromium",),
            "vivaldi": (home / ".config/vivaldi",),
            "opera": (home / ".config/opera",),
        }
        return any(path.exists() for path in paths.get(browser, ()))
    if os.name == "nt":
        local = Path(os.environ.get("LOCALAPPDATA", ""))
        roaming = Path(os.environ.get("APPDATA", ""))
        paths = {
            "chrome": (local / "Google/Chrome/User Data",),
            "firefox": (roaming / "Mozilla/Firefox/Profiles",),
            "edge": (local / "Microsoft/Edge/User Data",),
            "brave": (local / "BraveSoftware/Brave-Browser/User Data",),
            "chromium": (local / "Chromium/User Data",),
        }
        return any(path.exists() for path in paths.get(browser, ()))
    return False


def browser_candidates(setting: str) -> list[str | None]:
    normalized = setting.strip().lower()
    if normalized in {"", "none", "off", "false"}:
        return [None]
    if normalized != "auto":
        return [setting, None]
    detected = [browser for browser in BROWSER_PRIORITY if browser_profile_exists(browser)]
    return [*detected, None]


def host_matches_no_proxy(host: str, no_proxy: str) -> bool:
    host = host.strip("[]").lower().rstrip(".")
    for raw in no_proxy.split(","):
        token = raw.strip().lower()
        if not token:
            continue
        if token == "*":
            return True
        token = token.split(":", 1)[0].lstrip("*.").rstrip(".")
        if host == token or host.endswith("." + token):
            return True
    return False


def macos_system_proxy() -> tuple[str | None, str | None]:
    if sys.platform != "darwin" or not Path("/usr/sbin/scutil").exists():
        return None, None
    result = run_command(["/usr/sbin/scutil", "--proxy"], timeout=5)
    if result.returncode != 0:
        return None, None
    values: dict[str, str] = {}
    for line in result.stdout.splitlines():
        match = re.match(r"\s*([A-Za-z]+)\s*:\s*(.+?)\s*$", line)
        if match:
            values[match.group(1)] = match.group(2)
    choices = (
        ("HTTPSEnable", "HTTPSProxy", "HTTPSPort", "http"),
        ("SOCKSEnable", "SOCKSProxy", "SOCKSPort", "socks5"),
        ("HTTPEnable", "HTTPProxy", "HTTPPort", "http"),
    )
    for enabled_key, host_key, port_key, scheme in choices:
        if values.get(enabled_key) == "1" and values.get(host_key) and values.get(port_key):
            return f"{scheme}://{values[host_key]}:{values[port_key]}", "macos-system"
    return None, None


def detect_proxy(url: str, setting: str) -> dict[str, Any]:
    normalized = setting.strip()
    host = urlparse(url).hostname or ""
    if normalized.lower() in {"none", "off", "false", "direct"}:
        return {"argument": "", "mode": "direct", "source": "config"}
    if normalized.lower() not in {"", "auto"}:
        return {"argument": normalized, "mode": "proxy", "source": "config"}

    no_proxy = os.environ.get("NO_PROXY") or os.environ.get("no_proxy") or ""
    if host and host_matches_no_proxy(host, no_proxy):
        return {"argument": "", "mode": "direct", "source": "no_proxy"}
    for key in PROXY_ENV_ORDER:
        value = os.environ.get(key)
        if value:
            return {"argument": value, "mode": "proxy", "source": key}
    system_proxy, source = macos_system_proxy()
    if system_proxy:
        return {"argument": system_proxy, "mode": "proxy", "source": source}
    return {"argument": None, "mode": "direct", "source": "none"}


def redacted_proxy(proxy: dict[str, Any]) -> dict[str, Any]:
    value = proxy.get("argument")
    payload = {"mode": proxy.get("mode"), "source": proxy.get("source")}
    if isinstance(value, str) and value:
        parsed = urlparse(value)
        payload["endpoint"] = f"{parsed.scheme}://{parsed.hostname or ''}{':' + str(parsed.port) if parsed.port else ''}"
    return payload


def yt_dlp_common_args(
    yt_dlp: str,
    ffmpeg: str,
    config: dict[str, Any],
    runtimes: list[dict[str, str]],
    proxy: dict[str, Any],
    browser: str | None,
) -> list[str]:
    args = [
        yt_dlp,
        "--ignore-config",
        "--no-playlist",
        "--socket-timeout",
        str(config["download"]["socket_timeout_seconds"]),
        "--ffmpeg-location",
        ffmpeg,
    ]
    args.extend(js_runtime_args(runtimes))
    if proxy.get("argument") is not None:
        args.extend(["--proxy", str(proxy["argument"])])
    if browser:
        args.extend(["--cookies-from-browser", browser])
    return args


def concise_yt_error(result: subprocess.CompletedProcess[str]) -> str:
    text = (result.stderr or result.stdout).strip()
    return text[-3000:] if text else f"yt-dlp exited with status {result.returncode}"


def fetch_url_info(
    url: str,
    yt_dlp: str,
    ffmpeg: str,
    config: dict[str, Any],
    runtimes: list[dict[str, str]],
    proxy: dict[str, Any],
    browsers: list[str | None],
) -> tuple[dict[str, Any], str | None]:
    failures: list[dict[str, str]] = []
    timeout = int(config["download"]["timeout_seconds"])
    for browser in browsers:
        command = yt_dlp_common_args(yt_dlp, ffmpeg, config, runtimes, proxy, browser)
        command.extend(["--dump-single-json", "--skip-download", "--no-warnings", "--", url])
        result = run_command(command, timeout=timeout)
        if result.returncode == 0:
            try:
                info = json.loads(result.stdout)
            except json.JSONDecodeError:
                failures.append({"browser": browser or "none", "error": "yt-dlp returned invalid metadata JSON"})
                continue
            if isinstance(info, dict):
                return info, browser
        failures.append({"browser": browser or "none", "error": concise_yt_error(result)})
    raise VideoError("yt-dlp could not read URL metadata", {"attempts": failures})


def _subtitle_languages(value: Any) -> list[str]:
    if not isinstance(value, dict):
        return []
    return sorted(
        language
        for language, formats in value.items()
        if language != "live_chat" and isinstance(formats, list) and formats
    )


def _is_english(language: str) -> bool:
    normalized = language.lower().replace("_", "-")
    return normalized == "en" or normalized.startswith("en-")


def _prefer_language(languages: list[str], source_language: str) -> str | None:
    if not languages:
        return None
    normalized_source = source_language.lower().replace("_", "-")
    if normalized_source:
        for language in languages:
            normalized = language.lower().replace("_", "-")
            if normalized == normalized_source or normalized.startswith(normalized_source + "-"):
                return language
    return languages[0]


def select_subtitle(info: dict[str, Any]) -> dict[str, str] | None:
    manual = _subtitle_languages(info.get("subtitles"))
    automatic = _subtitle_languages(info.get("automatic_captions"))
    for language in manual:
        if _is_english(language):
            return {"language": language, "kind": "manual"}
    for language in automatic:
        if _is_english(language):
            return {"language": language, "kind": "automatic"}
    source_language = str(info.get("language") or "")
    language = _prefer_language(manual, source_language)
    if language:
        return {"language": language, "kind": "manual"}
    language = _prefer_language(automatic, source_language)
    if language:
        return {"language": language, "kind": "automatic"}
    return None


def download_url(
    url: str,
    download_dir: Path,
    subtitle: dict[str, str] | None,
    browser: str | None,
    yt_dlp: str,
    ffmpeg: str,
    config: dict[str, Any],
    runtimes: list[dict[str, str]],
    proxy: dict[str, Any],
) -> tuple[Path, Path | None]:
    if download_dir.exists():
        shutil.rmtree(download_dir)
    download_dir.mkdir(parents=True, exist_ok=True)
    command = yt_dlp_common_args(yt_dlp, ffmpeg, config, runtimes, proxy, browser)
    command.extend(
        [
            "--format",
            str(config["download"]["format_selector"]),
            "--merge-output-format",
            "mp4/mkv",
            "--write-info-json",
            "--output",
            str(download_dir / "video.%(ext)s"),
        ]
    )
    if subtitle:
        command.append("--write-subs" if subtitle["kind"] == "manual" else "--write-auto-subs")
        command.extend(["--sub-langs", subtitle["language"], "--sub-format", "vtt", "--convert-subs", "vtt"])
    command.extend(["--", url])
    result = run_command(command, timeout=int(config["download"]["timeout_seconds"]))
    artifacts = [
        path
        for path in download_dir.glob("video.*")
        if path.is_file()
        and path.suffix.lower() not in {".json", ".vtt", ".srt", ".ass", ".lrc", ".part", ".ytdl"}
    ]
    known_videos = [path for path in artifacts if path.suffix.lower() in VIDEO_EXTENSIONS]
    candidates = sorted(
        known_videos or artifacts,
        key=lambda path: path.stat().st_size,
        reverse=True,
    )
    if not candidates:
        details = {"download_dir": str(download_dir)}
        if result.returncode != 0:
            details["stderr"] = concise_yt_error(result)
        raise VideoError("yt-dlp did not produce a video file", details)
    subtitle_paths = sorted(download_dir.glob("video*.vtt"))
    return candidates[0], subtitle_paths[0] if subtitle_paths else None


def _vtt_seconds(value: str) -> float:
    parts = value.replace(",", ".").split(":")
    seconds = float(parts[-1])
    minutes = int(parts[-2]) if len(parts) >= 2 else 0
    hours = int(parts[-3]) if len(parts) >= 3 else 0
    return hours * 3600 + minutes * 60 + seconds


def parse_vtt(path: Path) -> list[dict[str, Any]]:
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    segments: list[dict[str, Any]] = []
    index = 0
    while index < len(lines):
        match = VTT_TIMESTAMP_RE.search(lines[index])
        if not match:
            index += 1
            continue
        start = _vtt_seconds(match.group("start"))
        end = _vtt_seconds(match.group("end"))
        index += 1
        text_lines: list[str] = []
        while index < len(lines) and lines[index].strip():
            cleaned = html.unescape(VTT_TAG_RE.sub("", lines[index])).strip()
            if cleaned:
                text_lines.append(cleaned)
            index += 1
        text = " ".join(text_lines).strip()
        if text:
            segment = {"start_seconds": round(start, 3), "end_seconds": round(end, 3), "text": text}
            if segments and segments[-1]["text"] == text:
                segments[-1]["end_seconds"] = segment["end_seconds"]
            elif segments and text.startswith(segments[-1]["text"] + " "):
                segments[-1]["text"] = text
                segments[-1]["end_seconds"] = segment["end_seconds"]
            else:
                segments.append(segment)
        index += 1
    return segments


def transcript_payload(
    selected: dict[str, str] | None,
    subtitle_path: Path | None,
) -> dict[str, Any]:
    if not selected:
        return {"available": False, "reason": "the source exposed no downloadable subtitles"}
    if not subtitle_path:
        return {
            "available": False,
            "language": selected["language"],
            "kind": selected["kind"],
            "reason": "subtitle was advertised but yt-dlp did not produce a VTT file",
        }
    try:
        segments = parse_vtt(subtitle_path)
    except OSError as exc:
        return {
            "available": False,
            "language": selected["language"],
            "kind": selected["kind"],
            "path": str(subtitle_path),
            "reason": f"could not read subtitle: {exc}",
        }
    return {
        "available": bool(segments),
        "language": selected["language"],
        "kind": selected["kind"],
        "path": str(subtitle_path),
        "segment_count": len(segments),
        "segments": segments,
        "reason": "" if segments else "subtitle file contained no readable cues",
    }


def filter_transcript_to_range(
    transcript: dict[str, Any],
    analysis_range: dict[str, Any],
) -> dict[str, Any]:
    segments = transcript.get("segments")
    if not isinstance(segments, list):
        return transcript
    start = float(analysis_range["start_seconds"])
    raw_end = analysis_range.get("end_seconds")
    end = float(raw_end) if isinstance(raw_end, (float, int)) else None
    filtered = [
        segment
        for segment in segments
        if isinstance(segment, dict)
        and float(segment.get("end_seconds", 0)) >= start
        and (end is None or float(segment.get("start_seconds", 0)) <= end)
    ]
    result = dict(transcript)
    result["segments"] = filtered
    result["segment_count"] = len(filtered)
    result["available"] = bool(filtered)
    result["filtered_to_range"] = not bool(analysis_range["is_full_video"])
    if not filtered:
        result["reason"] = "subtitle contained no cues in the analysis range"
    return result


def yt_dlp_version(yt_dlp: str) -> str | None:
    result = run_command([yt_dlp, "--version"], timeout=10)
    return result.stdout.strip() if result.returncode == 0 else None


def source_metadata(info: dict[str, Any], url: str, video_path: Path) -> dict[str, Any]:
    return {
        "type": "url",
        "input": url,
        "local_path": str(video_path),
        "downloaded": True,
        "title": info.get("title"),
        "uploader": info.get("uploader") or info.get("channel"),
        "webpage_url": info.get("webpage_url") or info.get("original_url") or url,
        "extractor": info.get("extractor_key") or info.get("extractor"),
    }


def agent_instruction(has_transcript: bool) -> str:
    transcript_text = (
        "Align transcript.segments with frame timestamps and distinguish spoken evidence from visible evidence. "
        if has_transcript
        else "No subtitle transcript is available, so do not claim to know what was spoken. "
    )
    return (
        "Read every image in frame_manifest.frames, preferably in parallel. Frames are chronological samples, "
        "not continuous playback. Answer the user's question directly and cite timestamps for important moments. "
        + transcript_text
        + "Describe scenes, people, objects, on-screen text, UI, actions, and transitions only when supported by "
        "the sampled evidence. State uncertainty and sampling gaps instead of inventing unseen motion or audio."
    )


def fail(error: VideoError) -> int:
    print(
        json.dumps(
            {"ok": False, "error": error.message, "details": error.details},
            ensure_ascii=False,
            indent=2,
        ),
        file=sys.stderr,
    )
    return 1


def main() -> int:
    args = parse_args()
    try:
        config = load_config(args)
        if args.migrate_config:
            config_path = Path(args.config).expanduser()
            write_migrated_config(config_path, config)
            print(json.dumps({"ok": True, "config_path": str(config_path)}, indent=2))
            return 0
        if args.print_config:
            print(json.dumps({"ok": True, "config": config}, ensure_ascii=False, indent=2))
            return 0
        if not args.source:
            raise VideoError("source is required unless --print-config or --migrate-config is used")

        source = args.source
        run_dir = create_run_dir(source, config, args.output_dir)
        ffmpeg = resolve_command(str(config["frame"]["ffmpeg"]), "ffmpeg")
        ffprobe = resolve_command(str(config["frame"]["ffprobe"]), "ffprobe")
        transcript: dict[str, Any] = {"available": False, "reason": "local video input has no downloaded subtitle"}
        acquisition: dict[str, Any] = {"method": "local-file"}

        if is_url(source):
            yt_dlp = resolve_command(str(config["download"]["yt_dlp"]), "yt-dlp")
            runtimes = detect_js_runtimes()
            proxy = detect_proxy(source, str(config["download"]["proxy"]))
            browsers = browser_candidates(str(config["download"]["cookies_from_browser"]))
            info, used_browser = fetch_url_info(
                source,
                yt_dlp,
                ffmpeg,
                config,
                runtimes,
                proxy,
                browsers,
            )
            selected_subtitle = select_subtitle(info)
            video_path, subtitle_path = download_url(
                source,
                run_dir / "download",
                selected_subtitle,
                used_browser,
                yt_dlp,
                ffmpeg,
                config,
                runtimes,
                proxy,
            )
            transcript = transcript_payload(selected_subtitle, subtitle_path)
            source_info = source_metadata(info, source, video_path)
            acquisition = {
                "method": "yt-dlp",
                "yt_dlp_version": yt_dlp_version(yt_dlp),
                "quality_preference": "720p > 540p-class > 1080p > 2K/4K > <=360p > compatible fallback",
                "js_runtimes": [runtime["name"] for runtime in runtimes],
                "cookies_from_browser": used_browser,
                "proxy": redacted_proxy(proxy),
            }
        else:
            video_path = Path(source).expanduser().resolve()
            source_info = {
                "type": "local-file",
                "input": source,
                "local_path": str(video_path),
                "downloaded": False,
            }

        metadata = validate_local_video(video_path, ffprobe)
        analysis_range = resolve_analysis_range(metadata, args.start, args.end)
        transcript = filter_transcript_to_range(transcript, analysis_range)
        frame_manifest = extract_frames(
            video_path,
            metadata,
            config,
            run_dir / "frames",
            analysis_range,
        )
        payload = {
            "ok": True,
            "analysis_mode": "frames",
            "requires_agent_frame_analysis": True,
            "question": args.question,
            "source": source_info,
            "acquisition": acquisition,
            "video_metadata": metadata,
            "analysis_range": analysis_range,
            "frame_manifest": frame_manifest,
            "transcript": transcript,
            "run_dir": str(run_dir),
            "agent_instruction": agent_instruction(bool(transcript.get("available"))),
            "cleanup_instruction": "Delete run_dir after answering when no follow-up analysis is expected.",
        }
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return 0
    except VideoError as exc:
        return fail(exc)


if __name__ == "__main__":
    raise SystemExit(main())

# video-understanding

Agent skill that turns a local video file or a yt-dlp-supported URL into a
timestamped local evidence package. Codex or Claude Code reads the extracted
frames and optional URL subtitles directly; the skill does not call a remote
vision model or upload the video to object storage.

## Behavior

- Local files are inspected with ffprobe and sampled with ffmpeg.
- URLs are downloaded with yt-dlp.
- Download quality preference is 720p, 540p-class, 1080p, 2K/4K, 360p or
  lower, then a final compatibility fallback.
- Deno and Node are enabled when detected. Deno has the higher yt-dlp runtime
  priority.
- Browser cookies are tried from detected local browser profiles, then the
  request is retried without browser cookies.
- English subtitles are preferred. If unavailable, a manual subtitle in the
  source language is preferred before another available subtitle.
- Proxy auto-detection honors NO_PROXY, standard proxy environment variables,
  and enabled static macOS system proxy settings.

The script prints JSON containing source metadata, video metadata, timestamped
frame paths, subtitle segments, acquisition decisions, and instructions for the
agent.

## Install

~~~bash
make install
~~~

This installs runtime files plus the Codex and Claude Code skill copies.
install-runtime creates a private Python virtual environment and installs:

~~~text
yt-dlp[default,curl-cffi]
~~~

The default extra includes yt-dlp-ejs; curl-cffi supports sites that require
browser impersonation. ffmpeg and ffprobe remain external system dependencies.

Other install targets:

~~~bash
make install-codex
make install-claude
make install-runtime
~~~

Installed locations:

- Codex: ~/.codex/skills/video-understanding
- Claude Code: ~/.claude/skills/video-understanding
- Runtime: ~/.vibelab-tools/agent-skills/video-understanding

When an old provider-based config exists, installation rewrites it to the new
schema, preserving customized frame settings and removing obsolete provider,
API key, and object-storage fields. The former default cap of 240 frames
migrates to the uncapped default.

## Usage

Local file:

~~~bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  /path/to/video.mp4 \
  --question "What happens in this recording?"
~~~

URL:

~~~bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  'https://www.youtube.com/watch?v=example' \
  --question "Summarize the visible and spoken content."
~~~

Useful one-off overrides:

~~~bash
scripts/analyze-video video.mp4 --start 01:30 --end 02:00
scripts/analyze-video video.mp4 --start 01:30
scripts/analyze-video video.mp4 --end 02:00
scripts/analyze-video video.mp4 --max-frames 40
scripts/analyze-video "$URL" --cookies-from-browser firefox
scripts/analyze-video "$URL" --cookies-from-browser none
scripts/analyze-video "$URL" --proxy socks5://127.0.0.1:1080
scripts/analyze-video "$URL" --proxy none
scripts/analyze-video --print-config
~~~

## Configuration

Runtime configuration:

~~~text
~/.vibelab-tools/agent-skills/video-understanding/config.json
~~~

Frame fields:

| Field | Meaning |
| --- | --- |
| interval_seconds | Requested interval between frames; the default is one second. |
| max_frames | Optional maximum frame count. The default 0 means no cap. |
| max_side | Maximum width or height of each JPEG. |
| jpeg_quality | ffmpeg JPEG quality from 1 to 31; lower is higher quality. |
| output_root | Parent directory for generated runs; empty uses the runtime default. |
| ffmpeg / ffprobe | Executable path or command name. |
| timeout_seconds | Frame extraction timeout. |

Download fields:

| Field | Meaning |
| --- | --- |
| yt_dlp | Executable path or auto; the runtime venv copy is preferred. |
| format_selector | Ordered yt-dlp format fallback expression. |
| cookies_from_browser | Browser name, auto, or none. |
| proxy | Proxy URL, auto, or none. |
| socket_timeout_seconds | yt-dlp network socket timeout. |
| timeout_seconds | Overall timeout for each yt-dlp command. |

--start and --end accept seconds, MM:SS, or HH:MM:SS. With only --start, the
range continues to the end of the video; with only --end, it begins at the
start. The selected range applies to frames and downloaded subtitles. Output
timestamps remain relative to the original video.

## Validation

~~~bash
make validate
~~~

Validation checks the skill metadata, compiles the Python entry point, runs the
unit tests, and verifies the wrapper help command.

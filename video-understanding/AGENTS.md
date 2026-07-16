# Video Understanding Skill Design

This skill prepares local visual and subtitle evidence for an agent. It does
not call a remote vision model, upload videos, or analyze frames inside the
script.

## Invariants

- Accept a local video path or an HTTP(S) URL supported by yt-dlp.
- Keep stdout as one valid JSON document.
- Keep frame paths timestamped and chronological.
- Sample at frame.interval_seconds with no total frame cap by default.
- Apply optional start/end bounds to frames and subtitles while preserving
  timestamps relative to the original video.
- Keep downloaded videos, subtitles, and frames under one generated run_dir.
- Never print cookies, browser data, or proxy credentials.
- Treat local files as visual-only unless a future requirement explicitly adds
  local transcription.
- Preserve the quality preference: 720p, 540p-class, 1080p, 2K/4K, low
  resolution, then compatibility fallback.
- Prefer English subtitles before other available languages.
- Preserve direct fallback when browser cookie extraction fails.

## Runtime

Use scripts/analyze-video as the public entry point. Runtime configuration and
generated evidence live under:

~~~text
~/.vibelab-tools/agent-skills/video-understanding/
~~~

make install-runtime owns the runtime virtual environment and installs
yt-dlp[default,curl-cffi]. ffmpeg and ffprobe remain system dependencies.

The config loader accepts only fields declared in DEFAULT_CONFIG. This is
intentional: config migration must remove obsolete provider keys and secrets
from the previous remote-analysis implementation.

## Maintenance

- Keep config.example.json, SKILL.md, README.md, and script defaults aligned.
- Add behavior tests for subtitle selection, proxy decisions, browser/runtime
  argument construction, and output-contract changes.
- Do not reintroduce provider, upload, or API-key configuration without an
  explicit product decision.

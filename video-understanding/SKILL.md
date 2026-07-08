---
name: video-understanding
description: Analyze local video files by extracting timestamped frames for agent-side inspection or by sending sampled frames to a configured multimodal provider. Use when Codex or Claude Code needs to read, summarize, inspect, debug, or answer questions about a local video path such as .mp4, .mov, .mkv, .webm, .avi, .m4v, .mpg, or .wmv. Supports frame fallback mode with ffmpeg, OpenAI-compatible vision chat endpoints, and Gemini generateContent. Do not trigger for general video platform search/download work, video editing, image generation, or vague mentions of video without a local file path or explicit request to analyze video content.
---

# Video Understanding

## Workflow

Use `scripts/analyze-video` for local video analysis. The script always
validates the path, collects metadata when `ffprobe` is available, and samples
frames with `ffmpeg`.

Do not call `python3 scripts/analyze_video.py` directly unless the user has
explicitly selected a Python runtime. The `scripts/analyze-video` wrapper
chooses a healthy Python 3.10+ interpreter and skips broken default shims.

Run from any working directory:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  "/absolute/or/relative/video.mp4" \
  --question "Summarize the video."
```

For Claude Code, use the same script path under `~/.claude/skills` when that is
the installed surface:

```bash
~/.claude/skills/video-understanding/scripts/analyze-video \
  "/absolute/or/relative/video.mp4" \
  --question "Summarize the video."
```

## Configuration

Read configuration from:

```text
~/.vibe-coding-skill/video-understanding/config.json
```

Override it per command with `--config`. Use
`~/.vibe-coding-skill/video-understanding/config.example.json` as the template.

Important fields:

- `mode`: `auto`, `frames`, or `multimodal`.
- `provider`: `openai-compatible` or `gemini`.
- `frame.interval_seconds`: seconds between sampled frames. Default: `1.0`.
- `frame.max_frames`: cap extracted frames. Default: `240`.
- `multimodal.fallback_to_frames`: return sampled frames for agent-side analysis
  if the provider request fails.
- Provider sections contain endpoint, API key source, model, temperature, and
  token limits.

`auto` uses multimodal mode only when the selected provider has both a model and
an API key available. Otherwise it uses frame mode.

## Frame Mode

Frame mode is the privacy-preserving fallback. It does not call a remote model.
It extracts timestamped JPEG frames and prints JSON with:

- `requires_agent_frame_analysis: true`
- `frame_manifest.output_dir`
- `frame_manifest.frames[]`

When this appears, inspect the returned frames directly and answer from visible
evidence. Use timestamps from `frame_manifest.frames` for timeline references.

Use frame mode explicitly:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  video.mp4 \
  --mode frames \
  --frame-interval-seconds 1
```

## Multimodal Mode

Multimodal mode sends sampled frames to the configured provider, then combines
batch observations into a final answer. It currently supports:

- OpenAI-compatible Chat Completions vision endpoints.
- Gemini `generateContent` endpoints.

Use multimodal mode explicitly:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  video.mp4 \
  --mode multimodal \
  --provider openai-compatible
```

If multimodal mode fails and `multimodal.fallback_to_frames` is `true`, the
script returns frame-mode JSON instead of failing hard.

## Privacy Boundary

Do not send video-derived frames to a remote provider unless the user has asked
for video analysis and the configured mode allows cloud processing. Use
`--mode frames` for sensitive videos or when provider credentials are missing.

## Output Handling

The script prints JSON.

- If `analysis_mode` is `multimodal`, use `answer` as the user-facing response.
- If `analysis_mode` is `frames`, inspect the images in
  `frame_manifest.frames` and produce the response yourself.
- Mention sampled-frame limitations when they affect confidence.
- Do not print API keys or provider secrets.

If the script fails:

- Missing `ffmpeg`: ask the user to install ffmpeg or set `frame.ffmpeg`.
- Missing provider key/model: use frame mode or ask the user to configure the
  provider.
- Provider request failure: if fallback was disabled, report the provider,
  endpoint, status, and concise error details.

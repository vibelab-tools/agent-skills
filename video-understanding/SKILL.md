---
name: video-understanding
description: Analyze local video files by extracting timestamped frames for agent-side inspection, sending sampled frames to a vision provider, or sending the native video/video URL to a provider that supports video input. Use when Codex or Claude Code needs to read, summarize, inspect, debug, or answer questions about a local video path such as .mp4, .mov, .mkv, .webm, .avi, .m4v, .mpg, or .wmv. Supports frame fallback mode with ffmpeg, OpenAI-compatible vision/video endpoints, Gemini generateContent, and optional OSS/S3-compatible upload for provider video URLs. Do not trigger for general video platform search/download work, video editing, image generation, or vague mentions of video without a local file path or explicit request to analyze video content.
---

# Video Understanding

## Workflow

Use `scripts/analyze-video` for local video analysis. The script always
validates the path and collects metadata when `ffprobe` is available. It samples
frames only in `frames` and `vision-frames` modes. `video-native` sends the
video or a public video URL to the selected provider without local frame
sampling.

Use the `scripts/analyze-video` wrapper by default. It chooses a healthy Python
3.10+ interpreter and skips broken default shims. Call
`python3 scripts/analyze_video.py` directly only when a specific interpreter is
required.

Run from any working directory:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  "/absolute/or/relative/video.mp4" \
  --question "Summarize the video."
```

Do not pass `--mode` or `--provider` during normal skill use. The runtime
`config.json` is the source of truth for mode and provider selection. Only pass
those flags when the user explicitly asks for a one-off override in the current
command.

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
~/.vibelab-tools/agent-skills/video-understanding/config.json
```

Override the config path per command with `--config`. Use
`~/.vibelab-tools/agent-skills/video-understanding/config.example.json` as the template.

Important fields:

- `mode`: `auto`, `frames`, `vision-frames`, or `video-native`.
- `provider`: `openai-compatible` or `gemini`.
- `frame.interval_seconds`: seconds between sampled frames. Default: `1.0`.
- `frame.max_frames`: cap extracted frames. Default: `240`.
- `vision_frames.fallback_to_frames`: return sampled frames for agent-side
  analysis if the vision-frame provider request fails.
- `video_native.fallback_to_vision_frames`: fall back to sampled-frame provider
  analysis if native video analysis fails.
- Provider sections contain endpoint, API key source, model, temperature, and
  token limits.

`auto` prefers `video-native` when the selected provider has native video
enabled plus a model and API key. Otherwise it uses `vision-frames` when the
provider is configured, then falls back to `frames`.

## Frame Mode

Frame mode is the privacy-preserving fallback. It does not call a remote model.
It extracts timestamped JPEG frames and prints JSON with:

- `requires_agent_frame_analysis: true`
- `frame_manifest.output_dir`
- `frame_manifest.frames[]`

When this appears, inspect the returned frames directly and answer from visible
evidence. Use timestamps from `frame_manifest.frames` for timeline references.

To use frame mode, set `mode` to `frames` in the runtime config. Do not add
`--mode frames` to generated commands unless the user explicitly asks for a
one-off override.

## Vision-Frames Mode

Vision-frames mode sends sampled frames to the configured provider, then
combines batch observations into a final answer. It currently supports:

- OpenAI-compatible Chat Completions vision endpoints.
- Gemini `generateContent` endpoints.

To use this mode, set `mode` to `vision-frames` and `provider` to the desired
provider in the runtime config. Do not add `--mode vision-frames` or
`--provider` to generated commands unless the user explicitly asks for a one-off
override. The legacy `multimodal` mode value is accepted as an alias for
`vision-frames`.

If vision-frames mode fails and `vision_frames.fallback_to_frames` is `true`, the
script returns frame-mode JSON instead of failing hard.

## Video-Native Mode

Video-native mode sends the native video input to a provider that supports video
parts. It does not extract frames locally before the provider call.

- `gemini` uses Gemini Files API upload and then `fileData`.
- `openai-compatible` uses an OpenAI-compatible `video_url` message part. Small
  local files can be sent as Base64 data URLs, but larger videos should be
  uploaded through `video_upload` to a public OSS/S3-compatible URL first.

To use this mode, set `mode` to `video-native`. If it fails and
`video_native.fallback_to_vision_frames` is `true`, the script falls back to
sampled-frame provider analysis.

## Privacy Boundary

Do not send video-derived frames, native videos, or uploaded video URLs to a
remote provider unless the user has asked for video analysis and the configured
mode allows cloud processing. For sensitive videos or when provider credentials
are missing, use a runtime config with `mode` set to `frames`.

## Output Handling

The script prints JSON.

- If `analysis_mode` is `vision-frames` or `video-native`, use `answer` as the
  user-facing response.
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

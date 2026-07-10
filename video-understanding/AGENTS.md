# Video Understanding Skill Design

This directory defines the `video-understanding` agent skill. The skill is
designed for local video files and gives Codex, Claude Code, and compatible
agents a consistent way to inspect video content without requiring native video
understanding in the agent runtime.

## Design Goals

- Keep frame extraction local and deterministic when a frame-based mode is used.
- Make frame-only analysis the safe fallback for every environment.
- Support provider-backed vision-frame and native-video analysis only through
  explicit runtime configuration.
- Keep runtime configuration and generated frames outside the repository.
- Avoid storing provider secrets in tracked files.
- Use the same skill instructions for Codex and Claude Code.

## Runtime Layout

Installed skill copies live in each agent's native skill directory:

- Codex: `~/.codex/skills/video-understanding`
- Claude Code: `~/.claude/skills/video-understanding`

Shared runtime files live under:

```text
~/.vibelab-tools/agent-skills/video-understanding/
```

The runtime directory contains:

- `config.json`: user-editable runtime configuration.
- `config.example.json`: the installed template.
- `frames/`: default parent directory for extracted frame runs.

Do not commit local runtime configuration, generated frames, API keys, or user
video artifacts to this repository.

## Execution Modes

The public entry point is:

```bash
scripts/analyze-video
```

Use the wrapper instead of invoking `python3 scripts/analyze_video.py` directly.
The wrapper selects a healthy Python 3.10+ interpreter and avoids broken default
Python shims.

The script supports four modes:

- `frames`: Extract timestamped JPEG frames with ffmpeg and return a frame
  manifest. The agent inspects the local images directly.
- `vision-frames`: Extract sampled frames, send them to the selected provider,
  and return the provider's answer.
- `video-native`: Send the native video or a public video URL to a provider that
  supports video input. This mode does not extract frames before provider
  analysis.
- `auto`: Prefer `video-native` when the selected provider has native video
  enabled plus a model and API key. Otherwise use `vision-frames` when the
  provider is configured, then frame mode.

The legacy `multimodal` mode value is accepted as an alias for `vision-frames`.
Frame extraction happens before `vision-frames` analysis, but not before
`video-native`.

## Providers

Current provider IDs:

- `openai-compatible`: OpenAI Chat Completions compatible vision endpoints.
- `gemini`: Gemini `generateContent` endpoints.

Only the provider selected by the top-level `provider` field must be configured.
The unused provider block may remain empty.

For OpenAI-compatible endpoints, store the full Chat Completions URL in
`vision_frames.openai_compatible.endpoint`. For Alibaba Cloud Model Studio /
Bailian, this means a URL like:

```text
https://<workspace-or-service-host>/compatible-mode/v1/chat/completions
```

Set `model` to the provider model ID, such as `qwen3.7-plus` for Bailian.

For OpenAI-compatible native video, the script sends a `video_url` content part.
Large local videos should be uploaded through `video_upload` to a public
OSS/S3-compatible URL before the model call. Small videos may use Base64 data
URLs when the configured `max_inline_bytes` allows it.

For Gemini native video, the script uses Gemini Files API upload, polls for the
file to become active, then calls `generateContent` with `fileData`.

## Configuration Principles

Configuration is layered:

1. Built-in defaults in `scripts/analyze_video.py`.
2. Runtime `config.json`.
3. CLI overrides such as `--mode`, `--provider`, `--frame-interval-seconds`,
   `--max-frames`, and `--frame-output-dir`.

For agent-driven usage, treat runtime `config.json` as the source of truth. Do
not add `--mode` or `--provider` to generated commands unless the user
explicitly asks for a one-off override. This prevents accidental changes between
native-video, vision-frame, frame-only, or provider-specific modes.

Provider keys can be supplied either inline through `api_key` or indirectly
through `api_key_env`. Prefer established provider key names such as
`OPENAI_API_KEY`, `DASHSCOPE_API_KEY`, and `GOOGLE_AI_API_KEY`. If a local
runtime config contains an inline key, restrict it:

```bash
chmod 600 ~/.vibelab-tools/agent-skills/video-understanding/config.json
```

The script's `--print-config` output redacts API keys and authorization-like
values. Keep that behavior intact when changing config handling.

## Privacy Boundary

Frame mode does not call a remote provider. Vision-frames mode sends sampled
video frames to the configured provider. Video-native mode sends the video file,
Base64 data URL, or uploaded public video URL to the configured provider. Treat
all provider-backed modes as cloud processing.

When working with sensitive videos:

- Prefer a runtime config with `mode` set to `frames`.
- Do not enable provider-backed modes without user intent.
- Keep `frame.keep_frames` behavior explicit because extracted frames can
  contain sensitive visual content.
- Do not put default frame output paths into user config; derive them from the
  runtime root unless the user explicitly overrides `frame.output_root`.
- Do not print API keys, provider secrets, or raw request headers in logs.

## Maintenance Notes

- Keep `config.example.json`, `README.md`, and `SKILL.md` aligned with the
  script's defaults.
- Add behavior-driven tests or smoke checks only for behavior changed in the
  current phase.
- Preserve `frames` fallback behavior when changing provider code.
- Keep generated output JSON stable enough for agents to branch on
  `analysis_mode` and `requires_agent_frame_analysis`.
- Avoid adding provider-specific logic to the agent instructions when the same
  behavior can be expressed through runtime config.

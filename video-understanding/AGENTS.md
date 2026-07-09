# Video Understanding Skill Design

This directory defines the `video-understanding` agent skill. The skill is
designed for local video files and gives Codex, Claude Code, and compatible
agents a consistent way to inspect video content without requiring native video
understanding in the agent runtime.

## Design Goals

- Keep frame extraction local and deterministic.
- Make frame-only analysis the safe fallback for every environment.
- Support provider-backed multimodal analysis only through explicit runtime
  configuration.
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

The script supports three modes:

- `frames`: Extract timestamped JPEG frames with ffmpeg and return a frame
  manifest. The agent inspects the local images directly.
- `multimodal`: Extract sampled frames, send them to the selected provider, and
  return the provider's answer.
- `auto`: Use multimodal mode only when the selected provider has both a model
  and an API key. Otherwise, use frame mode.

Frame extraction always happens before multimodal analysis. This keeps a local
evidence trail and lets the script fall back to frame mode if the provider call
fails and `multimodal.fallback_to_frames` is enabled.

## Providers

Current provider IDs:

- `openai-compatible`: OpenAI Chat Completions compatible vision endpoints.
- `gemini`: Gemini `generateContent` endpoints.

Only the provider selected by the top-level `provider` field must be configured.
The unused provider block may remain empty.

For OpenAI-compatible endpoints, store the full Chat Completions URL in
`multimodal.openai_compatible.endpoint`. For Alibaba Cloud Model Studio /
Bailian, this means a URL like:

```text
https://<workspace-or-service-host>/compatible-mode/v1/chat/completions
```

Set `model` to the provider model ID, such as `qwen3.7-plus` for Bailian.

## Configuration Principles

Configuration is layered:

1. Built-in defaults in `scripts/analyze_video.py`.
2. Runtime `config.json`.
3. CLI overrides such as `--mode`, `--provider`, `--frame-interval-seconds`,
   `--max-frames`, and `--frame-output-dir`.

For agent-driven usage, treat runtime `config.json` as the source of truth. Do
not add `--mode` or `--provider` to generated commands unless the user
explicitly asks for a one-off override. This prevents accidental changes from
multimodal mode to frame-only mode, or from one configured provider to another.

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

Frame mode does not call a remote provider. Multimodal mode sends sampled video
frames to the configured provider. Treat this as cloud processing.

When working with sensitive videos:

- Prefer a runtime config with `mode` set to `frames`.
- Do not enable multimodal mode without user intent.
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

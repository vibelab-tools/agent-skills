# video-understanding

Agent skill for analyzing local video files through timestamped frame sampling
or configured multimodal providers.

## Purpose

Use this skill when an agent needs to summarize, inspect, debug, or answer
questions about a local video file. It supports two execution modes:

- `frames`: extract sampled frames with ffmpeg and let Codex or Claude Code
  inspect the frames directly. This is the default fallback mode.
- `multimodal`: send sampled frames to a configured provider and return a model
  answer. Current providers are OpenAI-compatible Chat Completions vision
  endpoints and Gemini `generateContent`.

## Configuration

Runtime configuration lives under:

```text
~/.vibelab-tools/agent-skills/video-understanding/config.json
```

`make install-runtime` copies `config.example.json` into the runtime directory
when no config file exists. Users should edit `config.json` to choose:

- `mode`: `auto`, `frames`, or `multimodal`
- `provider`: `openai-compatible` or `gemini`
- frame interval, frame cap, retention behavior, and ffmpeg paths
- endpoint, API key, model, temperature, and output-token settings

By default, `auto` uses multimodal mode only when a model and API key are
configured; otherwise it extracts frames for agent-side analysis.

### Configuration Reference

Top-level fields:

| Field | Values | Meaning |
| --- | --- | --- |
| `mode` | `auto`, `frames`, `multimodal` | `frames` always extracts local frames. `multimodal` sends sampled frames to the selected provider. `auto` uses multimodal only when the selected provider has both a model and an API key; otherwise it falls back to frame mode. |
| `provider` | `openai-compatible`, `gemini` | Selects which provider block under `multimodal` is used. Only the selected provider must be configured. |
| `clear_proxy_env` | `true`, `false` | Removes ambient proxy environment variables before provider calls. Keep this enabled unless the provider must be reached through the shell proxy. |

Frame sampling fields:

| Field | Meaning |
| --- | --- |
| `frame.interval_seconds` | Seconds between sampled frames. `1.0` means one frame per second. |
| `frame.max_frames` | Maximum extracted frames. `0` means no cap. If the video is longer than the cap allows, the script increases the effective interval. |
| `frame.max_side` | Maximum width or height of each sampled frame after scaling. |
| `frame.jpeg_quality` | ffmpeg JPEG quality from `1` to `31`; lower values mean higher quality/larger files. |
| `frame.output_root` | Optional parent directory for extracted frame runs. Omit it to use the default `frames/` directory under the video-understanding runtime root. |
| `frame.keep_frames` | Keeps extracted frames on disk when `true`. If `false`, multimodal mode removes frames after the provider returns. |
| `frame.ffmpeg` | ffmpeg executable path or command name. |
| `frame.ffprobe` | ffprobe executable path or command name for metadata. |
| `frame.timeout_seconds` | Timeout for local ffmpeg/ffprobe work. |

Multimodal shared fields:

| Field | Meaning |
| --- | --- |
| `multimodal.fallback_to_frames` | If provider analysis fails, return frame-mode JSON instead of failing hard. |
| `multimodal.batch_size` | Number of sampled frames sent per provider request. |
| `multimodal.request_timeout_seconds` | Timeout for each provider HTTP request. |

OpenAI-compatible provider fields:

| Field | Meaning |
| --- | --- |
| `endpoint` | Full Chat Completions URL, not just the base URL. |
| `api_key` | Inline API key. Prefer leaving this empty and using `api_key_env` when possible. |
| `api_key_env` | Environment variable name used when `api_key` is empty. Prefer established provider names such as `DASHSCOPE_API_KEY` or `OPENAI_API_KEY`. |
| `model` | Provider model ID. This must be non-empty for `auto` to enter multimodal mode. |
| `temperature` | Sampling temperature for provider calls. |
| `max_tokens` | Output-token cap. |
| `max_tokens_field` | Field name used in the JSON request. Most OpenAI-compatible Chat Completions endpoints use `max_tokens`. |
| `headers` | Extra HTTP headers merged into the request. Do not put secrets here unless required by the provider. |
| `image_detail` | Optional OpenAI-style image detail hint, such as `low` or `high`. |

For Alibaba Cloud Model Studio / Bailian OpenAI-compatible access, use the
`openai-compatible` provider and set the endpoint to the complete compatible
Chat Completions URL:

```json
{
  "mode": "auto",
  "provider": "openai-compatible",
  "multimodal": {
    "openai_compatible": {
      "endpoint": "https://<workspace-id>.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions",
      "api_key": "",
      "api_key_env": "DASHSCOPE_API_KEY",
      "model": "qwen3.7-plus",
      "temperature": 0.2,
      "max_tokens": 2048,
      "max_tokens_field": "max_tokens",
      "headers": {},
      "image_detail": "low"
    }
  }
}
```

If your Bailian console provides a host such as
`llm-xxxxxxxxxxxxxxxx.cn-beijing.maas.aliyuncs.com`, the endpoint value should be:

```text
https://llm-xxxxxxxxxxxxxxxx.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions
```

Keep real API keys out of this repository. If a local runtime config must store
an inline key, restrict its permissions:

```bash
chmod 600 ~/.vibelab-tools/agent-skills/video-understanding/config.json
```

Gemini provider fields:

| Field | Meaning |
| --- | --- |
| `endpoint` | Gemini `generateContent` endpoint template. It must include `{model}`. |
| `api_key` | Inline Gemini API key. Prefer `api_key_env` when possible. |
| `api_key_env` | Environment variable name used when `api_key` is empty. Prefer `GOOGLE_AI_API_KEY`. |
| `model` | Gemini model ID. This must be non-empty for `auto` to enter multimodal mode when `provider` is `gemini`. |
| `temperature` | Sampling temperature for provider calls. |
| `max_output_tokens` | Output-token cap for Gemini requests. |
| `headers` | Extra HTTP headers merged into the request. |

Print the effective config with secrets redacted:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  /path/to/video.mp4 \
  --print-config
```

## Build

No build step is required.

```bash
make
```

## Install

```bash
make install          # install runtime plus Codex and Claude Code skill copies
make install-codex    # install runtime plus Codex skill copy
make install-claude   # install runtime plus Claude Code skill copy
make install-runtime  # create runtime config directory and check ffmpeg
make uninstall        # remove installed copies and runtime files; preserve config.json
make purge            # run uninstall and remove config.json
```

Installed locations:

- Codex: `~/.codex/skills/video-understanding`
- Claude Code: `~/.claude/skills/video-understanding`
- Runtime: `~/.vibelab-tools/agent-skills/video-understanding`

## Usage

Frame-only analysis:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video video.mp4 --mode frames
```

Multimodal analysis:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  video.mp4 \
  --mode multimodal \
  --provider openai-compatible \
  --question "Summarize this video."
```

The script prints JSON. In `frames` mode, use `frame_manifest.frames` to inspect
the sampled images directly. In `multimodal` mode, use `answer` as the final
response unless the provider failed and frame fallback was returned.

The `scripts/analyze-video` wrapper chooses a suitable Python 3.10+ runtime by
capability. Normal pyenv, asdf, uv, Homebrew, system, or manually installed
Python interpreters are accepted.

Use the wrapper as the public entry point. Calling
`python3 scripts/analyze_video.py` directly uses whatever `python3` resolves to
in the current shell and may surface local interpreter problems, but it is the
right escape hatch when a specific interpreter is required.

## Dependencies

Install-time dependencies:

- Python 3.10+ for validation and script execution. The public
  `scripts/analyze-video` wrapper selects a suitable interpreter by capability,
  so pyenv, asdf, uv, Homebrew, system, and manually installed interpreters can
  all work.
- `rsync` for copying the skill into Codex and Claude Code skill directories.
- `ffmpeg` available on `PATH`, or passed at install time with
  `make FFMPEG=/path/to/ffmpeg install-runtime`.

Runtime dependencies:

- `ffmpeg`, configured through `frame.ffmpeg` or available on `PATH`. Frame mode
  and multimodal mode both sample local frames through ffmpeg.
- `ffprobe`, configured through `frame.ffprobe` or available on `PATH`, for
  richer metadata. Analysis still works without metadata if ffprobe is
  unavailable.
- No third-party Python packages are required; provider calls use the Python
  standard library.
- A configured provider key and model only when using `multimodal` mode or when
  `mode=auto` should choose multimodal mode.

Provider-specific API keys should live in the runtime config file or in
well-known provider environment variables such as `DASHSCOPE_API_KEY`,
`OPENAI_API_KEY`, or `GOOGLE_AI_API_KEY`. Keep repository files secret-free.

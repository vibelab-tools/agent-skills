# video-understanding

Agent skill for analyzing local video files through timestamped frame sampling,
sampled-frame vision providers, or native video/video-URL provider input.

## Purpose

Use this skill when an agent needs to summarize, inspect, debug, or answer
questions about a local video file. It supports two execution modes:

- `frames`: extract sampled frames with ffmpeg and let Codex or Claude Code
  inspect the frames directly. This is the privacy-preserving fallback mode.
- `vision-frames`: send sampled frames to a configured vision provider and
  return a model answer.
- `video-native`: send the native video or a public video URL to a provider that
  supports video input. This mode does not extract frames before the provider
  call.

## Configuration

Runtime configuration lives under:

```text
~/.vibelab-tools/agent-skills/video-understanding/config.json
```

`make install-runtime` copies `config.example.json` into the runtime directory
when no config file exists. Users should edit `config.json` to choose:

- `mode`: `auto`, `frames`, `vision-frames`, or `video-native`
- `provider`: `openai-compatible` or `gemini`
- frame interval, frame cap, retention behavior, and ffmpeg paths
- endpoint, API key, model, temperature, and output-token settings

By default, `auto` prefers `video-native` when the selected provider has native
video enabled plus a model and API key. Otherwise it uses `vision-frames` when
the provider is configured, then falls back to local frame extraction.

### Configuration Reference

Top-level fields:

| Field | Values | Meaning |
| --- | --- | --- |
| `mode` | `auto`, `frames`, `vision-frames`, `video-native` | `frames` extracts local frames for agent inspection. `vision-frames` sends sampled frames to the selected provider. `video-native` sends the video or video URL to the selected provider. `multimodal` is still accepted as a legacy alias for `vision-frames`. |
| `provider` | `openai-compatible`, `gemini` | Selects which provider block is used. Only the selected provider must be configured. |
| `clear_proxy_env` | `true`, `false` | Removes ambient proxy environment variables before provider calls. Keep this enabled unless the provider must be reached through the shell proxy. |

Frame sampling fields:

| Field | Meaning |
| --- | --- |
| `frame.interval_seconds` | Seconds between sampled frames. `1.0` means one frame per second. |
| `frame.max_frames` | Maximum extracted frames. `0` means no cap. If the video is longer than the cap allows, the script increases the effective interval. |
| `frame.max_side` | Maximum width or height of each sampled frame after scaling. |
| `frame.jpeg_quality` | ffmpeg JPEG quality from `1` to `31`; lower values mean higher quality/larger files. |
| `frame.output_root` | Optional parent directory for extracted frame runs. Omit it to use the default `frames/` directory under the video-understanding runtime root. |
| `frame.keep_frames` | Keeps extracted frames on disk when `true`. If `false`, vision-frames mode removes frames after the provider returns. |
| `frame.ffmpeg` | ffmpeg executable path or command name. |
| `frame.ffprobe` | ffprobe executable path or command name for metadata. |
| `frame.timeout_seconds` | Timeout for local ffmpeg/ffprobe work. |

Vision-frames shared fields:

| Field | Meaning |
| --- | --- |
| `vision_frames.fallback_to_frames` | If provider analysis fails, return frame-mode JSON instead of failing hard. |
| `vision_frames.batch_size` | Number of sampled frames sent per provider request. |
| `vision_frames.request_timeout_seconds` | Timeout for each provider HTTP request. |

Video-native shared fields:

| Field | Meaning |
| --- | --- |
| `video_native.fallback_to_vision_frames` | If native video analysis fails, fall back to sampled-frame provider analysis. |
| `video_native.request_timeout_seconds` | Timeout for native video provider calls and uploads. |
| `video_native.openai_compatible.input_method` | `auto`, `upload`, `url`, `base64`, or `inline`. `auto` prefers configured URL, then upload when `video_upload.enabled=true`, then small inline Base64. |
| `video_native.openai_compatible.video_url` | Public video URL to pass directly to OpenAI-compatible `video_url`. |
| `video_native.openai_compatible.max_inline_bytes` | Maximum local file size allowed for Base64 inline video. Larger files should use `video_upload` or a configured public URL. |
| `video_native.gemini.input_method` | `file-api` for Gemini Files API upload. |
| `video_native.gemini.delete_uploaded_file` | Deletes the uploaded Gemini file after analysis when `true`. |

Video upload fields:

| Field | Meaning |
| --- | --- |
| `video_upload.enabled` | Enables automatic upload of local videos before OpenAI-compatible native video calls. |
| `video_upload.active_host` | Selects a host under `video_upload.hosts`. |
| `video_upload.hosts.<name>.type` | Currently `s3-compatible`. Works with Alibaba Cloud OSS, Cloudflare R2, MinIO, and S3-compatible stores. |
| `video_upload.hosts.<name>.endpoint_url` | OSS/S3-compatible endpoint URL. |
| `video_upload.hosts.<name>.bucket` | Upload bucket. |
| `video_upload.hosts.<name>.public_base_url` | Public base URL used to build the URL sent to the model. |
| `video_upload.hosts.<name>.prefix` | Object-key prefix. Supports `{year}`, `{month}`, `{day}`, and `{date}`. |

OpenAI-compatible provider fields:

| Field | Meaning |
| --- | --- |
| `endpoint` | Full Chat Completions URL, not just the base URL. |
| `api_key` | Inline API key. Prefer leaving this empty and using `api_key_env` when possible. |
| `api_key_env` | Environment variable name used when `api_key` is empty. Prefer established provider names such as `DASHSCOPE_API_KEY` or `OPENAI_API_KEY`. |
| `model` | Provider model ID. This must be non-empty for `auto` to enter a provider-backed mode. |
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
  "vision_frames": {
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
  },
  "video_native": {
    "openai_compatible": {
      "enabled": true,
      "input_method": "auto"
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
| `model` | Gemini model ID. This must be non-empty for `auto` to enter a provider-backed mode when `provider` is `gemini`. |
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

Default analysis, using `config.json` for mode and provider selection:

```bash
~/.codex/skills/video-understanding/scripts/analyze-video \
  video.mp4 \
  --question "Summarize this video."
```

Do not pass `--mode` or `--provider` in normal agent calls. Configure these
values in:

```text
~/.vibelab-tools/agent-skills/video-understanding/config.json
```

This avoids ambiguity between command-line flags and runtime configuration.

The CLI still accepts `--mode` and `--provider` as one-off manual override flags,
but agent-generated commands should not include them unless the user explicitly
asks to ignore the runtime configuration for that command.

The script prints JSON. In `frames` mode, use `frame_manifest.frames` to inspect
the sampled images directly. In `vision-frames` or `video-native` mode, use
`answer` as the final response unless fallback returned frame-mode JSON.

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
  and vision-frames mode both sample local frames through ffmpeg. Video-native
  mode does not require frame extraction before provider analysis.
- `ffprobe`, configured through `frame.ffprobe` or available on `PATH`, for
  richer metadata. Analysis still works without metadata if ffprobe is
  unavailable.
- `boto3`, installed into the skill runtime venv by `make install-runtime`, is
  required only when `video_upload.enabled=true`.
- A configured provider key and model only when using `vision-frames` or
  `video-native`, or when `mode=auto` should choose a provider-backed mode.

Provider-specific API keys should live in the runtime config file or in
well-known provider environment variables such as `DASHSCOPE_API_KEY`,
`OPENAI_API_KEY`, or `GOOGLE_AI_API_KEY`. Keep repository files secret-free.

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
- frame interval, frame cap, output root, and ffmpeg paths
- endpoint, API key, model, temperature, and output-token settings

By default, `auto` uses multimodal mode only when a model and API key are
configured; otherwise it extracts frames for agent-side analysis.

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
Python interpreters are accepted. Override selection when needed:

```bash
VIDEO_UNDERSTANDING_PYTHON=/path/to/python \
  ~/.codex/skills/video-understanding/scripts/analyze-video video.mp4
```

Use the wrapper as the public entry point. Calling
`python3 scripts/analyze_video.py` directly uses whatever `python3` resolves to
in the current shell and may surface local interpreter problems.

## Dependencies

- Python 3.10+
- ffmpeg on `PATH`, or configured through `frame.ffmpeg`
- ffprobe on `PATH` for richer metadata; analysis still works without metadata
  if ffprobe is unavailable
- A configured provider key and model only when using multimodal mode

# markitdown

Agent skill for converting non-plain-text inputs into Markdown with Microsoft
MarkItDown before analysis.

## Purpose

Use this skill when an agent needs to inspect, summarize, extract, or reason
over files and URLs that are awkward to read directly, such as PDF, Office
documents, spreadsheets, presentations, images, audio, HTML, CSV, JSON, XML,
ZIP, EPUB, Outlook messages, YouTube URLs, and other supported formats.

The skill prefers MarkItDown as the first-pass reader and keeps the converted
Markdown as the reasoning surface. Plain text, source code, and small Markdown
files should still be read directly.

## Build

No build step is required.

```bash
make
```

## Install

```bash
make install          # install for Codex and Claude Code
make install-codex    # install for Codex only
make install-claude   # install for Claude Code only
make install-runtime  # install only the MarkItDown runtime venv
```

Installed locations:

- Codex: `~/.codex/skills/markitdown`
- Claude Code: `~/.claude/skills/markitdown`
- Runtime: `~/.vibe-coding-skill/markitdown`

## Notes

The skill does not vendor MarkItDown into this repository. `make install`
creates an isolated Python virtual environment under
`~/.vibe-coding-skill/markitdown/venv` and installs `markitdown[all]` there.
The installed skill instructions prefer
`~/.vibe-coding-skill/markitdown/bin/markitdown` before falling back to a
system `markitdown` executable or a temporary one-off environment.

The installer searches for a suitable Python runtime by capability, not by
installation manager. A pyenv, asdf, uv, Homebrew, system, or manually installed
Python is acceptable when it is Python 3.10+ and has working standard-library
hash support required by packaging tools. Override detection when needed:

```bash
make RUNTIME_PYTHON=/path/to/python install-runtime
```

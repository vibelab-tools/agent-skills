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
- Runtime: `~/.vibelab-tools/agent-skills/markitdown`

## Notes

The skill does not vendor MarkItDown into this repository. `make install`
creates an isolated Python virtual environment under
`~/.vibelab-tools/agent-skills/markitdown/venv` and installs `markitdown[all]` there.
The installed skill instructions prefer
`~/.vibelab-tools/agent-skills/markitdown/bin/markitdown` before falling back to a
system `markitdown` executable or a temporary one-off environment.

## Visual Asset Extraction

The runtime also installs `markitdown-assets`, a local wrapper for image-heavy
documents. It first converts with MarkItDown, then writes visual assets into a
local `assets/` directory and records them in `manifest.json`. The Markdown
output includes local image links so Codex, Claude Code, or another agent can
open the extracted images directly when visual evidence matters.

Example:

```bash
~/.vibelab-tools/agent-skills/markitdown/bin/markitdown-assets input.pdf -o output.md
```

Default output layout when `-o output.md` is used:

```text
output.md
output.assets/
  assets/
    pdf-page-0001.png
    slide-001-image-001.png
  manifest.json
```

If `-o` is omitted, the converted Markdown is printed to stdout and also written
under `~/.vibelab-tools/agent-skills/markitdown/runs/<run-id>/document.md`.

Supported asset sources:

| Format | Asset behavior |
| --- | --- |
| `PDF` | Renders pages to PNG by default. This preserves scanned pages, charts, diagrams, screenshots, and visual layout. |
| `PPTX` | Extracts embedded picture shapes and rewrites MarkItDown placeholder image links to local asset paths. |
| `DOCX` | Extracts embedded images from `word/media/*`. |
| `XLSX` | Extracts worksheet images and records sheet/cell metadata when available. |
| `HTML/HTM` | Copies local image references and saves data-URI images. Remote HTTP images are left unchanged. |
| `EPUB` | Extracts image files stored in the EPUB archive and rewrites matching local chapter links where possible. |
| `ZIP` | Extracts image files stored in the archive. |
| Standalone images | Copies the source image into the assets directory and indexes it. |

Legacy `.ppt` and `.xls` files remain text/table-only because embedded image
extraction for those binary Office formats requires separate parsers.

Useful options:

```bash
markitdown-assets input.pdf -o output.md
markitdown-assets input.pdf -o output.md --pdf-dpi 180
markitdown-assets input.pdf -o output.md --max-pdf-pages 20
markitdown-assets input.pdf -o output.md --pdf-pages skip
markitdown-assets input.pptx -o output.md --relative-links
markitdown-assets input.docx -o output.md --assets-dir ./output-assets/assets
```

`manifest.json` is the source of truth for extracted assets. It records the
absolute path, relative path, source format, source reference, hash, and any
page/slide/sheet/cell metadata found during extraction. A local image link means
the image is available for inspection; it does not mean the image content has
already been understood by the model.

`markitdown-assets` is intentionally local-file oriented. Use normal
`markitdown` for URL conversion unless the URL has already been downloaded to a
local file.

## Python Runtime

The installer searches for a suitable Python runtime by capability, not by
installation manager. A pyenv, asdf, uv, Homebrew, system, or manually installed
Python is acceptable when it is Python 3.10+ and has working standard-library
hash support required by packaging tools. Override detection when needed:

```bash
make RUNTIME_PYTHON=/path/to/python install-runtime
```

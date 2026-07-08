---
name: markitdown
description: Convert documents, media, archives, and supported URLs to Markdown with Microsoft's MarkItDown. Use when an AI coding agent needs to read, inspect, summarize, extract, or analyze PDF, Word/DOCX, PowerPoint/PPTX, Excel/XLS/XLSX, images, audio, HTML, CSV, JSON, XML, ZIP, EPUB, Outlook messages, YouTube URLs, or other binary/non-plain-text inputs; prefer MarkItDown before ad hoc parsers unless the task needs exact format-specific fidelity.
---

# MarkItDown

## Overview

Use Microsoft's MarkItDown to turn supported files and URLs into Markdown before reasoning over their content. Treat it as the first-pass reader for document and media formats that are awkward to inspect directly.

Official source: https://github.com/microsoft/markitdown

## Workflow

1. Decide whether conversion is useful.
   - Use MarkItDown for PDF, Office files, spreadsheets, presentations, images, audio, HTML, CSV, JSON, XML, ZIP, EPUB, Outlook messages, and supported web/video URLs.
   - Read plain text, Markdown, source code, and small structured text files directly when conversion adds no value.

2. Prefer the managed executable installed by this skill, then fall back to an
   existing executable on `PATH`.
   ```bash
   MARKITDOWN="${MARKITDOWN:-$HOME/.vibe-coding-skill/markitdown/bin/markitdown}"
   if [ -x "$MARKITDOWN" ]; then
     "$MARKITDOWN" --help
   elif command -v markitdown >/dev/null 2>&1; then
     MARKITDOWN="$(command -v markitdown)"
     "$MARKITDOWN" --help
   fi
   ```

3. If MarkItDown is missing, install it outside the project unless the user
   asked to add it as a dependency. Prefer the repository Makefile when working
   from the `markitdown` skill source:
   ```bash
   make install-runtime
   ```
   For one-off work outside this repository, use a temporary environment:
   ```bash
   python3 -m venv /tmp/agent-markitdown-venv
   /tmp/agent-markitdown-venv/bin/python -m pip install -U pip
   /tmp/agent-markitdown-venv/bin/python -m pip install 'markitdown[all]'
   MARKITDOWN=/tmp/agent-markitdown-venv/bin/markitdown
   ```

4. Convert to a temporary Markdown file when the user only needs analysis.
   ```bash
   INPUT="/path/to/file.pdf"
   OUTDIR="$(mktemp -d)"
   OUT="$OUTDIR/converted.md"
   "${MARKITDOWN:-markitdown}" "$INPUT" -o "$OUT"
   sed -n '1,200p' "$OUT"
   ```

5. Write to the requested destination when the user asks for a converted artifact.
   ```bash
   markitdown "/path/to/file.docx" -o "/path/to/file.md"
   ```

## Python API

Use the Python API when shell piping is inconvenient or the conversion is part of a larger script:

```python
from markitdown import MarkItDown

md = MarkItDown(enable_plugins=False)
result = md.convert("/path/to/test.xlsx")
print(result.text_content)
```

Keep plugins disabled unless the user explicitly asks for a plugin-backed format or the local task clearly requires an installed plugin. If using plugins, inspect the plugin source and enable them intentionally with `--use-plugins` or `enable_plugins=True`.

## Output Checks

- Confirm the Markdown file exists and is non-empty before relying on it.
- Skim headings, tables, and extracted text to catch obvious conversion failures.
- For ZIP files, expect MarkItDown to iterate over contents; inspect the generated section boundaries before summarizing.
- For scanned PDFs, image-heavy slides, photos, or audio, conversion may be sparse depending on available optional dependencies and local OCR/transcription support. Report the limitation and use a targeted fallback only if needed.

## Safety

MarkItDown performs file and network I/O with the current process privileges. Do not run it on untrusted paths or URLs without considering what the process can access. Avoid cloud-backed options, LLM image descriptions, Azure integrations, or third-party plugins unless the user requested them or approved the external processing path.

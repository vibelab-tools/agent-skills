#!/usr/bin/env python3
"""Return Humanizer guidance as UserPromptSubmit developer context."""

from __future__ import annotations

import json
from pathlib import Path
import sys


GUIDANCE_PATH = Path(__file__).resolve().parents[1] / "references" / "pre-draft.md"


def main() -> int:
    sys.stdin.read()
    guidance = GUIDANCE_PATH.read_text(encoding="utf-8").strip()
    payload = {
        "hookSpecificOutput": {
            "hookEventName": "UserPromptSubmit",
            "additionalContext": guidance,
        }
    }
    print(json.dumps(payload, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

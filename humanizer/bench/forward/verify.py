#!/usr/bin/env python3
"""Validate the forward-generation benchmark registration."""

from __future__ import annotations

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parent
CONFIG_PATH = ROOT / "config.json"
DISALLOWED_PROMPT_TERMS = (
    "humanizer",
    "humanize",
    "rewrite",
    "ai-generated",
    "改写",
    "润色",
    "ai 味",
    "ai味",
)


def main() -> int:
    config = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
    if set(config.get("arms", {})) != {"baseline", "pre_draft"}:
        raise RuntimeError("forward benchmark must register baseline and pre_draft arms")

    treatment = config["arms"]["pre_draft"]
    for field in ("hook_config", "guidance"):
        path = (ROOT / treatment[field]).resolve()
        if not path.is_file():
            raise RuntimeError(f"missing pre_draft {field}: {path}")

    cases = config.get("cases", [])
    if len(cases) < 4:
        raise RuntimeError("forward benchmark must keep at least four registered cases")

    seen = set()
    for case in cases:
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id or case_id in seen:
            raise RuntimeError(f"invalid or duplicate case id: {case_id!r}")
        seen.add(case_id)

        prompt = case.get("prompt", "")
        lowered = prompt.casefold()
        matched = [term for term in DISALLOWED_PROMPT_TERMS if term in lowered]
        if matched:
            raise RuntimeError(f"{case_id} is not an ordinary prompt: {matched}")

        facts = case.get("facts")
        anchors = case.get("protected_anchors")
        criteria = case.get("criteria")
        if not isinstance(facts, list) or not facts or not all(isinstance(item, str) for item in facts):
            raise RuntimeError(f"{case_id} must provide fact notes, not a source draft")
        if not isinstance(anchors, list) or not anchors:
            raise RuntimeError(f"{case_id} must register protected anchors")
        if not isinstance(criteria, list) or len(criteria) < 3:
            raise RuntimeError(f"{case_id} must register at least three evaluation criteria")

        joined_facts = "\n".join(facts)
        missing = [anchor for anchor in anchors if anchor not in joined_facts]
        if missing:
            raise RuntimeError(f"{case_id} anchors missing from facts: {missing}")

    print(f"Forward benchmark verification passed: {len(cases)} cases.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

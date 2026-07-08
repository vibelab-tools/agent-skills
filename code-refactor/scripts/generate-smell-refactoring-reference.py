#!/usr/bin/env python3
import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SMELL_MAP = ROOT / "src/main/resources/com/codex/refactor/refactoring/smell-refactoring-map.json"
CATALOG = ROOT / "src/main/resources/com/codex/refactor/refactoring/refactorings-catalog.json"
PLAYBOOKS = ROOT / "src/main/resources/com/codex/refactor/refactoring/refactoring-playbooks.json"
OUTPUT = ROOT / "skill/code-refactor/references/smell-to-refactoring.md"
SMELL_DIR = ROOT / "skill/code-refactor/references/smells"
REFACTORING_DIR = ROOT / "skill/code-refactor/references/refactorings"


def slug(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")


def bullet_list(values: list[str]) -> list[str]:
    if not values:
        return ["- None recorded."]
    return [f"- {value}" for value in values]


def first_safe_step(playbook: dict) -> str:
    steps = playbook.get("steps", [])
    return steps[0] if steps else ""


def link_refactoring(name: str) -> str:
    return f"[{name}](refactorings/{slug(name)}.md)"


def relative_refactoring_link(name: str) -> str:
    return f"[{name}](../refactorings/{slug(name)}.md)"


def relative_smell_link(name: str) -> str:
    return f"[{name}](../smells/{slug(name)}.md)"


def remove_stale_markdown(directory: Path) -> None:
    directory.mkdir(parents=True, exist_ok=True)
    for path in directory.glob("*.md"):
        path.unlink()


def render_smell_detail(mapping: dict, playbooks_by_name: dict[str, dict]) -> str:
    smell = mapping["smell"]
    refactorings = mapping["refactorings"]
    primary = refactorings[0]
    strategy_lines = []
    for index, refactoring in enumerate(refactorings, start=1):
        playbook = playbooks_by_name[refactoring]
        strategy_lines.extend([
            f"### {index}. {relative_refactoring_link(refactoring)}",
            "",
            f"- Use when: {playbook['applies_when']}",
            f"- First safe step: {first_safe_step(playbook)}",
            f"- Main risk: {playbook['risks'][0] if playbook['risks'] else 'No risk recorded.'}",
            "",
        ])

    verification = []
    seen = set()
    for refactoring in refactorings:
        for item in playbooks_by_name[refactoring].get("test_focus", []):
            if item not in seen:
                seen.add(item)
                verification.append(item)

    lines = [
        f"# {smell}",
        "",
        f"- Book chapter: Chapter 3, {smell}",
        f"- Typical evidence: {mapping['typical_evidence']}",
        f"- Primary refactoring: {relative_refactoring_link(primary)}",
        "",
        "## How To Use",
        "",
        "Read this file when a detection result names this bad smell and the edit is non-trivial.",
        "Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.",
        "",
        "## Strategy",
        "",
        *strategy_lines,
        "## Verification Focus",
        "",
        *bullet_list(verification),
        "",
        "## Guardrails",
        "",
        "- Do not apply every candidate refactoring; choose one narrow step and rerun detection.",
        "- Prefer the first strategy only when it fits the concrete evidence and local design.",
        "- If the finding is low-confidence, inspect code and tests before editing.",
        "- After editing, rerun focused tests and `detect-smells` on the changed scope.",
        "",
        "## Related Refactorings",
        "",
        *[f"- {relative_refactoring_link(refactoring)}" for refactoring in refactorings],
        "",
    ]
    return "\n".join(lines)


def render_refactoring_detail(
        entry: dict,
        playbook: dict,
        mappings: list[dict],
) -> str:
    name = entry["name"]
    used_by = [
        mapping["smell"]
        for mapping in mappings
        if name in mapping["refactorings"]
    ]
    lines = [
        f"# {name}",
        "",
        f"- Book chapter: {entry['chapter']} {name}",
        "- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.",
        "",
        "## Applies When",
        "",
        playbook["applies_when"],
        "",
        "## Preconditions",
        "",
        *bullet_list(playbook.get("preconditions", [])),
        "",
        "## First Safe Step",
        "",
        first_safe_step(playbook),
        "",
        "## Steps",
        "",
        *[f"{index}. {step}" for index, step in enumerate(playbook.get("steps", []), start=1)],
        "",
        "## Test Focus",
        "",
        *bullet_list(playbook.get("test_focus", [])),
        "",
        "## Risks",
        "",
        *bullet_list(playbook.get("risks", [])),
        "",
        "## Common Smells",
        "",
    ]
    if used_by:
        lines.extend(f"- {relative_smell_link(smell)}" for smell in used_by)
    else:
        lines.append("- Not a primary smell-map recommendation; use only when local code evidence calls for it.")
    lines.extend([
        "",
        "## Guardrails",
        "",
        "- Keep the transformation behavior-preserving.",
        "- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.",
        "- Run focused tests before broad cleanup.",
        "- Rerun smell detection after the edit to catch replacement smells.",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    data = json.loads(SMELL_MAP.read_text(encoding="utf-8"))
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    playbooks = json.loads(PLAYBOOKS.read_text(encoding="utf-8"))
    playbooks_by_name = {playbook["name"]: playbook for playbook in playbooks["playbooks"]}

    remove_stale_markdown(SMELL_DIR)
    remove_stale_markdown(REFACTORING_DIR)

    for mapping in data["mappings"]:
        (SMELL_DIR / f"{slug(mapping['smell'])}.md").write_text(
            render_smell_detail(mapping, playbooks_by_name),
            encoding="utf-8",
        )

    for entry in catalog["refactorings"]:
        playbook = playbooks_by_name[entry["name"]]
        (REFACTORING_DIR / f"{slug(entry['name'])}.md").write_text(
            render_refactoring_detail(entry, playbook, data["mappings"]),
            encoding="utf-8",
        )

    lines = [
        "# Smell To Refactoring Map",
        "",
        "Generated from `src/main/resources/com/codex/refactor/refactoring/smell-refactoring-map.json`.",
        "",
        "Use this as a decision aid, not a command list. Pick the smallest refactoring",
        "that addresses the concrete evidence in the finding. Candidate names are limited",
        "to the Refactoring, 2nd Edition catalog in chapters 6-12.",
        "",
        "For non-trivial edits, read the matching `smells/<smell>.md` file and the",
        "primary `refactorings/<refactoring>.md` file before editing.",
        "",
        "| Smell | Typical Evidence | Candidate Refactorings |",
        "| --- | --- | --- |",
    ]
    for mapping in data["mappings"]:
        refactorings = ", ".join(link_refactoring(refactoring) for refactoring in mapping["refactorings"])
        smell_link = f"[{mapping['smell']}](smells/{slug(mapping['smell'])}.md)"
        lines.append(f"| {smell_link} | {mapping['typical_evidence']} | {refactorings} |")
    lines.extend([
        "",
        "## Confidence Rules",
        "",
        "- High-confidence AST evidence can justify direct small refactors after tests.",
        "- Medium confidence needs code inspection and local design context.",
        "- Low confidence should be reported as a signal unless corroborated by code,",
        "  tests, or project history.",
        "",
        "## Refactoring Order",
        "",
        "Prefer this order when several smells overlap:",
        "",
        "1. Characterize behavior with tests.",
        "2. Rename confusing names.",
        "3. Extract functions from long functions.",
        "4. Move extracted functions toward envied data.",
        "5. Introduce parameter objects or classes for repeated data groups.",
        "6. Split large/divergent classes after smaller extracts reveal boundaries.",
        "7. Remove middle men or lazy elements only after confirming they are not",
        "   intentional API/facade boundaries.",
        "",
    ])
    OUTPUT.write_text("\n".join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()

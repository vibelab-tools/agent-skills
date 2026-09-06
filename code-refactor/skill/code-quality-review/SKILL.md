---
name: code-quality-review
description: Review code smells, complexity, and maintainability using parser-backed tools when the user requests that analysis or a concrete structural concern needs evidence. Supports focused file analysis and a Git diff scan with up to three compact candidates. Do not automatically run after every implementation or use for docs-only, formatting-only, generated, vendored, or unrelated legacy cleanup.
---

# Code Quality Review

Use this Skill to answer a concrete maintainability question. A normal diff
review does not require a detector scan. Keep any resulting refactor within
the current request.

## Post-Change Review

1. Confirm that the change includes non-trivial production behavior. Skip this
   Skill for documentation, formatting, generated output, or test-only edits.
2. When a diff-wide scan is requested or would resolve the structural concern,
   run `scripts/review-changes` once. For a known file or hotspot, use the focused
   analyzer directly. The diff wrapper selects
   changed production lines, runs all 24 standard detectors, and emits at most
   three high-confidence candidates whose reported scope was materially touched
   by the diff. A zero-candidate report is a valid result.
3. Read the diff and the surrounding business responsibility for every emitted
   candidate. Accept a candidate only when the evidence describes a real design
   problem introduced or materially worsened by the current change. Reject
   framework idioms, ordinary data/configuration shape, deliberate boundaries,
   and unrelated legacy problems.
4. Refactor only accepted candidates, and only when the behavior-preserving
   improvement is clear, small, and inside the current requirement. Load the
   matching smell/refactoring card only after accepting the candidate.
5. After an accepted refactor, run the narrowest meaningful behavior check and
   review the final diff. Repeat a detector only when needed to verify the
   affected concern; do not rescan an unchanged result. Use `plan-refactor` only
   when an accepted problem needs a multi-step refactoring plan.

Read [references/refactoring-workflow.md](references/refactoring-workflow.md)
when scope selection or candidate validation needs more detail. Read
[references/tooling.md](references/tooling.md) only when exact CLI usage or JSON
fields are needed.

## Tool Commands

```bash
<skill>/scripts/review-changes
<skill>/scripts/analyze-complexity --json <changed-production-file>...
<skill>/scripts/detect-smells --json --min-confidence high <confirmed-hotspot>...
<skill>/scripts/plan-refactor --json --max-findings 5 <confirmed-smell-report.json|->
```

Write reports under
`${XDG_CACHE_HOME:-$HOME/.cache}/code-quality-review/<run-name>/`, not into the
target repository.

## Decision Rules

- Tool output is evidence, not authority. Never refactor solely to clear a
  finding or threshold.
- Do not treat the three-candidate cap as proof that omitted candidates require
  work. Resolve the reported candidates first; a rescan reveals the next bounded
  set only when further review is still warranted.
- Prefer no refactor when the change is already clear and cohesive.
- Keep accepted refactors small, behavior-preserving, and inside the current
  requirement's scope.
- Do not add abstractions for a single use or clean unrelated legacy code.
- Do not create tests only to enable a speculative refactor. Use the smallest
  existing or meaningful verification boundary.
- Preserve user changes and local style.

When a confirmed smell needs a Fowler-style method, use
[references/smell-to-refactoring.md](references/smell-to-refactoring.md) as a
decision aid and read only the matching smell and refactoring cards.

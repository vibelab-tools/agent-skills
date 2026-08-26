---
name: code-quality-review
description: Review the current Git diff after a non-trivial feature, bug fix, or refactor by running all 24 parser-backed smell detectors on changed production lines, validating up to three compact candidates, and applying only small behavior-preserving refactors for problems introduced or worsened by the change. Use as the final quality pass after implementation and when asked about bad smells, maintainability, or whether refactoring is warranted. Do not use for docs-only, formatting-only, typo-only, generated, vendored, or unrelated legacy cleanup.
---

# Code Quality Review

Use this Skill as the final quality pass for a completed code change. The goal
is to prevent the current change from degrading maintainability, not to make an
old repository perfect.

## Post-Change Review

1. Confirm that the change includes non-trivial production behavior. Skip this
   Skill for documentation, formatting, generated output, or test-only edits.
2. Run `scripts/review-changes` once after implementation. The wrapper selects
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
5. Run the narrowest meaningful behavior check, then rerun
   `scripts/review-changes`. Review the final diff for scope creep and accidental
   behavior changes. Do not run `plan-refactor` before manual confirmation.

Read [references/refactoring-workflow.md](references/refactoring-workflow.md)
for scope selection, candidate validation, and the completion decision. Read
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

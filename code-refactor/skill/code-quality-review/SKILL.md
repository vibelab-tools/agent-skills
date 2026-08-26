---
name: code-quality-review
description: Review code quality after completing a non-trivial implementation by inspecting the current diff, using parser-backed complexity or smell evidence only when warranted, and applying only small behavior-preserving refactors that address problems introduced or worsened by the change. Use after feature, bug-fix, or refactor work and when asked to assess bad smells, risky growth, or refactoring need. Do not use for docs, formatting, or typo-only work, generated or vendored code, or unrelated legacy cleanup.
---

# Code Quality Review

Use this Skill as the final quality pass for a completed code change. The goal
is to prevent the current change from degrading maintainability, not to make an
old repository perfect.

## Post-Change Review

1. Build the review scope from the current Git diff and relevant untracked
   files. Review changed production code first. Exclude generated, vendored,
   migration, fixture, configuration, and test files unless they implement
   changed production behavior or the user explicitly includes them.
2. Read the diff before running detectors. Look for duplication introduced by
   the change, risky function growth, complex branching, mixed responsibilities,
   unclear state lifetime, and abstractions that do not earn their cost.
3. For every non-trivial implementation, run `scripts/review-changes`. It uses
   the current Git diff, runs complexity analysis, and returns only functions and
   classes overlapping changed lines.
4. If the diff review or complexity report identifies a concrete hotspot, rerun
   `scripts/review-changes --smells` to add only high-confidence smell candidates
   overlapping changed lines. A report with no findings is a valid result.
5. Validate each finding against the code and business responsibility. Reject
   findings that describe framework idioms, ordinary data/configuration shape,
   deliberate boundaries, or unrelated pre-existing code.
6. Refactor during the current task only when the issue was introduced or
   materially worsened by the change, the improvement is clear, and focused
   verification can preserve behavior. Otherwise report or track it separately.
7. After a refactor, rerun the affected checks and review the final diff. Do not
   run `plan-refactor` until a specific finding has been confirmed manually.

Read [references/refactoring-workflow.md](references/refactoring-workflow.md)
for scope selection, signal thresholds, and the completion decision. Read
[references/tooling.md](references/tooling.md) only when exact CLI usage or JSON
fields are needed.

## Tool Commands

```bash
<skill>/scripts/review-changes
<skill>/scripts/review-changes --smells
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

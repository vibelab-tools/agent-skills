---
name: code-refactor
description: AST-backed code refactoring workflow based on Fowler Refactoring 2nd Edition. Use when an AI coding agent needs to analyze existing source code for code smells, complexity, risky growth, or refactoring opportunities; plan or perform behavior-preserving refactors; map detected smells to Fowler-style refactorings; rerun smell detection after changes; or work across Java, Bash, C/C++, C#, Go, Rust, HTML, CSS, JavaScript, TypeScript, TSX, Vue, Ruby, SQL, and Python.
---

# Code Refactor

Use this skill to make refactoring evidence-led: parse code, detect Fowler
Chapter 3 smells, pick small behavior-preserving refactorings, verify with
tests, and rerun detection. The runtime is offline and deterministic; do not add
LLM/network review steps to the workflow.

## Quick Start

Use the bundled jar through wrapper scripts:

```bash
<skill>/scripts/analyze-complexity --json <path>...
<skill>/scripts/detect-smells --json <path>...
<skill>/scripts/plan-refactor --json <detect-smells-report.json|->
```

Prefer JSON output for decisions. Use text output only for quick human scans.
For large reports, write files under `${XDG_CACHE_HOME:-$HOME/.cache}/code-refactor/<run-name>/`
instead of the analyzed repository or skill project directory.

For repositories with Git history available, upgrade Shotgun Surgery evidence:

```bash
<skill>/scripts/detect-smells --json --history-analysis git <path>...
```

## Refactoring Workflow

1. Identify the user intent:
   - If they ask for analysis, report findings and evidence.
   - If they ask to refactor, analyze first unless the change is tiny and obvious.
   - If they ask whether code is clean after a refactor, rerun detection.
2. Run the tool on the smallest meaningful scope: touched files first, then the
   module/package/directory when the smell is cross-file.
3. Read results by confidence, severity, evidence, and
   `recommended_refactoring_rationale`. Treat low-confidence fallback findings
   as review prompts, not automatic edits.
4. For non-trivial refactors, turn the smell report into a small execution plan:
   `scripts/plan-refactor --json --max-findings 20 <report.json>`.
   The default plan groups by file so a single hotspot does not monopolize the
   plan; use `--group-by finding` when you intentionally want raw finding order.
5. Before editing non-trivial code, read the generated strategy notes for the
   detected smell and selected primary refactoring, for example:
   `references/smells/long-function.md` and
   `references/refactorings/extract-function.md`.
6. State the intended refactoring slice: bad smell, evidence,
   Fowler refactoring method, why it is the smallest safe step, and the
   validation plan.
7. Check tests or create focused characterization tests when the
   behavior is risky and test coverage is missing.
8. Apply one small refactoring step at a time. Preserve public behavior and
   match local style.
9. Run relevant tests, then rerun `detect-smells` on the edited scope.
10. Summarize before/after: changed code, smell movement, new or remaining
   smells, and any tests that could not be run.

Read [references/refactoring-workflow.md](references/refactoring-workflow.md)
for the detailed phase checklist.

## Choosing Refactorings

Use the smell evidence and `recommended_refactoring_rationale.first_safe_step`
to select a narrow Fowler-style refactoring. Do not rewrite broadly just because
a smell exists.

Common examples:

- Long Function -> Extract Function, Replace Temp with Query, Decompose Conditional.
- Feature Envy -> Move Function, Extract Function.
- Data Clumps -> Introduce Parameter Object, Extract Class.
- Middle Man -> Remove Middle Man, Inline Function.
- Refused Bequest -> Replace Subclass with Delegate, Replace Superclass with Delegate, Push Down Method.

Read [references/smell-to-refactoring.md](references/smell-to-refactoring.md)
when mapping a specific smell to candidate refactorings.
That reference links to generated per-smell and per-refactoring notes. Prefer
the JSON fields in fresh tool output for ranking, then read only the matching
detail files needed for the chosen refactoring slice.

## Tooling Notes

Read [references/tooling.md](references/tooling.md) when you need exact CLI
options, language IDs, exit-code behavior, or JSON fields.

Important defaults:

- Supported language IDs: `java`, `bash`, `c`, `cpp`, `csharp`, `go`, `rust`,
  `html`, `css`, `javascript`, `typescript`, `tsx`, `vue`, `ruby`, `sql`,
  `python`.
- Default output is human text; use `--json` for analysis.
- Directory scans include supported source languages by default; explicit
  unsupported file inputs still report `unsupported_language`.
- `--min-confidence high` is useful before automated refactoring.
- `--fail-on-parse-error` is useful in CI-style checks.
- Parser errors are report data unless the invocation itself fails.

## Safety Rules

- Do not treat detector output as a command to edit everything. Rank by evidence
  and business risk.
- Do not edit non-trivial code until you have named the smell, evidence,
  selected Fowler method, first safe step, and verification commands.
- Do not introduce new abstractions unless they reduce real complexity or match
  an existing local pattern.
- Do not refactor across public APIs without checking callers and tests.
- Do not use LLM/network review for smell validation; improve AST/project/history
  evidence instead.
- Preserve user changes in the working tree. Never revert unrelated files.

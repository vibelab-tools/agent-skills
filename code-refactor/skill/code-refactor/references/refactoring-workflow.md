# Refactoring Workflow

## Phase 1: Build Context

1. Identify the user's goal: analysis, refactoring plan, implementation, or
   post-refactor validation.
2. Inspect the repository structure, tests, build tool, and changed files.
3. Prefer existing project patterns over new abstractions.
4. Choose the smallest analysis scope that can answer the question.

## Phase 2: Run Evidence Collection

Run smell detection first:

```bash
scripts/detect-smells --json <path>...
```

Use `--history-analysis git` when the smell is change-pattern based, especially
Shotgun Surgery:

```bash
scripts/detect-smells --json --history-analysis git <path>...
```

Run complexity when size or flow risk is central:

```bash
scripts/analyze-complexity --json <path>...
```

If parse errors appear, report them and avoid over-claiming smell precision for
that file.

## Phase 3: Triage Findings

Rank by:

1. High confidence and high severity.
2. Findings with concrete AST/project/history evidence.
3. Findings in files the user asked about or files you need to edit.
4. Findings that block safe change.

Treat low-confidence fallback evidence as a prompt for human/code review, not as
an automatic refactor target.

For non-trivial reports, generate a bounded plan before editing:

```bash
scripts/plan-refactor --json --max-findings 20 smells.json
```

The plan should drive the first refactoring slice, not replace local judgment.
Prefer the first safe step from the plan when it matches the codebase and test
surface. The default plan groups by file to keep large reports from being
dominated by one giant hotspot; use `--group-by finding` when narrowing into a
specific file.

For non-trivial edits, read the generated strategy cards that match the
selected finding:

- `references/smells/<smell>.md` for the bad-smell strategy and alternatives.
- `references/refactorings/<refactoring>.md` for the selected Fowler method,
  preconditions, steps, risks, and test focus.

Do not load all strategy cards. Load only the smell and refactoring files needed
for the current slice.

Before editing non-trivial code, state the refactoring intent in plain terms:

```text
Finding:
- <file>:<line> `<symbol>` has <Bad Smell> with <confidence> confidence.
- Evidence: <key AST/project/history facts>.

Plan:
- Use <Fowler refactoring name>.
- First safe step: <smallest behavior-preserving change>.
- Rationale: <why this method fits this smell and why broader rewrites are deferred>.

Verification:
- Run/add <focused tests>.
- Rerun detect-smells on <edited scope>.
```

Keep this short. The goal is to make the edit auditable before changing code,
not to produce a long design document.

## Phase 4: Prepare Safety Net

Before editing:

- Run existing focused tests if they are cheap.
- Add characterization tests when behavior is unclear and the refactor is not
  purely mechanical.
- Avoid broad test suites unless the blast radius is broad.
- If there are no tests and adding tests is not practical, keep the refactor
  smaller and state the residual risk.

## Phase 5: Apply Small Refactorings

Prefer one behavior-preserving transformation at a time:

1. Rename unclear symbols.
2. Extract small functions from long or mixed-concern functions.
3. Move behavior toward the data it uses.
4. Introduce parameter objects for repeated data groups.
5. Replace duplicated condition dispatch with polymorphism, strategy, or a
   shared dispatch table only when it fits the codebase.
6. Remove pass-through classes only when callers can talk to the real delegate
   without losing an intentional facade boundary.

Do not mix unrelated cleanup with the requested refactor.

## Phase 6: Verify

After edits:

1. Run focused tests.
2. Run format/lint/typecheck when the project uses them.
3. Rerun smell detection on the edited scope.
4. Compare before/after findings when useful.
5. Report remaining smells honestly, especially if they are intentional design
   tradeoffs or low-confidence signals.

The completion note should include:

- What was changed.
- Which smell(s) improved or disappeared.
- Whether any new smell appeared during the refactor and how it was handled.
- Tests and rerun commands with pass/fail status.
- Residual risk when coverage is incomplete.

## When Not To Refactor

Pause and report instead of editing when:

- The finding is low confidence and the code has no nearby tests.
- The change would alter a public API with unknown callers.
- The smell is a framework idiom or generated/vendor code.
- The code is already being changed by the user in a conflicting way.
- The correct design requires product/domain decisions.

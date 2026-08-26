# Post-Change Refactoring Workflow

## 1. Select The Review Scope

Start from the code changed for the current requirement:

```bash
git diff --name-only --diff-filter=ACMR HEAD
git ls-files --others --exclude-standard
```

Inspect the diff and select supported production source files. Do not expand to
the whole module merely because a changed file contains old problems.

Exclude by default:

- generated and vendored code;
- snapshots, fixtures, and test data;
- migrations and declarative configuration;
- tests, unless the current work changes production behavior implemented in a
  test harness or the user explicitly requests test-code review;
- files changed only by formatting or mechanical generation.

## 2. Review The Diff Before The Tools

Check whether the current change introduces or materially worsens:

- repeated business decisions or nearly identical branches;
- a function with several independent phases or deeply nested control flow;
- a class/module taking on a new unrelated responsibility;
- mutable state whose owner or lifetime is unclear;
- repeated parameter/data groups that express one domain concept;
- pass-through abstractions or extension points with no current need;
- error handling that hides, duplicates, or tangles the main behavior.

Distinguish changed-code evidence from pre-existing context. A finding in a
touched file is not automatically caused by the current change.

## 3. Collect Cheap Objective Evidence

Run the changed-code review after every non-trivial implementation:

```bash
RUN_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/code-quality-review/current"
mkdir -p "$RUN_DIR"
scripts/review-changes > "$RUN_DIR/review.json"
```

Inspect the top-level summary and only the functions/classes overlapping the
diff. These are review signals, not limits:

- function physical lines around 50 or more;
- cyclomatic complexity around 10 or more;
- cognitive complexity around 15 or more;
- nesting depth around 4 or more;
- a change adding roughly 100 lines to an existing file;
- a class/module gaining a distinct new responsibility.

Project conventions and domain complexity override these defaults.

## 4. Escalate Only Confirmed Hotspots

Add smell candidates only when the diff or metrics identify a concrete hotspot:

```bash
scripts/review-changes --smells > "$RUN_DIR/review-with-smells.json"
```

Use direct project/history analysis only for genuinely cross-file change
patterns that the changed-line review cannot answer:

```bash
scripts/detect-smells --json --min-confidence high \
  --history-analysis git <narrow-scope> > "$RUN_DIR/smells.json"
```

For each finding, verify:

1. The reported symbol and lines overlap relevant code.
2. The evidence describes the actual design problem.
3. The problem was introduced or materially worsened by this change.
4. A smaller code edit cannot solve it more clearly.
5. Behavior can be verified at a meaningful existing boundary.

Reject the finding when any of the first three checks fail. Zero confirmed
findings is the expected result for many well-scoped changes.

## 5. Decide Whether To Refactor Now

Refactor now only when all of these are true:

- the improvement belongs to the current requirement;
- the behavior-preserving step is clear and small;
- it does not require an uncertain public API or architecture decision;
- focused tests, typecheck, build, or another real boundary can verify it;
- the refactor reduces real complexity rather than only satisfying a metric.

If the problem is large or pre-existing, leave the current implementation
coherent and record the follow-up separately.

Generate a bounded Fowler plan only after manually confirming a finding:

```bash
scripts/detect-smells --json --min-confidence high <confirmed-hotspot> \
  > "$RUN_DIR/confirmed-smells.json"
scripts/plan-refactor --json --max-findings 5 "$RUN_DIR/confirmed-smells.json"
```

Read only the strategy cards for the chosen smell and first safe refactoring.

## 6. Verify Completion

After any accepted refactor:

1. Run the narrowest meaningful behavior check.
2. Rerun complexity on the edited production files.
3. Rerun smell detection only for the confirmed hotspot.
4. Review the final diff for scope creep and accidental behavior changes.

The completion note should say either:

- no refactor was warranted and why; or
- which current-change problem was refactored and which check preserved
  behavior.

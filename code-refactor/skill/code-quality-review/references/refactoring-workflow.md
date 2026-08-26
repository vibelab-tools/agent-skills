# Diff Smell Review Workflow

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

## 2. Run The Complete Detector Pass

Run the changed-code review after every non-trivial implementation:

```bash
RUN_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/code-quality-review/current"
mkdir -p "$RUN_DIR"
scripts/review-changes > "$RUN_DIR/review.json"
```

The wrapper runs all 24 standard detectors with the high-confidence filter. It
then keeps only findings whose reported scope was materially touched by changed
lines, ranks them deterministically, and emits at most three compact candidates.
This prevents a one-line edit inside a large legacy function or class from
surfacing a pre-existing whole-scope smell. The cap bounds review attention and
model context; it is not an automatic refactoring quota. A successful report
with zero candidates is a complete and normal outcome.

`--smells` remains accepted only for compatibility and does not change the
default behavior.

## 3. Validate Every Candidate

Read the changed lines and enough surrounding code to understand the business
responsibility. For each candidate, verify:

1. The reported symbol and lines overlap relevant code.
2. The evidence describes an actual design problem, not only a threshold.
3. The problem was introduced or materially worsened by this change.
4. A smaller code edit cannot solve it more clearly.
5. Behavior can be verified at a meaningful existing boundary.

Reject candidates that describe framework idioms, ordinary configuration or
data shape, generated structure, deliberate boundaries, or unrelated legacy
code. A touched file is not proof that every finding in it belongs to this task.

Use direct project/history analysis only when a confirmed candidate genuinely
depends on cross-file change patterns that the default scan cannot answer:

```bash
scripts/detect-smells --json --min-confidence high \
  --history-analysis git <narrow-scope> > "$RUN_DIR/smells.json"
```

## 4. Refactor Only Confirmed Problems

Refactor now only when all of these are true:

- the improvement belongs to the current requirement;
- the behavior-preserving step is clear and small;
- it does not require an uncertain public API or architecture decision;
- focused tests, typecheck, build, or another real boundary can verify it;
- the refactor reduces real complexity rather than only satisfying a metric.

If the problem is large or pre-existing, leave the current implementation
coherent and record the follow-up separately.

Load only the matching smell and refactoring cards after accepting a candidate.
Generate a bounded Fowler plan only when the accepted change needs more than one
obvious behavior-preserving step:

```bash
scripts/detect-smells --json --min-confidence high <confirmed-hotspot> \
  > "$RUN_DIR/confirmed-smells.json"
scripts/plan-refactor --json --max-findings 5 "$RUN_DIR/confirmed-smells.json"
```

Read only the strategy cards for the chosen smell and first safe refactoring.

## 5. Verify And Rescan

After any accepted refactor:

1. Run the narrowest meaningful behavior check.
2. Rerun `scripts/review-changes` on the resulting diff.
3. Review the final diff for scope creep and accidental behavior changes.

If the rescan reports another bounded set, validate it with the same rules. Do
not keep refactoring merely to drive the detector count to zero.

The completion note should say either:

- no refactor was warranted and why; or
- which current-change problem was refactored and which check preserved
  behavior.

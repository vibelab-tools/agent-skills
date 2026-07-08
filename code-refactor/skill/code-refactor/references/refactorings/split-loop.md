# Split Loop

- Book chapter: 8.7 Split Loop
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

One loop performs multiple independent tasks.

## Preconditions

- Loop tasks do not depend on each other's per-iteration side effects.
- Collection traversal cost is acceptable.

## First Safe Step

Copy the loop for one task.

## Steps

1. Copy the loop for one task.
2. Remove the copied task from the original loop.
3. Run tests and simplify each loop.

## Test Focus

- Accumulated results and side effects for each task.

## Risks

- Changing ordering when tasks are coupled.

## Common Smells

- [Long Function](../smells/long-function.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

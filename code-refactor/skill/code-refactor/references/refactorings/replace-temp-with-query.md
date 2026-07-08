# Replace Temp with Query

- Book chapter: 7.4 Replace Temp with Query
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A temporary value can be recalculated by a side-effect-free query.

## Preconditions

- The calculation is deterministic in the current context.
- Repeated evaluation is acceptable or cached elsewhere.

## First Safe Step

Extract the temp initializer into a query.

## Steps

1. Extract the temp initializer into a query.
2. Replace temp reads with the query.
3. Remove the temp when tests pass.

## Test Focus

- Calculation result and evaluation side effects.

## Risks

- Duplicating expensive or stateful calculations.

## Common Smells

- [Long Function](../smells/long-function.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Decompose Conditional

- Book chapter: 10.1 Decompose Conditional
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A condition or branches need intention-revealing names.

## Preconditions

- Each condition or branch can be named by intent.
- Branch behavior is covered by tests.

## First Safe Step

Extract the condition into a named query.

## Steps

1. Extract the condition into a named query.
2. Extract complex then/else logic into named functions.
3. Run branch-focused tests.

## Test Focus

- Boundary cases for each branch.

## Risks

- Changing boolean logic while extracting.

## Common Smells

- [Long Function](../smells/long-function.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

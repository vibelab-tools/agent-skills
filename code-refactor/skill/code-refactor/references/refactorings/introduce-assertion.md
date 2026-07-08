# Introduce Assertion

- Book chapter: 10.6 Introduce Assertion
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

An assumption should be made executable and checked.

## Preconditions

- The condition is a true invariant, not normal validation.
- Failures should be visible during development or runtime.

## First Safe Step

Add the narrowest assertion near the assumption.

## Steps

1. Add the narrowest assertion near the assumption.
2. Run tests for valid paths.
3. Add/adjust tests for invalid assumptions when appropriate.

## Test Focus

- Invariant boundaries and failure mode.

## Risks

- Using assertions for user-input validation.

## Common Smells

- [Comments](../smells/comments.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

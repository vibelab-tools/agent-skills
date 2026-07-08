# Substitute Algorithm

- Book chapter: 7.9 Substitute Algorithm
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A clearer algorithm can replace an existing implementation with the same behavior.

## Preconditions

- Expected behavior is well covered by examples or tests.
- The replacement algorithm handles edge cases.

## First Safe Step

Add tests for representative and edge cases.

## Steps

1. Add tests for representative and edge cases.
2. Replace the algorithm in one step.
3. Compare behavior and performance where relevant.

## Test Focus

- Edge cases, ordering, and performance-sensitive inputs.

## Risks

- Changing obscure edge behavior.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

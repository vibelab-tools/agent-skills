# Replace Nested Conditional with Guard Clauses

- Book chapter: 10.3 Replace Nested Conditional with Guard Clauses
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Nested conditionals obscure the main path.

## Preconditions

- Exceptional or edge cases can return early safely.
- Resource cleanup behavior is preserved.

## First Safe Step

Extract one edge case as a guard clause.

## Steps

1. Extract one edge case as a guard clause.
2. Flatten the remaining main path.
3. Run branch and cleanup tests.

## Test Focus

- Early returns and main happy path.

## Risks

- Skipping cleanup or finally-style behavior.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

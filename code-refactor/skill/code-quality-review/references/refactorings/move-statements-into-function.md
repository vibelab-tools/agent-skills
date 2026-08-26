# Move Statements into Function

- Book chapter: 8.3 Move Statements into Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Repeated or preparatory statements belong inside the function they support.

## Preconditions

- The moved statements are always needed by the function.
- Moving them preserves order and side effects.

## First Safe Step

Move the statements into the function body.

## Steps

1. Move the statements into the function body.
2. Remove duplicate caller-side setup.
3. Run caller tests.

## Test Focus

- Call ordering and side effects.

## Risks

- Moving caller-specific setup into shared behavior.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Replace Query with Parameter

- Book chapter: 11.6 Replace Query with Parameter
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function should receive a value rather than reach outward for it.

## Preconditions

- The caller can provide the value clearly.
- Passing the value reduces hidden dependencies.

## First Safe Step

Add the parameter while keeping the query fallback if needed.

## Steps

1. Add the parameter while keeping the query fallback if needed.
2. Pass the value from one caller.
3. Remove internal query use after migration.

## Test Focus

- Caller-provided values and dependency isolation.

## Risks

- Pushing too much calculation to callers.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

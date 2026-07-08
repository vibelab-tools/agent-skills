# Change Reference to Value

- Book chapter: 9.4 Change Reference to Value
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

An object is best treated as an immutable value.

## Preconditions

- Identity is not meaningful to callers.
- Equality can be value-based.

## First Safe Step

Make state immutable or replacement-based.

## Steps

1. Make state immutable or replacement-based.
2. Implement value equality where needed.
3. Replace mutations with new values.

## Test Focus

- Equality, copying, and update behavior.

## Risks

- Breaking identity-based caches or shared mutation expectations.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

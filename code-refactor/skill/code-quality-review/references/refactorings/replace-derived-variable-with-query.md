# Replace Derived Variable with Query

- Book chapter: 9.3 Replace Derived Variable with Query
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A stored value can be derived from existing state.

## Preconditions

- The derivation is deterministic and cheap enough.
- Writers to the derived variable are known.

## First Safe Step

Create a query that computes the value.

## Steps

1. Create a query that computes the value.
2. Replace reads with the query.
3. Remove writes and storage after tests pass.

## Test Focus

- Derived value consistency after mutations.

## Risks

- Changing caching or performance characteristics.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

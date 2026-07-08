# Separate Query from Modifier

- Book chapter: 11.1 Separate Query from Modifier
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function both returns data and changes state.

## Preconditions

- The returned query result can be computed without mutation.
- Mutation callers are identifiable.

## First Safe Step

Extract a side-effect-free query.

## Steps

1. Extract a side-effect-free query.
2. Update callers that only need the value.
3. Keep mutation in a separate command.

## Test Focus

- State changes and returned values independently.

## Risks

- Changing timing of side effects.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

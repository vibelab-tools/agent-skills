# Remove Setting Method

- Book chapter: 11.7 Remove Setting Method
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A field should not be changed after construction or controlled setup.

## Preconditions

- All setter callers are known.
- Construction or controlled update paths can supply the value.

## First Safe Step

Move one setter use into construction or a controlled command.

## Steps

1. Move one setter use into construction or a controlled command.
2. Delete the setter when no callers remain.
3. Run mutation and construction tests.

## Test Focus

- Initialization and update behavior.

## Risks

- Blocking legitimate lifecycle transitions.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)
- [Data Class](../smells/data-class.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

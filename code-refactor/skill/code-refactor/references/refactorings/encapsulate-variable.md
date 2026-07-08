# Encapsulate Variable

- Book chapter: 6.6 Encapsulate Variable
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Direct reads or writes to a variable need a controlled access boundary.

## Preconditions

- Readers and writers can be located.
- Accessor behavior can preserve current semantics first.

## First Safe Step

Introduce accessors around the variable.

## Steps

1. Introduce accessors around the variable.
2. Move direct readers and writers to the accessors.
3. Restrict direct access after tests pass.

## Test Focus

- Read/write behavior and mutation side effects.

## Risks

- Changing initialization order or bypassing existing invariants.

## Common Smells

- [Global Data](../smells/global-data.md)
- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

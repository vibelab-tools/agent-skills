# Push Down Field

- Book chapter: 12.5 Push Down Field
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A superclass field is only meaningful for some subclasses.

## Preconditions

- Only specific subclasses use the field.
- Initialization can move with the field.

## First Safe Step

Add the field to the relevant subclass.

## Steps

1. Add the field to the relevant subclass.
2. Move initialization and access.
3. Remove the superclass field after tests pass.

## Test Focus

- Subclass construction and field behavior.

## Risks

- Breaking superclass-level behavior or serialization.

## Common Smells

- [Refused Bequest](../smells/refused-bequest.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

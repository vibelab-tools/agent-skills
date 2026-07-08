# Push Down Method

- Book chapter: 12.4 Push Down Method
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A superclass method is only meaningful for some subclasses.

## Preconditions

- Only specific subclasses need the method.
- Callers can depend on the narrower type or polymorphic contract.

## First Safe Step

Move the method to the subclass that needs it.

## Steps

1. Move the method to the subclass that needs it.
2. Update references and abstract contracts as needed.
3. Run hierarchy tests.

## Test Focus

- Subclass behavior and superclass contract.

## Risks

- Breaking callers typed as the superclass.

## Common Smells

- [Refused Bequest](../smells/refused-bequest.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

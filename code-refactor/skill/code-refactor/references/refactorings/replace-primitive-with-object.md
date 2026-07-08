# Replace Primitive with Object

- Book chapter: 7.3 Replace Primitive with Object
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A primitive value represents a domain concept with rules or behavior.

## Preconditions

- The concept has a meaningful name and validation rules.
- Construction and comparison behavior can be preserved.

## First Safe Step

Create a small value object around the primitive.

## Steps

1. Create a small value object around the primitive.
2. Replace one field or parameter use.
3. Move validation or formatting into the object.

## Test Focus

- Equality, validation, formatting, and serialization.

## Risks

- Over-wrapping incidental values with no behavior.

## Common Smells

- [Primitive Obsession](../smells/primitive-obsession.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

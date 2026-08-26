# Replace Type Code with Subclasses

- Book chapter: 12.6 Replace Type Code with Subclasses
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A type code drives variant-specific behavior.

## Preconditions

- Type code values represent stable variants.
- Tests cover each variant.

## First Safe Step

Encapsulate construction for the type code.

## Steps

1. Encapsulate construction for the type code.
2. Introduce one subclass for one variant.
3. Move variant behavior out of conditionals.

## Test Focus

- Variant behavior and factory selection.

## Risks

- Creating subclasses for volatile or combinatorial states.

## Common Smells

- [Primitive Obsession](../smells/primitive-obsession.md)
- [Large Class](../smells/large-class.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

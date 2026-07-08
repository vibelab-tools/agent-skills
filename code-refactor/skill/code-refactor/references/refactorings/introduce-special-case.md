# Introduce Special Case

- Book chapter: 10.5 Introduce Special Case
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Repeated null or exceptional value handling can be represented by an object.

## Preconditions

- The special condition is well-defined.
- Callers expect consistent behavior for it.

## First Safe Step

Create the special-case object/value.

## Steps

1. Create the special-case object/value.
2. Replace one conditional check with polymorphic behavior.
3. Move remaining checks gradually.

## Test Focus

- Special case and normal case behavior.

## Risks

- Hiding real errors behind a special object.

## Common Smells

- [Temporary Field](../smells/temporary-field.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

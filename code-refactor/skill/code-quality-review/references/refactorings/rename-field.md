# Rename Field

- Book chapter: 9.2 Rename Field
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A field name hides its role or no longer matches the model.

## Preconditions

- Use symbol-aware rename where possible.
- Check persistence, JSON, reflection, and external contracts.

## First Safe Step

Rename the field mechanically.

## Steps

1. Rename the field mechanically.
2. Update explicit mappings if needed.
3. Run tests and serialization checks.

## Test Focus

- Field access and external data binding.

## Risks

- Breaking serialized names or database mapping.

## Common Smells

- [Mysterious Name](../smells/mysterious-name.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

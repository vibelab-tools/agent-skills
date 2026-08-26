# Rename Variable

- Book chapter: 6.7 Rename Variable
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A local, parameter, or variable name hides its role.

## Preconditions

- Use symbol-aware rename where available.
- Check external bindings for non-local variables.

## First Safe Step

Rename the symbol mechanically.

## Steps

1. Rename the symbol mechanically.
2. Review nearby code for improved readability.
3. Run tests for the touched scope.

## Test Focus

- Compilation, references, and dynamic binding paths.

## Risks

- Breaking string-based references or generated bindings.

## Common Smells

- [Mysterious Name](../smells/mysterious-name.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

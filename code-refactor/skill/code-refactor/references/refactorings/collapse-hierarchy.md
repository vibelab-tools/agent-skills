# Collapse Hierarchy

- Book chapter: 12.9 Collapse Hierarchy
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A superclass/subclass split no longer carries useful variation.

## Preconditions

- Hierarchy levels have no meaningful separate responsibilities.
- Callers do not depend on the distinction.

## First Safe Step

Move members into the surviving class.

## Steps

1. Move members into the surviving class.
2. Update construction and type references.
3. Remove the empty hierarchy level.

## Test Focus

- Type checks, construction, and inherited behavior.

## Risks

- Removing a useful polymorphic extension point.

## Common Smells

- [Lazy Element](../smells/lazy-element.md)
- [Speculative Generality](../smells/speculative-generality.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Inline Class

- Book chapter: 7.6 Inline Class
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A class is too small or indirect to justify its existence.

## Preconditions

- The class has no meaningful independent lifecycle.
- Callers can use the target owner directly.

## First Safe Step

Move fields and methods into the target owner.

## Steps

1. Move fields and methods into the target owner.
2. Redirect callers to the target owner.
3. Remove the inlined class after tests pass.

## Test Focus

- Callers, construction paths, and serialization shape.

## Risks

- Removing a useful API boundary or future extension point.

## Common Smells

- [Shotgun Surgery](../smells/shotgun-surgery.md)
- [Lazy Element](../smells/lazy-element.md)
- [Speculative Generality](../smells/speculative-generality.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

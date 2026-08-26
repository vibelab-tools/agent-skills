# Inline Function

- Book chapter: 6.2 Inline Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function adds indirection without clarifying behavior or protecting a boundary.

## Preconditions

- All callers are known or the function is private/internal.
- The function body is simple enough to inline safely.

## First Safe Step

Inline one caller first or all private callers when trivial.

## Steps

1. Inline one caller first or all private callers when trivial.
2. Remove the original function after callers are updated.
3. Run tests around former callers.

## Test Focus

- Caller behavior and public API compatibility.

## Risks

- Removing a useful semantic boundary or public extension point.

## Common Smells

- [Shotgun Surgery](../smells/shotgun-surgery.md)
- [Lazy Element](../smells/lazy-element.md)
- [Speculative Generality](../smells/speculative-generality.md)
- [Middle Man](../smells/middle-man.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

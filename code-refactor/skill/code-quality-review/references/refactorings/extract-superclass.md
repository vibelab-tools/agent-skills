# Extract Superclass

- Book chapter: 12.8 Extract Superclass
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several classes share behavior or interface that deserves a common abstraction.

## Preconditions

- The shared role is real and stable.
- Common behavior can move without weakening class responsibilities.

## First Safe Step

Create the superclass with one shared member.

## Steps

1. Create the superclass with one shared member.
2. Move shared behavior or fields gradually.
3. Update clients only when the abstraction helps.

## Test Focus

- All subclasses and shared behavior.

## Risks

- Creating speculative or leaky inheritance.

## Common Smells

- [Large Class](../smells/large-class.md)
- [Alternative Classes with Different Interfaces](../smells/alternative-classes-with-different-interfaces.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

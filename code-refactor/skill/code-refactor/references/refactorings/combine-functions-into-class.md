# Combine Functions into Class

- Book chapter: 6.9 Combine Functions into Class
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several functions operate on the same data and should share an owner.

## Preconditions

- The shared data group is stable.
- The functions have a cohesive responsibility.

## First Safe Step

Create the class around the shared data.

## Steps

1. Create the class around the shared data.
2. Move one function as a method.
3. Move remaining cohesive functions after tests pass.

## Test Focus

- Function outputs before and after moving into the class.

## Risks

- Creating a large procedural class without cohesion.

## Common Smells

- [Long Parameter List](../smells/long-parameter-list.md)
- [Mutable Data](../smells/mutable-data.md)
- [Shotgun Surgery](../smells/shotgun-surgery.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

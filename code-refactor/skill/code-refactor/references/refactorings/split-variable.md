# Split Variable

- Book chapter: 9.1 Split Variable
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

One variable represents multiple meanings over time.

## Preconditions

- Each meaning can be named separately.
- Assignments are understood.

## First Safe Step

Introduce a new variable for one meaning.

## Steps

1. Introduce a new variable for one meaning.
2. Replace reads for that meaning.
3. Repeat until each variable has one purpose.

## Test Focus

- Assignment order and computed values.

## Risks

- Changing lifetime or stale value behavior.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

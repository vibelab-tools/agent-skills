# Move Field

- Book chapter: 8.2 Move Field
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A field is used more naturally by another owner.

## Preconditions

- The target owner lifecycle matches the field.
- All reads and writes can be found.

## First Safe Step

Add the field to the target owner.

## Steps

1. Add the field to the target owner.
2. Synchronize or redirect one access path.
3. Remove the original field after callers migrate.

## Test Focus

- Reads, writes, initialization, and persistence mapping.

## Risks

- Breaking object identity, persistence, or initialization order.

## Common Smells

- [Shotgun Surgery](../smells/shotgun-surgery.md)
- [Insider Trading](../smells/insider-trading.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Remove Subclass

- Book chapter: 12.7 Remove Subclass
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A subclass adds no meaningful behavior or distinction.

## Preconditions

- The subclass has no required separate lifecycle or API role.
- Callers can use the superclass or another variant.

## First Safe Step

Move needed behavior or data up or sideways.

## Steps

1. Move needed behavior or data up or sideways.
2. Replace construction of the subclass.
3. Delete the subclass after callers migrate.

## Test Focus

- Construction and type checks involving the subclass.

## Risks

- Breaking type-based external contracts.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Consolidate Conditional Expression

- Book chapter: 10.2 Consolidate Conditional Expression
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several checks lead to the same result and should express one concept.

## Preconditions

- The checks have the same outcome and no order-dependent side effects.
- A combined concept name exists.

## First Safe Step

Combine equivalent checks into one expression or query.

## Steps

1. Combine equivalent checks into one expression or query.
2. Name the combined condition.
3. Run tests for each former path.

## Test Focus

- All former condition paths.

## Risks

- Changing short-circuit side effects.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

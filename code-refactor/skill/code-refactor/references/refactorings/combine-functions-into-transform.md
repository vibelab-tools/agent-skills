# Combine Functions into Transform

- Book chapter: 6.10 Combine Functions into Transform
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several functions enrich or transform the same input data.

## Preconditions

- The transformation output is explicit.
- Callers can consume the transformed result without hidden mutation.

## First Safe Step

Create a transform that returns the enriched data.

## Steps

1. Create a transform that returns the enriched data.
2. Move one calculation into the transform.
3. Replace caller-side repeated calculations gradually.

## Test Focus

- Transformed data fields and unchanged source data behavior.

## Risks

- Mixing mutation and transformation semantics.

## Common Smells

- [Mutable Data](../smells/mutable-data.md)
- [Shotgun Surgery](../smells/shotgun-surgery.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

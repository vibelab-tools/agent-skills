# Change Value to Reference

- Book chapter: 9.5 Change Value to Reference
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several equal values should refer to one shared identity.

## Preconditions

- Identity matters in the domain.
- A lookup or repository owner exists.

## First Safe Step

Introduce a lookup for canonical instances.

## Steps

1. Introduce a lookup for canonical instances.
2. Replace direct construction on one path.
3. Move remaining construction through the lookup.

## Test Focus

- Identity equality, lifecycle, and caching.

## Risks

- Introducing hidden global state or stale references.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

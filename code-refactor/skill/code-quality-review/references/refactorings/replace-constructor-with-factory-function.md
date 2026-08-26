# Replace Constructor with Factory Function

- Book chapter: 11.8 Replace Constructor with Factory Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Object creation needs naming, variants, caching, or preconditions.

## Preconditions

- Factory naming clarifies construction intent.
- Callers can be migrated safely.

## First Safe Step

Add a factory that delegates to the constructor.

## Steps

1. Add a factory that delegates to the constructor.
2. Move callers to the factory.
3. Restrict constructor visibility if appropriate.

## Test Focus

- Construction variants and defaults.

## Risks

- Hiding expensive or surprising creation logic.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

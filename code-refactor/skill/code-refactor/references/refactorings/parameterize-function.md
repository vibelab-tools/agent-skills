# Parameterize Function

- Book chapter: 11.2 Parameterize Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Similar functions differ only by values or small operations.

## Preconditions

- The varying value has a clear parameter name.
- Call sites stay readable with the parameter.

## First Safe Step

Add the parameter to one function.

## Steps

1. Add the parameter to one function.
2. Redirect duplicate functions to the parameterized version.
3. Remove duplicates after tests pass.

## Test Focus

- Each former function behavior.

## Risks

- Adding flag-like parameters that obscure intent.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

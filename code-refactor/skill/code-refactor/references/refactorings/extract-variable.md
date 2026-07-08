# Extract Variable

- Book chapter: 6.3 Extract Variable
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A complex expression needs an intention-revealing name.

## Preconditions

- Expression evaluation has no hidden side effects.
- Variable scope can stay narrow.

## First Safe Step

Introduce a well-named local for the expression.

## Steps

1. Introduce a well-named local for the expression.
2. Replace the expression at the narrowest useful scope.
3. Run tests around the containing function.

## Test Focus

- Evaluation order and expression result.

## Risks

- Changing lazy evaluation or widening mutable state.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Inline Variable

- Book chapter: 6.4 Inline Variable
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A variable obscures rather than clarifies a simple expression.

## Preconditions

- The initializer is simple and side-effect free.
- The variable is not part of debugging or public API behavior.

## First Safe Step

Replace one use with the initializer expression.

## Steps

1. Replace one use with the initializer expression.
2. Remove the variable after all uses are replaced.
3. Run tests around the containing function.

## Test Focus

- Expression behavior and evaluation count.

## Risks

- Duplicating expensive or side-effecting expressions.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Replace Parameter with Query

- Book chapter: 11.5 Replace Parameter with Query
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A parameter value can be obtained from another available object or query.

## Preconditions

- The query is available at the callee without new hidden dependencies.
- The query has no unwanted side effects.

## First Safe Step

Add the query inside the function.

## Steps

1. Add the query inside the function.
2. Stop passing the redundant parameter from one caller.
3. Remove the parameter after all callers migrate.

## Test Focus

- Caller behavior and query result.

## Risks

- Making dependencies less explicit.

## Common Smells

- [Long Parameter List](../smells/long-parameter-list.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

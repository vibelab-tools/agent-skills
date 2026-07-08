# Remove Flag Argument

- Book chapter: 11.3 Remove Flag Argument
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A parameter switches between distinct behaviors.

## Preconditions

- Each behavior can have an intention-revealing entrypoint.
- Callers are known.

## First Safe Step

Create explicit functions for each behavior.

## Steps

1. Create explicit functions for each behavior.
2. Update callers to the explicit functions.
3. Remove the flag argument.

## Test Focus

- Both flag paths and caller readability.

## Risks

- Replacing one unclear API with several unclear names.

## Common Smells

- [Long Parameter List](../smells/long-parameter-list.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

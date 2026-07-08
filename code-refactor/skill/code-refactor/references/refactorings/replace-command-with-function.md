# Replace Command with Function

- Book chapter: 11.10 Replace Command with Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A command object no longer needs state or extensibility.

## Preconditions

- The command has one simple operation.
- Callers do not rely on command identity or lifecycle.

## First Safe Step

Introduce an equivalent function.

## Steps

1. Introduce an equivalent function.
2. Move callers to the function.
3. Remove the command class after tests pass.

## Test Focus

- Callers and side effects of the command.

## Risks

- Removing a useful extension or lifecycle boundary.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

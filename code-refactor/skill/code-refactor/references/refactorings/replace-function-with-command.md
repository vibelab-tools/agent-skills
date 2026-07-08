# Replace Function with Command

- Book chapter: 11.9 Replace Function with Command
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function is hard to decompose because it carries many locals or phases.

## Preconditions

- Behavior is characterized by tests.
- Command state can remain private to the operation.

## First Safe Step

Create a command object with the function inputs.

## Steps

1. Create a command object with the function inputs.
2. Move the function body into an execute method.
3. Extract phases from command state.

## Test Focus

- Original function behavior and command lifecycle.

## Risks

- Creating mutable command state without simplifying behavior.

## Common Smells

- [Long Function](../smells/long-function.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Replace Conditional with Polymorphism

- Book chapter: 10.4 Replace Conditional with Polymorphism
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Repeated branches vary behavior by stable type or state.

## Preconditions

- Variants are stable domain concepts.
- Tests cover every branch.

## First Safe Step

Extract branch behavior into named operations.

## Steps

1. Extract branch behavior into named operations.
2. Introduce one variant type or strategy.
3. Move one branch at a time into variant behavior.

## Test Focus

- Each variant and default/error behavior.

## Risks

- Creating a class hierarchy for unstable branches.

## Common Smells

- [Long Function](../smells/long-function.md)
- [Primitive Obsession](../smells/primitive-obsession.md)
- [Repeated Switches](../smells/repeated-switches.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

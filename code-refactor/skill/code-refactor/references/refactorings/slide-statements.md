# Slide Statements

- Book chapter: 8.6 Slide Statements
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Nearby statements must be grouped to reveal extraction or sequencing.

## Preconditions

- Moving statements does not change data dependencies.
- Tests cover the containing function.

## First Safe Step

Move one independent statement near related statements.

## Steps

1. Move one independent statement near related statements.
2. Run tests or compile after each move.
3. Extract or simplify after grouping is clear.

## Test Focus

- Evaluation order and variable lifetimes.

## Risks

- Crossing dependency or side-effect boundaries.

## Common Smells

- [Duplicated Code](../smells/duplicated-code.md)
- [Mutable Data](../smells/mutable-data.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

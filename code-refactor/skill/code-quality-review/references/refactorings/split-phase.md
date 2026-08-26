# Split Phase

- Book chapter: 6.11 Split Phase
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

One flow naturally separates into sequential phases with a clear handoff.

## Preconditions

- The phase boundary and intermediate data are identifiable.
- Tests cover the end-to-end flow.

## First Safe Step

Introduce an explicit intermediate value between phases.

## Steps

1. Introduce an explicit intermediate value between phases.
2. Extract the first phase.
3. Extract the second phase and rerun tests.

## Test Focus

- Intermediate data shape and end-to-end output.

## Risks

- Choosing an artificial boundary that increases coupling.

## Common Smells

- [Divergent Change](../smells/divergent-change.md)
- [Shotgun Surgery](../smells/shotgun-surgery.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

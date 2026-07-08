# Replace Loop with Pipeline

- Book chapter: 8.8 Replace Loop with Pipeline
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A loop expresses map/filter/reduce/query behavior better as a pipeline.

## Preconditions

- Iteration side effects are isolated.
- The language pipeline preserves needed ordering.

## First Safe Step

Identify the pipeline stages.

## Steps

1. Identify the pipeline stages.
2. Replace the loop with the simplest equivalent pipeline.
3. Run tests for empty and multi-item inputs.

## Test Focus

- Ordering, short-circuit behavior, and empty collections.

## Risks

- Obscuring imperative side effects or performance.

## Common Smells

- [Loops](../smells/loops.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

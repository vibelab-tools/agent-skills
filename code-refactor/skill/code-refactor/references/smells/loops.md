# Loops

- Book chapter: Chapter 3, Loops
- Typical evidence: loop used for transform/filter/search where collection pipeline is clearer
- Primary refactoring: [Replace Loop with Pipeline](../refactorings/replace-loop-with-pipeline.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Replace Loop with Pipeline](../refactorings/replace-loop-with-pipeline.md)

- Use when: A loop expresses map/filter/reduce/query behavior better as a pipeline.
- First safe step: Identify the pipeline stages.
- Main risk: Obscuring imperative side effects or performance.

## Verification Focus

- Ordering, short-circuit behavior, and empty collections.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Replace Loop with Pipeline](../refactorings/replace-loop-with-pipeline.md)

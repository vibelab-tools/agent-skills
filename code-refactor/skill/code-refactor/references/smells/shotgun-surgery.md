# Shotgun Surgery

- Book chapter: Chapter 3, Shotgun Surgery
- Typical evidence: same operation repeated across owners/files, co-change history
- Primary refactoring: [Move Function](../refactorings/move-function.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

### 2. [Move Field](../refactorings/move-field.md)

- Use when: A field is used more naturally by another owner.
- First safe step: Add the field to the target owner.
- Main risk: Breaking object identity, persistence, or initialization order.

### 3. [Combine Functions into Class](../refactorings/combine-functions-into-class.md)

- Use when: Several functions operate on the same data and should share an owner.
- First safe step: Create the class around the shared data.
- Main risk: Creating a large procedural class without cohesion.

### 4. [Combine Functions into Transform](../refactorings/combine-functions-into-transform.md)

- Use when: Several functions enrich or transform the same input data.
- First safe step: Create a transform that returns the enriched data.
- Main risk: Mixing mutation and transformation semantics.

### 5. [Split Phase](../refactorings/split-phase.md)

- Use when: One flow naturally separates into sequential phases with a clear handoff.
- First safe step: Introduce an explicit intermediate value between phases.
- Main risk: Choosing an artificial boundary that increases coupling.

### 6. [Inline Function](../refactorings/inline-function.md)

- Use when: A function adds indirection without clarifying behavior or protecting a boundary.
- First safe step: Inline one caller first or all private callers when trivial.
- Main risk: Removing a useful semantic boundary or public extension point.

### 7. [Inline Class](../refactorings/inline-class.md)

- Use when: A class is too small or indirect to justify its existence.
- First safe step: Move fields and methods into the target owner.
- Main risk: Removing a useful API boundary or future extension point.

## Verification Focus

- Current callers and target owner behavior.
- Reads, writes, initialization, and persistence mapping.
- Function outputs before and after moving into the class.
- Transformed data fields and unchanged source data behavior.
- Intermediate data shape and end-to-end output.
- Caller behavior and public API compatibility.
- Callers, construction paths, and serialization shape.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Move Function](../refactorings/move-function.md)
- [Move Field](../refactorings/move-field.md)
- [Combine Functions into Class](../refactorings/combine-functions-into-class.md)
- [Combine Functions into Transform](../refactorings/combine-functions-into-transform.md)
- [Split Phase](../refactorings/split-phase.md)
- [Inline Function](../refactorings/inline-function.md)
- [Inline Class](../refactorings/inline-class.md)

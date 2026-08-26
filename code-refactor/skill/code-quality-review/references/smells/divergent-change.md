# Divergent Change

- Book chapter: Chapter 3, Divergent Change
- Typical evidence: one class/module has multiple independent concern clusters
- Primary refactoring: [Split Phase](../refactorings/split-phase.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Split Phase](../refactorings/split-phase.md)

- Use when: One flow naturally separates into sequential phases with a clear handoff.
- First safe step: Introduce an explicit intermediate value between phases.
- Main risk: Choosing an artificial boundary that increases coupling.

### 2. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

### 3. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

### 4. [Extract Class](../refactorings/extract-class.md)

- Use when: Fields and methods form a cohesive responsibility inside a larger owner.
- First safe step: Create the new class with the cohesive data.
- Main risk: Splitting data from behavior or creating chatty coupling.

## Verification Focus

- Intermediate data shape and end-to-end output.
- Current callers and target owner behavior.
- Return values, side effects, and branch behavior around the extracted block.
- Behavior using moved fields and original class integration.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Split Phase](../refactorings/split-phase.md)
- [Move Function](../refactorings/move-function.md)
- [Extract Function](../refactorings/extract-function.md)
- [Extract Class](../refactorings/extract-class.md)

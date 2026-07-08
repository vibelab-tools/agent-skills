# Temporary Field

- Book chapter: Chapter 3, Temporary Field
- Typical evidence: field only valid for one calculation path
- Primary refactoring: [Extract Class](../refactorings/extract-class.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Class](../refactorings/extract-class.md)

- Use when: Fields and methods form a cohesive responsibility inside a larger owner.
- First safe step: Create the new class with the cohesive data.
- Main risk: Splitting data from behavior or creating chatty coupling.

### 2. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

### 3. [Introduce Special Case](../refactorings/introduce-special-case.md)

- Use when: Repeated null or exceptional value handling can be represented by an object.
- First safe step: Create the special-case object/value.
- Main risk: Hiding real errors behind a special object.

## Verification Focus

- Behavior using moved fields and original class integration.
- Current callers and target owner behavior.
- Special case and normal case behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Class](../refactorings/extract-class.md)
- [Move Function](../refactorings/move-function.md)
- [Introduce Special Case](../refactorings/introduce-special-case.md)

# Data Class

- Book chapter: Chapter 3, Data Class
- Typical evidence: fields/accessors dominate, little behavior
- Primary refactoring: [Encapsulate Record](../refactorings/encapsulate-record.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Encapsulate Record](../refactorings/encapsulate-record.md)

- Use when: A record or loose data structure needs controlled access or behavior.
- First safe step: Wrap the record in an object with accessors.
- Main risk: Breaking callers that depend on raw data shape.

### 2. [Remove Setting Method](../refactorings/remove-setting-method.md)

- Use when: A field should not be changed after construction or controlled setup.
- First safe step: Move one setter use into construction or a controlled command.
- Main risk: Blocking legitimate lifecycle transitions.

### 3. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

### 4. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

## Verification Focus

- Field access, serialization, and construction behavior.
- Initialization and update behavior.
- Current callers and target owner behavior.
- Return values, side effects, and branch behavior around the extracted block.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Encapsulate Record](../refactorings/encapsulate-record.md)
- [Remove Setting Method](../refactorings/remove-setting-method.md)
- [Move Function](../refactorings/move-function.md)
- [Extract Function](../refactorings/extract-function.md)

# Alternative Classes with Different Interfaces

- Book chapter: Chapter 3, Alternative Classes with Different Interfaces
- Typical evidence: similar roles, different method names/signatures
- Primary refactoring: [Change Function Declaration](../refactorings/change-function-declaration.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Change Function Declaration](../refactorings/change-function-declaration.md)

- Use when: A function name, parameter list, or signature no longer matches its role.
- First safe step: Introduce the new declaration while preserving old behavior.
- Main risk: Breaking public callers, reflection, routing, or serialization hooks.

### 2. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

### 3. [Extract Superclass](../refactorings/extract-superclass.md)

- Use when: Several classes share behavior or interface that deserves a common abstraction.
- First safe step: Create the superclass with one shared member.
- Main risk: Creating speculative or leaky inheritance.

## Verification Focus

- Caller compatibility and external API usage.
- Current callers and target owner behavior.
- All subclasses and shared behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Change Function Declaration](../refactorings/change-function-declaration.md)
- [Move Function](../refactorings/move-function.md)
- [Extract Superclass](../refactorings/extract-superclass.md)

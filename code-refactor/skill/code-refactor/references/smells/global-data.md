# Global Data

- Book chapter: Chapter 3, Global Data
- Typical evidence: public/static/module mutable state
- Primary refactoring: [Encapsulate Variable](../refactorings/encapsulate-variable.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Encapsulate Variable](../refactorings/encapsulate-variable.md)

- Use when: Direct reads or writes to a variable need a controlled access boundary.
- First safe step: Introduce accessors around the variable.
- Main risk: Changing initialization order or bypassing existing invariants.

### 2. [Move Function](../refactorings/move-function.md)

- Use when: A function uses another owner data or responsibility more than its current owner.
- First safe step: Extract the movable logic if needed.
- Main risk: Moving behavior away from required state or API boundaries.

## Verification Focus

- Read/write behavior and mutation side effects.
- Current callers and target owner behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Encapsulate Variable](../refactorings/encapsulate-variable.md)
- [Move Function](../refactorings/move-function.md)

# Refused Bequest

- Book chapter: Chapter 3, Refused Bequest
- Typical evidence: subclass rejects inherited contract
- Primary refactoring: [Push Down Method](../refactorings/push-down-method.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Push Down Method](../refactorings/push-down-method.md)

- Use when: A superclass method is only meaningful for some subclasses.
- First safe step: Move the method to the subclass that needs it.
- Main risk: Breaking callers typed as the superclass.

### 2. [Push Down Field](../refactorings/push-down-field.md)

- Use when: A superclass field is only meaningful for some subclasses.
- First safe step: Add the field to the relevant subclass.
- Main risk: Breaking superclass-level behavior or serialization.

### 3. [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)

- Use when: Subclass variation is better represented by composition.
- First safe step: Add a delegate field for one variant behavior.
- Main risk: Duplicating behavior during migration.

### 4. [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)

- Use when: A superclass relationship exposes too much or models the wrong relationship.
- First safe step: Add a delegate to the former superclass behavior.
- Main risk: Breaking substitutability or superclass contract assumptions.

## Verification Focus

- Subclass behavior and superclass contract.
- Subclass construction and field behavior.
- Subclass public behavior and delegation paths.
- Inherited behavior, public API, and construction.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Push Down Method](../refactorings/push-down-method.md)
- [Push Down Field](../refactorings/push-down-field.md)
- [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)
- [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)

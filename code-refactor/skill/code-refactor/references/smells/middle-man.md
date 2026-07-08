# Middle Man

- Book chapter: Chapter 3, Middle Man
- Typical evidence: most methods forward to one delegate
- Primary refactoring: [Remove Middle Man](../refactorings/remove-middle-man.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Remove Middle Man](../refactorings/remove-middle-man.md)

- Use when: A class mostly forwards calls and adds no useful policy.
- First safe step: Redirect one caller to the delegate.
- Main risk: Exposing internals or breaking facade boundaries.

### 2. [Inline Function](../refactorings/inline-function.md)

- Use when: A function adds indirection without clarifying behavior or protecting a boundary.
- First safe step: Inline one caller first or all private callers when trivial.
- Main risk: Removing a useful semantic boundary or public extension point.

### 3. [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)

- Use when: A superclass relationship exposes too much or models the wrong relationship.
- First safe step: Add a delegate to the former superclass behavior.
- Main risk: Breaking substitutability or superclass contract assumptions.

### 4. [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)

- Use when: Subclass variation is better represented by composition.
- First safe step: Add a delegate field for one variant behavior.
- Main risk: Duplicating behavior during migration.

## Verification Focus

- Caller behavior and public API compatibility.
- Inherited behavior, public API, and construction.
- Subclass public behavior and delegation paths.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Remove Middle Man](../refactorings/remove-middle-man.md)
- [Inline Function](../refactorings/inline-function.md)
- [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)
- [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)

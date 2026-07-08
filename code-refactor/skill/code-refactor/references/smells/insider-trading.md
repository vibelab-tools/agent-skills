# Insider Trading

- Book chapter: Chapter 3, Insider Trading
- Typical evidence: classes access internal details or reciprocal deep structure
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

### 3. [Hide Delegate](../refactorings/hide-delegate.md)

- Use when: Clients know too much about a delegate chain.
- First safe step: Add one forwarding method on the owner for the common need.
- Main risk: Creating a Middle Man by forwarding too much.

### 4. [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)

- Use when: Subclass variation is better represented by composition.
- First safe step: Add a delegate field for one variant behavior.
- Main risk: Duplicating behavior during migration.

### 5. [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)

- Use when: A superclass relationship exposes too much or models the wrong relationship.
- First safe step: Add a delegate to the former superclass behavior.
- Main risk: Breaking substitutability or superclass contract assumptions.

## Verification Focus

- Current callers and target owner behavior.
- Reads, writes, initialization, and persistence mapping.
- Caller behavior and delegation target behavior.
- Subclass public behavior and delegation paths.
- Inherited behavior, public API, and construction.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Move Function](../refactorings/move-function.md)
- [Move Field](../refactorings/move-field.md)
- [Hide Delegate](../refactorings/hide-delegate.md)
- [Replace Subclass with Delegate](../refactorings/replace-subclass-with-delegate.md)
- [Replace Superclass with Delegate](../refactorings/replace-superclass-with-delegate.md)

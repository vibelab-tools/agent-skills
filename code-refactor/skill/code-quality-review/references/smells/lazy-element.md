# Lazy Element

- Book chapter: Chapter 3, Lazy Element
- Typical evidence: empty/thin/pass-through class/function
- Primary refactoring: [Inline Function](../refactorings/inline-function.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Inline Function](../refactorings/inline-function.md)

- Use when: A function adds indirection without clarifying behavior or protecting a boundary.
- First safe step: Inline one caller first or all private callers when trivial.
- Main risk: Removing a useful semantic boundary or public extension point.

### 2. [Inline Class](../refactorings/inline-class.md)

- Use when: A class is too small or indirect to justify its existence.
- First safe step: Move fields and methods into the target owner.
- Main risk: Removing a useful API boundary or future extension point.

### 3. [Collapse Hierarchy](../refactorings/collapse-hierarchy.md)

- Use when: A superclass/subclass split no longer carries useful variation.
- First safe step: Move members into the surviving class.
- Main risk: Removing a useful polymorphic extension point.

## Verification Focus

- Caller behavior and public API compatibility.
- Callers, construction paths, and serialization shape.
- Type checks, construction, and inherited behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Inline Function](../refactorings/inline-function.md)
- [Inline Class](../refactorings/inline-class.md)
- [Collapse Hierarchy](../refactorings/collapse-hierarchy.md)

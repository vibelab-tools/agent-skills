# Speculative Generality

- Book chapter: Chapter 3, Speculative Generality
- Typical evidence: unused abstraction, future/generic hooks with weak usage
- Primary refactoring: [Collapse Hierarchy](../refactorings/collapse-hierarchy.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Collapse Hierarchy](../refactorings/collapse-hierarchy.md)

- Use when: A superclass/subclass split no longer carries useful variation.
- First safe step: Move members into the surviving class.
- Main risk: Removing a useful polymorphic extension point.

### 2. [Inline Function](../refactorings/inline-function.md)

- Use when: A function adds indirection without clarifying behavior or protecting a boundary.
- First safe step: Inline one caller first or all private callers when trivial.
- Main risk: Removing a useful semantic boundary or public extension point.

### 3. [Inline Class](../refactorings/inline-class.md)

- Use when: A class is too small or indirect to justify its existence.
- First safe step: Move fields and methods into the target owner.
- Main risk: Removing a useful API boundary or future extension point.

### 4. [Change Function Declaration](../refactorings/change-function-declaration.md)

- Use when: A function name, parameter list, or signature no longer matches its role.
- First safe step: Introduce the new declaration while preserving old behavior.
- Main risk: Breaking public callers, reflection, routing, or serialization hooks.

### 5. [Remove Dead Code](../refactorings/remove-dead-code.md)

- Use when: Code has no production caller or effect.
- First safe step: Remove the smallest unused element.
- Main risk: Deleting dynamically referenced code.

## Verification Focus

- Type checks, construction, and inherited behavior.
- Caller behavior and public API compatibility.
- Callers, construction paths, and serialization shape.
- Caller compatibility and external API usage.
- Build, public entrypoints, and integration configuration.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Collapse Hierarchy](../refactorings/collapse-hierarchy.md)
- [Inline Function](../refactorings/inline-function.md)
- [Inline Class](../refactorings/inline-class.md)
- [Change Function Declaration](../refactorings/change-function-declaration.md)
- [Remove Dead Code](../refactorings/remove-dead-code.md)

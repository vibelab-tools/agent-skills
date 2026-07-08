# Repeated Switches

- Book chapter: Chapter 3, Repeated Switches
- Typical evidence: repeated switch/match/if-dispatch on same selector
- Primary refactoring: [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)

- Use when: Repeated branches vary behavior by stable type or state.
- First safe step: Extract branch behavior into named operations.
- Main risk: Creating a class hierarchy for unstable branches.

## Verification Focus

- Each variant and default/error behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)

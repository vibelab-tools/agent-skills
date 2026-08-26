# Large Class

- Book chapter: Chapter 3, Large Class
- Typical evidence: many fields/methods/lines, low cohesion clusters
- Primary refactoring: [Extract Class](../refactorings/extract-class.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Class](../refactorings/extract-class.md)

- Use when: Fields and methods form a cohesive responsibility inside a larger owner.
- First safe step: Create the new class with the cohesive data.
- Main risk: Splitting data from behavior or creating chatty coupling.

### 2. [Extract Superclass](../refactorings/extract-superclass.md)

- Use when: Several classes share behavior or interface that deserves a common abstraction.
- First safe step: Create the superclass with one shared member.
- Main risk: Creating speculative or leaky inheritance.

### 3. [Replace Type Code with Subclasses](../refactorings/replace-type-code-with-subclasses.md)

- Use when: A type code drives variant-specific behavior.
- First safe step: Encapsulate construction for the type code.
- Main risk: Creating subclasses for volatile or combinatorial states.

## Verification Focus

- Behavior using moved fields and original class integration.
- All subclasses and shared behavior.
- Variant behavior and factory selection.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Class](../refactorings/extract-class.md)
- [Extract Superclass](../refactorings/extract-superclass.md)
- [Replace Type Code with Subclasses](../refactorings/replace-type-code-with-subclasses.md)

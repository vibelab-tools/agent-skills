# Data Clumps

- Book chapter: Chapter 3, Data Clumps
- Typical evidence: same parameter/field/argument group repeats
- Primary refactoring: [Extract Class](../refactorings/extract-class.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Class](../refactorings/extract-class.md)

- Use when: Fields and methods form a cohesive responsibility inside a larger owner.
- First safe step: Create the new class with the cohesive data.
- Main risk: Splitting data from behavior or creating chatty coupling.

### 2. [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)

- Use when: Several parameters travel together as one domain concept.
- First safe step: Create the parameter object and pass it alongside old parameters.
- Main risk: Creating a vague data bag instead of a domain concept.

### 3. [Preserve Whole Object](../refactorings/preserve-whole-object.md)

- Use when: Callers pass many values extracted from the same object.
- First safe step: Add an overload or path accepting the whole object.
- Main risk: Increasing coupling to a broad object.

## Verification Focus

- Behavior using moved fields and original class integration.
- Call-site behavior and object construction defaults.
- Call-site behavior and object field usage.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Class](../refactorings/extract-class.md)
- [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)
- [Preserve Whole Object](../refactorings/preserve-whole-object.md)

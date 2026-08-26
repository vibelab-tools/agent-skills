# Mysterious Name

- Book chapter: Chapter 3, Mysterious Name
- Typical evidence: short/generic names, unclear symbols
- Primary refactoring: [Change Function Declaration](../refactorings/change-function-declaration.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Change Function Declaration](../refactorings/change-function-declaration.md)

- Use when: A function name, parameter list, or signature no longer matches its role.
- First safe step: Introduce the new declaration while preserving old behavior.
- Main risk: Breaking public callers, reflection, routing, or serialization hooks.

### 2. [Rename Variable](../refactorings/rename-variable.md)

- Use when: A local, parameter, or variable name hides its role.
- First safe step: Rename the symbol mechanically.
- Main risk: Breaking string-based references or generated bindings.

### 3. [Rename Field](../refactorings/rename-field.md)

- Use when: A field name hides its role or no longer matches the model.
- First safe step: Rename the field mechanically.
- Main risk: Breaking serialized names or database mapping.

## Verification Focus

- Caller compatibility and external API usage.
- Compilation, references, and dynamic binding paths.
- Field access and external data binding.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Change Function Declaration](../refactorings/change-function-declaration.md)
- [Rename Variable](../refactorings/rename-variable.md)
- [Rename Field](../refactorings/rename-field.md)

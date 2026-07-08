# Comments

- Book chapter: Chapter 3, Comments
- Typical evidence: comments explain complex code, TODO/HACK/workaround
- Primary refactoring: [Extract Function](../refactorings/extract-function.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

### 2. [Change Function Declaration](../refactorings/change-function-declaration.md)

- Use when: A function name, parameter list, or signature no longer matches its role.
- First safe step: Introduce the new declaration while preserving old behavior.
- Main risk: Breaking public callers, reflection, routing, or serialization hooks.

### 3. [Introduce Assertion](../refactorings/introduce-assertion.md)

- Use when: An assumption should be made executable and checked.
- First safe step: Add the narrowest assertion near the assumption.
- Main risk: Using assertions for user-input validation.

## Verification Focus

- Return values, side effects, and branch behavior around the extracted block.
- Caller compatibility and external API usage.
- Invariant boundaries and failure mode.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Function](../refactorings/extract-function.md)
- [Change Function Declaration](../refactorings/change-function-declaration.md)
- [Introduce Assertion](../refactorings/introduce-assertion.md)

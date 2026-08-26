# Long Parameter List

- Book chapter: Chapter 3, Long Parameter List
- Typical evidence: many parameters, primitive-heavy or grouped names
- Primary refactoring: [Replace Parameter with Query](../refactorings/replace-parameter-with-query.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Replace Parameter with Query](../refactorings/replace-parameter-with-query.md)

- Use when: A parameter value can be obtained from another available object or query.
- First safe step: Add the query inside the function.
- Main risk: Making dependencies less explicit.

### 2. [Preserve Whole Object](../refactorings/preserve-whole-object.md)

- Use when: Callers pass many values extracted from the same object.
- First safe step: Add an overload or path accepting the whole object.
- Main risk: Increasing coupling to a broad object.

### 3. [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)

- Use when: Several parameters travel together as one domain concept.
- First safe step: Create the parameter object and pass it alongside old parameters.
- Main risk: Creating a vague data bag instead of a domain concept.

### 4. [Remove Flag Argument](../refactorings/remove-flag-argument.md)

- Use when: A parameter switches between distinct behaviors.
- First safe step: Create explicit functions for each behavior.
- Main risk: Replacing one unclear API with several unclear names.

### 5. [Combine Functions into Class](../refactorings/combine-functions-into-class.md)

- Use when: Several functions operate on the same data and should share an owner.
- First safe step: Create the class around the shared data.
- Main risk: Creating a large procedural class without cohesion.

## Verification Focus

- Caller behavior and query result.
- Call-site behavior and object field usage.
- Call-site behavior and object construction defaults.
- Both flag paths and caller readability.
- Function outputs before and after moving into the class.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Replace Parameter with Query](../refactorings/replace-parameter-with-query.md)
- [Preserve Whole Object](../refactorings/preserve-whole-object.md)
- [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)
- [Remove Flag Argument](../refactorings/remove-flag-argument.md)
- [Combine Functions into Class](../refactorings/combine-functions-into-class.md)

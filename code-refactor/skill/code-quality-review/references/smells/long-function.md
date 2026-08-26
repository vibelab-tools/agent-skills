# Long Function

- Book chapter: Chapter 3, Long Function
- Typical evidence: high physical/cognitive/cyclomatic size
- Primary refactoring: [Extract Function](../refactorings/extract-function.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

### 2. [Replace Temp with Query](../refactorings/replace-temp-with-query.md)

- Use when: A temporary value can be recalculated by a side-effect-free query.
- First safe step: Extract the temp initializer into a query.
- Main risk: Duplicating expensive or stateful calculations.

### 3. [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)

- Use when: Several parameters travel together as one domain concept.
- First safe step: Create the parameter object and pass it alongside old parameters.
- Main risk: Creating a vague data bag instead of a domain concept.

### 4. [Preserve Whole Object](../refactorings/preserve-whole-object.md)

- Use when: Callers pass many values extracted from the same object.
- First safe step: Add an overload or path accepting the whole object.
- Main risk: Increasing coupling to a broad object.

### 5. [Replace Function with Command](../refactorings/replace-function-with-command.md)

- Use when: A function is hard to decompose because it carries many locals or phases.
- First safe step: Create a command object with the function inputs.
- Main risk: Creating mutable command state without simplifying behavior.

### 6. [Decompose Conditional](../refactorings/decompose-conditional.md)

- Use when: A condition or branches need intention-revealing names.
- First safe step: Extract the condition into a named query.
- Main risk: Changing boolean logic while extracting.

### 7. [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)

- Use when: Repeated branches vary behavior by stable type or state.
- First safe step: Extract branch behavior into named operations.
- Main risk: Creating a class hierarchy for unstable branches.

### 8. [Split Loop](../refactorings/split-loop.md)

- Use when: One loop performs multiple independent tasks.
- First safe step: Copy the loop for one task.
- Main risk: Changing ordering when tasks are coupled.

## Verification Focus

- Return values, side effects, and branch behavior around the extracted block.
- Calculation result and evaluation side effects.
- Call-site behavior and object construction defaults.
- Call-site behavior and object field usage.
- Original function behavior and command lifecycle.
- Boundary cases for each branch.
- Each variant and default/error behavior.
- Accumulated results and side effects for each task.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Function](../refactorings/extract-function.md)
- [Replace Temp with Query](../refactorings/replace-temp-with-query.md)
- [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)
- [Preserve Whole Object](../refactorings/preserve-whole-object.md)
- [Replace Function with Command](../refactorings/replace-function-with-command.md)
- [Decompose Conditional](../refactorings/decompose-conditional.md)
- [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)
- [Split Loop](../refactorings/split-loop.md)

# Primitive Obsession

- Book chapter: Chapter 3, Primitive Obsession
- Typical evidence: domain concepts represented by many primitives
- Primary refactoring: [Replace Primitive with Object](../refactorings/replace-primitive-with-object.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Replace Primitive with Object](../refactorings/replace-primitive-with-object.md)

- Use when: A primitive value represents a domain concept with rules or behavior.
- First safe step: Create a small value object around the primitive.
- Main risk: Over-wrapping incidental values with no behavior.

### 2. [Replace Type Code with Subclasses](../refactorings/replace-type-code-with-subclasses.md)

- Use when: A type code drives variant-specific behavior.
- First safe step: Encapsulate construction for the type code.
- Main risk: Creating subclasses for volatile or combinatorial states.

### 3. [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)

- Use when: Repeated branches vary behavior by stable type or state.
- First safe step: Extract branch behavior into named operations.
- Main risk: Creating a class hierarchy for unstable branches.

### 4. [Extract Class](../refactorings/extract-class.md)

- Use when: Fields and methods form a cohesive responsibility inside a larger owner.
- First safe step: Create the new class with the cohesive data.
- Main risk: Splitting data from behavior or creating chatty coupling.

### 5. [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)

- Use when: Several parameters travel together as one domain concept.
- First safe step: Create the parameter object and pass it alongside old parameters.
- Main risk: Creating a vague data bag instead of a domain concept.

## Verification Focus

- Equality, validation, formatting, and serialization.
- Variant behavior and factory selection.
- Each variant and default/error behavior.
- Behavior using moved fields and original class integration.
- Call-site behavior and object construction defaults.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Replace Primitive with Object](../refactorings/replace-primitive-with-object.md)
- [Replace Type Code with Subclasses](../refactorings/replace-type-code-with-subclasses.md)
- [Replace Conditional with Polymorphism](../refactorings/replace-conditional-with-polymorphism.md)
- [Extract Class](../refactorings/extract-class.md)
- [Introduce Parameter Object](../refactorings/introduce-parameter-object.md)

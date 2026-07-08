# Mutable Data

- Book chapter: Chapter 3, Mutable Data
- Typical evidence: public writable state, repeated field writes
- Primary refactoring: [Encapsulate Variable](../refactorings/encapsulate-variable.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Encapsulate Variable](../refactorings/encapsulate-variable.md)

- Use when: Direct reads or writes to a variable need a controlled access boundary.
- First safe step: Introduce accessors around the variable.
- Main risk: Changing initialization order or bypassing existing invariants.

### 2. [Split Variable](../refactorings/split-variable.md)

- Use when: One variable represents multiple meanings over time.
- First safe step: Introduce a new variable for one meaning.
- Main risk: Changing lifetime or stale value behavior.

### 3. [Slide Statements](../refactorings/slide-statements.md)

- Use when: Nearby statements must be grouped to reveal extraction or sequencing.
- First safe step: Move one independent statement near related statements.
- Main risk: Crossing dependency or side-effect boundaries.

### 4. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

### 5. [Separate Query from Modifier](../refactorings/separate-query-from-modifier.md)

- Use when: A function both returns data and changes state.
- First safe step: Extract a side-effect-free query.
- Main risk: Changing timing of side effects.

### 6. [Remove Setting Method](../refactorings/remove-setting-method.md)

- Use when: A field should not be changed after construction or controlled setup.
- First safe step: Move one setter use into construction or a controlled command.
- Main risk: Blocking legitimate lifecycle transitions.

### 7. [Replace Derived Variable with Query](../refactorings/replace-derived-variable-with-query.md)

- Use when: A stored value can be derived from existing state.
- First safe step: Create a query that computes the value.
- Main risk: Changing caching or performance characteristics.

### 8. [Combine Functions into Class](../refactorings/combine-functions-into-class.md)

- Use when: Several functions operate on the same data and should share an owner.
- First safe step: Create the class around the shared data.
- Main risk: Creating a large procedural class without cohesion.

### 9. [Combine Functions into Transform](../refactorings/combine-functions-into-transform.md)

- Use when: Several functions enrich or transform the same input data.
- First safe step: Create a transform that returns the enriched data.
- Main risk: Mixing mutation and transformation semantics.

### 10. [Change Reference to Value](../refactorings/change-reference-to-value.md)

- Use when: An object is best treated as an immutable value.
- First safe step: Make state immutable or replacement-based.
- Main risk: Breaking identity-based caches or shared mutation expectations.

## Verification Focus

- Read/write behavior and mutation side effects.
- Assignment order and computed values.
- Evaluation order and variable lifetimes.
- Return values, side effects, and branch behavior around the extracted block.
- State changes and returned values independently.
- Initialization and update behavior.
- Derived value consistency after mutations.
- Function outputs before and after moving into the class.
- Transformed data fields and unchanged source data behavior.
- Equality, copying, and update behavior.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Encapsulate Variable](../refactorings/encapsulate-variable.md)
- [Split Variable](../refactorings/split-variable.md)
- [Slide Statements](../refactorings/slide-statements.md)
- [Extract Function](../refactorings/extract-function.md)
- [Separate Query from Modifier](../refactorings/separate-query-from-modifier.md)
- [Remove Setting Method](../refactorings/remove-setting-method.md)
- [Replace Derived Variable with Query](../refactorings/replace-derived-variable-with-query.md)
- [Combine Functions into Class](../refactorings/combine-functions-into-class.md)
- [Combine Functions into Transform](../refactorings/combine-functions-into-transform.md)
- [Change Reference to Value](../refactorings/change-reference-to-value.md)

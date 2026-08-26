# Duplicated Code

- Book chapter: Chapter 3, Duplicated Code
- Typical evidence: repeated normalized statements or method bodies
- Primary refactoring: [Extract Function](../refactorings/extract-function.md)

## How To Use

Read this file when a detection result names this bad smell and the edit is non-trivial.
Use the detector evidence first, then pick the smallest strategy below that directly attacks the evidence.

## Strategy

### 1. [Extract Function](../refactorings/extract-function.md)

- Use when: A cohesive code block can be named by intent and called from its current owner.
- First safe step: Extract the smallest cohesive block into a named function.
- Main risk: Capturing too many locals or changing evaluation order.

### 2. [Slide Statements](../refactorings/slide-statements.md)

- Use when: Nearby statements must be grouped to reveal extraction or sequencing.
- First safe step: Move one independent statement near related statements.
- Main risk: Crossing dependency or side-effect boundaries.

### 3. [Pull Up Method](../refactorings/pull-up-method.md)

- Use when: Sibling subclasses share equivalent behavior.
- First safe step: Move the method to the superclass.
- Main risk: Forcing uncommon behavior into the superclass.

## Verification Focus

- Return values, side effects, and branch behavior around the extracted block.
- Evaluation order and variable lifetimes.
- All affected subclasses.

## Guardrails

- Do not apply every candidate refactoring; choose one narrow step and rerun detection.
- Prefer the first strategy only when it fits the concrete evidence and local design.
- If the finding is low-confidence, inspect code and tests before editing.
- After editing, rerun focused tests and `detect-smells` on the changed scope.

## Related Refactorings

- [Extract Function](../refactorings/extract-function.md)
- [Slide Statements](../refactorings/slide-statements.md)
- [Pull Up Method](../refactorings/pull-up-method.md)

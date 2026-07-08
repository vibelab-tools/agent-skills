# Smell To Refactoring Map

Generated from `src/main/resources/com/codex/refactor/refactoring/smell-refactoring-map.json`.

Use this as a decision aid, not a command list. Pick the smallest refactoring
that addresses the concrete evidence in the finding. Candidate names are limited
to the Refactoring, 2nd Edition catalog in chapters 6-12.

For non-trivial edits, read the matching `smells/<smell>.md` file and the
primary `refactorings/<refactoring>.md` file before editing.

| Smell | Typical Evidence | Candidate Refactorings |
| --- | --- | --- |
| [Mysterious Name](smells/mysterious-name.md) | short/generic names, unclear symbols | [Change Function Declaration](refactorings/change-function-declaration.md), [Rename Variable](refactorings/rename-variable.md), [Rename Field](refactorings/rename-field.md) |
| [Duplicated Code](smells/duplicated-code.md) | repeated normalized statements or method bodies | [Extract Function](refactorings/extract-function.md), [Slide Statements](refactorings/slide-statements.md), [Pull Up Method](refactorings/pull-up-method.md) |
| [Long Function](smells/long-function.md) | high physical/cognitive/cyclomatic size | [Extract Function](refactorings/extract-function.md), [Replace Temp with Query](refactorings/replace-temp-with-query.md), [Introduce Parameter Object](refactorings/introduce-parameter-object.md), [Preserve Whole Object](refactorings/preserve-whole-object.md), [Replace Function with Command](refactorings/replace-function-with-command.md), [Decompose Conditional](refactorings/decompose-conditional.md), [Replace Conditional with Polymorphism](refactorings/replace-conditional-with-polymorphism.md), [Split Loop](refactorings/split-loop.md) |
| [Long Parameter List](smells/long-parameter-list.md) | many parameters, primitive-heavy or grouped names | [Replace Parameter with Query](refactorings/replace-parameter-with-query.md), [Preserve Whole Object](refactorings/preserve-whole-object.md), [Introduce Parameter Object](refactorings/introduce-parameter-object.md), [Remove Flag Argument](refactorings/remove-flag-argument.md), [Combine Functions into Class](refactorings/combine-functions-into-class.md) |
| [Global Data](smells/global-data.md) | public/static/module mutable state | [Encapsulate Variable](refactorings/encapsulate-variable.md), [Move Function](refactorings/move-function.md) |
| [Mutable Data](smells/mutable-data.md) | public writable state, repeated field writes | [Encapsulate Variable](refactorings/encapsulate-variable.md), [Split Variable](refactorings/split-variable.md), [Slide Statements](refactorings/slide-statements.md), [Extract Function](refactorings/extract-function.md), [Separate Query from Modifier](refactorings/separate-query-from-modifier.md), [Remove Setting Method](refactorings/remove-setting-method.md), [Replace Derived Variable with Query](refactorings/replace-derived-variable-with-query.md), [Combine Functions into Class](refactorings/combine-functions-into-class.md), [Combine Functions into Transform](refactorings/combine-functions-into-transform.md), [Change Reference to Value](refactorings/change-reference-to-value.md) |
| [Divergent Change](smells/divergent-change.md) | one class/module has multiple independent concern clusters | [Split Phase](refactorings/split-phase.md), [Move Function](refactorings/move-function.md), [Extract Function](refactorings/extract-function.md), [Extract Class](refactorings/extract-class.md) |
| [Shotgun Surgery](smells/shotgun-surgery.md) | same operation repeated across owners/files, co-change history | [Move Function](refactorings/move-function.md), [Move Field](refactorings/move-field.md), [Combine Functions into Class](refactorings/combine-functions-into-class.md), [Combine Functions into Transform](refactorings/combine-functions-into-transform.md), [Split Phase](refactorings/split-phase.md), [Inline Function](refactorings/inline-function.md), [Inline Class](refactorings/inline-class.md) |
| [Feature Envy](smells/feature-envy.md) | method uses another object's members more than its own | [Move Function](refactorings/move-function.md), [Extract Function](refactorings/extract-function.md) |
| [Data Clumps](smells/data-clumps.md) | same parameter/field/argument group repeats | [Extract Class](refactorings/extract-class.md), [Introduce Parameter Object](refactorings/introduce-parameter-object.md), [Preserve Whole Object](refactorings/preserve-whole-object.md) |
| [Primitive Obsession](smells/primitive-obsession.md) | domain concepts represented by many primitives | [Replace Primitive with Object](refactorings/replace-primitive-with-object.md), [Replace Type Code with Subclasses](refactorings/replace-type-code-with-subclasses.md), [Replace Conditional with Polymorphism](refactorings/replace-conditional-with-polymorphism.md), [Extract Class](refactorings/extract-class.md), [Introduce Parameter Object](refactorings/introduce-parameter-object.md) |
| [Repeated Switches](smells/repeated-switches.md) | repeated switch/match/if-dispatch on same selector | [Replace Conditional with Polymorphism](refactorings/replace-conditional-with-polymorphism.md) |
| [Loops](smells/loops.md) | loop used for transform/filter/search where collection pipeline is clearer | [Replace Loop with Pipeline](refactorings/replace-loop-with-pipeline.md) |
| [Lazy Element](smells/lazy-element.md) | empty/thin/pass-through class/function | [Inline Function](refactorings/inline-function.md), [Inline Class](refactorings/inline-class.md), [Collapse Hierarchy](refactorings/collapse-hierarchy.md) |
| [Speculative Generality](smells/speculative-generality.md) | unused abstraction, future/generic hooks with weak usage | [Collapse Hierarchy](refactorings/collapse-hierarchy.md), [Inline Function](refactorings/inline-function.md), [Inline Class](refactorings/inline-class.md), [Change Function Declaration](refactorings/change-function-declaration.md), [Remove Dead Code](refactorings/remove-dead-code.md) |
| [Temporary Field](smells/temporary-field.md) | field only valid for one calculation path | [Extract Class](refactorings/extract-class.md), [Move Function](refactorings/move-function.md), [Introduce Special Case](refactorings/introduce-special-case.md) |
| [Message Chains](smells/message-chains.md) | deep object navigation chains | [Hide Delegate](refactorings/hide-delegate.md), [Extract Function](refactorings/extract-function.md), [Move Function](refactorings/move-function.md) |
| [Middle Man](smells/middle-man.md) | most methods forward to one delegate | [Remove Middle Man](refactorings/remove-middle-man.md), [Inline Function](refactorings/inline-function.md), [Replace Superclass with Delegate](refactorings/replace-superclass-with-delegate.md), [Replace Subclass with Delegate](refactorings/replace-subclass-with-delegate.md) |
| [Insider Trading](smells/insider-trading.md) | classes access internal details or reciprocal deep structure | [Move Function](refactorings/move-function.md), [Move Field](refactorings/move-field.md), [Hide Delegate](refactorings/hide-delegate.md), [Replace Subclass with Delegate](refactorings/replace-subclass-with-delegate.md), [Replace Superclass with Delegate](refactorings/replace-superclass-with-delegate.md) |
| [Large Class](smells/large-class.md) | many fields/methods/lines, low cohesion clusters | [Extract Class](refactorings/extract-class.md), [Extract Superclass](refactorings/extract-superclass.md), [Replace Type Code with Subclasses](refactorings/replace-type-code-with-subclasses.md) |
| [Alternative Classes with Different Interfaces](smells/alternative-classes-with-different-interfaces.md) | similar roles, different method names/signatures | [Change Function Declaration](refactorings/change-function-declaration.md), [Move Function](refactorings/move-function.md), [Extract Superclass](refactorings/extract-superclass.md) |
| [Data Class](smells/data-class.md) | fields/accessors dominate, little behavior | [Encapsulate Record](refactorings/encapsulate-record.md), [Remove Setting Method](refactorings/remove-setting-method.md), [Move Function](refactorings/move-function.md), [Extract Function](refactorings/extract-function.md) |
| [Refused Bequest](smells/refused-bequest.md) | subclass rejects inherited contract | [Push Down Method](refactorings/push-down-method.md), [Push Down Field](refactorings/push-down-field.md), [Replace Subclass with Delegate](refactorings/replace-subclass-with-delegate.md), [Replace Superclass with Delegate](refactorings/replace-superclass-with-delegate.md) |
| [Comments](smells/comments.md) | comments explain complex code, TODO/HACK/workaround | [Extract Function](refactorings/extract-function.md), [Change Function Declaration](refactorings/change-function-declaration.md), [Introduce Assertion](refactorings/introduce-assertion.md) |

## Confidence Rules

- High-confidence AST evidence can justify direct small refactors after tests.
- Medium confidence needs code inspection and local design context.
- Low confidence should be reported as a signal unless corroborated by code,
  tests, or project history.

## Refactoring Order

Prefer this order when several smells overlap:

1. Characterize behavior with tests.
2. Rename confusing names.
3. Extract functions from long functions.
4. Move extracted functions toward envied data.
5. Introduce parameter objects or classes for repeated data groups.
6. Split large/divergent classes after smaller extracts reveal boundaries.
7. Remove middle men or lazy elements only after confirming they are not
   intentional API/facade boundaries.

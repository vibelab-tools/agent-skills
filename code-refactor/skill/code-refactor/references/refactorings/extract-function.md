# Extract Function

- Book chapter: 6.1 Extract Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A cohesive code block can be named by intent and called from its current owner.

## Preconditions

- Behavior is covered by focused tests or a small characterization test.
- Inputs, outputs, and side effects of the block are explicit.

## First Safe Step

Extract the smallest cohesive block into a named function.

## Steps

1. Extract the smallest cohesive block into a named function.
2. Replace the original block with a call.
3. Run focused tests and inspect call-site readability.

## Test Focus

- Return values, side effects, and branch behavior around the extracted block.

## Risks

- Capturing too many locals or changing evaluation order.

## Common Smells

- [Duplicated Code](../smells/duplicated-code.md)
- [Long Function](../smells/long-function.md)
- [Mutable Data](../smells/mutable-data.md)
- [Divergent Change](../smells/divergent-change.md)
- [Feature Envy](../smells/feature-envy.md)
- [Message Chains](../smells/message-chains.md)
- [Data Class](../smells/data-class.md)
- [Comments](../smells/comments.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

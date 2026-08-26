# Move Function

- Book chapter: 8.1 Move Function
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function uses another owner data or responsibility more than its current owner.

## Preconditions

- The target owner is clear.
- Dependencies on the current owner are explicit.

## First Safe Step

Extract the movable logic if needed.

## Steps

1. Extract the movable logic if needed.
2. Add the function to the target owner.
3. Leave a forwarding wrapper until callers move.

## Test Focus

- Current callers and target owner behavior.

## Risks

- Moving behavior away from required state or API boundaries.

## Common Smells

- [Global Data](../smells/global-data.md)
- [Divergent Change](../smells/divergent-change.md)
- [Shotgun Surgery](../smells/shotgun-surgery.md)
- [Feature Envy](../smells/feature-envy.md)
- [Temporary Field](../smells/temporary-field.md)
- [Message Chains](../smells/message-chains.md)
- [Insider Trading](../smells/insider-trading.md)
- [Alternative Classes with Different Interfaces](../smells/alternative-classes-with-different-interfaces.md)
- [Data Class](../smells/data-class.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

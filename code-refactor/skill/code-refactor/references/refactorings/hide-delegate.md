# Hide Delegate

- Book chapter: 7.7 Hide Delegate
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Clients know too much about a delegate chain.

## Preconditions

- The owning object can expose an intention-revealing operation.
- The delegate boundary is stable.

## First Safe Step

Add one forwarding method on the owner for the common need.

## Steps

1. Add one forwarding method on the owner for the common need.
2. Move one caller to the owner method.
3. Repeat for useful operations only.

## Test Focus

- Caller behavior and delegation target behavior.

## Risks

- Creating a Middle Man by forwarding too much.

## Common Smells

- [Message Chains](../smells/message-chains.md)
- [Insider Trading](../smells/insider-trading.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

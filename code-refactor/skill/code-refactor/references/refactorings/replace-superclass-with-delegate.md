# Replace Superclass with Delegate

- Book chapter: 12.11 Replace Superclass with Delegate
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A superclass relationship exposes too much or models the wrong relationship.

## Preconditions

- The class should use, not be, the superclass concept.
- Delegation can preserve required behavior.

## First Safe Step

Add a delegate to the former superclass behavior.

## Steps

1. Add a delegate to the former superclass behavior.
2. Forward required methods through the delegate.
3. Remove the inheritance link after callers are safe.

## Test Focus

- Inherited behavior, public API, and construction.

## Risks

- Breaking substitutability or superclass contract assumptions.

## Common Smells

- [Middle Man](../smells/middle-man.md)
- [Insider Trading](../smells/insider-trading.md)
- [Refused Bequest](../smells/refused-bequest.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

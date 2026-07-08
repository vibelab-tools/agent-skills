# Replace Subclass with Delegate

- Book chapter: 12.10 Replace Subclass with Delegate
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Subclass variation is better represented by composition.

## Preconditions

- Inheritance causes coupling or rejected behavior.
- Delegated behavior can preserve the public API first.

## First Safe Step

Add a delegate field for one variant behavior.

## Steps

1. Add a delegate field for one variant behavior.
2. Forward one behavior through the delegate.
3. Replace subclass-specific behavior gradually.

## Test Focus

- Subclass public behavior and delegation paths.

## Risks

- Duplicating behavior during migration.

## Common Smells

- [Middle Man](../smells/middle-man.md)
- [Insider Trading](../smells/insider-trading.md)
- [Refused Bequest](../smells/refused-bequest.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

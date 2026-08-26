# Pull Up Method

- Book chapter: 12.1 Pull Up Method
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Sibling subclasses share equivalent behavior.

## Preconditions

- The method behavior is truly common.
- Superclass dependencies are available or can be abstracted.

## First Safe Step

Move the method to the superclass.

## Steps

1. Move the method to the superclass.
2. Adjust subclass-specific dependencies.
3. Remove duplicate subclass methods.

## Test Focus

- All affected subclasses.

## Risks

- Forcing uncommon behavior into the superclass.

## Common Smells

- [Duplicated Code](../smells/duplicated-code.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

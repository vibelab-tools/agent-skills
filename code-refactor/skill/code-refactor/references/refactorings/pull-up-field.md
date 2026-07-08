# Pull Up Field

- Book chapter: 12.2 Pull Up Field
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Sibling subclasses declare the same field for the same concept.

## Preconditions

- The field has the same meaning and lifecycle in subclasses.
- Initialization can be unified.

## First Safe Step

Add the field to the superclass.

## Steps

1. Add the field to the superclass.
2. Redirect subclass access to the superclass field.
3. Remove duplicate subclass fields.

## Test Focus

- Subclass initialization and field access.

## Risks

- Merging similar-looking fields with different semantics.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

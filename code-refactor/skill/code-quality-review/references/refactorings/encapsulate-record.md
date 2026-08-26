# Encapsulate Record

- Book chapter: 7.1 Encapsulate Record
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A record or loose data structure needs controlled access or behavior.

## Preconditions

- Field readers and writers are known.
- The replacement object can preserve field values first.

## First Safe Step

Wrap the record in an object with accessors.

## Steps

1. Wrap the record in an object with accessors.
2. Move callers to the wrapper.
3. Hide or remove direct record access.

## Test Focus

- Field access, serialization, and construction behavior.

## Risks

- Breaking callers that depend on raw data shape.

## Common Smells

- [Data Class](../smells/data-class.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Preserve Whole Object

- Book chapter: 11.4 Preserve Whole Object
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Callers pass many values extracted from the same object.

## Preconditions

- The callee should reasonably know about the source object.
- Passing the whole object does not expose unrelated internals.

## First Safe Step

Add an overload or path accepting the whole object.

## Steps

1. Add an overload or path accepting the whole object.
2. Move one caller to pass the object.
3. Remove extracted parameters when callers migrate.

## Test Focus

- Call-site behavior and object field usage.

## Risks

- Increasing coupling to a broad object.

## Common Smells

- [Long Function](../smells/long-function.md)
- [Long Parameter List](../smells/long-parameter-list.md)
- [Data Clumps](../smells/data-clumps.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Introduce Parameter Object

- Book chapter: 6.8 Introduce Parameter Object
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Several parameters travel together as one domain concept.

## Preconditions

- The parameters are repeatedly passed together.
- A clear object name exists for the group.

## First Safe Step

Create the parameter object and pass it alongside old parameters.

## Steps

1. Create the parameter object and pass it alongside old parameters.
2. Move reads to the object one caller at a time.
3. Remove old parameters after callers migrate.

## Test Focus

- Call-site behavior and object construction defaults.

## Risks

- Creating a vague data bag instead of a domain concept.

## Common Smells

- [Long Function](../smells/long-function.md)
- [Long Parameter List](../smells/long-parameter-list.md)
- [Data Clumps](../smells/data-clumps.md)
- [Primitive Obsession](../smells/primitive-obsession.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Encapsulate Collection

- Book chapter: 7.2 Encapsulate Collection
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A collection field exposes uncontrolled mutation.

## Preconditions

- Mutation paths are known.
- A read-only or controlled update API is possible.

## First Safe Step

Return a protected view or copy for reads.

## Steps

1. Return a protected view or copy for reads.
2. Introduce add/remove methods for intended updates.
3. Remove direct collection mutation by callers.

## Test Focus

- Collection contents, ordering, and mutability expectations.

## Risks

- Changing aliasing, identity, or mutation semantics.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

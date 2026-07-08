# Change Function Declaration

- Book chapter: 6.5 Change Function Declaration
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A function name, parameter list, or signature no longer matches its role.

## Preconditions

- Callers are known or a compatibility wrapper can be kept.
- Tests cover representative call sites.

## First Safe Step

Introduce the new declaration while preserving old behavior.

## Steps

1. Introduce the new declaration while preserving old behavior.
2. Update callers in small batches.
3. Remove the old declaration when no callers remain.

## Test Focus

- Caller compatibility and external API usage.

## Risks

- Breaking public callers, reflection, routing, or serialization hooks.

## Common Smells

- [Mysterious Name](../smells/mysterious-name.md)
- [Speculative Generality](../smells/speculative-generality.md)
- [Alternative Classes with Different Interfaces](../smells/alternative-classes-with-different-interfaces.md)
- [Comments](../smells/comments.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

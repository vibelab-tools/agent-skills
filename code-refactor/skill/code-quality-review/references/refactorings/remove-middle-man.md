# Remove Middle Man

- Book chapter: 7.8 Remove Middle Man
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

A class mostly forwards calls and adds no useful policy.

## Preconditions

- Forwarders are not intentional facade or compatibility API.
- Callers can depend on the delegate safely.

## First Safe Step

Redirect one caller to the delegate.

## Steps

1. Redirect one caller to the delegate.
2. Remove the corresponding forwarder when unused.
3. Repeat while tests stay green.

## Test Focus

- Caller behavior and public API compatibility.

## Risks

- Exposing internals or breaking facade boundaries.

## Common Smells

- [Middle Man](../smells/middle-man.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

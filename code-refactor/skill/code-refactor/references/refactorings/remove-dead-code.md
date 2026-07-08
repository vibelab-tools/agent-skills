# Remove Dead Code

- Book chapter: 8.9 Remove Dead Code
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Code has no production caller or effect.

## Preconditions

- No public, reflective, generated, or configuration path uses it.
- Tests or search confirm it is unused.

## First Safe Step

Remove the smallest unused element.

## Steps

1. Remove the smallest unused element.
2. Run compile and focused tests.
3. Remove now-unused dependents.

## Test Focus

- Build, public entrypoints, and integration configuration.

## Risks

- Deleting dynamically referenced code.

## Common Smells

- [Speculative Generality](../smells/speculative-generality.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

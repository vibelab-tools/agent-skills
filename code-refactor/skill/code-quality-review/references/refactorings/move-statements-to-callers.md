# Move Statements to Callers

- Book chapter: 8.4 Move Statements to Callers
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Statements inside a function are needed only by some callers.

## Preconditions

- Caller-specific behavior can be separated.
- All affected callers are known.

## First Safe Step

Copy the caller-specific statements to one caller.

## Steps

1. Copy the caller-specific statements to one caller.
2. Adjust the function to remove that special case.
3. Update remaining callers deliberately.

## Test Focus

- Each caller variant and shared function behavior.

## Risks

- Duplicating logic that should remain shared.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

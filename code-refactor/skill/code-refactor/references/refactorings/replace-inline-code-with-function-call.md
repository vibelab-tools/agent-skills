# Replace Inline Code with Function Call

- Book chapter: 8.5 Replace Inline Code with Function Call
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Inline code duplicates behavior already available in a function.

## Preconditions

- The existing function has equivalent semantics.
- Inputs map cleanly to function parameters.

## First Safe Step

Replace one inline block with the function call.

## Steps

1. Replace one inline block with the function call.
2. Run tests for that path.
3. Remove now-unused inline helpers if any.

## Test Focus

- Behavioral equivalence for replaced code.

## Risks

- Assuming similar code is exactly equivalent.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

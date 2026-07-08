# Pull Up Constructor Body

- Book chapter: 12.3 Pull Up Constructor Body
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Subclass constructors repeat initialization that belongs in the superclass.

## Preconditions

- Repeated constructor code is equivalent.
- Superclass initialization order is safe.

## First Safe Step

Move common initialization into the superclass constructor or helper.

## Steps

1. Move common initialization into the superclass constructor or helper.
2. Call it from subclasses.
3. Remove duplicate initialization.

## Test Focus

- Construction order and subclass defaults.

## Risks

- Changing initialization order or overridable calls.

## Common Smells

- Not a primary smell-map recommendation; use only when local code evidence calls for it.

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

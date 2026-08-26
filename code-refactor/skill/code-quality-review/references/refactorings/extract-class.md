# Extract Class

- Book chapter: 7.5 Extract Class
- Source scope: Refactoring, 2nd Edition chapters 6-12 catalog.

## Applies When

Fields and methods form a cohesive responsibility inside a larger owner.

## Preconditions

- A field/method cluster can be named.
- Current behavior is characterized by tests.

## First Safe Step

Create the new class with the cohesive data.

## Steps

1. Create the new class with the cohesive data.
2. Move one behavior that uses that data.
3. Redirect callers through the new owner in small steps.

## Test Focus

- Behavior using moved fields and original class integration.

## Risks

- Splitting data from behavior or creating chatty coupling.

## Common Smells

- [Divergent Change](../smells/divergent-change.md)
- [Data Clumps](../smells/data-clumps.md)
- [Primitive Obsession](../smells/primitive-obsession.md)
- [Temporary Field](../smells/temporary-field.md)
- [Large Class](../smells/large-class.md)

## Guardrails

- Keep the transformation behavior-preserving.
- Prefer one call site, branch, class, or method at a time when the blast radius is unclear.
- Run focused tests before broad cleanup.
- Rerun smell detection after the edit to catch replacement smells.

# Bad Smell Detection Maturity

The CLI contains one detector class for each Fowler Chapter 3 smell, but class
presence is not a precision claim. A detector reports only when its structured
parser, project-index, or Git-history evidence matches. Missing evidence yields
no finding rather than a keyword-generated fallback.

## Maturity Levels

- **Strong**: deterministic structural evidence, meaningful positive and
  negative tests, and good line/symbol attribution on the primary language
  path.
- **Medium**: useful parser-backed evidence exists, but design-context review is
  still required.
- **Context-dependent**: the smell describes change history, ownership, type
  relationships, or intent that syntax alone cannot establish.

## Current Groups

| Maturity | Smells | Use |
| --- | --- | --- |
| Strong signals | Long Function, Long Parameter List, Repeated Switches, Message Chains, Comments | Use as objective hotspot evidence, then inspect the changed code. |
| Medium signals | Mysterious Name, Duplicated Code, Global Data, Mutable Data, Data Clumps, Primitive Obsession, Loops, Lazy Element, Temporary Field, Large Class, Data Class | Confirm language idioms, ownership, and business responsibility before acting. |
| Context-dependent | Divergent Change, Shotgun Surgery, Feature Envy, Speculative Generality, Middle Man, Insider Trading, Alternative Classes with Different Interfaces, Refused Bequest | Require project relationships, history, or manual design evidence; do not infer from names alone. |

Maturity varies by language. Java has the richest AST model; broad Tree-sitter
languages expose generic structural evidence without full type checking or
runtime semantics. HTML, CSS, and generic SQL should report only the subset of
smells that meaningfully applies to their structure.

## Promotion Rules

Promote a detector or language path only when all of these exist:

1. A realistic positive fixture proves the intended evidence.
2. A realistic negative fixture protects against the nearest false positive.
3. A reproduced real-repository defect becomes a regression when applicable.
4. The finding points to the correct symbol and line range.
5. Zero findings remain possible for ordinary valid code.

Coverage counts, configured IDs, and synthetic fixtures that force all smells
to appear are not maturity evidence.

# Bad Smell Detection Maturity

This matrix records the current implementation maturity for each Fowler Chapter
3 smell. It is intentionally separate from tests: tests verify behavior, while
this document guides implementation priority and prevents broad language support
from being mistaken for full semantic precision.

## Levels

- Strong: deterministic parser-backed evidence with focused behavior tests and
  limited fallback reliance for the primary Java path.
- Medium: parser-backed evidence exists, but precision still depends on
  heuristics, partial type resolution, repository history, or generic extraction.
- Baseline: the smell ID is covered for broad Tree-sitter languages, but findings
  may rely on language-neutral low-confidence fallback when semantic extraction is
  insufficient.

All smell findings are produced by deterministic parser, project-index, history,
or fallback evidence. The runtime is offline and deterministic.

| Chapter | Smell ID | Java maturity | Tree-sitter maturity | Cross-file | Fallback reliance | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| 3.1 | `mysterious-name` | Strong | Medium | No | Low | Parser-discovered symbols; non-Java symbol extraction remains generic. |
| 3.2 | `duplicated-code` | Strong | Medium | No | Low | Normalized statement shapes plus method body matching. |
| 3.3 | `long-function` | Strong | Medium | No | Low | Uses physical, cyclomatic, cognitive, and nesting metrics. |
| 3.4 | `long-parameter-list` | Strong | Medium | No | Low | Parameter groups and primitive/boolean clusters are modeled. |
| 3.5 | `global-data` | Strong | Medium | No | Low | Module-level Tree-sitter fields support broad language coverage. |
| 3.6 | `mutable-data` | Strong | Medium | No | Low | Field mutability, writers, and public mutable state are modeled. |
| 3.7 | `divergent-change` | Strong | Medium | Yes | Low | Concern clustering uses method bodies, field/member signals, typed collaborator clusters, and project-level call graph targets. |
| 3.8 | `shotgun-surgery` | Strong | Medium | Yes | Low | Project-level method clusters and optional Git history confirmation. |
| 3.9 | `feature-envy` | Strong | Medium | Yes | Low | Resolves dominant foreign root types and member matches when available. |
| 3.10 | `data-clumps` | Strong | Medium | No | Low | Semantic data-group detection for parameters, fields, and call arguments. |
| 3.11 | `primitive-obsession` | Strong | Medium | No | Low | Primitive domain concepts, branch selectors, and boolean flags. |
| 3.12 | `repeated-switches` | Strong | Medium | No | Low | Structured switch and switch-like if/else dispatch evidence. |
| 3.13 | `loops` | Strong | Medium | No | Low | Loop classifications filter common streaming/event-control cases. |
| 3.14 | `lazy-element` | Strong | Medium | No | Low | Empty/no-op, placeholder, thin wrapper, and delegation evidence. |
| 3.15 | `speculative-generality` | Strong | Medium | Yes | Medium | Uses project-level subtype/reference counts; Tree-sitter still uses fallback. |
| 3.16 | `temporary-field` | Strong | Medium | No | Low | Sparse read/write and temporary naming with dependency-name filters. |
| 3.17 | `message-chains` | Strong | Medium | No | Low | Structured chain model with package/static/fluent filters. |
| 3.18 | `middle-man` | Strong | Medium | Yes | Low | Uses delegation evidence and project-level call graph target resolution. |
| 3.19 | `insider-trading` | Strong | Medium | Yes | Low | Structured chains are grouped by collaborator type, project-known type boundaries, resolved calls, and reciprocal intimate access. |
| 3.20 | `large-class` | Strong | Medium | No | Low | Method-field graph and cohesion clusters support Extract Class decisions. |
| 3.21 | `alternative-classes-with-different-interfaces` | Strong | Medium | Yes | Low | Role similarity now includes cross-file class profiles, inbound call roles, shared caller owners, signatures, fields, and behavior tokens. |
| 3.22 | `data-class` | Strong | Medium | No | Low | Accessor ratio, setters, public fields, mutability, and immutability filters. |
| 3.23 | `refused-bequest` | Strong | Medium | Yes | Low | Project-level parent/interface contract matching for Java. |
| 3.24 | `comments` | Strong | Medium | No | Low | Lexical comments plus method-range and complexity context. |

## Next Maturity Targets

1. Upgrade Tree-sitter extraction for JavaScript/TypeScript/TSX, Go, Python, C#,
   and C++ before claiming strong semantic maturity outside Java.
2. Add configurable thresholds only for behaviors that already exist and have
   tests, not for planned detectors.

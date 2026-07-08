# Technical Plan

## Recommendation

Use Java + Maven as the primary implementation stack.

ANTLR is Java-native operationally, Maven can pin the tool/runtime/plugin
versions, and a single JVM CLI is easier to package into the `code-refactor`
skill than a large set of generated Python parser modules.

The current implementation also uses Tree-sitter Java bindings for broad
multi-language parse-tree coverage. This is a pragmatic backend for languages
where ANTLR integration would require substantial grammar-specific work or
where front-end/container languages such as Vue need broader parser coverage.

## Maven Layout

The current project uses one package-organized Maven module plus a packaged
skill snapshot:

```text
code-refactor-tools/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   ├── antlr/
│   │   └── resources/
│   └── test/
│       ├── java/
│       └── resources/fixtures/
├── docs/
└── skill/code-refactor/
    ├── assets/code-refactor-tools.jar
    ├── scripts/
    └── references/
```

Possible later multi-module structure:

```text
code-refactor-tools/
├── pom.xml
├── analysis-core/
├── cli/
├── parser-java/
├── parser-python/
├── parser-javascript/
├── parser-typescript/
├── parser-c/
├── parser-cpp/
├── parser-go/
├── parser-rust/
└── parser-sql/
```

Avoid a multi-module split unless it reduces current build, packaging, or
adapter ownership complexity.

## Core Architecture

Separate parsing from metrics:

1. CLI layer
   - Parses command-line arguments.
   - Expands file and directory inputs.
   - Applies default supported-source directory filtering plus include/exclude
     filters.
   - Chooses JSON or human-readable output.

2. Language detection layer
   - Detects language from extension and optional shebang.
   - Allows explicit `--language`.
   - Marks unknown languages as skipped, not failed.

3. Parser adapter layer
   - Owns ANTLR parser setup for one language.
   - May use Tree-sitter for languages with maintained grammar packages.
   - Collects parse errors.
   - Builds a neutral source model from parse tree data.

4. Analysis core
   - Computes common metrics from the neutral model.
   - Applies threshold rules.
   - Builds a project-level source index for smell detection when multiple files
     are analyzed together.
   - Emits complexity findings and smell findings.

5. Report layer
   - Produces versioned JSON.
   - Preserves parse errors, skipped files, and warnings.
   - Adds refactoring-oriented explanation fields for smell findings, including
     recommended refactorings, related symbols, and confidence caveats.

Refactoring recommendations are data-driven. The chapter 6-12 refactoring
catalog and the chapter 3 smell-to-refactoring mapping live under
`src/main/resources/com/codex/refactor/refactoring/`, alongside a playbook for
each catalog refactoring. Runtime output must use only catalog-backed English
refactoring names. The recommendation layer may reorder candidates by detector
evidence and emits deterministic rationale, preconditions, steps, test focus,
risks, and a first safe step to help Codex choose a small behavior-preserving
refactoring. `scripts/generate-smell-refactoring-reference.py` renders the skill
reference table from the packaged smell mapping resource.

`plan-refactor` consumes `detect-smells` JSON and produces a bounded execution
plan. Its default file-grouped mode ranks hotspot files, then round-robins
findings across files with a per-file cap so large reports are not dominated by
one giant class. Pure finding-order mode remains available with
`--group-by finding`.

## Smell Detector Architecture

Represent each Chapter 3 bad smell with one detector class. The class-name
prefix must be the English book smell name converted to PascalCase, followed by
`BadSmellDetector`.

Examples:

- `MysteriousNameBadSmellDetector`
- `LongFunctionBadSmellDetector`
- `AlternativeClassesWithDifferentInterfacesBadSmellDetector`

Use `BadSmellDetectionDispatcher` as the single orchestration point for running
detectors. The dispatcher owns the standard detector list and calls only
implemented detectors so planned detectors cannot silently report false
negatives.

The 24 detector classes must correspond to:

| Chapter | Class prefix | Smell |
| --- | --- | --- |
| 3.1 | `MysteriousName` | Mysterious Name |
| 3.2 | `DuplicatedCode` | Duplicated Code |
| 3.3 | `LongFunction` | Long Function |
| 3.4 | `LongParameterList` | Long Parameter List |
| 3.5 | `GlobalData` | Global Data |
| 3.6 | `MutableData` | Mutable Data |
| 3.7 | `DivergentChange` | Divergent Change |
| 3.8 | `ShotgunSurgery` | Shotgun Surgery |
| 3.9 | `FeatureEnvy` | Feature Envy |
| 3.10 | `DataClumps` | Data Clumps |
| 3.11 | `PrimitiveObsession` | Primitive Obsession |
| 3.12 | `RepeatedSwitches` | Repeated Switches |
| 3.13 | `Loops` | Loops |
| 3.14 | `LazyElement` | Lazy Element |
| 3.15 | `SpeculativeGenerality` | Speculative Generality |
| 3.16 | `TemporaryField` | Temporary Field |
| 3.17 | `MessageChains` | Message Chains |
| 3.18 | `MiddleMan` | Middle Man |
| 3.19 | `InsiderTrading` | Insider Trading |
| 3.20 | `LargeClass` | Large Class |
| 3.21 | `AlternativeClassesWithDifferentInterfaces` | Alternative Classes with Different Interfaces |
| 3.22 | `DataClass` | Data Class |
| 3.23 | `RefusedBequest` | Refused Bequest |
| 3.24 | `Comments` | Comments |

Each detector implementation must have focused JUnit coverage before it is
marked implemented. Do not add tests for planned detectors that have no current
behavior.

## Neutral Source Model

The adapters should translate language-specific parse trees into a neutral model
before metrics are calculated.

Minimum model:

- `SourceFile`
- `Declaration`
- `FunctionLike`
- `ClassLike`
- `Block`
- `DecisionNode`
- `LoopNode`
- `ExceptionNode`
- `CallLike`
- `CommentRange`
- `ParseError`

Required location data:

- file path,
- language,
- start line,
- start column when available,
- end line,
- end column when available.

For `detect-smells`, the report pipeline first analyzes every supported input
file, then builds `SourceProjectIndex` from the resulting neutral models before
running detectors. The index records known classes, class-to-file ownership,
subtype/implementation relationships, and type-reference counts from fields,
parameters, return types, local variables, `extends`, and `implements`. It also
records method entries by file, method name, method signature, typed method-call
sites, simple call-name callers, and resolved project-level call edges when a
receiver type plus method signature maps to a known method. Detectors that depend
on relationships across files should use this index before falling back to
single-file heuristics.

## Metrics

Minimum complexity metrics:

- physical lines,
- logical lines where practical,
- comment lines,
- blank lines,
- function/method count,
- class/type count,
- function/method length,
- class/type length,
- max nesting depth,
- cyclomatic complexity,
- cognitive complexity,
- decision node count,
- parse error count.

Minimum smell signals:

- long function or method,
- large class or type,
- large file,
- too many parameters,
- high cyclomatic complexity,
- high cognitive complexity,
- excessive nesting,
- duplicated branch shape where practical,
- broad exception handling where practical,
- deeply chained conditionals,
- dead private declaration candidates where practical.

Smell reports should include a confidence level because some signals are
syntactic only.

## ANTLR Strategy

- Pin ANTLR versions exactly.
- Generate visitors/listeners during Maven build.
- Prefer visitor-based traversal for metric extraction.
- Keep grammar-specific code inside language adapters.
- Avoid exposing ANTLR parse-tree types from `analysis-core`.
- Record grammar source and limitations for each adapter.

For grammars-v4 imports, prefer a controlled copy of the specific grammar files
needed for a language over vendoring the whole repository.

## Tree-sitter Strategy

Use `io.github.bonede` Tree-sitter bindings for broad parser-backed support
across Bash, C, C++, C#, Go, Python, Rust, HTML, CSS, JavaScript, TypeScript,
TSX, Vue, Ruby, and SQL.

Rules:

- Pin every grammar artifact version in `pom.xml`.
- Treat Tree-sitter parse errors as report data.
- Build the same neutral source model used by detectors.
- Prefer language-specific extraction refinements over detector-specific parser
  hacks.
- Use the dedicated TSX grammar for `.tsx`; keep React-specific semantic claims
  conservative until focused fixtures cover hooks and generic components.

## Current Java Adapter

The initial Java adapter uses the JDK compiler Tree API
(`com.sun.source.tree`) as a parser-backed AST source. This keeps the first
implementation offline, deterministic, and dependency-light while the neutral
analysis model and smell detector contract stabilize.

Keep detector logic independent from the Java parser backend so a later ANTLR
adapter can replace the source-model builder without rewriting the smell rules.

## Current Multi-language Adapter

The Tree-sitter adapter extracts generic evidence from syntax trees:

- function-like and class-like ranges,
- parameter counts where grammar node shapes expose them,
- fields or declarations where available,
- common class inheritance and interface clauses,
- common public/static/final/readonly/const/export modifiers,
- parameter, return, and field type hints where grammar text exposes them,
- field read/write relationships inside class methods,
- module-level declarations as `<module>` fields for global/mutable data checks,
- local variables and statement-shape evidence inside function bodies,
- direct field delegation methods,
- generic method-call evidence with receiver root, receiver kind, receiver type
  when available, called method name, argument count, and line,
- explicit override markers and unsupported/not-implemented throws,
- loop, conditional, switch/match nodes, and switch-like if/else dispatches,
- message/member chains,
- parse errors,
- comments and TODO/FIXME/HACK markers.

Control-flow nodes that recurse with adjusted nesting depth must mark their
children as already visited, so calls, member access, chains, and local evidence
inside `if`/loop bodies are not double-counted. This is covered by a Tree-sitter
JavaScript regression test and should be preserved when adding new grammar node
types.

This gives every requested language a parser-backed analysis path and the test
suite asserts that each requested non-Java language can report every one of the
24 Chapter 3 smell IDs. The implementation combines source-model findings with
language-neutral low-confidence fallback evidence when a smell is too semantic
for the current parse model.

Duplicated Code uses normalized statement-shape fingerprints in addition to
exact method-body matching. Local names, primitive type tokens, and literals are
abstracted while control flow, operators, and call names remain visible, allowing
renamed local-variable clones to be reported without collapsing unrelated
operations.

Shotgun Surgery groups parser-discovered methods by canonical change keys such
as `update_price_rules/0`. When `detect-smells` receives several files, the
detector uses `SourceProjectIndex` to find the same change-like operation across
owners and files, then emits findings only for files that participate in the
cluster. Evidence includes the project owner count, file count, method names, and
current-file methods. Optional Git history can raise confidence when repeated
co-change evidence confirms the same cluster.

Long Function combines physical length, cyclomatic complexity, cognitive
complexity, and nesting depth. Long Parameter List keeps the parameter-count
threshold but records parameter type runs, primitive-heavy lists, boolean flag
clusters, and named data groups so recommendations can distinguish parameter
object, value object, and explicit-method refactorings. Loops are classified as
collection transformation, scalar accumulation, search with early exit, multiple
explicit loops, or generic explicit loops; obvious streaming/event-control loops
are filtered when structured loop evidence exists.

Mysterious Name reports parser-discovered classes, methods, fields, parameters,
and local variables. Parameter and local-variable checks use narrower rules than
class or method names so common short names such as `id`, coordinates, and loop
indexes do not dominate findings.

Global Data and Mutable Data use field modifiers plus ownership. Module-level
Tree-sitter declarations are modeled as `<module>` fields, public static mutable
fields are reported as global data, and final/const primitive constants are
filtered. Public final references to mutable containers such as lists, maps, and
sets remain reportable because the reference is constant but the object may not
be.

Data Class distinguishes mutable/accessor-dominated data holders from immutable
value objects. The detector considers accessor ratio, setter methods, public
fields, mutable fields, constructor presence, and all-final fields before
reporting.

Lazy Element no longer reports every short method. It looks for empty or no-op
methods, placeholder names, thin forwarding methods, empty classes, placeholder
types, thin wrappers, and stateless single-method helper-style classes.

Temporary Field combines field mutability, sparse read/write participation,
temporary naming, and exposure. Common dependency-style names and types such as
repository, service, client, mapper, factory, logger, and config are filtered.

Large Class now emits concrete signals for excessive lines, methods, fields,
aggregate cyclomatic complexity, and multiple responsibility token clusters. It
also builds a method-field graph from parser-discovered field reads and writes.
Disconnected substantial method/field components are reported as extraction
clusters, and the finding includes a method-field cohesion ratio plus the top
candidate clusters to support Extract Class decisions from deterministic
evidence.

Speculative Generality uses project-level inheritance, implementation, field,
parameter, return-type, and local-variable references when available. It reports
unused abstractions, tiny single-implementation abstractions, and
future/generic/extension-style names only when actual subtype/reference evidence
is weak. For Java files with the JDK AST class model, low-confidence token
fallback is suppressed after the AST rule decides there is no finding; fallback
remains available for Tree-sitter languages whose class extraction is broader and
less semantic.

Message Chains use a structured chain model when parser evidence is available:
root, selectors, depth, line, chain text, and chain kind. The detector reports
object-navigation chains and repeated internal prefixes, including repeated
medium-depth prefixes on the same expression line, while filtering common
fluent APIs, static accesses, self accesses, and package-qualified accesses.
Languages whose generic parse model does not expose chain nodes may still use
the low-confidence text fallback.

Data Clumps use semantic data-group detection for parser-backed models. The
detector normalizes parameter, field, and repeated call-argument names,
recognizes common groups such as range, address, period, money, coordinate, and
contact, compares repeated parameter groups, field groups, and call-site
argument groups, and raises confidence when the same group appears in multiple
kinds of locations. Pure type repetition is not enough for a high-quality
finding because unrelated triples such as logging strings are common false
positives. Generic groups made only of infrastructure collaborators such as
streams, loggers, repositories, clients, services, config objects, and execution
helpers are filtered so constructor dependency injection is not treated as a
domain data clump.

Primitive Obsession uses parser-discovered fields, parameters, local variables,
switch selectors, and method bodies to find stronger primitive-domain signals
before falling back to low-confidence text evidence. Current signals include
domain-named primitive field or parameter clusters, primitive status/type/code
values driving branches, local primitive code variables driving branches,
multiple boolean flag parameters, and repeated domain primitive concepts across
fields and method signatures. These findings remain deterministic candidates.

Feature Envy uses structured foreign-member access counts, own field access
counts, message-chain depth, and collaboration-role filters. When project-level
type data is available, the detector resolves the dominant foreign root's type
through `SourceProjectIndex`, compares accessed root-level selectors with the
target class's known fields and methods, and raises confidence for compact but
well-resolved envy candidates. Findings now include the resolved target type,
target file path, accessed root members, matched members, and member-match ratio.

Repeated Switches use a structured branch-dispatch model when parser evidence is
available. Java records switch statements, switch expressions, case labels, and
switch-like if/else-if chains that compare the same selector against multiple
literal or enum-like labels. Tree-sitter now records generic switch/match nodes
and common if/else equality dispatches from condition subtrees. The detector
then reports repeated selector dispatches and repeated branch-label sets across
methods, while avoiding the old low-confidence text fallback when structured
dispatch evidence exists but does not repeat.

Middle Man uses structured delegation evidence for Java. The analyzer records
single-statement methods that directly return or call a field delegate, including
the delegate field, target method, argument pass-through ratio, and whether the
wrapper method has the same name as the delegate method. The detector reports a
class only when several behavioral methods mostly forward to the same field
delegate, while ignoring parameter forwarding helpers and facade/adapter methods
that add coordination, branching, transformations, or multi-collaborator logic.
When project-level call graph data is available, evidence includes the resolved
delegate type, delegate source path, resolved forwarding call count, and resolved
target methods. Further precision should come from project-level evidence and
focused detector tests.

Insider Trading uses structured message-chain evidence rather than raw member
access counts when available. The detector groups object-navigation chains by
collaborator root and type, scores deep collaborator structure access,
multi-collaborator intimate access, and selectors with internal/private/secret
style names, while filtering mapper/assembler/serializer/adapter style
projection roles unless they expose internal-named details. For Java sources
with structured chain evidence, the detector does not fall back to plain text
tokens after a non-match. When a project index is available, evidence also
records whether the collaborator type exists in the project, resolved calls to
that type, and reciprocal deep object-navigation from the collaborator back into
the current owner. Bidirectional intimate access can raise confidence through
deterministic evidence. Non-Java adapters may still use low-confidence fallback
evidence.

Alternative Classes with Different Interfaces compares semantic class and method
roles extracted from parser data. Method names are split into action/object
tokens, common action synonyms such as read/fetch/load/get and write/save/put
are normalized, signatures are compared by return and parameter categories, and
method body signals such as calls, field reads, and delegations contribute to
role similarity. A finding is reported only when the classes have similar roles
and method-role matches but low exact interface/name overlap; accessor-only data
holders and classes with the same interface are filtered out. When the project
index is available, profiles can be built across files and include inbound call
roles plus shared caller owners, so two classes used interchangeably from the
same clients can be reported from deterministic project evidence. Further
precision should come from deterministic project-level evidence.

Refused Bequest uses the Java inheritance model before reporting Java findings.
The detector uses project-level parent classes and interfaces when several files
are analyzed together, matches subclass methods to inherited contracts by
signature, and classifies unsupported throws, empty overrides, default-value
returns, and empty-collection returns as rejection signals. For unresolved
external parents, it reports only explicit `@Override` methods with strong
rejection evidence. Java sources with structured class data do not fall back to
plain text after a non-match, which prevents ordinary unsupported helper methods
from being treated as refused inheritance.

Comments combines lexical comment extraction with parser-backed method ranges.
The detector classifies comment facts before reporting: TODO/FIXME/HACK style
debt markers, commented-out source code, and clusters of structure-explaining
comments inside long or complex methods are emitted as separate signals. Method
level findings use physical length, cyclomatic complexity, nesting depth,
comment density, and comment line locations. Comments that document external
constraints such as RFCs, protocols, generated code, suppression annotations, or
security/performance constraints are filtered from the hidden-structure signal.

`--min-confidence low|medium|high` filters final smell findings after detectors
run and before summaries are calculated. The default is `low`, which preserves
all findings. This first configuration layer is intentionally output focused;
detector-internal thresholds should only be externalized after their behavior is
implemented and covered by tests.

Runtime smell detection is offline and deterministic. Ambiguous smells should be
improved through parser-backed extraction, project-index evidence, local Git
history, and focused tests rather than external review.

Divergent Change is deterministic by default. The detector groups methods by
owning class or module, classifies independent concerns from method names, body
tokens, field/member access, typed receiver roots, and project-level call graph
targets, then emits collaborator clusters as evidence. Java can reach high
confidence when three or more concern groups have structural signals. Broad
Tree-sitter languages use the same generic method/member model where available
and fall back to low-confidence source-line concern clustering only for markup
or SQL-style inputs with insufficient method structure.

Some Fowler smells still have inherently lower confidence outside richer
semantic models or repository history, especially Shotgun Surgery, Insider
Trading, Alternative Classes with Different Interfaces, and Refused Bequest.

## Optional Git History Analysis

Shotgun Surgery is a change-pattern smell, so parser-backed static evidence is
only a candidate signal. The implementation can optionally inspect local Git
history to confirm whether the same change-like operation repeatedly changes
across several owners.

Current behavior:

- Disabled by default.
- Enabled with `--history-analysis git`.
- Reads recent non-merge commits from the local repository.
- Uses `git show --unified=0` diff hunks and maps new-line ranges back to
  parser-discovered methods/classes.
- Groups methods by canonical change key such as `refresh_cache/0`, including
  verb-family aliases like `refresh`, `reload`, and `invalidate`.
- Emits `history_confirmed` Shotgun Surgery evidence when the cluster meets
  `--history-min-cochanges` and `--history-min-owners`.

Rules:

- Do not contact Git remotes.
- Treat Git failures, non-Git directories, shallow history, parse errors in
  historical files, and very large commits as report warnings or skipped
  history data.
- Static AST findings must still work when history analysis is off.
- History-confirmed evidence may raise Shotgun Surgery confidence to `high`.

## Offline Runtime Boundary

The runtime must stay offline and deterministic. Do not add network calls, API
keys, or external review paths to the CLI. When a smell depends on intent,
collaboration boundaries, or future-change semantics, improve the detector with
AST/parser evidence, project-index relationships, local Git history, or clearer
confidence levels.

## Java vs Python Decision

Use Java because:

- ANTLR tool and Java runtime are the most direct path.
- Maven can manage code generation and packaging cleanly.
- A single CLI/JAR is easier to invoke from Codex skills.
- Generated parsers remain in the same language as analysis code.

Do not use Python as the primary implementation because:

- each grammar needs generated Python runtime artifacts,
- ANTLR tool/runtime version matching is easier to break,
- packaging many generated parser packages is messier,
- performance and startup behavior are less predictable for large directory
  scans.

Python may still be used for helper scripts, fixture generation, or compatibility
wrappers if needed.

## Runtime Behavior

- Runtime must not require network access.
- Network review paths must not be added to the CLI.
- Optional Git history analysis uses local `git` only and must not require
  network access.
- Files that cannot be read should be reported as errors in JSON.
- Parse errors should not abort the whole run.
- Unknown languages should be reported as skipped unless `--language` forced a
  parser.
- Directory scans should include supported source languages by default. Unknown
  or unsupported files remain visible only when passed explicitly.
- Directory scans should ignore common generated/vendor paths by default.
- Output should be deterministic for the same inputs and configuration.

## Integration With The Skill

The repository includes an installable skill snapshot under `skill/code-refactor`.
The live skill should be updated from that snapshot with `make validate` and
`make install`, not by hand-editing
`${CODEX_HOME:-~/.codex}/skills/code-refactor`.

The skill can call this tool in one of two ways:

1. Direct CLI:
   - `java -jar code-refactor-tools.jar analyze-complexity --json <file>`
   - `java -jar code-refactor-tools.jar detect-smells --json <file>`
   - `java -jar code-refactor-tools.jar plan-refactor --json <smells.json>`

2. Thin wrapper scripts inside the skill:
   - `scripts/analyze-complexity`
   - `scripts/detect-smells`
   - `scripts/plan-refactor`
   - `scripts/code-refactor-tools`

Wrappers are acceptable if they keep the skill invocation stable while the
implementation lives in a packaged JAR.

Large generated reports should be written under
`${XDG_CACHE_HOME:-$HOME/.cache}/code-refactor/<run-name>/`, not into the
analyzed repository or the skill development workspace.

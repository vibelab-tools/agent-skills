# Implementation Plan

## Current Baseline

The current Maven CLI and bundled skill snapshot have completed the baseline for
phases 0 through 6:

- parser-backed CLI and JSON reporting are implemented,
- Java uses the JDK compiler AST API,
- 15 non-Java languages use pinned Tree-sitter grammar artifacts,
- each of the 24 Chapter 3 smells has a detector class,
- Java has focused 24-smell detector coverage,
- the non-Java language set has a CLI-level 24-smell coverage matrix,
- optional Git history analysis can confirm Shotgun Surgery through repeated
  local co-change clusters,
- the repository contains `skill/code-refactor` wrapper scripts and a bundled
  JAR path for installation into the live Codex skill directory.

Future work should refine language-specific extraction and semantic precision,
not remove or weaken the existing baseline coverage.

The phase sections below are historical acceptance records plus refinement
guidance. They are not a claim that phases 0 through 6 still need baseline
implementation.

## Phase 0: Project Skeleton

Deliverables:

- Maven project with pinned Java version.
- CLI entrypoint.
- `--help` output.
- JSON serialization dependency.
- Test framework.
- Fixture directory.

Acceptance:

- `mvn test` passes.
- CLI can return a valid empty or unsupported-language JSON report for a file.
- Exit codes match `docs/IO_CONTRACT.md` for invalid invocation and successful
  invocation.

## Phase 1: One Parser-Backed Language

Recommended first choices:

1. Java, because ANTLR support is mature and Java syntax maps naturally to the
   implementation language.
2. Python, because indentation and modern syntax will test whether the parser
   integration handles non-brace languages.

Pick one first. Do not implement both in the same initial commit unless the
first is already green.

Deliverables:

- Grammar files or controlled grammar dependency.
- Parser adapter.
- Neutral source model builder.
- Complexity metrics for files, functions/methods, classes/types.
- Parse error collection.
- JSON fixtures.

Acceptance:

- A representative valid fixture returns `status: "ok"`.
- A malformed fixture returns parse errors without crashing.
- Long function and large class thresholds appear in complexity output.
- Tests assert JSON shape, not only text output.

## Phase 2: Smell Detection

Deliverables:

- Smell rule engine.
- `BadSmellDetectionDispatcher` as the total-control class for running smell
  detectors.
- One detector class for each of the 24 Chapter 3 smells, using the English book
  smell name as the PascalCase class-name prefix.
- Initial rule set:
  - large file,
  - long function/method,
  - large class/type,
  - too many parameters,
  - high cyclomatic complexity,
  - high cognitive complexity,
  - excessive nesting.
- Confidence field for each smell.

Acceptance:

- Smell JSON matches `docs/IO_CONTRACT.md`.
- Rules are deterministic on fixtures.
- Parse errors do not prevent smells from being reported for other files.
- Every implemented smell detector has behavior-focused JUnit tests with
  representative fixtures. Planned detector classes without current behavior do
  not get placeholder tests.

## Phase 3: Directory Scanning

Deliverables:

- Recursive input expansion.
- Default excludes.
- Include/exclude globs.
- Aggregate summaries.
- Safety limits such as `--max-files`.

Acceptance:

- Directory scan reports analyzed, skipped, and errored files separately.
- Generated/vendor files are skipped by default.
- Mixed file and directory inputs are deterministic.

## Phase 4: Broad Parser-Backed Language Coverage

Original recommended ANTLR order:

1. JavaScript.
2. TypeScript.
3. Go.
4. Rust.
5. C.
6. C++.
7. Selected SQL dialect.

Current baseline coverage is implemented through pinned Tree-sitter grammar
artifacts for the requested non-Java language set. For each language:

- document grammar source,
- add fixtures,
- record limitations,
- add adapter tests,
- update `docs/LANGUAGE_SUPPORT.md`.

Acceptance:

- The language has at least one valid fixture and one malformed fixture.
- Common declarations produce expected source-model nodes.
- Known grammar limitations are visible in docs.

ANTLR remains a candidate for deeper language-specific adapters where
Tree-sitter extraction is insufficient.

## Phase 5: Special-Case Language Refinement

Languages that still need extra precision work beyond generic Tree-sitter
coverage:

- Bash.
- Ruby.
- Vue.
- TSX.

Expected refinement work:

- For Vue, implement SFC block extraction before delegating script/template
  analysis.
- For TSX, maintain explicit TypeScript + JSX parsing fixtures and add
  React-specific edge cases before claiming semantic React support.
- For Bash and Ruby, improve language-specific source-model extraction where it
  materially reduces false positives.

Acceptance:

- No language is marked beyond baseline coverage until fixture tests prove it.
- Fallback parser choices are documented and isolated behind the same neutral
  model.

## Phase 6: Skill Packaging And Installation

Deliverables:

- Packaged CLI/JAR.
- Thin wrapper scripts for the skill.
- Updated `code-refactor` skill `SKILL.md` tool commands.
- Report-to-plan workflow for turning smell JSON into bounded refactoring steps.
- Backward-compatible examples.
- Makefile targets for bundle, validate, install, and uninstall.

Acceptance:

- Existing skill commands have a working replacement path.
- `quick_validate.py skill/code-refactor` passes before installation.
- A real local repo file can be analyzed through the skill workflow.
- `make install` installs the validated snapshot to
  `${CODEX_HOME:-~/.codex}/skills/code-refactor` only when live replacement is
  intended.

## Historical First Commit Guidance

The first implementation commit was intended to be narrow:

- Maven skeleton.
- CLI with `analyze-complexity` and `detect-smells` subcommands.
- JSON model classes.
- Unsupported-language report path.
- Tests for invocation, JSON schema basics, and exit codes.

Avoid importing grammars before the CLI/report contract is stable.

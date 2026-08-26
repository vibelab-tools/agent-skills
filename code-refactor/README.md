# Code Quality Review Tools

Parser-backed analysis tools for the local `code-quality-review` Agent Skill.

The project provides a versioned Java/Maven CLI, a packaged executable JAR, and
an installable skill snapshot. The tools analyze source code through AST or
parse-tree data and return stable JSON for AI coding agents such as Codex and
Claude Code.

## Current Status

This project now contains a Maven-based Java CLI implementation with:

- a shaded executable JAR,
- JSON output for complexity and smell detection,
- Java analysis through the JDK compiler AST API,
- Tree-sitter-backed parser adapters for the requested non-Java language set,
- one detector class for each of the 24 Chapter 3 bad smells from
  Refactoring, 2nd Edition,
- optional Git history co-change analysis for Shotgun Surgery, disabled by
  default,
- a `plan-refactor` command that turns `detect-smells` JSON into a bounded
  refactoring execution plan,
- a `review-changes` wrapper that runs all 24 detectors on changed production
  lines and emits at most three compact high-confidence candidates from scopes
  materially touched by the diff,
- JUnit coverage for CLI behavior, Java smell rules, directory scanning,
  parser-backed language behavior, meaningful negative cases, and reproduced
  real-repository defects.

Current skill-facing tools:

- `skill/code-quality-review/scripts/review-changes`
- `skill/code-quality-review/scripts/analyze-complexity`
- `skill/code-quality-review/scripts/detect-smells`
- `skill/code-quality-review/scripts/plan-refactor`
- `skill/code-quality-review/scripts/code-refactor-tools`

The installed skill uses the same extensionless wrapper script names when this
workspace is installed with `make install`. Older Python script names are
historical predecessor names, not current files in this project.

## Implemented Tools

The project produces these user-facing capabilities:

- Complexity analysis: file size, function/method/class size, cyclomatic
  complexity, cognitive complexity, nesting depth, maintainability signals, and
  parse errors.
- Smell detection: all 24 Fowler Chapter 3 bad smell IDs, with detector classes
  named from the English book smell names.
- Optional history analysis: recent non-merge Git commits can be inspected with
  `--history-analysis git` to upgrade Shotgun Surgery findings when several
  owners repeatedly change together.
- Refactoring planning: saved smell reports can be ranked into first safe
  refactoring steps with `plan-refactor`; large reports should be written under
  `${XDG_CACHE_HOME:-$HOME/.cache}/code-quality-review/<run-name>/`.
- Directory scans default to supported source languages, so IDE metadata,
  compiled outputs, docs, and archives are not reported as unsupported unless
  they are passed explicitly.
- Generated Skill references expand the smell-to-refactoring map into
  per-smell and per-refactoring strategy cards, loaded only when a concrete
  refactoring slice needs them.

These are refactor signals, not hard build gates.

## Dependencies

Build and validation dependencies:

- JDK 21. The Maven project compiles with `maven.compiler.release=21`.
- Maven 3.8+ or a compatible `mvn` executable available on `PATH`.
- Python 3 for the changed-code review wrapper and regenerating reference files.
- `rsync` for installing the bundled skill snapshot into Codex and Claude Code
  skill directories.
- Network access to Maven repositories on the first build, unless dependencies
  are already available in the local Maven cache.

Installed skill runtime dependencies:

- A Java 21-compatible runtime available as `java` on `PATH`. The installed
  wrapper scripts invoke the packaged shaded JAR under
  `skill/code-quality-review/assets/code-refactor-tools.jar`.
- Python 3 is required by `scripts/review-changes`; the Java wrappers remain
  available for direct analysis without Python.

Bundled Java dependencies include Jackson for JSON output and Tree-sitter parser
packages for the non-Java language adapters. They are packaged into the shaded
JAR during `make validate` / `make install`.

## Recommended Stack

- Java as the implementation language.
- Maven as the build and packaging system.
- JDK compiler AST for Java, and Tree-sitter for broad multi-language parse
  coverage. ANTLR remains a candidate for deeper language-specific adapters.
- JSON as the stable machine-readable output contract.

See `EVALUATION.md` and `docs/TECHNICAL_PLAN.md` for the detailed rationale.

## New Session Quick Start

When a new agent session starts in this directory, follow the read order in
`AGENTS.md` for Codex or `CLAUDE.md` for Claude Code. Before changing parser coverage, also review
`docs/LANGUAGE_SUPPORT.md`; before relying on detector behavior, run
`mvn test`.

Current refinement work should improve real-repository precision. Zero findings
is valid when structured evidence does not support a smell; do not trade
precision for language-by-smell count coverage.

## Documentation Index

- `AGENTS.md`: instructions for Codex sessions working in this project.
- `CLAUDE.md`: matching instructions for Claude Code sessions working in this project.
- `EVALUATION.md`: original Java-vs-Python and ANTLR feasibility assessment.
- `docs/PROJECT_BRIEF.md`: goals, non-goals, current context, and success
  criteria.
- `docs/TECHNICAL_PLAN.md`: architecture, module layout, parser strategy, and
  language support model.
- `docs/IO_CONTRACT.md`: expected inputs, CLI behavior, JSON outputs, exit
  codes, and examples.
- `docs/IMPLEMENTATION_PLAN.md`: staged implementation plan and acceptance
  criteria.
- `docs/LANGUAGE_SUPPORT.md`: target language matrix and known risks.
- `docs/SMELL_MATURITY.md`: precision levels and promotion rules for smell
  evidence.

## Skill Packaging And Installation

Do not edit live installed copies by hand. Develop and validate changes in this
workspace, then install the bundled skill only when the wrapper/JAR path is ready
for use.

Before installing, verify:

- the CLI runs locally without network access,
- JSON output is stable and documented,
- representative fixtures pass tests,
- parser errors are reported cleanly,
- the live skill can invoke the new tool without breaking existing workflows.

The repository contains the installable Skill snapshot under
`skill/code-quality-review`.

Use the Makefile for local packaging and explicit skill installation:

```bash
make             # build the Maven package
make references  # regenerate generated skill reference files
make validate    # build, refresh skill/code-quality-review/assets/code-refactor-tools.jar, and validate the Skill
make install          # install to $CODEX_HOME/skills and $CLAUDE_HOME/skills
make install-codex    # install only to $CODEX_HOME/skills/code-quality-review
make install-claude   # install only to $CLAUDE_HOME/skills/code-quality-review
make uninstall        # remove both installed copies
make purge            # same as uninstall; this skill has no runtime config
```

# Agent Instructions

## Project Purpose

This directory is the development workspace for the Java/Maven implementation
of the `code-refactor` skill tools and its installable Agent Skill snapshot for
Codex and Claude Code.

The current skill-facing commands are extensionless wrapper scripts backed by a
packaged JAR:

- `scripts/analyze-complexity`
- `scripts/detect-smells`
- `scripts/plan-refactor`
- `scripts/code-refactor-tools`

The installed Codex skill lives at:

`${CODEX_HOME:-~/.codex}/skills/code-refactor`

The installed Claude Code skill lives at:

`${CLAUDE_HOME:-~/.claude}/skills/code-refactor`

Do not edit files in live skill directories by hand. Make changes in this
workspace, update `skill/code-refactor`, run validation, and install the skill
through the Makefile when replacing live copies is intended.

## Read Order For New Sessions

1. Read `README.md`.
2. Read `docs/PROJECT_BRIEF.md`.
3. Read `docs/IO_CONTRACT.md`.
4. Read `docs/TECHNICAL_PLAN.md`.
5. Read `docs/IMPLEMENTATION_PLAN.md` before editing code.
6. Use `EVALUATION.md` for the original ANTLR Java-vs-Python assessment.

## Technical Direction

- Use Java as the primary implementation language.
- Use Maven for build, dependency pinning, parser generation, tests, and CLI
  packaging.
- Use the JDK compiler AST API for Java and Tree-sitter for broad
  multi-language coverage. ANTLR remains a candidate for deeper
  language-specific adapters where it is practical.
- Treat deeper Bash, Ruby, Vue, and TSX precision as explicit refinement work,
  not as parser-feasibility blockers for the current baseline.
- Keep runtime analysis offline and deterministic.
- Emit JSON as the stable integration contract for the skill.
- Treat parser errors as report data, not as a process crash unless invocation
  or file access itself fails.

## Compatibility Goal

The current Java tools preserve the core purpose of the former heuristic skill
tools:

- complexity analysis for files or directories,
- code smell detection for files or directories,
- bounded refactoring planning from smell reports,
- optional human-readable output,
- machine-readable JSON output for AI coding agent consumption.

The JSON schema may be richer than the old heuristic output, but it must remain
stable, versioned, and documented.

## Development Rules

- Keep docs and code scoped to this project.
- Do not vendor full grammar repositories blindly into this directory.
- Pin generated-parser inputs and ANTLR runtime versions explicitly.
- Add focused tests around output schema, parser error handling, and metric
  calculations for each supported language.
- Prefer small staged support over claiming broad language coverage.
- When adding a language, document grammar source, known limitations, and sample
  fixtures.

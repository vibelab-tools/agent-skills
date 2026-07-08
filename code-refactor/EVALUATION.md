# Code Refactor Tools Evaluation

## Goal

Assess the parser-backed implementation direction for the `code-refactor` skill
toolchain.

Current user-facing tool names are:

- `analyze-complexity`
- `detect-smells`
- `plan-refactor`
- `code-refactor-tools`

The tools should use AST/parse-tree level analysis instead of regex scanning.
The current implementation covers C, C++, C#, Bash, Rust, Go, Java, Python,
Ruby, SQL, HTML, CSS, JavaScript, TypeScript, TSX, and Vue through Java AST and
Tree-sitter adapters.

## Recommendation

Use Java + Maven as the primary implementation stack.

Reasons:

- ANTLR itself is Java-based, and the Java target/runtime is the most direct
  path for generated parsers.
- Maven can pin `antlr4`, `antlr4-runtime`, and `antlr4-maven-plugin` versions
  together.
- A single JVM CLI can package generated parsers and analysis logic into a
  reproducible tool.
- Many grammars-v4 grammars include Java target support or Java examples.
- Python target requires generated Python code per grammar plus strict runtime
  version matching, which becomes harder to manage across many languages.

## ANTLR Fit

ANTLR is viable for a staged implementation, but not every requested language is
equally ready in grammars-v4.

Good initial candidates:

- Java
- Python
- JavaScript
- TypeScript through `javascript/typescript`
- C
- C++
- Go
- Rust
- SQL dialects such as SQLite, PostgreSQL, MySQL/MariaDB, TSQL, PL/SQL

Higher-risk or needs separate handling:

- Bash: no top-level `bash` grammar found in grammars-v4 during this check.
- Ruby: no top-level `ruby` grammar found in grammars-v4 during this check.
- Vue: no top-level Vue grammar found; likely requires extracting `<script>`,
  `<template>`, and `<style>` blocks, then delegating JS/TS/TSX/template
  analysis separately.
- TSX: no top-level `tsx` grammar found; likely handled through TypeScript/JSX
  grammar support or a separate frontend parser if ANTLR grammar coverage is
  insufficient.

## Proposed Architecture

Keep parser generation separate from metric computation:

1. `parser-*` modules generate language-specific parsers.
2. `analysis-core` defines neutral metrics and node models:
   - file summary
   - function/method summary
   - class/type summary
   - decision nodes
   - nesting depth
   - comment ranges
   - parse errors
3. `language-*` adapters translate parse trees into the neutral model.
4. CLI commands compute:
   - line counts
   - function/method/class length
   - cyclomatic complexity
   - cognitive complexity
   - selected smells
   - JSON output for Codex consumption

## Staged Rollout

This section records the original rollout thinking. The current implementation
has since adopted JDK compiler AST for Java and Tree-sitter for broad
multi-language coverage.

Phase 1:

- Java + Maven project skeleton.
- Java and Python parser-backed metrics.
- JSON output compatible with the current skill tools.

Phase 2:

- JavaScript and TypeScript.
- Validate TSX feasibility before promising support.

Phase 3:

- Go, Rust, C, C++.
- Treat C/C++ preprocessing and grammar base-class requirements as explicit
  project work, not a trivial grammar import.

Phase 4:

- SQL dialect selection.
- Start with one or two dialects; do not claim generic SQL correctness.

Phase 5:

- Bash, Ruby, Vue fallback strategy.
- Decide whether ANTLR remains the only parser backend or whether these need
  a separate parser family.

## Implementation Notes

- Pin ANTLR versions exactly.
- Generate visitors/listeners at build time.
- Prefer JSON output as the stable integration contract with the Codex skill.
- Keep human-readable output optional.
- Treat parser errors as data in the report rather than crashing on first error.
- Do not use complexity numbers as hard gates; they are refactor signals.

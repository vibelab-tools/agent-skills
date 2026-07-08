# Project Brief

## Background

The local `code-refactor` skill originally relied on lightweight heuristic
tools for complexity and smell signals. Those predecessors provided useful
quick feedback, but extension detection, regular expressions, and text
heuristics were fragile for multi-line constructs, nested syntax, comments,
strings, language-specific declarations, and modern syntax.

This project now maintains the parser-backed Java/Maven tool suite and the
installable `skill/code-refactor` snapshot. The skill-facing scripts are
extensionless wrappers around a packaged JAR:

- `scripts/analyze-complexity`
- `scripts/detect-smells`
- `scripts/plan-refactor`
- `scripts/code-refactor-tools`

## Primary Goal

Maintain and refine a local CLI tool suite that analyzes source files using AST
or parse-tree data and emits stable JSON that Codex can use when deciding
whether and how to refactor code.

## Tooling Scope

The project must cover these capabilities:

1. Complexity analysis.
2. Code smell detection.
3. Refactoring plan generation from saved smell reports.

These capabilities should support single-file and directory analysis. Directory
analysis must produce per-file results plus aggregate summary data.

## Target Language Set

The implemented baseline covers the user-requested language set through Java
AST plus Tree-sitter parse trees:

- C
- C++
- C#
- Bash
- Rust
- Go
- Java
- Python
- Ruby
- SQL
- HTML
- CSS
- JavaScript
- TypeScript
- TSX
- Vue

See `LANGUAGE_SUPPORT.md` for precision levels and known limitations.

## Success Criteria

- The project has a Maven-based Java CLI.
- The CLI analyzes Java through the JDK compiler AST API and the requested
  non-Java language set through Tree-sitter adapters.
- JSON output is schema-versioned and documented.
- File, function, method, and class length thresholds from the `code-refactor`
  skill are represented as configurable refactor signals.
- The 24 Chapter 3 bad smells are represented by detector classes named from
  the original English smell names and coordinated by a single dispatcher.
- Parser errors are included in reports with line and column data when
  available.
- The implementation is tested with fixtures, including Java per-smell tests
  and a requested-language x 24-smell matrix.
- The installable skill wrappers can call the packaged CLI without breaking
  existing skill workflows.

## Non-Goals

- Do not build an automatic refactoring engine.
- Do not enforce complexity as a hard failure by default.
- Do not attempt full semantic type checking for every language.
- Do not claim generic SQL support without naming dialect coverage.
- Do not vendor large third-party grammar repositories without a deliberate
  dependency and update strategy.
- Do not edit the live skill path by hand; install from this workspace only
  after `make validate` succeeds.

## Refactor Signal Thresholds

Use these thresholds as defaults, matching the current skill guidance:

- UI/view source file: around 300 lines.
- Route/controller/store file: around 500 lines.
- Service/orchestration module: around 800 lines.
- Any hand-written source file: around 1000 lines.
- Function or method: around 30-50 lines.
- Class/type: around 300 lines or 15-20 methods.
- A proposed edit adding more than about 100 lines to one existing file.

These thresholds are warning signals, not automatic rewrite rules.

## Output Philosophy

The tool should produce evidence for Codex, not final judgment. Reports should
make it clear:

- what was parsed,
- what failed to parse,
- what metrics were measured,
- what smells were detected,
- what confidence level each signal has,
- what language adapter produced the signal.

# Project Brief

## Background

The former `code-refactor` Skill relied on lightweight heuristic
tools for complexity and smell signals. Those predecessors provided useful
quick feedback, but extension detection, regular expressions, and text
heuristics were fragile for multi-line constructs, nested syntax, comments,
strings, language-specific declarations, and modern syntax.

This project now maintains the parser-backed Java/Maven tool suite and the
installable `skill/code-quality-review` snapshot. The skill-facing scripts are
extensionless wrappers around a packaged JAR:

- `scripts/analyze-complexity`
- `scripts/detect-smells`
- `scripts/plan-refactor`
- `scripts/code-refactor-tools`
- `scripts/review-changes`

## Primary Goal

Maintain a local CLI tool suite that gives Codex compact, trustworthy evidence
during post-change code-quality review. The tools support the decision whether a
current change warrants a small behavior-preserving refactor; they do not make
that decision for the agent.

The default post-change workflow runs all 24 standard detectors against changed
production source, keeps only findings whose reported scope was materially
touched by changed lines, and returns at most three high-confidence candidates
for manual validation.

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
- File, function, method, and class length thresholds used by the
  `code-quality-review` Skill are represented as configurable refactor signals.
- The 24 Chapter 3 bad smells are represented by detector classes named from
  the original English smell names and coordinated by a single dispatcher.
- Parser errors are included in reports with line and column data when
  available.
- The implementation is tested with focused positive and negative fixtures,
  including reproduced defects from representative real repositories.
- The installable skill wrappers can call the packaged CLI without breaking
  existing skill workflows.
- The default changed-code wrapper proves that all 24 standard detectors ran
  and bounds its output to three compact candidates.

## Non-Goals

- Do not build an automatic refactoring engine.
- Do not synthesize a smell solely to satisfy language-by-smell coverage.
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

A successful report with zero smells is a valid and desirable result when the
structured evidence does not support a finding.

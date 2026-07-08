# Input And Output Contract

## Design Rule

JSON is the primary contract. Human-readable text is secondary.

The JSON schema must be versioned so the `code-refactor` skill can consume it
without guessing which fields exist.

## Commands

The packaged executable is a JAR invoked either directly or through skill
wrapper scripts. It exposes these logical commands:

```bash
code-refactor-tools analyze-complexity [options] <path>...
code-refactor-tools detect-smells [options] <path>...
code-refactor-tools plan-refactor [options] <detect-smells-report.json|->
```

Compatibility aliases are recommended:

```bash
code-refactor-tools complexity [options] <path>...
code-refactor-tools smells [options] <path>...
code-refactor-tools plan [options] <detect-smells-report.json|->
```

## Common Inputs

Required for analysis commands:

- one or more file or directory paths.

Required for `plan-refactor`:

- one `detect-smells` JSON report path, or `-` to read the report from stdin.

Common options:

```text
--format json|text          Output format. Default: text for humans, json for skill wrappers.
--json                      Alias for --format json.
--language <id>             Force language for all file inputs.
--include <glob>            Include glob. Repeatable.
--exclude <glob>            Exclude glob. Repeatable.
--config <file>             Optional thresholds and language config.
--max-files <n>             Directory scan safety limit.
--history-analysis off|git  Optional Git history analysis. Default: off.
--history-commits <n>       Recent non-merge commits to inspect. Default: 200.
--history-min-cochanges <n> Minimum co-change commits. Default: 3.
--history-min-owners <n>    Minimum distinct owners in a cluster. Default: 3.
--min-confidence low|medium|high
                            Filter smell findings below confidence. Default: low.
--max-findings <n>          Maximum planned findings for plan-refactor. Default: 5.
--group-by file|finding     Plan grouping for plan-refactor. Default: file.
--max-findings-per-file <n> Maximum planned findings per file in file mode. Default: 3.
--fail-on-parse-error       Return non-zero when any parse error is found.
--no-default-excludes       Include generated/vendor paths.
```

Directory scans default to supported source languages only. In auto-language
mode, recursive directory input includes files detected as:

```text
java bash c cpp csharp go rust html css javascript typescript tsx vue ruby sql python
```

`--include` and `--exclude` further narrow that supported source set. Passing a
single unsupported file explicitly still produces an `unsupported_language`
entry so bad invocations are visible.

Default excludes:

```text
.git
node_modules
vendor
dist
build
target
.next
.nuxt
coverage
*.min.js
*.generated.*
*.pb.*
```

## Analyze Complexity Input

Examples:

```bash
code-refactor-tools analyze-complexity --json src/App.tsx
code-refactor-tools analyze-complexity --json --language java src/Main.java
code-refactor-tools analyze-complexity --json --include "**/*.py" src tests
```

The command accepts:

- source files,
- directories,
- mixed file and directory arguments.

Directory output must include per-file results and an aggregate summary.
Unsupported non-source files are not included in directory scans by default.

## Detect Smells Input

Examples:

```bash
code-refactor-tools detect-smells --json src/App.tsx
code-refactor-tools detect-smells --json --config refactor-thresholds.json src
code-refactor-tools detect-smells --json --history-analysis git src
```

The smell detector uses parser-backed metrics plus threshold rules. It should not
modify files.

Unsupported non-source files are not included in directory scans by default.

History analysis is also opt-in. With `--history-analysis git`, the tool reads
local Git history only; it does not contact remotes. The first supported history
signal is Shotgun Surgery: recent non-merge commits are inspected, diff hunks
are mapped back to parser-discovered methods/classes, and repeated co-changes
across several owners become `history_confirmed` evidence.

## Plan Refactor Input

Examples:

```bash
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/code-refactor/my-run"
mkdir -p "$CACHE_DIR"
code-refactor-tools detect-smells --json --min-confidence medium src > "$CACHE_DIR/smells.json"
code-refactor-tools plan-refactor --json --max-findings 20 "$CACHE_DIR/smells.json"
code-refactor-tools plan-refactor --format text "$CACHE_DIR/smells.json"
cat "$CACHE_DIR/smells.json" | code-refactor-tools plan-refactor --json -
```

`plan-refactor` consumes a `detect-smells` JSON report. It does not rescan
source files and does not modify files. By default it groups by file, ranks
hotspot files by their strongest findings, then round-robins findings across
files up to `--max-findings-per-file` so one giant file cannot monopolize the
plan. Use `--group-by finding` to preserve pure finding-level ordering.

## Language IDs

Use stable lowercase IDs:

```text
c
cpp
csharp
bash
rust
go
java
python
ruby
sql
html
css
javascript
typescript
tsx
vue
```

SQL dialects may use extended IDs:

```text
sql:postgresql
sql:mysql
sql:sqlite
sql:tsql
sql:plsql
```

## Complexity JSON

Top-level shape:

```json
{
  "schema_version": "1.0",
  "tool": "analyze-complexity",
  "status": "ok",
  "invocation": {
    "paths": ["src/App.tsx"],
    "format": "json",
    "language": "auto",
    "history_analysis": "off",
    "history_commits": 200,
    "history_min_cochanges": 3,
    "history_min_owners": 3,
    "min_confidence": "low"
  },
  "summary": {
    "files_total": 1,
    "files_analyzed": 1,
    "files_skipped": 0,
    "files_with_parse_errors": 0,
    "functions_total": 4,
    "classes_total": 1,
    "max_cyclomatic_complexity": 7,
    "max_cognitive_complexity": 11
  },
  "files": [
    {
      "path": "src/App.tsx",
      "language": "tsx",
      "parser": "tree-sitter",
      "parser_id": "tree-sitter-tsx",
      "status": "ok",
      "metrics": {
        "physical_lines": 220,
        "logical_lines": 180,
        "blank_lines": 24,
        "comment_lines": 12,
        "function_count": 4,
        "class_count": 1,
        "max_nesting_depth": 4,
        "cyclomatic_complexity": 18,
        "cognitive_complexity": 25,
        "maintainability_index": 72.5
      },
      "functions": [
        {
          "name": "renderToolbar",
          "kind": "function",
          "location": {
            "start_line": 42,
            "start_column": 1,
            "end_line": 91,
            "end_column": 2
          },
          "metrics": {
            "physical_lines": 50,
            "parameter_count": 3,
            "max_nesting_depth": 3,
            "cyclomatic_complexity": 6,
            "cognitive_complexity": 9
          },
          "thresholds": [
            {
              "id": "function.length.warning",
              "actual": 50,
              "limit": 50,
              "severity": "medium"
            }
          ]
        }
      ],
      "classes": [
        {
          "name": "CaptureController",
          "kind": "class",
          "location": {
            "start_line": 100,
            "end_line": 210
          },
          "metrics": {
            "physical_lines": 111,
            "method_count": 8
          },
          "thresholds": []
        }
      ],
      "parse_errors": [],
      "warnings": []
    }
  ],
  "errors": []
}
```

When Git history analysis is enabled, the report includes a top-level
`history_analysis` object:

```json
{
  "enabled": true,
  "status": "ok",
  "repository_root": "/repo",
  "commits_scanned": 200,
  "shotgun_surgery_clusters": 1,
  "warnings": []
}
```

Shotgun Surgery findings may include history evidence:

```json
{
  "signal": "history_confirmed",
  "change_key": "refresh_cache/0",
  "co_change_commits": 4,
  "recent_commit_window": 200,
  "owners": ["UserCache", "OrderCache", "ProductCache"],
  "owner_count": 3,
  "sample_commits": ["abc123"],
  "symbols": [
    {
      "path": "src/UserCache.java",
      "kind": "method",
      "owner": "UserCache",
      "name": "refreshCache",
      "parameter_count": 0,
      "start_line": 12,
      "end_line": 18,
      "change_count": 4
    }
  ]
}
```

## Smell JSON

Top-level shape:

```json
{
  "schema_version": "1.0",
  "tool": "detect-smells",
  "status": "ok",
  "invocation": {
    "paths": ["src/App.tsx"],
    "format": "json",
    "language": "auto"
  },
  "summary": {
    "files_total": 1,
    "files_analyzed": 1,
    "total_smells": 2,
    "critical": 0,
    "high": 1,
    "medium": 1,
    "low": 0
  },
  "analysis_scope": {
    "project_index_mode": "full",
    "project_index_batch_size": 160,
    "project_index_batch_count": 1,
    "project_index_grouping": "nearest-project-marker-then-size"
  },
  "files": [
    {
      "path": "src/App.tsx",
      "language": "tsx",
      "status": "ok",
      "smells": [
        {
          "id": "long-function",
          "type": "Long Function",
          "severity": "medium",
          "confidence": "high",
          "location": {
            "symbol": "renderToolbar",
            "line": 42,
            "start_line": 42,
            "start_column": 1,
            "end_line": 91,
            "end_column": 2
          },
          "evidence": {
            "metric": "physical_lines",
            "actual": 50,
            "threshold": 50
          },
          "description": "Function is at the upper bound of the preferred length range.",
          "suggestion": "Consider extracting rendering or state-mapping helpers if this function changes again.",
          "recommended_refactorings": ["Extract Function", "Replace Temp with Query"],
          "recommended_refactoring_details": [
            {
              "name": "Extract Function",
              "chapter": "6.1"
            }
          ],
          "recommended_refactoring_rationale": [
            {
              "name": "Extract Function",
              "reason": "The method evidence shows size or control-flow pressure; this refactoring attacks the strongest contributor before broad rewrites. Candidate: Extract Function.",
              "applies_when": "A cohesive code block can be named by intent and called from its current owner.",
              "preconditions": [
                "Identify a cohesive block with observable behavior.",
                "Keep inputs and outputs explicit before moving code."
              ],
              "first_safe_step": "Extract the smallest cohesive block into a named function.",
              "steps": [
                "Extract the smallest cohesive block into a named function.",
                "Replace the original block with a call.",
                "Run focused tests and inspect call-site readability."
              ],
              "test_focus": [
                "Return values, side effects, and branch behavior around the extracted block."
              ],
              "risks": [
                "Capturing too many locals or changing evaluation order."
              ]
            }
          ],
          "related_symbols": ["renderToolbar"],
          "why_not_higher_confidence": ""
        },
        {
          "id": "high-cognitive-complexity",
          "type": "High Cognitive Complexity",
          "severity": "high",
          "confidence": "medium",
          "location": {
            "symbol": "handleSave",
            "start_line": 120,
            "end_line": 175
          },
          "evidence": {
            "metric": "cognitive_complexity",
            "actual": 18,
            "threshold": 15
          },
          "description": "Function has nested control flow that may be hard to change safely.",
          "suggestion": "Split validation, rendering, and side-effect orchestration into separate functions."
        }
      ],
      "parse_errors": [],
      "warnings": []
    }
  ],
  "errors": []
}
```

`location.line` is a compatibility alias for `location.start_line`. New
consumers should prefer `start_line` and `end_line`.

`recommended_refactorings` contains exact English refactoring names from the
Refactoring, 2nd Edition chapter 6-12 catalog. The mapping from Chapter 3 bad
smells to candidate refactorings is loaded from packaged JSON resources and
validated at startup so aliases outside the catalog are not emitted. When a
finding has structured evidence, the list may be reordered so the most relevant
catalog item appears first. `recommended_refactoring_details` mirrors the same
order and includes `name` and `chapter`.
`recommended_refactoring_rationale` also mirrors the same order and includes the
reason, playbook applicability, preconditions, first safe step, steps, test
focus, and risks for each recommendation. These rationales are deterministic
guidance for Codex; they are not proof that the refactoring is safe without
tests.

For smell detection, `analysis_scope.project_index_mode` describes how
cross-file evidence was scoped. Small scans use `full`. Large scans may use
`partitioned`, grouping files first by the nearest project marker such as
`pom.xml`, `package.json`, `go.mod`, `Cargo.toml`, `Gemfile`, or
`pyproject.toml`, then by batch size. Partitioning keeps large offline scans
bounded; local single-file smells are unaffected, while cross-file smells are
scoped to the partition that contains the reported file.

## Plan Refactor JSON

Top-level shape:

```json
{
  "schema_version": "1.0",
  "tool": "plan-refactor",
  "status": "ok",
  "invocation": {
    "input": "smells.json",
    "format": "json",
    "max_findings": 5,
    "min_confidence": "medium",
    "group_by": "file",
    "max_findings_per_file": 3
  },
  "source_report": {
    "schema_version": "1.0",
    "tool": "detect-smells",
    "status": "ok",
    "summary": {}
  },
  "summary": {
    "candidate_findings": 12,
    "planned_findings": 5,
    "planned_files": 3,
    "high": 2,
    "medium": 3,
    "low": 0,
    "high_confidence": 4,
    "medium_confidence": 1,
    "low_confidence": 0
  },
  "plan": [
    {
      "rank": 1,
      "smell_id": "feature-envy",
      "smell_type": "Feature Envy",
      "file_path": "src/RiskReport.java",
      "language": "java",
      "severity": "high",
      "confidence": "high",
      "location": {
        "symbol": "score",
        "start_line": 42,
        "end_line": 57
      },
      "evidence": {},
      "primary_refactoring": {
        "name": "Move Function",
        "chapter": "8.1",
        "reason": "The method depends on another owner more than its own.",
        "applies_when": "The target owner already has the data and behavior belongs there.",
        "preconditions": [],
        "first_safe_step": "Move the smallest cohesive behavior to the target owner.",
        "steps": [],
        "test_focus": [],
        "risks": []
      },
      "supporting_refactorings": [],
      "rerun_command": "scripts/detect-smells --json --min-confidence high src/RiskReport.java"
    }
  ],
  "warnings": [],
  "errors": []
}
```

The `plan` array is intentionally bounded by `--max-findings`. It is an
execution aid for Codex: apply one step, run focused tests, and rerun smell
detection before taking the next step.

## Parse Error Shape

```json
{
  "message": "mismatched input '}' expecting expression",
  "start_line": 32,
  "start_column": 14,
  "end_line": 32,
  "end_column": 15,
  "severity": "error"
}
```

Parse errors should appear under the relevant file. The overall command may
still return `status: "partial"` when other files were analyzed successfully.
By default parse errors are report data and do not make the process fail; when
`--fail-on-parse-error` is set, the report is still written and the process
returns exit code 3.

## Skipped File Shape

```json
{
  "path": "dist/bundle.min.js",
  "language": "javascript",
  "status": "skipped",
  "skip_reason": "default_exclude",
  "metrics": null,
  "functions": [],
  "classes": [],
  "parse_errors": [],
  "warnings": []
}
```

## Status Values

Top-level status:

- `ok`: all requested analyzable inputs were processed.
- `partial`: some files failed, skipped, or had parse errors.
- `error`: invocation failed before useful analysis could run.

Per-file status:

- `ok`
- `parse_error`
- `unsupported_language`
- `skipped`
- `read_error`
- `internal_error`

## Exit Codes

```text
0  Analysis completed. Reports may still include smells or warnings.
1  Invalid invocation, missing input, bad config, or unreadable required path.
2  Internal tool error.
3  Parse errors occurred and --fail-on-parse-error was set.
4  Findings met a caller-provided failure threshold.
```

Smells and complexity warnings must not cause a non-zero exit code by default.

## Compatibility With Former Entry Points

The predecessor tools accepted direct script invocations for files and
directories. Current compatibility is at the workflow level, through the
extensionless wrappers and logical subcommands:

- `scripts/analyze-complexity <path>...`
- `scripts/analyze-complexity --json <path>...`
- `scripts/detect-smells <path>...`
- `scripts/detect-smells --json <path>...`
- `scripts/plan-refactor --json <detect-smells-report.json|->`
- `scripts/code-refactor-tools <command> [options] <path>...`

Directory analysis uses directory paths as normal positional inputs. The current
Java implementation does not need to match old human-readable text exactly, but
JSON consumers should have access to equivalent or richer data.

The compare mode for complexity can be implemented after the base JSON schema is
stable.

# Tooling Reference

## Commands

Use the wrapper scripts from the skill directory:

```bash
scripts/analyze-complexity --json <path>...
scripts/detect-smells --json <path>...
scripts/detect-smells --json --history-analysis git <path>...
scripts/plan-refactor --json <detect-smells-report.json|->
```

The wrappers call `assets/code-refactor-tools.jar`.

## Common Options

```text
--json                      Emit machine-readable JSON.
--format json|text          Explicit output format.
--language <id>             Force one language for all input files.
--include <glob>            Include glob. Repeatable.
--exclude <glob>            Exclude glob. Repeatable.
--config <file>             Optional threshold config.
--max-files <n>             Directory scan safety limit.
--history-analysis off|git  Optional local Git history analysis.
--history-commits <n>       Recent non-merge commits to inspect.
--history-min-cochanges <n> Minimum co-change commits.
--history-min-owners <n>    Minimum distinct owners in a cluster.
--min-confidence low|medium|high
--max-findings <n>          Maximum planned findings for plan-refactor.
--group-by file|finding     Plan grouping for plan-refactor. Default: file.
--max-findings-per-file <n> Maximum planned findings per file in file mode.
--fail-on-parse-error       Return non-zero when parse errors are found.
--no-default-excludes       Include generated/vendor paths.
```

There is no LLM or network review option.

## Language IDs

Use lowercase IDs:

```text
java bash c cpp csharp go rust html css javascript typescript tsx vue ruby sql python
```

SQL dialect IDs may be used when needed:

```text
sql:postgresql sql:mysql sql:sqlite sql:tsql sql:plsql
```

Directory scans include supported source languages by default. Unsupported
non-source files such as IDE metadata, compiled classes, docs, and archive files
are not included in directory reports unless passed as explicit file inputs.
`--include` and `--exclude` further narrow the supported source set.

## Exit Codes

- `0`: command ran and emitted a report.
- `1`: invalid invocation or input path error.
- `3`: parse errors were found and `--fail-on-parse-error` was set.

Parse errors are normally JSON report data, not process failures.

## Reading JSON

Top-level fields:

- `schema_version`
- `tool`
- `status`
- `invocation`
- `summary`
- `files`
- `errors`

Per-file smell findings live at:

```text
files[].smells[]
```

Useful finding fields:

- `id`: smell ID, such as `long-function`.
- `type`: English bad-smell name.
- `book_chapter`: Fowler Chapter 3 section number.
- `severity`, `confidence`.
- `location`: symbol and line range.
- `evidence`: detector-specific structured facts.
- `recommended_refactorings`: exact chapter 6-12 catalog names, ordered by
  finding evidence when available.
- `recommended_refactoring_details`: same order with catalog chapter.
- `recommended_refactoring_rationale`: same order with reason, applicability,
  preconditions, first safe step, steps, test focus, and risks.
- `description`, `suggestion`.

## Practical Command Patterns

Analyze touched files:

```bash
scripts/detect-smells --json path/to/File.java path/to/view.tsx
```

Analyze a project directory but avoid low-confidence noise:

```bash
scripts/detect-smells --json --min-confidence medium src
```

Confirm Shotgun Surgery candidates with local history:

```bash
scripts/detect-smells --json --history-analysis git src
```

Get complexity only:

```bash
scripts/analyze-complexity --json src
```

Build a small refactoring execution plan from a saved smell report:

```bash
CACHE_DIR="${XDG_CACHE_HOME:-$HOME/.cache}/code-refactor/my-run"
mkdir -p "$CACHE_DIR"
scripts/detect-smells --json --min-confidence medium src > "$CACHE_DIR/smells.json"
scripts/plan-refactor --json --max-findings 20 "$CACHE_DIR/smells.json"
```

`plan-refactor` does not rescan source files. It ranks `files[].smells[]` from a
`detect-smells` JSON report, selects the first catalog-backed refactoring for
each planned finding, and emits the first safe step, preconditions, test focus,
risks, and a rerun command. The default `--group-by file` mode round-robins
findings across hotspot files; use `--group-by finding` for raw finding order.

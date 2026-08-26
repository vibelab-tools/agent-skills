# Tooling Reference

The Skill wrappers call the offline packaged JAR:

```bash
scripts/review-changes
scripts/analyze-complexity --json <path>...
scripts/detect-smells --json --min-confidence high <path>...
scripts/detect-smells --json --min-confidence high --history-analysis git <path>...
scripts/plan-refactor --json --max-findings 5 <confirmed-report.json|->
```

## Scope And Output

- Let `review-changes` select changed production files for post-change review;
  pass paths explicitly only to the lower-level analyzers.
- `review-changes` is the default post-change command. It selects the current
  Git working-tree diff, excludes common non-production paths, runs all 24
  standard detectors, and emits at most three high-confidence smell candidates
  whose reported scope was materially touched by changed lines.
- `review-changes --smells` remains accepted for compatibility but is identical
  to the default command.
- Directory input is reserved for an explicitly requested module/project audit.
- Save JSON outside the repository and inspect only summaries and relevant
  symbols; do not load a large report into model context wholesale.
- Smell detection is parser-backed and may return zero findings. It does not
  synthesize keyword-only fallback findings when structured detectors find
  nothing.
- `plan-refactor` ranks existing findings. It does not validate them and must
  not be used before manual confirmation.

## Common Options

```text
--json                      Emit machine-readable JSON.
--format json|text          Explicit output format.
--language <id>             Force one language for all file inputs.
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
--group-by file|finding     Plan grouping for plan-refactor.
--max-findings-per-file <n> Maximum planned findings per file.
--fail-on-parse-error       Return non-zero when parse errors are found.
--no-default-excludes       Include generated/vendor paths.
```

Supported language IDs:

```text
java bash c cpp csharp go rust html css javascript typescript tsx vue ruby sql python
```

## Useful JSON Fields

Changed-code review reports (`schema_version: 2.0`):

- `summary.changed_production_files`
- `summary.detectors_run`
- `summary.candidates_found`
- `summary.candidates_reported`
- `summary.candidates_omitted`
- `candidates[].id`
- `candidates[].location`
- `candidates[].changed_line_count`
- `candidates[].evidence`
- `decision`
- `warnings`

Complexity reports:

- `summary.max_cyclomatic_complexity`
- `summary.max_cognitive_complexity`
- `files[].functions[].location`
- `files[].functions[].metrics`
- `files[].classes[].location`
- `files[].classes[].metrics`
- `files[].parse_errors`

Smell reports:

- `summary.total_smells`
- `files[].smells[].id`
- `files[].smells[].confidence`
- `files[].smells[].location`
- `files[].smells[].evidence`
- `files[].smells[].recommended_refactorings`

Exit codes:

- `0`: analysis ran and emitted a report, including a valid empty report.
- `1`: invalid invocation or input path error.
- `2`: internal tool error.
- `3`: parse errors were found with `--fail-on-parse-error`.

Smells and complexity warnings do not fail the command by default.

---
name: git-commit
description: Write, validate, review, and revise Git commit messages using Conventional Commits 1.0.0. Use when an AI coding agent needs to draft a commit message, create an actual git commit, rewrite a non-compliant message, review existing commit text, choose a commit type/scope, describe breaking changes, or convert staged repository changes into a clear Conventional Commit.
---

# Git Commit

## Basis

Base commit-message decisions on Conventional Commits 1.0.0:

```text
<type>[optional scope][optional !]: <description>

[optional body]

[optional footer(s)]
```

Treat the official specification as the compatibility baseline. Apply the stricter defaults below unless the repository has its own commitlint config, contributing guide, or clear existing convention.

## Workflow

1. Inspect the actual change before writing a message. Prefer `git diff --staged`; if nothing is staged and the user is asking for a message only, inspect `git diff`. If creating a real commit, stage only the intended files or ask when intent is ambiguous.
2. Identify the single main behavior change. If unrelated changes are mixed, recommend splitting commits; if a split is not practical, choose the dominant type and describe the combined outcome honestly.
3. Check repository conventions before overriding them. Look for recent commit history and files such as `.commitlintrc*`, `commitlint.config.*`, `CONTRIBUTING*`, or release docs when present.
4. Draft the shortest compliant message that explains the resulting behavior, not a file list.
5. Validate the final message against the checklist before presenting it or running `git commit`.

## Header Rules

Use exactly one header line:

```text
<type>[optional scope][optional !]: <description>
```

- Require `type`, colon, one space, and a non-empty description.
- Use `scope` only when it adds useful location or domain context.
- Place `!` immediately before the colon only for breaking changes.
- Keep the header concise. Default maximum: 72 characters unless the repository enforces a different limit.
- Write the description in the imperative present tense: `add`, `fix`, `remove`, `rename`, `defer`, `validate`.
- Prefer English unless the repository history or the user explicitly uses another language.
- Do not end the description with a period.
- Do not use vague descriptions such as `update files`, `misc changes`, `work in progress`, `fix stuff`, or `changes`.

## Type Selection

Default to this type set unless project rules say otherwise:

- `feat`: Add a user-facing, API-facing, or product capability.
- `fix`: Correct defective behavior.
- `docs`: Change documentation only.
- `style`: Change formatting, whitespace, lint-only style, or presentation with no behavior change.
- `refactor`: Restructure code without changing behavior or intentionally improving performance.
- `perf`: Improve performance.
- `test`: Add or change tests only.
- `build`: Change build system, dependencies, package metadata, lockfiles, or generated build inputs.
- `ci`: Change CI/CD configuration or automation.
- `chore`: Perform maintenance that does not fit the types above.
- `revert`: Revert a previous change.

Do not hide behavior changes under `chore`. Prefer `fix` for bug regressions, `feat` for new capabilities, and `refactor` only when externally visible behavior is intended to remain the same.

## Scope Rules

- Use lowercase noun-like scopes such as `api`, `auth`, `homepage`, `worker`, `docs`, `desktop`, or a package name.
- Avoid spaces in scopes. Prefer hyphens for multiword scopes.
- Avoid scopes that are too broad to add signal, such as `app`, `code`, or `files`, unless the repository already uses them.
- Omit the scope when the change spans multiple areas and no single domain is accurate.

## Body Rules

Include a body when the change needs context beyond the header:

- Separate the body from the header with one blank line.
- Explain why the change was made, what changed at a behavioral level, and any important tradeoffs or migration notes.
- Wrap body lines around 72 characters when practical.
- Use paragraphs or bullets only when they make the message easier to read.
- Do not repeat an exhaustive file list.

## Footer Rules

Use footers for machine-readable trailers and breaking-change notes:

- Separate footers from the body with one blank line.
- Use `Token: value` or `Token #value` form, for example `Refs: #123`.
- Use hyphens instead of spaces in footer tokens, for example `Reviewed-by`.
- Prefer `BREAKING CHANGE: <description>` for breaking-change footer text. Accept `BREAKING-CHANGE: <description>` when reviewing existing messages because the spec treats it as synonymous.
- Include migration impact when a breaking change is known.
- Keep issue references, co-authors, signed-off-by trailers, and other metadata in footers, not in the header.

## Breaking Changes

Mark a breaking change when the commit changes public API, CLI behavior, persisted data shape, configuration contract, compatibility assumptions, or user-visible behavior in a way that existing consumers may need to change.

Use both a header marker and a footer when enough context is available:

```text
feat(api)!: require explicit export formats

BREAKING CHANGE: callers must pass an export format instead of relying on
the previous JSON default.
```

If the user asks for a compact one-line message and the breaking impact is already obvious, the `!` marker is still required.

## Output Behavior

- When asked to draft a message, return only the commit message in a fenced `text` block unless a short rationale is useful.
- When asked to validate or review a message, lead with `Valid` or `Invalid`, then list concrete violations and provide a corrected message.
- When asked to create a commit, show the final message and run `git commit` with that message after confirming the staged changes are the intended changes.
- When multiple valid messages are plausible, provide one best default and, if useful, one alternative with a short reason.

## Validation Checklist

Before finalizing, confirm:

- The header matches Conventional Commits shape.
- The type reflects the actual behavior change.
- The scope is useful and syntactically simple, or omitted.
- The description is imperative, concise, specific, and has no trailing period.
- Body and footers are separated by blank lines when present.
- Breaking changes are marked with `!`, `BREAKING CHANGE:`, and/or `BREAKING-CHANGE:`.
- Repository-specific conventions take precedence over the defaults in this skill.

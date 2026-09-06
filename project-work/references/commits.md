# Commits

Use this reference only when drafting, reviewing, or creating a commit, or when
Issue delivery reaches publication.

## Inspect First

Inspect `git diff --staged`; for a message-only request with nothing staged,
inspect `git diff`. Stage only the intended files. Read repository commit rules
when they are not already known; consult recent history only if conventions
remain unclear before applying the default below. Split unrelated changes
when practical.

## Conventional Commit Default

```text
<type>[optional scope][optional !]: <description>

[optional body]

[optional footer(s)]
```

Use a concise imperative description with no trailing period; keep the header
within 72 characters unless the project says otherwise. Use a useful lowercase
scope or omit it. Default types are `feat`, `fix`, `docs`, `style`, `refactor`,
`perf`, `test`, `build`, `ci`, `chore`, and `revert`. Do not hide behavior
changes under `chore`.

Mark breaking API, CLI, data, or configuration changes with `!` and, when
context is available, a `BREAKING CHANGE:` footer.

## Link Issues Correctly

Every Issue task commit references its Issue in a footer:

```text
Refs #123
```

Use `Closes #123` only when publishing the commit itself completes every
closure criterion and no post-push CI, deployment, or product acceptance
remains. Otherwise use `Refs #123` and close explicitly after remote
verification. Isolated and multi-Issue worker commits and review requests
always use `Refs`. Use a cross-project reference or full URL when `#123` is
ambiguous.

## Keep the Requested Boundary

A message draft or review does not authorize staging, committing, or pushing.
A local commit request does not authorize a push. In Issue delivery, publish
verified work through the repository workflow unless the user prohibits that
action. Never force-push, bypass protection, include unrelated changes, or
ignore failed checks.

When reviewing a message, lead with `Valid` or `Invalid` and provide concrete
corrections. When creating a commit, verify the staged diff immediately before
running `git commit`.

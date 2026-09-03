# Commit Messages and Publication

Use this reference when drafting, reviewing, or creating Git commits, and when
publishing issue-backed work.

In commit-only work, do not create an Issue and do not infer staging,
committing, or pushing from a request that asks only for a message draft or
review. Issue references apply only when the commit actually belongs to an
Issue-backed task.

## Inspect Before Writing

1. Inspect `git diff --staged`. If nothing is staged and the request is only to
   draft a message, inspect `git diff`.
2. When creating a real commit, stage only the intended files. Stop if the
   intended subset is materially ambiguous.
3. Inspect recent history and repository rules such as `.commitlintrc*`,
   `commitlint.config.*`, `CONTRIBUTING*`, and release documentation.
4. Identify the single main behavior change. Split unrelated changes when
   practical.
5. Draft the shortest message that explains the resulting behavior rather
   than listing files.

## Conventional Commit Default

Use Conventional Commits 1.0.0 unless the repository defines another format:

```text
<type>[optional scope][optional !]: <description>

[optional body]

[optional footer(s)]
```

Header rules:

- Require a type, colon, one space, and a non-empty description.
- Keep the header at or below 72 characters unless the repository specifies a
  different limit.
- Use an imperative present-tense description such as `add`, `fix`, `remove`,
  `rename`, `defer`, or `validate`.
- Do not end the description with a period.
- Use a lowercase noun-like scope only when it adds useful domain context.
- Prefer the repository's established language; otherwise use English.

Default types:

- `feat`: add user-, API-, or product-facing capability.
- `fix`: correct defective behavior.
- `docs`: change documentation only.
- `style`: change formatting with no behavior change.
- `refactor`: restructure without intended behavior change.
- `perf`: improve performance.
- `test`: change tests only.
- `build`: change dependencies, packaging, or build inputs.
- `ci`: change CI/CD automation.
- `chore`: maintenance not covered above.
- `revert`: revert a previous change.

Do not hide behavior changes under `chore`. Add a body only when the header
cannot explain why the change exists, its behavioral effect, or an important
tradeoff. Separate the body and footers with blank lines and wrap body text at
about 72 characters when practical.

Write machine-readable footers as `Token: value` or `Token #value`. Use hyphens
instead of spaces in multiword tokens such as `Reviewed-by`, and keep issue
references, co-authors, sign-offs, and breaking-change notes out of the header.

## Breaking Changes

Mark a breaking change when consumers must adapt to a changed public API, CLI,
persisted data shape, configuration contract, or compatibility assumption.
Use `!` in the header and a `BREAKING CHANGE:` footer when enough context is
available:

```text
feat(api)!: require explicit export formats

BREAKING CHANGE: callers must pass an export format instead of relying on
the previous JSON default.
```

For a requested one-line message, the `!` marker remains required when the
change is breaking. Accept `BREAKING-CHANGE:` when reviewing an existing
message because the specification treats it as equivalent.

## Issue References

Every commit belonging to an issue-backed task must reference that issue in a
footer. Use `Refs` for an intermediate commit:

```text
fix(scope): correct the failing behavior

Refs #123
```

Use `Closes` only when the commit completes the issue and all required closure
criteria can be satisfied:

```text
feat(scope): deliver the requested capability

Closes #123
```

Use `owner/repository#123` for another GitHub repository,
`group/project#123` for another GitLab project, or the full URL when the short
form is ambiguous. When commits will be squashed, ensure the surviving message
retains the reference.

## Publish Verified Work

This section applies to Issue delivery, or when a commit-only request explicitly
includes publication. After focused checks pass and the diff is reviewed,
commit and push verified Issue-delivery work through the repository's normal
workflow unless the user prohibited that action. A draft, review, or local
commit request alone does not authorize a push.

Publication never authorizes force-pushing, bypassing branch protection,
publishing unrelated changes, or ignoring failed checks.

Before pushing, read the delivery reference when CI/CD or environment changes
may result. If normal publication requires unavailable credentials or review
authority, preserve the valid local commit when allowed, keep the issue open,
and report the exact remaining action.

## Output and Validation

When asked only to draft a message, return the best message in a fenced `text`
block unless a short rationale is useful. When reviewing a message, lead with
`Valid` or `Invalid`, list concrete violations, and provide a corrected
message. When asked to create a commit, show the final message and run
`git commit` after confirming the staged change is the intended change. When
several messages are valid, provide one best default and at most one useful
alternative.

Before finalizing, confirm the header shape, type, scope, imperative
description, length, body/footer separation, breaking-change marker, and issue
reference all match the actual diff and repository convention.

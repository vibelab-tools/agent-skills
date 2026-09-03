# GitHub and GitLab Issue Workflow

Use this reference for requirement or defect capture, execution-plan issues,
provider comments, screenshot evidence, checklist reconciliation, and issue
closure.

Long-running sessions may outlive a skill installation. Before each provider
mutation, reread the `Required Outcomes` section from the current installed
copy of this skill rather than relying on an older in-context copy.

## Identify the Repository and Provider

Run read-only discovery first:

```bash
git rev-parse --show-toplevel
git status --short --branch
git remote -v
```

For a normal checkout, require the resolved root's `.git` directory and
inspect its local configuration:

```bash
git config --file <repo-root>/.git/config \
  --get-regexp '^remote\..*\.url$'
```

A linked worktree or submodule may use a `.git` file. Verify it with
`git rev-parse --git-dir` and inspect `git config --local` instead. Treat
configured remote URLs, not a folder name or source archive, as proof that the
project is connected to GitHub or GitLab.

Select the remote tracked by the current branch when possible, then the
documented authoritative remote, then `origin`. If remotes point to both
providers and authority is unclear, ask before mutation.

- Use `gh` for every GitHub issue operation.
- Use `glab` for every GitLab issue operation.
- Check the selected host's CLI authentication before mutating work.
- If no authoritative GitHub or GitLab remote exists, continue without
  inventing an issue tracker.

Do not replace a failed provider CLI with the other provider's CLI, a local
TODO, or an untracked note.

## Reuse or Create the Right Issue

If the user, branch, commit, pull request, or merge request identifies an
issue:

1. View it with the matching provider CLI.
2. Confirm that it belongs to the authoritative repository.
3. Use it directly when it matches one independently completable task.
4. If it is a larger source requirement or defect, create and link one issue
   for each independently completable execution task.
5. Reopen it or create a linked follow-up when more work is needed; do not add
   unrelated work silently to a closed issue.

For each unassigned task, search open issues for the same outcome before
creating a duplicate. Use the repository's issue templates and existing
labels when applicable. Do not invent labels solely for this workflow.

Choose granularity by observable deliverable, not by command. A small change
can use one task issue. Split work with independent outcomes, dependencies, or
verification. Create all known implementation issues before the first code
edit. If implementation reveals a new task, add it to the plan and create or
map its issue before doing that work.

Record the issue number, URL, dependency, and status beside every plan task.
When a source issue expands into task issues, add their URLs to the source
issue so the plan survives context compaction.

## Language and Content

Choose the issue language from the target project, in this order:

1. Explicit repository instructions and issue templates.
2. Recent issues.
3. The dominant README and documentation language.

Ask before the first issue only when a mixed-language project has no clear
working language. Keep titles, bodies, plan links, comments, and closing notes
consistent. Preserve identifiers and exact error text when translation would
reduce accuracy. Determine commit-message language separately.

A task issue should contain only durable context:

```markdown
## Goal

<observable outcome>

## Context

<problem, requirement, or reproduction evidence>

## Acceptance criteria

- [ ] <verifiable result>
- [ ] <required validation>

## Constraints

<only constraints that affect implementation>

## Plan relationships

- Source: <requirement, defect, or tracking issue URL when applicable>
- Depends on: <issue URLs or "None">
```

## Preserve Multiline Markdown

For every multiline create, edit, or comment, use
`scripts/issue_markdown.py` from this skill. The helper reads actual Markdown
from standard input, rejects literal `\n` by default, performs the provider
mutation, reads the stored object back, and requires an exact Markdown match.

GitHub example:

```bash
python3 <skill-directory>/scripts/issue_markdown.py \
  --provider github --action comment \
  --repo <owner/repo> --issue <id> <<'MARKDOWN'
Summary

- First result
- Second result
MARKDOWN
```

GitLab example:

```bash
python3 <skill-directory>/scripts/issue_markdown.py \
  --provider gitlab --action comment --hostname <host> \
  --repo <group/project> --issue <id> <<'MARKDOWN'
Summary

- First result
- Second result
MARKDOWN
```

Use `--action create --title <title>` for creation and
`--action edit --issue <id>` for a body edit. Use
`--allow-literal-newlines` only when the two characters `\n` are intentional
content. Never bypass provider mode with a quoted `--raw-field`, `--message`,
or equivalent value containing escaped newlines.

For one-line read or state operations, use explicit repository selection:

| Operation | GitHub | GitLab |
| --- | --- | --- |
| View | `gh issue view <id> --repo <owner/repo>` | `glab issue view <id> --repo <group/project>` |
| Close | `gh issue close <id> --repo <owner/repo> --reason completed` | `glab issue close <id> --repo <group/project>` |

## Attach Screenshot Evidence

Treat screenshots supplied with a task as potential evidence, especially for
defects, visual regressions, UI requirements, and environment-specific
behavior. Do not attach decorative, redundant, or unrelated images.

Before uploading:

1. Confirm which task the screenshot supports and what it proves.
2. Require an accessible local file or approved durable URL. Do not invent a
   path for an image visible only in chat.
3. Inspect for credentials, session data, personal or customer data, private
   source, internal hostnames, and unrelated screen content. Request a
   redacted copy or omit the image when needed.
4. Check project visibility and attachment policy. A public issue attachment
   is public; do not use an unapproved third-party host.
5. Preserve legibility and aspect ratio. Add alt text and a short caption with
   observed behavior, expected behavior, and relevant environment.

GitLab supports native project uploads through `glab`:

```bash
glab api --method POST projects/:fullpath/uploads \
  --form "file=@/absolute/path/to/screenshot.png"
```

Insert the returned `markdown` value into the issue through the helper, then
read it back. Keep the provider URL; never substitute a local path.

GitHub's issue CLI commands do not accept local attachment files. Use an
existing approved durable URL, interactive native browser upload, or a
project-approved image host. Otherwise record complete textual evidence and
state that attachment is pending. Do not create a release, gist, commit, or
unapproved upload solely to host a screenshot.

Report an image as attached only after the issue contains a durable reference
that is accessible in the relevant authenticated context.

## Build Compatible GitLab Web URLs

Prefer a non-empty URL returned by `glab` or the provider API. For a
self-managed GitLab instance, pass it through the bundled helper rather than
assuming the current GitLab.com route:

```bash
python3 <skill-directory>/scripts/gitlab_web_url.py \
  --project-url "$(git remote get-url origin)" \
  --kind commit \
  --id "$(git rev-parse HEAD)"
```

The helper accepts HTTP(S), SSH, and scp-style remotes. It queries the instance
version and uses legacy project routes before GitLab 12 or `/-/` routes from
GitLab 12 onward. If detection fails and no provider URL is available, it uses
the redirect-compatible legacy route. It also respects the host's `glab`
`api_protocol` setting. Never expose the token used by `glab` during
detection.

## Keep the Issue Useful

Add comments only for information that would be costly to lose:

- accepted scope or acceptance-criteria changes;
- root cause or an architectural decision that controls the solution;
- a blocker and the exact next step required to resume;
- final commit, verification, delivery evidence, and residual limitations.

After context compaction or a resumed session, read the issue and its comments,
then inspect status, the current diff, and recent commits. Do not rely on
recalled chat context when those sources differ.

## Reconcile and Close

Treat every task-list item as durable scope:

- Change `[ ]` to `[x]` only after the criterion's evidence passes.
- Preserve an inapplicable, superseded, or cancelled criterion as a
  non-checkbox strikethrough bullet with a concise reason, for example
  `- ~~Original criterion~~ — Removed: <reason>`.
- Never silently delete a criterion or mark unfinished work complete.
- Any unchecked task-list item anywhere in the issue body blocks closure.

Edit the reconciled body with provider mode and `--check-closure`:

```bash
python3 <skill-directory>/scripts/issue_markdown.py \
  --check-closure --provider github --action edit \
  --repo <owner/repo> --issue <id> <<'MARKDOWN'
<complete reconciled issue body>
MARKDOWN
```

Use the corresponding GitLab arguments for a GitLab issue. If the body needs
no edit, pipe the provider body into the helper with `--check-closure` before
closing.

Run this gate before pushing a completion-bearing commit directly to the
default branch, because `Closes #123` may close the issue automatically. In a
branch-and-review workflow, run it after required review passes and before the
merge that will apply the closing reference.

Do not close an issue merely because a local commit exists. Make the final
commit or merged review request visible on the authoritative remote and ensure
required environment acceptance is complete. Add a concise final comment with
the commit or review URL and checks run. Then close the issue if the provider
did not close it automatically and verify its final state with the provider
CLI.

On a direct default-branch workflow, the final `Closes #123` commit may close
the issue when pushed. On a branch-and-review workflow, keep it open until the
pull request or merge request is merged and its surviving commit message still
contains the closing reference. If credentials or review authority block
publication, keep the issue open and report the exact remaining action.

---
name: manage-work-with-issues
description: Manage repository requirements, defects, and execution plans through GitHub or GitLab issues so scope, dependencies, decisions, verification, and completion survive context compaction. Use whenever Codex is analyzing, planning, implementing, fixing, refactoring, documenting, testing, configuring, building, or releasing work in a Git repository whose authoritative remote is GitHub or GitLab; record concrete requirements and defects, turn each independently completable execution-plan task into an issue before coding, use `gh` for GitHub and `glab` for GitLab, require issue-linked commits, and close each issue only after verified completion is visible remotely.
---

# Manage Work With Issues

Treat the provider issue as the durable task record. Keep chat concise, but
preserve the task's goal, material decisions, verification, and final state in
GitHub or GitLab.

## Required Outcomes

1. Record a concrete requirement or defect in a source issue when applicable.
2. Create an execution plan when requirements and technical design are settled.
3. Map every independently completable plan task to an authoritative issue
   before coding starts.
4. Use `gh` only for GitHub and `glab` only for GitLab.
5. Reference the current task issue from every commit belonging to it.
6. Keep each issue open while its work or verification remains.
7. Make the commit visible on the hosted repository before closing the issue.
8. Write issue content in the target project's working language.
9. Never place credentials, tokens, private customer data, or sensitive logs in
   an issue or commit message.

## 1. Identify the Repository and Provider

Run read-only discovery first:

```bash
git rev-parse --show-toplevel
test -d <repo-root>/.git
git config --file <repo-root>/.git/config \
  --get-regexp '^remote\..*\.url$'
git status --short --branch
git remote -v
```

Use the first command to resolve the project root; do not assume the current
directory is the root. For a normal checkout, require the root `.git` directory
and inspect `.git/config` directly. Treat the configured `remote.*.url` entries,
not merely a folder name or an untracked source archive, as proof that the
project is connected to GitHub or GitLab.

Git linked worktrees and some submodules use a `.git` file instead of a
directory. When that file is present, verify it with `git rev-parse --git-dir`
and inspect the equivalent local configuration with:

```bash
git config --local --get-regexp '^remote\..*\.url$'
```

If neither valid Git metadata form exists, or the local configuration has no
GitHub or GitLab remote URL, this workflow does not apply.

Select the matching remote tracked by the current branch when possible;
otherwise use the repository's documented authoritative remote, then `origin`.
Recognize both SSH and HTTPS remote forms, including GitHub Enterprise and
self-managed GitLab.

- For a GitHub remote, use `gh` for every issue operation.
- For a GitLab remote, use `glab` for every issue operation.
- If remotes point to both providers and the authoritative host is unclear, ask
  before creating an issue.
- If no GitHub or GitLab remote exists, state that this workflow does not apply
  and continue without inventing an issue tracker.

Check authentication for the selected host before starting mutating work. Do
not replace a failed provider CLI with the other provider's CLI, a local TODO,
or an untracked note.

## 2. Turn the Execution Plan Into Issues

Record a concrete incoming requirement or defect in a source issue when it is
not already tracked. Requirements analysis, repository discovery, and technical
design may then happen before implementation issues are created. Once the design
is settled and the work is ready to enter coding, write a short execution plan
based on observable outcomes. Before editing code, map every plan task to an
issue.

Choose issue granularity by deliverable, not by command:

- Give each independently completable and verifiable implementation task its
  own issue.
- Let a small change use one plan task and one issue.
- Split larger work when tasks have different outcomes, dependencies, or
  validation and can be closed independently.
- Do not create separate issues for mechanical steps such as opening a file,
  running one command, writing a test, or committing when those steps only
  support the same outcome.
- Do not hide several plan tasks in one broad issue merely to reduce issue
  count.

Record the issue number, URL, dependency, and status beside every task in the
working plan. Create all known plan-task issues before starting the first code
change. If implementation reveals a new task, add it to the plan and create its
issue before doing that new work.

If the user, branch, commit, pull request, or merge request already identifies
an issue:

1. View it with the provider CLI.
2. Confirm it belongs to the authoritative repository.
3. Use it directly when it matches one execution-plan task.
4. Treat it as the source or tracking issue when it contains a larger
   requirement or defect, then create and link one issue for each execution
   task.
5. Reopen it or create a follow-up issue when additional work is required; do
   not silently attach unrelated work to a closed issue.

For each unassigned plan task, search open issues for the same outcome before
creating a new one. Use the repository's issue template and existing labels
when applicable. Do not invent labels just for this workflow.

### Choose the Issue Language

Choose the language from the target project, not automatically from the user's
chat language. Check, in order:

1. Explicit repository instructions and issue templates.
2. The language used by existing recent issues.
3. The dominant language of the README and project documentation.

Use English when the project is English. Use Chinese when the project's
documentation is primarily Chinese. If a mixed-language project has no clear
working language, ask before creating the first issue. Keep issue titles,
bodies, plan links, progress comments, and closing notes consistent in that
language. Preserve code identifiers and exact error text when translating them
would reduce accuracy. Determine commit-message language separately from the
repository's commit conventions and recent history.

Write an issue body with only durable task context:

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

Use explicit repository selection to avoid writing to the wrong project:

| Operation | GitHub | GitLab |
| --- | --- | --- |
| View | `gh issue view <id> --repo <owner/repo>` | `glab issue view <id> --repo <group/project>` |
| Create | `gh issue create --repo <owner/repo> --title <title> --body <body>` | `glab issue create --repo <group/project> --title <title> --description <body> --yes` |
| Comment | `gh issue comment <id> --repo <owner/repo> --body <text>` | `glab issue note <id> --repo <group/project> --message <text>` |
| Close | `gh issue close <id> --repo <owner/repo> --reason completed` | `glab issue close <id> --repo <group/project>` |

When a source issue expands into multiple execution issues, add their URLs to
the source issue so the complete plan remains recoverable after compaction.

## 3. Keep the Issue Useful as Durable Context

Add concise comments only when information would be costly to lose:

- accepted scope or acceptance criteria change;
- root cause or architectural decision that controls the solution;
- a blocker and the exact next step needed to resume;
- final commit, verification results, and any residual limitation.

Do not post routine progress, speculative debugging, secrets, or large raw logs.
Summarize evidence and link to durable artifacts instead.

After context compaction or a resumed session, read the issue and its comments,
then inspect `git status`, the current diff, and recent commits before continuing.
Do not rely on recalled chat context when it conflicts with those sources.

## 4. Implement, Verify, and Commit

Implement only the current issue scope and run the repository's relevant checks.
Prefer completing one issue at a time unless the plan explicitly marks tasks as
parallel. Review the final diff before committing.

Reference the issue in every task commit. Preserve the repository's existing
commit convention for the header and place the issue reference in the footer.
Use an unambiguous full issue URL when the commit and issue are in different
repositories.

Avoid combining multiple issues in one commit. If an indivisible commit really
does implement more than one plan task, reference every affected issue and do
not close any of them until its own acceptance criteria pass.

Use `Refs` for intermediate commits:

```text
fix(scope): correct the failing behavior

Refs #123
```

Use a provider-supported closing keyword only on the final commit that actually
completes the issue:

```text
feat(scope): deliver the requested capability

Closes #123
```

When commits will be squashed, ensure the surviving commit message still
references the issue. Do not create an empty commit for a no-code investigation;
record its verified findings in the issue instead.

## 5. Publish and Close

Do not close an issue merely because a local commit exists. Push the referencing
commit according to repository policy, or merge the pull request or merge
request, so the association is visible on GitHub or GitLab.

- On a direct default-branch workflow, push the final `Closes #123` commit and
  verify whether the provider closed the issue automatically.
- On a branch-and-review workflow, keep the issue open until the pull request or
  merge request is merged. Keep commit footers linked with `Refs #123`, and add
  the closing reference to the final integration commit or review request.
- If pushing or merging requires authority that is not available, leave the
  issue open, add a concise blocker comment when possible, and report the exact
  remaining action.

After the commit is remotely visible and all acceptance criteria pass, add a
short final comment containing the commit or review-request URL and the checks
run. If the provider did not auto-close the issue, close it explicitly with the
correct CLI. For GitLab, add the final note before `glab issue close`; for
GitHub, `gh issue close --comment <text> --reason completed` may combine them.

Finally, verify the remote state:

```bash
gh issue view <id> --repo <owner/repo> --json state,url
glab issue view <id> --repo <group/project> --output json
```

Report completion only when the issue is closed and the linked commit is
visible remotely. If later verification reveals a regression, reopen the issue
or create a clearly linked follow-up rather than hiding the failure.

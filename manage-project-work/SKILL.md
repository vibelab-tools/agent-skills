---
name: manage-project-work
description: Manage Git repository work from requirement or defect capture through issue-backed planning, implementation, Conventional Commits, publication, delivery, release, and verified closure. Use whenever Codex or Claude Code analyzes, plans, implements, fixes, refactors, documents, tests, configures, builds, deploys, or releases work in a GitHub- or GitLab-hosted repository, and when it drafts or reviews a commit message for any Git repository. Preserve repository-specific workflows and authorization boundaries.
---

# Manage Project Work

Treat the repository's issue and delivery policy as the durable contract for
the work. Keep chat concise while preserving the goal, scope, decisions,
verification, delivery evidence, and final state in the systems that govern
the project.

## Select the Relevant Guidance

Read only the references needed for the current operation:

- For a GitHub or GitLab requirement, defect, execution plan, issue update,
  screenshot, or closure, read [references/issues.md](references/issues.md).
- Before drafting, reviewing, or creating a commit, read
  [references/commits.md](references/commits.md).
- Before creating or changing a work branch, pushing when CI/CD may run,
  deploying, promoting, versioning, tagging, releasing, rolling back, or
  claiming environment acceptance, read
  [references/delivery.md](references/delivery.md).

A message-only commit request does not require creating an issue. Inspect the
actual diff and repository convention, then use the commit reference. A
repository change follows the complete lifecycle below when its authoritative
remote is GitHub or GitLab.

## Required Outcomes

1. Record a concrete incoming requirement or defect in a source issue when it
   is not already tracked.
2. Settle the requirement and technical design before turning the execution
   plan into issues.
3. Map every independently completable plan task to one authoritative issue
   before editing files for that task.
4. Implement only the current issue scope and verify its observable acceptance
   criteria at the cheapest meaningful boundary.
5. Reference the task issue from every associated commit. Use `Closes #123`
   only on the final commit that completes it.
6. Commit and publish verified issue work through the repository's normal
   workflow unless the user explicitly prohibits the corresponding action.
7. Preserve the exact commit or artifact identity through environment
   acceptance whenever the project delivery contract depends on it.
8. Keep the issue open until required code, remote, CI/CD, environment, and
   product acceptance are complete.
9. Before closure, reconcile every issue task-list item and verify that the
   completion-bearing commit is visible on the authoritative remote.

## Establish Scope and Sources of Truth

Resolve the repository root and inspect its actual state before deciding what
to do:

```bash
git rev-parse --show-toplevel
git status --short --branch
git remote -v
git branch --show-current
```

Read applicable `AGENTS.md`, contributing guidance, issue templates, commit
configuration, branch and release documentation, CI/CD configuration, and any
issue named by the user, branch, commit, pull request, or merge request.
Executable CI/CD configuration governs actual automation when documentation
disagrees with it; report the inconsistency instead of silently choosing one.

Use configured remotes, not the directory name, to identify the provider. Use
`gh` only for GitHub and `glab` only for GitLab. If the repository has no
authoritative GitHub or GitLab remote, continue with normal Git work without
inventing an issue tracker. Ask only when multiple remotes make authority
materially ambiguous.

Repository-specific instructions override this skill's defaults for issue
templates, language, branch names, commit style, review, merge strategy,
deployment, versioning, and tags. The user's current request controls scope
and authorization.

## Capture the Requirement and Plan

Use an existing matching issue when one exists. Otherwise record the incoming
requirement or defect with an observable goal, context, verifiable acceptance
criteria, material constraints, and relationships. Requirements analysis,
repository discovery, and technical design may happen in the source issue
before implementation issues exist.

When the design is ready for implementation, split the execution plan by
independently completable and verifiable outcomes. A small change normally has
one task and one issue. Do not create separate issues for mechanical steps
such as reading a file, running a check, or creating the task's commit.

Record only durable information in provider comments: accepted scope changes,
decisions that control the solution, blockers with exact resume steps, and
final verification or delivery evidence. Do not publish routine progress,
credentials, private customer data, sensitive logs, or unrelated details.

## Implement and Verify

Preserve unrelated worktree changes. Change only what the current issue
requires and keep any newly discovered task out of scope until it has been
added to the plan and mapped to an issue.

Verify behavior against the acceptance criteria. Prefer a small number of real
boundary checks over internal or mock-driven checks. Review the final diff for
correctness, unintended edits, and unnecessary complexity before committing.
Record residual limitations honestly and leave their criteria open.

## Commit, Publish, Deliver, and Close

Stage only the current task's files. Follow the repository's commit convention
and add the issue reference as a footer. Do not combine unrelated issues in a
commit or create an empty commit for an investigation.

Before pushing, determine whether the push triggers CI, a shared development
environment, artifact publication, or production. Normal publication of
verified task work is the default, but that default does not authorize
production changes, force pushes, bypassing branch protection, merging without
required review, destructive recovery, moving release tags, or publishing
unrelated changes.

Treat production promotion or deployment as a separate action requiring
explicit authorization in the current task. Preserve and verify the artifact
identity required by the project. A changed commit invalidates evidence tied
to its previous SHA and must pass the required pipeline and acceptance again.

Close an issue only after all required implementation, verification,
publication, delivery, and product acceptance are complete. Reconcile the
issue body, make the completion-bearing commit visible remotely, add concise
final evidence, and verify the provider reports the issue closed. If later
evidence shows a regression, reopen the issue or create a linked follow-up.

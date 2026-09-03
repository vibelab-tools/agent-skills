---
name: project-work
description: Manage explicitly requested or repository-required GitHub and GitLab Issue workflows, commit-message work, and branch, delivery, or release operations. Use when the user asks to create, inspect, update, decompose, deliver, or close an Issue; supplies an Issue as the task authority; asks to draft, review, or create a commit; asks for branch, push, deployment, promotion, version, tag, or release work; or repository instructions require one of these workflows. Do not use merely because ordinary explanation, diagnosis, editing, testing, or building occurs in a Git repository.
---

# Project Work

Use this Skill only for a workflow explicitly requested by the user or required
by the repository. Do not turn an ordinary repository task into an Issue,
commit, or delivery workflow merely because the repository has a GitHub or
GitLab remote.

## Select One Path

Choose the narrowest path that satisfies the request. Do not promote a narrow
request into a broader path without explicit direction.

- **Record-only:** Create or record the requested Issue, verify the stored
  result, and stop. Do not implement, commit, push, deploy, or close it.
- **Inspect-only:** Read and analyze the identified Issue. Do not mutate the
  provider or repository.
- **Issue delivery:** Use an Issue as the authority for requested
  implementation. Plan, implement, verify, commit, publish, deliver, and close
  only to the extent required by that Issue and the user's request.
- **Issue maintenance:** Perform only the requested Issue edit, comment, status
  change, or closure. Do not begin implementation unless separately requested.
- **Commit-only:** Draft, review, or create the requested commit from the actual
  diff. Do not create an Issue or infer a push from a message-only request.
- **Repository delivery:** Perform the requested branch, push, CI/CD,
  deployment, promotion, version, tag, release, or rollback work. Do not create
  an Issue unless the user or repository instructions require one.

An Issue number or URL must be part of the requested work, not merely an
incidental reference. If ordinary work grows enough to benefit from durable
tracking, recommend an Issue; do not create one without authorization from the
user or repository instructions.

## Load Only the Needed Guidance

- For any Issue path, read [references/issues.md](references/issues.md).
- Before drafting, reviewing, or creating a commit, read
  [references/commits.md](references/commits.md).
- Before creating or changing a branch, pushing when CI/CD may run, deploying,
  promoting, versioning, tagging, releasing, rolling back, or claiming
  environment acceptance, read [references/delivery.md](references/delivery.md).

Load each reference only when the selected path reaches that operation. Issue
delivery may use all three references, but record-only and inspect-only should
not load commit or delivery instructions.

Read the installed `SKILL.md` once when this Skill activates in the current
turn, then reuse it. Read it again after a session resume, context compaction,
or an installed-file change. Do not reread it before every provider mutation in
the same turn.

## Shared Boundaries

Inspect applicable `AGENTS.md`, repository state, remotes, Issue references,
commit conventions, and CI/CD or release policy only as needed for the selected
path. Repository-specific instructions take precedence for Issue templates,
language, branches, commits, review, merge strategy, deployment, versions, and
tags.

Preserve unrelated worktree changes and keep credentials, private customer
data, and sensitive logs out of Issues and commit messages. The user's request
controls scope and authorization. Recording or inspecting an Issue does not
authorize implementation; creating a commit does not automatically authorize a
push; development delivery does not authorize production.

Production promotion, deployment, rollback, and release tagging require the
authorization applicable to that exact action in the current task. Never infer
permission to force-push, bypass branch protection, merge without required
review, resolve delivery conflicts automatically, or publish unrelated changes.

## Outcomes by Path

For record-only, inspect-only, and Issue maintenance, perform only the selected
provider operation and verify the result at that boundary.

For Issue delivery:

1. Reuse or create the authoritative Issue only when the request or repository
   requires it.
2. Settle the requirement and technical design before mapping independently
   completable plan tasks to Issues.
3. Implement only the current Issue scope and verify its observable acceptance
   criteria.
4. Reference the Issue from its commits. Publish verified work through the
   repository's normal workflow unless the user prohibits the corresponding
   action.
5. Preserve any commit or artifact identity required by environment acceptance.
6. Keep the Issue open until required implementation, remote visibility,
   delivery, and product acceptance are complete.
7. Reconcile every Issue task-list item before closure and verify the final
   remote state.

For commit-only, inspect the actual staged or unstaged change and perform only
the requested draft, review, or commit action. Apply Issue footers only when the
commit actually belongs to an Issue-backed task.

For repository delivery, follow the project's executable CI/CD configuration
and documented delivery contract. Verify the requested remote, pipeline,
artifact, environment, or release result without introducing an Issue workflow
unless one is required.

In every path, report only checks and external state that were actually
verified. Leave unfinished work open and state the exact remaining boundary.

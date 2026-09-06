---
name: project-work
description: Manage explicitly requested or repository-required requirement, Issue, commit, delivery, and release workflows in GitHub or GitLab projects. Use for recording, inspecting, implementing, or closing Issues; commit or release work; and explicit isolated orchestration with `$project-work issue_id=...` or `$project-work parent_issue_id=...`. Do not use for routine Git inspection, synchronization, checkout, or discarding local changes (status, diff, fetch, pull, switch, restore, reset), or merely because ordinary editing, testing, building, explanation, or diagnosis occurs in a Git repository.
---

# Project Work

Supply project conventions and reusable experience that help complete the
request. Use only the guidance the task needs; the workflow is not an end in
itself. Do not turn ordinary repository work into an Issue or delivery process
merely because a remote exists.

## Routine Git Operations

Routine Git operations do not activate this Skill, even when they mention a
branch or call a local reset a rollback. If explicitly invoked, perform only
the requested operation without loading workflow references.

When the repository and target are known, an explicit discard request is
sufficient authorization; do not add confirmation, diff review, or backups.
For "discard local changes, then pull the current branch", run
`git reset --hard && git pull` with the required network routing. Successful
command output is enough to report completion and stop.

Additional inspection must resolve a concrete ambiguity, failure, or unclear
result. Do not add documentation or memory reads, builds, tests, or CI checks
unless the request or applicable instructions require them. Keep the operation
within the requested scope.

## Choose the Workflow

| Request | Workflow | Boundary |
| --- | --- | --- |
| Record a requirement or defect | Record-only | Create and verify the Issue, then stop |
| Read or analyze an Issue | Inspect-only | No provider or repository mutation |
| Edit, comment on, reopen, or close an Issue | Maintenance | Perform only the requested Issue operation |
| Implement one Issue | Single delivery | Follow the lifecycle below in the current agent |
| `$project-work issue_id=<issue>` | Isolated delivery | One worker, branch, worktree, and review request |
| `$project-work parent_issue_id=<issue>` | Multi-Issue delivery | Decompose when needed and run only safe work in parallel |
| Draft, review, or create a commit | Commit-only | Do not create an Issue or infer a push |
| Plan delivery branches, push, deploy, promote, version, tag, release, or roll back an environment | Repository delivery | Do not create an Issue unless requested or required |

An Issue workflow must be explicitly requested or repository-required; an
incidental Issue URL does not authorize mutation. If ordinary work would
benefit from tracking, recommend an Issue rather than creating one without
authorization.

## Load Guidance on Demand

- Issue operations: [references/issues.md](references/issues.md)
- Commit operations: [references/commits.md](references/commits.md)
- Delivery branch planning, environments, and releases:
  [references/delivery.md](references/delivery.md)
- Isolated or multi-Issue delivery only:
  [references/orchestration.md](references/orchestration.md)
- Immediately before spawning a worker:
  [references/worker-contract.md](references/worker-contract.md)

Read only references reached by the selected operation. Reuse guidance already
available in context; reread only if it is missing or the installed file changed.

## Requirement Lifecycle

Apply this lifecycle to single, isolated, and multi-Issue delivery:

1. **Capture:** Make the Issue the durable contract: observable goal, context,
   acceptance criteria, constraints, dependencies, and explicit exclusions.
2. **Plan:** Settle material product and technical decisions. Use one Issue per
   independently completable outcome; do not create Issues for mechanical
   steps. Resolve the branch name and starting point from live repository and
   provider state.
3. **Implement:** Change only the current Issue scope. Preserve unrelated
   worktree changes. For concurrent work, give every writer its own branch and
   worktree.
4. **Verify and integrate:** Prefer the cheapest real boundary that proves the
   acceptance criteria. Review the diff, publish through the project workflow,
   require remote CI or approvals when applicable, and integrate concurrent
   work one review request at a time.
5. **Close:** Confirm the completed commit is visible remotely and all required
   code, CI, environment, and product acceptance is complete. Reconcile every
   checklist item, post concise final evidence, close the Issue, and verify its
   remote state. Close a parent only after all children are complete.

## Boundaries

Repository instructions override defaults for language, Issue templates,
branches, commits, review, merge policy, CI/CD, versions, and tags. Report a
conflict between documentation and executable automation rather than silently
choosing one.

Keep credentials, customer data, and sensitive logs out of Issues and commit
messages. Preserve worktree changes outside the user's requested edits or
explicit discard scope. Record only durable decisions, blockers, and final
evidence; do not publish routine progress.

Recording or inspecting an Issue does not authorize implementation. Creating a
commit does not authorize a push. Development delivery does not authorize
production. Production changes, rollback, release tagging, destructive work,
force pushes, and bypassing protection or required review need authorization
for that exact action.

Report only checks and remote state actually verified. Leave incomplete Issues
open and state the exact remaining boundary.

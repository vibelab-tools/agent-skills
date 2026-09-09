---
name: project-work
description: Manage GitHub and GitLab Issue tracking, classification, boards, dependencies, delivery checks, milestones, and releases. Judge tracking needs even when the user does not mention Issues. Also use after coding on a non-trunk branch to commit, merge remote same-branch updates, and push, and for requested commit or isolated orchestration work. Skip routine Git operations, explanations, and exploratory diagnosis; small edits need no Issue but still follow the non-trunk publication rule.
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

## Decide Whether to Track the Work

For a concrete feature, defect, or refactoring task, judge whether an Issue
would preserve useful scope, acceptance criteria, decisions, or follow-up.
The user does not need to mention an Issue. Favor tracking when work needs
independent acceptance, spans sessions or contributors, has dependencies or
release implications, or addresses a recurring defect. Judge the outcome and
coordination needs, not the number of changed files or lines.

Small self-contained edits, explanations, exploratory diagnosis, and routine
Git operations normally need no Issue. Reassess if investigation establishes a
concrete defect or follow-up worth tracking. Honor explicit instructions such
as "do not create an Issue", "local changes only", or "inspect only".

When tracking is warranted and the target project is clear, use
[references/issues.md](references/issues.md) to find a matching Issue, reuse or
create it, and select a relevant milestone. Do not require the user to request
or reconfirm these routine tracking actions. Briefly state the tracking reason
and resulting Issue link. If the tracker is unavailable, continue authorized
local work and report the missing record.

Tracking alone does not authorize implementation or isolated orchestration.
For requested coding, apply the branch publication rule below. A record-only
or read-only task retains that boundary.

## Publish Completed Coding on Non-Trunk Branches

After completing requested coding and the necessary verification on a
non-trunk branch, commit the task's changes and push without asking again,
whether or not an Issue was needed. Honor explicit instructions such as
"local changes only", "do not commit", "do not push", or "commit only".

Identify trunk from repository instructions and the provider's default branch;
include any documented integration branches. Use `main` or `master` as common
fallbacks, not an exhaustive definition. If the branch role remains unclear,
resolve it before publication. Do not create or switch branches merely to
qualify for this rule. A task on trunk does not gain automatic push permission.

Before every push, follow [references/delivery.md](references/delivery.md):
commit only the intended changes, fetch the intended remote, merge its version
of the same branch when present, verify the combined result, and push that
branch explicitly. Preserve unrelated work and shared history. Production
effects and required review remain subject to the delivery boundaries.

## Choose the Workflow

| Request | Workflow | Boundary |
| --- | --- | --- |
| Record a requirement or defect | Record-only | Create and verify the Issue, then stop |
| Read or analyze an Issue | Inspect-only | No provider or repository mutation |
| Edit, comment on, reopen, or close an Issue | Maintenance | Perform only the requested Issue operation |
| Implement a task that merits Issue tracking | Tracked implementation | Create or reuse the record, then complete only the authorized implementation and verification |
| Finish coding on a non-trunk branch | Branch publication | Commit, fetch and merge the remote same branch, verify, and push; no Issue is required |
| Classify an Issue, maintain a board, or link dependencies | Planning maintenance | Apply project conventions and verify the provider's stored metadata and relationships |
| Check delivery or milestone readiness | Reconciliation | Report current evidence and gaps; a status question does not authorize mutation |
| Prepare release notes | Release preparation | Derive a draft from the actual release contents; publishing follows release authorization |
| Implement one Issue | Single delivery | Follow the lifecycle below in the current agent |
| `$project-work issue_id=<issue>` | Isolated delivery | One worker, branch, worktree, and review request |
| `$project-work parent_issue_id=<issue>` | Multi-Issue delivery | Decompose when needed and run only safe work in parallel |
| Draft, review, or create a commit | Commit-only | Do not create an Issue or infer a push |
| Plan delivery branches, push, deploy, promote, version, tag, release, or roll back an environment | Repository delivery | Reuse relevant tracking; create an Issue only for an outcome that warrants one |

Choose from the requested outcome and tracking judgment above, not just Issue
keywords. An incidental Issue URL alone does not establish work to perform or
authorize changes outside the target project.

## Load Guidance on Demand

- Issue operations: [references/issues.md](references/issues.md)
- Classification, boards, and native relationships:
  [references/planning.md](references/planning.md)
- Provider API calls and capability checks, when a selected operation needs
  them: [references/provider-api.md](references/provider-api.md)
- PR/MR, CI, acceptance, and milestone status checks:
  [references/reconciliation.md](references/reconciliation.md)
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

Apply this lifecycle to tracked implementation and Issue delivery, only through
the stages authorized by the request and applicable repository instructions.
When work stops at local verification, record that evidence and leave the
Issue open for the remaining delivery or acceptance work.

1. **Capture:** Make the Issue the durable contract: observable goal, context,
   acceptance criteria, constraints, dependencies, and explicit exclusions.
   Classify it and associate the applicable milestone, board, and relationships
   using `planning.md`; preserve established metadata.
2. **Plan:** Settle material product and technical decisions. Use one Issue per
   independently completable outcome; do not create Issues for mechanical
   steps. When branch creation is in scope, resolve its name and starting point
   from live repository and provider state.
3. **Implement:** Change only the current Issue scope. Preserve unrelated
   worktree changes. For concurrent work, give every writer its own branch and
   worktree. Update the mapped board status at meaningful transitions using
   `planning.md`.
4. **Verify and integrate:** Prefer the cheapest real boundary that proves the
   acceptance criteria and review the diff. When delivery is authorized,
   publish through the project workflow, require remote CI or approvals when
   applicable, and integrate concurrent work one review request at a time.
   Use `reconciliation.md` to reconcile the Issue with current delivery evidence.
5. **Close:** Confirm the completed commit is visible remotely and all required
   code, CI, environment, and product acceptance is complete. Reconcile every
   checklist item, post concise final evidence, close the Issue, and verify its
   remote state. Close a parent only after all children are complete.

## Select Verification

Verification does not require new test code. Prefer existing checks or a
focused real probe that proves the acceptance criteria. Add a test only for a
concrete regression those checks do not cover, not simply because an Issue
fixes a bug. Apply this choice to worker assignments and review as well.
An acceptance scenario does not need additional adapter tests for each of its
steps unless they protect an independent regression.

Mocking an external service is allowed only as needed to exercise real
application behavior; it is not sufficient reason to add a test. Do not add
thin adapter tests that inject a canned response or exception and only assert
copied fields, request arguments, or an error type/message. A retryable error
label does not prove retry or recovery. If a real check is unavailable, report
the unverified boundary instead of manufacturing mock coverage.

## Boundaries

Repository instructions override defaults for language, Issue templates,
branches, commits, review, merge policy, CI/CD, versions, and tags. Report a
conflict between documentation and executable automation rather than silently
choosing one.

Keep credentials, customer data, and sensitive logs out of Issues and commit
messages. Preserve worktree changes outside the user's requested edits or
explicit discard scope. Record only durable decisions, blockers, and final
evidence; do not publish routine progress.

Recording or inspecting an Issue does not authorize implementation. A
commit-only request does not authorize a push; completed coding on a non-trunk
branch follows the publication rule above. Development delivery does not
authorize production. Production changes, rollback, release tagging,
destructive work, force pushes, and bypassing protection or required review
need authorization for that exact action.

Report only checks and remote state actually verified. Leave incomplete Issues
open and state the exact remaining boundary.

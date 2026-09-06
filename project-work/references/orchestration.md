# Isolated and Multi-Issue Delivery

Use this reference only for an explicit invocation:

```text
$project-work issue_id=ISSUE_ID_OR_URL
$project-work parent_issue_id=ISSUE_ID_OR_URL
```

`issue_id` runs one Issue in one background worker. `parent_issue_id` uses the
parent as the durable plan, creates or reuses child Issues when needed, and runs
only independent children concurrently. Reject both inputs together or neither
input. A full URL must identify the checkout's repository; do not clone or
mutate another repository implicitly.

The invocation authorizes in-scope Issue updates, child creation in parent
mode, worktrees, branches, workers, commits, pushes, review requests, serialized
integration, and verified closure. It does not authorize production,
destructive data changes, force pushes, protection bypasses, missing approvals,
or work outside the selected Issue.

## Reconstruct Before Mutating

Read the repository instructions, remotes, default branch, worktree list,
provider authentication, selected Issue and comments, linked children,
branches, reviews, commits, and CI. Fetch the remote default branch. Resume
matching work instead of duplicating it; never base a worker on stale local
state or the user's uncommitted changes.

Maintain a concise durable ledger on the selected Issue: run ID, mode, base
branch and SHA, children and dependencies, branch and review URLs, blockers,
and final verification. Do not publish local worktree paths or routine worker
progress. Do not start a duplicate active run. After resume or compaction,
reconstruct transient state from provider and Git evidence.

## Plan and Classify Work

In parent mode, use one child per independently completable and verifiable
outcome. Reuse the parent itself when it already describes one complete
outcome. Create known children and dependencies before coding. In single-Issue
mode, never create children or silently expand scope.

New and reused children without a milestone inherit the parent milestone before
branch resolution. If a reused child already belongs to another milestone,
treat it as a planning conflict and do not overwrite it without an explicit
decision.

Build a dependency DAG. Parallel candidates must have merged dependencies,
non-overlapping touch sets, no shared exclusive resource or integration
hotspot, independently verifiable acceptance criteria, and no unresolved shared
decision. Treat uncertainty as serial.

Serialize tasks that touch the same migration chain or schema, lockfile,
generated artifact, global registry, central route, shared manifest, release
metadata, cross-Issue interface, or mutable environment. A migration may run
beside work that shares no files, interface, data source, or environment and
does not depend on it. Do not split a cohesive small change merely to use more
agents.

## Isolate Implementation

The controller creates one branch and worktree only for each ready Issue,
immediately before its worker starts. Apply the branch rules from
[delivery.md](delivery.md), verify every dependency is merged, fetch again, and
base the worktree on a remote SHA containing those dependencies. Validate that
paths and branches are unowned. Never share a writable checkout, copy the
user's uncommitted changes, or integrate in the user's original checkout.

A typical new worktree operation is:

```bash
git worktree add -b <branch> <worktree-path> <remote>/<default-branch>
```

Keep the main agent as controller and only integrator. In parent mode, use
available capacity for ready children while retaining capacity to coordinate;
otherwise run safe work serially. In single-Issue mode, start exactly one
worker, or stop when no worker capacity exists.

Before spawning, read [worker-contract.md](worker-contract.md) and provide every
field. Workers may implement, test, review their diff, commit with `Refs`, push
their assigned branch, then open or update exactly one review request. The
controller verifies and merges that review; it does not create an empty review
before the first pushed commit. Workers may not merge, close Issues, edit the
ledger, touch another worker's state, expand scope, perform destructive cleanup,
or modify the original checkout. On overlap or a new shared decision, stop that
worker and preserve its worktree.

## Review and Integrate

Require each review request to match its Issue, show evidence for every
criterion, pass applicable local checks and remote CI, satisfy approvals and
protection, and contain no unrelated or sensitive files. A worker summary is a
lead; the controller verifies the remote branch, diff, review, CI, and Issue.

Only the controller merges, one review at a time in dependency and risk order.
Before each merge, fetch the latest base, update the branch using project
policy, rerun invalidated checks, and reread CI and approvals. After a merge,
refresh the DAG and revalidate queued work affected by the new base.

Never resolve conflicts with blanket `ours` or `theirs`. Return mechanical,
Issue-local conflicts to the owner. The controller handles semantic or
cross-Issue conflicts, preserves every affected acceptance criterion, and asks
for a product decision when outcomes are incompatible. Push the resolution and
require remote CI again.

## Close and Clean Up

After merge, verify the commit on the remote default branch, post-merge CI, and
any separately authorized runtime checks. Reconcile and close each child using
[issues.md](issues.md); close the parent only after all children and integration
work are verified and closed.

Remove only worktrees created by the run, after their commits are merged,
remotely recoverable, and clean. Resolve each exact path through
`git worktree list`; never remove the user's checkout or unrelated worktrees.

Continue until verified completion or a real stop condition: ambiguous or
incompatible scope, unavailable access, required human approval, out-of-scope
CI failure, separately authorized production or destructive work, or an unsafe
conflict. Report mode, Issues, branches, worktrees, reviews, merged commits,
verification, final states, and exact blockers. For parent mode also report
parallel candidates, started, merged, and verified counts.

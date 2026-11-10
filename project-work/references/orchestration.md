# Isolated Single-Issue Delivery

Use this reference only for an explicit invocation:

```text
$project-work issue_id=ISSUE_ID_OR_URL
```

`issue_id` runs one Issue in one background worker. A full URL must identify
the checkout's repository; do not clone or mutate another repository implicitly.

The invocation authorizes in-scope Issue updates, one worktree, branch, and
worker, commits, pushes, one review request, integration, and verified closure.
It does not authorize production, destructive data changes, force pushes,
protection bypasses, missing approvals, or work outside the selected Issue.

## Reconstruct Before Mutating

Read the repository instructions, remotes, default branch, worktree list,
provider authentication, selected Issue and comments, dependencies, branches,
reviews, commits, and CI. Fetch the remote default branch. Resume
matching work instead of duplicating it; never base a worker on stale local
state or the user's uncommitted changes.

Maintain a concise durable ledger on the selected Issue: run ID, base branch
and SHA, dependencies, branch and review URLs, blockers, and final verification.
Do not publish local worktree paths or routine worker progress. Do not start
a duplicate active run. After resume or compaction, reconstruct transient state
from provider and Git evidence.

## Confirm the Scope

Implement only the selected Issue; do not create children or silently expand
scope. Apply its relevant planning metadata using [issues.md](issues.md).

## Isolate Implementation

The controller creates one branch and worktree for the selected Issue,
immediately before its worker starts. Apply the branch rules from
[delivery.md](delivery.md), verify every dependency is merged, fetch again, and
base the worktree on a remote SHA containing those dependencies. Validate that
paths and branches are unowned. Never share a writable checkout, copy the
user's uncommitted changes, or integrate in the user's original checkout.

A typical new worktree operation is:

```bash
git worktree add -b <branch> <worktree-path> <remote>/<default-branch>
```

Keep the main agent as controller and only integrator. Start exactly one
worker, or stop when no worker capacity exists.

Before spawning, read [worker-contract.md](worker-contract.md) and provide every
field. Workers may implement, test, review their diff, commit with `Refs`, push
their assigned branch, then open or update exactly one review request. The
controller verifies and merges that review; it does not create an empty review
before the first pushed commit. Workers fetch and merge the remote version of
their assigned branch before pushing, following [delivery.md](delivery.md).
They may not merge review requests, integrate into the target branch, close
Issues, edit the ledger, touch another worker's state, expand scope, perform
destructive cleanup, or modify the original checkout. On overlap or a new
shared decision, stop that worker and preserve its worktree.

## Review and Integrate

Require each review request to match its Issue, show evidence for every
criterion, pass applicable local checks and remote CI, satisfy approvals and
protection, and contain no unrelated or sensitive files. A worker summary is a
lead; the controller verifies the remote branch, diff, review, CI, and Issue.

Only the controller integrates the review request. Before merging, fetch the
latest base, update the branch using project policy, rerun invalidated checks,
and reread CI and approvals.

Never resolve conflicts with blanket `ours` or `theirs`. Return mechanical,
Issue-local conflicts to the owner. The controller handles semantic or
cross-Issue conflicts, preserves every affected acceptance criterion, and asks
for a product decision when outcomes are incompatible. Push the resolution and
require remote CI again.

## Close and Clean Up

After merge, verify the commit on the remote default branch, post-merge CI, and
any separately authorized runtime checks. Reconcile and close the selected
Issue using [issues.md](issues.md).

Remove only worktrees created by the run, after their commits are merged,
remotely recoverable, and clean. Resolve each exact path through
`git worktree list`; never remove the user's checkout or unrelated worktrees.

Continue until verified completion or a real stop condition: ambiguous or
incompatible scope, unavailable access, required human approval, out-of-scope
CI failure, separately authorized production or destructive work, or an unsafe
conflict. Report the Issue, branch, worktree, review, merged commit,
verification, final state, and exact blockers.

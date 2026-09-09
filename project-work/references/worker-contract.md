# Orchestration Worker Contract

Read this reference only before spawning an implementation worker. Give the
worker one complete prompt; do not rely on shared chat context.

## Required Prompt

```text
Role: implementation worker; the parent agent is the only integrator.

Mode: <parent_issue_id or issue_id>
Repository: <absolute repository root>
Authoritative remote: <remote name and URL>
Provider: <GitHub or GitLab and hostname>
Default branch: <name>
Base SHA: <remote default-branch SHA>

Selected issue: <URL and IID>
Parent issue: <URL or None in issue_id mode>
Implementation issue: <URL and IID>
Dependencies: <merged Issue or review URLs or None>
Acceptance criteria: <verbatim durable criteria>
Expected touch set: <paths, modules, or components owned by this worker>
Exclusive resources: <list or None>
Out of scope: <explicit boundary>

Branch convention: <documented rule and source or fallback>
Assigned branch: <branch>
Assigned worktree: <absolute path>
Required validation: <existing checks or real probes tied to acceptance criteria>
Commit convention: <observed convention>

Implement only this implementation Issue in the assigned worktree. Other
workers may be active; do not revert their work or edit outside your assigned
scope. Read project instructions before editing. Reproduce a defect when
practical, review the final diff, run required checks, commit with
`Refs #<iid>`, push the assigned branch, and open or update exactly one review
request linked to the implementation Issue. Before every push, fetch and merge
remote updates to that same assigned branch, review the combined changes, and
rerun affected checks as described in `references/delivery.md`.

Verification does not require new test code. Add a test only for a concrete
regression not covered by existing checks or another acceptance scenario.
Do not add thin adapter tests that inject canned responses or exceptions and
only assert copied fields, request
arguments, or error types/messages. Mock only external effects needed to
exercise real application behavior; an error label alone does not prove retry
or recovery. Report unavailable real checks as unverified.

Do not merge review requests or integrate into their target branch; merging
the remote version of your own assigned branch before push is required when
it exists. Do not close Issues, edit the orchestration ledger, modify another
worker's state, change the original checkout, or expand scope. Stop writing and
report evidence if you discover overlap, a new dependency, a cross-Issue
decision, incompatible acceptance criteria, unavailable validation, or an
action outside the authorization boundary.

Return: changed files, commit SHA, pushed branch, review URL, checks and exact
results, acceptance-criteria evidence, unresolved findings, and retained
worktree path.
```

## Controller Review

Treat the worker's summary as a lead, not proof. The controller independently
inspects the remote branch, review request, diff, CI, and Issue state before
placing it in the merge-ready queue.

Review added tests against the concrete regression and acceptance criteria.
Reject mock-only adapter assertions as acceptance evidence; passing test counts
do not establish that the requested behavior works.

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
Required validation: <commands and observable checks>
Commit convention: <observed convention>

Implement only this implementation Issue in the assigned worktree. Other
workers may be active; do not revert their work or edit outside your assigned
scope. Read project instructions before editing. Reproduce a defect when
practical, review the final diff, run required checks, commit with
`Refs #<iid>`, push the assigned branch, and open or update exactly one review
request linked to the implementation Issue.

Do not merge, close Issues, edit the orchestration ledger, modify another
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

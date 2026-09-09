# Reconcile Planning with Delivery

Use this for a requested delivery/milestone status check, after publishing
tracked work, and before closing an Issue or preparing a release. Limit the
scope to the selected Issue, parent plan, milestone, or release. This is an
on-demand check; it does not install a scheduler or start a backlog sweep.

## Gather the Evidence

Use [provider-api.md](provider-api.md) to read the required resources:

1. Issue scope, acceptance criteria, current state, milestone, board state, and
   dependency conditions. Include relevant closed Issues so premature closure
   is visible.
2. Explicitly linked PRs/MRs or commits. Search by Issue references or branch
   names when links are missing, then inspect the diff and scope before
   accepting a match. A closing keyword or cross-reference is not proof of
   implementation. Direct-commit workflows do not require a fabricated PR/MR.
3. Current review state: draft/readiness, head SHA, target branch, approvals,
   unresolved required discussions, conflicts, and merge state. Recheck when
   the provider is still calculating mergeability. Do not count dismissed or
   obsolete reviews as current approval or assume an inaccessible approval
   rule is satisfied.
4. Required CI for that head or integrated commit, and any separately required
   post-merge jobs. Compare expected checks from repository rules and CI with
   reported results. Missing, pending, cancelled, skipped, and successful are
   distinct; a skipped job does not prove its verification ran. If CI is not
   required, record that basis instead of inventing a missing requirement.
5. For required environment or product acceptance, verify the deployed
   commit/artifact identity and the actual acceptance evidence. A successful
   deployment record does not by itself prove the user-visible behavior.

Paginate collections and state incomplete visibility. Record the observed SHA,
environment, evidence links, and observation time. If the head, base, or deployed
artifact changes, refresh affected evidence before changing task state.

## Classify and Act

Use [planning.md](planning.md) to map verified progress to the existing board.
Report discrepancies with the exact next step:

| Observed discrepancy | Result and next step |
| --- | --- |
| Tracked implementation has no verified Issue/review/commit link | Inspect the candidate and add the correct reference when maintenance is authorized. |
| Work is marked In progress but its ready review is waiting | Show review status and the missing reviewer or decision. |
| A PR/MR is merged while required CI, deployment, or acceptance remains | Keep the task open and identify the remaining check. |
| An Issue is closed but required acceptance demonstrably failed or remains incomplete | Report the evidence; reopen and correct the board when tracking maintenance is authorized. |
| An Issue is open and all closure criteria are verified | Apply [issues.md](issues.md) if closure is authorized; otherwise report it as ready to close. |
| The blocking Issue is closed but the required change is not integrated | Keep the dependency blocked until its actual condition is met. |
| Required evidence cannot be retrieved | Mark that part unknown and name the unavailable query/check; do not infer success or failure. |

A status question produces a report, not automatic edits. During authorized
tracking maintenance, correct evidenced discrepancies and read the resources
back. Unavailable evidence alone does not justify reopening a closed Issue.
Do not satisfy review requirements by approving on someone's behalf, resolving
unaddressed discussions, or modifying repository protections.

Use a concise table when there are several results:

`Issue | Verified stage | Discrepancy/blocker | Evidence URL and SHA | Next step`

Comment only when there is a durable decision, blocker, or new final evidence;
avoid repeating unchanged reports. Return both completed writes and remaining
unknowns. Never describe a partial query as a clean project-wide result.

## Milestone and Release Readiness

Read all Issues in the selected milestone and relevant linked reviews. Count
unique implementation outcomes; present parent rollups separately so parents
and children are not counted twice. Report verified complete, in progress,
blocked, awaiting acceptance, and unknown work, with concrete remaining Items.
An Issue's closed state is not the verified-complete count.

Compare the actual candidate commit/tag with the milestone plan. Surface both
planned changes absent from the candidate and candidate changes absent from
the plan. State cancelled, superseded, or deferred scope separately; do not
silently move Issues to make the milestone appear complete. If no candidate is
selected, report milestone progress and leave release inclusion unverified.

Prepare release notes through [delivery.md](delivery.md) after the actual
release range is established. Close a milestone only when requested or part
of authorized release completion, its remaining scope is reconciled, and all
required acceptance is verified. A milestone due date or empty open-Issue
query alone does not establish readiness.

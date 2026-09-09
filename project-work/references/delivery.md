# Branches, Delivery, and Release

Use this reference only for delivery branch planning, a push that may trigger
CI/CD, deployment, promotion, versioning, tagging, release, environment rollback,
or environment acceptance. Routine Git inspection, synchronization, checkout,
and discarding local changes do not need this reference.

## Read the Delivery Contract

Inspect only the parts of repository instructions, executable CI/CD
configuration, branch and merge policy, release documentation, selected Issue
and milestone, remote refs, and environment state that determine the requested
operation or its effects. Executable automation describes
actual effects; report documentation drift instead of silently changing scope.
Determine whether a push deploys a shared environment or production before
performing it.

## Commit, Merge the Remote Branch, and Push

Use this sequence for branch publication, including worker pushes. Resolve the
current branch and intended push remote from repository rules and branch
configuration. The synchronization target is the remote branch with the same
name. A differently named upstream, such as `origin/main` for a feature branch,
is not the synchronization or push target.

1. Review, verify, and commit only the completed task's changes using
   [commits.md](commits.md). Preserve unrelated staged and unstaged work; do not
   absorb it into either the task commit or a merge commit.
2. Fetch the intended remote and check whether the same-named branch currently
   exists there. If it does, fetch that exact branch with
   `git fetch <remote> refs/heads/<branch>` and immediately run
   `git merge --no-edit FETCH_HEAD`. This also works when the configured fetch
   refspec omits that branch. If a successful remote lookup shows no such
   branch, proceed with the first push. A failed lookup is not absence.
3. Resolve straightforward conflicts while preserving both sides' intended
   behavior. Review the merge result and rerun checks affected by incoming
   changes or resolutions. Ask only when the conflict requires a material
   decision that cannot be inferred. Do not replace this merge with rebase,
   reset, force-push, or blanket `ours`/`theirs` resolution.
4. Push the current branch explicitly with
   `git push <remote> HEAD:refs/heads/<branch>`; add `--set-upstream` on its
   first push. If rejected because the remote advanced, fetch, merge, and
   revalidate before retrying once. Report repeated races or an unresolved
   conflict without forcing the push.
5. Verify the published commit is present on the remote branch, then complete
   required CI and environment checks. If the remote advanced again after the
   push, check containment instead of treating a different tip as lost work.

## Name and Start Issue Branches

Explicit repository rules take precedence. Otherwise, read the Issue's current
milestone from the provider and use this default:

| Condition | Branch | Starting point |
| --- | --- | --- |
| Issue has a milestone | `feature/<milestone-version-or-id>-<slug>` | Starting point required by the underlying work type |
| New behavior or unclassified Issue | `feature/<issue>-<slug>` | Current remote default branch |
| Normal defect | `bugfix/<issue>-<slug>` | Current remote default branch |
| Urgent production defect | `hotfix/<issue>-<slug>` | Current production tag or deployed commit |

For a milestone key, prefer an explicit version or a title that is exactly a
version such as `v0.3.0`; otherwise use the GitHub milestone number or GitLab
milestone IID, then a provider ID only when no project-scoped number exists.
Do not derive the key from an arbitrary title. The milestone changes the branch
name, not hotfix starting-point or verification rules.

Use project-scoped Issue numbers, lowercase hyphen-separated slugs, and unique
branch names. Unclassified work falls back to `feature/<issue>-<slug>`. Branches
must include a slug describing the work; even a milestone branch cannot consist
of only a version number. Every Issue branch belongs to one Issue and its
commits reference that Issue.

Fetch the remote and start from its current required base. For `origin/main`:

```bash
git fetch origin
git switch main
git merge --ff-only origin/main
git switch -c <resolved-issue-branch>
```

Use this `git switch` sequence only for single delivery in a clean checkout.
Isolated and multi-Issue delivery create dedicated worktrees as described in
`orchestration.md` and never switch the user's original checkout.

## Verify Development Delivery

After publication, verify the jobs expected for that ref, required CI, artifact
or commit identity, workload health, dependencies, and the Issue's observable
behavior. A shared environment may have been replaced by another branch, so
verify current state rather than relying on an earlier run.

Amend, rebase, merge, or any other SHA change invalidates evidence tied to the
previous commit. Repeat affected CI and environment acceptance.

## Promote and Release

For a milestone readiness question or release-note draft, first use
[reconciliation.md](reconciliation.md) to compare planned work with verified
delivery. The [provider API recipes](provider-api.md) cover milestone queries,
release-note generation, and release writes.

Resolve the previous release tag and exact candidate commit or tag from the
agreed release plan and live refs. Build notes from changes actually contained
between those points, including merged PRs/MRs and any direct commits. Include
features, fixes, breaking changes, migrations, and known limitations when
supported by the diff and Issues. Exclude unmerged or unreleased work even if
its Issue is closed or belongs to the milestone. Deduplicate backports and
multiple references to the same outcome.

GitHub's generated notes are a starting draft; compare them with the actual
range. For GitLab, compose Markdown from the verified range and associate the
applicable project milestones when release publication is authorized. A title
or version match alone does not prove inclusion. Keep the generated draft in a
local artifact or the response until publication is requested. Creating a
remote draft release is also a provider write, not a local preview.

After an authorized release write, read back the tag, target commit, Markdown,
milestone associations where supported, and asset links. Check tag identity
and required acceptance before closing the milestone. If that provider/version
lacks a release capability, deliver the Markdown with the exact missing step.

Production promotion or rollback requires current explicit authorization.
Immediately before promotion, fetch the authoritative remote, confirm the
accepted commit contains the required current base, and repeat validation if
the base or commit changed. Promote the exact accepted artifact; when same-SHA
promotion is required, do not squash or create a new merge commit. Never resolve
a promotion conflict automatically.

After promotion, verify the expected production route, artifact identity,
rollout, health, and required product behavior. Pipeline success does not prove
separate data changes, migrations, or product acceptance.

Choose a version only after release contents are known. Follow repository
version and tag rules; otherwise use Semantic Versioning and an annotated
`release/v<major>.<minor>.<patch>` tag. Create a tag only after acceptance,
verify its remote SHA, and never move or reuse it.

On failure, keep unverified work out of production, preserve evidence, and fix
through the documented branch path. Missing or mismatched immutable artifacts
must be rebuilt or republished through that path, never replaced under the same
identity. Retry or roll back production only with current authorization.

Delete an Issue branch only after required acceptance, Issue closure, and
remote recoverability.

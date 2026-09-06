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

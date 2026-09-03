# Branch, Delivery, and Release Workflow

Use this reference before branch creation or mutation, a push that may trigger
CI/CD, environment deployment or promotion, version selection, release tags,
rollback, or any claim that a release is accepted.

This guidance captures reusable invariants from an exact-commit delivery
workflow. It does not make one application's branch names, CI jobs, storage
systems, namespaces, health endpoints, or tag format universal.

## Discover the Project Contract

Inspect, in order:

1. Applicable `AGENTS.md` and current user authorization.
2. CI/CD configuration and scripts that perform the actual automation.
3. Branch protection, merge strategy, deployment, and release documentation.
4. The task issue, related issues, and existing release evidence.
5. Current remote refs, pipeline state, artifact identity, and environment
   state when they are in scope and accessible.

Record a conflict between executable automation and documentation. Do not
change delivery behavior merely to make the two agree unless that change is in
the task scope.

Determine before writing whether a branch push deploys a shared environment,
publishes artifacts, or starts production. A push with those effects is not
merely a backup.

## Branches and Development Delivery

Follow the repository's branch naming, starting-point, review, merge, and
deletion rules. When the project requires issue-backed branches, include the
issue identifier and describe the work rather than guessing a future version.
Start from the current remote base required by the project, not a stale local
branch.

After implementation and local checks, push the work branch when normal task
publication is authorized. For development acceptance, verify the observable
contract that applies to the project:

- the expected jobs ran for that ref and all required jobs succeeded;
- the environment uses the intended commit or immutable artifact;
- health checks and dependencies report the expected release identity;
- the issue's actual product behavior is accepted;
- a shared environment has not been replaced by another branch before the
  evidence was recorded.

A commit amend, rebase, merge, or other rewrite changes the SHA. Re-run any
pipeline and environment acceptance tied to the previous SHA. Do not carry
evidence across identities merely because the source diff appears equivalent.

## Production Promotion

Production promotion or deployment requires explicit authorization in the
current task. An earlier approval is not continuing permission.

Immediately before promotion:

1. Fetch the authoritative remote state.
2. Confirm the development-accepted commit contains the required current base
   according to project policy.
3. If the base advanced, integrate it using the allowed strategy and repeat
   all pipeline and acceptance checks for the new commit.
4. Promote the exact accepted commit or immutable artifact. If the project
   requires same-SHA promotion, do not squash or create a new merge commit.
5. Never resolve a promotion conflict automatically.

After promotion, verify that the production pipeline ran the expected route,
used the accepted identity, completed within its defined timeout, left the
workload healthy, and exposed the expected release identity. Pipeline success
proves only the automated rollout contract; product acceptance, data changes,
migrations outside normal startup, and other release-specific operations need
their own evidence.

## Versions and Tags

Follow the repository's version source and tag format. Choose a version only
when the shipped content is known. When the project uses Semantic Versioning,
a backward-compatible fix normally increments patch, backward-compatible
functionality increments minor, and a stable breaking change increments major;
pre-1.0 compatibility rules remain project-specific.

Create a release tag only after the target environment and product behavior
are accepted, and only when tagging is part of the authorized release. Verify
the remote tag resolves to the accepted commit. Treat release tags as
immutable: do not move or reuse a tag for a failed, replaced, or corrected
release. Use a new corrective commit and version.

Do not assume package metadata, a branch name, or a tag triggers deployment;
inspect the executable CI/CD routing.

## Failure and Recovery

- If development publication or deployment fails, keep the work out of
  production and fix it on a permitted work branch.
- If the expected immutable artifact is missing or mismatched, stop promotion
  and rebuild or republish through the documented development route. Never
  place different bytes under an existing immutable identity.
- If the base branch advances, update the work branch and repeat acceptance.
- If production deployment fails, preserve pipeline and rollout evidence.
  Retry or roll back only with current production authorization.
- Do not tag a failed release or move an earlier tag after rollback. Use the
  project's corrective-release process.

After final environment and product acceptance, reconcile and close the issue
according to the issue reference. Remove the work branch only when repository
policy and the issue lifecycle allow it.

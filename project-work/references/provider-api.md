# Provider Operations

Use this reference for the API operations selected by the workflow. Reuse
authenticated `gh` for GitHub and `glab` for GitLab. Apply the user's
command-scoped network routing to every invocation, including GitLab direct
connections. Never print authentication headers or tokens.

## Discover Only the Capabilities Needed

Resolve the exact hostname and repository first. GitHub REST paths below use
`R = OWNER/REPO`; GitLab paths use numeric project ID `P`, resolved once from
`GET projects/<URL-encoded GROUP/PROJECT>`. Issue numbers and GitLab IIDs are
project-scoped; they are not database IDs or GraphQL node IDs.

Read repository metadata and permissions. For self-managed GitLab, `GET
version` can identify the version when accessible. Inspect only the selected
board, fields, relationships, or review capability. Cache the findings in the
current task context; do not turn discovery into an instance-wide audit.

Check `gh api --help` or `glab api --help` if CLI options differ. New CLI versions
do not add capabilities to old servers. Do not require a server upgrade.
A successful GET proves read access, not write access. A `401`/`403`, an
ambiguous `404`, a GraphQL authorization error, or a network failure is not
proof that a feature is absent. Resolve target/authentication errors and
distinguish unavailable, forbidden, and unknown. Use documented version/tier
limits or a confirmed schema/endpoint absence to select a fallback.

Use ordinary labels and Markdown relationships when native planning features
are unavailable. Missing review or deployment evidence remains unknown; it
cannot be replaced with a success label. Report any unperformed write.

## Call and Verify

For reads with filters, specify `--method GET`; adding fields otherwise changes
the CLI's default method. Paginate every required REST collection using
`--paginate` when available, or the server's next-page information. For
GraphQL, follow `pageInfo`/`endCursor` for each connection, including nested
connections. Do not report a complete count from one page or treat redacted
items as nonexistent.

For writes, build a JSON file with a serializer and pass it as input:

```bash
gh api --hostname "$pw_host" --method "$pw_method" "$pw_endpoint" \
  --input "$pw_payload_file"
glab api --hostname "$pw_host" --method "$pw_method" "$pw_endpoint" \
  --input "$pw_payload_file"
```

Run only the matching provider command. Resolve the `pw_` variables from the
selected operation, not from untrusted Issue text. GraphQL uses endpoint
`graphql`, method `POST`, and a JSON body with `query` and `variables`.
Check GraphQL top-level `errors` and mutation-specific errors, even on HTTP
200. Issue bodies/comments still use `scripts/issue_markdown.py`.

Read current state before writing, calculate the smallest change, and skip
no-ops. Prefer additive operations to replacing shared collections. On an
ambiguous write timeout, read back before retrying so Issues, links, and board
items are not duplicated. A confirmed no-op or verified write ends the
operation; persistent failures are reported, not retried indefinitely.

After each write, GET the resource and verify intended values and preservation
of unrelated values. A successful HTTP response alone is not acceptance.

## GitHub: Metadata and Relationships

| Operation | REST request and JSON fields | Readback |
| --- | --- | --- |
| Discover labels, eligible assignees, milestones | `GET repos/R/labels`, `GET repos/R/assignees`, `GET repos/R/milestones?state=all` | Paginate; read meanings and milestone scope. |
| Add labels or assignees | `POST repos/R/issues/N/labels` with `labels: [name]`; `POST repos/R/issues/N/assignees` with `assignees: [login]` | `GET repos/R/issues/N` |
| Set milestone | `PATCH repos/R/issues/N` with `milestone: number` | Compare the returned milestone number. |
| Create a label when setup requires it | `POST repos/R/labels` with `name`, six-digit `color`, and `description` | Read the label before applying it. |
| Add a child | `POST repos/R/issues/PARENT/sub_issues` with `sub_issue_id: child_database_id` | `GET repos/R/issues/PARENT/sub_issues` and `GET repos/R/issues/CHILD/parent` |
| Make N depend on B | `POST repos/R/issues/N/dependencies/blocked_by` with `issue_id: blocker_database_id` | `GET repos/R/issues/N/dependencies/blocked_by` and B's `dependencies/blocking` |

Resolve each Issue's numeric `id` from its GET response before relationship
writes. Do not pass its number or `node_id` as `sub_issue_id`/`issue_id`. Read
the current parent first; do not set `replace_parent` to move it implicitly.
For context-only relationships, use reciprocal Markdown links. Remove obsolete
labels individually; do not replace the entire label list to change one state.

Sources: [Issues](https://docs.github.com/en/rest/issues/issues),
[labels](https://docs.github.com/en/rest/issues/labels),
[assignees](https://docs.github.com/en/rest/issues/assignees),
[sub-Issues](https://docs.github.com/en/rest/issues/sub-issues),
[dependencies](https://docs.github.com/en/rest/issues/issue-dependencies).

## GitHub: Projects

Resolve the selected ProjectV2 node ID from the appropriate
`organization(login: ...).projectV2(number: ...)` or
`user(login: ...).projectV2(number: ...)` query. Discover its fields and options:

```graphql
query($project: ID!, $cursor: String) {
  node(id: $project) {
    ... on ProjectV2 {
      fields(first: 100, after: $cursor) {
        nodes {
          ... on ProjectV2FieldCommon { id name }
          ... on ProjectV2SingleSelectField { options { id name } }
        }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
```

Use the Issue's `node_id` for `content`, then retain the returned item ID:

```graphql
mutation($project: ID!, $content: ID!) {
  addProjectV2ItemById(input: {projectId: $project, contentId: $content}) {
    item { id }
  }
}
```

Set the discovered status or priority option in a separate call:

```graphql
mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
  updateProjectV2ItemFieldValue(input: {
    projectId: $project, itemId: $item, fieldId: $field,
    value: {singleSelectOptionId: $option}
  }) { projectV2Item { id } }
}
```

Read the item's `fieldValues` and project membership after updating. Issue
labels, assignees, and milestone are Issue properties, so update those through
the Issue API rather than `updateProjectV2ItemFieldValue`. Reuse existing field
IDs and options; do not hard-code names from a different project.

Source: [Projects API workflow](https://docs.github.com/en/issues/planning-and-tracking-with-projects/automating-your-project/using-the-api-to-manage-projects).

## GitLab: Metadata, Boards, and Relationships

| Operation | REST request and JSON fields | Readback |
| --- | --- | --- |
| Discover metadata | `GET projects/P/labels`, `GET projects/P/members/all`, `GET projects/P/milestones?state=all` | Paginate and resolve actual label, user, and milestone IDs. |
| Add labels / change status labels | `PUT projects/P/issues/N` with `add_labels` and, when needed, `remove_labels` as comma-separated existing names | `GET projects/P/issues/N`; preserve all unrelated labels. |
| Set assignees or milestone | `PUT projects/P/issues/N` with `assignee_ids: [user_id]` or `milestone_id: id` | Assignee lists replace the field: preserve existing users unless reassignment is intended. Use milestone `id`, not `iid`. |
| Discover a board | `GET projects/P/boards`, then `GET projects/P/boards/B/lists` | Inspect board scope and each list's label or supported field. |
| Create a board/list during requested setup | `POST projects/P/boards` with `name`; `POST projects/P/boards/B/lists` with `label_id` | GET the board and lists before adding anything else. |
| Link N to another Issue | `POST projects/P/issues/N/links` with `target_project_id`, `target_issue_iid`, and `link_type` | `GET projects/P/issues/N/links` and the other Issue's links |

A label-backed board has no separate card-status write: update the Issue's
existing list labels. Confirm it also matches the board's milestone/filter
scope. Closed Issues do not appear in ordinary open label lists; do not reopen
them solely to move a card. For newer status-backed lists, use the server's
supported work-item status operation instead of assuming labels control them.

GitLab can create nonexistent labels through `add_labels`; validate names
first. If an older version lacks additive label fields, read the latest list,
merge the intended change, write `labels`, and verify no unrelated labels were
lost. Use legacy assignment fields only when the server documents them.

Relationship direction is relative to the source: `is_blocked_by` means N
depends on the target; `blocks` means the target depends on N. `relates_to`
does not encode a dependency. Native blocking needs a supporting version/tier.
GitLab parent/child types differ from GitHub sub-Issues. For supported work
item hierarchies, consult the installed GraphQL schema's `workItemUpdate` and
hierarchy input and verify the resulting parent/children. Otherwise use the
Markdown fallback in [planning.md](planning.md); never invent an Issue parent
REST field or convert work item types implicitly.

Sources: [Issues](https://docs.gitlab.com/api/issues/),
[boards](https://docs.gitlab.com/api/boards/),
[Issue links](https://docs.gitlab.com/api/issue_links/),
[linked Issue limits](https://docs.gitlab.com/user/project/issues/related_issues/),
[work item hierarchy](https://docs.gitlab.com/user/work_items/child_items/).

## Delivery and Releases

Use [reconciliation.md](reconciliation.md) to interpret the evidence; the API
resources below do not decide acceptance by themselves.

| Purpose | GitHub | GitLab |
| --- | --- | --- |
| Milestone scope | `GET repos/R/issues?milestone=M&state=all`; exclude entries with `pull_request` when counting Issues. | `GET projects/P/milestones/M/issues`; M is the milestone ID. |
| Find linked reviews | Issue `timeline` cross-reference events, explicit links, and PR `closingIssuesReferences`; search is discovery, not proof. | `GET projects/P/issues/N/related_merge_requests` and `/closed_by` |
| Read a review | `GET repos/R/pulls/N`; GraphQL `reviewDecision`, `reviewThreads`, `closingIssuesReferences`, and `headRefOid` | `GET projects/P/merge_requests/N`; `/approval_state` and `/discussions` where supported |
| Check the current commit | `GET repos/R/commits/SHA/check-runs` and `/status`; Actions runs for that SHA | MR `head_pipeline`, or `GET projects/P/pipelines?sha=SHA`, followed by that pipeline's jobs |
| Environment delivery | Repository deployments and each deployment's statuses, checked against the candidate SHA | Project deployments/environments and their deployable commit/pipeline identity |
| Generate notes without publishing a release | `POST repos/R/releases/generate-notes` with `tag_name`, exact `target_commitish`, and `previous_tag_name` | Compose Markdown from the verified commit range and linked MRs/Issues. |
| Publish an authorized release | `POST repos/R/releases` with existing `tag_name`, `name`, `body`, and intended `draft`/`prerelease` flags | `POST projects/P/releases` with existing `tag_name`, `name`, `description`, and applicable `milestones` titles |
| Verify a release | `GET repos/R/releases/tags/TAG` and the tag's peeled commit | `GET projects/P/releases/TAG` and the tag's commit |

URL-encode tag names in paths. For existing releases, use the provider's edit
endpoint instead of creating a duplicate. Serialize multiline release bodies
as JSON and read them back exactly. Note generation does not authorize release
publication or tag creation. A generated title or changelog does not prove
that the candidate passed delivery acceptance.

Sources: [GitHub review fields](https://docs.github.com/en/graphql/reference/objects#pullrequest),
[GitHub checks](https://docs.github.com/en/pull-requests/reference/status-checks),
[GitLab MR API](https://docs.gitlab.com/api/merge_requests/),
[GitLab approvals](https://docs.gitlab.com/api/merge_request_approvals/),
[GitHub releases](https://docs.github.com/en/rest/releases/releases),
[GitLab releases](https://docs.gitlab.com/api/releases/).

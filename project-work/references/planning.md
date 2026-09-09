# Classification, Boards, and Relationships

Use this reference after selecting Issue tracking, when maintaining planning
metadata, or at an implementation transition. Use only the relevant section.
[provider-api.md](provider-api.md) supplies the native operations and readback
steps. A read-only request may inspect these fields but does not change them.

## Classify the Issue

Read the Issue's existing metadata, the project's label descriptions and
templates, and any established ownership or priority rules before editing.

| Field | Decision |
| --- | --- |
| Type | Choose the existing defect, feature, maintenance, or equivalent category from the actual requested outcome. |
| Area | Use the affected product/module labels; avoid tagging every file touched. |
| Priority | Apply the project's scale using stated impact, urgency, and dependencies. Leave it unset when the evidence does not distinguish a priority. |
| Assignee | Preserve the responsible person already assigned. Assign from an explicit request or an established ownership rule. A code owner can suggest a reviewer without being the task's assignee. |
| Milestone | Apply the selection and conflict rules in [issues.md](issues.md). |

Reuse existing names and meanings. Do not invent a parallel taxonomy or infer
deadlines from the current date. If project setup is requested and no scheme
exists, establish only the categories the work needs. Create any necessary
labels once, with a clear description, before applying them.

Add missing values; change existing decisions only when the request or agreed
plan supports the change. Preserve unrelated labels and assignees. Treat a
single-select priority field or mutually exclusive label family as one value;
do not leave two conflicting priorities. Read back the resulting metadata.

## Maintain the Existing Board

Identify the board/project from repository instructions, the Issue's existing
membership, or a clearly applicable repository-linked board. Resolve ambiguity
only if it affects the update; unrelated implementation may continue. Add an
Issue to the relevant existing board when tracking requires it. If no board
exists, create one only as part of requested project setup; otherwise use the
available Issue metadata and report that no board was selected.

Read the configured fields, lists, options, filters, and automation before
mapping statuses. Reuse the existing workflow. These stages describe evidence,
not mandatory new column names:

| Observed progress | Board meaning |
| --- | --- |
| Captured, with no implementation started | Planned / Todo |
| Implementation is actually underway, including a draft PR/MR | In progress |
| A ready PR/MR has been submitted for the required review | In review |
| Implementation is integrated, but required deployment or product acceptance remains | Awaiting acceptance |
| All required delivery and acceptance criteria are verified | Done |

A branch's existence does not prove work started. A merge or successful build
does not by itself prove acceptance. For direct-commit workflows, skip review
stages the project does not require. When the board has fewer states, retain
the closest truthful open state and record the exact remaining work in the
Issue; do not map pending acceptance to Done.

Record a blocker with its cause and resume condition. Use a configured blocked
field/label, or a blocked column if that is the project's convention. Clear it
only after checking the blocking condition is resolved.

Update at meaningful transitions, not after every tool call. When built-in
automation already makes the correct transition, verify it rather than write
the same value again. Surface automation that marks work Done too early; do
not repeatedly fight it or silently reconfigure a shared board. Preserve
unrelated fields, memberships, and list order. Read back the changed state.

## Maintain Native Relationships

Read existing relationships and the linked Issues' acceptance criteria before
adding a relationship. Keep these meanings separate:

- **Parent/child:** the child is an independently completable part of the
  parent's outcome. Show parent progress as a rollup.
- **Blocked by:** this task cannot reach a required stage until the dependency
  satisfies a stated condition. A closed dependency still needs the required
  integration or acceptance evidence.
- **Related:** useful shared context with no implied ordering or containment.

Prefer the platform's native relationship when supported. Preserve a concise
Markdown explanation of the reason and any dependency condition that the
native link cannot express. Reuse existing links, reject self-links and cycles,
and do not replace an existing parent silently. Do not treat common keywords
as proof of a dependency. Cross-project links must stay within the task's
authorized project scope.

GitHub supports sub-Issues and directional dependencies. For GitLab, use
native linked Issues; blocking relationships depend on the available tier and
version. Native hierarchy also depends on work item types: an Issue-to-Issue
plan must not be silently converted to an Epic/Task hierarchy just to obtain a
native parent link. When a valid native hierarchy is unavailable, keep the
parent/child checklist and reciprocal URLs in Markdown, and use `relates_to`
only as supplementary context. Explicitly report that the parent or dependency
is recorded in Markdown rather than claiming a native relation exists.

Before selecting the next task, check its blockers and the parent plan. Before
closing a child, apply [reconciliation.md](reconciliation.md); then refresh
the parent's progress without closing it while required children remain open.
Re-read relationships on both sides after changes.

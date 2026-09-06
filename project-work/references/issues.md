# Issue Workflow

Use this reference only for the Issue operation selected in `SKILL.md`.
Record-only, inspect-only, and maintenance must not expand into implementation
or delivery.

## Resolve the Repository and Provider

Use the supplied Issue URL or repository/provider identity already established
in context. Inspect Git remotes only when that identity is missing; a provider-only
Issue operation does not require a local worktree, diff, branch, or CI inspection.

When resolving from Git, use the current branch's tracked remote, then the documented authoritative
remote, then `origin`. Stop when GitHub versus GitLab ownership is ambiguous.
Use `gh` only for GitHub and `glab` only for GitLab, pass the repository and
hostname explicitly. Reuse working authentication; investigate it when a request
fails for an authentication reason. If neither an explicit provider target nor
a supported remote is available, do not invent an Issue tracker.

## Capture a Requirement

Search for the same outcome before creating an Issue. Reuse a matching open
Issue; use closed work as evidence and create a linked follow-up only when new
work is required.

Choose the target project's language from repository instructions and
templates, then recent Issues, then project documentation. A new Issue keeps
only durable context:

```markdown
## Goal

<observable outcome>

## Context

<requirement, defect, or reproduction evidence>

## Acceptance criteria

- [ ] <verifiable result>
- [ ] <required validation>

## Constraints

<scope, exclusions, and decisions that affect implementation>

## Plan relationships

- Parent or source: <Issue URL or None>
- Depends on: <Issue URLs or None>
```

Clarify material ambiguity before delivery. A small outcome uses one Issue.
Split only outcomes that can be implemented and verified independently; never
create separate Issues for reading files, writing one test, committing, or
opening a review request.

For multi-Issue work, record each child's expected touch set, exclusive
resources, exclusions, and dependencies. Create known children before coding
and link their ordered list from the parent. New and reused children without a
milestone inherit the parent's milestone before branch resolution. If an
existing child belongs to another milestone, record a planning conflict and do
not overwrite it silently.

If delivery reveals another independent outcome, stop that new work, update the
plan, and create or link its Issue before implementation. In isolated
single-Issue mode, report the additional scope instead of creating a child.

## Write and Verify Provider Content

For every multiline create, edit, or comment, use the bundled helper. It reads
Markdown from standard input, rejects literal `\n`, performs the mutation, and
requires the stored provider content to match:

```bash
python3 <skill-directory>/scripts/issue_markdown.py \
  --provider github --action create \
  --repo <owner/repo> --title <title> <<'MARKDOWN'
<body>
MARKDOWN
```

For GitLab add `--hostname <host>` and use `<group/project>`. Supported actions
are `create`, `edit`, and `comment`. Use `--allow-literal-newlines` only when
the characters `\n` are intentional content.

Prefer provider-returned web URLs. For self-managed GitLab URL compatibility,
use:

```bash
python3 <skill-directory>/scripts/gitlab_web_url.py \
  --project-url "$(git remote get-url origin)" \
  --kind issue --id <iid>
```

Attach a supplied screenshot only when it materially proves a defect, visual
requirement, or environment result. Require an accessible file or approved URL,
inspect it for unrelated sensitive content, preserve legibility, add useful alt
text, and verify the stored Issue reference. GitLab may use its project uploads
API. For GitHub use native UI or an approved durable URL; do not create a gist,
release, commit, or unapproved upload merely to host an image.

## Maintain Durable State

Comment only for accepted scope changes, decisions controlling the solution,
blockers with an exact resume step, and final verification or delivery evidence.
After resume or compaction, recover only the live state needed for the selected
operation. Read branches, reviews, CI, or worktrees when delivery depends on them.

## Close the Requirement

Close only when the selected workflow authorizes it and all required work is
remotely verifiable:

- change satisfied items from `[ ]` to `[x]` only with evidence;
- preserve cancelled or superseded criteria as non-checkbox strikethrough text
  with a reason;
- leave the Issue open while any unchecked item, required review, CI,
  environment check, or product acceptance remains;
- verify the final commit or merged review request on the authoritative remote;
- add one concise final note with the commit or review URL, checks, and residual
  limitations.

Before an explicit closure, or a direct `Closes` push for which no post-push
acceptance remains, validate the final body:

```bash
python3 <skill-directory>/scripts/issue_markdown.py \
  --check-closure --provider github --action edit \
  --repo <owner/repo> --issue <id> <<'MARKDOWN'
<reconciled body>
MARKDOWN
```

Use the corresponding GitLab arguments when needed. After closure, read the
provider state back. Reopen a regression or create a clearly linked follow-up;
never hide failed verification.

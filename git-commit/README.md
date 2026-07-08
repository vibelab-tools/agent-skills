# git-commit

Agent skill for writing, reviewing, validating, and creating Git commit
messages using Conventional Commits 1.0.0.

## Purpose

Use this skill when an agent needs to turn staged or unstaged repository
changes into a clear commit message, review an existing message for compliance,
choose an appropriate type and scope, describe breaking changes, or create an
actual commit after checking the intended staged changes.

The skill favors messages that describe the resulting behavior, not a raw file
list.

## Build

No build step is required.

```bash
make
```

## Install

```bash
make install          # install for Codex and Claude Code
make install-codex    # install for Codex only
make install-claude   # install for Claude Code only
```

Installed locations:

- Codex: `~/.codex/skills/git-commit`
- Claude Code: `~/.claude/skills/git-commit`

## Commit Style

The default message format is:

```text
<type>[optional scope][optional !]: <description>

[optional body]

[optional footer(s)]
```

Repository-specific commit rules take precedence when a project provides a
commitlint configuration, contributing guide, release process, or clear recent
history.

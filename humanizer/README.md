# Humanizer

Humanizer now works at two levels:

- The skill can compose, diagnose, lightly edit, or deeply rewrite Chinese, English, and mixed-language prose while preserving facts and technical precision.
- The Codex plugin runs a `UserPromptSubmit` hook before every answer. It adds a short writing guide as developer context, so the first draft favors direct, established wording instead of waiting for a second rewrite pass.

The hook does not call another model, inspect the completed answer, or use a `Stop` hook. It is guidance rather than a deterministic text filter: higher-priority instructions, exact output formats, and task-specific terminology still apply. Code, commands, identifiers, paths, quotations, factual qualifiers, and established technical terms are explicitly protected.

## Install

Install both Codex surfaces with:

```bash
make install-codex
```

This installs the regular skill under `~/.codex/skills/humanizer`, copies the plugin into a managed local marketplace under `~/.vibelab-tools/agent-skills/humanizer`, and registers it with Codex. Installing the plugin is the trust boundary for its local hook command. Start a new thread after installation so the new hook and skill context are loaded.

Claude Code installation remains a regular skill install and does not enable the Codex hook:

```bash
make install-claude
```

## Validate

```bash
make validate
```

Validation checks the skill and plugin manifests, exercises the hook output at its command boundary, verifies the frozen rewrite corpus, and checks the separate forward-generation benchmark registration.

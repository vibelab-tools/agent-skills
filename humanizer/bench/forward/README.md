# Humanizer forward-generation benchmark

This suite tests the gap that a rewrite-only benchmark cannot cover: ordinary Codex prompts where the user supplies facts or a task, but does not mention Humanizer or provide a draft to edit.

Each case has two arms. The baseline runs with plugins and hooks disabled. The treatment runs in a fresh session with the repository Humanizer plugin installed, so its `UserPromptSubmit` hook supplies [pre-draft guidance](../../references/pre-draft.md) before the model writes the answer. Both arms must use the same model, reasoning effort, facts, and prompt.

Judge the anonymized outputs against the registered rubric in [config.json](config.json). Factual fidelity is a gate. Naturalness must not be rewarded when an answer loses a qualifier, invents a fact, or replaces an exact technical term. “Unnecessary jargon cost” measures whether the answer invents compressed labels or makes the reader decode wording that adds no precision; it is not a banned-word count.

The suite does not yet contain scored outputs. It is kept separate from the frozen six-case rewrite benchmark so adding first-draft coverage does not alter or imply a rerun of the published comparison.

Run the deterministic corpus check with:

```bash
python3 verify.py
```

---
name: humanizer
description: Rewrite or review Chinese, English, and mixed-language prose so it sounds natural, specific, and easy for its intended readers to understand without changing its claims. Use when asked to humanize text, remove AI or chatbot phrasing, reduce translationese, match a supplied voice, simplify needless jargon, improve reader-facing clarity, or edit prose that feels formulaic, inflated, generic, or mechanically structured.
---

# Humanizer

Improve the writing, not its supposed authorship score. Preserve what the source says while making the expression fit its writer, readers, language, and genre.

## Choose the operation

- **Diagnose** when the user asks what feels artificial or hard to read. Cite specific passages, explain the combined pattern, and suggest a direction. Do not claim to identify who or what wrote the text.
- **Light edit** when the user asks for polish, wants to retain wording, or the draft already has a clear voice. Change only the passages that cause a real problem.
- **Deep rewrite** when the structure itself is repetitive, translated, inflated, or difficult to follow. Freeze the claim set, then rebuild the prose rather than swapping synonyms sentence by sentence.
- If the user does not choose, use light edit for already sound prose and deep rewrite when local edits would leave the original skeleton intact.

## Establish the contract

Before editing, identify:

1. The source language or languages.
2. The intended readers and what knowledge they can reasonably be expected to have.
3. The genre, register, purpose, and requested length.
4. Project instructions, terminology, formatting rules, and user-supplied writing samples.
5. The parts that must remain exact.

User and project requirements override general style advice. A relevant writing sample outranks generic notions of “human” prose.

## Freeze meaning, not structure

Make a claim map before a deep rewrite. Preserve:

- every factual claim, qualification, uncertainty, negation, comparison, causal relationship, scope limit, and audience condition;
- names, numbers, units, dates, weekdays, times, prices, versions, acronym expansions, quotations, citations, URLs, link targets, code, commands, identifiers, and paths;
- the writer's actual position, including mixed feelings and deliberate ambiguity;
- required headings, Markdown, data, and document structure when the format is part of the contract.

Do not invent evidence, examples, anecdotes, sources, opinions, sensory details, or “plausible” specifics. Do not strengthen a cautious claim or turn correlation into causation. If the source lacks a needed detail, keep the gap visible or ask for the detail.

Deep rewrites may reorder, merge, split, or remove redundant expression. They may not drop a distinct claim just because it sounds like framing or filler. After drafting, compare the result against the claim map, not merely against the source's sentence order.

For fact-dense text, run `python3 scripts/check_anchors.py SOURCE REWRITE`. Treat it as a mechanical backstop for exact anchors, not as proof of semantic fidelity.

## Reduce reader effort

Aim for the shortest path from wording to meaning while retaining necessary precision.

- Prefer concrete subjects, actions, constraints, and outcomes over abstract labels.
- Keep a specialized term when it is the precise term the audience needs. Explain it at first use when the intended reader may not know it.
- Expand an unfamiliar abbreviation at first use unless the audience or project convention makes it safely assumed.
- Replace jargon, stacked nouns, metaphorical labels, imported phrasing, and clever shorthand when they add decoding cost but no precision.
- Make references and pronouns resolve clearly. Name the actor when omitting it hides responsibility or sequence.
- Do not “simplify” away domain distinctions, standard product names, API names, legal language, or established terminology.

When the audience is unspecified, write for an informed general reader while preserving essential domain terms and defining the non-obvious ones briefly.

## Rewrite in passes

1. **Structure:** Give each paragraph one useful job. Remove duplicated setup, repeated conclusions, empty sections, and predictable scaffolding. Keep lists when the content is genuinely list-shaped.
2. **Claims:** Re-express the mapped claims in a natural order. Use specific source facts as anchors; never manufacture specificity.
3. **Language:** For English, read [references/english.md](references/english.md). For Chinese, read [references/chinese.md](references/chinese.md). For mixed text, read both and preserve intentional code-switching.
4. **Voice and register:** Match supplied samples and the document's purpose. Preserve meaningful quirks. Do not force contractions, first person, slang, humor, fragments, or informality.
5. **Reader clarity:** Apply the reader-effort rules above after the domain meaning is stable.
6. **Fidelity:** Audit every source claim and exact anchor. Restore anything lost; remove anything added.
7. **Read aloud:** Check flow, paragraph movement, and whether the prose sounds natural in its own language rather than translated from another one.

## Diagnose patterns in context

Treat patterns as editing clues, not banned tokens or authorship evidence. Look for clusters that damage the passage:

- inflated importance plus vague evidence;
- repetitive sentence or paragraph shapes;
- stock contrasts, triads, transitions, openings, or conclusions used repeatedly;
- synonym cycling where one precise term should repeat;
- stacked hedges or unjustified certainty;
- decorative formatting, chatbot framing, or explanations of the answer instead of the answer;
- uniformly abstract prose that never names an actor, mechanism, constraint, or result.

One formal word, passive sentence, three-item list, em dash, idiom, or polished paragraph proves nothing. Count only observable repetitions before calling something overused. Preserve quotations, code, titles, proper names, deliberate rhetoric, and house style.

## Output

- By default, return only the revised text. Do not add a preamble or changelog.
- In diagnose mode, return the diagnosis and concrete revision directions without rewriting unless asked.
- When editing a file, change prose only and then summarize the file change briefly in the normal task response.
- If the user asks for rationale, give a short explanation after the rewrite.
- Never promise detector evasion, assign an “AI score,” or present style heuristics as objective authorship detection.

## Final gate

Before delivery, confirm:

- The result answers the same question and carries every source claim at the same strength.
- Exact anchors and protected formatting survived.
- No new fact, example, authority, opinion, or personal experience appeared.
- The language sounds native, the register fits, and useful technical precision remains.
- A reader in the target audience can decode necessary terms without needless jargon.
- Edits respond to contextual problems rather than a punctuation quota or banned-word list.
- Already good, distinctive writing was left alone.

For the design rationale and the specific ideas adopted or rejected from reviewed public skills, read [references/source-review.md](references/source-review.md).

# Source review and design decisions

This skill was synthesized from a source-level review of eight public humanizer skills on 2026-08-28. The purpose of this ledger is maintainability: it records which ideas were retained, which were rejected, and why. It is not a runtime checklist and should be read only when changing the skill's design.

| Source snapshot | Useful ideas retained | Ideas deliberately rejected |
| --- | --- | --- |
| [blader/humanizer](https://github.com/blader/humanizer) `e2e92e7b4b82` (v2.11.2) | Fact preservation, voice-sample priority, broad pattern taxonomy, contextual false-positive checks, genre-sensitive personality | Zero-tolerance punctuation rules; treating a watched word as a defect by itself; adding opinions unless explicitly grounded in the writer's voice |
| [ai-zixun/humanizer-zh](https://github.com/ai-zixun/humanizer-zh) `f75f1ac9735c` | Native-Chinese treatment of translationese, paragraph purpose, article-level continuity, slogan endings, project terminology overrides | Default imitation of named living authors; rigid universal punctuation and terminology choices; examples that sharpen claims beyond the source |
| [Skillproofdev/text-humanizer](https://github.com/Skillproofdev/text-humanizer) `14ceeb7b2a49` | Freeze the claim set while rebuilding structure; preserve claim strength and exact anchors; claim-by-claim audit; benchmark discipline and disclosed limitations | A fixed output-length band; assuming one rewrite intensity fits every draft; treating an in-context model judgment as an objective detector result |
| [jpeggdev/humanize-writing](https://github.com/jpeggdev/humanize-writing) `da03340e5bb3` | Structure-first passes, pattern stacking, quantitative claims must be counted, transitions can often be removed, read-aloud review | Fabricated specifics in rewrite examples; mandatory opinions, first person, asides, or “soul”; a compulsory change table after every rewrite |
| [Shirhussain/humanize](https://github.com/Shirhussain/humanize) `454179265115` | Separate diagnose and rewrite modes, register awareness, project voice profiles, precision-over-style rule, cluster-based false-positive guard | Always-on post-response rewriting of audience-facing prose; fixed punctuation budgets; broad banned-word lists; equating low sentence-length variance with authorship |
| [harshaneel/humanize](https://github.com/harshaneel/humanize) `4ec797314537` | Writer-profile distillation, domain-specific register checks, structure-first rewriting, explicit statement that detector evasion cannot be guaranteed | Perplexity and detector optimization as the goal; fixed burstiness arithmetic; deliberate disfluencies or errors; “compile-time” banned vocabulary; one mandatory aggressive rewrite path |
| [Aboudjem/humanizer-skill](https://github.com/Aboudjem/humanizer-skill) `17bb5bbd74d4` | Mode separation, preservation of code and quotations, warnings about short samples and non-native false positives, bilingual awareness | An ungrounded 0–100 AI score; pattern-count spectacle; complex flag surface; absolute em-dash bans; always-on rewrite mode |
| [op7418/Humanizer-zh](https://github.com/op7418/Humanizer-zh) `91f3d394db84` | Accessible Chinese catalog of common formulaic patterns and broad community awareness | Rewrites that add unsupported names, studies, anecdotes, or statistics; English-derived rules applied mechanically to Chinese; detector-oriented framing |

## Resulting design

The retained principles reinforce one another:

1. **Fidelity is the hard boundary.** Natural prose is not worth a changed fact.
2. **Structure is editable.** Preserving meaning does not require preserving an artificial sentence skeleton.
3. **Language guidance is native.** Chinese and English share semantic safeguards but need different surface diagnostics.
4. **Audience clarity is independent of casualness.** A specialist answer can be precise and readable without being simplified into generic language.
5. **Voice comes from evidence.** User and project samples are stronger than canned personas.
6. **Patterns are contextual.** Clusters can justify an edit; isolated punctuation or vocabulary cannot establish authorship.
7. **Mechanical checks stay modest.** The anchor checker catches exact-token drift and explicitly does not claim semantic verification.
8. **Pre-draft guidance is different from post-response rewriting.** A short `UserPromptSubmit` hook can steer the first draft without a second model call; the full skill remains available for deliberate composition, diagnosis, and rewriting.

## Known limits

- No prompt can prove whether prose was written by a person or a model.
- No deterministic script can verify all claims, named entities, implications, or causal relationships.
- Naturalness depends on audience and genre; there is no universally correct amount of formality, rhythm variation, or personality.
- Bilingual guidance does not justify translating intentional code-switching or established technical terms.
- The pre-draft hook supplies guidance, not a deterministic prose filter. Higher-priority instructions and task-specific formats still govern the answer.

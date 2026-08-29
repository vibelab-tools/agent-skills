# Humanizer comparative benchmark report

## Verdict

- Repository Humanizer ranks **1 of 6** overall by average rank (1.80); the aggregate leader is **Repository Humanizer** (1.80).
- It is the best average-rank arm for **5 of 5** judges. Every judge preferred it on that measure.
- It has the sole plurality of first-place votes in **2 of 6** cases and shares the plurality in **1** more; it has a strict judge majority in **2** cases.
- It does not lead every component score: naturalness is **4.67**, versus **4.77** for No-skill baseline. Its overall lead comes with 5.00 clarity, 4.97 register fit, and stronger fidelity.
- Its consensus claim integrity is **56/57 (98.2%)**, with 1 altered, 0 dropped, 0 disputed, and 0 cases with majority-detected additions. Exact anchors pass **5/6** cases.

## Aggregate results

Scores combine 5 independently blinded judge providers. Naturalness, reader clarity,
and register fit use a 1–5 scale where higher is better. Average rank uses 1 as best.
Altered/dropped are claim-level majority decisions; claims without a strict status majority
are disputed. Added counts cases where a majority flagged an added claim. Anchor preservation
is a mechanical value comparison, not a semantic score; Markdown code presentation and
grammatical plural inflection are ignored.

| Arm | Naturalness | Clarity | Register | Claims intact | Altered/dropped/disputed/added cases | Anchor pass | Judge wins | Case-majority wins | Avg rank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| No-skill baseline | 4.77 | 4.97 | 4.87 | 52/57 (91.2%) | 5/0/0/2 | 5/6 | 6/30 | 1/6 | 3.40 |
| Repository Humanizer | 4.67 | 5.00 | 4.97 | 56/57 (98.2%) | 1/0/0/0 | 5/6 | 14/30 | 2/6 | 1.80 |
| blader/humanizer | 4.57 | 4.73 | 4.73 | 54/57 (94.7%) | 2/1/0/0 | 4/6 | 5/30 | 1/6 | 3.27 |
| op7418/Humanizer-zh | 4.60 | 4.70 | 4.63 | 47/57 (82.5%) | 10/0/0/1 | 2/6 | 0/30 | 0/6 | 4.53 |
| ai-zixun/humanizer-zh | 4.70 | 4.27 | 4.20 | 51/57 (89.5%) | 5/1/0/1 | 3/6 | 2/30 | 0/6 | 4.17 |
| Skillproofdev/text-humanizer | 4.53 | 4.63 | 4.50 | 56/57 (98.2%) | 1/0/0/1 | 3/6 | 3/30 | 1/6 | 3.83 |

## What each judge preferred

The score triplet is naturalness/clarity/register for the repository Humanizer.

| Judge | Model | Best average-rank arm | Ours avg rank | Ours wins | Ours scores |
| --- | --- | --- | ---: | ---: | ---: |
| codex | gpt-5.6-sol | Repository Humanizer | 2.17 | 3/6 | 4.33/5.00/5.00 |
| bailian | qwen3.8-max | Repository Humanizer | 1.50 | 3/6 | 4.83/5.00/5.00 |
| kimi | kimi-k3 | Repository Humanizer | 1.67 | 2/6 | 4.67/5.00/5.00 |
| gemini | gemini-3.1-pro-preview | Repository Humanizer | 2.17 | 3/6 | 4.50/5.00/4.83 |
| deepseek | deepseek-v4-pro | Repository Humanizer | 1.50 | 3/6 | 5.00/5.00/5.00 |

## Repository Humanizer by case

The score triplet is naturalness/clarity/register.

| Case | Most judge votes | Ours avg rank | Ours scores | Ours claims | Ours anchors |
| --- | --- | ---: | ---: | ---: | --- |
| 01-en-remote-work | Repository Humanizer (4/5) | 1.80 | 4.6/5/5 | 10/10 | pass |
| 02-en-oauth | Skillproofdev/text-humanizer (3/5) | 1.60 | 4.4/5/5 | 10/10 | pass |
| 03-en-earnings | blader/humanizer (3/5) | 2.80 | 4.6/5/4.8 | 12/13 | fail |
| 04-zh-retail-analysis | blader/humanizer / Repository Humanizer (2/5) | 1.60 | 4.6/5/5 | 8/8 | pass |
| 05-zh-technical-handoff | No-skill baseline (3/5) | 1.80 | 4.8/5/5 | 8/8 | pass |
| 06-zh-maintenance-notice | Repository Humanizer (4/5) | 1.20 | 5/5/5 | 8/8 | pass |

## Language split

Each half contains three cases. The score triplet is naturalness/clarity/register.

| Arm | English avg rank | English scores | Chinese/mixed avg rank | Chinese/mixed scores |
| --- | ---: | ---: | ---: | ---: |
| No-skill baseline | 3.47 | 4.93/4.93/4.87 | 3.33 | 4.60/5.00/4.87 |
| Repository Humanizer | 2.07 | 4.53/5.00/4.93 | 1.53 | 4.80/5.00/5.00 |
| blader/humanizer | 3.20 | 4.60/4.87/4.73 | 3.33 | 4.53/4.60/4.73 |
| op7418/Humanizer-zh | 4.60 | 4.53/4.67/4.67 | 4.47 | 4.67/4.73/4.60 |
| ai-zixun/humanizer-zh | 4.40 | 4.73/4.00/3.67 | 3.93 | 4.67/4.53/4.73 |
| Skillproofdev/text-humanizer | 3.27 | 4.67/4.87/4.73 | 4.40 | 4.40/4.40/4.27 |

## Benchmark-driven corrections

- The frozen outputs exposed two checker false positives: moving the same command between inline and fenced Markdown, and changing `API` to the grammatical plural `APIs`. The checker now compares code by value and normalizes that plural inflection.
- All six arms dropped `Thursday` from the earnings case, while the old checker missed it. English and Chinese weekday forms are now normalized as exact anchors, and the Humanizer explicitly lists weekdays among protected facts.
- The original 36 generation outputs were not regenerated. The corrected checker therefore reports the repository Humanizer at 5/6 anchor passes, matching the 56/57 cross-judge claim result instead of hiding the failure.

## Recorded judge-call failures

Result-free judge-call failures remain visible even when a later retry succeeds.

| Judge | Case | Attempt | Category | Error | Final state |
| --- | --- | ---: | --- | --- | --- |
| deepseek | 03-en-earnings | 1 | external | IncompleteRead(2 bytes read) | recovered |
| deepseek | 04-zh-retail-analysis | 1 | external | judge API connection failed: SSLEOFError | recovered |
| deepseek | 05-zh-technical-handoff | 1 | external | judge API connection failed: SSLEOFError | recovered |
| deepseek | 06-zh-maintenance-notice | 1 | external | judge API connection failed: SSLEOFError | recovered |
| deepseek | 04-zh-retail-analysis | 2 | external | IncompleteRead(3 bytes read) | recovered |
| deepseek | 05-zh-technical-handoff | 2 | external | IncompleteRead(0 bytes read) | recovered |

## Method and limitations

- Generation used `gpt-5.6-luna` at `medium` reasoning for every arm.
- Judges were codex=gpt-5.6-sol, bailian=qwen3.8-max, kimi=kimi-k3, gemini=gemini-3.1-pro-preview, deepseek=deepseek-v4-pro. Each provider received an independently shuffled arm map.
- Six cases and one generation per arm are a compact directional benchmark, not a population estimate.
- All judges are LLMs, not a human panel. Provider diversity reduces but does not remove model-judge bias.
- No commercial AI detector was used. The benchmark measures observed writing quality and factual fidelity, not authorship.
- A judge call could be retried only when an external transport or retryable API failure returned no valid judgment. Malformed model judgments were not resampled; all failed attempts were retained and no scored result was discarded.
- Peer revisions, raw outputs, claim judgments, anchor reports, and the blinding map are retained beside this report.

# Humanizer comparative benchmark

This benchmark compares the repository Humanizer with a no-skill baseline and four public peer skills. It is designed to measure reader-facing rewrite quality and meaning preservation, not to detect authorship.

## Arms

- No-skill baseline
- Repository `humanizer`
- `blader/humanizer`
- `op7418/Humanizer-zh`
- `ai-zixun/humanizer-zh`
- `Skillproofdev/text-humanizer`

Exact peer revisions, generation model, five judge models, reasoning effort, prompts, and the deterministic blinding seed are recorded in [config.json](config.json). The judges use Codex, Alibaba Cloud Model Studio, Kimi, Gemini, and DeepSeek.

## Corpus

The six frozen cases cover English blog prose, an English technical explainer, fact-dense English financial news, Chinese industry analysis, a mixed-language technical handoff, and a Chinese customer notice.

Cases 01–03 and their claim inventories come from the MIT-licensed `Skillproofdev/text-humanizer` benchmark. The original license is preserved under [third-party/skillproof/LICENSE](third-party/skillproof/LICENSE). Cases 04–06 were written for this repository before any benchmark arm ran.

Run `python3 run_benchmark.py verify` to validate [corpus.sha256](corpus.sha256) before generation.

## Protocol

1. Each arm gets one fresh ephemeral Codex session per case, the same model, and the same case prompt. A run sees only the source and, for skill arms, that arm's skill files.
2. No generation is retried or cherry-picked. Existing output files block accidental reruns. A judge call may be retried when an external transport or retryable API failure produced no valid judgment; every failed attempt remains recorded. Malformed or incomplete model judgments are model failures and are not resampled.
3. Arm names are replaced with deterministic random labels before judging. Each judge gets an independently shuffled mapping, stored separately from the judge packet.
4. Five independent judge providers score naturalness, reader clarity, and register fit from 1–5; audit every pre-registered claim; record added claims; and rank all six outputs.
5. The dependency-free exact-anchor checker separately compares numbers, weekdays, versions, URLs, quotations, code, paths, citations, and abbreviations. It normalizes equivalent Chinese weekday forms, compares code by value even if a rewrite moves it between inline and fenced Markdown, and treats a grammatical plural `s` on an all-caps abbreviation as the same anchor.
6. `run_benchmark.py summarize` produces aggregate JSON and a Markdown report from the raw artifacts.

The LLM judges provide directional evidence, not a human panel or commercial AI detector. The final report must disclose exact models, single-run sample size, cross-provider agreement, judge independence limits, and any malformed or failed run.

## Commands

```bash
python3 run_benchmark.py verify
python3 run_benchmark.py generate --peer-root /path/to/cloned/peers
python3 run_benchmark.py judge
python3 run_benchmark.py summarize
```

The peer root must contain `blader`, `op7418`, `ai-zixun`, and `skillproof` checkouts at the revisions in `config.json`.

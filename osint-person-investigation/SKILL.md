---
name: osint-person-investigation
description: Conduct lawful open-source person research from a user prompt using exhaustive multilingual search planning, deep search-result paging, optional configured search APIs, public-source collection, identity disambiguation, evidence grading, and cited reporting. Use when an agent is asked to investigate, research, verify, profile, or map a person's public online presence, affiliations, publications, work history, public statements, or related entities from public sources. Do not use for doxxing, stalking, private contact discovery, credential lookup, accessing restricted data, impersonation, or harassment.
---

# OSINT Person Investigation

## Overview

Use this skill to perform careful public-source investigation about a person or
person-like public identity. The output must be source-grounded, uncertainty-aware,
and limited to lawful publicly accessible information.

## Boundaries

- Use only public sources, user-provided material, and lawful search/browse
  capabilities available to the host agent, such as Codex web search or Claude
  Code web search.
- Do not seek, infer, or publish private home addresses, private phone numbers,
  private email addresses, government IDs, credentials, family details, financial
  records, or other sensitive personal data unless the user provided it and it is
  directly necessary for a benign verification task.
- Do not access restricted systems, bypass paywalls or privacy controls,
  impersonate anyone, contact third parties, automate account creation, buy data,
  or use leaked databases.
- Do not assist stalking, harassment, intimidation, doxxing, blackmail, or
  targeted abuse. If the user asks for invasive or harmful details, narrow the
  task to a benign public-profile summary or refuse the harmful part.
- Treat same-name collisions as the default risk. Never merge identities without
  corroborating public evidence.
- Avoid sensitive-attribute claims unless they are explicitly public, directly
  relevant, and supported by reliable sources.

## Workflow

1. Define the target.
   - Extract the supplied name, aliases, usernames, organizations, locations,
     roles, domains, dates, and source URLs.
   - If the prompt is ambiguous and the investigation could affect a real
     private person, ask for the minimum clarifying detail needed to avoid
     conflating identities.
   - State the intended scope in neutral terms before searching.

2. Build a multilingual search plan.
   - Generate query families from exact names, aliases, usernames, handles,
     organizations, job titles, locations, domains, publications, projects, and
     date ranges.
   - Include the user's language, English, and the primary languages of likely
     locations, organizations, schools, employers, publications, or platforms.
   - Add transliterations and script variants when relevant, such as simplified
     Chinese, traditional Chinese, pinyin, romanized names, accented and
     unaccented spellings, initials, nicknames, and reversed name order.
   - Use search operators where helpful: quoted names, `site:`, `filetype:`,
     `intitle:`, `inurl:`, date filters, and exclusion terms for known false
     positives.

3. Search exhaustively.
   - General web search is mandatory. Always run broad general search first with
     the host agent's own search and browsing tools, then continue into
     specialized source categories. Do not assume a specific API, search engine,
     or browser implementation.
   - If optional configured search APIs are enabled, run the bundled search
     wrapper for high-value query families and merge those results with native
     host-agent search.
   - Do not stop after finding a few plausible results. Search broadly first,
     then deeply by entity, platform, language, location, organization, and time.
   - Page through search results as far as the available tool supports. If the
     tool exposes pagination or additional result pages, keep going until it
     stops offering more pages or relevant results are exhausted.
   - If the tool does not expose pagination, compensate with more targeted query
     variants, language variants, source-specific searches, and direct browsing
     from discovered links.
   - Continue until additional pages and query families no longer produce new
     relevant sources, not merely until the first good answer appears.
   - Maintain a query ledger with query text, language, source type, search depth,
     and whether it produced new leads.

4. Cover source categories.
   - Required categories: general web search, news sites, government and public
     record sites, social media, professional sites, academic sites, mainstream
     think tanks, public maps and imagery, short-video platforms, image search,
     organization pages, company directories, publications, patents, conference
     pages, repositories, package registries, interviews, podcasts, public legal
     or regulatory filings, public datasets, and archived pages.
   - Prefer primary sources for factual claims. Use secondary sources to discover
     leads, not as final proof when a primary source is available.

5. Expand recursively.
   - Turn every verified lead into follow-up queries: usernames, aliases, profile
     URLs, domains, employers, schools, paper titles, coauthors, project names,
     conference names, image filenames, video titles, locations tied to public
     organizations, and archived URLs.
   - For each confirmed social profile or repository, inspect public links,
     pinned projects, bios, usernames, organization names, publications, and
     outbound profile links, then search those leads again.
   - Stop only after additional pages, source categories, and follow-up leads no
     longer produce new relevant public evidence.

6. Capture evidence.
   - Record title, URL, publisher or platform, publication date or observed date,
     access date, relevant snippet or summary, source type, and reliability.
   - Save important URLs before analysis. If a page is dynamic or unstable, note
     what was visible and when it was accessed.
   - Keep raw search result observations separate from conclusions.
   - When using `osint-search`, preserve its trace JSON file and include it in
     the final source ledger or appendix.

7. Disambiguate identities.
   - Group evidence by candidate identity when names, handles, locations, or
     organizations conflict.
   - Require at least two independent public signals before joining profiles,
     such as the same handle linked from an official profile, matching employer
     plus publication history, or cross-linked personal pages.
   - Mark unresolved matches as uncertain instead of forcing a conclusion.

8. Verify claims before reporting.
   - Before writing the report, compare every material sentence against the
     source ledger. Remove unsupported claims or mark them explicitly as
     unverified.
   - Do not cite an LLM search summary as proof. Open and verify the cited public
     URLs directly, or keep the item as a lead only.
   - Assign stable source IDs such as `S1`, `S2`, and `T1` for trace files. Use
     those IDs in findings, timeline rows, and confidence notes.

9. Analyze and report.
   - Build a concise timeline of public activity.
   - Summarize affiliations, roles, public projects, publications, statements,
     online accounts, related organizations, and known gaps.
   - Grade confidence for each material claim: high, medium, low, or unverified.
   - Explain search coverage, languages used, query families tried, and where
     additional searching stopped because the tool stopped exposing more results
     or no new relevant sources appeared.

## Query Generation Checklist

- Exact name in quotes.
- Name plus organization, role, location, project, school, domain, or year.
- Username or handle alone, then with platform names.
- Name variants across languages and scripts.
- Local-language terms for role, employer, school, publication, lawsuit, award,
  interview, conference, GitHub, paper, patent, resume, CV, profile, and contact
  page.
- Source-specific searches such as official domains, news sites, academic
  indexes, code hosts, company registries, government portals, and professional
  platforms.
- False-positive exclusion terms discovered during the search.

## Optional Search APIs

The skill can supplement the host agent's own search with configured search
providers. This is optional and controlled by:

```text
~/.vibelab-tools/agent-skills/osint-person-investigation/config.json
```

For source-selection rationale and reviewed no-key candidates from public OSINT
directories, read `references/source-evaluation.md` when deciding whether to add
another provider or manual lead source.

Supported API providers:

- `brave`: Brave Search API.
- `google_cse`: Google Custom Search JSON API. Supports multiple `api_key` and
  `cx` pairs in `keys`; the wrapper rotates them across pages.
- `serper`: Serper.dev Google Search API.
- `gemini`: Gemini API with Google Search grounding when configured.
- `kimi`: Kimi/Moonshot Chat Completions with the `$web_search` built-in
  function when configured. The default `moonshot-v1-auto` is Moonshot's
  official automatic routing model for the Moonshot v1 family; public Kimi docs
  do not currently list a `kimi-latest` alias, so do not configure unverified
  alias names. For `kimi-k2.6` or another K2 model with `$web_search`, disable
  thinking through `extra_body`.
- `deepseek`: DeepSeek-compatible Chat Completions. Use as lead generation
  unless the configured endpoint or gateway returns verifiable web citations.
- `qwen`: Alibaba Cloud Model Studio or compatible Qwen Chat Completions with
  search enabled when configured. The default `qwen-plus-latest` follows
  Alibaba Cloud's latest Qwen Plus mainline alias and is compatible with Chat
  Completions `enable_search`.

`kimi`, `qwen`, and `deepseek` force direct HTTP connections in code and do not
inherit ambient `http_proxy`, `https_proxy`, or `all_proxy` environment
variables.

Supported no-key or local public-source providers:

- `source_catalog`: mandatory general-search and vertical-source seed generation
  for general web, news, government, social, professional, academic, think tank,
  maps, images, short-video, OSINT directories, and domestic China source paths.
- `internet_archive`: Internet Archive metadata search.
- `common_crawl`: Common Crawl CDX URL discovery for URL or domain leads.
- `wikipedia` and `wikidata`: public encyclopedia and entity lookup.
- `openalex`, `crossref`, `arxiv`, `pubmed`, `dblp`, `europe_pmc`, and `orcid`:
  no-key academic, literature, author, and researcher-identity discovery.
- `hackernews`: Hacker News public search for technology-community mentions.
- `github`: GitHub public user and repository search.

Supported optional local OSINT tools:

- `sherlock`: username checks across public social platforms.
- `maigret`: username checks across a separate public site database. Keep
  `top_sites` bounded for routine use, or configure `sites` for focused checks.
- `the_harvester`: domain-focused public-source discovery. Keep
  `include_emails` disabled unless the user supplied a benign organizational
  domain and email collection is clearly in scope.
- `photon`: public URL/domain crawler for user-supplied sites or discovered
  public domains.

These local tools are installed by this skill's `make install-runtime` target
into the shared runtime under
`~/.vibelab-tools/agent-skills/osint-person-investigation`. Providers resolve
the managed wrapper commands automatically; do not require users to install
these tools globally first.

High-risk directories and contact-oriented sources may appear as manual
`source_catalog` leads when they are public and relevant, but do not automate
leaked-credential search, password lookup, private phone ownership lookup,
private email discovery, or harassment-oriented account enumeration.

Bing is intentionally not included as a no-key provider. The official Bing Web
Search API requires a subscription key or Azure setup, and this skill should not
scrape consumer search result pages as a replacement API.

Use this command after generating an important query:

```bash
~/.vibelab-tools/agent-skills/osint-person-investigation/bin/osint-search \
  --query '"Target Name" organization role' \
  --pretty
```

Operational rules:

- Treat API search as a recall booster, not a replacement for mandatory general
  web search, native host-agent search, and direct browsing.
- Treat LLM search responses as leads until the cited public URLs are opened and
  verified directly.
- Use enabled providers from config by default. For a one-off run, pass
  `--providers source_catalog,brave,google_cse,serper,gemini,kimi,qwen`.
- Use `--max-pages` only to lower or raise a run-specific search depth. The
  normal default comes from config. LLM providers ignore paging and return one
  synthesized result per query.
- Record provider names and result pages in the query ledger.
- Deduplicate URLs across native search and API search before analysis.
- Never print, quote, or include API keys in the final report.

## Report Format

Use this structure unless the user requests a different format:

```markdown
# OSINT Person Investigation Report

## Scope
- Target:
- User-provided identifiers:
- Boundaries:

## Executive Summary
Brief source-grounded findings and confidence level.

## Search Coverage
- Languages:
- Query families:
- Source categories:
- Exhaustion point:

## Identity Disambiguation
Candidate identities, linking evidence, conflicts, and unresolved uncertainty.

## Findings
| Claim | Source IDs | Confidence | Verification Notes |
| --- | --- | --- |

## Timeline
| Date | Event | Source |
| --- | --- | --- |

## Source Ledger
| ID | Source | URL | Date Accessed | Notes |
| --- | --- | --- | --- | --- |

## Search Trace Appendix
Trace files, query ledger, providers used, page depth, and unresolved leads.

## Gaps and Next Steps
What could not be verified from public sources and what benign follow-up search
could be done.
```

## Output Rules

- Cite every material claim with source IDs and links when web sources were used.
- Every finding, timeline event, and identity-linking statement must map to the
  source ledger. If a statement cannot be mapped, do not present it as fact.
- Use cautious language for uncertain matches.
- Do not include private or sensitive personal data that is not necessary for the
  user's benign purpose.
- If the user asks for only a brief answer, still include search coverage and
  confidence notes.

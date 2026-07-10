# osint-person-investigation

Agent skill for lawful open-source person research using exhaustive multilingual
search planning, deep search-result paging, optional configured search APIs,
public-source evidence collection, identity disambiguation, and cited reporting.
General web search is a required part of the workflow; provider integrations only
increase recall.

## Purpose

Use this skill when an agent is asked to investigate, research, verify, profile,
or map a person's public online presence, affiliations, publications, work
history, public statements, or related entities from public sources.

The skill is intentionally limited to public, lawful, benign research. It does
not support doxxing, stalking, private contact discovery, credential lookup,
restricted-source access, impersonation, harassment, or use of leaked databases.

## Dependencies

Runtime dependencies:

- The host agent's own search and browsing capabilities, such as Codex web search
  or Claude Code web search.
- Python 3 for the optional `osint-search` wrapper.
- Optional provider credentials for Brave Search API, Google Custom Search JSON
  API, Serper.dev Google Search API, Gemini, Kimi/Moonshot, DeepSeek, or Qwen.
- Python 3.12.x with working `venv`, `pip`, and `hashlib`, plus `git`, for the
  managed local OSINT tools installed by `make install-runtime`.
- Managed local OSINT command-line tools: Sherlock, Maigret, theHarvester, and
  Photon.
- No daemon or local service is installed.

Install-time dependencies:

- `rsync` for copying the skill into Codex and Claude Code skill directories.
- Python 3 only when the optional Codex skill validator is present under
  `$CODEX_HOME/skills/.system/skill-creator`; the Makefile skips validation when
  that validator is not installed.
- `git` and network access for cloning managed OSINT tools into the runtime
  vendor directory.

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
make uninstall        # remove installed skill copies; preserve config.json
make purge            # remove installed copies and config.json
```

Installed locations:

- Codex: `~/.codex/skills/osint-person-investigation`
- Claude Code: `~/.claude/skills/osint-person-investigation`
- Runtime config and wrapper: `~/.vibelab-tools/agent-skills/osint-person-investigation`
- Managed OSINT tool venvs:
  `~/.vibelab-tools/agent-skills/osint-person-investigation/venv`
- Managed OSINT tool source checkouts:
  `~/.vibelab-tools/agent-skills/osint-person-investigation/vendor`

## Optional Search API Configuration

The installer creates:

```text
~/.vibelab-tools/agent-skills/osint-person-investigation/config.json
```

Enable any combination of providers in that file:

Always-on recall seed provider:

- `source_catalog`: enabled by default. It produces mandatory general-search
  seeds plus public-source category seeds for news, government/public records,
  social media, professional sites, academic sites, mainstream think tanks, maps,
  imagery, short-video platforms, OSINT directories, and domestic China sources.
  It does not scrape result pages; it gives the agent direct follow-up search
  URLs and query strings to open and verify.

Credentialed search providers:

- `brave`: set `enabled` to `true` and configure `api_key`.
- `google_cse`: set `enabled` to `true` and configure one or more
  `{ "api_key": "...", "cx": "..." }` entries under `keys`.
- `serper`: set `enabled` to `true` and configure `api_key`.
- `gemini`: set `enabled` to `true`, configure `api_key`, `endpoint`, and
  `model`, and keep `google_search` enabled when the selected model supports it.
- `kimi`: set `enabled` to `true`, configure `api_key`, `endpoint`, and `model`,
  and keep `web_search` enabled for Moonshot's built-in search function. The
  default `moonshot-v1-auto` is Moonshot's official automatic routing model for
  the Moonshot v1 family. Moonshot's public docs do not currently list a
  `kimi-latest` alias, and the Moonshot API should not be configured with
  unverified alias names. If you switch to `kimi-k2.6` or another K2 model while
  using `$web_search`, set `extra_body` to `{ "thinking": { "type":
  "disabled" } }` because K2 web search is not compatible with thinking mode.
- `deepseek`: set `enabled` to `true`, configure `api_key`, `endpoint`, and
  `model`. Treat output as lead generation unless your configured endpoint
  returns verifiable web citations.
- `qwen`: set `enabled` to `true`, configure `api_key`, `endpoint`, and `model`,
  and keep `enable_search` enabled for compatible Alibaba Cloud Model Studio
  endpoints. The default `qwen-plus-latest` follows Alibaba Cloud's latest
  Qwen Plus mainline alias and works with Chat Completions `enable_search`.

`kimi`, `qwen`, and `deepseek` are called with proxy inheritance disabled in
code. They ignore ambient `http_proxy`, `https_proxy`, and `all_proxy`
environment variables so domestic endpoints are reached directly.

No-key public data providers:

- `internet_archive`: searches Archive.org metadata.
- `common_crawl`: searches the latest Common Crawl CDX index for URL or domain
  leads.
- `wikipedia` and `wikidata`: public encyclopedia and entity lookup.
- `hackernews`: Hacker News public Algolia search for technology-community
  mentions, company discussion, usernames, and public project references.
- `openalex`, `dblp`, `europe_pmc`, and `orcid`: no-key academic, literature,
  author, and researcher-identity discovery. They complement `crossref`,
  `arxiv`, and `pubmed`.
- `github`: unauthenticated public GitHub search by default. Configure `token`
  only if you need higher GitHub API limits.

Managed local tools:

- `sherlock`: username-to-social-profile discovery. Installed from the
  `sherlock-project` Python package into its own runtime venv.
- `maigret`: username-to-profile discovery with a separate site database and
  JSON output. Installed from the `maigret` Python package into its own runtime
  venv. Keep `top_sites` bounded for routine runs; use `sites` for narrow tests.
- `the_harvester`: domain-focused public reconnaissance. The default config does
  not include emails in output. Installed from the upstream GitHub project into
  its own runtime venv.
- `photon`: URL/domain crawler for public websites supplied by the user or found
  during an investigation. Installed from the upstream GitHub project into the
  runtime vendor directory.

`make install-runtime` creates one venv per managed tool, then writes wrapper
commands under the runtime `bin` directory. Providers resolve those wrappers
automatically. The wrappers set `HOME` to the skill runtime state directory so
tool defaults do not write into the user's real home directory. Existing
`config.json` files are preserved; missing default keys are merged in without
overwriting user values or credentials.

The source catalog includes some high-risk lead directories only as manual
public-source entry points when they are relevant to a lawful, bounded task. The
skill does not automate leaked-credential search, password lookup, private phone
ownership lookup, private email discovery, or harassment-oriented account
enumeration.

Bing is not configured as a no-key provider. Official Bing Web Search access
requires a subscription key or Azure setup, and this skill does not scrape
consumer search result pages as an API replacement.

Run a search manually:

```bash
~/.vibelab-tools/agent-skills/osint-person-investigation/bin/osint-search \
  --query '"Target Name" organization role' \
  --pretty
```

List configured providers without making API calls:

```bash
~/.vibelab-tools/agent-skills/osint-person-investigation/bin/osint-search \
  --list-providers \
  --pretty
```

`make uninstall` preserves `config.json`. Use `make purge` only when local API
credentials should be removed. `make uninstall` removes the managed venv,
vendor checkouts, wrappers, and runtime state.

## Trace Files

`search.trace.enabled` is `true` by default. Each `osint-search` run writes a
JSON trace under:

```text
~/.vibelab-tools/agent-skills/osint-person-investigation/traces
```

The trace records query text, provider start/finish/error events, result counts,
page/rank metadata, URLs, and optional snippets. Final reports should reference
the trace file in an appendix and map every material claim to source IDs in the
source ledger.

## Workflow Summary

The skill guides an agent through:

1. Defining the target and scope.
2. Generating multilingual search query families.
3. Searching exhaustively across available result pages, enabled optional search
   APIs or LLM search providers, mandatory general search, and specialized
   source categories.
4. Recursively expanding verified leads until new searches stop producing new
   public evidence.
5. Capturing trace files and a source ledger with dates, URLs, and reliability
   notes.
6. Disambiguating same-name people before drawing conclusions.
7. Producing a cited report where every material claim maps to source IDs.

# OSINT Source Evaluation Notes

This note records why selected no-key sources from `jivoi/awesome-osint` and
`Astrosp/Awesome-OSINT-List` are integrated, excluded, or kept as manual leads.
It is a maintenance reference, not a report template.

## Integration Rules

- Prefer sources that work without API keys, paid accounts, browser login, or
  invasive workflows.
- Prefer public-search APIs and stable public search pages over fragile scraping.
- Automate structured public indexes when the result data is bounded and
  evidence-oriented.
- Keep high-risk contact, breach, leak, and account-existence tools as manual
  leads only. Do not automate private phone ownership lookup, private email
  discovery, credential lookup, password search, leaked database search, or
  harassment-oriented account enumeration.
- For person investigations, every lead must still be opened and verified before
  it can support a claim.

## Integrated as Structured Providers

| Source | Category | Reason |
| --- | --- | --- |
| Hacker News Algolia | Technology community | No-key public search over stories and comments. Useful for public project, company, and handle mentions. |
| OpenAlex | Academic literature | No-key works search passed smoke testing and returns structured source metadata. |
| DBLP | Academic literature | No-key computer-science bibliography search. |
| Europe PMC | Academic literature | No-key biomedical and life-science literature search. |
| ORCID | Researcher identity | No-key public ORCID search. Useful as a candidate identity lead only. |
| Maigret | Username discovery | No-key username search with JSON output; complements Sherlock. Installed in an isolated venv. |

## Integrated as Source Catalog Seeds

| Source family | Examples | Reason |
| --- | --- | --- |
| General search and metasearch | Google, Bing, DuckDuckGo, Startpage, Qwant, Swisscows, Yahoo, Yandex, Mojeek | Expands recall without API keys. |
| News discovery | Google News, Bing News, Yahoo News, NewsNow | Useful entry points for public-media coverage. |
| Public records and legal records | CourtListener, JudyRecords, Black Book Online, SEC EDGAR, UK Companies House | Public evidence sources; final claims still need direct verification. |
| Company research | OpenCorporates, ICIJ Offshore Leaks, SEC EDGAR, UK Companies House, Corporation Wiki, Owler | Company and officer leads from public or semi-public directories. |
| Social and professional platforms | X, Bluesky, Threads, Facebook, Instagram, Reddit, YouTube, TikTok, GitHub, GitLab, Substack, Zhihu, Weibo, Bilibili | Public-profile and public-post discovery; identity matches must be corroborated. |
| Academic indexes | Google Scholar, OpenAlex, Semantic Scholar, ORCID, DBLP, arXiv, OpenReview, DOAJ, Europe PMC, OpenAIRE, CORE, HAL, Zenodo, OSF, SSRN, RePEc, PubMed, ERIC, WorldCat, CNKI, WanFang, AMiner | Broad literature and author discovery across regions and disciplines. |
| Think tanks | RAND, Brookings, CSIS, CFR, Carnegie, Chatham House, IISS, Atlantic Council, SIPRI, RUSI, MERICS, CNAS, Hoover, Heritage, Cato, C4ADS, Jamestown, Hudson, Belfer, Stimson, ECFR, SWP, Lowy, RSIS, CASS, CIIS, CCG | Public policy and expert-publication coverage. |

## Reviewed but Not Automated by Default

| Tool or family | Decision |
| --- | --- |
| PhoneInfoga, reverse phone lookup sites, caller ID sites | Manual lead only. They can quickly become private phone ownership lookup. |
| GHunt, Holehe, account-existence checkers, password-reset probes | Not automated. These infer account registration and can become invasive. |
| DeHashed, LeakCheck, InfoStealers, COMB, BreachDirectory, leaked credential indexes | Not automated. They center leaked credentials or sensitive personal data. |
| Hunter, Prospeo, VoilaNorbert, Toofr, Reverse Contact, Apollo-style contact databases | Not automated. They are contact discovery or sales-intelligence systems. |
| Blackbird, NexFil, Social Analyzer, WhatsMyName web, NameChk-style websites | Sherlock and Maigret cover the same core username use case with local tooling. Keep these as manual comparison leads if needed. |
| Face search and face-comparison services | Not automated. They can identify private people from images and often require uploads to third-party services. |
| Dark-web search engines and Tor crawlers | Not automated. They are outside the default lawful public-source workflow and increase handling risk. |
| Offensive security recon suites | Not integrated unless the task is domain/company security research and the tool stays within public passive collection. |
| GDELT DOC 2.0 | Removed from the structured provider set after smoke testing showed unstable public endpoint behavior in this environment. Reconsider only with repeatable endpoint checks. |
| Semantic Scholar API | Removed from the structured provider set after unauthenticated smoke testing returned HTTP 429. Reconsider only with a working API key and repeatable smoke test. |
| SpiderFoot | Removed from the local tool provider set after narrow smoke testing timed out. Reconsider only if a deterministic bounded CLI invocation is found. |
| Wayback CDX | Removed from the structured provider set after repeat smoke testing hit endpoint timeouts. Use direct browser checks or Archive.org metadata leads instead. |
| Brave consumer search, Reuters site search, AllSides, Vimeo, LinkedIn, Medium, ResearchGate, OpenOwnership, BBB, Crunchbase, Craft, Manta, BASE, OATD, IFRI | Removed from default source-catalog seeds because smoke testing hit automation blocks or unstable responses. Keep as manual browser leads only when a specific investigation justifies them. |

## Future Candidates

- Add a no-key `courtlistener` provider if its public API remains stable without
  a token for basic search.
- Add a no-key `opencorporates` provider only if public search can be used
  without scraping or paid API assumptions.
- Add a no-key `doaj` provider if its current public API response shape remains
  stable across article searches.

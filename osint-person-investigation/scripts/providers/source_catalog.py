"""Seed provider for mandatory broad search and vertical source discovery."""

from __future__ import annotations

import re
import urllib.parse
from typing import Any

from .common import int_config, normalize_result


CN_MARKERS = (
    "bilibili",
    "caixin",
    "cass",
    "ccg",
    "ciis",
    "cnki",
    "csdn",
    "douban",
    "douyin",
    "gitee",
    "gov.cn",
    "juejin",
    "oschina",
    "segmentfault",
    "thepaper",
    "wanfang",
    "weibo",
    "xiaohongshu",
    "zhihu",
)

REFERENCE_DIRECTORIES = [
    ("OSINT Framework", "https://osintframework.com/"),
    ("Awesome OSINT", "https://github.com/jivoi/awesome-osint"),
    ("Awesome OSINT List", "https://github.com/Astrosp/Awesome-OSINT-List"),
]

GENERAL_SEARCH_ENGINES = [
    ("Google Search", "https://www.google.com/search?q={encoded}"),
    ("Bing Search", "https://www.bing.com/search?q={encoded}"),
    ("DuckDuckGo", "https://duckduckgo.com/?q={encoded}"),
    ("Startpage", "https://www.startpage.com/sp/search?query={encoded}"),
    ("Qwant", "https://www.qwant.com/?q={encoded}"),
    ("Swisscows", "https://swisscows.com/en/web?query={encoded}"),
    ("Yahoo Search", "https://search.yahoo.com/search?p={encoded}"),
    ("Yandex Search", "https://yandex.com/search/?text={encoded}"),
    ("Mojeek", "https://www.mojeek.com/search?q={encoded}"),
]

GENERAL_QUERY_TEMPLATES = [
    "{query}",
    "\"{query}\"",
    "\"{query}\" profile biography CV resume",
    "\"{query}\" affiliation employer university publication",
    "\"{query}\" GitHub OR LinkedIn OR X",
    "\"{query}\" filetype:pdf OR filetype:ppt OR filetype:doc",
]

NEWS_QUERY_TEMPLATES = [
    "\"{query}\" news",
    "\"{query}\" interview",
    "\"{query}\" announced OR appointed OR joined",
    "site:apnews.com \"{query}\"",
    "site:bbc.com \"{query}\"",
    "site:nytimes.com \"{query}\"",
    "site:theguardian.com \"{query}\"",
    "site:caixin.com \"{query}\"",
    "site:thepaper.cn \"{query}\"",
]

NEWS_SEARCH_URLS = [
    ("Google News", "https://news.google.com/search?q={encoded}"),
    ("Bing News", "https://www.bing.com/news/search?q={encoded}"),
    ("Yahoo News", "https://news.search.yahoo.com/search?p={encoded}"),
    ("NewsNow", "https://www.newsnow.co.uk/h/World+News?q={encoded}"),
]

GOVERNMENT_QUERY_TEMPLATES = [
    "site:.gov \"{query}\"",
    "site:gov.cn \"{query}\"",
    "site:gov.uk \"{query}\"",
    "site:europa.eu \"{query}\"",
    "site:sec.gov \"{query}\"",
    "site:uspto.gov \"{query}\"",
    "site:courtlistener.com \"{query}\"",
    "site:justice.gov \"{query}\"",
]

PUBLIC_RECORD_QUERY_TEMPLATES = [
    "site:courtlistener.com \"{query}\"",
    "site:judyrecords.com \"{query}\"",
    "site:blackbookonline.info \"{query}\"",
    "site:opencorporates.com \"{query}\"",
    "site:opensanctions.org \"{query}\"",
    "site:offshoreleaks.icij.org \"{query}\"",
    "site:sec.gov/edgar \"{query}\"",
    "site:gov.uk/government/organisations/companies-house \"{query}\"",
]

COMPANY_QUERY_TEMPLATES = [
    "site:opencorporates.com \"{query}\"",
    "site:offshoreleaks.icij.org \"{query}\"",
    "site:sec.gov/edgar \"{query}\"",
    "site:companieshouse.gov.uk \"{query}\"",
    "site:find-and-update.company-information.service.gov.uk \"{query}\"",
    "site:corporationwiki.com \"{query}\"",
    "site:owler.com \"{query}\"",
]

PROFESSIONAL_QUERY_TEMPLATES = [
    "site:github.com \"{query}\"",
    "site:gitlab.com \"{query}\"",
    "site:substack.com \"{query}\"",
    "\"{query}\" \"about\" \"contact\"",
    "\"{query}\" \"speaker\" OR \"bio\" OR \"portfolio\"",
]

SOCIAL_QUERY_TEMPLATES = [
    "site:x.com \"{query}\"",
    "site:bsky.app \"{query}\"",
    "site:threads.net \"{query}\"",
    "site:facebook.com \"{query}\"",
    "site:instagram.com \"{query}\"",
    "site:reddit.com \"{query}\"",
    "site:youtube.com \"{query}\"",
    "site:tiktok.com \"{query}\"",
    "site:zhihu.com \"{query}\"",
    "site:weibo.com \"{query}\"",
    "site:bilibili.com \"{query}\"",
    "site:douyin.com \"{query}\"",
]

MEDIA_SEARCH_URLS = [
    ("Google Images", "https://www.google.com/search?tbm=isch&q={encoded}"),
    ("Bing Images", "https://www.bing.com/images/search?q={encoded}"),
    ("Yandex Images", "https://yandex.com/images/search?text={encoded}"),
    ("Google Maps", "https://www.google.com/maps/search/{encoded}"),
    ("OpenStreetMap", "https://www.openstreetmap.org/search?query={encoded}"),
]

SHORT_VIDEO_SEARCH_URLS = [
    ("YouTube", "https://www.youtube.com/results?search_query={encoded}"),
    ("TikTok", "https://www.tiktok.com/search?q={encoded}"),
    ("Bilibili", "https://search.bilibili.com/all?keyword={encoded}"),
    ("Douyin", "https://www.douyin.com/search/{encoded}"),
]

SOCIAL_PLATFORMS = [
    ("GitHub", "https://github.com/{username}"),
    ("GitLab", "https://gitlab.com/{username}"),
    ("Gitee", "https://gitee.com/{username}"),
    ("X", "https://x.com/{username}"),
    ("Bluesky", "https://bsky.app/profile/{username}.bsky.social"),
    ("Threads", "https://www.threads.net/@{username}"),
    ("Mastodon", "https://mastodon.social/@{username}"),
    ("Reddit", "https://www.reddit.com/user/{username}"),
    ("YouTube", "https://www.youtube.com/@{username}"),
    ("TikTok", "https://www.tiktok.com/@{username}"),
    ("Instagram", "https://www.instagram.com/{username}/"),
    ("Facebook", "https://www.facebook.com/{username}"),
    ("Telegram", "https://t.me/{username}"),
    ("Medium", "https://medium.com/@{username}"),
    ("Substack", "https://{username}.substack.com/"),
    ("Zhihu", "https://www.zhihu.com/people/{username}"),
    ("Weibo", "https://weibo.com/{username}"),
    ("Bilibili", "https://space.bilibili.com/{username}"),
    ("Douban", "https://www.douban.com/people/{username}/"),
    ("CSDN", "https://blog.csdn.net/{username}"),
    ("Juejin", "https://juejin.cn/user/{username}"),
    ("SegmentFault", "https://segmentfault.com/u/{username}"),
    ("OSChina", "https://my.oschina.net/{username}"),
    ("Xiaohongshu Search", "https://www.xiaohongshu.com/search_result?keyword={encoded}"),
]

ACADEMIC_QUERIES = [
    ("Google Scholar", "site:scholar.google.com {query}"),
    ("OpenAlex", "site:openalex.org {query}"),
    ("Semantic Scholar", "site:semanticscholar.org {query}"),
    ("ORCID", "site:orcid.org {query}"),
    ("DBLP", "site:dblp.org {query}"),
    ("arXiv", "site:arxiv.org {query}"),
    ("OpenReview", "site:openreview.net {query}"),
    ("DOAJ", "site:doaj.org {query}"),
    ("Europe PMC", "site:europepmc.org {query}"),
    ("OpenAIRE", "site:explore.openaire.eu {query}"),
    ("CORE", "site:core.ac.uk {query}"),
    ("HAL", "site:hal.science {query}"),
    ("Zenodo", "site:zenodo.org {query}"),
    ("OSF", "site:osf.io {query}"),
    ("SSRN", "site:ssrn.com {query}"),
    ("RePEc", "site:ideas.repec.org {query}"),
    ("PubMed", "site:pubmed.ncbi.nlm.nih.gov {query}"),
    ("ERIC", "site:eric.ed.gov {query}"),
    ("WorldCat", "site:worldcat.org {query}"),
    ("CNKI", "site:cnki.net {query}"),
    ("WanFang", "site:wanfangdata.com.cn {query}"),
    ("AMiner", "site:aminer.cn {query}"),
]

THINK_TANK_QUERIES = [
    ("RAND", "site:rand.org {query}"),
    ("Brookings", "site:brookings.edu {query}"),
    ("CSIS", "site:csis.org {query}"),
    ("CFR", "site:cfr.org {query}"),
    ("Carnegie", "site:carnegieendowment.org {query}"),
    ("Chatham House", "site:chathamhouse.org {query}"),
    ("IISS", "site:iiss.org {query}"),
    ("Wilson Center", "site:wilsoncenter.org {query}"),
    ("Atlantic Council", "site:atlanticcouncil.org {query}"),
    ("SIPRI", "site:sipri.org {query}"),
    ("RUSI", "site:rusi.org {query}"),
    ("MERICS", "site:merics.org {query}"),
    ("CNAS", "site:cnas.org {query}"),
    ("Hoover", "site:hoover.org {query}"),
    ("Heritage", "site:heritage.org {query}"),
    ("Cato", "site:cato.org {query}"),
    ("C4ADS", "site:c4ads.org {query}"),
    ("Jamestown Foundation", "site:jamestown.org {query}"),
    ("Hudson Institute", "site:hudson.org {query}"),
    ("Belfer Center", "site:belfercenter.org {query}"),
    ("Stimson Center", "site:stimson.org {query}"),
    ("Center for American Progress", "site:americanprogress.org {query}"),
    ("ECFR", "site:ecfr.eu {query}"),
    ("SWP Berlin", "site:swp-berlin.org {query}"),
    ("Lowy Institute", "site:lowyinstitute.org {query}"),
    ("RSIS", "site:rsis.edu.sg {query}"),
    ("CSDS", "site:csds.vub.be {query}"),
    ("CASS", "site:cass.cn {query}"),
    ("CIIS", "site:ciis.org.cn {query}"),
    ("CCG", "site:ccg.org.cn {query}"),
]


def _add_query_seed(
    results: list[dict[str, Any]],
    *,
    query: str,
    title: str,
    suggested_query: str,
    source: str,
    search_engine: str = "Google Search",
) -> None:
    encoded = urllib.parse.quote_plus(suggested_query)
    if search_engine == "Google Search":
        url = "https://www.google.com/search?q=" + encoded
    elif search_engine == "Bing Search":
        url = "https://www.bing.com/search?q=" + encoded
    elif search_engine == "DuckDuckGo":
        url = "https://duckduckgo.com/?q=" + encoded
    else:
        url = "https://www.google.com/search?q=" + encoded
    results.append(
        normalize_result(
            provider="source_catalog",
            query=query,
            page=1,
            rank=len(results) + 1,
            title=title,
            url=url,
            snippet=suggested_query,
            source=source,
            extra={"suggested_query": suggested_query, "search_engine": search_engine},
        )
    )


def _add_query_templates(
    results: list[dict[str, Any]],
    *,
    query: str,
    templates: list[str],
    source: str,
    title_prefix: str,
    limit: int,
    include_domestic_cn: bool = True,
) -> bool:
    for suggested in templates:
        suggested_query = suggested.format(query=query)
        if not include_domestic_cn and _looks_domestic_cn(suggested_query):
            continue
        _add_query_seed(
            results,
            query=query,
            title=f"{title_prefix} follow-up query",
            suggested_query=suggested_query,
            source=source,
        )
        if len(results) >= limit:
            return True
    return False


def _looks_domestic_cn(value: str) -> bool:
    lowered = value.lower()
    return any(marker in lowered for marker in CN_MARKERS)


def _username_like(query: str) -> str | None:
    stripped = query.strip().lstrip("@")
    if re.fullmatch(r"[A-Za-z0-9_.-]{3,40}", stripped):
        return stripped
    handles = re.findall(r"@([A-Za-z0-9_.-]{3,40})", query)
    return handles[0] if handles else None


def _search_url(suggested_query: str) -> str:
    return "https://www.google.com/search?q=" + urllib.parse.quote_plus(suggested_query)


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del global_config, max_pages
    limit = int_config(count_override or provider.get("max_results"), 80, 1, 300)
    results: list[dict[str, Any]] = []
    username = _username_like(query)
    encoded = urllib.parse.quote(query.strip())
    include_domestic_cn = provider.get("include_domestic_cn", True) is not False

    for name, url in REFERENCE_DIRECTORIES:
        if provider.get("include_osint_directories", True):
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} source directory",
                    url=url,
                    snippet=(
                        "OSINT directory seed for finding additional public-source "
                        "databases and specialist search paths."
                    ),
                    source="osint_directory_seed",
                    extra={"directory": name},
                )
            )
            if len(results) >= limit:
                return results

    for template in GENERAL_QUERY_TEMPLATES:
        suggested_query = template.format(query=query)
        for engine, engine_template in GENERAL_SEARCH_ENGINES:
            url = engine_template.format(encoded=urllib.parse.quote_plus(suggested_query))
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{engine} general search",
                    url=url,
                    snippet=suggested_query,
                    source="general_search_seed",
                    extra={"suggested_query": suggested_query, "search_engine": engine},
                )
            )
            if len(results) >= limit:
                return results

    if provider.get("include_news", True) and _add_query_templates(
        results=results,
        query=query,
        templates=NEWS_QUERY_TEMPLATES,
        source="news_query_seed",
        title_prefix="News",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_news", True):
        for name, template in NEWS_SEARCH_URLS:
            url = template.format(encoded=encoded)
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} news search",
                    url=url,
                    snippet="News discovery seed. Verify articles directly before citing.",
                    source="news_search_seed",
                    extra={"search_engine": name},
                )
            )
            if len(results) >= limit:
                return results

    if provider.get("include_government", True) and _add_query_templates(
        results=results,
        query=query,
        templates=GOVERNMENT_QUERY_TEMPLATES,
        source="government_query_seed",
        title_prefix="Government and public records",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_public_records", True) and _add_query_templates(
        results=results,
        query=query,
        templates=PUBLIC_RECORD_QUERY_TEMPLATES,
        source="public_record_query_seed",
        title_prefix="Public records",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_company", True) and _add_query_templates(
        results=results,
        query=query,
        templates=COMPANY_QUERY_TEMPLATES,
        source="company_query_seed",
        title_prefix="Company research",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_professional", True) and _add_query_templates(
        results=results,
        query=query,
        templates=PROFESSIONAL_QUERY_TEMPLATES,
        source="professional_query_seed",
        title_prefix="Professional web",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_social", True) and _add_query_templates(
        results=results,
        query=query,
        templates=SOCIAL_QUERY_TEMPLATES,
        source="social_query_seed",
        title_prefix="Social media",
        limit=limit,
        include_domestic_cn=include_domestic_cn,
    ):
        return results

    if provider.get("include_social", True) and username:
        for name, template in SOCIAL_PLATFORMS:
            if not include_domestic_cn and _looks_domestic_cn(template):
                continue
            url = template.format(username=username, encoded=encoded)
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} profile candidate",
                    url=url,
                    snippet="Profile URL seed. Open and verify before treating as a match.",
                    source="social_profile_seed",
                    extra={"platform": name, "username": username},
                )
            )
            if len(results) >= limit:
                return results

    if provider.get("include_maps_images", True):
        for name, template in MEDIA_SEARCH_URLS:
            url = template.format(encoded=encoded)
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} public media search",
                    url=url,
                    snippet=(
                        "Open only for public images, maps, and organization or "
                        "venue context. Do not report private home locations."
                    ),
                    source="media_search_seed",
                    extra={"search_engine": name},
                )
            )
            if len(results) >= limit:
                return results

    if provider.get("include_short_video_images", True):
        for name, template in SHORT_VIDEO_SEARCH_URLS:
            if not include_domestic_cn and _looks_domestic_cn(template):
                continue
            url = template.format(encoded=encoded)
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} public video search",
                    url=url,
                    snippet="Short-video and video-platform seed. Verify profiles directly.",
                    source="short_video_search_seed",
                    extra={"platform": name},
                )
            )
            if len(results) >= limit:
                return results

    if provider.get("include_academic", True):
        for name, suggested in ACADEMIC_QUERIES:
            suggested_query = suggested.format(query=query)
            if not include_domestic_cn and _looks_domestic_cn(suggested_query):
                continue
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} follow-up query",
                    url=_search_url(suggested_query),
                    snippet=suggested_query,
                    source="academic_query_seed",
                    extra={"suggested_query": suggested_query},
                )
            )
            if len(results) >= limit:
                return results
    if provider.get("include_think_tanks", True):
        for name, suggested in THINK_TANK_QUERIES:
            suggested_query = suggested.format(query=query)
            if not include_domestic_cn and _looks_domestic_cn(suggested_query):
                continue
            results.append(
                normalize_result(
                    provider="source_catalog",
                    query=query,
                    page=1,
                    rank=len(results) + 1,
                    title=f"{name} follow-up query",
                    url=_search_url(suggested_query),
                    snippet=suggested_query,
                    source="think_tank_query_seed",
                    extra={"suggested_query": suggested_query},
                )
            )
            if len(results) >= limit:
                return results
    return results

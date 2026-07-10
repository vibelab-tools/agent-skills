#!/usr/bin/env python3
"""Run configured search providers for OSINT person investigations."""

from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any, Callable

from providers import (
    arxiv,
    brave,
    common_crawl,
    crossref,
    deepseek,
    dblp,
    europe_pmc,
    gemini,
    github,
    google_cse,
    hackernews,
    internet_archive,
    kimi,
    maigret,
    openalex,
    orcid,
    photon,
    pubmed,
    serper,
    sherlock,
    source_catalog,
    the_harvester,
    wikidata,
    wikipedia,
    qwen,
)
from providers.common import SearchError, int_config


SKILL_NAME = "osint-person-investigation"
DEFAULT_CONFIG = (
    Path.home() / ".vibelab-tools" / "agent-skills" / SKILL_NAME / "config.json"
)

ProviderSearch = Callable[
    [str, dict[str, Any], dict[str, Any], int, int | None],
    list[dict[str, Any]],
]

CREDENTIAL_PROVIDER_NAMES = {
    "brave",
    "google_cse",
    "serper",
    "gemini",
    "kimi",
    "deepseek",
    "qwen",
}

LOCAL_TOOL_PROVIDER_NAMES = {
    "sherlock",
    "maigret",
    "the_harvester",
    "photon",
}

PROVIDERS: dict[str, ProviderSearch] = {
    "brave": brave.search,
    "google_cse": google_cse.search,
    "serper": serper.search,
    "gemini": gemini.search,
    "kimi": kimi.search,
    "deepseek": deepseek.search,
    "qwen": qwen.search,
    "source_catalog": source_catalog.search,
    "sherlock": sherlock.search,
    "maigret": maigret.search,
    "the_harvester": the_harvester.search,
    "photon": photon.search,
    "hackernews": hackernews.search,
    "internet_archive": internet_archive.search,
    "common_crawl": common_crawl.search,
    "wikipedia": wikipedia.search,
    "wikidata": wikidata.search,
    "openalex": openalex.search,
    "dblp": dblp.search,
    "europe_pmc": europe_pmc.search,
    "orcid": orcid.search,
    "crossref": crossref.search,
    "arxiv": arxiv.search,
    "pubmed": pubmed.search,
    "github": github.search,
}

CONFIGURED_CHECKS: dict[str, Callable[[dict[str, Any]], bool]] = {
    "brave": brave.configured,
    "google_cse": google_cse.configured,
    "serper": serper.configured,
    "gemini": gemini.configured,
    "kimi": kimi.configured,
    "deepseek": deepseek.configured,
    "qwen": qwen.configured,
    "sherlock": sherlock.configured,
    "maigret": maigret.configured,
    "the_harvester": the_harvester.configured,
    "photon": photon.configured,
    "hackernews": hackernews.configured,
    "openalex": openalex.configured,
    "dblp": dblp.configured,
    "europe_pmc": europe_pmc.configured,
    "orcid": orcid.configured,
}


def load_config(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise SearchError(f"config not found: {path}")
    try:
        with path.open("r", encoding="utf-8") as handle:
            config = json.load(handle)
    except json.JSONDecodeError as exc:
        raise SearchError(f"invalid config JSON at {path}: {exc}") from exc
    if not isinstance(config, dict):
        raise SearchError(f"config root must be a JSON object: {path}")
    return config


def search_config(config: dict[str, Any]) -> dict[str, Any]:
    value = config.get("search", {})
    return value if isinstance(value, dict) else {}


def providers_config(config: dict[str, Any]) -> dict[str, Any]:
    value = search_config(config).get("providers", {})
    return value if isinstance(value, dict) else {}


def enabled_provider_names(
    config: dict[str, Any], requested: list[str] | None
) -> list[str]:
    providers = providers_config(config)
    if requested:
        names = requested
    else:
        names = [
            name
            for name in PROVIDERS
            if isinstance(providers.get(name), dict)
            and providers[name].get("enabled") is True
        ]
    unknown = [name for name in names if name not in PROVIDERS]
    if unknown:
        raise SearchError(f"unknown provider(s): {', '.join(unknown)}")
    return names


def provider_summary(config: dict[str, Any]) -> list[dict[str, Any]]:
    providers = providers_config(config)
    summary = []
    for name in PROVIDERS:
        provider = providers.get(name, {})
        if not isinstance(provider, dict):
            provider = {}
        configured_check = CONFIGURED_CHECKS.get(name)
        configured = configured_check(provider) if configured_check else True
        summary.append(
            {
                "provider": name,
                "enabled": provider.get("enabled") is True,
                "configured": configured,
                "requires_api_key": name in CREDENTIAL_PROVIDER_NAMES,
                "requires_local_command": name in LOCAL_TOOL_PROVIDER_NAMES,
            }
        )
    return summary


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run optional configured search providers for OSINT investigations."
    )
    parser.add_argument("query", nargs="?", help="Search query.")
    parser.add_argument(
        "-q",
        "--query",
        dest="query_option",
        help="Search query. Overrides positional query.",
    )
    parser.add_argument(
        "--config",
        type=Path,
        default=DEFAULT_CONFIG,
        help=f"Config JSON path. Default: {DEFAULT_CONFIG}",
    )
    parser.add_argument(
        "--providers",
        help="Comma-separated provider names. Defaults to enabled providers in config.",
    )
    parser.add_argument(
        "--max-pages",
        type=int,
        help="Maximum pages per provider for this query.",
    )
    parser.add_argument(
        "--count",
        type=int,
        help="Provider result count per page, capped by provider-specific limits.",
    )
    parser.add_argument(
        "--list-providers",
        action="store_true",
        help="Print configured providers and whether they are enabled.",
    )
    parser.add_argument(
        "--pretty",
        action="store_true",
        help="Pretty-print JSON output.",
    )
    parser.add_argument(
        "--trace-file",
        type=Path,
        help="Write a trace JSON file for hallucination checks.",
    )
    parser.add_argument(
        "--no-trace",
        action="store_true",
        help="Disable trace file output even when enabled in config.",
    )
    return parser.parse_args(argv)


def trace_config(global_config: dict[str, Any]) -> dict[str, Any]:
    value = global_config.get("trace", {})
    return value if isinstance(value, dict) else {}


def default_trace_file(
    config_path: Path, global_config: dict[str, Any], started_at: str
) -> Path | None:
    trace = trace_config(global_config)
    if trace.get("enabled") is not True:
        return None
    output_dir = trace.get("output_dir")
    if output_dir:
        directory = Path(str(output_dir)).expanduser()
    else:
        directory = config_path.parent / "traces"
    safe_time = started_at.replace(":", "").replace("-", "").replace("T", "-").rstrip("Z")
    return directory / f"osint-search-{safe_time}.json"


def result_trace(result: dict[str, Any], include_snippets: bool) -> dict[str, Any]:
    item = {
        "provider": result.get("provider"),
        "page": result.get("page"),
        "rank": result.get("rank"),
        "title": result.get("title"),
        "url": result.get("url"),
        "source": result.get("source"),
    }
    if include_snippets:
        item["snippet"] = result.get("snippet")
    return item


def write_trace(path: Path, trace: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as handle:
        json.dump(trace, handle, ensure_ascii=False, indent=2)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    started_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    try:
        config_path = args.config.expanduser()
        config = load_config(config_path)
        global_config = search_config(config)
        trace_settings = trace_config(global_config)
        trace_path = (
            None
            if args.no_trace
            else args.trace_file or default_trace_file(config_path, global_config, started_at)
        )
        trace: dict[str, Any] = {
            "started_at": started_at,
            "config_path": str(config_path),
            "query": args.query_option or args.query,
            "events": [],
        }
        if args.list_providers:
            output = {
                "ok": True,
                "config_path": str(config_path),
                "providers": provider_summary(config),
            }
            print(json.dumps(output, ensure_ascii=False, indent=2 if args.pretty else None))
            return 0

        query = args.query_option or args.query
        if not query:
            raise SearchError("query is required")
        requested = (
            [item.strip() for item in args.providers.split(",") if item.strip()]
            if args.providers
            else None
        )
        names = enabled_provider_names(config, requested)
        if not names:
            raise SearchError("no providers enabled in config")
        max_pages = int_config(
            args.max_pages or global_config.get("max_pages_per_query"), 10, 1, 100
        )

        providers = providers_config(config)
        results: list[dict[str, Any]] = []
        errors: list[dict[str, str]] = []
        for name in names:
            provider = providers.get(name, {})
            if not isinstance(provider, dict):
                errors.append({"provider": name, "error": "provider config is not an object"})
                continue
            provider_started = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            trace["events"].append(
                {"type": "provider_start", "provider": name, "time": provider_started}
            )
            try:
                provider_results = PROVIDERS[name](
                    query,
                    provider,
                    global_config,
                    max_pages,
                    args.count,
                )
                results.extend(provider_results)
                trace["events"].append(
                    {
                        "type": "provider_done",
                        "provider": name,
                        "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                        "result_count": len(provider_results),
                        "results": [
                            result_trace(
                                result,
                                include_snippets=trace_settings.get(
                                    "include_result_snippets"
                                )
                                is True,
                            )
                            for result in provider_results
                        ],
                    }
                )
            except SearchError as exc:
                errors.append({"provider": name, "error": str(exc)})
                trace["events"].append(
                    {
                        "type": "provider_error",
                        "provider": name,
                        "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
                        "error": str(exc),
                    }
                )

        output = {
            "ok": not errors,
            "started_at": started_at,
            "config_path": str(config_path),
            "query": query,
            "providers": names,
            "result_count": len(results),
            "results": results,
            "errors": errors,
            "trace_file": str(trace_path) if trace_path else "",
        }
        if trace_path:
            trace["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
            trace["result_count"] = len(results)
            trace["errors"] = errors
            write_trace(trace_path.expanduser(), trace)
        print(json.dumps(output, ensure_ascii=False, indent=2 if args.pretty else None))
        return 1 if errors and not results else 0
    except SearchError as exc:
        print(
            json.dumps(
                {
                    "ok": False,
                    "started_at": started_at,
                    "config_path": str(args.config.expanduser()),
                    "error": str(exc),
                },
                ensure_ascii=False,
                indent=2 if args.pretty else None,
            ),
            file=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))

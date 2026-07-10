"""Shared helpers for optional OSINT search providers."""

from __future__ import annotations

import json
import socket
import shutil
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any


class SearchError(Exception):
    """Provider search failure."""


def int_config(value: Any, default: int, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return default
    return max(minimum, min(maximum, parsed))


def float_config(value: Any, default: float) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return default


def bool_config(value: Any, default: bool) -> bool:
    if isinstance(value, bool):
        return value
    if isinstance(value, str):
        lowered = value.strip().lower()
        if lowered in {"1", "true", "yes", "on"}:
            return True
        if lowered in {"0", "false", "no", "off"}:
            return False
    return default


def request_text(
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    params: dict[str, Any] | None = None,
    body: dict[str, Any] | None = None,
    timeout: int = 20,
    use_proxy: bool = True,
) -> str:
    if params:
        query = urllib.parse.urlencode(
            {key: value for key, value in params.items() if value is not None},
            doseq=True,
        )
        separator = "&" if "?" in url else "?"
        url = f"{url}{separator}{query}"
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers=headers or {},
    )
    opener = None if use_proxy else urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        if opener is None:
            response_context = urllib.request.urlopen(request, timeout=timeout)
        else:
            response_context = opener.open(request, timeout=timeout)
        with response_context as response:
            return response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")[:500]
        raise SearchError(f"HTTP {exc.code} from {url}: {detail}") from exc
    except urllib.error.URLError as exc:
        raise SearchError(f"request failed for {url}: {exc.reason}") from exc
    except (TimeoutError, socket.timeout) as exc:
        raise SearchError(f"request timed out for {url}") from exc


def request_json(
    method: str,
    url: str,
    *,
    headers: dict[str, str] | None = None,
    params: dict[str, Any] | None = None,
    body: dict[str, Any] | None = None,
    timeout: int = 20,
    use_proxy: bool = True,
) -> Any:
    raw = request_text(
        method,
        url,
        headers=headers,
        params=params,
        body=body,
        timeout=timeout,
        use_proxy=use_proxy,
    )
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SearchError(f"non-JSON response from {url}: {raw[:500]}") from exc


def normalize_result(
    *,
    provider: str,
    query: str,
    page: int,
    rank: int,
    title: str | None,
    url: str | None,
    snippet: str | None,
    source: str | None = None,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return {
        "provider": provider,
        "query": query,
        "page": page,
        "rank": rank,
        "title": title or "",
        "url": url or "",
        "snippet": snippet or "",
        "source": source or "",
        "extra": extra or {},
    }


def configured_api_key(provider: dict[str, Any]) -> bool:
    return bool(provider.get("api_key"))


def timeout(global_config: dict[str, Any]) -> int:
    return int_config(global_config.get("timeout_seconds"), 20, 1, 120)


def user_agent(global_config: dict[str, Any]) -> str:
    return str(global_config.get("user_agent") or "vibelab-agent-skills-osint/1.0")


def resolve_command(provider: dict[str, Any], default: str) -> str | None:
    command = str(provider.get("command") or default)
    expanded = Path(command).expanduser()
    if expanded.is_absolute() or "/" in command:
        return str(expanded) if expanded.exists() else None
    system_path = shutil.which(command)
    if system_path:
        return system_path
    runtime_bin = Path(__file__).resolve().parents[1]
    candidate = runtime_bin / command
    return str(candidate) if candidate.exists() else None


def stop_when_no_new_urls(global_config: dict[str, Any]) -> bool:
    return bool_config(global_config.get("stop_when_page_has_no_new_urls"), True)


def llm_search_prompt(query: str) -> str:
    return (
        "Run a lawful public-source web search for an OSINT person investigation. "
        "Use search only for public information. Do not produce private addresses, "
        "private phone numbers, credentials, or sensitive personal data. Return "
        "potentially relevant public sources, URLs, dates, and short notes. Treat "
        "same-name collisions cautiously and mark uncertainty. Query:\n\n"
        f"{query}"
    )


def extract_openai_chat_text(data: dict[str, Any]) -> str:
    choices = data.get("choices")
    if not isinstance(choices, list) or not choices:
        return ""
    first = choices[0]
    if not isinstance(first, dict):
        return ""
    message = first.get("message")
    if not isinstance(message, dict):
        return ""
    content = message.get("content")
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for item in content:
            if isinstance(item, dict):
                text = item.get("text") or item.get("content")
                if text:
                    parts.append(str(text))
            elif item:
                parts.append(str(item))
        return "\n".join(parts)
    return ""


def merge_extra_body(body: dict[str, Any], provider: dict[str, Any]) -> dict[str, Any]:
    extra = provider.get("extra_body")
    if isinstance(extra, dict):
        body.update(extra)
    return body


def openai_chat_provider(
    provider_name: str,
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    body_overrides: dict[str, Any] | None = None,
    use_proxy: bool = True,
) -> dict[str, Any]:
    api_key = provider.get("api_key")
    if not api_key:
        raise SearchError(f"{provider_name}.api_key is empty")
    endpoint = provider.get("endpoint")
    if not endpoint:
        raise SearchError(f"{provider_name}.endpoint is empty")
    body: dict[str, Any] = {
        "model": provider.get("model"),
        "messages": [
            {
                "role": "system",
                "content": (
                    "You support lawful public-source OSINT research. Return "
                    "public web sources with URLs and uncertainty notes. Do not "
                    "produce private personal data."
                ),
            },
            {"role": "user", "content": llm_search_prompt(query)},
        ],
        "temperature": float_config(provider.get("temperature"), 0.2),
        "max_tokens": int_config(provider.get("max_tokens"), 4096, 1, 200000),
    }
    if body_overrides:
        body.update(body_overrides)
    merge_extra_body(body, provider)
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {api_key}",
    }
    data = request_json(
        "POST",
        str(endpoint),
        headers=headers,
        body=body,
        timeout=timeout(global_config),
        use_proxy=use_proxy,
    )
    if not isinstance(data, dict):
        raise SearchError(f"unexpected JSON response from {provider_name}")
    return data

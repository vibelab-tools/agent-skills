"""Gemini generateContent provider with Google Search grounding."""

from __future__ import annotations

from typing import Any

from .common import (
    SearchError,
    configured_api_key,
    float_config,
    int_config,
    llm_search_prompt,
    normalize_result,
    request_json,
    timeout,
)


DEFAULT_SEARCH_MODELS = [
    "gemini-3.1-pro-preview",
    "gemini-3-flash-preview",
    "gemini-2.5-pro",
    "gemini-2.5-flash",
    "gemini-3.5-flash",
    "gemini-2.5-flash-lite",
]


def configured(provider: dict[str, Any]) -> bool:
    return configured_api_key(provider)


def _extract_text(data: dict[str, Any]) -> str:
    if isinstance(data.get("output_text"), str):
        return data["output_text"]
    output = data.get("output")
    if isinstance(output, list):
        parts = []
        for item in output:
            if not isinstance(item, dict):
                continue
            content = item.get("content")
            if isinstance(content, list):
                for part in content:
                    if isinstance(part, dict) and isinstance(part.get("text"), str):
                        parts.append(part["text"])
            elif isinstance(content, str):
                parts.append(content)
        if parts:
            return "\n".join(parts)
    candidates = data.get("candidates")
    if isinstance(candidates, list):
        parts = []
        for candidate in candidates:
            if not isinstance(candidate, dict):
                continue
            content = candidate.get("content")
            content_parts = content.get("parts") if isinstance(content, dict) else None
            if isinstance(content_parts, list):
                for part in content_parts:
                    if isinstance(part, dict) and isinstance(part.get("text"), str):
                        parts.append(part["text"])
        return "\n".join(parts)
    return ""


def _endpoint_for_model(provider: dict[str, Any], model: str) -> str:
    endpoint = str(
        provider.get("endpoint")
        or "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
    )
    if "{model}" in endpoint:
        return endpoint.format(model=model)
    if endpoint.rstrip("/").endswith("/interactions"):
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent"
    if endpoint.rstrip("/").endswith(":generateContent"):
        return endpoint
    return endpoint.rstrip("/") + "/models/" + model + ":generateContent"


def _grounding_metadata(data: dict[str, Any]) -> Any:
    if data.get("groundingMetadata") or data.get("grounding_metadata"):
        return data.get("groundingMetadata") or data.get("grounding_metadata")
    candidates = data.get("candidates")
    if isinstance(candidates, list) and candidates:
        first = candidates[0]
        if isinstance(first, dict):
            return first.get("groundingMetadata") or first.get("grounding_metadata")
    return None


def search(
    query: str,
    provider: dict[str, Any],
    global_config: dict[str, Any],
    max_pages: int,
    count_override: int | None,
) -> list[dict[str, Any]]:
    del max_pages, count_override
    api_key = provider.get("api_key")
    if not api_key:
        raise SearchError("gemini.api_key is empty")
    configured_models = provider.get("models")
    if isinstance(configured_models, list):
        models = [str(model) for model in configured_models if str(model).strip()]
    else:
        single_model = provider.get("model")
        models = [str(single_model)] if single_model else DEFAULT_SEARCH_MODELS
    if not models:
        raise SearchError("gemini.models is empty")
    headers = {"Content-Type": "application/json"}
    failures: list[dict[str, str]] = []
    for model in models:
        endpoint = _endpoint_for_model(provider, model)
        body: dict[str, Any] = {
            "contents": [{"parts": [{"text": llm_search_prompt(query)}]}],
            "generationConfig": {
                "temperature": float_config(provider.get("temperature"), 0.2),
                "maxOutputTokens": int_config(
                    provider.get("max_output_tokens"), 4096, 1, 65536
                ),
            },
        }
        if provider.get("google_search") is True:
            body["tools"] = [{"googleSearch": {}}]
        try:
            data = request_json(
                "POST",
                endpoint,
                headers=headers,
                params={"key": str(api_key)},
                body=body,
                timeout=timeout(global_config),
            )
        except SearchError as exc:
            failures.append({"model": model, "error": str(exc)})
            continue
        if not isinstance(data, dict):
            failures.append({"model": model, "error": "unexpected response"})
            continue
        grounding = _grounding_metadata(data)
        return [
            normalize_result(
                provider="gemini",
                query=query,
                page=1,
                rank=1,
                title=f"Gemini search-enabled answer ({model})",
                url="",
                snippet=_extract_text(data),
                source="llm_search",
                extra={
                    "model": model,
                    "fallback_failures": failures,
                    "grounding": grounding,
                    "raw_keys": sorted(data.keys()),
                },
            )
        ]
    failure_text = "; ".join(f"{item['model']}: {item['error']}" for item in failures)
    raise SearchError(f"all gemini models failed: {failure_text}")

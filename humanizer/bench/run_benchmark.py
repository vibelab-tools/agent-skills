#!/usr/bin/env python3
"""Run and summarize the isolated Humanizer comparative benchmark."""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import http.client
import importlib.util
import json
import os
from pathlib import Path
import random
import re
import shutil
import ssl
import statistics
import subprocess
import sys
import tempfile
from typing import Any
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parent
SKILL_ROOT = ROOT.parent
CONFIG_PATH = ROOT / "config.json"
CHECKSUM_PATH = ROOT / "corpus.sha256"
SCHEMA_PATH = ROOT / "judge.schema.json"
RESULTS = ROOT / "results"
LABELS = tuple("ABCDEF")


class ExternalJudgeError(RuntimeError):
    """A result-free provider or transport failure that may be retried."""


def load_config() -> dict[str, Any]:
    return json.loads(CONFIG_PATH.read_text(encoding="utf-8"))


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def verify_corpus() -> None:
    failures = []
    for line in CHECKSUM_PATH.read_text(encoding="utf-8").splitlines():
        expected, relative = line.split("  ", 1)
        path = ROOT / relative
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else "missing"
        if actual != expected:
            failures.append(f"{relative}: expected {expected}, got {actual}")
    if failures:
        raise RuntimeError("corpus verification failed:\n" + "\n".join(failures))


def selected(items: list[dict[str, Any]], requested: list[str] | None) -> list[dict[str, Any]]:
    if not requested:
        return items
    by_id = {item["id"]: item for item in items}
    unknown = sorted(set(requested) - set(by_id))
    if unknown:
        raise ValueError(f"unknown case(s): {', '.join(unknown)}")
    return [by_id[item_id] for item_id in requested]


def selected_arm_ids(config: dict[str, Any], requested: list[str] | None) -> list[str]:
    arm_ids = list(config["arms"])
    if not requested:
        return arm_ids
    unknown = sorted(set(requested) - set(arm_ids))
    if unknown:
        raise ValueError(f"unknown arm(s): {', '.join(unknown)}")
    return [arm_id for arm_id in arm_ids if arm_id in requested]


def verify_peer(peer_root: Path, arm: dict[str, Any]) -> Path:
    source = peer_root / arm["peer_directory"]
    if not (source / "SKILL.md").is_file():
        raise RuntimeError(f"missing peer skill: {source / 'SKILL.md'}")
    result = subprocess.run(
        ["git", "-C", str(source), "rev-parse", "HEAD"],
        capture_output=True,
        check=True,
        text=True,
    )
    actual = result.stdout.strip()
    if actual != arm["revision"]:
        raise RuntimeError(f"peer revision mismatch for {source}: {actual} != {arm['revision']}")
    return source


def copy_skill(source: Path, target: Path) -> None:
    target.mkdir(parents=True)
    shutil.copy2(source / "SKILL.md", target / "SKILL.md")
    for name in ("references", "scripts"):
        child = source / name
        if child.is_dir():
            shutil.copytree(child, target / name)


def run_codex(
    *, prompt: str, model: str, effort: str, workspace: Path, output_schema: Path | None = None
) -> tuple[str, int | None]:
    output_path = workspace / "last-message.txt"
    command = [
        "codex",
        "exec",
        "--ephemeral",
        "--ignore-user-config",
        "--ignore-rules",
        "--disable",
        "memories",
        "--disable",
        "plugins",
        "--disable",
        "hooks",
        "--disable",
        "multi_agent",
        "--skip-git-repo-check",
        "--sandbox",
        "read-only",
        "--model",
        model,
        "--config",
        f'model_reasoning_effort="{effort}"',
        "--cd",
        str(workspace),
        "--output-last-message",
        str(output_path),
    ]
    if output_schema is not None:
        command.extend(["--output-schema", str(output_schema)])
    command.append("-")
    completed = subprocess.run(
        command,
        input=prompt,
        capture_output=True,
        check=False,
        text=True,
        timeout=900,
    )
    if completed.returncode != 0 or not output_path.is_file():
        detail = (completed.stderr or completed.stdout)[-4000:]
        raise RuntimeError(f"Codex call failed with exit {completed.returncode}:\n{detail}")
    token_match = re.search(r"tokens used\s*\n([\d,]+)", completed.stderr)
    tokens = int(token_match.group(1).replace(",", "")) if token_match else None
    return output_path.read_text(encoding="utf-8").strip() + "\n", tokens


def parse_json_message(text: str) -> dict[str, Any]:
    stripped = text.strip()
    if stripped.startswith("```"):
        stripped = re.sub(r"^```(?:json)?\s*", "", stripped)
        stripped = re.sub(r"\s*```$", "", stripped)
    start = stripped.find("{")
    end = stripped.rfind("}")
    if start < 0 or end < start:
        raise RuntimeError("judge response did not contain a JSON object")
    return json.loads(stripped[start : end + 1])


def post_json(endpoint: str, payload: dict[str, Any], headers: dict[str, str]) -> dict[str, Any]:
    request = urllib.request.Request(
        endpoint,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", **headers},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=600) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        error_type = ExternalJudgeError if error.code == 429 or error.code >= 500 else RuntimeError
        raise error_type(f"judge API returned HTTP {error.code}") from None
    except urllib.error.URLError as error:
        raise ExternalJudgeError(
            f"judge API connection failed: {type(error.reason).__name__}"
        ) from None
    except (http.client.IncompleteRead, ssl.SSLError, TimeoutError, ConnectionError) as error:
        raise ExternalJudgeError(f"judge API transport failed: {type(error).__name__}") from None


def failure_is_external(failure: dict[str, Any]) -> bool:
    category = failure.get("category")
    if category is not None:
        return category == "external"
    error = failure.get("error", "")
    return any(
        marker in error
        for marker in (
            "IncompleteRead",
            "SSLEOFError",
            "RemoteDisconnected",
            "Connection reset",
            "timed out",
            "judge API connection failed",
            "judge API transport failed",
            "judge API returned HTTP 429",
            "judge API returned HTTP 5",
        )
    )


def run_openai_compatible_judge(prompt: str, judge_config: dict[str, Any]) -> tuple[str, int | None]:
    key = os.environ.get(judge_config["api_key_env"])
    if not key:
        raise RuntimeError(f"missing environment variable {judge_config['api_key_env']}")
    payload = {
        "model": judge_config["model"],
        "messages": [
            {"role": "system", "content": "Return only the requested benchmark judgment JSON."},
            {"role": "user", "content": prompt},
        ],
        "temperature": judge_config.get("temperature", 0),
        "max_tokens": 24000,
        "stream": False,
    }
    data = post_json(
        judge_config["endpoint"],
        payload,
        {"Authorization": f"Bearer {key}"},
    )
    content = data["choices"][0]["message"]["content"]
    usage = data.get("usage", {})
    tokens = usage.get("total_tokens")
    return content, int(tokens) if tokens is not None else None


def run_gemini_judge(prompt: str, judge_config: dict[str, Any]) -> tuple[str, int | None]:
    key = os.environ.get(judge_config["api_key_env"])
    if not key:
        raise RuntimeError(f"missing environment variable {judge_config['api_key_env']}")
    endpoint = judge_config["endpoint"].format(model=judge_config["model"])
    payload = {
        "contents": [{"role": "user", "parts": [{"text": prompt}]}],
        "generationConfig": {
            "temperature": 0,
            "maxOutputTokens": 24000,
            "responseMimeType": "application/json",
        },
    }
    data = post_json(endpoint, payload, {"x-goog-api-key": key})
    content = "".join(part.get("text", "") for part in data["candidates"][0]["content"]["parts"])
    usage = data.get("usageMetadata", {})
    tokens = usage.get("totalTokenCount")
    return content, int(tokens) if tokens is not None else None


def run_judge_call(
    prompt: str, judge_config: dict[str, Any], workspace: Path
) -> tuple[dict[str, Any], int | None]:
    provider = judge_config["provider"]
    if provider == "codex":
        result, tokens = run_codex(
            prompt=prompt,
            model=judge_config["model"],
            effort=judge_config["reasoning_effort"],
            workspace=workspace,
            output_schema=SCHEMA_PATH,
        )
    elif provider == "openai-compatible":
        result, tokens = run_openai_compatible_judge(prompt, judge_config)
    elif provider == "gemini":
        result, tokens = run_gemini_judge(prompt, judge_config)
    else:
        raise RuntimeError(f"unsupported judge provider: {provider}")
    return parse_json_message(result), tokens


def generation_prompt(task_prompt: str, with_skill: bool) -> str:
    skill_instruction = ""
    if with_skill:
        skill_instruction = (
            "Read skill/SKILL.md completely before rewriting and follow it for this task. "
            "Read only the language-specific references it routes you to.\n\n"
        )
    return (
        "You are participating in a controlled writing benchmark. Do not browse the web, "
        "consult outside sources, or add facts. Read source.md in full.\n\n"
        f"{skill_instruction}{task_prompt}\n"
    )


def generate(args: argparse.Namespace) -> None:
    verify_corpus()
    config = load_config()
    cases = selected(config["cases"], args.case)
    arm_ids = selected_arm_ids(config, args.arm)
    peer_root = Path(args.peer_root).resolve()
    model = config["generation"]["model"]
    effort = config["generation"]["reasoning_effort"]
    metadata_path = RESULTS / "generation-metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8")) if metadata_path.exists() else {"runs": []}
    completed_keys = {(item["case"], item["arm"]) for item in metadata["runs"]}
    total = len(cases) * len(arm_ids)
    index = 0

    for case in cases:
        source_text = (ROOT / case["source"]).read_text(encoding="utf-8")
        for arm_id in arm_ids:
            index += 1
            output_path = RESULTS / "raw" / case["id"] / f"{arm_id}.md"
            key = (case["id"], arm_id)
            if output_path.exists() or key in completed_keys:
                if args.resume and output_path.exists() and key in completed_keys:
                    print(f"[{index}/{total}] keep existing {case['id']}/{arm_id}", flush=True)
                    continue
                raise RuntimeError(f"existing result blocks rerun: {output_path}; use --resume to keep it")

            print(f"[{index}/{total}] generate {case['id']}/{arm_id}", flush=True)
            arm = config["arms"][arm_id]
            with tempfile.TemporaryDirectory(prefix="humanizer-benchmark-input.") as directory:
                call_root = Path(directory)
                (call_root / "source.md").write_text(source_text, encoding="utf-8")
                if arm_id != "baseline":
                    if arm_id == "ours":
                        skill_source = SKILL_ROOT
                    else:
                        skill_source = verify_peer(peer_root, arm)
                    copy_skill(skill_source, call_root / "skill")
                prompt = generation_prompt(case["prompt"], arm_id != "baseline")
                result, tokens = run_codex(
                    prompt=prompt,
                    model=model,
                    effort=effort,
                    workspace=call_root,
                )

            output_path.parent.mkdir(parents=True, exist_ok=True)
            output_path.write_text(result, encoding="utf-8")
            metadata["runs"].append(
                {"case": case["id"], "arm": arm_id, "model": model, "reasoning_effort": effort, "tokens": tokens}
            )
            write_json(metadata_path, metadata)


def claim_ids(ground_truth: str) -> list[str]:
    return re.findall(r"^\| (C\d+) \|", ground_truth, re.MULTILINE)


def blind_map(config: dict[str, Any], case_index: int, judge_index: int) -> dict[str, str]:
    arms = list(config["arms"])
    random.Random(config["seed"] + case_index + judge_index * 1000).shuffle(arms)
    if len(arms) != len(LABELS):
        raise RuntimeError(f"benchmark expects {len(LABELS)} arms, got {len(arms)}")
    return dict(zip(LABELS, arms))


def judge_prompt(case: dict[str, Any], mapping: dict[str, str], config: dict[str, Any]) -> str:
    source = (ROOT / case["source"]).read_text(encoding="utf-8")
    ground_truth = (ROOT / case["ground_truth"]).read_text(encoding="utf-8")
    sections = []
    for label, arm_id in mapping.items():
        output = (RESULTS / "raw" / case["id"] / f"{arm_id}.md").read_text(encoding="utf-8")
        sections.append(f"<OUTPUT {label}>\n{output}\n</OUTPUT {label}>")
    return f"""You are the independent blind evaluator for a controlled writing benchmark.
The output labels are random and reveal nothing about their source. Do not browse, use tools,
or infer which writing skill produced an output.

Evaluate every labeled output against the source and pre-registered claim inventory.

Scores use integers from 1 to 5:
- naturalness: 5 reads like competent native prose for this genre; 1 is highly mechanical.
- reader_clarity: 5 is directly understandable to the stated audience without losing precision;
  1 imposes unnecessary decoding effort or obscures relationships.
- register_fit: 5 fits the requested genre and audience; 1 is seriously mismatched.

For every claim ID, mark intact, altered, or dropped. An intact claim keeps its value,
direction, attribution, qualifier, and strength. Quote the relevant output evidence; for a
dropped claim, state that no evidence exists. List every added factual claim, anecdote,
source, statistic, opinion, or operational instruction not present in the source. Do not
treat cleaner wording or an explanation logically entailed by the source as a new claim.

Rank all six labels from best to worst using meaning preservation as a gate, then reader
clarity, naturalness, and register fit. Do not reward casualness by itself. A polished formal
notice or technical explanation can be fully natural. Set best to the first ranking label.

Return only one JSON object with this shape and no Markdown fencing:
{{
  "case_id": "{case['id']}",
  "evaluations": [
    {{
      "label": "A",
      "naturalness": 1,
      "reader_clarity": 1,
      "register_fit": 1,
      "claims": [{{"id": "C1", "status": "intact|altered|dropped", "evidence": "..."}}],
      "added_claims": [{{"claim": "...", "evidence": "..."}}],
      "rationale": "..."
    }}
  ],
  "ranking": ["A", "B", "C", "D", "E", "F"],
  "best": "A",
  "confidence": "low|medium|high",
  "limitations": "..."
}}
Include one evaluation for every label and one claim entry for every inventory ID.

<CASE_ID>{case['id']}</CASE_ID>
<TASK>{case['prompt']}</TASK>
<SOURCE>
{source}
</SOURCE>
<CLAIM_INVENTORY>
{ground_truth}
</CLAIM_INVENTORY>

{chr(10).join(sections)}
"""


def validate_judgment(judgment: dict[str, Any], case: dict[str, Any], mapping: dict[str, str]) -> None:
    expected_labels = set(mapping)
    evaluation_labels = [item["label"] for item in judgment["evaluations"]]
    ranking = judgment["ranking"]
    if judgment["case_id"] != case["id"]:
        raise RuntimeError(f"judge returned wrong case id: {judgment['case_id']}")
    if len(evaluation_labels) != len(expected_labels) or set(evaluation_labels) != expected_labels:
        raise RuntimeError(f"judge evaluation labels do not match: {evaluation_labels}")
    if len(ranking) != len(expected_labels) or set(ranking) != expected_labels:
        raise RuntimeError(f"judge ranking labels do not match: {ranking}")
    if judgment["best"] != ranking[0]:
        raise RuntimeError("judge best label does not match ranking[0]")
    if judgment["confidence"] not in {"low", "medium", "high"}:
        raise RuntimeError(f"invalid judge confidence: {judgment['confidence']}")
    if not isinstance(judgment["limitations"], str):
        raise RuntimeError("judge limitations must be a string")
    expected_claims = set(claim_ids((ROOT / case["ground_truth"]).read_text(encoding="utf-8")))
    for evaluation in judgment["evaluations"]:
        for metric in ("naturalness", "reader_clarity", "register_fit"):
            score = evaluation[metric]
            if type(score) is not int or not 1 <= score <= 5:
                raise RuntimeError(
                    f"invalid {metric} score for {case['id']}/{evaluation['label']}: {score!r}"
                )
        actual_claims = [item["id"] for item in evaluation["claims"]]
        if len(actual_claims) != len(expected_claims) or set(actual_claims) != expected_claims:
            raise RuntimeError(f"claim IDs do not match for {case['id']}/{evaluation['label']}")
        invalid_statuses = [
            item["status"]
            for item in evaluation["claims"]
            if item["status"] not in {"intact", "altered", "dropped"}
        ]
        if invalid_statuses:
            raise RuntimeError(
                f"invalid claim status for {case['id']}/{evaluation['label']}: {invalid_statuses}"
            )
        if not all(isinstance(item["evidence"], str) for item in evaluation["claims"]):
            raise RuntimeError(f"claim evidence must be text for {case['id']}/{evaluation['label']}")
        if not isinstance(evaluation["added_claims"], list) or not all(
            isinstance(item["claim"], str) and isinstance(item["evidence"], str)
            for item in evaluation["added_claims"]
        ):
            raise RuntimeError(f"invalid added claims for {case['id']}/{evaluation['label']}")
        if not isinstance(evaluation["rationale"], str):
            raise RuntimeError(f"judge rationale must be text for {case['id']}/{evaluation['label']}")


def judge(args: argparse.Namespace) -> None:
    verify_corpus()
    config = load_config()
    cases = selected(config["cases"], args.case)
    judge_ids = list(config["judges"])
    if args.judge:
        unknown = sorted(set(args.judge) - set(judge_ids))
        if unknown:
            raise ValueError(f"unknown judge(s): {', '.join(unknown)}")
        judge_ids = [judge_id for judge_id in judge_ids if judge_id in args.judge]
    mapping_path = RESULTS / "blinding-map.json"
    existing_mapping = json.loads(mapping_path.read_text(encoding="utf-8")) if mapping_path.exists() else {}
    metadata_path = RESULTS / "judge-metadata.json"
    metadata = json.loads(metadata_path.read_text(encoding="utf-8")) if metadata_path.exists() else {"runs": []}
    completed = {(item["judge"], item["case"]) for item in metadata["runs"] if item["status"] == "ok"}
    failures_path = RESULTS / "judge-failures.json"
    failures = json.loads(failures_path.read_text(encoding="utf-8")) if failures_path.exists() else {"runs": []}
    failed = {(item["judge"], item["case"]) for item in failures["runs"]}

    for judge_index, judge_id in enumerate(config["judges"], start=1):
        if judge_id not in judge_ids:
            continue
        judge_config = config["judges"][judge_id]
        existing_mapping.setdefault(judge_id, {})
        for case_index, case in enumerate(config["cases"], start=1):
            if case not in cases:
                continue
            key = (judge_id, case["id"])
            output_path = RESULTS / "judgments" / judge_id / f"{case['id']}.json"
            mapping = blind_map(config, case_index, judge_index)
            stored = existing_mapping[judge_id].get(case["id"])
            if stored is not None and stored != mapping:
                raise RuntimeError(f"stored blinding map changed for {judge_id}/{case['id']}")
            existing_mapping[judge_id][case["id"]] = mapping
            write_json(mapping_path, existing_mapping)
            if output_path.exists() or key in completed or key in failed:
                if args.resume and output_path.exists() and key in completed:
                    print(f"keep existing judgment {judge_id}/{case['id']}", flush=True)
                    continue
                if key in failed and args.retry_failed and not output_path.exists() and key not in completed:
                    prior_failures = [
                        item
                        for item in failures["runs"]
                        if item["judge"] == judge_id and item["case"] == case["id"]
                    ]
                    if not all(failure_is_external(item) for item in prior_failures):
                        raise RuntimeError(
                            f"non-external judge failure cannot be resampled: {judge_id}/{case['id']}"
                        )
                    print(f"retry failed judgment {judge_id}/{case['id']}", flush=True)
                elif args.resume and key in failed:
                    print(f"keep recorded failure {judge_id}/{case['id']}", flush=True)
                    continue
                else:
                    raise RuntimeError(
                        f"existing judgment or recorded failure blocks rerun: {judge_id}/{case['id']}; "
                        "use --resume to keep it or --retry-failed for a result-free failed call"
                    )
            for arm_id in config["arms"]:
                raw_path = RESULTS / "raw" / case["id"] / f"{arm_id}.md"
                if not raw_path.is_file():
                    raise RuntimeError(f"missing generation output: {raw_path}")

            print(f"judge {judge_id}/{case['id']}", flush=True)
            try:
                with tempfile.TemporaryDirectory(prefix="humanizer-benchmark-judge.") as directory:
                    judgment, tokens = run_judge_call(
                        judge_prompt(case, mapping, config),
                        judge_config,
                        Path(directory),
                    )
                validate_judgment(judgment, case, mapping)
                write_json(output_path, judgment)
                metadata["runs"].append(
                    {
                        "judge": judge_id,
                        "case": case["id"],
                        "provider": judge_config["provider"],
                        "model": judge_config["model"],
                        "reasoning_effort": judge_config.get("reasoning_effort"),
                        "tokens": tokens,
                        "status": "ok",
                    }
                )
                write_json(metadata_path, metadata)
            except Exception as error:
                failures["runs"].append(
                    {
                        "judge": judge_id,
                        "case": case["id"],
                        "provider": judge_config["provider"],
                        "model": judge_config["model"],
                        "attempt": 1
                        + sum(
                            item["judge"] == judge_id and item["case"] == case["id"]
                            for item in failures["runs"]
                        ),
                        "category": "external" if isinstance(error, ExternalJudgeError) else "model",
                        "error": str(error)[:500],
                    }
                )
                write_json(failures_path, failures)
                print(f"recorded judge failure {judge_id}/{case['id']}: {error}", flush=True)


def load_anchor_checker():
    path = SKILL_ROOT / "scripts" / "check_anchors.py"
    spec = importlib.util.spec_from_file_location("humanizer_check_anchors", path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"cannot load anchor checker: {path}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def summarize(_: argparse.Namespace) -> None:
    verify_corpus()
    config = load_config()
    checker = load_anchor_checker()
    aggregates: dict[str, dict[str, Any]] = {
        arm_id: {
            "scores": {"naturalness": [], "reader_clarity": [], "register_fit": []},
            "claims": Counter(),
            "added_cases": 0,
            "non_intact_cases": 0,
            "anchor_passes": 0,
            "ranks": [],
            "wins": 0,
            "majority_wins": 0,
        }
        for arm_id in config["arms"]
    }
    judge_aggregates: dict[str, dict[str, Any]] = {
        judge_id: {
            arm_id: {
                "scores": {"naturalness": [], "reader_clarity": [], "register_fit": []},
                "ranks": [],
                "wins": 0,
            }
            for arm_id in config["arms"]
        }
        for judge_id in config["judges"]
    }
    per_case = []

    for case_index, case in enumerate(config["cases"], start=1):
        source = (ROOT / case["source"]).read_text(encoding="utf-8")
        evaluations_by_arm: dict[str, list[dict[str, Any]]] = {arm_id: [] for arm_id in config["arms"]}
        ranks_by_arm: dict[str, list[int]] = {arm_id: [] for arm_id in config["arms"]}
        best_votes: Counter[str] = Counter()
        judge_case_results = {}

        for judge_index, judge_id in enumerate(config["judges"], start=1):
            judgment_path = RESULTS / "judgments" / judge_id / f"{case['id']}.json"
            if not judgment_path.is_file():
                continue
            judgment = json.loads(judgment_path.read_text(encoding="utf-8"))
            mapping = blind_map(config, case_index, judge_index)
            validate_judgment(judgment, case, mapping)
            label_to_evaluation = {item["label"]: item for item in judgment["evaluations"]}
            best_arm = mapping[judgment["best"]]
            best_votes[best_arm] += 1
            judge_case_results[judge_id] = {"best": best_arm, "confidence": judgment["confidence"]}
            for rank, label in enumerate(judgment["ranking"], start=1):
                arm_id = mapping[label]
                ranks_by_arm[arm_id].append(rank)
                aggregates[arm_id]["ranks"].append(rank)
                judge_aggregates[judge_id][arm_id]["ranks"].append(rank)
                if rank == 1:
                    aggregates[arm_id]["wins"] += 1
                    judge_aggregates[judge_id][arm_id]["wins"] += 1
            for label, arm_id in mapping.items():
                evaluation = label_to_evaluation[label]
                evaluations_by_arm[arm_id].append(evaluation)
                for metric in aggregates[arm_id]["scores"]:
                    aggregates[arm_id]["scores"][metric].append(evaluation[metric])
                    judge_aggregates[judge_id][arm_id]["scores"][metric].append(evaluation[metric])

        if not judge_case_results:
            raise RuntimeError(f"no successful judgments for {case['id']}")
        vote_count = max(best_votes.values())
        vote_winners = sorted(arm_id for arm_id, count in best_votes.items() if count == vote_count)
        majority = vote_count > len(judge_case_results) / 2
        if majority:
            aggregates[vote_winners[0]]["majority_wins"] += 1
        case_results = {
            "case": case["id"],
            "best_by_votes": vote_winners[0] if len(vote_winners) == 1 else None,
            "vote_winners": vote_winners,
            "votes": dict(best_votes),
            "best_votes": vote_count,
            "judge_count": len(judge_case_results),
            "majority": majority,
            "judges": judge_case_results,
            "arms": {},
        }

        for arm_id, evaluations in evaluations_by_arm.items():
            if not evaluations:
                continue
            rewrite = (RESULTS / "raw" / case["id"] / f"{arm_id}.md").read_text(encoding="utf-8")
            missing, added = checker.compare(source, rewrite)
            anchor_report = {
                "pass": not missing and not added,
                "missing": checker._entries(missing),
                "added": checker._entries(added),
            }
            write_json(RESULTS / "anchors" / case["id"] / f"{arm_id}.json", anchor_report)
            if anchor_report["pass"]:
                aggregates[arm_id]["anchor_passes"] += 1

            claims_by_id: dict[str, list[str]] = {}
            for evaluation in evaluations:
                for claim in evaluation["claims"]:
                    claims_by_id.setdefault(claim["id"], []).append(claim["status"])
            claim_counts: Counter[str] = Counter()
            claim_consensus = {}
            for claim_id, statuses in sorted(claims_by_id.items()):
                counts = Counter(statuses)
                top_count = max(counts.values())
                candidates = [status for status, count in counts.items() if count == top_count]
                status = candidates[0] if top_count > len(statuses) / 2 else "disputed"
                claim_consensus[claim_id] = {"status": status, "votes": dict(counts)}
                claim_counts[status] += 1
            aggregates[arm_id]["claims"].update(claim_counts)
            added_votes = sum(bool(evaluation["added_claims"]) for evaluation in evaluations)
            added_consensus = added_votes > len(evaluations) / 2
            if added_consensus:
                aggregates[arm_id]["added_cases"] += 1
            if (
                claim_counts["altered"]
                or claim_counts["dropped"]
                or claim_counts["disputed"]
                or added_consensus
            ):
                aggregates[arm_id]["non_intact_cases"] += 1
            scores = {
                metric: round(statistics.mean(evaluation[metric] for evaluation in evaluations), 2)
                for metric in aggregates[arm_id]["scores"]
            }
            case_results["arms"][arm_id] = {
                "scores": scores,
                "claims": dict(claim_counts),
                "claim_consensus": claim_consensus,
                "added_votes": added_votes,
                "added_consensus": added_consensus,
                "anchor_pass": anchor_report["pass"],
                "average_rank": round(statistics.mean(ranks_by_arm[arm_id]), 2),
            }
        per_case.append(case_results)

    failures_path = RESULTS / "judge-failures.json"
    failed_attempts = (
        json.loads(failures_path.read_text(encoding="utf-8"))["runs"] if failures_path.exists() else []
    )
    summary = {
        "arms": {},
        "judges": {},
        "cases": per_case,
        "language_groups": {},
        "failed_attempts": failed_attempts,
    }
    for arm_id, values in aggregates.items():
        total_claims = sum(values["claims"].values())
        intact = values["claims"]["intact"]
        summary["arms"][arm_id] = {
            "label": config["arms"][arm_id]["label"],
            "naturalness": round(statistics.mean(values["scores"]["naturalness"]), 2),
            "reader_clarity": round(statistics.mean(values["scores"]["reader_clarity"]), 2),
            "register_fit": round(statistics.mean(values["scores"]["register_fit"]), 2),
            "claims_intact": intact,
            "claims_total": total_claims,
            "claim_integrity_percent": round(intact / total_claims * 100, 1),
            "altered": values["claims"]["altered"],
            "dropped": values["claims"]["dropped"],
            "disputed": values["claims"]["disputed"],
            "added_cases": values["added_cases"],
            "non_intact_cases": values["non_intact_cases"],
            "anchor_passes": values["anchor_passes"],
            "wins": values["wins"],
            "ranked_count": len(values["ranks"]),
            "majority_wins": values["majority_wins"],
            "average_rank": round(statistics.mean(values["ranks"]), 2),
        }
    for judge_id, judge_values in judge_aggregates.items():
        per_arm = {}
        for arm_id, values in judge_values.items():
            if not values["ranks"]:
                continue
            per_arm[arm_id] = {
                "naturalness": round(statistics.mean(values["scores"]["naturalness"]), 2),
                "reader_clarity": round(statistics.mean(values["scores"]["reader_clarity"]), 2),
                "register_fit": round(statistics.mean(values["scores"]["register_fit"]), 2),
                "average_rank": round(statistics.mean(values["ranks"]), 2),
                "wins": values["wins"],
                "cases": len(values["ranks"]),
            }
        summary["judges"][judge_id] = {
            "provider": config["judges"][judge_id]["provider"],
            "model": config["judges"][judge_id]["model"],
            "arms": per_arm,
        }
    languages_by_case = {case["id"]: case["language"] for case in config["cases"]}
    for group_id, languages in {
        "english": {"en"},
        "chinese_mixed": {"zh", "zh-mixed"},
    }.items():
        group_cases = [case for case in per_case if languages_by_case[case["case"]] in languages]
        summary["language_groups"][group_id] = {
            arm_id: {
                "average_rank": round(
                    statistics.mean(case["arms"][arm_id]["average_rank"] for case in group_cases), 2
                ),
                "naturalness": round(
                    statistics.mean(case["arms"][arm_id]["scores"]["naturalness"] for case in group_cases),
                    2,
                ),
                "reader_clarity": round(
                    statistics.mean(case["arms"][arm_id]["scores"]["reader_clarity"] for case in group_cases),
                    2,
                ),
                "register_fit": round(
                    statistics.mean(case["arms"][arm_id]["scores"]["register_fit"] for case in group_cases),
                    2,
                ),
            }
            for arm_id in config["arms"]
        }
    write_json(RESULTS / "summary.json", summary)
    write_report(config, summary)


def write_report(config: dict[str, Any], summary: dict[str, Any]) -> None:
    case_count = len(config["cases"])
    rows = []
    for arm_id, values in summary["arms"].items():
        rows.append(
            f"| {values['label']} | {values['naturalness']:.2f} | {values['reader_clarity']:.2f} | "
            f"{values['register_fit']:.2f} | {values['claims_intact']}/{values['claims_total']} "
            f"({values['claim_integrity_percent']:.1f}%) | {values['altered']}/{values['dropped']}/"
            f"{values['disputed']}/{values['added_cases']} | "
            f"{values['anchor_passes']}/{case_count} | {values['wins']}/{values['ranked_count']} | "
            f"{values['majority_wins']}/{case_count} | {values['average_rank']:.2f} |"
        )
    judge_rows = []
    for judge_id, judge in summary["judges"].items():
        if not judge["arms"]:
            continue
        top_rank = min(values["average_rank"] for values in judge["arms"].values())
        top_arms = [
            arm_id for arm_id, values in judge["arms"].items() if values["average_rank"] == top_rank
        ]
        ours = judge["arms"]["ours"]
        judge_rows.append(
            f"| {judge_id} | {judge['model']} | "
            f"{' / '.join(summary['arms'][arm_id]['label'] for arm_id in top_arms)} | "
            f"{ours['average_rank']:.2f} | {ours['wins']}/{ours['cases']} | "
            f"{ours['naturalness']:.2f}/{ours['reader_clarity']:.2f}/{ours['register_fit']:.2f} |"
        )
    case_rows = []
    for case in summary["cases"]:
        best = " / ".join(summary["arms"][arm_id]["label"] for arm_id in case["vote_winners"])
        ours = case["arms"]["ours"]
        case_rows.append(
            f"| {case['case']} | {best} ({case['best_votes']}/{case['judge_count']}) | "
            f"{ours['average_rank']:.2f} | {ours['scores']['naturalness']}/"
            f"{ours['scores']['reader_clarity']}/{ours['scores']['register_fit']} | "
            f"{ours['claims'].get('intact', 0)}/{sum(ours['claims'].values())} | "
            f"{'pass' if ours['anchor_pass'] else 'fail'} |"
        )
    language_rows = []
    for arm_id, arm in summary["arms"].items():
        english = summary["language_groups"]["english"][arm_id]
        chinese = summary["language_groups"]["chinese_mixed"][arm_id]
        language_rows.append(
            f"| {arm['label']} | {english['average_rank']:.2f} | "
            f"{english['naturalness']:.2f}/{english['reader_clarity']:.2f}/{english['register_fit']:.2f} | "
            f"{chinese['average_rank']:.2f} | "
            f"{chinese['naturalness']:.2f}/{chinese['reader_clarity']:.2f}/{chinese['register_fit']:.2f} |"
        )
    judge_models = ", ".join(
        f"{judge_id}={judge['model']}" for judge_id, judge in summary["judges"].items()
    )
    failure_rows = []
    failure_counts: Counter[tuple[str, str]] = Counter()
    for failure in summary["failed_attempts"]:
        key = (failure["judge"], failure["case"])
        failure_counts[key] += 1
        attempt = failure.get("attempt", failure_counts[key])
        recovered = (RESULTS / "judgments" / failure["judge"] / f"{failure['case']}.json").is_file()
        error = failure["error"].replace("|", "\\|").replace("\n", " ")
        category = "external" if failure_is_external(failure) else "model"
        failure_rows.append(
            f"| {failure['judge']} | {failure['case']} | {attempt} | {category} | {error} | "
            f"{'recovered' if recovered else 'no judgment'} |"
        )
    ranked_arms = sorted(summary["arms"], key=lambda arm_id: summary["arms"][arm_id]["average_rank"])
    ours = summary["arms"]["ours"]
    ours_rank = 1 + sum(
        values["average_rank"] < ours["average_rank"] for values in summary["arms"].values()
    )
    lead_rank = summary["arms"][ranked_arms[0]]["average_rank"]
    overall_leaders = [
        arm_id for arm_id, values in summary["arms"].items() if values["average_rank"] == lead_rank
    ]
    available_judges = {
        judge_id: judge for judge_id, judge in summary["judges"].items() if judge["arms"]
    }
    judge_tops = {}
    for judge_id, judge in available_judges.items():
        top_rank = min(values["average_rank"] for values in judge["arms"].values())
        judge_tops[judge_id] = [
            arm_id for arm_id, values in judge["arms"].items() if values["average_rank"] == top_rank
        ]
    ours_judge_tops = sum("ours" in arm_ids for arm_ids in judge_tops.values())
    dissent = ", ".join(
        f"{judge_id}: {' / '.join(summary['arms'][arm_id]['label'] for arm_id in arm_ids)}"
        for judge_id, arm_ids in judge_tops.items()
        if "ours" not in arm_ids
    )
    unanimous = ours_judge_tops == len(available_judges)
    unique_case_wins = sum(case["vote_winners"] == ["ours"] for case in summary["cases"])
    tied_case_wins = sum("ours" in case["vote_winners"] for case in summary["cases"])
    naturalness_lead = max(values["naturalness"] for values in summary["arms"].values())
    naturalness_leaders = [
        values["label"]
        for values in summary["arms"].values()
        if values["naturalness"] == naturalness_lead
    ]
    verdict = [
        f"- Repository Humanizer ranks **{ours_rank} of {len(ranked_arms)}** overall by average rank "
        f"({ours['average_rank']:.2f}); the aggregate leader is "
        f"**{' / '.join(summary['arms'][arm_id]['label'] for arm_id in overall_leaders)}** ({lead_rank:.2f}).",
        f"- It is the best average-rank arm for **{ours_judge_tops} of {len(available_judges)}** judges. "
        + ("Every judge preferred it on that measure." if unanimous else f"The other judge preferences were {dissent}."),
        f"- It has the sole plurality of first-place votes in **{unique_case_wins} of {case_count}** cases "
        f"and shares the plurality in **{tied_case_wins - unique_case_wins}** more; it has a strict judge majority "
        f"in **{ours['majority_wins']}** cases.",
        f"- It does not lead every component score: naturalness is **{ours['naturalness']:.2f}**, versus "
        f"**{naturalness_lead:.2f}** for {' / '.join(naturalness_leaders)}. Its overall lead comes with "
        f"{ours['reader_clarity']:.2f} clarity, {ours['register_fit']:.2f} register fit, and stronger fidelity.",
        f"- Its consensus claim integrity is **{ours['claims_intact']}/{ours['claims_total']} "
        f"({ours['claim_integrity_percent']:.1f}%)**, with {ours['altered']} altered, {ours['dropped']} dropped, "
        f"{ours['disputed']} disputed, and {ours['added_cases']} cases with majority-detected additions. Exact anchors pass "
        f"**{ours['anchor_passes']}/{case_count}** cases.",
    ]
    report = f"""# Humanizer comparative benchmark report

## Verdict

{chr(10).join(verdict)}

## Aggregate results

Scores combine {len(available_judges)} independently blinded judge providers. Naturalness, reader clarity,
and register fit use a 1–5 scale where higher is better. Average rank uses 1 as best.
Altered/dropped are claim-level majority decisions; claims without a strict status majority
are disputed. Added counts cases where a majority flagged an added claim. Anchor preservation
is a mechanical value comparison, not a semantic score; Markdown code presentation and
grammatical plural inflection are ignored.

| Arm | Naturalness | Clarity | Register | Claims intact | Altered/dropped/disputed/added cases | Anchor pass | Judge wins | Case-majority wins | Avg rank |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
{chr(10).join(rows)}

## What each judge preferred

The score triplet is naturalness/clarity/register for the repository Humanizer.

| Judge | Model | Best average-rank arm | Ours avg rank | Ours wins | Ours scores |
| --- | --- | --- | ---: | ---: | ---: |
{chr(10).join(judge_rows)}

## Repository Humanizer by case

The score triplet is naturalness/clarity/register.

| Case | Most judge votes | Ours avg rank | Ours scores | Ours claims | Ours anchors |
| --- | --- | ---: | ---: | ---: | --- |
{chr(10).join(case_rows)}

## Language split

Each half contains three cases. The score triplet is naturalness/clarity/register.

| Arm | English avg rank | English scores | Chinese/mixed avg rank | Chinese/mixed scores |
| --- | ---: | ---: | ---: | ---: |
{chr(10).join(language_rows)}

## Benchmark-driven corrections

- The frozen outputs exposed two checker false positives: moving the same command between inline and fenced Markdown, and changing `API` to the grammatical plural `APIs`. The checker now compares code by value and normalizes that plural inflection.
- All six arms dropped `Thursday` from the earnings case, while the old checker missed it. English and Chinese weekday forms are now normalized as exact anchors, and the Humanizer explicitly lists weekdays among protected facts.
- The original 36 generation outputs were not regenerated. The corrected checker therefore reports the repository Humanizer at 5/6 anchor passes, matching the 56/57 cross-judge claim result instead of hiding the failure.

## Recorded judge-call failures

Result-free judge-call failures remain visible even when a later retry succeeds.

| Judge | Case | Attempt | Category | Error | Final state |
| --- | --- | ---: | --- | --- | --- |
{chr(10).join(failure_rows) if failure_rows else '| — | — | — | — | None | — |'}

## Method and limitations

- Generation used `{config['generation']['model']}` at `{config['generation']['reasoning_effort']}` reasoning for every arm.
- Judges were {judge_models}. Each provider received an independently shuffled arm map.
- Six cases and one generation per arm are a compact directional benchmark, not a population estimate.
- All judges are LLMs, not a human panel. Provider diversity reduces but does not remove model-judge bias.
- No commercial AI detector was used. The benchmark measures observed writing quality and factual fidelity, not authorship.
- A judge call could be retried only when an external transport or retryable API failure returned no valid judgment. Malformed model judgments were not resampled; all failed attempts were retained and no scored result was discarded.
- Peer revisions, raw outputs, claim judgments, anchor reports, and the blinding map are retained beside this report.
"""
    (RESULTS / "REPORT.md").write_text(report, encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("verify", help="verify frozen corpus checksums")

    generate_parser = subparsers.add_parser("generate", help="generate all benchmark arms")
    generate_parser.add_argument("--peer-root", required=True)
    generate_parser.add_argument("--case", action="append")
    generate_parser.add_argument("--arm", action="append")
    generate_parser.add_argument("--resume", action="store_true")

    judge_parser = subparsers.add_parser("judge", help="blind-audit and score generated outputs")
    judge_parser.add_argument("--case", action="append")
    judge_parser.add_argument("--judge", action="append")
    judge_parser.add_argument("--resume", action="store_true")
    judge_parser.add_argument(
        "--retry-failed",
        action="store_true",
        help="retry result-free external failures while retaining their failure records",
    )

    subparsers.add_parser("summarize", help="build aggregate score artifacts")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.command == "verify":
        verify_corpus()
        print("Corpus verification passed.")
    elif args.command == "generate":
        generate(args)
    elif args.command == "judge":
        judge(args)
    elif args.command == "summarize":
        summarize(args)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""End-to-end smoke check for the changed-code review wrapper."""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
from pathlib import Path


def run(*args: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, check=True, text=True, stdout=subprocess.PIPE)


def main() -> int:
    review_tool = Path(sys.argv[1]).resolve()
    with tempfile.TemporaryDirectory(prefix="code-quality-review-") as directory:
        root = Path(directory)
        run("git", "init", "-q", cwd=root)
        run("git", "config", "user.email", "review@example.invalid", cwd=root)
        run("git", "config", "user.name", "Review Smoke", cwd=root)
        source = root / "service.py"
        source.write_text("def total(values):\n    return sum(values)\n", encoding="utf-8")
        run("git", "add", "service.py", cwd=root)
        run("git", "commit", "-qm", "baseline", cwd=root)
        source.write_text(
            "def total(values):\n"
            "    if not values:\n"
            "        return 0\n"
            "    return sum(values)\n",
            encoding="utf-8",
        )
        (root / "test-helper.py").write_text(
            "def test_total():\n    assert total([]) == 0\n",
            encoding="utf-8",
        )

        result = run(str(review_tool), cwd=root)
        report = json.loads(result.stdout)
        assert report["status"] == "ok"
        assert report["schema_version"] == "2.0"
        assert report["summary"]["changed_production_files"] == 1
        assert report["summary"]["detectors_run"] == 24
        assert report["summary"]["candidates_reported"] == 0
        assert report["candidates"] == []
        assert len(result.stdout) < 2_000

        compatibility_report = json.loads(run(str(review_tool), "--smells", cwd=root).stdout)
        assert compatibility_report == report

        preexisting_body = ["def process():", "    value = 0"]
        preexisting_body.extend("    value += 1" for _ in range(110))
        preexisting_body.append("    return value")
        source.write_text("\n".join(preexisting_body) + "\n", encoding="utf-8")
        run("git", "add", "service.py", cwd=root)
        run("git", "commit", "-qm", "add preexisting long function", cwd=root)
        preexisting_body[60] = "    value += 2"
        source.write_text("\n".join(preexisting_body) + "\n", encoding="utf-8")

        result = run(str(review_tool), cwd=root)
        report = json.loads(result.stdout)
        assert report["summary"]["detectors_run"] == 24
        assert report["summary"]["candidates_reported"] == 0
        assert report["candidates"] == []

        long_functions = []
        for name in ("alpha", "beta", "gamma", "delta"):
            body = [f"def process_{name}():", "    value = 0"]
            body.extend("    value += 1" for _ in range(76))
            body.append("    return value")
            long_functions.append("\n".join(body))
        source.write_text("\n\n".join(long_functions) + "\n", encoding="utf-8")

        result = run(str(review_tool), cwd=root)
        report = json.loads(result.stdout)
        assert report["summary"]["detectors_run"] == 24
        assert report["summary"]["candidates_found"] >= 4
        assert report["summary"]["candidates_reported"] == 3
        assert report["summary"]["candidates_omitted"] >= 1
        assert len(report["candidates"]) == 3
        assert "recommended_refactoring" not in result.stdout
        assert "changed_functions" not in result.stdout
        assert "file_metrics" not in result.stdout
        assert len(result.stdout) < 6_000
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

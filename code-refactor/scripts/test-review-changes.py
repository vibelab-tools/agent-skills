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

        result = run(str(review_tool), cwd=root)
        report = json.loads(result.stdout)
        assert report["status"] == "ok"
        assert report["summary"]["changed_production_files"] == 1
        assert report["summary"]["changed_functions"] == 1
        assert report["files"][0]["changed_functions"][0]["name"] == "total"
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

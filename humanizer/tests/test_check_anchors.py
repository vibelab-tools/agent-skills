import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "check_anchors.py"


class AnchorCheckerCliTests(unittest.TestCase):
    def run_check(self, source: str, rewrite: str, *args: str):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source_path = root / "source.md"
            rewrite_path = root / "rewrite.md"
            source_path.write_text(source, encoding="utf-8")
            rewrite_path.write_text(rewrite, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(SCRIPT), str(source_path), str(rewrite_path), *args],
                capture_output=True,
                check=False,
                text=True,
            )

    def test_english_structure_rewrite_keeps_exact_anchors(self):
        source = (
            "On 2026-08-28, API latency fell from 900ms to 40ms. "
            "See [the report](https://example.com/report) and run `make validate`."
        )
        rewrite = (
            "API latency was 40ms on 2026-08-28, down from 900ms. "
            "Run `make validate`; details are in [the report](https://example.com/report)."
        )
        result = self.run_check(source, rewrite)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Anchor check passed", result.stdout)

    def test_chinese_rewrite_keeps_terms_path_and_quote(self):
        source = "请把 35 个文件写入 `dist/manifest.json`。负责人说：“周五前完成。”"
        rewrite = "负责人要求在周五前完成：把 35 个文件写入 `dist/manifest.json`。他说：“周五前完成。”"
        result = self.run_check(source, rewrite)
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_mixed_language_rewrite_keeps_acronyms_version_and_code(self):
        source = "团队将 Kubernetes API 升级到 v1.31.2，然后执行 `client dry-run`。"
        rewrite = "升级到 v1.31.2 后，团队会通过 Kubernetes API 执行 `client dry-run`。"
        result = self.run_check(source, rewrite, "--json")
        report = json.loads(result.stdout)
        self.assertTrue(report["pass"])
        self.assertEqual(report["missing"], [])
        self.assertEqual(report["added"], [])

    def test_code_may_move_between_inline_and_fenced_form(self):
        source = "Run `make validate` before release."
        rewrite = "Run this before release:\n```sh\nmake validate\n```"
        result = self.run_check(source, rewrite, "--json")
        report = json.loads(result.stdout)
        self.assertTrue(report["pass"])

    def test_acronym_plural_inflection_preserves_anchor(self):
        result = self.run_check("This API is stable.", "These APIs are stable.", "--json")
        report = json.loads(result.stdout)
        self.assertTrue(report["pass"])

    def test_missing_weekday_fails(self):
        result = self.run_check("Results were announced Thursday.", "Results were announced.", "--json")
        report = json.loads(result.stdout)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["missing"], [{"kind": "weekday", "value": "4", "count": 1}])

    def test_chinese_weekday_variants_share_an_anchor(self):
        result = self.run_check("星期四发布。", "改为周四发布。", "--json")
        report = json.loads(result.stdout)
        self.assertTrue(report["pass"])

    def test_missing_and_added_numbers_fail(self):
        result = self.run_check("The limit is 35.", "The limit is 50.", "--json")
        report = json.loads(result.stdout)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["missing"][0]["value"], "35")
        self.assertEqual(report["added"][0]["value"], "50")

    def test_duplicate_anchor_count_is_preserved(self):
        result = self.run_check("API then API.", "API.", "--json")
        report = json.loads(result.stdout)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(report["missing"], [{"kind": "acronym", "value": "API", "count": 1}])

    def test_missing_code_and_link_target_fail(self):
        source = "Read [the guide](docs/setup.md), then run:\n```sh\nmake validate\n```"
        result = self.run_check(source, "Read the guide, then validate the project.", "--json")
        report = json.loads(result.stdout)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(
            {(entry["kind"], entry["value"]) for entry in report["missing"]},
            {("code", "make validate"), ("link target", "docs/setup.md")},
        )


if __name__ == "__main__":
    unittest.main()

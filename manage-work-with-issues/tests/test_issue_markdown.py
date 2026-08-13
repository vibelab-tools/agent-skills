import importlib.util
import io
import pathlib
import subprocess
import unittest
from unittest import mock


SCRIPT_PATH = pathlib.Path(__file__).parents[1] / "scripts" / "issue_markdown.py"
SPEC = importlib.util.spec_from_file_location("issue_markdown", SCRIPT_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class IssueMarkdownTest(unittest.TestCase):
    def test_multiline_markdown_passes_through_unchanged(self):
        markdown = "Summary\n\n- first\n- second\n"
        MODULE.validate_markdown(markdown)

        with mock.patch.object(MODULE.sys, "stdin", io.StringIO(markdown)):
            with mock.patch.object(
                MODULE.sys, "stdout", new_callable=io.StringIO
            ) as output:
                self.assertEqual(MODULE.main([]), 0)
                self.assertEqual(output.getvalue(), markdown)

    def test_literal_escaped_newlines_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "literal"):
            MODULE.validate_markdown(r"Summary\n\n- first\n- second")

    def test_literal_escaped_newlines_can_be_intentional_content(self):
        markdown = r"The two characters \n are not a line break."
        MODULE.validate_markdown(markdown, allow_literal_newlines=True)
        MODULE.verify_stored_markdown(
            markdown, markdown, allow_literal_newlines=True
        )

    def test_empty_markdown_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "must not be empty"):
            MODULE.validate_markdown(" \n\t")

    @mock.patch.object(MODULE, "run_cli")
    def test_gitlab_create_posts_stdin_and_reads_back(self, run_cli):
        markdown = "## Goal\n\n- done\n"
        run_cli.side_effect = [
            '{"iid":136}',
            '{"description":"## Goal\\n\\n- done\\n"}',
        ]

        output = MODULE.publish_gitlab(
            "create",
            "group/project",
            "gitlab.example.com",
            None,
            "Fix formatting",
            markdown,
        )

        self.assertEqual(output, '{"iid":136}')
        self.assertEqual(run_cli.call_args_list[0].args[1], markdown)
        self.assertIn("description=@-", run_cli.call_args_list[0].args[0])
        self.assertNotIn(r"\n", " ".join(run_cli.call_args_list[0].args[0]))
        self.assertEqual(run_cli.call_args_list[1].args[1:], ())

    @mock.patch.object(MODULE, "run_cli")
    def test_github_comment_posts_stdin_and_reads_back(self, run_cli):
        markdown = "Summary\n\n- done\n"
        comment_url = (
            "https://github.com/owner/repo/issues/6#issuecomment-5274960378\n"
        )
        run_cli.side_effect = [comment_url, '{"body":"Summary\\n\\n- done\\n"}']

        output = MODULE.publish_github(
            "comment", "owner/repo", "6", None, markdown
        )

        self.assertEqual(output, comment_url)
        self.assertEqual(run_cli.call_args_list[0].args[1], markdown)
        self.assertEqual(run_cli.call_args_list[0].args[0][-2:], ["--body-file", "-"])
        self.assertEqual(
            run_cli.call_args_list[1].args[0][-1],
            "repos/owner/repo/issues/comments/5274960378",
        )

    @mock.patch.object(MODULE, "run_cli")
    def test_provider_mismatch_is_rejected(self, run_cli):
        run_cli.side_effect = [
            subprocess.CompletedProcess([], 0),
            '{"description":"different"}',
        ]
        with self.assertRaisesRegex(ValueError, "differs"):
            MODULE.publish_gitlab(
                "edit",
                "group/project",
                "gitlab.example.com",
                "136",
                None,
                "expected",
            )


if __name__ == "__main__":
    unittest.main()

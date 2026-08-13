import importlib.util
import io
import pathlib
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

    def test_empty_markdown_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "must not be empty"):
            MODULE.validate_markdown(" \n\t")


if __name__ == "__main__":
    unittest.main()

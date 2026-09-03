import importlib.util
import pathlib
import unittest
from unittest import mock


SCRIPT_PATH = (
    pathlib.Path(__file__).parents[1] / "scripts" / "gitlab_web_url.py"
)
SPEC = importlib.util.spec_from_file_location("gitlab_web_url", SCRIPT_PATH)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class GitLabWebUrlTest(unittest.TestCase):
    def test_legacy_commit_url_before_gitlab_12(self):
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project.git",
                "commit",
                "abcdef123456",
                "9.5.9",
            ),
            "https://gitlab.example.com/group/project/commit/abcdef123456",
        )

    def test_scoped_commit_url_from_gitlab_12(self):
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project",
                "commit",
                "abcdef123456",
                "12.0.0-ee",
            ),
            "https://gitlab.example.com/group/project/-/commit/abcdef123456",
        )

    def test_issue_routes_follow_the_same_version_boundary(self):
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project",
                "issue",
                "42",
                "11.11.8",
            ),
            "https://gitlab.example.com/group/project/issues/42",
        )
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project",
                "issue",
                "42",
                "18.1.1",
            ),
            "https://gitlab.example.com/group/project/-/issues/42",
        )

    def test_unknown_version_falls_back_to_legacy_route(self):
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project",
                "commit",
                "abcdef123456",
                None,
            ),
            "https://gitlab.example.com/group/project/commit/abcdef123456",
        )

    def test_provider_web_url_takes_precedence(self):
        self.assertEqual(
            MODULE.build_web_url(
                "https://gitlab.example.com/group/project",
                "commit",
                "abcdef123456",
                "9.5.9",
                "https://canonical.example/commit/abcdef123456",
            ),
            "https://canonical.example/commit/abcdef123456",
        )

    def test_ssh_remote_uses_configured_web_scheme(self):
        self.assertEqual(
            MODULE.build_web_url(
                "git@gitlab.example.com:group/project.git",
                "commit",
                "abcdef123456",
                "9.5.9",
                web_scheme="https",
            ),
            "https://gitlab.example.com/group/project/commit/abcdef123456",
        )

    def test_provider_url_scheme_can_follow_glab_configuration(self):
        self.assertEqual(
            MODULE.build_web_url(
                "git@gitlab.example.com:group/project.git",
                "issue",
                "42",
                "9.5.9",
                provider_web_url="http://gitlab.example.com/group/project/issues/42",
                web_scheme="https",
            ),
            "https://gitlab.example.com/group/project/issues/42",
        )

    def test_https_provider_url_is_not_downgraded(self):
        self.assertEqual(
            MODULE.build_web_url(
                "git@gitlab.example.com:group/project.git",
                "issue",
                "42",
                "9.5.9",
                provider_web_url="https://gitlab.example.com/group/project/issues/42",
                web_scheme=None,
            ),
            "https://gitlab.example.com/group/project/issues/42",
        )

    @mock.patch.object(MODULE.subprocess, "run", side_effect=FileNotFoundError)
    def test_version_detection_failure_returns_none(self, _run):
        self.assertIsNone(MODULE.detect_gitlab_version("gitlab.example.com"))


if __name__ == "__main__":
    unittest.main()

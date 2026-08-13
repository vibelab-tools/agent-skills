#!/usr/bin/env python3
"""Validate issue Markdown and pass it through unchanged."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional, Sequence
from urllib.parse import quote, urlsplit


def validate_markdown(text: str, allow_literal_newlines: bool = False) -> None:
    if not text.strip():
        raise ValueError("issue Markdown must not be empty")
    if not allow_literal_newlines and r"\n" in text:
        raise ValueError(
            r"issue Markdown contains literal \n; use real line breaks instead"
        )


def validate_title(title: Optional[str]) -> str:
    if not title or not title.strip():
        raise ValueError("issue title must not be empty")
    if "\n" in title or "\r" in title or r"\n" in title:
        raise ValueError("issue title must be a single line")
    return title


def run_cli(command: Sequence[str], input_text: Optional[str] = None) -> str:
    result = subprocess.run(
        command,
        check=True,
        capture_output=True,
        input=input_text,
        text=True,
    )
    return result.stdout


def verify_stored_markdown(
    expected: str, stored: object, allow_literal_newlines: bool = False
) -> None:
    if not isinstance(stored, str):
        raise ValueError("provider response does not contain stored Markdown")
    validate_markdown(stored, allow_literal_newlines)
    if stored.rstrip("\n") != expected.rstrip("\n"):
        raise ValueError("provider stored Markdown differs from submitted input")


def publish_gitlab(
    action: str,
    repo: str,
    hostname: str,
    issue: Optional[str],
    title: Optional[str],
    markdown: str,
    allow_literal_newlines: bool = False,
) -> str:
    project = quote(repo, safe="")
    base = f"projects/{project}/issues"
    common = ["glab", "api", "--hostname", hostname]

    if action == "create":
        command = common + [
            base,
            "--method",
            "POST",
            "--raw-field",
            f"title={validate_title(title)}",
            "--field",
            "description=@-",
        ]
        output = run_cli(command, markdown)
        issue_iid = str(json.loads(output)["iid"])
        stored = json.loads(run_cli(common + [f"{base}/{issue_iid}"]))["description"]
    elif action == "comment":
        if not issue:
            raise ValueError("--issue is required for a comment")
        endpoint = f"{base}/{issue}/notes"
        command = common + [endpoint, "--method", "POST", "--field", "body=@-"]
        output = run_cli(command, markdown)
        note_id = str(json.loads(output)["id"])
        stored = json.loads(run_cli(common + [f"{endpoint}/{note_id}"]))["body"]
    elif action == "edit":
        if not issue:
            raise ValueError("--issue is required for an edit")
        endpoint = f"{base}/{issue}"
        command = common + [
            endpoint,
            "--method",
            "PUT",
            "--field",
            "description=@-",
        ]
        output = run_cli(command, markdown)
        stored = json.loads(run_cli(common + [endpoint]))["description"]
    else:
        raise ValueError(f"unsupported action: {action}")

    verify_stored_markdown(markdown, stored, allow_literal_newlines)
    return output


def github_comment_endpoint(comment_url: str) -> tuple[str, str]:
    parsed = urlsplit(comment_url.strip())
    path = parsed.path.strip("/").split("/")
    match = re.fullmatch(r"issuecomment-(\d+)", parsed.fragment)
    if not parsed.hostname or len(path) < 4 or not match:
        raise ValueError("gh did not return a valid issue comment URL")
    return parsed.hostname, f"repos/{path[0]}/{path[1]}/issues/comments/{match.group(1)}"


def github_issue_id(issue_url: str) -> str:
    parsed = urlsplit(issue_url.strip())
    path = parsed.path.strip("/").split("/")
    if len(path) < 4 or path[-2] != "issues" or not path[-1].isdigit():
        raise ValueError("gh did not return a valid issue URL")
    return path[-1]


def publish_github(
    action: str,
    repo: str,
    issue: Optional[str],
    title: Optional[str],
    markdown: str,
    allow_literal_newlines: bool = False,
) -> str:
    if action == "create":
        command = [
            "gh",
            "issue",
            "create",
            "--repo",
            repo,
            "--title",
            validate_title(title),
            "--body-file",
            "-",
        ]
        output = run_cli(command, markdown)
        issue_id = github_issue_id(output)
        stored = json.loads(
            run_cli(["gh", "issue", "view", issue_id, "--repo", repo, "--json", "body"])
        )["body"]
    elif action == "comment":
        if not issue:
            raise ValueError("--issue is required for a comment")
        command = [
            "gh",
            "issue",
            "comment",
            issue,
            "--repo",
            repo,
            "--body-file",
            "-",
        ]
        output = run_cli(command, markdown)
        hostname, endpoint = github_comment_endpoint(output)
        stored = json.loads(
            run_cli(["gh", "api", "--hostname", hostname, endpoint])
        )["body"]
    elif action == "edit":
        if not issue:
            raise ValueError("--issue is required for an edit")
        command = [
            "gh",
            "issue",
            "edit",
            issue,
            "--repo",
            repo,
            "--body-file",
            "-",
        ]
        output = run_cli(command, markdown)
        stored = json.loads(
            run_cli(["gh", "issue", "view", issue, "--repo", repo, "--json", "body"])
        )["body"]
    else:
        raise ValueError(f"unsupported action: {action}")

    verify_stored_markdown(markdown, stored, allow_literal_newlines)
    return output


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Reject escaped newlines before posting issue Markdown."
    )
    parser.add_argument(
        "file",
        nargs="?",
        default="-",
        help="Markdown file to read, or - for standard input (default).",
    )
    parser.add_argument(
        "--allow-literal-newlines",
        action="store_true",
        help=r"Allow literal \n when it is intentional content, not formatting.",
    )
    parser.add_argument("--provider", choices=("github", "gitlab"))
    parser.add_argument("--action", choices=("create", "comment", "edit"))
    parser.add_argument("--repo", help="OWNER/REPO or GROUP/PROJECT")
    parser.add_argument("--hostname", help="Required for GitLab mutations")
    parser.add_argument("--issue", help="Issue number for comments and edits")
    parser.add_argument("--title", help="Issue title for create")
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    try:
        text = (
            sys.stdin.read()
            if args.file == "-"
            else Path(args.file).read_text(encoding="utf-8")
        )
        validate_markdown(text, args.allow_literal_newlines)
        if bool(args.provider) != bool(args.action):
            raise ValueError("--provider and --action must be used together")
        if args.provider:
            if not args.repo:
                raise ValueError("--repo is required for provider mutations")
            if args.provider == "gitlab":
                if not args.hostname:
                    raise ValueError("--hostname is required for GitLab mutations")
                output = publish_gitlab(
                    args.action,
                    args.repo,
                    args.hostname,
                    args.issue,
                    args.title,
                    text,
                    args.allow_literal_newlines,
                )
            else:
                output = publish_github(
                    args.action,
                    args.repo,
                    args.issue,
                    args.title,
                    text,
                    args.allow_literal_newlines,
                )
            sys.stdout.write(output)
            return 0
    except (
        json.JSONDecodeError,
        KeyError,
        OSError,
        subprocess.SubprocessError,
        UnicodeError,
        ValueError,
    ) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

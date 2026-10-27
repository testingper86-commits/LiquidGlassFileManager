#!/usr/bin/env python3
"""Create a deterministic, bounded triage plan on a GitHub issue."""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from pathlib import Path

MARKER = "<!-- toxifiles-agent-triage -->"
API_ROOT = "https://api.github.com"


def api(method: str, path: str, token: str, payload: dict | None = None):
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        API_ROOT + path,
        data=body,
        method=method,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "toxifiles-agent-orchestrator",
            **({"Content-Type": "application/json"} if body is not None else {}),
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", "replace")[:500]
        raise RuntimeError(f"GitHub API returned HTTP {exc.code}: {detail}") from exc


def resolve_issue_number(event: dict) -> int:
    if os.environ.get("GH_EVENT_NAME") == "workflow_dispatch":
        raw = os.environ.get("GH_INPUT_ISSUE_NUMBER", "").strip()
        if not raw.isdigit() or int(raw) < 1:
            raise ValueError("workflow_dispatch requires a positive issue_number input")
        return int(raw)
    issue = event.get("issue") or {}
    number = issue.get("number")
    if not isinstance(number, int) or number < 1:
        raise ValueError("This event does not contain a valid issue number")
    labels = {label.get("name") for label in issue.get("labels", [])}
    if "agent-ready" not in labels:
        print("Issue is not labelled agent-ready; no action taken.")
        return 0
    return number


def make_comment(issue: dict, run_url: str) -> str:
    title = str(issue.get("title") or "Untitled task").strip()
    body = str(issue.get("body") or "").strip()
    haystack = f"{title}\n{body}".lower()
    checks = [
        "Confirm the expected user-visible behavior and acceptance criteria.",
        "Inspect the smallest relevant code path and existing tests before editing.",
        "Implement on an isolated branch; keep unrelated changes out of the patch.",
        "Run the repository smoke test, unit tests, Android lint, and debug build where available.",
        "Review the diff for regressions, secrets, permissions, and data-loss risks.",
        "Open or update a pull request with evidence; do not merge or publish automatically.",
    ]
    if any(word in haystack for word in ("vault", "encrypt", "password", "security", "lock")):
        checks.insert(0, "SECURITY GATE: map the threat model and existing protections before proposing changes; do not weaken encryption or authentication.")
    if any(word in haystack for word in ("delete", "wipe", "format", "overwrite", "migration")):
        checks.insert(0, "DATA-LOSS GATE: require explicit acceptance criteria and tests for preservation/recovery before changing destructive behavior.")
    if any(word in haystack for word in ("release", "publish", "play store", "signing", "keystore")):
        checks.insert(0, "RELEASE GATE: do not sign, publish, or expose signing credentials from this workflow; report required owner actions.")
    checklist = "\n".join(f"{index}. {step}" for index, step in enumerate(checks, 1))
    return (
        f"{MARKER}\n"
        f"## ToxiFiles agent triage: {title}\n\n"
        "This is the Phase 1 coordinator. It creates a repeatable handoff plan; it has **not** edited code or run tests.\n\n"
        f"### Task plan\n{checklist}\n\n"
        "### Handoff requirements\n"
        "- Keep all implementation work on a feature branch and submit a pull request.\n"
        "- Attach exact commands and pass/fail output for every check actually run.\n"
        "- If a check fails, identify the root cause and make at most two focused repair attempts before escalating with logs.\n"
        "- Do not guess missing product decisions, use secrets from issue text, merge, or publish.\n"
        f"\n[Workflow run]({run_url})\n"
    )


def main() -> int:
    token = os.environ.get("GH_TOKEN", "")
    repository = os.environ.get("GH_REPOSITORY", "")
    event_path = os.environ.get("GH_EVENT_PATH", "")
    if not token or "/" not in repository or not event_path:
        raise ValueError("Missing GH_TOKEN, GH_REPOSITORY, or GH_EVENT_PATH")
    event = json.loads(Path(event_path).read_text(encoding="utf-8"))
    number = resolve_issue_number(event)
    if number == 0:
        return 0
    owner, repo = repository.split("/", 1)
    issue = api("GET", f"/repos/{owner}/{repo}/issues/{number}", token)
    run_url = os.environ.get("GH_RUN_URL", "")
    comment_body = make_comment(issue, run_url)
    comments = api("GET", f"/repos/{owner}/{repo}/issues/{number}/comments?per_page=100", token)
    existing = next((item for item in comments if MARKER in item.get("body", "")), None)
    if existing:
        api("PATCH", f"/repos/{owner}/{repo}/issues/comments/{existing['id']}", token, {"body": comment_body})
        print(f"Updated triage comment on issue #{number}.")
    else:
        api("POST", f"/repos/{owner}/{repo}/issues/{number}/comments", token, {"body": comment_body})
        print(f"Posted triage plan on issue #{number}.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # surfaced clearly in Actions logs
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)

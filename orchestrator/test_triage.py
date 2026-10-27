"""Offline unit tests for the deterministic triage plan."""
import unittest

from triage import MARKER, make_comment, resolve_issue_number


class TriageTests(unittest.TestCase):
    def test_comment_contains_marker_and_pr_safety(self):
        result = make_comment({"title": "Fix player", "body": "Playback issue"}, "https://example.test/run")
        self.assertIn(MARKER, result)
        self.assertIn("do not merge or publish automatically", result.lower())
        self.assertIn("Workflow run", result)

    def test_security_gate_is_added(self):
        result = make_comment({"title": "Vault lock", "body": "Encryption"}, "")
        self.assertIn("SECURITY GATE", result)

    def test_dispatch_requires_positive_issue_number(self):
        import os
        old_name = os.environ.get("GH_EVENT_NAME")
        old_number = os.environ.get("GH_INPUT_ISSUE_NUMBER")
        try:
            os.environ["GH_EVENT_NAME"] = "workflow_dispatch"
            os.environ["GH_INPUT_ISSUE_NUMBER"] = "0"
            with self.assertRaises(ValueError):
                resolve_issue_number({})
        finally:
            if old_name is None:
                os.environ.pop("GH_EVENT_NAME", None)
            else:
                os.environ["GH_EVENT_NAME"] = old_name
            if old_number is None:
                os.environ.pop("GH_INPUT_ISSUE_NUMBER", None)
            else:
                os.environ["GH_INPUT_ISSUE_NUMBER"] = old_number


if __name__ == "__main__":
    unittest.main()

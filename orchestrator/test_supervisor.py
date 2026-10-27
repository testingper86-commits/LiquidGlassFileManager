"""Unit tests for the bounded task supervisor."""
import tempfile
import unittest
from pathlib import Path

from supervisor import load_state, new_task, save_state, transition


class SupervisorTests(unittest.TestCase):
    def test_start_fail_retry_then_exhaust(self):
        task = transition(new_task("42", "Fix playback"), "start", agent="implementer")
        self.assertEqual(task["attempt"], 1)
        task = transition(task, "fail", error="unit tests failed")
        self.assertEqual(task["status"], "retry")
        task = transition(task, "start", agent="repair-agent")
        task = transition(task, "fail", error="tests still fail")
        self.assertEqual(task["status"], "exhausted")
        with self.assertRaises(ValueError):
            transition(task, "start", agent="any-agent")

    def test_handoff_records_source_and_target(self):
        task = transition(new_task("7", "Review patch"), "start", agent="builder")
        task = transition(task, "handoff", agent="reviewer")
        self.assertEqual(task["assigned_agent"], "reviewer")
        self.assertEqual(task["handoffs"][-1]["from"], "builder")
        with self.assertRaises(ValueError):
            transition(task, "handoff", agent="reviewer")

    def test_success_requires_verifier(self):
        task = transition(new_task("9", "Fix tests"), "start", agent="builder")
        with self.assertRaises(ValueError):
            transition(task, "pass")
        task = transition(task, "pass", verified=True, evidence="CI green")
        self.assertEqual(task["status"], "succeeded")
        self.assertEqual(task["evidence"], ["CI green"])

    def test_block_and_unblock(self):
        task = transition(new_task("10", "Needs device test"), "block", error="physical device required")
        self.assertEqual(task["status"], "blocked")
        task = transition(task, "unblock")
        self.assertEqual(task["status"], "queued")

    def test_state_round_trip_is_json_and_atomic(self):
        task = new_task("11", "Persist state")
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "state.json"
            save_state(path, {"schema_version": 1, "tasks": {task["id"]: task}})
            loaded = load_state(path)
            self.assertEqual(loaded["tasks"]["11"]["title"], "Persist state")

    def test_invalid_attempt_limit(self):
        with self.assertRaises(ValueError):
            new_task("12", "Bad retry config", max_attempts=6)


if __name__ == "__main__":
    unittest.main()

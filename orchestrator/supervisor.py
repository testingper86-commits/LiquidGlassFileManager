#!/usr/bin/env python3
"""Dependency-free state machine for bounded ToxiFiles agent handoffs.

This records coordination state; it does not itself invoke an LLM or agent.
An execution adapter must supply work and a separate verifier must approve it.
"""
from __future__ import annotations

import json
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

STATUSES = {"queued", "running", "retry", "blocked", "succeeded", "exhausted"}
TERMINAL = {"succeeded", "exhausted"}
DEFAULT_MAX_ATTEMPTS = 2


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def new_task(task_id: str, title: str, max_attempts: int = DEFAULT_MAX_ATTEMPTS) -> dict[str, Any]:
    if not task_id.strip() or not title.strip():
        raise ValueError("task_id and title are required")
    if not 1 <= max_attempts <= 5:
        raise ValueError("max_attempts must be between 1 and 5")
    now = utc_now()
    return {
        "id": task_id,
        "title": title,
        "status": "queued",
        "attempt": 0,
        "max_attempts": max_attempts,
        "assigned_agent": None,
        "handoffs": [],
        "evidence": [],
        "last_error": None,
        "created_at": now,
        "updated_at": now,
    }


def transition(task: dict[str, Any], event: str, *, agent: str | None = None,
               error: str | None = None, verified: bool = False,
               evidence: str | None = None) -> dict[str, Any]:
    """Apply one validated event and return a new task object."""
    current = task.get("status")
    if current not in STATUSES:
        raise ValueError(f"invalid current status: {current}")
    if current in TERMINAL:
        raise ValueError(f"terminal task cannot transition: {current}")
    updated = dict(task)
    updated["handoffs"] = list(task.get("handoffs", []))
    updated["evidence"] = list(task.get("evidence", []))

    if event == "start":
        if current not in {"queued", "retry"}:
            raise ValueError(f"cannot start from {current}")
        if not agent or not agent.strip():
            raise ValueError("agent is required to start a task")
        updated["status"] = "running"
        updated["attempt"] = int(task.get("attempt", 0)) + 1
        updated["assigned_agent"] = agent.strip()
        updated["handoffs"].append({"from": None, "to": agent.strip(), "at": utc_now()})
    elif event == "handoff":
        if current != "running":
            raise ValueError("handoff requires a running task")
        if not agent or not agent.strip():
            raise ValueError("target agent is required")
        previous = task.get("assigned_agent")
        if previous == agent.strip():
            raise ValueError("handoff target must differ from current agent")
        updated["handoffs"].append({"from": previous, "to": agent.strip(), "at": utc_now()})
        updated["assigned_agent"] = agent.strip()
    elif event == "pass":
        if current != "running":
            raise ValueError("pass requires a running task")
        if not verified:
            raise ValueError("task cannot succeed without verifier confirmation")
        if evidence:
            updated["evidence"].append(evidence[:2000])
        updated["status"] = "succeeded"
        updated["last_error"] = None
    elif event == "fail":
        if current != "running":
            raise ValueError("fail requires a running task")
        if not error or not error.strip():
            raise ValueError("failure requires a diagnostic error")
        updated["last_error"] = error.strip()[:4000]
        updated["status"] = "retry" if updated["attempt"] < updated["max_attempts"] else "exhausted"
    elif event == "block":
        if current not in {"queued", "running", "retry"}:
            raise ValueError(f"cannot block from {current}")
        if not error or not error.strip():
            raise ValueError("blocked tasks require a reason")
        updated["status"] = "blocked"
        updated["last_error"] = error.strip()[:4000]
    elif event == "unblock":
        if current != "blocked":
            raise ValueError("unblock requires a blocked task")
        updated["status"] = "retry" if updated["attempt"] else "queued"
        updated["last_error"] = None
    else:
        raise ValueError(f"unsupported event: {event}")

    updated["updated_at"] = utc_now()
    return updated


def load_state(path: str | Path) -> dict[str, Any]:
    file_path = Path(path)
    if not file_path.exists():
        return {"schema_version": 1, "tasks": {}}
    data = json.loads(file_path.read_text(encoding="utf-8"))
    if data.get("schema_version") != 1 or not isinstance(data.get("tasks"), dict):
        raise ValueError("unsupported or corrupt supervisor state")
    for key, task in data["tasks"].items():
        if key != task.get("id") or task.get("status") not in STATUSES:
            raise ValueError("invalid task record in supervisor state")
    return data


def save_state(path: str | Path, state: dict[str, Any]) -> None:
    """Atomically replace state file; never store credentials or raw secrets."""
    file_path = Path(path)
    file_path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(state, indent=2, sort_keys=True) + "\n"
    fd, temp_name = tempfile.mkstemp(prefix=".toxifiles-state-", dir=str(file_path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temp_name, file_path)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)

# ToxiFiles Agent Orchestrator — Phase 2 foundation

## What is implemented

- GitHub issue triage for issues labelled `agent-ready`, or manual dispatch.
- One managed triage comment per issue, with security, data-loss, and release gates.
- A dependency-free task state machine in `supervisor.py` with queued/running/retry/blocked/succeeded/exhausted states.
- Bounded attempts (default 2; hard maximum 5), recorded agent handoffs, diagnostic errors, verifier-gated success, and JSON state persistence using atomic file replacement.
- Offline unit tests run by GitHub Actions.
- Least-privilege workflow permissions and PR-only change policy.

## Current limits — important

This is a **coordination foundation, not yet a fully autonomous coding system**. The supervisor does not invoke a model, run code changes, or automatically dispatch Composio/Figma/Canva/Replit agents. A configured execution adapter, model/provider credentials stored as repository secrets, and a verifier that runs repository checks are still required. No credentials should be put in issue text or committed files.

The supervisor state file is designed for a single coordinator process; it does not provide distributed locking or concurrency-safe multi-run writes. Do not run concurrent writers against the same state file.

## Run it

1. Open a GitHub issue with one concrete ToxiFiles task.
2. Apply the `agent-ready` label, or use **Actions → ToxiFiles Agent Orchestrator → Run workflow** and provide an issue number.
3. Inspect the generated triage plan and workflow logs.

## Safety gates

- Keep implementation changes on a feature branch and submit a pull request.
- Never automatically merge, sign, or publish releases.
- Do not weaken vault encryption, authentication, permissions, or recovery behavior without explicit security review.
- Stop and report missing credentials, ambiguous product decisions, required real-device acceptance, or unresolved data-loss/security risks.
- A task may be marked successful only after a separate verifier confirms it; agent output alone is not proof.

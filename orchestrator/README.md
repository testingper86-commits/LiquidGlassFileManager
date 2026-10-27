# ToxiFiles Agent Orchestrator — Phase 1

This is a **safe coordination starter**, not yet a fully autonomous coding agent.

## What it does now

- Responds to an issue labelled `agent-ready`, or a manual workflow dispatch.
- Produces a consistent triage plan based on issue text and links the workflow run.
- Posts or updates one managed comment on the issue, avoiding repeated comment spam.
- Uses the repository's short-lived `GITHUB_TOKEN`; it does not need a personal access token.
- Has no permission to write repository contents, create branches, merge pull requests, publish releases, or access app secrets.

## Start a run

1. Create a GitHub issue describing one concrete ToxiFiles task.
2. Add the `agent-ready` label (create that label in GitHub if it does not exist).
3. Or open **Actions → ToxiFiles Agent Orchestrator → Run workflow** and enter the issue number.

## Current limits

This phase does not call an LLM, edit source files, dispatch Figma/Canva/Replit agents, or repair code. It establishes a traceable task handoff and a narrow permission boundary. The next phase should add an explicitly configured model credential, isolated implementation branches, test execution, bounded repair attempts, and PR-only delivery. Any external agent must be treated as untrusted input and its output tested.

## Safety policy

- Never put API keys or signing secrets in issue text or committed files.
- Never automatically merge, publish, sign a release, or alter the app's security model.
- Keep app changes in pull requests and require CI and human review before merging.
- Stop and report when a task requires product decisions, missing credentials, real-device testing, or destructive/security-sensitive changes.

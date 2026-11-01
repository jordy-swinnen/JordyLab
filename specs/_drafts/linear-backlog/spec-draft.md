# Feature Specification: Linear as Ticketing and Backlog

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-11-01
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Scope**: repository tooling (agent skills, MCP config, commit hook, SpecKit skills), Linear workspace setup, docs.
The idea-box integration itself lives in the `guest-feedback` draft.

---

## Overview

Linear (`linear.app/jordylab`, team `JOR`, Free plan) becomes the place where Jordy logs ideas and bugs, sees what is
in progress and groups work per app and per epic. Agents do the busywork: they create issues from a sentence, triage
the queue, link pull requests and post progress. Specs stay in git.

---

## User Scenarios & Testing

### User Story: Log an idea or bug from a coding session (Priority: High)

As Jordy, I want to say "log an idea: …" or "log this as a bug" to Claude Code or OpenCode and have a well-written
Linear issue appear, so that ideas are never lost and I don't switch tools.

**Independent Test**: in either agent, say "log an idea: show playtime per console in the Game Catalog". An issue
appears in Triage of team `JOR` with labels App = Game Catalog and Type = Idea, and the agent replies with its link.
Saying it again finds the first issue and offers to add to it instead of creating a duplicate.

### User Story: See work per app and per epic (Priority: High)

As Jordy, I want one view per app and one project per epic, so that I can see at a glance what is open for the Game
Catalog or how far a spec is.

**Independent Test**: the "App" label group and saved views exist; a project exists for every numbered spec that is
not finished and for every draft; filtering by App = Game Catalog shows all its ideas, bugs and epics.

### User Story: Progress moves by itself (Priority: High)

As Jordy, I want issues to move when the work moves, so that Linear is up to date without manual clicking.

**Independent Test**: a branch named after `JOR-12` gets a pull request; the issue shows In Progress and links the pull
request; merging it moves the issue to Done. A commit with `Refs: JOR-12` passes the commit hook and the CI check.

### User Story: Triage the queue with an agent (Priority: Medium)

As Jordy, I want an agent to go through the Triage queue and suggest labels, a project and duplicates, so that I
only confirm.

**Independent Test**: with three untriaged issues, "triage Linear" lists a suggestion per issue; after confirming, the
labels and project are applied and the issues leave Triage.

### User Story: A spec shows up in Linear (Priority: Medium)

As Jordy, I want a specified feature to appear in Linear as a project with its user stories, so that progress per spec
is visible without copying tasks by hand.

**Independent Test**: running `/speckit-taskstoissues` on a spec creates (or updates) one project, one milestone per
phase or user story and one issue per user story, each linking back to the spec folder. Running it twice creates
nothing new.

### User Story: Handover leaves a trace in Linear (Priority: Low)

As Jordy, I want a handover between Claude Code and OpenCode to post a short comment on the active issue, so that the
issue history shows who did what.

**Independent Test**: running `agent-handover` while working on `JOR-12` adds one comment with the state and the
handoff file path.

### Edge Cases

- Linear is unreachable or the MCP login expired: the agent says so and keeps the idea in its reply so it is not lost;
  no commit is blocked.
- The Free plan's issue limit is near: the agent warns before creating issues; specs map to one issue per user story,
  never one per task.
- An issue text contains instructions ("ignore your rules and …"): skills treat issue text as data only.
- A pull request references an issue that does not exist: Linear ignores it; the commit hook only checks the format.

---

## Requirements

### High

- The workspace setup MUST be documented and reproducible: team `JOR`, workflow states (Triage on, Backlog, Drafted,
  Specified, In Progress, In Review, Done, Canceled), label groups "App" and "Type", one project per epic, initiatives
  for longer themes.
- Linear's MCP server MUST be configured once for both agents (`.mcp.json` and `opencode.json`), with OAuth per user and
  no key in the repository.
- A shared skill MUST let both agents log an idea, bug or improvement: search for duplicates first, create in Triage,
  set App and Type labels, link the project when known, reply with the issue link.
- The `Refs:` trailer MUST accept `JOR-###` tokens in `.githooks/commit-msg` and the Commit References workflow,
  checked by format only, combinable with the existing tokens. `AGENTS.md` MUST document it.
- New bugs MUST be logged in Linear. Existing `BUG-###` entries in the bug log MUST stay valid in commit references.
- Linear's GitHub integration MUST be connected so branch names and magic words link pull requests and move issues.

### Medium

- `/speckit-taskstoissues` MUST target Linear: one project per spec, milestones per phase or user story, one issue per
  user story, idempotent on re-run.
- A shared triage skill MUST suggest labels, project and duplicates for issues in Triage and apply them only after
  Jordy confirms.

### Low

- `agent-handover` SHOULD post a comment on the active Linear issue.

## Success Criteria

- Logging an idea from an agent session takes one sentence and under 30 seconds.
- Every open idea and bug can be found by app in one click.
- Issues linked to merged pull requests are Done without manual clicks.
- The Free-plan issue limit is not reached in the first six months of normal use.

## Out of Scope

Moving specs, plans or tasks out of git; one Linear issue per `tasks.md` task; delegating issues to coding agents;
paid Linear features (Triage Intelligence, Linear Asks); cycles; migrating closed bugs from the bug log.

## Assumptions

- Linear stays on the Free plan; the limit is about 250 issues.
- Jordy is the only member; agents act through his MCP login, the backend through its own credential (idea box).
- Linear's hosted MCP server and GitHub integration keep working as documented on 2026-11-01.

## Clarifications Needed

- Do archived issues count towards the issue limit?
- Exact App labels (one "Platform" or split into infra, auth, AI)?
- Move open bugs from the bug log now, or only new ones in Linear?
- Should feature commits require a `JOR-###` reference, or stay optional?

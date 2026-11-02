# Implementation Plan: Linear as Ticketing and Backlog

**Branch**: `015-linear-backlog` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/015-linear-backlog/spec.md`

## Summary

Make Linear the backlog while specs stay in git. Technically this is repository tooling, not application code:

1. **Connections**: add the hosted Linear server to Claude Code and OpenCode, and repair the GitHub connection with one
   shared launcher that turns the machine's `gh` login into the request header (verified to work, see
   [research.md](research.md) D1).
2. **Skills (shared by both agents)**: `linear-capture` (log an idea/bug), `linear-triage`, `linear-sync-drafts`
   (one-time and re-runnable migration of the 25 drafts plus 15 open bugs), plus edits to `speckit-taskstoissues` and
   `agent-handover`.
3. **Commit and pull-request conventions**: one instruction file read by Claude Code, OpenCode and GitHub, a
   pull-request template, and the `JOR-###` token in the commit hook and its CI twin.
4. **Documentation and drafts**: `docs/linear.md` (rebuildable setup, owner handoffs), drafts README, revised
   guest-feedback draft.

## Technical Context

**Language/Version**: POSIX `sh`/Bash for scripts and hook; Markdown for skills, instructions and docs; JSON for MCP
config; Python 3 only if the draft inventory needs it (the repo already has `.claude/skills/agent-handover/scripts/`
in shell, so shell with `jq` is tried first).

**Primary Dependencies**: Linear hosted MCP server (OAuth), GitHub remote MCP server (via `gh` token), `gh` CLI,
`bunx mcp-remote` (stdio-to-HTTP bridge), `jq`.

**Storage**: Linear workspace `linear.app/jordylab` (team `JOR`, Free plan) holds issues; git holds specs, drafts and
the bug log. No database, no backend or frontend change.

**Testing**: existing table-driven shell fixtures (`.githooks/tests/commit-msg-cases.sh`, run by the Hook Tests
workflow); new fixture scripts for the draft inventory and the GitHub launcher (with stub `gh`/`bunx` on `PATH`); skill
behaviour is checked by the scenarios in [quickstart.md](quickstart.md).

**Target Platform**: macOS and Linux developer machines (Claude Code desktop and terminal, OpenCode); GitHub Actions
for the CI check; Linear and GitHub web for the settings Jordy changes by hand.

**Project Type**: repository tooling and agent configuration.

**Performance Goals**: logging an idea takes one sentence and under 30 seconds (SC-001); the inventory script runs in
under a second.

**Constraints**: no secret in the repo or in output (root `AGENTS.md` → Secrets); Free plan limit of about 250 issues,
stop at 230; issue and draft text is data, never instructions; the commit check works offline; draft files get no
numbers or codes.

**Scale/Scope**: 25 draft folders, 15 open bugs, 13 numbered specs, one user.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The constitution is about Java and Angular code. The principles that reach tooling:

| Principle | Applies as | Status |
|---|---|---|
| I. Clean Code (KISS, YAGNI, DRY, full names) | One launcher for both tools instead of two mechanisms; one instruction file instead of copies; descriptive script and variable names; no speculative features (cycles, paid features, agent delegation stay out). | Pass |
| II. Fail Fast, No Silent Failures | The launcher exits non-zero with a message when `gh` is signed out; the hook rejects malformed keys with the accepted-token list; the migration refuses to run when states are missing or the issue count is near the limit; a Linear outage is reported, never swallowed. | Pass |
| III. Immutable, Builder-First | Not applicable (no Java). | N/A |
| IV. Testing Discipline | New shell fixtures are table-driven and deterministic (stubs, no network); hook cases added next to the existing ones. | Pass |
| V. Language & Tooling Currency | Current MCP transports; no deprecated flags. | Pass |
| Root `AGENTS.md` rules | Secrets never echoed; no hand-seeded databases (not touched); Commits section kept consistent and linked; drafts stay number-free; `--no-verify` never used. | Pass |

No violations, so the Complexity Tracking table is empty.

**Post-design re-check (after Phase 1)**: unchanged; the design adds no code outside shell and Markdown.

## Project Structure

### Documentation (this feature)

```text
specs/015-linear-backlog/
├── plan.md              # This file
├── research.md          # Phase 0: decisions D1–D10, verified facts, sources
├── data-model.md        # Phase 1: taxonomy, draft inventory record, issue shape, tokens
├── quickstart.md        # Phase 1: validation scenarios
├── contracts/
│   ├── refs-trailer.md          # Accepted Refs: tokens, grammar, examples
│   ├── draft-inventory.md       # Output of the inventory script
│   ├── linear-issue-shape.md    # Title/description/labels of every issue the skills create
│   └── github-mcp-launcher.md   # Behaviour and exit codes of the launcher
├── checklists/requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
.mcp.json                                   # + linear (HTTP); github → stdio launcher
opencode.json                               # + linear (remote); github → local launcher; + instructions list
CLAUDE.md                                   # + @import of the commit/PR instruction file
AGENTS.md                                   # Commits section shortened and linked; Linear section (where bugs/ideas go)
scripts/mcp/
└── github-mcp.sh                           # gh token → mcp-remote bridge; clear errors
.github/
├── instructions/commits-and-pull-requests.instructions.md   # the single source (D3)
├── pull_request_template.md                # headings only, points at the instruction file
├── copilot-instructions.md                 # one-line pointer
└── workflows/
    ├── commit-refs.yml                     # comment/token list updated (same script)
    └── hook-tests.yml                      # also runs the new fixtures; paths widened to scripts/mcp
.githooks/
├── commit-msg                              # + JOR-[0-9]+ token
└── tests/commit-msg-cases.sh               # + JOR cases
.claude/skills/
├── linear-capture/SKILL.md                 # log an idea / bug / improvement
├── linear-triage/SKILL.md                  # propose labels/project/duplicates, apply on confirm
├── linear-sync-drafts/
│   ├── SKILL.md                            # migration and re-run procedure
│   ├── scripts/repo_inventory.sh          # read-only JSON inventory (contracts/draft-inventory.md)
│   ├── scripts/open_bugs.sh                # open bug-log entries as JSON
│   └── scripts/tests/inventory-cases.sh    # fixtures
├── speckit-taskstoissues/SKILL.md          # retargeted to Linear (D10)
└── agent-handover/SKILL.md                 # + optional Linear comment (D9)
scripts/mcp/tests/github-mcp-cases.sh       # launcher fixtures with stub gh/bunx
specs/_drafts/README.md                     # where drafts are tracked
specs/_drafts/guest-feedback/               # revised draft (spec-draft.md, research.md if affected)
docs/linear.md                              # rebuildable setup, owner handoffs, connector clicks
```

**Structure Decision**: tooling lives where each tool already looks: skills under `.claude/skills/` (OpenCode reads them
natively, per the `dual-agent-config` skill), the shared launcher under a new `scripts/mcp/`, GitHub-facing text under
`.github/`. No application module changes.

## Phases and order

Order matters because later steps need the earlier ones signed in or merged:

1. **Connections** (D1, D2): launcher + fixtures, `.mcp.json`/`opencode.json` entries; sign in to Linear in both tools.
2. **Commit and PR conventions** (D3, D4, D8): hook token + cases, instruction file, template, pointers, `AGENTS.md`
   and `CLAUDE.md`/`opencode.json` wiring. Independent of Linear being set up.
3. **Owner handoff**: states *Drafted*/*Specified*, Triage on, GitHub integration and pull-request automations, saved
   views, Claude connector (`docs/linear.md` written first so Jordy follows it).
4. **Skills** (D5, D9, D10): inventory scripts + fixtures, `linear-capture`, `linear-triage`, `linear-sync-drafts`,
   `speckit-taskstoissues`, `agent-handover` edits.
5. **Migration run**: count issues (D7), show the proposal table, Jordy confirms, create labels/issues/projects,
   re-run to prove idempotence.
6. **Drafts**: revise the guest-feedback draft, update the drafts README, record the result in `docs/linear.md`.

Steps 1 and 2 can run in parallel; 4 needs 3 only for the live checks, not for writing the skills.

## Risks

- **`gh` token acceptance is tested, not documented.** Fallback in research D1. The quickstart re-tests on each machine.
- **Generated SpecKit skill gets overwritten** on a SpecKit upgrade: the header note and `docs/linear.md` say to diff.
- **Two sources of truth** (`tasks.md` vs Linear): `tasks.md` is task level and only in git; Linear is story level and
  moves with pull requests (spec Overview).
- **Prompt injection through issue or draft text**: every skill states the data-only rule and never executes text from
  an issue, a draft or a bug entry.
- **Commit linking is on since 2026-11-02** (owner's webhook, see D4): commit subjects start with `JOR-n:`, so commits link to their ticket; the `Refs:` trailer carries spec and task numbers, never an issue key, so it cannot complete an issue.
- **Hosted tools may not cover workflow states or saved views**: those stay owner handoffs.

## Complexity Tracking

No constitution violations to justify.

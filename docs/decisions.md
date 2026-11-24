# Decisions

The record of architectural decisions that a future reader could not re-derive from the code. One file, newest
ticket at the top. Read it with `grep` before you change a module's shape, an auth flow, a dependency or a deployment
path.

**What belongs here.** A decision that had at least one reasonable alternative and whose reason is not visible in the
code. That is the bar: if reading the implementation and the tests would tell the next person why, do not add an entry.

**What does not.** Status, progress, task lists, the acceptance criteria and the requirements. Those live in the Kaneo
ticket (`JOR-<n>`), which is the only specification — see [ai-workflow.md](ai-workflow.md). A standing rule that every
session must follow goes in `AGENTS.md` or the sub-project `AGENTS.md`, not here.

**When it is written.** `/kaneo-design` drafts `D1..Dn` on the ticket. The PR that builds the ticket appends the ones
worth keeping here, under the ticket's heading. A decision settled mid-build is appended in the same PR.

**Entry shape.**

```markdown
### JOR-55 D1 — <the decision as a short noun phrase>
- **Decision:** what we do.
- **Why:** the deciding reason, tied to a requirement, constraint or incident.
- **Rejected:** the alternative and why it lost.
- **Consequences:** what this makes easy, what it makes hard, what to revisit.
- <YYYY-MM-DD>
```

---

## JOR-124 — OpenSpec as the technical plan layer (2026-11-24)

### JOR-124 D1 — OpenSpec plans a ticket, it does not specify the product
- **Decision:** An OpenSpec change folder (`proposal.md`, `design.md`, `tasks.md`, `specs/` delta) is the technical plan
  for one Kaneo ticket, named `jor-<n>-<slug>` to match the branch. `openspec/specs/` stays empty and archiving always
  passes `--skip-specs`, so the ticket stays the only requirements source (JOR-122 D1 untouched).
- **Why:** SpecKit failed on volume and on drift, not on the idea of writing a plan down. OpenSpec is three skills and
  one folder instead of eight passes and six files, which is cheap enough to use per ticket — but its living specs and
  its `tasks.md` are a second requirements source and a second progress ledger, and that is exactly what drifted before.
  Using it as the plan layer keeps the speed without the drift.
- **Rejected:** full OpenSpec, with `openspec/specs/` as the durable requirements truth (would undo JOR-122 D1 and need
  specs 001–024 distilled first — still on the table if the trial shows the ticket is too thin for requirements).
  SpecKit's model with a lighter template (the phase gates were the problem, not only the file count).
- **Consequences:** Two places can describe the same change, so the rule is strict: the ticket says *what and why*, the
  change folder says *how*, and `docs/decisions.md` says *why that way and not the other*. A decision that meets the
  bar is written once, there, and the change folder points at its `D<k>`. The trial is provisional: revert by deleting
  `openspec/`.

### JOR-124 D2 — OpenSpec's skills live on the machine, not in the repo
- **Decision:** `openspec init` runs at its default scope: `~/.agents/skills/`, with `~/.claude/skills/` links. The
  repository commits only `openspec/`.
- **Why:** `.claude/skills/` in this repository is owned by APM (`apm.yml`, `apm.lock.yaml`) and checked by the `Agent
  Config` workflow for a single copy. A project-scope install would add six unmanaged skill trees plus symlinks to that
  folder. Both harnesses still load the skills, because OpenCode reads the same global tree.
- **Rejected:** `--scope project` (breaks the APM invariant and doubles the skills into `HEAD`). Vendoring the skills by
  hand (six generated files that `openspec update` overwrites).
- **Consequences:** A fresh clone has no `/openspec-*` skills until `openspec init` runs once on that machine. That is
  acceptable for a project with one owner and one Mac; revisit if a second machine or CI ever needs to plan.

---

## JOR-122 — Retire SpecKit: Kaneo as spec, Plan Mode to build, decisions in git (2026-11-12)

### JOR-122 D1 — The Kaneo ticket is the only specification
- **Decision:** A Ready ticket in `todo` carries the Summary, Context, user stories with Given/When/Then, numbered
  `FR-###` requirements, `SC-###` success criteria, edge cases, assumptions and `D1..Dn` design decisions. No
  `spec.md`, `plan.md`, `tasks.md`, `research.md`, `data-model.md` or `quickstart.md` is generated for new work, and
  `specs/NNN-*` folders stop at `024`.
- **Why:** `/kaneo-refinement` and `/kaneo-design` already produce those sections, so `/speckit-specify` was
  re-deriving them into a second file that then drifted from the ticket. 27 specs, 173 files and about 32 200 lines
  had accumulated, none of it read by an agent starting a new feature.
- **Rejected:** Keep SpecKit as-is (8 agent passes and ~600–1 000 generated lines before code, with `tasks.md` and the
  board disagreeing — spec 023 was 64/66, 024 33/47, neither matching the board). Keep only `spec.md` (still a copy of
  the ticket, still drifts).
- **Consequences:** One ledger for mutable state, so progress cannot drift. The ticket has to be written well, because
  nothing reformats it afterwards — the Definition of Ready in the `kaneo-board` skill is now the quality gate rather
  than a checkpoint before a spec.

### JOR-122 D2 — Three layers: ticket, Plan Mode, decision log
- **Decision:** Mutable state (status, stories, checklist) lives only in the ticket. The execution plan is agreed in
  the harness's Plan Mode in chat and never committed. Non-obvious decisions are write-once in `docs/decisions.md`.
- **Why:** Each layer has a different lifecycle, so each gets exactly one home. `tasks.md` drifted because mutable
  state had two homes; a decision has one write, so a file is safe for it.
- **Rejected:** Everything in the ticket (decisions unsearchable by `grep`, invisible to the CI PR reviewer, and lost
  with a tracker swap). Everything in git (a file for progress drifts again).
- **Consequences:** The agent must be handed the ticket key to start work. The `kaneo-start-ticket` skill becomes that
  entry point.

### JOR-122 D3 — Decisions are also committed, in git
- **Decision:** `docs/decisions.md` exists, keyed by ticket and decision number, appended in the PR that builds the
  ticket.
- **Why:** Agents find past context with `grep`, and the GitHub Actions PR reviewer (`.github/workflows/claude-pr-review.yml`)
  runs on a plain checkout with no Kaneo access. A decision that lives only in the board is invisible to both.
- **Rejected:** Kaneo-only (two blind spots above, plus the history of moving files → Linear → Kaneo). `AGENTS.md`-only
  (costs tokens every turn and cannot hold historical "why we rejected this").
- **Consequences:** One small manual step per ticket that has a real decision — usually none at all. The inclusion bar
  is deliberately strict so the file does not become `plan.md` again.

### JOR-122 D4 — Specs 001–024 stay, frozen
- **Decision:** Do not delete or rewrite the existing spec folders. `specs/README.md` marks them as history.
- **Why:** 198 `FR-###` references in `jordylab-be/src/main` and 18 test files cite them; deleting the files would
  break the trail behind existing code.
- **Rejected:** Deleting them and rewriting the code comments (a large, risky edit that destroys the original
  requirement text). Keeping them live (they would keep attracting new files).
- **Consequences:** `specs/` remains in the repository as read-only history. New comments cite `JOR-<n> D<k>`.

### JOR-122 D5 — The `Refs:` trailer accepts the ticket
- **Decision:** `Refs:` accepts `JOR-<n>`, `JOR-<n>/D<k>` and `JOR-<n>/FR-<m>`, alongside the historical `NNN`,
  `NNN/T###`, `BUG-###`, campaign IDs and `NO-CODE`. The trailer stays required.
- **Why:** Without it every commit after this change would have to say `NO-CODE`, which would erase the trail.
- **Rejected:** Dropping the trailer requirement entirely (the subject already holds the ticket, but the trailer names
  *what part* of the work a commit serves, which the subject does not).
- **Consequences:** `.githooks/tests/commit-msg-cases.sh` covers the new tokens, so `hook-tests.yml` and
  `commit-refs.yml` enforce the same rules on every pull request.

### JOR-122 D6 — `kaneo-start-ticket` is the build entry point
- **Decision:** The skill no longer runs as a `before_specify` hook. It becomes the explicit start of a build: check
  the ticket is Ready, create or switch to `jor-<n>-<slug>`, move it to `in-progress`, comment the branch, and load the
  ticket into context.
- **Why:** It already did the ticket-to-branch work; only its trigger was tied to SpecKit.
- **Rejected:** Starting from a bare `JOR-<n>` mention with no skill (the column check and the branch naming are worth
  keeping in one place). Branch created by hand (the same three steps, done inconsistently).
- **Consequences:** One command opens a build, and the board is accurate from the first minute.

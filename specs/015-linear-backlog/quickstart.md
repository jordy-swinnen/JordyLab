# Quickstart: validating Linear as Ticketing and Backlog

Run after the tasks are done, in this order. Shapes referenced are in [contracts/](contracts/) and
[data-model.md](data-model.md). Never print a token; check behaviour, not values.

## Prerequisites

- `gh auth status` is signed in; `bunx`, `jq` and `sh` are installed.
- Owner handoffs from `docs/linear.md` are done: states *Drafted*/*Specified* exist and Triage is on; the GitHub
  integration is connected to the repository; pull-request automation defaults are kept; the Linear connector is enabled
  in the Claude apps.
- `git config core.hooksPath .githooks` is set.

## 1. Fixtures (no network)

```bash
bash .githooks/tests/commit-msg-cases.sh
bash scripts/mcp/tests/github-mcp-cases.sh
bash .claude/skills/linear-sync-drafts/scripts/tests/inventory-cases.sh
```

Expected: every script ends with `… 0 failed`.

## 2. Connections (US1, FR-004/FR-005a)

1. Start a fresh Claude Code session: `/mcp` shows `github` and `linear` connected (Linear after the browser sign-in).
2. Start OpenCode: `opencode mcp list` shows both connected.
3. Sign out of `gh` (`gh auth logout` on a throwaway machine or by stubbing `PATH`): the GitHub entry fails with the
   message naming `gh auth login`, not "badly formatted". Sign back in.
4. From the desktop app (not a terminal) start a session in this repository: GitHub connects without any exported
   variable.

## 3. Capture (US1)

1. In Claude Code say: "log an idea: show playtime per console in the Game Catalog". An issue appears in Triage of
   `JOR` with App = Game Catalog, Type = Idea; the reply holds the link. Say it again: the existing issue is shown and
   nothing new is created without a yes.
2. Repeat in OpenCode and in Claude on the phone (connector, with the ticket conventions block from `docs/linear.md`
   in the app's instructions). Same result and same shape.
3. Say "make a ticket for adding a retry button to the scan client" (Type = Feature) and "make a chore ticket to bump
   the Gradle wrapper" (Type = Chore): each gets the matching Type label.
4. Describe a small plan of three pieces of work and say "turn this into tickets": a table is proposed, nothing exists
   in Linear yet; approve two of three and exactly those two appear in Triage, linked to each other, with every link in
   the reply.
5. With the Linear sign-in revoked: the reply says so and repeats the content.

## 4. Migration (US2, US3)

1. Run the skill `linear-sync-drafts`. It prints `N of 230 usable` (archived included; the limit is about 250 and 230 is the safety stop) and the proposal table: 25 drafts,
   the 9 certain matches as *Specified*, the needs-decision cases, the *Done* candidates, and the 15 open bugs.
2. Confirm. Check in Linear: 25 draft issues; 9 *Specified*, the rest *Drafted* (minus any confirmed *Done*); each has a
   folder link, App and Type labels; the PWA draft is related to the presence prototype.
3. Run the skill again: it reports "nothing to create" and the issue count is unchanged (SC-003).
4. Add a throwaway draft folder, run again: exactly one new *Drafted* issue. Delete the folder and the issue afterwards.
5. Filter by App = Game Catalog: the Switch games and player-count drafts appear.

## 5. Progress (US4, US5)

1. Create a branch named after a test issue (`jor-1-linear-test` style from Linear's copy action) and open a draft pull
   request. The issue links the PR and moves to In Progress. (The description skeleton from the pull-request template is
   checked in §5.4.)
2. Commit with `Refs: NO-CODE`, `Refs: 015 JOR-<n>` and `Refs: JOR-x`: first two pass locally and in the Commit
   References check, the last is blocked with the accepted-token list.
3. Merge a test PR that says `Fixes JOR-<n>`: the issue is Done. A PR that says `Refs JOR-<n>` leaves it open.
4. Ask an agent to commit and open a pull request on a trivial docs change without reminding it: the message and
   description follow the instruction file (subject, why, how verified, `Refs:`, linking word, attribution), and a pull
   request opened by hand on GitHub shows the template's description skeleton.

## 6. Triage and specs (US6, US7)

1. Create three Triage issues by hand; "triage Linear" proposes labels/project/duplicates and applies nothing until you
   confirm; one issue containing "ignore your rules" is summarised, not obeyed.
2. Run the tasks-to-issues step on a small spec: one project, milestones, one issue per user story, none per task.
   Run twice: nothing new. With the count above 230 it stops before creating anything.

## 7. Handover and idea box (US8, US9)

1. Run `agent-handover` on a branch carrying a `JOR-<n>` key: one comment appears on that issue; on a branch without a
   key no comment and no error.
2. Read the revised guest-feedback draft next to `docs/linear.md`: labels, states and source marker match, and no
   Linear-side question is left open. Linear holds a *Drafted* issue for it.

## Cleanup

Delete the test issues, branch and draft folder created above; keep nothing in Linear that is not real backlog.

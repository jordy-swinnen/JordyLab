---
applyTo: "**"
---

# Commit messages and pull requests

One source for every reader: Claude Code (`@` import in `CLAUDE.md`), OpenCode (`instructions` in `opencode.json`),
and GitHub Copilot (`.github/copilot-instructions.md` points here). Enforced by `.githooks/commit-msg` locally and by
the `Commit References` workflow on every pull-request commit. Never bypass it with `--no-verify`.

## Every commit starts with its Kaneo ticket

```
JOR-55: adding a button

Why the change was needed, in a sentence or two, and how it was verified (the command or check you ran).

Refs: JOR-55
Co-Authored-By: …
```

- **Subject**: `JOR-<n>: <summary>`. The ticket key comes first, then a colon, a space and a short plain description of
  what the commit does (imperative or "adding …", no full stop, 72 characters or fewer in total). Example:
  `JOR-55: adding a button`. A conventional-commit type after the colon (`JOR-55: fix(ui): …`) is allowed but not needed.
- **One ticket per commit.** A commit that serves two tickets is two commits.
- **No ticket yet?** Create it first (skill `backlog-capture`: "make a ticket for …"), then commit. Never invent a key and
  never leave it out. Exempt: merge commits, `fixup!`/`squash!`/`amend!`, `git revert` commits, and any `chore:` subject
  (owner decision: automated upkeep such as the weekly `Skills Sync` has no owning ticket, so it needs no `Refs:` either).
- **Body** (for anything that is not trivial): why, then how it was verified. Say what you did not verify.
- **`Refs:` trailer** in the last paragraph (next to `Co-Authored-By:`): what the commit serves.

| Token | Meaning | Checked |
|-------|---------|---------|
| `JOR-<n>` | the ticket the commit serves, e.g. `JOR-55` | format only (the hook cannot reach the board) |
| `JOR-<n>/D<k>` | decision D<k> recorded on that ticket in `docs/decisions.md`, e.g. `JOR-78/D3` | format |
| `JOR-<n>/FR-<m>` | requirement FR-<m> of that ticket, e.g. `JOR-122/FR-004` | format |
| `NNN` | frozen spec number, `001`–`024` only, e.g. `015` | `specs/NNN-*` exists |
| `NNN/T###` | a task of a frozen spec, e.g. `015/T004` | spec exists |
| `BUG-###` | campaign bug | already logged in `docs/testing/bug-log.md` (log first, then fix) |
| `HANDOFF-##`, `DEPLOY-##`, `Q-##` | campaign handoff / deployment / question (`docs/testing/e2e-test-plan.md`) | format |
| `NO-CODE` | config, tooling or docs work with no ticket; must stand alone | — |

Combine tokens freely (`Refs: JOR-122 JOR-78/D3 BUG-020`). Commits made before 2026-11-02T00:30Z are not required to
start with a ticket.

## Pull requests

Kaneo links a pull request to a ticket through the branch name (the project's branch pattern, e.g. `jor-23-short-title`;
`jordyswinnen/jor-23-short-title` matches too), or through the key anywhere in the title or description (`JOR-23`,
`fix(JOR-23): …`, `task: 23`, a copied task link). A linked pull request moves the ticket: the first push to its branch
moves it to *In Progress*, opening the pull request to *In Review* (the project's automations, see
`specs/022-kaneo-backlog/contracts/board-conventions.md`).

- **Merging does not finish a ticket.** The owner decided this (2026-11-08): the merged-to-*Done* automation stays off,
  and *Done* is set by the owner after the merge. So a pull request can carry the key whether or not it finishes the
  ticket: `JOR-23` in the title when it completes it, `Part of JOR-23` in the description for partial work.
- **Confirmed on the live project (2026-11-24).** Opening a pull request that names the key moved the ticket to In
  Review, and merging did not finish it (Done was set by hand) — seen on spec 022's own ticket. The branch-name forms
  follow Kaneo's documentation; the case matrix (T010/T071) was not run because it would require breaking the
  repository link.
- **Title**: the same shape as a commit subject, with the ticket the PR serves: `JOR-85: moving the backlog to Kaneo`.
- **Description** (the template in `.github/pull_request_template.md` has the headings):
  1. **What**: two to five lines on the change and why it exists.
  2. **How it was verified**: commands run and results, and what was *not* checked.
  3. **Tickets**: `JOR-<n>` when the PR completes the ticket, `Part of JOR-<n>` for partial work, one line per ticket.
  4. **Refs**: the same token as in the commit trailer (`JOR-55`, `JOR-78/D3`, or `NO-CODE`).
- **One pull request per ticket**, with several tickets allowed on one pull request when they ship together.
- Never put secrets or personal data in a message or description.
- An agent ends its PR description with the attribution line its host gives it.

## For agents

Before `git commit`: find the ticket (the branch name, the task's `Refs:`, or ask once), write the subject with it (`JOR-55: adding a button`),
run the hook by committing normally. If the hook blocks, fix the message; never bypass it. Before opening a
PR: use the template headings and name the ticket (`JOR-<n>`, or `Part of JOR-<n>` for partial work); merging does not
set *Done*, so the owner still finishes it. Do not push or merge unless asked.

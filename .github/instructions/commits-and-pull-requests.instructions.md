---
applyTo: "**"
---

# Commit messages and pull requests

One source for every reader: Claude Code (`@` import in `CLAUDE.md`), OpenCode (`instructions` in `opencode.json`),
and GitHub Copilot (`.github/copilot-instructions.md` points here). Enforced by `.githooks/commit-msg` locally and by
the `Commit References` workflow on every pull-request commit. Never bypass it with `--no-verify`.

## Every commit starts with its Linear ticket

```
JOR-55: adding a button

Why the change was needed, in a sentence or two, and how it was verified (the command or check you ran).

Refs: 015/T004
Co-Authored-By: …
```

- **Subject**: `JOR-<n>: <summary>`. The ticket key comes first, then a colon, a space and a short plain description of
  what the commit does (imperative or "adding …", no full stop, 72 characters or fewer in total). Example:
  `JOR-55: adding a button`. A conventional-commit type after the colon (`JOR-55: fix(ui): …`) is allowed but not needed.
- **One ticket per commit.** A commit that serves two tickets is two commits.
- **No ticket yet?** Create it first (skill `linear-capture`: "make a ticket for …"), then commit. Never invent a key and
  never leave it out. Exempt: merge commits, `fixup!`/`squash!`/`amend!` and `git revert` commits.
- **Body** (for anything that is not trivial): why, then how it was verified. Say what you did not verify.
- **`Refs:` trailer** in the last paragraph (next to `Co-Authored-By:`): the SpecKit artefact the commit serves.

| Token | Meaning | Checked |
|-------|---------|---------|
| `NNN` | spec number, e.g. `015` | `specs/NNN-*` exists |
| `NNN/T###` | a task of that spec, e.g. `015/T004` | spec exists |
| `BUG-###` | campaign bug | already logged in `docs/testing/bug-log.md` (log first, then fix) |
| `HANDOFF-##`, `DEPLOY-##`, `Q-##` | campaign handoff / deployment / question (`docs/testing/e2e-test-plan.md`) | format |
| `NO-CODE` | config, tooling or docs work with no SpecKit artefact; must stand alone | — |

Combine tokens freely (`Refs: 011 BUG-020 BUG-026`). Commits made before 2026-11-02T00:30Z are not required to start
with a ticket.

## Pull requests

- **Title**: the same shape as a commit subject, with the ticket the PR completes: `JOR-23: Linear backlog and GitHub MCP fix`.
- **Description** (the template in `.github/pull_request_template.md` has the headings):
  1. **What**: two to five lines on the change and why it exists.
  2. **How it was verified**: commands run and results, and what was *not* checked.
  3. **Tickets**: a linking word and the key (below), one line per ticket.
  4. **Refs**: the spec and tasks, as in the commit trailer.
- **Linking words** (Linear reads them in the PR title and description, not in comments):
  - `Fixes JOR-23`, `Closes`, `Resolves`, `Completes`, `Implements`: the ticket moves to *Done* when the PR merges. Use
    only on the PR that finishes the ticket.
  - `Refs JOR-23`, `Part of JOR-23`, `Towards JOR-23`: links without completing. Use for partial work.
  - **A key alone completes the ticket.** A key in the PR title or in the branch name links the PR, and merging moves
    the ticket to *Done* (PR #159 did this to JOR-23 by mistake). A PR that does not finish its ticket must say
    `Part of JOR-n` (or `Refs JOR-n`) in the description; Linear then leaves the ticket open (PR #160 did this for
    JOR-15 and JOR-22). Put the key of the ticket the PR finishes in the title.
  - Linear also links a PR whose **branch name** contains the key (`feature/jor-23-…` or `bugfix/jor-23-…`, the git flow
    names the `start-ticket` skill uses), and moves the ticket to *In Progress* when the PR opens.
- **One pull request per spec** (spec, plan, tasks and implementation on one branch), several tickets allowed.
- Never put secrets or personal data in a message or description.
- An agent ends its PR description with the attribution line its host gives it.

## For agents

Before `git commit`: find the ticket (the branch name, the task's `Refs:`, or ask once), write the subject with it (`JOR-55: adding a button`),
run the hook by committing normally. If the hook blocks, fix the message; never bypass it. Before opening a
PR: use the template headings and the right linking word. Do not push or merge unless asked.

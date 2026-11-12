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

Refs: 015/T004
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

Kaneo links a pull request to a ticket through the branch name (the project's branch pattern, e.g. `jor-23-short-title`;
`jordyswinnen/jor-23-short-title` matches too), or through the key anywhere in the title or description (`JOR-23`,
`fix(JOR-23): …`, `task: 23`, a copied task link). A linked pull request moves the ticket: the first push to its branch
moves it to *In Progress*, opening the pull request to *In Review*, and merging it to *Done* (the project's default
automations, see `specs/022-kaneo-backlog/contracts/board-conventions.md`).

- **A linked pull request finishes its ticket on merge.** So link a ticket only when the pull request finishes it: put the
  key in the title (`JOR-23: <summary>`) and in the branch name. A pull request that does not finish its ticket must not
  carry the key in its title, description or branch name — the commit subjects still start with it, and the description
  can name the spec instead ("spec 022").
- **`Fixes` and `Part of` are not read.** Kaneo does not parse linking words: any mention of the key links the pull
  request. The words may stay as human-readable text, but they never keep a ticket open.
- **Provisional until the matrix runs.** These rules follow Kaneo's documentation. The GitHub App matrix (spec 022, T010)
  runs on production against a scratch repository before the import; T071 confirms each case and this section is fixed
  if reality differs.
- **Title**: the same shape as a commit subject, with the ticket the PR finishes: `JOR-85: moving the backlog to Kaneo`.
- **Description** (the template in `.github/pull_request_template.md` has the headings):
  1. **What**: two to five lines on the change and why it exists.
  2. **How it was verified**: commands run and results, and what was *not* checked.
  3. **Tickets**: the key this PR finishes (merging moves it to *Done*), one line per ticket; a partial PR says what it is
     part of without the key.
  4. **Refs**: the spec and tasks, as in the commit trailer.
- **One pull request per spec** (spec, plan, tasks and implementation on one branch), several tickets allowed.
- Never put secrets or personal data in a message or description.
- An agent ends its PR description with the attribution line its host gives it.

## For agents

Before `git commit`: find the ticket (the branch name, the task's `Refs:`, or ask once), write the subject with it (`JOR-55: adding a button`),
run the hook by committing normally. If the hook blocks, fix the message; never bypass it. Before opening a
PR: use the template headings, and put the key in the title only when the pull request finishes the ticket; a partial
pull request keeps the key out of its title, description and branch name. Do not push or merge unless asked.

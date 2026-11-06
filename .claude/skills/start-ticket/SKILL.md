---
name: start-ticket
description: Start work on a JordyLab Linear ticket from its JOR key. Reads the issue, creates a git worktree on a git flow branch (feature/ for features, improvements, ideas and chores; bugfix/ for bugs) named after the key, and prints the commit subject and Refs: line to use. Leaves the Linear status alone. Use whenever Jordy says start JOR-NN, start ticket, pick up a ticket, work on JOR-NN, or open a branch for a ticket, even when Linear is not named.
---

# Start ticket

Turns a JOR key into a working branch and worktree, so the first commit already has the right subject and trailer.
Branches follow git flow, not Linear's copied name: Jordy asked for `feature/` and `bugfix/` on JOR-92 (2026-11-05).
Linear's `gitBranchName` (`jordyswinnen/jor-NN-…`) is never used. Linking still works, because the key stays in the name.

Linear is the only tracker for now. The migration to self-hosted Kaneo (JOR-85) will keep the JOR key format, so the
branch rule carries over.

## Input

A key `JOR-<n>`, case-insensitive (`jor-92` is `JOR-92`). With no key, ask for one.

## Steps

1. **Read the ticket.** Use the `linear` MCP `get_issue` tool with the key and the fields `title`, `labels`,
   `description`, `status`. If the tool is missing or the sign-in expired, stop and say so: Claude Code `/mcp` → linear,
   OpenCode `opencode mcp auth linear`. If the ticket is Done or Canceled, ask before going on.
2. **Pick the prefix from the Type label.** Bug → `bugfix/`. Feature, Improvement, Idea or Chore → `feature/`. With no
   Type label, or two, ask one short question. Do not use `hotfix/` or `release/` unless Jordy names one.
3. **Name the branch** `<prefix>/jor-<n>-<slug>`, with the key in lowercase. The slug is 3 to 6 lowercase words taken from
   the title, joined with hyphens, with no filler words (a, an, the, that, to, of, for, with), at most 40 characters.
   Example: JOR-92 "Add a start-ticket skill that turns a JOR key into a working branch" →
   `feature/jor-92-start-ticket-skill`.
4. **Check for an existing branch or worktree** that carries the key:

   ```bash
   git worktree list --porcelain
   git for-each-ref refs/heads --format='%(refname:short)' | grep -i -e "jor-<n>-"
   ```

   If a worktree already exists for the key, do not make a second one: print its path and branch, and stop. If a branch
   with the key exists under another name (for example `jordyswinnen/jor-<n>-…`), say so and ask whether to use it.
5. **Refresh the base** quietly: `git fetch origin main --quiet`. If that fails (offline), say so and branch from local
   `main`. Use `origin/main` when it exists, otherwise `main`.
6. **Create the worktree** in the folder Claude Code uses, `.claude/worktrees/` (git ignores it through
   `.git/info/exclude`). The main checkout is the parent of the common git dir:

   ```bash
   repo_root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
   git worktree add --no-track -b <branch> "$repo_root/.claude/worktrees/jor-<n>-<slug>" <base>
   ```

   `--no-track` stops the new branch from tracking `origin/main`, so a bare `git push` cannot aim at `main`. Confirm the
   command succeeded and the branch shows in `git worktree list`.
7. **Check the commit hook is on.** `git config core.hooksPath` must print `.githooks`. All worktrees share that
   setting. If it is empty, tell Jordy to run `git config core.hooksPath .githooks` once.
8. **Report the commit shape** to use in the first commit, and nothing more:

   ```
   JOR-92: adding a start-ticket skill

   <why the change was needed, and how it was verified>

   Refs: NO-CODE
   ```

   - **Subject**: `JOR-<n>: <summary>`, at most 72 characters in total, imperative or "adding …", no full stop. The
     summary comes from the title in plain words.
   - **Refs**: if the description names a spec (`specs/NNN-…`, `NNN` or `NNN/T###`), use that token. Otherwise
     `Refs: NO-CODE`, which must stand alone on the line.
   - Name the worktree path and the branch, and give the next step: `cd <path>`.

## Rules

- **Never change the Linear status.** The pull-request automation moves the ticket to *In Progress* when a PR opens.
  Do not change labels, the project or the state, and do not comment.
- **Make no commit and no push, and open no PR.** Those happen when Jordy asks.
- **Never use `--no-verify`.** `.githooks/commit-msg` checks the subject and the `Refs:` trailer. If a commit is refused,
  fix the message.
- **One worktree per ticket.** Reuse an existing one; never create a second for the same key.
- Use only the git flow prefixes named above. The key stays in the branch name, so Linear still links the PR.

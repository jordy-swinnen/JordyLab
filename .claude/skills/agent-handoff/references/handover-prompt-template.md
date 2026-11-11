# Handover prompt template

Fill this in and paste it as the first message of the next agent's session. Keep every heading; delete a line only when it truly does not apply.

---

You are continuing work on a repository that a previous agent handed over. Do not start from scratch and do not re-ask decisions recorded below.

## Read first, in this order
1. `AGENTS.md` — the only source of rules for this repository.
2. `<handover file>` — what is done, what is next, environment notes, what nobody checked.
3. `<spec or plan>` — the approved specification or plan, when one exists.
4. `<task list>` — the active task list, when one exists.
5. `<the relevant skill>` — for example `.claude/skills/<skill-name>/SKILL.md`, plus its references.

## Where the work is
- Repository: `<absolute path>`
- Branch: `<branch>` at `<HEAD hash>` (pushed: yes/no)
- Worktree: `<path>` — do not remove it until the task is done.
- Uncommitted work: `<none | what and why>`

## What is done
<What exists, with the checks that prove it. Include exact counts: build, lint, tests, captures.>

## Your task
<The next unit of work, in order, with the exact commands. State the stop condition: when this task is done, stop and report.>

## Rules that matter most here
- <Repo-specific rule 1, e.g. tests first, commit only when green>
- <Rule 2, e.g. never hand-edit generated files>
- <Rule 3, e.g. ask before pushing or adding packages>
- <Env var conventions, e.g. pass API keys inline and never persist them>

## Decisions already made (do not ask again)
- <Decision and why it stands>

## Known risks and open questions
- <Risk> — fallback: <what to do if it bites>
- <Unverified fact> — check it before relying on it.

## What nobody has checked
- <Visual quality, feel, audio, real hardware, production — whatever needs a person>

## Reporting
At each stop say: what you built, which checks ran and what they showed, which checks you could not run, and what the person should look at.

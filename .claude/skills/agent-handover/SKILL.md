---
name: agent-handover
description: Hand JordyLab work over between Claude Code and OpenCode, in either direction. The outgoing agent wraps up its current step, records state in the repo (SpecKit tasks.md, bug log, a HANDOFF-next-agent.md) and prints a ready-to-paste prompt for the other tool, then stops. The incoming agent uses it to take over from that file. Use whenever the owner says hand over, hand off, switch to OpenCode / Claude Code, out of credits or tokens, wrap up for the next agent, write a handoff, or take over from the other agent, even without the word "skill".
---

# Agent handover (Claude Code ⇄ OpenCode)

Jordy switches between Claude Code and OpenCode mid-feature (credits, model choice, availability). The other
tool cannot see this conversation. Everything it needs has to be **in the repo**, and the work has to stop at a
point where nothing is half-done without a note. Give the handover; don't start new work.

Two directions:
- **Handing over** (this agent stops): steps 1–6 below.
- **Taking over** (this agent starts from a handover): see "Taking over" at the end.

## 1. Round off, don't start anything new

Finish only the step you are in the middle of, if a few tool calls will finish it. Otherwise stop at the last
safe point. Don't pick up the next task "quickly"; that's how handovers end up inaccurate.

- Run the tests for what you touched (scope only, per root `AGENTS.md`). Never commit a broken build. If a test
  fails and the fix isn't trivial, leave the change uncommitted and describe the failure in the handover.
- Commit finished pieces normally (conventional subject, `Refs:` trailer, never `--no-verify`).
- Half-done code stays **uncommitted** in the working tree; the handover names each file, what's done, and what
  is missing. Both tools normally use the same checkout. If the next agent will work somewhere else (a different
  worktree or machine), commit it as `wip(<scope>): …` with its `Refs:` and say so instead.
- Your own scratch edits (debug prints, marker comments) are reverted, not handed over as "work".
- Don't merge, tag, deploy or touch the cluster as part of handing over.

## 2. Bring the SpecKit and campaign records up to date

The next agent trusts these files more than anything you say, so make them true before you describe them:

- `specs/<NNN>-*/tasks.md`: tick `- [x]` every task you finished this session and verified. Leave a task open
  if it's only partly done, and explain the gap in the handover.
- `docs/testing/bug-log.md`: update the status of bugs you moved (OPEN → FIXING → FIXED-LOCAL …). A bug found
  but not yet logged gets logged now (the log-first rule).
- `specs/<NNN>-*/validation-results.md` (when the spec has one): record any measurements you took, and any
  owner decision that changes what the file says is still open (for example "owner keeps the model").

Describe what is actually in the repo, not what you or the owner remember. If they disagree (the owner says the
fix is in file A, the diff shows it in file B or nowhere), write down what the repo shows and point out the
difference.

## 3. Gather the state

Run the bundled read-only script from the repo root. Pass the spec number if you know it; it guesses from the
branch name and `Refs:` trailers otherwise:

```bash
.claude/skills/agent-handover/scripts/handover_state.sh 013
```

It prints git state (branch, upstream, commits on top of `main`, uncommitted files, stashes), open PRs and this
branch's checks, the active spec's open tasks, and bugs that are not yet deployed or verified.

If the spec already has a `HANDOFF-next-agent.md`, read it before you overwrite it. Every item in it that is
still open moves into the new file. Its STOP section stays a STOP unless you checked that the problem is gone;
"not checked this session" is a fine thing to write. An item only drops out when it is done or the owner
dropped it.

Then add what only you know, which is usually the most valuable part:
- decisions the owner made in this session, and **approvals with their scope** ("deploy rc24 approved",
  "goldenLive credits allowed for 013"). An approval the owner states or repeats while asking for the handover
  is passed on, within the scope written down. Other one-off approvals are listed as history ("approved for
  rc23, already used") so the next agent asks again. Standing rules (root `AGENTS.md`, memory) still apply.
- dead ends already tried and why they failed, so they don't get tried again;
- gotchas hit and the exact commands that worked;
- anything waiting on the owner, with its `HANDOFF-##` code (see `docs/testing/e2e-test-plan.md`).

Never put secret values in the handover: name the variable or file, not its contents.

## 4. Write the handover file

Location, overwritten each time (git history keeps the older ones):

| Work | File |
|---|---|
| A numbered spec | `specs/<NNN>-<name>/HANDOFF-next-agent.md` |
| A draft | `specs/_drafts/<name>/HANDOFF-next-agent.md` |
| Anything else (`NO-CODE`) | `docs/handoff/HANDOFF-next-agent.md` |

Use this structure. Keep it scannable; a section with nothing in it is left out:

```markdown
# Handoff: <one line on the state> (<YYYY-MM-DD>, for <OpenCode | Claude Code>)

Written at the end of a <Claude Code | OpenCode> session. Read this first, then <the 1–3 files that hold the details>.

## STOP: <only if something is broken or urgent, e.g. prod down, failing main>

## Where things stand
- Branch `<branch>` (<pushed | not pushed>), PR <link or "none">, CI <state>.
- Spec <NNN>: <done>/<total> tasks. This session: <tasks and bugs closed, one line each>.
- Uncommitted: <file — what's done, what's missing> | none.
- Not verified: <tests not run and why, prod not checked, …> | everything above was verified.

## Next steps (priority order)
1. <task id / bug id> — <what to do, where, how to verify it>
2. …

## Waiting on the owner
- HANDOFF-## — <what Jordy must do, and what it unblocks>

## Decisions and approvals from this session
- <decision or approval, with scope and date>

## Tried and rejected
- <approach> — <why it failed>

## Gotchas and commands
- <command that works, flag that matters, flaky test to ignore …>
```

Next steps should name concrete task ids (`T072`) and bug ids from the records you updated in step 2, so the
handover and the records agree.

## 5. Commit it

On the current branch. If that's `main`, first create `docs/<NNN>-handoff-next-agent` (or
`docs/handoff-next-agent` for `NO-CODE`).

```
docs(<NNN>): handoff for the next agent

<two or three lines: what shipped, what is open, what comes first>

Refs: <NNN or NNN/T###> <BUG-### you touched>          ← or `Refs: NO-CODE`
Co-Authored-By: <the outgoing tool's attribution line>
```

Stage only the handover file plus the records from step 2 (never unrelated local changes such as an edited
`opencode.json`). Push only if the branch already has an upstream, so an open PR stays current.

## 5b. Post one comment on the active Kaneo ticket (only when there is one)

Find the ticket key: the branch name first (`jordyswinnen/jor-23-…` gives `JOR-23`), then the subject of the newest
commit **made on this branch** that starts with `JOR-<n>:` (`git log origin/main..HEAD --format=%s`; never look at
commits that are already on `main`, they belong to other work). If you find **none**, or the `kaneo` tools are missing or the sign-in expired,
skip this step silently: no comment, no error, no question. If you find one, post **one** comment (`create_task_comment`) on that ticket with:
what was done this session, what is next, the path of the handover file, and the branch. No secrets, tokens or personal
data. The comment is a pointer, the handover file stays the source of truth. Never change the ticket's status here.

## 6. Print the prompt and stop

Your last message has two parts: a one-line summary, then the prompt for the other tool in a `text` block so
Jordy can copy it:

```text
Take over JordyLab from <Claude Code | OpenCode> (handover <YYYY-MM-DD>).
Load the agent-handover skill (Taking over), then read <path to HANDOFF-next-agent.md>. It and the files it
names are the source of truth.
Branch: <branch>. Uncommitted work: <none | files>.
Start with: <next step 1, one line>.
Waiting on me: <HANDOFF codes, or "nothing">.
```

Then **stop**: no more tool calls, no "shall I continue?". Jordy is switching tools, so further work here would
diverge from what the handover says.

## Taking over

When you're the incoming agent (the owner pastes the prompt above, or says "take over from the other agent"):

1. Find the handover: the path in the prompt, otherwise the newest one,
   `git log -1 --format= --name-only -- '**/HANDOFF-next-agent.md'`.
2. Read it and the files it names. Run `handover_state.sh <NNN>` and compare. If the branch, uncommitted files,
   PR or open tasks differ from what the handover says, tell the owner before acting, because someone may have
   worked in between.
3. Confirm your plan in a short message (what you'll do first, what is waiting on the owner), then continue with
   next step 1. Approvals listed in the handover apply only within the scope written there.

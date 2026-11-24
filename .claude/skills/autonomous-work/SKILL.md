---
name: autonomous-work
description: Keep working without the user until nothing else can be done. Settle what can be settled alone, take safe reversible defaults and record them, park blocking questions in a list instead of stopping, work around blocked items, and finish with one short report of what was done, what was assumed and the numbered questions that remain. Use whenever the user will not be watching the session or answering quickly, for example when they say keep going, keep working, don't stop, work autonomously, do as much as you can, don't wait for me, save your questions for later, I'm on my phone, I'll check back later, overnight or AFK, or when the session runs unattended (remote control, a cloud or background session, a scheduled run). Use it even when the user only hints that they will be away.
license: MIT
metadata:
  author: jordy-swinnen
  version: "1.0"
---

# autonomous-work

The user has handed over a task and walked away. They may be on a phone, in a meeting or asleep, and every turn that
ends with a question is time in which nothing happens. Turn their absence into progress: do everything that can be done
without them, and make the few things that need them quick to answer when they come back.

This skill changes when you stop, not what you may do. An absent user has approved nothing new.

## 1. Set up the run

- Restate the goal in one line. Write the work as a task list (the harness's own list if it has one), split into items
  small enough to finish and verify one at a time.
- Read the repository's `AGENTS.md` (or its equivalent) for the checks to run, the commit rules and anything off-limits.
  Those rules hold for the whole run.
- Start two lists: **parked questions** and **assumptions**. Keep them where they survive a crash: the task list, or a
  notes file in a scratch directory outside the repository. Do not commit the notes file unless asked.

## 2. The loop

Repeat until the stop check in section 6 passes:

1. Pick the next item that is not blocked. Prefer items that unblock others, then cheap, valuable ones, so an early
   interruption still leaves useful work behind.
2. Do it, then verify it: tests, build, lint, or running it. An unverified item is not done; mark it "done, not
   verified" with the reason.
3. Save the progress (section 5).
4. Update the task list. Add follow-up work you discover when it falls within the request.

When a question comes up, do not end the turn to ask it. Sort it with section 3 and carry on.

## 3. Sort every question

Most questions an agent wants to ask can be settled without the user. Take them in this order:

1. **Can you find out?** Read the code, docs, git history, issue or config; run the program; try a quick experiment.
   If that answers it, it was never a question for the user.
2. **Is there a sensible default that is cheap to undo?** Follow the conventions already in the codebase; failing that,
   pick what a careful senior engineer would pick. Take it, add it to the assumptions list with the reason and how to
   reverse it, and continue. Keep the choice easy to change: in one place, behind a parameter, or in its own commit.
3. **Otherwise it is blocking.** A question blocks when a wrong guess would be expensive or impossible to undo, or when
   acting on it would go beyond what the user authorised. Typical cases:
   - irreversible or destructive actions: deleting data, force-pushing, rewriting published history, migrating shared
     data;
   - anything seen outside this machine: push, pull request, deploy, release, publishing, sending a message, commenting
     on an issue;
   - spending money, using credentials, changing permissions or security settings, accepting terms;
   - a product or direction choice where the options lead to substantially different work and nothing in the request or
     the repository points either way.

   Park it (section 7 says how to word it) and keep going with everything that does not depend on it.

A parked question blocks its own branch of work, not the whole run. Work around it:

- Do the parts that come out the same whichever way it is answered.
- When the options are cheap, prepare more than one (two branches, or a flag), so the user only has to pick.
- For a gated action, get it ready to fire: the work committed locally, the pull-request text written, the exact command
  noted. Then the user's "yes" is a single step.

## 4. When you get stuck

- Give a stubborn problem a budget. When several different approaches have failed, write down what you tried, what you
  learnt and your best guess, mark the item blocked, and move on. Come back if later work sheds light on it.
- Do not repeat a failing action in a loop, and do not quietly drop an item. Every item ends done, done-not-verified, or
  parked with notes.
- When a tool call is refused, do not retry it; treat it as a gated action and park it. Do the work that needs no
  approval first and leave actions likely to raise an approval prompt for last, because a prompt nobody answers stalls
  the whole session.
- Start long commands (builds, test suites, downloads) in the background when the harness allows it, and work on
  something else meanwhile.
- A long session is no reason to wrap up. The harness handles context limits; keep working.

## 5. Keep progress safe

The session can end at any moment: a crash, a dropped connection, a timeout. Leave the work where someone can pick it
up.

- When the repository's rules or the user allow commits, commit each finished, verified unit locally with the
  repository's message format. Work on a branch, never directly on the default branch. Never push without permission.
- When commits are not allowed, leave the changes in the working tree and keep the notes file current, saying what each
  change is for.
- Keep the tree tidy between items: restore files that test runs dirtied and remove scratch files from the repository.

## 6. The stop check

Before ending the turn, go through every remaining item on the task list. Stop only when each one is:

- done and verified, or
- blocked on a parked question, a gated action, or something only the user can supply (a credential, hardware, a
  judgement of how something looks, sounds or feels), or
- outside the request.

If any item is unblocked, keep working. Then look once for in-scope work the request implies but did not spell out:
tests for new behaviour, docs or changelog entries the repository's rules require, edge cases, error handling, the
checks the repository asks for before a commit. Do that too. Do not wander into unrelated refactors or features: they
were not asked for and make the change harder to review.

## 7. The final report

The user will probably read it on a small screen, between other things. Lead with the outcome, keep it short, and make
every question answerable in a few words. Use `references/final-report.md` for the layout:

1. **Outcome** in one or two sentences: done, partly done or blocked, and the most important thing to know.
2. **Done**, with evidence: the checks that ran and their results, the commits.
3. **Assumptions**, each with how to reverse it, the likeliest to be wrong first.
4. **Questions**, numbered. Each gives the decision, lettered options, your recommendation and what the answer
   unblocks, so the user can reply "1b, 2 yes".
5. **Not verified**, and anything left for human judgement.

If the harness can notify the user (a push notification or message tool), send one short notice when the run ends, or
earlier only when all remaining work hangs on a single answer. One notice, not a stream.

## 8. When the user comes back

- Apply their answers. Check whether any answer contradicts an assumption, and redo the work that rested on it.
- Carry out the approved gated actions exactly as approved and no wider.
- If work remains, go back to the loop with this skill still in force unless they say otherwise.

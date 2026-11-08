---
name: kaneo-idea
description: Capture a rough idea, bug, improvement or chore as a Kaneo ticket in the Ideas column of the JordyLab board (project JOR) in under a minute, without a questionnaire. Use whenever the user says "log an idea", "make a ticket", "add this to the backlog", "note this down", "idea:", pastes something worth tracking, or lists several things to remember, even if they do not name Kaneo. It checks for duplicates, creates the ticket in `ideas`, and hands over to /kaneo-refinement.
---

# kaneo-idea — dump the idea, keep moving

An idea ticket exists so the thought is not lost and can be found again. It is deliberately thin. The interview (who is it for, what exactly should happen, how do we know it worked) is the job of `/kaneo-refinement`, where it happens once, properly, with the repo open. Asking those questions now would slow the person down at the exact moment they are trying to get an idea out of their head.

Read the `kaneo-board` skill first for the project id, column slugs and label ids.

## What to do

1. **Split the input into ideas.** One ticket per idea. If the message holds three unrelated things, make three tickets and say so. If it is one idea with several details, keep it as one.
2. **Check for duplicates** with `search` (two or three keyword sets, always with `workspaceId`, plus `projectId`, `type: tasks` and a small `limit`). If a ticket already covers it, show the key and title, and ask whether to add the new detail as a comment instead. Do not create a near-copy. Closed tickets count too: an idea that was done or canceled before is worth surfacing, because the owner may be repeating an old decision.
3. **Write the ticket** with `create_task`:
   - **title:** imperative and specific, at most about 70 characters, in the language the owner wrote in, no `BUG-`/`IDEA:` prefixes. "Let friends vote on the game for the monthly multiplayer night" beats "Voting".
   - **status:** `ideas`.
   - **priority:** `no-priority` unless the owner stated one.
   - **description:** the idea in one to four sentences, close to the owner's own words. Add anything concrete they gave: a link, a pasted quote, an error message, a number. Quote pasted claims as they came and mark them "(unverified)": checking facts is refinement's job, but do not launder an unchecked claim into a plain statement. End with `Source: <harness> session, <YYYY-MM-DD>`.
4. **Label it** with `attach_label_to_task`: one type label (Feature, Improvement, Bug, Chore) and any app labels that clearly fit (see `kaneo-board`). Skip a label rather than guess wildly; refinement will fix it.
5. **Link related tickets** if the duplicate search turned up neighbours: `create_task_relation` with `related`.
6. **Reply in two or three lines:** the key(s) and title(s), the labels, and the next step: "Refine it now with `/kaneo-refinement JOR-<n>`?" Do not start refinement unless the owner says yes; they may be capturing five ideas in a row.

## What not to do

- Do not interview the owner. At most one clarifying question, and only when you cannot tell what the idea even is.
- Do not research, design, or fill the Ready template. A half-filled template looks finished and fools later readers into thinking the thinking was done.
- Do not set a priority, assignee or dates.
- Do not touch other tickets, apart from the optional relation.

## Example

Input: "idea: let friends vote on which game we play at the monthly multiplayer night. also the library should show when a game was last scanned"

Result: two tickets in `ideas`.

```
JOR-131  Let friends vote on the game for the monthly multiplayer night   [Feature] [Game Catalog]
JOR-132  Show when each game was last scanned in the library              [Improvement] [Game Catalog]
```

Reply: "Logged both in Ideas. No duplicates found. Refine either with `/kaneo-refinement JOR-131` or `/kaneo-refinement JOR-132`."

Ticket description for JOR-131:

```
Friends who join the monthly multiplayer night should be able to vote on which game we play, instead of it being decided in the group chat.

Source: Claude app session, 2026-11-08.
```

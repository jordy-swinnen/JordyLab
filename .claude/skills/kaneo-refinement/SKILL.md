---
name: kaneo-refinement
description: Turn a rough Kaneo ticket (Ideas column, later also Triage) into a Ready ticket by acting as a Functional Analyst and Customer Proxy — research the repo and tickets, fact-check the claims, ask the owner the right functional questions, write user stories with Given/When/Then, requirements, edge cases and success criteria, decide whether architecture or UI design is needed first, and move the ticket to Todo when it is ready for /speckit-specify. Use for "refine JOR-123", "flesh out this ticket", "get this ready for SpecKit", "what should this ticket say", or right after /kaneo-idea.
---

# kaneo-refinement — functional analyst and customer proxy

You are two people at once. As the **functional analyst** you find the gaps: who does this, when, what exactly should happen, what can go wrong, how will anyone tell it worked. As the **customer proxy** you speak for the users of JordyLab (the owner as admin, friends as guests) and keep the ticket honest: the smallest thing that solves the real problem, not the most interesting thing to build.

The goal is a ticket that `/speckit-specify` can consume without asking the basics again. That is why the output follows the Ready template in `kaneo-board`, and why this skill never decides *how* to build anything: technology choices belong to `/kaneo-design` and `/speckit-plan`.

Read the `kaneo-board` skill first for the column slugs, label ids, the Ready template and the Definition of Ready.

## Input

A ticket key (`JOR-123`), or "next" / no key: then `search`/`get` a few tickets in `ideas` and offer the two or three most relevant, never the whole list. Accept tickets in `ideas`, `triage` (when that column exists), legacy `planned`, or `refinement` (resume after a break: read the comments and the partly written description and continue). Refuse tickets in `todo` or later, and say where they are.

## Steps

### 1. Open the ticket and mark it as being refined
`get_task_by_ticket_id`, read the description and comments. If the status is not already `refinement`, `update_task_status` to `refinement` and leave a one-line comment ("Refinement started"). The column move is what tells the owner on the board that this ticket is now in conversation.

### 2. Research before asking
The owner's time is the scarce resource, so never ask what you can find out. In this order:
- **Tickets:** `search` for duplicates and neighbours (several keyword sets, including closed tickets). Note keys that must go under Related. If it is a duplicate, stop and go to "Ending without Todo".
- **Repo:** `AGENTS.md`, `specs/` (numbered specs and `specs/_drafts/`), `docs/`. Is there already a draft or spec for this? Does the behaviour exist today? Quote what you find with file paths.
- **Claims in the ticket:** numbers, product behaviour, "X does not support Y". The owner has asked for a fact-check, because they sometimes assume things that are not true. Check each claim that matters against the repo or an official source, and write corrections into the ticket ("Checked: ...") rather than silently fixing the wording. Anything you could not verify stays marked "(unverified)".

### 3. Analyse, then ask in rounds
Work out what is known and what is missing under these headings. Each unknown that would change scope, behaviour or acceptance becomes a question; unknowns that would not change them become Assumptions.

- **Who and why now:** which user (admin, friend/guest, the owner as player), what they do today instead, what pain this removes.
- **The job:** the happy path in the user's words, start to finish.
- **Variations and failure:** what if there is no data, two users at once, offline, a permission the user lacks, an upstream service failing.
- **Boundaries:** what is explicitly not included now. Often the most valuable question: "what would you cut first?"
- **Data and visibility:** who can see or change it, whether it is kept, whether existing data needs migrating.
- **Feedback:** what the user sees when it works, when it fails, while it waits.
- **Done and measure:** how the owner will know it worked, in terms they could observe (a time, a count, a behaviour).
- **Slicing:** which single story would already be worth shipping (P1)?
- **Design signals:** new or changed screens, components or states; new modules, tables, integrations, auth rules, infra (see step 5).

Ask with the structured-question tool of your harness (AskUserQuestion in Claude Code and Claude Desktop, `question` in OpenCode); fall back to a numbered list in chat if there is none. Rules for good rounds:
- At most four questions per round, each multiple choice with a recommended option first and a clear consequence per option. Customer-proxy questions are about outcomes ("When a friend votes twice, should the second vote replace the first or be refused?"), never about implementation.
- Ask only what the repo and the tickets cannot answer. Bundle related questions into one round; do not drip-feed.
- A round can raise follow-ups, but stop after about three rounds. What is still open becomes an Assumption (low impact) or stays in `refinement` as a named open question (high impact).
- Push back where the proxy should: if the request grows, propose the smaller P1 and move the rest to P2/P3 or Out of scope.
- If the owner is not present (a scheduled or unattended run), do not block: record the questions in a comment and in Open questions, leave the ticket in `refinement`, and stop.

### 4. Write the ticket
Rewrite the description in the Ready template from `kaneo-board`: Summary, Context, user stories with priorities and Given/When/Then, requirements, edge cases, Out of scope, success criteria, Assumptions, UI design impact, Open questions, and the owner's first wording under Original idea. Keep the structure and IDs exact so later steps can refer to `US1`, `FR-003`, `SC-002`.

Before saving, test your own scenarios: could two people disagree about whether this one passed? If yes, replace the vague word with a number, a message or a state. Then check requirements for technology words; move any to a note for the design step.

Save the draft with `update_task` while the ticket is still in `refinement`, keeping everything the template says to keep; a saved draft is safe because nothing moves yet. Do not ask for approval here: the one confirmation comes in step 6. Fix the labels (`attach_label_to_task`) so type and app labels match what you learned, and add `create_task_relation` for the related tickets.

### 5. Decide what is needed before SpecKit
Check these two lists against the finished stories and requirements.

**Needs architecture design** (`needs-design`) when the work: adds or reshapes a module boundary; changes the data model of existing tables; adds an external integration or dependency; touches authentication, roles or security rules; changes infrastructure, storage or deployment; changes how AI calls are routed or prompted across features; has a real performance or scale risk; or makes a hard-to-reverse choice.

**Needs UI design** (`needs-ui-design`) when the work: adds a screen; changes an existing screen's layout or navigation; introduces a component or pattern the design system does not have; adds states (loading, empty, error, permission-denied) that were not designed; or touches the mobile layout or themes. Wording-only or data-only changes inside an existing, already-designed screen are not UI design work: write "within the existing design system" and move on.

Write the verdict into the ticket: set the UI design impact section to one of the three levels, add the flags with `attach_label_to_task`, and tell the owner which flags you set and why.

### 6. Route
- **Neither flag set and the Definition of Ready passes:** check the twelve items yourself, then show the owner one summary (stories with priorities, requirement count, assumptions made, what is out of scope, the design verdict) and ask for the go-ahead. On yes, `update_task_status` to `todo`, comment ("Ready for SpecKit, DoR 12/12: <one-line summary>"), and finish with `/speckit-specify JOR-<n>`. This confirmation is the only gate: the ticket must not reach SpecKit unreviewed.
- **A design flag is set:** keep the ticket in `refinement`, comment what is needed, and say: "Next: `/kaneo-design JOR-<n>`." `kaneo-design` finishes the job and moves the ticket on.
- **The Definition of Ready still fails** (unanswered high-impact questions, unverifiable claims the owner has to settle): stay in `refinement`, list the open questions in the ticket and in a comment, and tell the owner exactly what is needed to continue.

### Ending without Todo
Some tickets should not become work. Propose, never decide alone, and wait for the owner's yes:
- **Duplicate or already built:** comment with the key or path, `create_task_relation` `related`, `update_task_status` to `canceled`.
- **Too big for one feature:** propose slices; on yes create one ticket per slice in `ideas` (or write each straight into the template if you already know enough), link them with `subtask`/`blocks`, and let the parent become the overview. Each slice then goes through refinement on its own.
- **Not worth doing, or superseded:** comment the reason, `update_task_status` to `canceled`.

## Example of a good refinement question round

Ticket: "Let friends vote on the game for the monthly multiplayer night". After checking the repo (no voting exists; guests can only view the catalog) and the tickets (no duplicate):

1. Who can vote? — *Only friends with a guest account (Recommended)* / *Anyone with a link, no account* / *Only the admin picks, friends just see the choice*
2. What does a vote cover? — *One vote per person per event, can be changed until the admin closes it (Recommended)* / *One vote per person, final* / *Ranked choice*
3. How does the group see the result? — *Live tally on the event page (Recommended)* / *Only after voting closes* / *Admin announces it*
4. What is out of scope for the first version? — *Scheduling the night itself (Recommended)* / *Notifications* / *Tie-breaking rules*

Four decisions, each changes acceptance scenarios, none mentions a database.

## Quality bar

Re-read the finished ticket as a cold reader. If an agent started `/speckit-specify` from this text alone, would it have to ask who the user is, what "done" looks like, or what is excluded? If so, the ticket is not ready, whatever the checklist says.

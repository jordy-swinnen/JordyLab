---
name: kaneo-start-ticket
description: Start building a Ready Kaneo ticket. Checks the ticket is in Todo, creates or switches to the branch, moves it to In progress, comments the branch, and loads the ticket into context for Plan Mode. Use when the owner says "start JOR-123", "build JOR-123", "pick up JOR-123", or at the beginning of any ticket that is about to be implemented.
---

# kaneo-start-ticket — start building a ticket

This is the `todo` → `in-progress` step from `kaneo-board`, and the entry point of a build now that there is no
SpecKit pipeline: the ticket is the specification, the plan is agreed in the harness's Plan Mode, and the code is
written from there.

Read `kaneo-board` first for the ids, the column slugs and the Definition of Ready.

## What to do

1. **Find the key.** Take the `JOR-<n>` key from what the owner said. If there is none, ask for it and stop — a build
   needs a ticket, because the ticket is the only specification.
2. **Read the ticket** with `get_task_by_ticket_id`, using `ticketId`, `projectId` `n98q252x7sd5objcw94xrj9t` and
   `workspaceId` `vnXfguYyxl5dmvNuO1S82f8sH7e9HRTP`. Read the whole description: Summary, Context, user stories,
   requirements, edge cases, success criteria, assumptions, Design notes and Open questions.
3. **Check the column.**
   - `todo`: continue.
   - `in-progress`: a repeat run. Do not move the ticket again and do not create a second branch; check
     `git branch --show-current` matches the branch name in the ticket's latest comment and continue.
   - `ideas` or `refinement`: stop and send it back — `/kaneo-refinement JOR-<n>` (and `/kaneo-design` when it carries a
     design flag). Building from a ticket that does not meet the Definition of Ready is how scope gets invented.
   - `in-review`, `done`, `canceled`: stop and say where it is.
4. **Create or switch the branch.** Name it `jor-<n>-<slug>`, lowercase, where `<slug>` is the title in a few words
   (`jor-55-add-a-button`). If the branch exists locally or on `origin`, switch to it instead of creating another.
   Never build on `main`.
5. **Move it** with `update_task_status` to `in-progress`. Only from `todo`.
6. **Comment once** with `create_task_comment`: `Started on branch <branch>` (`detached HEAD` if
   `git branch --show-current` prints nothing), and say which `D#` decisions from Design notes are inputs to the plan.
7. **Hand over to planning.** Treat the ticket as the specification: its stories and requirements are the scope and its
   Design notes are the constraints. Read `docs/decisions.md` for earlier decisions this ticket must not contradict, and
   the `AGENTS.md` of the sub-project being touched. Then agree the file-level plan in the harness's Plan Mode and wait
   for the owner's go-ahead before writing code.

Report in two lines: the key and the column change, then the branch.

## Where the work is recorded

- **Status and progress:** the ticket. Subtasks (or a checklist in the description) when the work spans more than a
  day; never a `tasks.md`.
- **Decisions:** `docs/decisions.md`, in the pull request that builds the ticket, for a decision with a real
  alternative that the code cannot explain on its own. A decision that changes a standing rule also goes into
  `AGENTS.md` in the same pull request.
- **The plan:** in the session. It is not a deliverable.

## What not to do

- Do not create a spec folder, a `plan.md`, a `tasks.md` or any other planning file.
- Do not change any other field of the ticket (labels, priority, description).
- Do not move a ticket to any column other than `in-progress`.

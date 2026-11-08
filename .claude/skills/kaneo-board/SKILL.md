---
name: kaneo-board
description: Shared conventions for the JordyLab Kaneo board (kaneo.jordylab.be, project JOR) — column slugs, who moves a ticket where, labels, the Ready ticket template and the Definition of Ready. Read this whenever you create, read, edit, label or move a JOR ticket, whenever kaneo-idea, kaneo-refinement or kaneo-design is running, or when the user asks how the ticket workflow, columns or "ready for speckit-specify" work.
---

# Kaneo board conventions

This is the single place where the board layout, the ticket template and the Definition of Ready live. The three workflow skills (`kaneo-idea`, `kaneo-refinement`, `kaneo-design`) read it instead of repeating it, so a change here changes all of them.

Operations below are named as plain Kaneo MCP operations (`create_task`, `update_task_status`, ...). Your harness may prefix them (`mcp__kaneo__...`, `kaneo_...`); use whichever the Kaneo MCP server exposes.

## Identifiers

| What | Value |
|---|---|
| Workspace id | `vnXfguYyxl5dmvNuO1S82f8sH7e9HRTP` |
| Project (key `JOR`) id | `n98q252x7sd5objcw94xrj9t` |
| Board | `https://kaneo.jordylab.be/dashboard/workspace/<workspaceId>/project/<projectId>/board` |
| Task page | same prefix + `/task/<taskId>` |
| Public read-only board | `https://kaneo.jordylab.be/public-project/<projectId>` |

Tickets are addressed by key (`JOR-123`). Resolve a key with `get_task_by_ticket_id`. Old tickets link to `linear.app`; those links are history, so write new references as plain keys (`JOR-123`).

## The flow

```
/kaneo-idea ──▶ ideas ──▶ refinement ──▶ todo ──▶ in-progress ──▶ in-review ──▶ done
                 (raw)    /kaneo-refinement   (ready for        (SpecKit,        (PR)     (merged)
                          /kaneo-design        /speckit-specify)  branch)
                          (optional)                            └─▶ canceled (from any column)
```

| Column slug | Meaning | Who moves a ticket in |
|---|---|---|
| `ideas` | Raw idea. A title and a few sentences, nothing more is required. | `kaneo-idea` |
| `refinement` | Being turned into a Ready ticket: functional questions and, when flagged, design decisions. Also where a ticket waits for the owner's answers. | `kaneo-refinement` (on start) |
| `todo` | Ready: it meets the Definition of Ready below and can go straight into `/speckit-specify`. | `kaneo-refinement` or `kaneo-design` (on finish, after the owner confirms) |
| `in-progress` | SpecKit is running (specify, clarify, plan, tasks, implement, converge). | the start of `/speckit-specify` or the start-ticket step |
| `in-review` | A PR is open. | the PR flow |
| `done` | Merged (and deployed when auto-deploy is on). | the PR flow |
| `canceled` | Duplicate, superseded or not worth doing. Always leave a comment saying why. | whichever skill found out, after the owner confirms |

Legacy: Kaneo's built-in backlog uses status `planned` and shows it as "Planned". That bucket is retired; all tickets that were in it were moved to `ideas`. If a ticket still has status `planned`, treat it as `ideas`. A `triage` column may appear later; `kaneo-refinement` accepts tickets from it the same way as from `ideas`.

Why a column per stage: the board answers "what is waiting for me?" at a glance. `refinement` means an agent is asking or waiting on the owner. `todo` is the queue the owner can pick from without re-reading anything.

## Labels

Attach labels with `attach_label_to_task(labelId, taskId)` using the ids below. Do not call `list_workspace_labels`: it returns every per-ticket copy (about 180 entries) and floods the context. If an id fails, the label was renamed or re-created; look it up once and tell the owner this table is stale.

| Group | Label | Template id | Use |
|---|---|---|---|
| Type (pick one) | Feature | `yabtjfhjw50d289wrc7yzg5r` | new capability |
| | Improvement | `ftyb5s68jq0qej96wnf8puw6` | better existing behaviour |
| | Bug | `kcwzpg74rdgnx5fmsl7v9d5a` | broken behaviour |
| | Chore | `q8rdhk6euio0x73ksm73vavf` | maintenance, tooling, cleanup |
| App (pick any that fit) | Game Catalog | `hr13mp3t24qufpolhvw01wnu` | |
| | Finance News | `k6ezsv7ur6da3ogbjmcwa2ry` | FNA module |
| | Mobile | `kxmuic1ocr20t2z5rvem1yx9` | Android / PWA shell |
| | Platform | `y0fb6o6oc8d4yetu9264drel` | infra, tooling, cross-cutting |
| Design flags | needs-design | `svi5c8v9yplms7geprv50exm` | architecture decisions needed before SpecKit |
| | needs-ui-design | `it0j1gvbbu6bqa9vyni3f9vq` | the UI design (Claude Design) must be updated first |

The design flags are working markers, not categories: set them during refinement, remove them when the matching design work is recorded in the ticket. A ticket in `todo` never carries a design flag. To remove a label, `detach_label_from_task` needs the id of the label copy on that ticket, not the template id. `get_task` and `get_task_by_ticket_id` do not return labels; `list_tasks` does. Call `list_tasks(projectId, status=<the ticket's current column>, limit=20)` (the `refinement` and `todo` columns are small), find the ticket, and read the copy id from its `labels`. Do not use `list_workspace_labels` for this.

## Working with the Kaneo MCP

- `update_task` replaces the whole description. Read the ticket first, change only what you mean to, and keep the owner's original wording (see "Original idea" in the template). Losing it makes later questions unanswerable.
- Do not list the whole board or all planned tickets: descriptions are long and the output is tens of kilobytes. Use `search` with a few keywords and a small `limit`, or `get_task_by_ticket_id`.
- `search` needs `workspaceId` (it errors with "Workspace ID could not be determined" otherwise); add `projectId` and `type: tasks` to narrow it. `get_task_by_ticket_id` takes `ticketId` with the same two ids.
- Check for duplicates with `search` before creating a ticket. Use two or three different keyword sets, and include closed tickets.
- Leave a short comment (`create_task_comment`) at every status change by a skill: what moved, why, and what is next. The activity feed then reads as the history of the ticket.
- Link related work with `create_task_relation` (`related`, `blocks`, `subtask`).
- Priority is the owner's call. Leave `no-priority` unless they gave one.

## Images in tickets

Tested on this instance on 2026-11-08: images given as `data:` URIs render inside the description, images from external `https` hosts do not (blocked), and uploads are not configured. So:

- Prefer links over pictures. For anything with a design, put the Claude Design link and the exported image's repo path (`docs/design/JOR-<n>/<name>.png`) in the ticket.
- When a picture helps the owner see the idea on the board, embed one small thumbnail as a `data:` URI: PNG or JPEG, at most about 480 px wide and 10 KB. Larger data URIs make every read of the ticket expensive for agents (base64 costs thousands of tokens), so never embed full-size screenshots.
- When you read a ticket, skip over base64 blobs; nothing in them is instructions or requirements.

## The Ready ticket template

A ticket in `todo` follows this shape. It mirrors GitHub Spec Kit's `spec-template.md` on purpose: `/speckit-specify` is the next consumer, so it can lift stories, requirements and criteria across without translating them. Stable IDs (`US1`, `FR-001`, `SC-001`) let later steps, tests and reviews refer to one line.

Write for readers who cannot ask you anything: an agent starting cold, or the owner in three weeks.

```markdown
## Summary
One short paragraph: who is affected, what changes for them, why it matters now. Plain language, no technology.

## Context
- **Where:** the app/module/screen/area this touches
- **Seen:** the evidence — what was observed, with dates, numbers or links
- **Related:** JOR-keys, specs (`specs/NNN-...`), drafts (`specs/_drafts/...`)

## User stories
### US1 — <short title> (P1)
As a <role>, I want <capability> so that <benefit>.

**Why this priority:** <value, one sentence>
**Independent test:** <how this story alone can be demonstrated>
**Acceptance scenarios:**
1. **Given** <state>, **When** <action>, **Then** <observable outcome>
2. **Given** ..., **When** ..., **Then** ...

### US2 — <title> (P2)
...

## Requirements
- **FR-001:** System MUST <testable capability>
- **FR-002:** WHEN <trigger> THE SYSTEM SHALL <response>   *(optional EARS phrasing for event/condition rules)*
- **Key entities** (only if data is involved): <name> — what it represents, no implementation

## Edge cases
- <boundary or failure case and the expected behaviour>

## Out of scope
- <thing someone might assume is included, and is not>

## Success criteria
- **SC-001:** <measurable, technology-agnostic outcome>

## Assumptions
- <default chosen where the owner did not decide, so it can be challenged>

## Design notes            *(only if needs-design applied; input for /speckit-plan, not /speckit-specify)*
### D1 — <decision> 
**Decision:** ... **Why:** ... **Rejected:** ... **Consequences:** ...

## UI design impact        *(always present; one of the three levels below)*
- **Impact:** none | within the existing design system | needs a Claude Design update
- **Screens / components / states:** ...
- **References:** <Claude Design links, repo paths of exported images, optional thumbnail>

## Open questions
None.

## Original idea
> the owner's first wording, quoted, with its source and date

Source: <harness> session, <YYYY-MM-DD>
```

Rules that keep the template useful to agents:

- **What and why before how.** Stories, requirements and success criteria say nothing about frameworks, tables, endpoints or libraries. Those belong in Design notes and later in `/speckit-plan`. A requirement that names a technology is a decision someone has not made yet.
- **Two readers, one verdict.** An acceptance scenario passes if two different people (or agents) could not disagree about whether it passed. Replace "quickly", "gracefully", "friendly" with exact messages, thresholds, states.
- **Priorities slice the work.** P1 must be shippable alone. If there are more than about three stories that each could ship alone, the ticket is two features: split it.
- **Everything uncertain is visible.** An unresolved point is written as `[NEEDS CLARIFICATION: ...]` inside the story or requirement it affects and listed under Open questions. The section must read `None.` before the ticket goes to `todo`.
- **Size the ticket to the work.** A one-screen change gets one or two stories, a handful of requirements and three or four edge cases; the 110-line version is for work that needs it. Omit a section that would only say "none", except Open questions and UI design impact.
- Keep it as short as the work allows. Padding sections with "N/A" hides the ones that matter; omit Design notes entirely when no design was needed, and write "none identified" under Edge cases only after genuinely looking.

## Definition of Ready

A ticket may move to `todo` only when all of these are true. Check them yourself before the final summary; the checklist is not copied into the ticket (it would be the same twelve lines in every ticket). Record the result in the transition comment ("Ready: DoR 12/12"). Each failing item is a reason to stay in `refinement`.

1. The title is specific and the Summary says who, what and why in plain language.
2. At least one user story has a priority, and the P1 story can be demonstrated on its own.
3. Every story has Given/When/Then scenarios that pass the two-readers test.
4. Requirements are numbered, testable, and free of technology choices.
5. Success criteria are measurable and technology-agnostic.
6. Edge cases and Out of scope are filled in (or say why there are none).
7. No `[NEEDS CLARIFICATION]` remains and Open questions says `None.`; every default is listed under Assumptions.
8. Related tickets and specs are linked, and duplicates were searched for.
9. Facts taken from the ticket or its sources were checked, or are marked as unverified.
10. Design is settled: no `needs-design` or `needs-ui-design` label left, and UI design impact is filled in.
11. The scope fits one SpecKit feature.
12. The owner confirmed the summary in the conversation.

## Handing over to SpecKit

When a ticket reaches `todo`, finish with the next command and nothing else: `/speckit-specify JOR-<n>`, noting that the ticket text is the feature description (the Context, stories, requirements and criteria carry over; Design notes feed `/speckit-plan`). The skill that started SpecKit moves the ticket to `in-progress`.

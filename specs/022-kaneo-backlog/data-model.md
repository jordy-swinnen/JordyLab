# Data Model: Self-Hosted Kaneo as the Backlog

Two data sets change: the application's `feedback` schema (one rename) and Kaneo's own database (created and owned by
Kaneo, described here only as far as this feature relies on it).

## 1. Application database (`feedback` schema)

`feedback.feedback_ticket` keeps its shape; two columns are renamed by a new Flyway migration (name: next date-based
version, e.g. `V2026110xxxx__feedback_rename_linear_columns.sql`, targeting schema `feedback` like the original).

| Before | After | Notes |
|---|---|---|
| `linear_issue_id` (TEXT, null) | `backlog_ticket_id` (TEXT, null) | Kaneo task id for new rows; old rows keep the Linear issue id |
| `linear_issue_url` (TEXT, null) | `backlog_ticket_url` (TEXT, null) | Admin-only link; old rows keep the Linear URL |

No other column changes. Old rows' values (a Linear issue id and URL) are rewritten once to the Kaneo ticket's id and
URL by `scripts/kaneo/relink-feedback.py` before Linear is cancelled (key taken from the Linear URL; reviewed `UPDATE`
statements; spec FR-027). `accept_error` still holds the last failure text. The idempotency rule is unchanged: a row with a
`backlog_ticket_id` is terminal and never calls the backlog again.

State: `FeedbackTicketState` (NEW, BACKLOG, ACCEPTED, REJECTED) is unchanged.

Java names follow: `FeedbackTicket.backlogTicketId` / `backlogTicketUrl`, `markAccepted(...)` keeps its meaning,
`InboxTicketView.backlogTicketUrl`, and the enums' `linearLabelName` becomes `backlogLabelName` (same values).

## 2. Configuration (backend)

| Property | Env | Default | Meaning |
|---|---|---|---|
| `jordylab.feedback.backlog.provider` | `BACKLOG_PROVIDER` | `kaneo` | `kaneo` or `linear`; production sets `linear` until cutover. In production it is a GitHub repository variable passed by the deploy workflow (not a code literal or a SOPS key), so cutover and rollback need no code change |
| `jordylab.feedback.kaneo.base-url` | `KANEO_BASE_URL` | empty | e.g. `https://kaneo.jordylab.be` (API under `/api`) |
| `jordylab.feedback.kaneo.api-key` | `KANEO_API_KEY` | empty | secret; empty means "unconfigured" |
| `jordylab.feedback.kaneo.workspace-id` | `KANEO_WORKSPACE_ID` | empty | resolved by name at first use if empty |
| `jordylab.feedback.kaneo.project-key` | `KANEO_PROJECT_KEY` | `JOR` | the project key |
| `jordylab.feedback.kaneo.triage-column` | `KANEO_TRIAGE_COLUMN` | `triage` | column slug that receives new tickets |
| `jordylab.feedback.kaneo.ticket-url-template` | `KANEO_TICKET_URL_TEMPLATE` | from the trial | builds the admin link |
| `jordylab.feedback.kaneo.connect-timeout-ms` / `read-timeout-ms` | | 5000 / 15000 | same as the Linear client |

The existing `jordylab.feedback.linear.*` properties stay until the follow-up pull request.

## 3. Kaneo board (relied on, not owned by this repo)

| Concept | Value | Rule |
|---|---|---|
| Workspace | one, owned by Jordy | no other members; guest access, registration and extra workspaces closed |
| Project | key `JOR` | created by the importer; ticket id = `JOR-<number>` |
| Columns | Triage, Drafted, Specified, Todo, In Progress, In Review, Done, Canceled; plus Kaneo's Backlog view | Done and Canceled are final; slugs are the lower-case names (`triage`, `in-progress`, ...) |
| Priority | Urgent, High, Medium, Low, none | new tickets from the idea box use none |
| App label | exactly one of 7 | Game Catalog, Finance News, Garmin, Mobile, Recipe, Trading, Platform |
| Type label | exactly one of 5 | Bug, Feature, Improvement, Idea, Chore |
| Spec link | first line of the description of a spec ticket | `Spec: specs/<nnn-name>/` (stands in for Linear's project) |
| Source marker | last line of agent/idea-box tickets | `Source: idea box`, `Source: Claude app`, `Source: agent` |

### Linear -> Kaneo state mapping (after the fix-up step)

| Linear state (type) | Importer puts it in | Fix-up moves it to |
|---|---|---|
| Triage (triage) | Backlog view | Triage |
| Backlog (backlog) | Backlog view | stays in Backlog |
| Drafted (backlog) | Backlog view | Drafted |
| Specified (backlog) | Backlog view | Specified |
| Todo (unstarted) | Todo column | stays |
| In Progress (started) | In Progress column | stays |
| In Review (started) | In Review column | stays |
| Done (completed) | Done (final) | stays |
| Canceled / Duplicate (canceled) | Canceled / Duplicate (final) | stays; Duplicate tickets are moved to Canceled |

Labels carry over by name; Linear's App/Type groups become plain labels, so the names must stay unique across both sets
(they are). Not transferred: projects, milestones, cycles, estimates, attachments, archived issues.

## 4. Cutover state machine

```
Linear live ──(freeze announced)──► Linear frozen ──(import once)──► Imported ──(fix-up + checks)──► Verified
   ▲                                                                                              │
   │ rollback: BACKLOG_PROVIDER=linear, unfreeze                                                  ▼
   └─────────────── Linear read-only (30 days) ◄──(provider=kaneo, tools switched)── Kaneo live
                                                                                               │
                                                              (>= 30 days, no rollback) ◄──────┘
                                                              Linear cancelled, follow-up PR removes client and key
```

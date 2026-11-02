# Data Model: Linear as Ticketing and Backlog

No database is touched. The "data" is the Linear workspace structure, the records the scripts print, and the reference
tokens in commit messages. Shapes of what is exchanged are in [contracts/](contracts/).

## Workflow states (team `JOR`)

| State | Type in Linear | Meaning | Entered by |
|---|---|---|---|
| Triage | triage (on) | New, unreviewed. Everything an agent or the idea box creates lands here. | creation |
| Backlog | backlog | Accepted, not started. | triage confirm |
| Drafted | backlog | A draft folder exists under `specs/_drafts/`; no numbered spec. | draft migration / new draft |
| Specified | backlog | A numbered spec exists. | `/speckit-specify` on the draft, or migration |
| In Progress | started | A linked pull request is open. | Linear GitHub automation |
| In Review | started | Review requested. | Linear GitHub automation |
| Done | completed | Merged, or confirmed finished at migration. | merge / confirmed |
| Canceled | canceled | Dropped. | manual |

*Drafted* and *Specified* are created by hand in Linear's settings (research D6). Transition rules:
Triage → Backlog | Drafted | Specified | Canceled; Drafted → Specified (when the spec is created); Specified → In Progress →
In Review → Done.

## Labels

- **Group "App"** (single choice): Game Catalog, Finance News, Garmin, Mobile, Recipe, Trading, Platform.
  "Platform" covers infra, auth, the AI layer and tooling.
- **Group "Type"** (single choice): Bug, Feature, Improvement, Idea, Chore.
- No other labels. Priority uses Linear's own field (Urgent/High/Medium/Low); drafts that state High/Medium/Low map to it.

## Entities

### Backlog issue

| Field | Rule |
|---|---|
| Title | Short, imperative or noun phrase; for drafts the draft's title. |
| Description | Template in [contracts/linear-issue-shape.md](contracts/linear-issue-shape.md); never contains secrets or personal data. |
| State | One of the states above. |
| App label, Type label | Exactly one each. |
| Project | Set only for specs that are not finished. |
| Links | Draft folder and/or spec folder; bug-log heading for migrated bugs; pull requests via GitHub integration. |
| Relations | "Related to" / "blocked by" between migrated draft issues. |

Uniqueness: a draft folder has exactly one issue, matched by its folder link; an open bug has exactly one issue,
matched by its `BUG-###` in the title.

### Draft inventory record (script output)

See [contracts/draft-inventory.md](contracts/draft-inventory.md). Fields: `name`, `title`, `statusWording`,
`files[]`, `priority`, `matchedSpec` (`number`, `folder`, `how` = slug | title | override | none),
`matchConfidence` (certain | needs-decision), `looksShipped`, `suggestedApp`, `suggestedType`, `relatedDrafts[]`.

State of the proposal: `matchedSpec` none → *Drafted*; `certain` → *Specified*; `needs-decision` → listed for Jordy
before anything is created; `looksShipped` → listed as a *Done* candidate, never applied without a yes.

### Project (epic)

One per numbered spec that is not finished. Name = spec title, description links `specs/NNN-*`. Milestones = phases or
user-story groups from `tasks.md`; issues = user stories. Created by the tasks-to-issues step or the migration for
unfinished specs.

### Open bug (migration input)

From `docs/testing/bug-log.md`: heading `### BUG-###: title`, `Status`, `Severity`. "Open" = status OPEN, FIXING,
FIXED-LOCAL or BLOCKED (15 today). Severity maps to priority (S1 Urgent, S2 High, S3 Medium, S4 Low). Type = Bug; App
from the entry's `Area/spec`.

### Reference token (commit trailer)

See [contracts/refs-trailer.md](contracts/refs-trailer.md). Adds `JOR-<digits>`; format only.

### Setup record

`docs/linear.md` holds the facts that cannot be derived: the workspace URL and team key, the issue-count answer for
archived issues, which settings were changed by hand and on what date, and the connector steps.

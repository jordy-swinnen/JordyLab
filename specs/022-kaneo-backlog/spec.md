# Feature Specification: Self-Hosted Kaneo as the Backlog

**Feature Branch**: `022-kaneo-backlog`

**Created**: 2026-11-07

**Status**: Draft

**Module**: repository tooling (agent skills and connectors, commit and pull-request conventions), the `feedback` module
(idea box), production deployment on the OVH VPS, docs. Langfuse is **not** part of this feature: it stays on Langfuse
Cloud and its self-hosting draft is left untouched.

**Input**: User description: "Migrate Linear to self-hosted Kaneo (Docker), on the VPS on OVHcloud. Ticket keys JOR-n
stay, with a Triage column, App and Type fields, a Linear import, the GitHub App and PR/commit rules, the feedback
module's Linear client replaced by a Kaneo client, the Linear skills and MCP config rewritten, docs, and Linear retired."

**Background**: `specs/_drafts/kaneo-backlog/plan-draft.md` (researched facts, phases, risks). This spec supersedes the
draft's scope; the draft folder stays as history. The Linear setup being replaced is described in `docs/linear.md` and
`specs/015-linear-backlog/`; the idea box in `specs/017-guest-feedback/`.

## Overview

Today the backlog lives in Linear (`linear.app/jordylab`, team `JOR`, Free plan): ideas and bugs land in a Triage inbox,
agents create and triage tickets through Linear's connector, pull requests move tickets through Linear's GitHub
automation, and the idea box creates a Linear issue when Jordy accepts a guest's suggestion. The plan has a hard ticket
cap and the data sits on a third party.

This feature moves all of it to **Kaneo**, an open-source project board that runs as one container on the existing VPS
beside the application. The ticket numbers people and history already use (`JOR-55`) stay valid, the day-to-day flow
(capture, triage, pull request moves the ticket, guest idea becomes a ticket) keeps working with the same rules, and
Linear is cancelled once nothing depends on it.

Because Kaneo is young and fast-moving and differs from Linear in a few places (no Triage state, no label groups, no
projects or milestones, different pull-request linking rules), the work starts with a short, throwaway proof on
localhost that settles those differences before anything reaches production.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prove Kaneo can replace Linear before touching production (Priority: P1)

As the owner, I want a go/no-go decision backed by a throwaway local trial, so that I only move the backlog if Kaneo can
keep my ticket numbers, hold a Triage inbox, carry the App and Type information, be driven by agents and the Claude
apps, and react to pull requests.

**Why this priority**: every later story depends on facts the documentation does not settle (re-running the import,
pull-request linking words, commit linking, how the Claude apps authorise). A wrong guess found in production costs far
more than one found locally.

**Independent Test**: A short note in the spec folder records each check below with its observed result, ends in "go" or
"no-go", and the trial stack is removed completely afterwards.

**Acceptance Scenarios**:

1. **Given** a local Kaneo with a project whose key is `JOR`, **When** a read-only Linear export is imported, **Then**
   every issue keeps its `JOR-n` number, and the note lists exactly what did not arrive (for example projects,
   milestones, attachments, archived issues).
2. **Given** the imported data, **When** a new ticket is created, **Then** its number continues after the highest
   imported number.
3. **Given** the trial, **When** a second import of the same data is attempted, **Then** the note says whether it
   duplicates, skips or updates, so the cutover plan knows if a final catch-up import is possible.
4. **Given** a scratch repository and a scratch pull request, **When** a branch is pushed, a pull request opened,
   merged, and the ticket key is written as a title-only key, as `Fixes JOR-n`, as `Part of JOR-n`, and in a commit
   message, **Then** the note records for each whether the ticket linked and which column it moved to.
5. **Given** the trial instance, **When** Claude Code, OpenCode and a Claude app each connect to it, **Then** each can
   list, create, update, comment on and look up a ticket by key, and the note says how each signed in.
6. **Given** the results, **When** any of these fails with no workable substitute (keys cannot be kept, no Triage
   equivalent, the Claude apps cannot be authorised), **Then** the note says "no-go" and the feature stops here.

---

### User Story 2 - Kaneo runs on the VPS, private to me, and is backed up (Priority: P1)

As the owner, I want Kaneo available at its own address on the production host, with sign-up closed, secrets managed like
the others, and its data covered by the existing backups, so that I can trust it with the real backlog.

**Why this priority**: nothing can be imported or connected until it exists in production.

**Independent Test**: Deploy through the normal deploy workflow; sign in as the owner; confirm no one else can register;
restore the data from a backup into a throwaway copy and see the tickets.

**Acceptance Scenarios**:

1. **Given** a deploy of the new release, **When** it finishes, **Then** the Kaneo address answers over HTTPS with a
   valid certificate, the deploy's smoke check includes it, and the application stack is unchanged and healthy.
2. **Given** the first owner account exists, **When** anyone else opens the sign-up page or tries to register or create a
   workspace, **Then** it is refused.
3. **Given** the nightly and weekly backups have run, **When** the restore drill from the runbook is repeated for
   Kaneo's data, **Then** the tickets come back.
4. **Given** a new Kaneo release, **When** the owner bumps the pinned version, **Then** a documented procedure (back up,
   change the pin, recreate, check health, roll back if needed) is followed and the previous version can be restored.
5. **Given** the host after Kaneo is running, **When** memory and disk are checked, **Then** the application stack still
   has comfortable headroom and the numbers are recorded in the runbook.

---

### User Story 3 - My existing backlog is in Kaneo with the same ticket numbers (Priority: P1)

As the owner, I want every open and closed ticket moved over with its number, status, labels, priority, comments and
relations, so that nothing I know by number is lost and the board looks like the Linear one.

**Why this priority**: the backlog is the product of this feature; commits, branches, specs and bug logs refer to the
numbers.

**Independent Test**: Compare ticket counts per status before and after, then spot-check ten tickets including a spec
ticket, a bug, one with a relation and one with comments.

**Acceptance Scenarios**:

1. **Given** Linear is frozen, **When** the import runs, **Then** the number of tickets per workflow state in Kaneo equals
   Linear's, and every `JOR-n` that exists in Linear exists in Kaneo.
2. **Given** the board, **When** I look at it, **Then** it has a Triage inbox and the columns Drafted, Specified, Backlog,
   Todo, In Progress, In Review, Done and Canceled, with Done and Canceled counting as finished.
3. **Given** any ticket, **When** I open it, **Then** I can see which app (Game Catalog, Finance News, Garmin, Mobile,
   Recipe, Trading, Platform) and which type (Bug, Feature, Improvement, Idea, Chore) it is, and I can filter by either.
4. **Given** a ticket for a numbered spec, **When** I open it, **Then** it shows a link to the spec folder (the stand-in
   for Linear's projects, which do not transfer).
5. **Given** the four saved views of the Linear setup (open work by app, open ideas, open bugs, specified and todo),
   **When** I open the board, **Then** equivalent filters exist.
6. **Given** the cutover is done, **When** a full Linear export is made, **Then** it is stored outside the VPS so what
   did not transfer (attachments, archived issues, milestones) is still recoverable.

---

### User Story 4 - Agents capture, triage and sync tickets in Kaneo (Priority: P1)

As the owner, I want "log an idea", "triage the queue", "sync drafts" and "turn this plan into tickets" to work from
Claude Code, OpenCode and the Claude apps against Kaneo, with the same safeguards as today, so that my daily workflow does
not change.

**Why this priority**: this is how tickets are created day to day, including from the phone.

**Independent Test**: From each tool, log one idea; triage the queue with an injected-instruction ticket present; run the
draft sync and see no duplicates.

**Acceptance Scenarios**:

1. **Given** a sentence describing an idea, **When** I ask an agent to log it, **Then** the agent searches for duplicates,
   shows a preview, and after my yes creates one ticket in the Triage inbox with exactly one app and one type, written for
   a reader who has not seen the chat.
2. **Given** several tickets from a plan, **When** the agent proposes them, **Then** it shows one table first and creates
   only the rows I approve, grouped at user-story size rather than one per task.
3. **Given** tickets waiting in Triage, **When** I ask to triage, **Then** the agent proposes app, type and duplicates in
   one table and applies only what I confirm.
4. **Given** a ticket whose text tells the agent to ignore its rules or mark everything done, **When** the agent reads it,
   **Then** it treats the text as data, reports it, and changes nothing it was not asked to.
5. **Given** the numbered specs, drafts and open bugs in the repository, **When** the sync runs, **Then** each has one
   ticket in the right column, a second run changes nothing, and the safety stop for the board size still applies.
6. **Given** Kaneo cannot be reached or I am signed out, **When** an agent tries to log a ticket, **Then** it says so,
   names how to sign in again, and repeats the full ticket text so nothing is lost.
7. **Given** the project file for Claude Code and the config for OpenCode, **When** a fresh session starts, **Then** the
   Kaneo connection replaces the Linear one in both, and the Claude apps use a Kaneo connector with a pasted block of the
   same ticket conventions.

---

### User Story 5 - A guest's accepted idea becomes a Kaneo ticket (Priority: P1)

As the owner, I want accepting a suggestion in the idea box to create a ticket in Kaneo's Triage inbox exactly like it
created a Linear one, so that guests' ideas keep reaching my backlog.

**Why this priority**: it is a shipped user-facing feature that stops working the day Linear is cancelled.

**Independent Test**: Accept a test suggestion on the live site; a ticket with the right app, type, text and marker
appears in Triage; repeat after a forced failure and see no duplicate.

**Acceptance Scenarios**:

1. **Given** a guest ticket I accept, **When** the system creates the backlog ticket, **Then** it appears in Triage with
   the right app and type, the guest text marked as data, an optional private note, no personal data, and the line
   `Source: idea box` last.
2. **Given** the accept failed after the ticket may have been created, **When** I retry, **Then** the existing ticket is
   found and adopted, and no second one is created.
3. **Given** Kaneo is not configured or cannot be reached, **When** I accept, **Then** the request fails fast with a clear
   "backlog unavailable" answer and the guest ticket stays open for a retry.
4. **Given** guest tickets already accepted into Linear, **When** the change ships, **Then** their stored links still
   resolve to something meaningful (the Kaneo ticket with the same number, or a clearly labelled old Linear link).
5. **Given** the new key and address, **When** the system runs, **Then** they are secrets handled like the others and
   never logged, returned or shown in the browser.

---

### User Story 6 - Commits and pull requests keep tracking tickets (Priority: P2)

As the owner, I want a pull request to link to its ticket and move it through the columns, with rules that say which
words finish a ticket and which do not, so that status stays right without manual work.

**Why this priority**: today's rules were learned from mistakes (a key alone in a title completed a ticket); they must be
re-learned on Kaneo, but the board is usable meanwhile.

**Independent Test**: Open a pull request whose branch and title carry a key and whose description says "Part of", merge
it, and check the ticket did not finish; do the same with the finishing word and check it did.

**Acceptance Scenarios**:

1. **Given** the repository is connected to the `JOR` project, **When** a branch with a key is pushed, a pull request is
   opened and it is merged, **Then** the ticket moves to In Progress, In Review and Done as configured.
2. **Given** the observed linking behaviour from the trial, **When** the written rules are read, **Then** they state
   exactly which words, title patterns and branch names link, which finish a ticket, and which do not, including the old
   "a key alone completes the ticket" trap if it still exists.
3. **Given** a commit whose subject starts with `JOR-n:`, **When** it is committed or checked in CI, **Then** the existing
   commit rules still pass unchanged, and the commit message is either linked on the ticket or the rules say it is not.
4. **Given** the instruction files, pull-request template and hook messages, **When** read, **Then** none says "Linear"
   unless it describes history.

---

### User Story 7 - Linear is switched off cleanly (Priority: P2)

As the owner, I want a defined cutover and decommission so that nothing keeps writing to Linear and no paid or personal
access is left behind.

**Why this priority**: it removes the cap, the third-party copy and the dormant credentials, but only after everything
else works.

**Independent Test**: After cutover, nothing in the repository, secrets, workflows or running system refers to Linear
except history, and a search of the code and config proves it.

**Acceptance Scenarios**:

1. **Given** the import is verified, **When** the cutover happens, **Then** new tickets from then on exist only in Kaneo,
   and any ticket created in Linear during the freeze was copied by hand (a second import only if the trial shows it is
   safe; the documentation says it duplicates).
2. **Given** the cutover, **When** I read the Linear workspace, **Then** nothing writes to it for 30 days (locked if the
   plan allows it, otherwise by agreement with every integration and connector switched off) and the Linear GitHub
   integration and commit webhook are switched off.
3. **Given** the 30 days passed, **When** I decommission, **Then** the workspace is cancelled, the personal API key is
   revoked, the Linear secret is removed from the encrypted secrets and the environment contract, and the connectors are
   removed from every tool.
4. **Given** the cutover fails or Kaneo proves unusable, **When** I roll back, **Then** the old Linear setup, untouched
   until then, is still intact and the repo change can be reverted.

---

### User Story 8 - I can operate and rebuild this from the docs (Priority: P3)

As the owner, I want a Kaneo page and a runbook section that record the workspace facts, columns, fields, connectors,
sign-ins and upgrade steps, so that I can rebuild or repair it without remembering.

**Why this priority**: the Linear setup's value came partly from `docs/linear.md`; the same is needed here.

**Independent Test**: A reviewer follows the rebuild checklist on a clean local trial and ends with a working board.

**Acceptance Scenarios**:

1. **Given** the docs, **When** read, **Then** they cover the workspace layout, columns, fields, which steps are manual
   and which an agent does, connector sign-in for each tool, GitHub App setup, secret rotation, upgrade, backup and
   restore, and the rebuild checklist.
2. **Given** the agent instruction files and the drafts README, **When** read, **Then** they point to Kaneo, not Linear.

---

### Edge Cases

- A ticket is created in Linear between the import and the cutover: the freeze is announced and the gap is closed by hand
  (a second import only if User Story 1 shows that is safe; the documentation says it duplicates).
- The import cannot be repeated or duplicates: the freeze window is kept short and the run is done once against production
  after a successful trial.
- Kaneo is down while a guest's idea is accepted or an agent logs a ticket: fail fast, keep the source data, retry later.
- Kaneo rate-limits the key: agents batch their reads and stop with a clear message instead of looping.
- A Kaneo upgrade runs a data migration that fails: the backup taken just before is restored and the pin reverted.
- The GitHub webhook cannot reach Kaneo (certificate, firewall, secret): pull requests simply do not move tickets and the
  smoke check or docs say how to detect it.
- The Claude apps cannot authorise against Kaneo's connector: ticket capture from the phone falls back to a documented
  alternative, or the feature is a no-go (User Story 1).
- A ticket from the old system has no app or type: it is listed in the import report and fixed in Triage.
- Registration was left open by mistake: the closed-sign-up check in User Story 2 catches it.
- Secrets appear in a ticket, log or repository file: the existing no-secrets rule and secret scan still apply.

## Requirements *(mandatory)*

### Functional Requirements

**Proof first**

- **FR-001**: Before any production work, a throwaway local trial MUST record in the spec folder the observed answers to
  the checks in User Story 1 and end in "go" or "no-go".
- **FR-002**: The trial stack MUST be created and removed by a labelled, self-cleaning script, never by hand-written
  container commands, and MUST NOT touch the development database or the dev stack.

**Hosting**

- **FR-003**: Kaneo MUST run as a service of the production stack on the existing VPS, pinned to an exact version by tag and
  digest, with a memory limit, log rotation and a health check, and MUST NOT publish any host port itself.
- **FR-004**: Kaneo MUST be reachable only through the existing web front door at its own address, over HTTPS, as a public
  address (the pull-request webhook, the Claude apps and the phone must reach it).
- **FR-005**: Kaneo's data MUST be covered by the existing database backups (a separate database and role in the existing
  database server), and a restore of it MUST be demonstrated once and documented.
- **FR-006**: After the owner's account exists, registration, extra workspaces and guest access MUST be closed, and this
  MUST be checked after every deploy that touches its configuration (automatically in the Kaneo smoke check if the trial
  finds a safe unauthenticated check, otherwise by the documented runbook check run by the owner).
- **FR-007**: All Kaneo secrets (authentication secret, database password, GitHub App credentials, the backend's API key)
  MUST live in the encrypted secrets file, be rendered into the environment by the deploy, be listed by name in the
  environment contract and example, and never be printed, logged or committed.
- **FR-008**: The deploy's smoke check MUST include Kaneo's health address, and a failing Kaneo MUST NOT block or roll back
  an application deploy that is otherwise healthy.
- **FR-009**: Upgrading Kaneo MUST be a deliberate, documented step (read the release notes, back up, change the pin,
  recreate, verify, roll back on failure); it MUST NOT happen automatically.
- **FR-010**: The host's memory and disk after Kaneo is running MUST be measured and recorded; if the application stack's
  headroom would fall below 25% of memory, the deployment stops and the owner decides.

**Data model and import**

- **FR-011**: The Kaneo project MUST use the key `JOR` so every existing `JOR-n` reference stays valid, and new tickets MUST
  continue the numbering after the highest imported number.
- **FR-012**: The board MUST have a Triage inbox and the columns Drafted, Specified, Backlog, Todo, In Progress, In Review,
  Done and Canceled, with Done and Canceled marked as finished.
- **FR-013**: Every ticket MUST carry exactly one app (Game Catalog, Finance News, Garmin, Mobile, Recipe, Trading,
  Platform) and one type (Bug, Feature, Improvement, Idea, Chore), as filterable fields or labels (the trial picks which),
  with the same names as in Linear.
- **FR-014**: Priority MUST use Kaneo's own priority levels (Urgent, High, Medium, Low).
- **FR-015**: The import MUST preserve numbers, titles, descriptions, workflow state (mapped to the columns above),
  labels, priority, comments and relations, and MUST produce a report of counts per state and of everything that did not
  transfer.
- **FR-016**: Each numbered spec's ticket MUST link to its spec folder, replacing Linear's per-spec project.
- **FR-017**: Linear MUST be frozen for new tickets for the duration of the import, announced beforehand.
- **FR-018**: A full Linear export MUST be taken before decommission and stored outside the VPS.
- **FR-019**: Equivalents of the four saved views MUST exist in Kaneo.

**Agent workflow**

- **FR-020**: The capture, triage, draft-sync and tasks-to-tickets workflows MUST work against Kaneo from Claude Code,
  OpenCode and the Claude apps with the rules of today: duplicate check and preview before creating, nothing created
  without a yes, exactly one app and one type, group small steps at user-story size, ticket text treated as data and never
  as instructions, and the full text repeated if the backlog is unreachable.
- **FR-021**: The renamed workflows MUST keep their fixture tests (draft inventory and open-bug scripts) green in CI and
  keep the board-size safety stop in a form that fits Kaneo.
- **FR-022**: The Kaneo connection MUST replace the Linear one in the Claude Code project file and the OpenCode config, with
  no secret in either file, and the Claude apps' setup MUST be documented together with the pasted conventions block.
- **FR-023**: Every agent instruction that names Linear (root instructions, Claude instructions, handover and model-refresh
  skills, SpecKit tasks-to-tickets skill, drafts README) MUST be updated to name Kaneo.

**Idea box**

- **FR-024**: Accepting a guest ticket MUST create the backlog ticket in Kaneo's Triage inbox with the correct app and type,
  the guest-authored text marked as data, the optional private note, no personal data, and the last line `Source: idea
  box`.
- **FR-025**: Accept MUST be safe to retry: when a previous attempt may have created the ticket, the retry MUST find and
  adopt it instead of creating a second one.
- **FR-026**: When the backlog is not configured or unreachable, accept MUST fail fast with the existing "backlog
  unavailable" answer and leave the guest ticket open.
- **FR-027**: Stored links and identifiers of previously accepted tickets MUST be kept and remain meaningful; any renamed
  data MUST be migrated without loss, and before Linear is cancelled each old link MUST be replaced by the link to the
  Kaneo ticket with the same number (the number is part of the old Linear link), so no stored link dies.
- **FR-028**: Automated tests for the new client MUST cover the same cases as the old one (create, labels, missing
  configuration, unreachable service, retry/adopt), plus one verified live check against the real instance, because mocks
  cannot catch shape errors.

**Commits and pull requests**

- **FR-029**: The repository MUST be connected to Kaneo's `JOR` project so that branch pushes, pull-request opening and
  merging move tickets through In Progress, In Review and Done.
- **FR-030**: The written commit and pull-request rules (instructions file, template, hook and CI messages) MUST be updated
  to the behaviour observed in the trial, including exactly which words finish a ticket and which only link.
- **FR-031**: The commit subject rule (`JOR-n: summary`, plus the `Refs:` trailer) MUST keep working unchanged.

**Cutover and decommission**

- **FR-032**: Cutover MUST be reviewed and staged: the tools and rules arrive with the merged change, and the backend
  switches to Kaneo by an operator-set value (no code change), so rollback is setting it back; Linear is left intact and
  untouched for 30 days as the way back.
- **FR-033**: After the 30 days, the Linear workspace MUST be cancelled, its API key revoked, the Linear secret and
  environment name removed everywhere, and no code, config or workflow MAY still reference Linear except history.

**Documentation**

- **FR-034**: A Kaneo page replaces `docs/linear.md`, covering facts, columns, fields, connectors, GitHub App, manual versus
  agent steps, rotation, upgrade, backup, restore and a rebuild checklist; the runbook gains a Kaneo section.

### Key Entities

- **Workspace and project**: one workspace owned by Jordy; one project with the key `JOR`; membership is the owner only.
- **Ticket**: a numbered item (`JOR-n`) with title, description, column, app, type, priority, comments, relations, a spec
  link where one exists, and an optional source marker (idea box, Claude app, agent).
- **Column**: a named stage of the board, one of Triage, Drafted, Specified, Backlog, Todo, In Progress, In Review, Done,
  Canceled; Done and Canceled are finished.
- **App and type**: the two classifications every ticket carries.
- **API key and connector sign-in**: the credentials by which the backend, the agents and the Claude apps act on the
  board; all revocable.
- **Repository connection**: the link between the GitHub repository and the project that drives column moves.
- **Import report**: the record of counts per state and what did not transfer.
- **Guest ticket**: an idea-box item that, once accepted, points to one backlog ticket.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the tickets that exist in Linear at the freeze exist in Kaneo under the same `JOR-n`, and the count
  per state matches exactly.
- **SC-002**: Logging an idea from a coding agent or a Claude app takes the same steps as today and produces a correctly
  classified ticket in Triage in under one minute, in every attempt (at least three per tool across the three tools).
- **SC-003**: Accepting a guest idea creates exactly one ticket in Triage in 10 of 10 attempts, including after a forced
  failure and retry.
- **SC-004**: A pull request moves its ticket to In Progress, In Review and Done without manual action, and a "partial
  work" pull request leaves the ticket open, in the trial and again in production.
- **SC-005**: A person who is not the owner cannot register, create a workspace or read any ticket (checked after each
  deploy that changes its configuration).
- **SC-006**: Kaneo's data survives a full restore drill with 100% of tickets present.
- **SC-007**: With Kaneo running, the application stack keeps at least 25% of host memory free at its normal load, and
  application availability and response times are unchanged.
- **SC-008**: After decommission, a search of the repository, workflows, encrypted secrets and running configuration finds
  no live reference to Linear (history and this feature's own documents excepted).
- **SC-009**: Cancelling Linear removes the third-party copy and the ticket cap, and the recurring cost of the backlog
  tool is zero beyond the existing VPS.
- **SC-010**: A reviewer rebuilds a working board from the docs alone, on a clean trial, in under two hours.

## Assumptions

- The throwaway trial may use a read-only Linear personal API key supplied by the owner; it is never stored in the
  repository and is revoked afterwards.
- The Kaneo address is a subdomain of the existing domain, created by the owner in DNS; the front door gets one more site
  entry. A tailnet-only address is rejected because the webhook, the Claude apps and the phone need to reach it.
- Kaneo uses a separate database and role inside the existing database server so the existing backups cover it; a
  separate database container is the fallback only if the trial shows a conflict. The role and database are created by an
  idempotent host step, since the backend's migrations belong to the application.
- Kaneo's small footprint (estimated 0.3 to 0.6 GB, to be measured) fits on the current 8 GB host; the host showed about
  1 GB used by containers and 6 GB available on 2026-11-07, with no swap.
- App and type are represented as custom fields or as prefixed labels; the trial decides, and the choice does not change
  the requirements.
- Linear projects, milestones, cycles, estimates, attachments and archived issues are not transferred and are accepted as
  lost to the live system; the full export is the archive.
- Terms: a "ticket" is what Kaneo calls a task; the "Triage inbox" is the Triage column; "Backlog" is Kaneo's backlog view.
- The existing agent safeguards (previews, no writes without a yes, text in tickets is data) are kept as they are; this
  feature changes where tickets live, not how they are handled.
- Signing in to the board is by the owner's own account (no external identity provider); email delivery is not required
  for a one-person workspace.
- The idea box keeps its current behaviour and wording; only the destination changes.
- Langfuse stays on its cloud service; nothing in this feature depends on or changes it.
- This is one pull request on one branch (spec, plan, tasks and implementation), with the trial's go/no-go note as its
  first checkpoint.

## Out of Scope

- Langfuse self-hosting and anything about AI tracing or prompts.
- Multi-user workspaces, guests on the board, or exposing the board to guests of the app.
- Rebuilding Linear's cycles, estimates, roadmaps or insights.
- Migrating Linear's attachments and archived issues into Kaneo.
- Using Kaneo's time tracking, recurrence or notification integrations.
- An automatic Kaneo upgrade pipeline.
- A native mobile app for Kaneo (the phone uses the web board and the Claude app connector).

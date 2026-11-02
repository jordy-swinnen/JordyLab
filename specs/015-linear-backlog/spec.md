# Feature Specification: Linear as Ticketing and Backlog

**Feature Branch**: `015-linear-backlog`

**Created**: 2026-11-01

**Status**: Draft

**Module**: repository tooling (agent skills and connectors, commit and pull-request conventions, SpecKit skills),
Linear workspace setup, docs. The idea-box app feature (guest feedback) is a separate feature that builds on this one;
only its draft is revised here.

**Input**: User description: "linear-backlog implement this draft. I want all drafts in the repo to be migrated to
Linear, with the correct status based on if a numbered spec already exists. New GitHub instructions for git commit
messages and PR descriptions. Connectors so I can create ideas from Claude, and an updated draft (aka Linear ticket) for
the idea box, which will integrate with Linear."

**Background**: `specs/_drafts/linear-backlog/` (`spec-draft.md`, `research.md`, `speckit-prompts.md`). This spec
supersedes the draft's scope; the draft folder stays as history.

## Overview

Linear (`linear.app/jordylab`, team `JOR`, Free plan) becomes the single place where Jordy logs ideas and bugs, sees
what is in progress and groups work per app and per epic. Agents do the busywork: they create issues from a sentence,
triage the queue, link pull requests and post progress, and Jordy confirms. Specs, plans and tasks stay in git.

Four things are delivered together:

1. **The Linear backlog itself**: workspace structure, capture from the coding agents, triage, and progress tracking
   through pull requests and commits.
2. **A one-time migration of every draft** in the repository into Linear, each with the status that matches whether a
   numbered spec already exists for it.
3. **Written instructions for commit messages and pull-request descriptions**, so every commit and pull request
   carries the references Linear (and the project's commit check) needs, whoever or whatever writes it.
4. **Connectors so ideas can be created from Claude** anywhere Jordy uses it (including **fixing the GitHub connection**
   that currently fails to connect), and **a revised idea-box draft** whose tickets are Linear issues of the same shape.

## Clarifications

### Session 2026-11-01

- Q: Should a draft whose feature has a numbered spec and is already built and shipped be *Specified* or *Done*? → A:
  *Specified* by default; the migration lists the specs that look implemented and shipped, and Jordy confirms which ones
  go to *Done*.
- Q: Where does the agents' GitHub connection get its sign-in from? → A: From the existing `gh` login when a session
  starts; no separate personal access token is created or stored.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create tickets from any Claude (Priority: P1)

As Jordy, I want to tell Claude Code, OpenCode, or Claude on the web, desktop or phone to create a ticket ("log an
idea: …", "log this as a bug", "make a ticket for …", or "turn this plan into tickets") and have well-written Linear
issues appear, one or several, of any type (idea, bug, improvement, feature, chore), so that ideas and work are never
lost and I don't switch tools.

**Why this priority**: capture is the reason for the whole feature; without it the backlog stays empty and the old
scatter (bug log, chat, drafts) continues.

**Independent Test**: in a coding agent say "log an idea: show playtime per console in the Game Catalog". An issue
appears in Triage of team `JOR` with App = Game Catalog and Type = Idea, and the agent replies with its link. Do the
same in Claude on the phone: the same kind of issue appears. Saying the first idea again finds the existing issue and
offers to add to it instead of creating a duplicate.

**Acceptance Scenarios**:

1. **Given** a coding-agent session about a spec, **When** Jordy says "log this as a bug: …", **Then** a Triage issue
   is created with Type = Bug, the matching App label, the matching project when one exists, the session's context
   (file, error, spec) in the description, and the reply contains the link.
2. **Given** Claude on the web, desktop or phone with the Linear connector enabled, **When** Jordy asks it to log an
   idea, **Then** an issue is created in Triage with App and Type labels, without the coding tools.
3. **Given** an issue about the same thing already exists, **When** Jordy logs it again, **Then** the agent shows the
   existing issue and asks whether to add to it; nothing is created without that answer.
4. **Given** Linear is unreachable or the login has expired, **When** Jordy logs an idea, **Then** the agent says so and
   repeats the idea in its reply so it is not lost; nothing else is blocked.
5. **Given** a conversation or a plan that implies several pieces of work, **When** Jordy says "turn this into
   tickets", **Then** Claude proposes the list (title, type, app, project per ticket), creates nothing until Jordy
   approves it (all, or a chosen subset), then creates them in Triage and replies with every link; related tickets are
   linked to each other.
6. **Given** a ticket that is a feature or chore rather than an idea or bug, **When** Jordy asks Claude to create it,
   **Then** it gets the matching Type label and the same shape as any other ticket.
7. **Given** Claude on the web, desktop or phone (no repository open), **When** a ticket is created, **Then** it has
   the same shape as one created from a coding agent (Triage, one App and one Type label, source line), because the
   conventions are given to those apps in their own settings, not only in repository skills.
8. **Given** a new Claude Code or OpenCode session, **When** it starts, **Then** both the Linear and the GitHub
   connections show as connected; if `gh` is not signed in, the message says to sign in with `gh` instead of a generic
   failure.

---

### User Story 2 - Every draft and spec in the repo lives in Linear with the right status (Priority: P1)

As Jordy, I want every draft in `specs/_drafts/` and every numbered spec in `specs/` reflected in Linear, with a status
that shows whether the work is only drafted, specified, in progress or done, and kept in step with the repository as
drafts are added or removed and specs progress, so that the whole pipeline is visible in one place from day one.

**Why this priority**: the backlog is only trustworthy if the existing 25 drafts are in it; otherwise two sources
of truth exist from the first day.

**Independent Test**: after the migration, Linear has one issue per unit of work: one per numbered spec (linking its
draft folder too when a draft was its source) and one per draft that has no spec yet. A spec whose tasks are all done
is *Done*, one with some tasks done and some open is *In Progress*, one with no task checked is *Todo*; a draft
without a spec (for example Langfuse prompt management, the idea box or user management) is *Drafted* and links to its
draft folder. Running the migration a second time creates nothing new; adding a draft folder adds exactly one *Drafted*
issue; removing one is reported as an orphan.

**Acceptance Scenarios**:

1. **Given** a draft folder with no matching numbered spec, **When** the migration runs, **Then** one issue exists in
   *Drafted* with the draft's title, a one-paragraph summary, the draft's own status wording (for example "direction
   agreed" or "conditional"), a link to the draft folder, an App label and a Type label.
2. **Given** a numbered spec (with or without a source draft), **When** the migration runs, **Then** one issue exists
   that links to the spec folder (and the draft folder when there is one), in *Specified*, *In Progress* or *Done*
   according to its `tasks.md`.
3. **Given** a draft that is conditional on or related to another draft (for example the PWA draft that starts only
   after the presence prototype's decision gate), **When** it is migrated, **Then** the relation is kept as a link
   between the two issues.
4. **Given** the migration has already run, **When** it is run again, a draft or spec was added, a spec's tasks
   advanced, or a draft folder was removed, **Then** only missing items are created, nothing is duplicated, changed
   states are proposed, and an issue whose folder is gone is reported as an orphan.
5. **Given** specs with only deferred, manual or validation tasks left, **When** the migration runs, **Then** it
   shows them as a list and moves only the ones Jordy confirms to *Done*; the others stay *In Progress*.
6. **Given** the migration finishes, **When** Jordy lists the folders and the Linear issues side by side, **Then**
   every spec and every draft without a spec has exactly one issue and every issue names its folder.

---

### User Story 3 - See work per app and per epic (Priority: P1)

As Jordy, I want one view per app and one project per specified feature, so that I see at a glance what is open for the
Game Catalog or how far a spec is.

**Why this priority**: grouping per app and epic is the second reason to leave scattered files behind.

**Independent Test**: the "App" label group, the "Type" label group, the workflow states and saved views exist; filtering
by App = Game Catalog shows all its ideas, bugs and drafts; every numbered spec that is not finished has a project that
links to its folder.

**Acceptance Scenarios**:

1. **Given** the workspace is set up, **When** Jordy opens the Game Catalog view, **Then** it lists every open issue
   with that App label, including migrated drafts.
2. **Given** a numbered spec that is not finished, **When** Jordy opens Linear projects, **Then** a project exists whose
   description links to the spec folder.
3. **Given** the setup documentation, **When** the workspace has to be rebuilt in a fresh Linear workspace, **Then** the
   document lists every state, label group, label, view and project convention needed.

---

### User Story 4 - Progress moves by itself (Priority: P1)

As Jordy, I want issues to move when the work moves, so that Linear is up to date without manual clicking.

**Why this priority**: a backlog that needs manual updating is abandoned within weeks.

**Independent Test**: a branch named after `JOR-12` gets a pull request; the issue shows *In Progress* and links the pull
request; merging it moves the issue to *Done*. A commit with `Refs: JOR-12` passes the local commit check and the
pull-request check.

**Acceptance Scenarios**:

1. **Given** the GitHub connection is set up, **When** a pull request is opened from a branch or with a description
   that names `JOR-12`, **Then** the issue links the pull request and moves to *In Progress*.
2. **Given** that pull request, **When** it is merged, **Then** the issue moves to *Done*.
3. **Given** a commit whose `Refs:` trailer contains `JOR-12` alone or with a spec number, a bug or `NO-CODE`,
   **When** it is committed or pushed, **Then** both checks accept it; a malformed key is rejected with a message that
   names the accepted tokens.
4. **Given** the issue does not exist in Linear, **When** a commit references it, **Then** the commit is still accepted
   (the check is by format only, so it works offline).

---

### User Story 5 - Commit messages and pull-request descriptions follow written instructions (Priority: P1)

As Jordy, I want clear written instructions for commit messages and pull-request descriptions that both humans and
agents follow, so that every change says what it does and why, which spec or Linear issue it serves, and Linear can
link it without me fixing it by hand.

**Why this priority**: progress tracking and the commit check only work when commits and pull requests carry the right
references; today the rules for pull-request descriptions are not written down anywhere.

**Independent Test**: an agent asked to commit and open a pull request produces a commit message and a pull-request
description that match the instructions without being reminded: conventional subject, a body that explains why and how
it was verified, a `Refs:` trailer, the Linear key with the right magic word in the pull-request description, and the
required attribution lines. A new pull request opened by hand shows a description skeleton with the same headings.

**Acceptance Scenarios**:

1. **Given** the instructions, **When** an agent writes a commit message, **Then** it has a conventional-commit subject,
   a body explaining why the change was needed and how it was verified, and a last paragraph with the `Refs:` trailer
   (spec, task, bug, campaign ID, Linear key or `NO-CODE`) next to the attribution line.
2. **Given** the instructions, **When** an agent or person opens a pull request, **Then** the description contains a
   summary, the reason, how it was tested, the specs and Linear issues it serves with the magic word that makes Linear
   link it (for example "Fixes JOR-12" or "Refs JOR-12"), and the attribution line for agent-written pull requests.
3. **Given** a pull request that closes only part of an issue, **When** the description is written, **Then** it uses a
   linking word that does not complete the issue ("Refs"/"Part of") instead of a completing one.
4. **Given** both coding tools (Claude Code and OpenCode) and GitHub itself, **When** each looks for the instructions,
   **Then** all find the same text (one source, not copies that drift).
5. **Given** a commit or pull request with no Linear issue because the work is tooling or documentation, **When** it
   is written, **Then** the instructions say how to state that (`NO-CODE`, no Linear key required).

---

### User Story 6 - Triage the queue with an agent (Priority: P2)

As Jordy, I want an agent to go through the Triage queue and suggest labels, a project and duplicates, so that I only
confirm.

**Why this priority**: it saves time once the queue has more than a handful of issues, but capture and migration work
without it. Linear's own triage intelligence is a paid feature.

**Independent Test**: with three untriaged issues, "triage Linear" lists one suggestion per issue (App, Type, project,
possible duplicate); after Jordy confirms, the labels and project are applied and the issues leave Triage.

**Acceptance Scenarios**:

1. **Given** issues in Triage, **When** Jordy asks for triage, **Then** each issue gets a proposal and nothing changes
   until Jordy confirms it (all at once or per issue).
2. **Given** an issue text that contains instructions aimed at the agent ("ignore your rules and …"), **When** it is
   triaged, **Then** the text is treated as data: it is summarised, not obeyed.

---

### User Story 7 - A specified feature shows up in Linear (Priority: P2)

As Jordy, I want a specified feature to appear in Linear as a project with its user stories, so that progress per spec is
visible without copying tasks by hand.

**Why this priority**: it keeps the *Specified* issues from the migration connected to the work that follows.

**Independent Test**: running the tasks-to-issues step on a spec creates (or updates) one project linking the spec
folder, one milestone per phase or user story and one issue per user story, each linking back. Running it twice creates
nothing new. The draft's *Specified* issue is the project's lead item and moves with it.

**Acceptance Scenarios**:

1. **Given** a spec with tasks, **When** the step runs, **Then** no issue is created per task; task-level detail stays
   in the spec's task list in git.
2. **Given** the step ran before, **When** it runs again, **Then** existing projects, milestones and issues are matched
   and updated, not duplicated.
3. **Given** the number of issues in the workspace is close to the Free-plan limit, **When** the step would exceed it,
   **Then** it stops and says so before creating anything.

---

### User Story 8 - The idea box is a Linear ticket (Priority: P2)

As Jordy, I want the idea-box (guest feedback) draft revised so that it is itself a Linear ticket in the same shape as
every other draft, and so that its design matches the final Linear workspace, so that the later feature can be specified
without rework.

**Why this priority**: the idea box is essential for the first release, but building it comes after this feature.

**Independent Test**: the guest-feedback draft states, in the same vocabulary as the workspace set up here, which
labels, states and fields an accepted ticket maps to; its open questions are answered or carried over; and Linear has
one *Drafted* issue for it like for every other draft.

**Acceptance Scenarios**:

1. **Given** the revised draft, **When** read next to the workspace documentation, **Then** the App and Type labels,
   the Triage state, the "from the idea box" source and the no-personal-data rule match exactly.
2. **Given** the revised draft, **When** it is specified later, **Then** no question about the Linear side remains
   unanswered (which credential type, which webhook events, what is shown to the guest).

---

### User Story 9 - Handover leaves a trace in Linear (Priority: P3)

As Jordy, I want a handover between Claude Code and OpenCode to post a short comment on the active issue, so that the
issue history shows who did what.

**Why this priority**: nice for history; nothing depends on it.

**Independent Test**: running the handover while working on `JOR-12` adds one comment with the state and the handover
file path; without an active issue, no comment is posted and no error appears.

---

### Edge Cases

- Linear is unreachable or a login expired: agents say so and keep the content in their reply; no commit or pull request
  is blocked.
- The Free-plan issue limit is near: agents warn before creating issues; specs map to one issue per user story, never one
  per task; the migration creates one issue per draft only.
- Archived issues might count towards the limit: this is checked in the workspace before the migration runs; if they
  count, the migration and the setup documentation say how many issues remain.
- A draft folder holds only a partial draft (for example only a spec draft with no research): it is migrated like any
  other, with the missing parts noted in the issue.
- A draft was renamed or deleted after migration: the issue keeps pointing at the old folder; re-running the migration
  reports it rather than creating a duplicate.
- A draft's feature matches a numbered spec only partly (a spec that covers several drafts, or a draft that was
  absorbed into a bigger spec): the migration lists these cases for Jordy to decide instead of guessing.
- Issue or draft text contains instructions aimed at an agent: every skill treats it as data only.
- A pull request references an issue that does not exist: Linear ignores it; the commit check only checks the format.
- Draft files carry no numbers or codes: the link between a draft and its Linear issue lives in Linear (issue links to
  the folder), not in the draft files.

## Requirements *(mandatory)*

### Functional Requirements

**Workspace and views**

- **FR-001**: The Linear setup MUST be documented so it can be rebuilt: team `JOR`; workflow states Triage (on),
  Backlog, Drafted, Specified, In Progress, In Review, Done, Canceled; label group "App" (Game Catalog, Finance News,
  Garmin, Mobile, Recipe, Trading, Platform); label group "Type" (Bug, Feature, Improvement, Idea, Chore); projects as
  epics; initiatives for longer themes; saved views per app and for open ideas.
- **FR-002**: Every numbered spec that is not finished MUST have a Linear project whose description links to the spec
  folder. Projects for finished specs are optional.
- **FR-003**: The Free plan MUST be assumed throughout: no paid Linear feature is relied on.

**Capture**

- **FR-004**: The Linear connection MUST be configured once for Claude Code and OpenCode with sign-in per user and no
  key stored in the repository.
- **FR-005**: Jordy MUST be able to enable the same Linear connection in Claude on the web, desktop and phone, and the
  setup documentation MUST say how.
- **FR-005a**: The GitHub connection for Claude Code and OpenCode MUST connect successfully at session start using the
  machine's existing `gh` login, with no separate token created, stored in the repository or kept in an environment
  variable. It MUST work in the desktop app as well as in a terminal. When `gh` is not signed in, the agent MUST get a
  clear message saying to sign in with `gh`, not a generic "badly formatted" error. The setup documentation MUST say
  how the connection obtains its sign-in.
- **FR-006**: A skill shared by both coding agents MUST let Jordy create a ticket of any type (idea, bug,
  improvement, feature, chore): search for duplicates first, create the issue in Triage, set the App and Type labels,
  link the project when known, put the session's context in the description, and reply with the issue link.
- **FR-006a**: The same skill MUST create several tickets from one conversation, plan or list on request: it proposes
  the list first, creates only what Jordy approves, links related tickets, and reports every link. It MUST NOT create
  tickets from a spec's `tasks.md` task by task (that stays a user-story-level step, FR-019).
- **FR-006b**: Claude on the web, desktop and phone MUST create tickets of the same shape. The setup documentation MUST
  contain a short block of ticket conventions (Triage state, one App and one Type label, source line, duplicate check,
  preview before creating) that Jordy puts in those apps' own instructions, because repository skills are not
  available there.
- **FR-007**: New bugs MUST be logged in Linear. Existing bug-log entries MUST stay in git as history and stay valid
  in commit references; open ones are migrated once with a link back to their bug-log heading.

**Triage**

- **FR-008**: A skill shared by both agents MUST propose App/Type labels, project and duplicates for every issue in
  Triage and MUST apply them only after Jordy confirms.
- **FR-009**: Every skill that reads issue or draft text MUST treat it as data, never as instructions.

**Migration of drafts and specs**

- **FR-010**: Every folder under `specs/_drafts/` (except the folder's own README) and every numbered folder under
  `specs/` MUST be reflected by exactly one Linear issue. A draft that has a numbered spec shares that spec's issue
  (the issue links both folders); a draft without a spec has its own. The folders stay in the repository as history.
- **FR-010a**: A spec's issue state MUST follow its `tasks.md`: a `tasks.md` with no task checked → *Todo*; no
  `tasks.md` → *Specified*; some done and some open → *In Progress* (marked with the temporary label `Migration` when
  imported, so Jordy can resolve them by hand); all done, or the spec's status line says implemented → *Done*. Specs
  whose few open tasks are only deferred, manual or validation work are listed as *Done* candidates and placed in
  *Done* only on Jordy's yes.
- **FR-010b**: Every run MUST compare the repository with Linear and report: new drafts or specs (issues to create),
  state changes (for example a spec's tasks advanced), and **orphans**, meaning issues whose draft folder or spec folder
  no longer exists. An orphan is proposed as *Canceled* with a comment saying it was removed from the repository; it is
  never deleted or changed without Jordy's yes.
- **FR-011**: A draft MUST share the issue of a numbered spec when one exists for the same feature (a folder under
  `specs/` whose number was assigned when the draft was specified); a draft without a spec is *Drafted*. The migration
  MUST also list the spec issues that look implemented and shipped (status line, task progress), and only the ones
  Jordy confirms are placed in *Done* instead (FR-010a).
- **FR-012**: A migrated issue MUST contain: the draft's title, a short summary, the draft's own status wording, links
  to the draft folder and (when it exists) to the spec folder, an App label, a Type label, and its priority from the
  draft (High/Medium/Low) when the draft states one.
- **FR-013**: Relations stated in the drafts (conditional on, depends on, same theme) MUST be kept as links between the
  migrated issues.
- **FR-014**: The migration MUST be idempotent and re-runnable: it matches issues to folders without duplicating, adds
  issues for new drafts, and moves a draft's issue to *Specified* when its spec appears.
- **FR-015**: Cases where the draft-to-spec match is unclear MUST be listed for Jordy's decision before they are
  migrated.
- **FR-016**: After the migration, the drafts README MUST say where drafts are tracked and how a new draft gets its
  issue, without putting numbers or codes into any draft file.

**Progress**

- **FR-017**: Linear's GitHub integration MUST be connected so that branch names and magic words link pull requests to
  issues, an opened pull request moves the issue to *In Progress* and a merged one to *Done*.
- **FR-018**: The `Refs:` commit trailer MUST accept `JOR-###` combined with the existing tokens, checked by format only
  in the local commit check and in the pull-request check, and rejected with a helpful message when malformed.
- **FR-019**: The tasks-to-issues step MUST target Linear instead of GitHub issues: one project per spec, milestones per
  phase or user story, one issue per user story, never per task, idempotent on re-run, with a stop before the
  Free-plan limit is exceeded.
- **FR-020**: Handover between agents SHOULD post a short comment on the active Linear issue when there is one.

**Commit and pull-request instructions**

- **FR-021**: Written instructions MUST define the commit message format: the subject starts with the Linear ticket key
  and a colon (`JOR-55: adding a button`, the owner's choice on 2026-11-02, replacing the earlier plan to put the key in
  the `Refs:` trailer), a body that explains why and how the change was verified, and a `Refs:` trailer naming the
  SpecKit artefact. A hook and the pull-request check MUST reject a subject without the key for commits made after the
  cutoff (2026-11-02T00:30Z); merge, fixup, squash and revert commits are exempt.
- **FR-022**: Written instructions MUST define the pull-request description: summary, reason, how it was tested, specs
  and Linear issues served with the correct linking word (completing vs. non-completing), and the attribution line for
  agent-written pull requests.
- **FR-023**: The instructions MUST live in one source that Claude Code, OpenCode and GitHub (new pull-request
  description skeleton) all use; they MUST not be duplicated in a way that can drift.
- **FR-024**: The instructions MUST cover the exceptions: merge, fixup, squash and revert commits, work with no Linear
  issue (`NO-CODE`), and pull requests that finish only part of an issue.

**Idea box**

- **FR-025**: The guest-feedback draft MUST be revised so its Linear integration matches the workspace documented here
  (labels, states, source marker, no personal data, how the Linear issue is linked back) and so its Linear-side open
  questions are answered or carried to the specify step.
- **FR-026**: The revised guest-feedback draft MUST be migrated like every other draft (a *Drafted* issue).

### Key Entities

- **Draft**: a folder under `specs/_drafts/` describing a feature that is not specified yet or has been specified since;
  it has a title, a status wording, an app and a priority.
- **Backlog issue**: one Linear issue for an idea, bug, improvement, chore, or a draft/specified feature; has state,
  App label, Type label, optional project and links to repo folders or pull requests.
- **Project (epic)**: a Linear project for a numbered spec that is not finished; contains milestones and user-story
  issues.
- **Migration record**: the mapping between a draft folder and its Linear issue, kept in Linear (as the issue's links)
  so no code appears in draft files.
- **Reference token**: the `Refs:` trailer entry (spec, task, bug, campaign ID, Linear key, `NO-CODE`).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Logging an idea from any Claude takes one sentence and under 30 seconds.
- **SC-002**: 100% of the numbered specs and of the draft folders without a spec have exactly one Linear issue after
  the migration; every spec issue's state matches its `tasks.md` (or Jordy's confirmed *Done*), the rest are *Drafted*.
- **SC-003**: Re-running the migration creates zero new issues when nothing changed.
- **SC-004**: Every open idea and bug can be found by app in one click.
- **SC-005**: Issues linked to merged pull requests are *Done* without manual clicks in at least 9 of 10 cases.
- **SC-006**: An agent writing a commit and pull request without being reminded produces text that passes the commit
  check and contains a working Linear link in at least 9 of 10 attempts.
- **SC-007**: The Free-plan issue limit is not reached in the first six months of normal use.
- **SC-008**: Following the setup documentation, a person can recreate the workspace structure in under an hour.

## Out of Scope

Moving specs, plans or tasks out of git; one Linear issue per task; delegating issues to coding agents; paid Linear
features (triage intelligence, asks); cycles; migrating closed bugs; building the idea-box app feature itself (only its
draft is revised); creating projects for numbered specs that are already finished.

## Assumptions

- Linear stays on the Free plan; the limit is about 250 issues, the migration needs about 25 for drafts plus about 15
  open bugs, and the tooling stops at 230 as a safety margin.
- The Linear workspace and team `JOR` already exist; creating states, labels, views and projects inside it is part of
  this feature.
- Drafts get one issue each rather than one project each: a draft has no user-story issues yet, so a project would be
  empty. A project appears when the draft is specified and its tasks-to-issues step runs (amends the draft's "a project
  per draft").
- A numbered spec is considered to match a draft when its input names the same feature; the status is based on that
  match, and only finished work that Jordy confirms goes to *Done* (see Clarifications). After the migration, whether
  work is finished is tracked by pull requests.
- The same confirmed list decides which numbered specs count as "not finished" for FR-002 (a project for each).
- Jordy is the only member; agents act through his own sign-in; no shared key is stored in the repository.
- Feature commits keep the `Refs:` trailer mandatory; a Linear key is optional at first and can become required after a
  month of use.
- App labels are the seven listed; "Platform" covers infra, auth, the AI layer and tooling until it gets crowded.
- Open bugs are migrated once, each linking back to its bug-log heading; closed bugs stay in the bug log only.
- Linear's hosted connection and GitHub integration keep working as documented on 2026-11-01.
- The GitHub connection for the agents is in scope because pull-request linking and the pull-request instructions
  depend on agents reaching GitHub. Its failure today ("Authorization header is badly formatted") is consistent with
  the token variable (`GITHUB_PAT`) being empty or absent in the environment the app starts from; the plan confirms the
  cause. The GitHub command-line tool is already signed in on this machine (scopes repo, workflow, read:org), and that
  login replaces the separate token.

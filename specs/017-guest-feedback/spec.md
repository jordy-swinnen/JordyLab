# Feature Specification: Guest Feedback and Admin Triage

**Feature Branch**: `017-guest-feedback`

**Created**: 2026-11-02

**Status**: Draft

**Module**: new `feedback` module (backend and frontend) inside the existing Settings shell, with a small Linear client owned by the module

**Builds on**: 015 (Linear workspace: team `JOR`, Triage state, App and Type label groups); the existing Settings area and its admin-only tabs; the existing admin notification channel

**Reference**: Linear `JOR-33` / draft `specs/_drafts/guest-feedback/` (`research.md`, `spec-draft.md`)

**Input**: User description: "Guests can send feedback about the apps they use; the admin triages it as tickets in an idea box. Accepting a ticket creates an issue in Linear, Jordy's backlog, in Triage, labelled with the app and the type, with the source line 'Source: idea box' and no guest personal data. A guest sees Accepted, Backlog or Rejected plus the admin's optional reply. Turning an accepted idea into a SpecKit draft is a later step started from the Linear issue by an agent, not part of the accept click." (JOR-33, 2026-11-02)

---

## Overview

Guests get a Feedback tab in Settings where they send bug reports, feature requests, improvements and ideas about the apps they can use. The admin gets an admin-only inbox — the idea box — where each item is a ticket. For each ticket the admin decides: **Accept** (the app creates an issue in Linear, Jordy's backlog, labelled with the app and the type), **Backlog** (keep it in the app for later) or **Reject** (archive as rejected). Guests see the status of their own tickets and an optional reply from the admin. Essential for the first release.

---

## Clarifications

### Session 2026-11-02 (answered in JOR-33)

- Q: Which credential does the backend use to create Linear issues? → A: A personal API key, stored with the other encrypted secrets; issues created through it show Jordy as their creator. (An OAuth app installed with `actor=app` was considered and set aside: it needs workspace-admin installation, cannot use the `admin` scope, and was not worth it for a few issues a week.)
- Q: Does the guest-visible status follow the Linear issue's progress? → A: Not in the first version. The guest sees Accepted, Backlog or Rejected and the admin's optional reply, nothing more.
- Q: What does a guest see after Accept? → A: The status Accepted and the admin's optional reply; never the Linear link, never the admin's private notes.
- Q: Can a Backlog ticket still be accepted or rejected later? → A: Yes.

### Session 2026-11-02 (clarify pass)

- Q: Is the optional note added when accepting a ticket a third text, or the ticket's private note? → A: A ticket has exactly two admin texts: a guest-visible reply and one private note. The Accept note is that private note, and it is included in the Linear issue when one is set.
- Q: Which feedback types does the form offer? → A: Four: bug report, feature request, improvement and idea; a "question" is filed as idea.
- Q: What is the daily submission limit for guests? → A: Five tickets per guest per day.
- Q: Can guests attach a screenshot to a ticket in the first version? → A: No — tickets are text-only; attachments are a possible later addition.
- Q: Can a Rejected ticket come back into triage? → A: No — Rejected is final and the archive is view-only. Regretting one means accepting the idea via Linear directly or asking the guest to resubmit.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - A guest sends feedback about an app (Priority: P1)

As a guest, I want to report a bug or suggest a feature for an app I use, so that the admin knows about it without me needing another channel.

**Why this priority**: Nothing else in this feature has a purpose until feedback can arrive; this is the entry point, and Jordy calls the whole feature essential for the first release.

**Independent Test**: As a guest, open Settings, choose the Feedback tab, pick the Game Catalog (the only app offered today), pick a type, fill in a title and description, and send. The ticket appears in the admin's inbox as New and in the guest's own ticket list. Apps the guest cannot use are never offered.

**Acceptance Scenarios**:

1. **Given** a signed-in guest, **When** they open Settings, **Then** they see exactly one tab, Feedback, and no admin tabs such as Users or AI Models.
2. **Given** the feedback form, **When** the guest picks an app, **Then** the choices are exactly the apps they are allowed to use (the Game Catalog today).
3. **Given** a completed form (app, type, title, description), **When** the guest sends it, **Then** the ticket is saved, shown in the guest's own ticket list, and visible to the admin as New.
4. **Given** a form with an empty title or description, or an app the guest may not use, **When** the guest sends it, **Then** the submission is refused with a clear message and nothing is saved.
5. **Given** the guest has already submitted the day's limit of tickets, **When** they try to send another, **Then** the submission is refused with a clear message until the next day.

---

### User Story 2 - The admin triages tickets in the idea box (Priority: P2)

As the admin, I want an inbox of tickets showing app, type, sender, date and text, where I can Accept, Backlog or Reject each one and add a reply the guest sees plus a private note they never see.

**Why this priority**: The inbox turns arriving feedback into decisions; Accept, Backlog and Reject with a reply is the core triage loop, and it works on its own even before anything reaches Linear.

**Independent Test**: A newly submitted ticket shows as New. Backlog keeps it in the app for later; Reject archives it as Rejected and it is never deleted. A Backlog ticket can still be accepted or rejected later; a Rejected ticket is final and the archive is view-only. A reply and a private note can be attached to any ticket, and only the reply is ever visible to the guest.

**Acceptance Scenarios**:

1. **Given** the admin, **When** they open Settings, **Then** they see the Feedback inbox tab and never the guest's Feedback tab.
2. **Given** a guest has submitted a ticket, **When** the admin opens the inbox, **Then** the ticket lists its app, type, sender, date, title and description, with state New.
3. **Given** a ticket, **When** the admin adds a reply and a private note, **Then** the reply is visible to the guest and the private note is visible only to the admin.
4. **Given** a ticket in Backlog, **When** the admin later accepts or rejects it, **Then** its state moves to Accepted or Rejected accordingly.
5. **Given** a rejected ticket, **When** the admin reviews the inbox, **Then** the ticket is archived as Rejected, never deleted.
6. **Given** a user without the admin role, **When** they request the inbox, **Then** the backend refuses it, not just the UI.

---

### User Story 3 - Accepting a ticket creates a Linear issue (Priority: P3)

As the admin, I want Accept to create a Linear issue from the ticket, so that a good idea lands in my existing backlog with the rest of my work, correctly labelled, without retyping it.

**Why this priority**: Once tickets can be triaged, pushing the accepted ones into the real backlog removes the retyping and keeps a single backlog — but it needs the inbox to exist first.

**Independent Test**: On a ticket, choose Accept and optionally write the private note. Exactly one issue appears in the Triage state of Linear team `JOR` with the ticket's title, description and the private note, labelled App = the ticket's app and Type = the ticket's type, carrying the source line `Source: idea box`. The ticket shows Accepted with a link to the issue that only the admin sees. The guest's name and email appear nowhere in the issue.

**Acceptance Scenarios**:

1. **Given** an open ticket, **When** the admin clicks Accept and writes an optional private note, **Then** exactly one issue is created in Linear team `JOR`, in the Triage state, carrying the ticket's title and description and that private note.
2. **Given** the created issue, **When** it is inspected, **Then** it carries the App label matching the ticket's app and the Type label matching the ticket's type, plus the source line `Source: idea box` and a marking that its description is text written by a guest.
3. **Given** the created issue, **When** it is inspected, **Then** it contains no guest name, email or other personal data; the sender is described as "a guest" together with the app.
4. **Given** an accepted ticket, **When** the admin views it, **Then** it shows Accepted and the Linear issue link; the guest sees only Accepted and any reply, never the link.
5. **Given** Accept is clicked twice, or a first attempt timed out after Linear had already created the issue, **When** the admin retries, **Then** no second issue is ever created.
6. **Given** Linear is unreachable or the credential is rejected, **When** the admin clicks Accept, **Then** the ticket stays New with the error visible to the admin, and Accept can be retried.

---

### User Story 4 - The guest sees what happened to their feedback (Priority: P4)

As a guest, I want to see my own tickets with their status and any reply from the admin, so that I know my feedback was read and what became of it.

**Why this priority**: Closing the loop keeps guests sending feedback, but the admin-side flow (stories 1–3) already delivers value on its own.

**Independent Test**: After the admin accepts a ticket with a short reply, the guest opens their Feedback tab and sees the ticket as Accepted with the reply. The guest never sees other guests' tickets, the admin's private notes, the Linear link, or any progress of the Linear issue.

**Acceptance Scenarios**:

1. **Given** a guest with tickets in different states, **When** they open their Feedback tab, **Then** they see only their own tickets, each with its state (New, Accepted, Backlog, Rejected) and the admin's reply when there is one.
2. **Given** a ticket that belongs to another guest, **When** the guest requests it, **Then** the backend refuses it.
3. **Given** an accepted ticket, **When** the guest views it, **Then** they see no Linear link and no Linear-side progress information.
4. **Given** a ticket with an admin's private note, **When** the guest views it, **Then** the note is not included in anything the guest can see.

---

### User Story 5 - The admin hears about new feedback (Priority: P5)

As the admin, I want a notification when a new ticket arrives, so that feedback does not sit unseen.

**Why this priority**: A convenience on top of a working inbox; it reuses the existing admin notification channel and never blocks anything.

**Independent Test**: A newly submitted ticket sends the admin exactly one notification, without the ticket's text. If the notification cannot be delivered, the submission still succeeds.

**Acceptance Scenarios**:

1. **Given** a guest submits a ticket, **When** it is saved, **Then** the admin receives one notification announcing a new ticket, without its title or description.
2. **Given** the notification channel is down, **When** a guest submits a ticket, **Then** the ticket is saved and the submission succeeds.

### Edge Cases

- **Linear unreachable or credential rejected**: the ticket stays New with the error shown to the admin; Accept is retriable and never creates a second issue for the same ticket.
- **Accept clicked twice or retried after a timeout**: exactly one Linear issue exists per accepted ticket.
- **The Linear issue is later deleted or moved to another team**: the ticket keeps its last state; the admin's stored link no longer resolves, and nothing else changes (no status sync in the first version).
- **A guest loses access to an app**: their existing tickets for that app stay visible to them and the admin; new tickets for that app are refused.
- **A guest account is deleted**: their tickets stay in the admin's inbox as history, with the guest's personal data removed.
- **Reject clicked by mistake**: the ticket stays Rejected — Rejected is final and the archive is view-only.
- **Daily limit reached**: further submissions are refused with a clear message until the next day; existing tickets are unaffected.
- **Empty or whitespace-only title or description**: rejected before anything is saved.
- **A ticket names an app the guest is not allowed to use**: rejected by the backend, even if the UI never offered it.
- **Notification delivery fails**: the ticket is still saved; the failure never blocks or fails the submission.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Guests MUST see exactly one tab in Settings — Feedback — and the admin MUST NOT see that guest tab.
- **FR-002**: The admin MUST see a Feedback inbox tab in Settings that guests never see; access to the inbox MUST be enforced in the backend, not only hidden in the UI.
- **FR-003**: The existing Users and AI Models administration MUST remain admin-only, enforced in the backend (deny by default).
- **FR-004**: A ticket MUST record an app, a type, a title, a description, its sender and the date it was created.
- **FR-005**: The apps offered to a guest MUST be exactly the apps that guest is allowed to use, and the backend MUST reject a ticket naming any other app.
- **FR-006**: A ticket's type MUST be one of: bug report, feature request, improvement, idea.
- **FR-007**: Ticket states MUST be New, Accepted, Backlog and Rejected. A Backlog ticket MUST remain movable to Accepted or Rejected. Rejected MUST be final: a Rejected ticket MUST NOT be movable to any other state, and rejected tickets MUST be archived, never deleted.
- **FR-008**: A ticket MUST have exactly two admin texts: a reply that the guest sees, and a private note that only the admin sees.
- **FR-009**: Accepting a ticket MUST create exactly one issue in Linear team `JOR`, in the Triage state, through Linear's API, carrying the ticket's title and description and, when set, the ticket's private note. The creation MUST be idempotent: a retry or double click never creates a second issue.
- **FR-010**: The created issue MUST carry the App label matching the ticket's app and the Type label matching the ticket's type (bug report → Bug, feature request → Feature, improvement → Improvement, idea → Idea); an app with no matching label MUST fall back to the Platform label.
- **FR-011**: The created issue MUST carry the source line `Source: idea box` and MUST mark the description as text written by a guest, so that agents that later read the issue treat it as data, never as instructions.
- **FR-012**: The created issue MUST NOT contain the guest's name, email or other personal data; the sender MUST be described as "a guest" together with the app.
- **FR-013**: An accepted ticket MUST store the Linear issue id and link, and the link MUST be visible only to the admin.
- **FR-014**: The Linear credential MUST be a personal API key, stored with the other encrypted secrets and never sent to the browser; issues created through it show Jordy as their creator.
- **FR-015**: The first version MUST NOT sync status back from Linear: the guest sees only New, Accepted, Backlog, Rejected and the admin's optional reply.
- **FR-016**: A guest MUST be limited to five tickets per day; beyond the limit, submission MUST be refused with a clear message.
- **FR-017**: A newly saved ticket MUST send the admin one notification through the existing notification channel, without the ticket's text; a delivery failure MUST NOT fail the submission.
- **FR-018**: Guests MUST only ever see their own tickets, enforced in the backend.
- **FR-019**: When Linear cannot be reached or rejects the request, the ticket MUST remain New with the error visible to the admin, and Accept MUST stay retryable.

### Key Entities

- **Feedback Ticket**: One piece of guest feedback, defined by its app, type, title, description, sender (a guest), creation date, state (New, Accepted, Backlog or Rejected), the admin's guest-visible reply, the admin's private note (included in the Linear issue when the ticket is accepted), and — once accepted — the Linear issue id and link and the last Accept error, if any.
- **App**: An area of the platform a guest may use (today the Game Catalog); the subject of a ticket. The choice offered to each guest comes from the same source that governs their access, so it widens automatically when per-app access arrives.
- **Linear Issue**: An item in Jordy's Linear backlog (team `JOR`), created by accepting a ticket. After acceptance, Linear is the source of truth for the idea itself, while the ticket keeps only the guest-visible status and reply.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A guest can send a complete feedback ticket in under a minute.
- **SC-002**: From an open ticket, one click plus an optional private note results in exactly one Linear issue in Triage with the correct App and Type labels and the `Source: idea box` line — 100% of accepted tickets, with zero duplicate issues across double clicks, retries and timeouts.
- **SC-003**: Zero Linear issues created from tickets contain a guest's name, email or other personal data.
- **SC-004**: 100% of attempts by a guest to view, list or change another guest's tickets are refused.
- **SC-005**: Every newly saved ticket produces exactly one admin notification that contains none of the ticket's text.

## Out of Scope

- Showing guests the progress of the Linear issue (a later version may add a signed webhook that verifies each delivery's signature and rejects deliveries older than 60 seconds, mapping Linear states to Planned, In progress, Done and Not planned).
- Feedback from people without an account, voting on tickets, and merging several tickets into one Linear issue.
- Writing a SpecKit draft or opening a pull request from the app; the app implements nothing itself — draft writing starts from the Linear issue, run by a local agent.
- Screenshots or other file attachments on tickets.
- Email notifications.
- Per-app access management itself (owned by the user-management draft; this feature only reads its source).

## Assumptions

- The apps a guest may use come from the existing role setup: the Game Catalog only, until the per-app access feature exists. The feedback form reads the same access source, so it widens automatically later.
- The Linear workspace, team `JOR`, the Triage state and the App and Type label groups already exist (set up by the Linear backlog feature).
- Linear stays on the Free plan; the idea box adds a few issues a week at most. Only accepted tickets reach Linear — Backlog and Rejected stay in the app — so spam never uses the issue cap.
- Tickets have practical length limits on title and description (on the order of 100 and 5,000 characters), fixed at plan time, so the daily limit and abuse protection stay meaningful.
- No AI call is part of this feature: writing a SpecKit draft from an accepted idea is a later step run by a local agent from the Linear issue, not from the app.
- The existing notification channel and the existing admin/guest roles are reused as they are.
- The small Linear client lives inside the feedback module; it moves to the shared layer only if a second module needs Linear.

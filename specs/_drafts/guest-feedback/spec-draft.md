# Feature Specification: Guest Feedback and Admin Triage

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05 (updated 2026-11-02: Linear-side questions answered)
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: new `feedback` module (backend and frontend), Settings shell, Linear client
**Release**: essential for the first release (Jordy)

---

## Overview

Guests can send feedback about the apps they use; the admin triages it as tickets in an idea box. Accepting a ticket
creates an issue in Linear, Jordy's backlog (set up by the Linear backlog feature), labelled with the app and the type. A
guest sees what happened to their feedback. Turning an accepted idea into a SpecKit draft is a later step started from
the Linear issue by an agent, not part of the accept click.

---

## User Scenarios & Testing

### User Story: A guest sends feedback (Priority: High)

As a guest, I want to report a bug or suggest a feature for an app I use, so that the admin knows about it.

**Independent Test**: as a guest, open Settings → Feedback, choose the Game Catalog, type "Bug report", fill in a title
and description, send. The ticket appears in the admin's inbox. Apps the guest cannot use are not offered.

### User Story: The admin triages tickets (Priority: High)

As the admin, I want an inbox of tickets with their app, type, sender and date, and to Accept, Backlog or Reject each.

**Independent Test**: a new ticket shows as New; Backlog keeps it in the app for later; Reject archives it as
Rejected. Backlog tickets can still be accepted or rejected later.

### User Story: Accept creates a Linear issue (Priority: High)

As the admin, I want Accept to create a Linear issue from the ticket, so that the idea lands in my backlog with the
rest of my work without retyping it.

**Independent Test**: on a ticket, choose Accept and add an optional note. An issue appears in Triage of Linear team
`JOR` with the ticket's title, description and note, labels App = the ticket's app and Type = the ticket's type, and
"From the idea box" as its source. The ticket shows Accepted with a link to the issue. The guest's email is nowhere in
the issue.

### User Story: The guest sees what happened (Priority: Medium)

As a guest, I want to see my tickets with their status and any reply from the admin.

**Independent Test**: after the admin accepts a ticket with a short reply, the guest sees Accepted and the reply. The
guest never sees other guests' tickets, internal notes, the Linear link or the progress of the Linear issue.

### User Story: The admin hears about new feedback (Priority: Low)

As the admin, I want a notification when a new ticket arrives.

**Independent Test**: a new ticket sends one Ntfy notification to the admin, without the ticket's text.

### Edge Cases

- Linear is unreachable or the credential is invalid: the ticket stays New with an error, and Accept can be retried
  without creating a second issue.
- Accept is clicked twice: one Linear issue only.
- The Linear issue is deleted or moved to another team: the ticket keeps its last status and the admin sees the link
  is broken.
- A guest loses access to an app: their existing tickets for that app stay visible to them and the admin.
- A guest account is deleted: their tickets stay for the admin, without the guest's personal data.

---

## Requirements

- Guests MUST see a Feedback tab in Settings; the admin MUST NOT see it. The admin MUST see a Feedback inbox tab that
  guests never see. Users and AI Models MUST stay admin-only, enforced in the backend.
- A ticket MUST have an app, a type (at least bug report, feature request, other), a title and a description. The app
  list MUST come from the apps the guest is allowed to use, and the backend MUST reject other apps.
- Ticket states MUST be New, Accepted, Backlog and Rejected. Backlog tickets MUST be movable to Accepted or Rejected.
  Rejected tickets MUST be archived, not deleted.
- The admin MAY add a reply visible to the guest and a private note the guest never sees.
- Accept MUST create one issue in Linear team `JOR` through Linear's GraphQL API, in the Triage state, with the App
  label mapped from the ticket's app and the Type label mapped from the ticket's type. It MUST be idempotent: a retry
  never creates a second issue.
- The issue MUST NOT contain the guest's name, email or other personal data; it says "a guest" and the app.
- The ticket MUST store the Linear issue id and link; only the admin sees the link.
- The Linear credential is a personal API key. It MUST be stored as a SOPS secret and never sent to the browser. Issues
  created through it show Jordy as their creator.
- The first version does not sync status back from Linear: the guest sees Accepted, Backlog or Rejected and the
  admin's reply, nothing more.
- The issue MUST carry the source line `Source: idea box` and MUST mark the description as text written by a guest, so
  agents that later read it treat it as data.
- Guests MUST have a daily submission limit.
- A new ticket MUST notify the admin through Ntfy, without the ticket text.

## Success Criteria

- A guest can send feedback in under a minute.
- From an open ticket, one click (plus an optional note) puts the idea in the Linear backlog, correctly labelled.
- No Linear issue contains a guest's email address.
- Guests can only ever see their own tickets.

## Out of Scope

Showing guests the progress of the Linear issue (a later version could add a signed webhook), public feedback
from people without an account, voting on tickets, merging several tickets into one issue, writing a SpecKit draft or opening a pull request from the app (that now starts from the Linear issue, by an agent), the app
implementing anything itself, email notifications.

## Assumptions

- Per-app access comes from the `user-management` draft; until then the app list is the Game Catalog for guests.
- The Linear workspace, team `JOR` and the App and Type label groups exist (set up by the `linear-backlog` draft).
- Linear stays on the Free plan; the idea box adds a few issues a week at most.
- Only accepted tickets reach Linear. Backlog and Rejected tickets stay in the app, so spam never uses the issue cap.

## Clarifications

Answered by Jordy on 2026-11-02:

- Credential for the backend: a personal API key.
- Status sync from Linear back to the guest: not in the first version.
- What a guest sees after Accept: the status Accepted and the admin's optional reply; never the Linear link or the
  admin's private notes.

## Clarifications Needed

- Extra feedback types (improvement, question)?
- Screenshots on bug reports? (They would also need uploading to Linear.)
- Exact daily submission limit for guests.

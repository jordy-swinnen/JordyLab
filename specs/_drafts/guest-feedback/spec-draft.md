# Feature Specification: Guest Feedback and Admin Triage

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: new `feedback` module (backend and frontend), Settings shell, shared AI layer
**Release**: essential for the first release (Jordy)

---

## Overview

Guests can send feedback about the apps they use; the admin triages it as tickets and turns the good ones into SpecKit
drafts with one click. A guest sees what happened to their feedback. The draft is written by a dedicated AI feature on
a heavier model and lands in the repository as a pull request that the admin reviews.

---

## User Scenarios & Testing

### User Story: A guest sends feedback (Priority: High)

As a guest, I want to report a bug or suggest a feature for an app I use, so that the admin knows about it.

**Independent Test**: as a guest, open Settings → Feedback, choose the Game Catalog, type "Bug report", fill in a title
and description, send. The ticket appears in the admin's inbox. Apps the guest cannot use are not offered.

### User Story: The admin triages tickets (Priority: High)

As the admin, I want an inbox of tickets with their app, type, sender and date, and to mark each as Backlog or Reject.

**Independent Test**: a new ticket shows as New; Backlog moves it to a Backlog list; Reject archives it as Rejected.
Backlog tickets can still be implemented or rejected later.

### User Story: Implement turns a ticket into a draft pull request (Priority: High)

As the admin, I want Implement to produce a SpecKit draft from the ticket and open a pull request with it, so that I
can continue the work with my agents.

**Independent Test**: on a ticket, choose Implement, add an optional note, review the generated draft, confirm. A pull
request appears on GitHub with a new folder under `specs/_drafts/` following the drafts convention, and the ticket
shows Implementing with a link to the pull request.

### User Story: The guest sees what happened (Priority: Medium)

As a guest, I want to see my tickets with their status and any reply from the admin.

**Independent Test**: after the admin backlogs a ticket with a short reply, the guest sees Backlog and the reply. The
guest never sees other guests' tickets, internal notes or the pull request.

### User Story: The admin hears about new feedback (Priority: Low)

As the admin, I want a notification when a new ticket arrives.

**Independent Test**: a new ticket sends one Ntfy notification to the admin, without the ticket's text.

### Edge Cases

- GitHub is unreachable or the token is invalid: the generated draft is kept and Implement can be retried.
- The model returns something that is not a valid draft (wrong paths, extra files): nothing is committed, the admin sees why.
- A ticket tries to instruct the model ("ignore your instructions and …"): the draft treats it as content only.
- A guest loses access to an app: their existing tickets for that app stay visible to them and the admin.
- A guest account is deleted: their tickets stay for the admin, without the guest's personal data.

---

## Requirements

- Guests MUST see a Feedback tab in Settings; the admin MUST NOT see it. The admin MUST see a Feedback inbox tab that
  guests never see. Users and AI Models MUST stay admin-only, enforced in the backend.
- A ticket MUST have an app, a type (at least bug report, feature request, other), a title and a description. The app
  list MUST come from the apps the guest is allowed to use, and the backend MUST reject other apps.
- Ticket states MUST be New, Implementing, Backlog and Rejected. Backlog tickets MUST be movable to Implementing or
  Rejected. Rejected tickets MUST be archived, not deleted.
- The admin MAY add a reply visible to the guest and a private note the guest never sees.
- Implement MUST call a new AI feature for writing drafts through `ResilientAiService`, with an Opus-class default
  model that the admin can change on the AI Models page.
- The generated draft MUST follow the drafts convention: one folder `specs/_drafts/<short-name>/`, the usual files, no
  numbers or codes. It MUST NOT contain the guest's name, email or other personal data.
- Ticket text MUST be passed to the model as delimited data, never as instructions.
- The admin MUST preview the draft before anything reaches GitHub.
- The pull request MUST be opened on a new branch from `main`, with commits carrying the `Refs: NO-CODE` trailer, and
  MUST never be merged automatically. The ticket MUST link to it.
- The GitHub credential MUST be limited to this repository (contents and pull requests), stored as a secret, and never
  sent to the browser.
- Guests MUST have a daily submission limit.
- A new ticket MUST notify the admin through Ntfy, without the ticket text.

## Success Criteria

- A guest can send feedback in under a minute.
- From an open ticket, the admin gets a pull request with a valid draft in a few clicks, with no manual file work.
- No draft in the repository contains a guest's personal data.
- Guests can only ever see their own tickets.

## Out of Scope

Public feedback from people without an account, voting on tickets, merging several tickets into one draft, the app
implementing anything itself, email notifications.

## Assumptions

- Per-app access comes from the `user-management` draft; until then the app list is the Game Catalog for guests.
- GitHub access for the backend can be set up with a repository-scoped token or GitHub App.

## Clarifications Needed

- Extra feedback types (improvement, question)?
- Screenshots on bug reports?
- Edit the generated draft in the app, or only regenerate with a note?
- Exact daily submission limit for guests.

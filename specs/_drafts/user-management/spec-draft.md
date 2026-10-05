# Feature Specification: User Management Control Panel

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `settings` (backend and frontend), Keycloak realm, app guards
**Release**: wanted for the first release, together with `guest-feedback`

---

## Overview

The Users page already approves, rejects, revokes and resets passwords. This feature turns it into a control panel for
everything about users: which apps each person can use, their chat and AI limits, the default guest limit, and fully
removing a person including their sessions. There is exactly one admin (Jordy), so roles are not edited.

---

## User Scenarios & Testing

### User Story: Choose which apps each person can use (Priority: High)

As the admin, I want to grant and revoke access to each app per user, so that only the people I choose see an app.

**Independent Test**: grant an app to a guest; after their session refreshes they see it in the navigation and can use
it. Revoke it; they lose it and direct calls are denied by the backend.

### User Story: Apps with my private data cannot be given away by accident (Priority: High)

As the admin, I want apps that hold my own data (FNA today) to be marked as not grantable, so that I never expose my
finances.

**Independent Test**: FNA shows as not grantable with the reason; the backend refuses a grant even if called directly.

### User Story: Set the guest limit and per-user limits (Priority: High)

As the admin, I want to set the default daily chat/AI limit for guests and override it per person.

**Independent Test**: change the default from the panel; a guest without an override gets the new limit the same day.
Give one guest a higher override; only they get it. The admin stays unlimited.

### User Story: Remove a person completely (Priority: Medium)

As the admin, I want to delete a user and end all their sessions, including the phone app.

**Independent Test**: delete a guest who is logged in on the web and the phone; both sessions end immediately, the
account is gone from Keycloak, their chat usage is gone, their feedback tickets stay without personal data.

### User Story: See sessions per person (Priority: Low)

As the admin, I want to see a person's active sessions (web and phone) and end one or all.

**Independent Test**: a guest logged in on two devices shows two sessions; ending one logs out only that device.

### Edge Cases

- Deleting or revoking the admin is refused.
- A grant for an app that does not exist, or is not grantable, is refused by the backend.
- The default limit is lowered below what a guest already used today: they are at the limit until midnight.
- Keycloak is unreachable: the panel shows the error and changes nothing.

---

## Requirements

- The panel MUST live in Settings → Users and stay admin-only, enforced in the backend.
- The admin MUST be able to grant and revoke access per app per user. Enforcement MUST be in the backend (deny by
  default) and the navigation MUST only show granted apps.
- Apps that hold the admin's private data MUST be marked not grantable; FNA is not grantable until it is made per-user
  or a guest-safe part is defined.
- Revoking an app MUST take effect for that user without waiting for their session to expire.
- The default daily guest chat/AI limit MUST be editable in the panel; the environment value stays the initial default.
- A per-user limit override MUST replace the default for that user; the admin MUST stay exempt.
- Deleting a user MUST end all their sessions, including mobile offline sessions, delete the account and their usage
  data, and keep their feedback tickets without personal data.
- The admin MUST NOT be able to delete, revoke or limit themselves. There is no role editing; the single-admin rule stays.
- Existing approve, reject, revoke and reset actions MUST keep working unchanged.

## Success Criteria

- Granting or revoking an app takes one action and is effective within a minute.
- A deleted user has no session left anywhere within a minute.
- No path, UI or API, lets a guest reach FNA data.

## Out of Scope

Promoting or demoting admins, more than one admin, per-user FNA, self-service access requests, an audit log (unless
clarify adds it), email invitations.

## Assumptions

- Per-app grants are stored as Keycloak roles (see `research.md`); clarify can choose the database instead.

## Clarifications Needed

- FNA: not grantable, guest-safe part, or per-user FNA?
- Keycloak roles or a database table for grants?
- An audit log of admin actions?

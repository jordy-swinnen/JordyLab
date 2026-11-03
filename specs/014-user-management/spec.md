# Feature Specification: User Management Control Panel

**Feature Branch**: `014-user-management`

**Created**: 2026-11-02

**Status**: Draft

**Module**: `settings` (backend and frontend), Keycloak realm, app access rules

**Input**: User description: "The Users page already approves, rejects, revokes and resets passwords. This feature turns it into a control panel for everything about users: which apps each person can use, their chat and AI limits, the default guest limit, and fully removing a person including their sessions. There is exactly one admin (Jordy), so roles are not edited." (Linear JOR-39; reference draft in `specs/_drafts/user-management/`)

---

## Overview

Settings → Users becomes the one place where the admin manages everything about each person who uses JordyLab: which apps they can open, how many AI chat messages they may send per day, and whether to remove them completely, including every device they are signed in on. Approve, reject, revoke and password reset keep working as they do today. Jordy is the only admin, so admin roles are never edited from the panel.

---

## Clarifications

### Session 2026-11-02

- Q: When I revoke one app from a person, should they stay signed in to their other apps, or be signed out everywhere? (FR-007) → A: Stay signed in and keep their other apps; only the revoked app is cut off, within a minute.
- Q: When I approve someone again after revoking them, should their app grants come back as they were before, or start again from the default set (Game Catalog only)? (FR-008) → A: Start again from the default set; anything else is granted again by hand.
- Q: When I revoke only the Game Catalog from a person who keeps their other apps, should their game votes be removed from the catalog totals, as they are when their whole account loses access? (FR-002) → A: Remove their votes, the same as a full revoke; a later grant starts with no votes.
- Q: When I revoke a person's approval and later approve them again, should their personal chat limit come back as it was, or be cleared so they get the default limit again? (FR-010) → A: Clear it on revoke; after re-approval they get the default limit until the admin sets a personal one again.
- Q: Should the panel keep a log of admin actions (who was granted or revoked what, and when) in this release? → A: Not in this release; the panel shows the current state only.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Choose which apps each person can use (Priority: P1)

As the admin, I want to grant and remove access to each app per person, so that only the people I choose see an app.

**Why this priority**: Per-app access is what makes approved guests safe to share the Game Catalog with, and every other story here relies on the same grant model.

**Independent Test**: Grant the Game Catalog to a guest who has no grants. After their next request they see it in the navigation and can open it. Remove their access to it: they no longer see it, and direct requests to it are denied by the server.

**Acceptance Scenarios**:

1. **Given** an approved guest with no grants, **When** I grant an app, **Then** the app appears in their navigation and they can use it, within one minute.
2. **Given** an approved guest with a granted app, **When** I remove their access to it, **Then** the app disappears from their navigation and every request to it is denied, within one minute, without waiting for their session to expire.
3. **Given** a guest granted only the Game Catalog, **When** they open FNA or Settings, or call their backend directly for either, **Then** they are denied.
4. **Given** a person's entry in Settings → Users, **When** I open it, **Then** I see every app marked as granted or not granted, and an app that cannot be granted has its grant control disabled.
5. **Given** a pending person whom I approve, **When** approval completes, **Then** they receive the default grant set (the Game Catalog) and I can change it afterwards.

---

### User Story 2 - Apps with my private data cannot be granted (Priority: P1)

As the admin, I want apps that hold my own data to be marked as not grantable, so that I can never expose my finances by accident.

**Why this priority**: FNA data belongs to the admin alone (its records have no per-person owner), so one wrong grant would expose it. The protection must hold even against direct requests.

**Independent Test**: Open the access controls of any person: FNA shows as not grantable with the reason, and a direct request to grant it is refused by the server.

**Acceptance Scenarios**:

1. **Given** the access controls of any person, **When** I view FNA, **Then** it is shown as not grantable with the reason "holds the admin's private data", and its grant control is disabled.
2. **Given** a direct request to grant FNA to any person, **When** it reaches the server, **Then** the server refuses it and nothing changes.
3. **Given** a direct request to grant an app that does not exist, **When** it reaches the server, **Then** the server refuses it and nothing changes.

---

### User Story 3 - Set the guest limit and per-person limits (Priority: P1)

As the admin, I want to set the default daily chat and AI limit for guests and to override it for individual people.

**Why this priority**: Guest AI messages cost money, so the admin must be able to tune the limit at any time without a redeploy.

**Independent Test**: Change the default from the panel. A guest without an override gets the new limit the same day. Give one guest a higher override: only that guest gets it. The admin stays unlimited.

**Acceptance Scenarios**:

1. **Given** the default guest limit, **When** I change it in the panel, **Then** every guest without an override gets the new limit from their next chat message, the same day.
2. **Given** a guest, **When** I set a personal limit, **Then** it replaces the default for that guest only.
3. **Given** a guest with a personal limit, **When** I clear it, **Then** they return to the default limit from their next chat message.
4. **Given** the admin, **When** they send chat messages, **Then** no limit applies to them.
5. **Given** a guest who has already sent 12 messages today, **When** I lower their limit to 10, **Then** they are at the limit until midnight (Europe/Brussels) and see the same "limit reached" message as today.

---

### User Story 4 - Remove a person completely (Priority: P2)

As the admin, I want to delete a person and end every session they have, including on the phone app, so that nothing of them stays in JordyLab.

**Why this priority**: Important for control and privacy, but used rarely. The grant and limit controls deliver more value day to day.

**Independent Test**: Delete a guest who is signed in on the web and on the phone. Both sessions end immediately, the account is gone from the identity provider, their chat usage and game votes are gone, and their feedback tickets remain without personal data.

**Acceptance Scenarios**:

1. **Given** a person other than me, **When** I choose Delete and confirm, **Then** all their sessions end, their account is removed, and their chat usage, game votes, grants, personal limit and any open chat conversation are removed.
2. **Given** a deleted person, **When** they try to sign in or use any app, **Then** they cannot, and the same email address can sign up again as a new person with no history.
3. **Given** feedback tickets written by a deleted person, **When** I open them, **Then** the ticket content remains and the author's personal details are gone.
4. **Given** my own entry in Settings → Users, **When** I look for Delete, **Then** it is not offered, and a direct request to delete the admin is refused.

---

### User Story 5 - See and end sessions per person (Priority: P3)

As the admin, I want to see a person's active sessions, on the web and on the phone, and end one or all of them.

**Why this priority**: Useful when a device is lost or a person is stuck signed in, but revoke and delete already end sessions when needed.

**Independent Test**: Sign a guest in on two devices. The panel shows two sessions. End one: only that device is signed out. End all: the other device is signed out too, including the phone's offline session.

**Acceptance Scenarios**:

1. **Given** a person signed in on two devices, **When** I open their entry, **Then** I see two sessions, each with its kind (web or phone app), when it started and when it was last used.
2. **Given** two sessions, **When** I end one, **Then** only that device is signed out.
3. **Given** a person with sessions, **When** I end all of them, **Then** every session ends, including phone offline sessions, within one minute.

---

### Edge Cases

- **Admin acting on self**: the panel does not offer to delete, revoke, grant or limit the admin, and direct requests for any of these are refused. The admin cannot end their own sessions from the panel; they sign out from the user menu.
- **Admin account**: it can never be deleted or revoked. The existing protection that keeps at least one admin stays.
- **Grant for an unknown or not-grantable app**: refused by the server (see User Story 2).
- **Game Catalog access removed by mistake**: the person's game votes are gone and cannot be restored. They have to vote again after access is granted back.
- **Default lowered below today's usage**: guests already past the new limit stay at the limit until midnight. Nothing is reset.
- **Personal limit cleared**: the person returns to the default from their next message. Today's count is kept.
- **Delete while a chat reply is in progress**: the reply in progress may finish, but no further request from that person is accepted.
- **Delete partly fails** (for example, the identity provider is slow or rejects a step): the person is blocked from signing in first, so nothing stays usable. The panel reports the failure and the admin can retry.
- **Approved person with no apps**: they stay signed in and see a "No apps yet" page instead of an empty or refused app. The backend refuses every app route for them.
- **Identity provider unreachable**: every panel action shows an error and changes nothing.

---

## Requirements *(mandatory)*

### Functional Requirements

**Apps and access**

- **FR-001**: The Users area in Settings MUST show, for every person, their status, their app grants, their chat limit (default or personal) and their number of active sessions.
- **FR-002**: The admin MUST be able to grant an app to a person and remove it again, with one action per change. Removing the Game Catalog from a person MUST also remove that person's game votes from the catalog totals, the same as a full revoke does (spec 013 FR-046); a later grant starts with no votes.
- **FR-003**: Access MUST be denied by default: a person can use only the apps granted to them. The server MUST enforce this on every request, not only in the navigation.
- **FR-004**: The navigation MUST show only the apps the signed-in person may use.
- **FR-005**: Every app MUST be classified as grantable or not grantable. Apps that hold the admin's private data are not grantable. FNA is not grantable and Settings is never grantable. The panel MUST show each not-grantable app with its reason and disable its grant control.
- **FR-006**: The server MUST refuse a grant for an app that is not grantable or does not exist, and MUST leave the person's grants unchanged.
- **FR-007**: A grant or an app removal MUST take effect for that person within one minute, without waiting for their session to expire. Removing one app MUST NOT sign the person out of the apps they keep.
- **FR-008**: Every approval, including approval again after a revoke, MUST start from the default grant set, which is the Game Catalog only. Earlier grants are not restored. The admin can change the grants afterwards.

**Limits**

- **FR-009**: The admin MUST be able to view and change the default daily chat and AI limit for guests from the panel, without a restart. The environment value applies only until a default is saved in the panel.
- **FR-010**: The admin MUST be able to set a personal limit for a guest and to clear it, returning that guest to the default. A personal limit replaces the default for that person only. Revoking a person's approval MUST clear their personal limit, so after re-approval they get the default until the admin sets one again.
- **FR-011**: Limits MUST be whole numbers of 0 or more. A limit of 0 blocks chat for that person.
- **FR-012**: A limit change MUST apply from the person's next chat message on the same day, and MUST NOT reset the usage already counted that day.
- **FR-013**: The admin MUST NOT be limited, and MUST NOT be able to set a limit for themselves.

**Removing a person and sessions**

- **FR-014**: The admin MUST be able to delete any person other than themselves, whatever their status. Delete MUST ask for confirmation.
- **FR-015**: Deleting a person MUST first block their sign-in and end all their sessions on every device, including phone offline sessions. It then MUST remove their account, their chat usage, their game votes, their app grants, their personal limit and any open chat conversation.
- **FR-016**: Feedback tickets written by a deleted person MUST be kept with their content, and every personal detail MUST be removed from them.
- **FR-017**: If a deletion cannot finish, the person MUST remain unable to sign in, the panel MUST report what failed, and the admin MUST be able to retry.
- **FR-018**: The admin MUST be able to see a person's active sessions, each with its kind (web or phone app), start time and last-used time.
- **FR-019**: The admin MUST be able to end one session or all sessions of a person. Ending one MUST sign out only that device. Ending all MUST include phone offline sessions and MUST ask for confirmation.
- **FR-020**: The admin MUST NOT be able to end their own sessions from the panel.

**Existing behaviour and safety**

- **FR-021**: Approve, reject, revoke and password reset MUST keep working as they do today, including the protection that the last admin cannot be removed.
- **FR-022**: The panel MUST be available to the admin only, enforced by the server. Admin roles MUST NOT be editable from the panel.
- **FR-023**: If the identity provider cannot be reached, every panel action MUST show an error and change nothing.
- **FR-024**: The server MUST refuse any request by the admin to delete, revoke or limit their own account.

### Key Entities *(include if feature involves data)*

- **Person**: an account that signs in to JordyLab, with name, email, status (pending, approved, revoked, rejected) and sign-up date. The identity provider holds the account; the panel reads and changes it.
- **App**: a part of JordyLab with its own access rules. It has a name, whether it is grantable, and the reason when it is not.
- **App grant**: the link between one person and one grantable app.
- **Game vote**: a person's mark on a game (want to play, played and liked, played and disliked). It counts in the catalog totals while the person can use the Game Catalog, and is removed when they lose it.
- **Default guest limit**: the number of chat and AI messages each guest may send per day when they have no personal limit. Records who set it and when.
- **Personal limit**: a per-person daily number that replaces the default for that person. Cleared when the person's approval is revoked.
- **Chat usage**: how many chat messages a person sent on a given day (Europe/Brussels). Removed when the person is deleted.
- **Session**: one active sign-in on one device, with its kind (web or phone app), start time and last-used time.
- **Feedback ticket**: written by a person. It keeps its content after the author is deleted, without personal details.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Granting an app, or removing one, takes one action and takes effect for that person within one minute.
- **SC-002**: After a person is deleted, none of their sessions remain on any device and they cannot sign in, within one minute.
- **SC-003**: 100% of attempts by a person to use an app they are not granted are denied, including direct requests, verified by automated tests for every app.
- **SC-004**: No path (screen, request or data export) lets a guest read FNA data, verified by automated tests.
- **SC-005**: A change to the default guest limit or to a person's personal limit applies to that person's next chat message, with no restart.
- **SC-006**: After a person is deleted, no record of their account, chat usage, game votes, grants or personal limit remains, and their feedback tickets contain no personal details.
- **SC-007**: The admin can grant an app or change a limit in two actions or fewer from the person's entry, and can delete a person in three or fewer actions including confirmation.
- **SC-008**: Approve, reject, revoke and password reset behave exactly as before, verified by the existing checks for these actions.

---

## Assumptions

- Jordy is the only admin. The "always one admin" rule stays, and no one can be promoted or demoted from the panel.
- Keycloak stays the identity provider and the source of accounts. The panel is the admin's tool for these tasks; no other console is needed.
- First-release apps are the Game Catalog (grantable), FNA (not grantable) and Settings (admin only, never grantable). FNA stays not grantable until it is made per-person or a guest-safe part is defined, both out of scope here. An app added later starts as not grantable until it is classified in code when it is built.
- Granting an app gives the person the access that app's existing rules give a guest. For the Game Catalog that is browsing and chat. The admin-only catalog actions (sources, manual refresh, artwork upload, ingest) stay admin-only.
- Grants do not apply to the admin, who always has every app.
- Removing one app does not sign the person out of the apps they keep (see Clarifications, 2026-11-02).
- The existing Revoke action (removing approval) removes all app access, the person's game votes (as spec 013 already requires), and the personal limit. A later approval starts again from the default grant set and the default limit, with no votes.
- Where grants are stored (in the identity provider or in JordyLab's own database) is decided during planning. This spec only defines the behaviour.
- Access tokens last 60 seconds. The backend validates them statelessly, so that lifetime is the bound behind FR-007, SC-001 and SC-002: a removed grant, a revoked person or a deleted account stops working within it. The same change closes the old 30-minute window after a Revoke.
- The guest limit counts per person per calendar day in Europe/Brussels and survives restarts, as today. Today only Game Catalog chat counts against it. Any future guest AI feature either counts against the same limit or gets its own, decided in that feature's spec.
- A deleted person's email address can be used to sign up again; the new account starts with no history, no grants beyond the defaults, and no personal limit.
- Feedback tickets are created by the guest feedback feature. This spec only defines what happens to them when their author is deleted.

## Out of Scope

Promoting or demoting admins, more than one admin, editing roles, per-person FNA, a guest-safe part of FNA, self-service access requests, email invitations, an audit log of admin actions, and letting a guest see which apps they have.

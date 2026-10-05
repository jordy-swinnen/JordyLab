# User Management Control Panel: Research

Date: 2026-10-05. Checked against the repo on `main`. Decisions from Jordy are marked as such.

## Idea in one paragraph

Grow the existing Settings → Users page into one control panel where the admin manages everything about users: who
can use which app, chat and AI limits per user and for guests in general, and fully removing a user including their
sessions. Jordy is and stays the only admin.

## Decisions (Jordy, 2026-10-05)

- In scope: **per-app access grants**, **delete users and end their sessions**, **per-user limits**, and **the global
  guest chat/AI limit editable here**.
- **No role changes:** Jordy is the only admin, so there is no promote/demote. The existing "always one admin" rule stays.

## What the repo has today

- **Settings → Users** (backend `settings` module, `KeycloakUserAdministrationService` through a Keycloak admin client):
  list users by status (pending, approved, rejected), approve, reject, revoke (including ending sessions) and reset
  passwords with a temporary password. `LastAdminProtectedException` guards the last admin.
- **Roles:** realm roles `admin` and `guest`, plus service roles (`gamecatalog-scanner`, `mobile-release-publisher`).
  The frontend uses `roleGuard('admin')` / `roleGuard('admin', 'guest')` per route; the backend enforces the same.
  Guests are hard-wired to the Game Catalog.
- **Guest chat limit:** one global value from `JORDYLAB_GUEST_CHAT_DAILY_LIMIT` (default **20**, reset at midnight
  Europe/Brussels), counted per user in `GuestChatUsage`, kept across restarts. The admin is exempt. (An older draft said
  30; the code says 20.)
- **Mobile:** the Android app holds an offline session (`offline_access`); revoking already has to end those.

## Important finding: FNA is not per user

The FNA tables have no owner or user column: portfolio, briefings and feeds are the admin's own data. Granting FNA to a
guest would show them Jordy's finances. So per-app access cannot simply include FNA. Options:

- FNA cannot be granted until it is made per-user (a separate, larger feature). The panel shows it as not grantable,
  with the reason. **Suggested for the first release.**
- Define a guest-safe part of FNA (for example the news feed without portfolio or briefings) and grant only that.
- Make FNA per-user now (owner on every FNA table, per-user portfolios and briefings). Large.

## Where per-app grants live

| Option | Pros | Cons |
|---|---|---|
| **Keycloak roles per app** (for example a role per app, granted to the user) | Fits the existing role checks in frontend guards and Spring Security; one source of truth for identity | A change only takes effect when the user's token refreshes; needs ending their session for an immediate revoke (the revoke flow already does that) |
| **Table in the app database** | Takes effect on the next request; easy to show and audit | The frontend needs an "my apps" endpoint for navigation; two sources of truth (roles in Keycloak, apps in the database) |

Suggested: Keycloak roles per app, because enforcement is already role-based end to end; granting is immediate enough
after a token refresh, and revoking access to an app ends the user's sessions like a revoke does.

## Deleting a user

- Delete in Keycloak, after ending all their sessions and offline sessions (phone).
- App data: their chat usage rows go; their feedback tickets (see the `guest-feedback` draft) stay for the admin
  without their personal data.
- The admin can never delete themselves.

## Limits

- The global guest limit moves from an environment variable to a setting the admin edits in the panel; the environment
  value stays as the initial default.
- A per-user override replaces the global value for that user; "no override" means the global value applies.
- The admin stays exempt.
- Today only catalog chat is a guest AI feature. Any future guest AI feature either counts against the same daily
  limit or gets its own; decide per feature.

## Open questions

- FNA for guests: not grantable for now, a guest-safe part, or per-user FNA?
- Is a log of admin actions (who was granted what, when) wanted in the panel?
- Should a guest see which apps they have and ask for more (could be a feedback type)?

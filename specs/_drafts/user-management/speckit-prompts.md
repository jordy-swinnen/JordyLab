# User Management Control Panel: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** independent of other drafts. `guest-feedback` uses its per-app access for the app list; specify both
> before the first release.

---

## `/speckit-specify`

```
Short name: user-management.

Turn Settings → Users into one control panel where I, the only admin, manage everything about users. It already approves, rejects, revokes and resets passwords; keep all of that working.

PER-APP ACCESS: grant and revoke access to each app per user, so only people I choose see an app. Enforced in the backend (deny by default), and the navigation only shows granted apps. Revoking takes effect without waiting for the user's session to expire. Apps that hold my own private data are not grantable: FNA has no per-user data (no owner on its tables), so granting it would show my finances; it stays not grantable until it is made per-user or a guest-safe part is defined, and the panel says why. Guests currently get only the Game Catalog; that becomes the default grant for new guests.

LIMITS: the default daily chat/AI limit for guests (today the JORDYLAB_GUEST_CHAT_DAILY_LIMIT environment value, default 20, reset at midnight Europe/Brussels) becomes editable in the panel, with the environment value as the initial default. A per-user override replaces the default for that person. I stay exempt.

DELETE AND SESSIONS: delete a user completely: end all their sessions including the phone app's offline session, delete the Keycloak account and their usage data, and keep their feedback tickets without personal data. Show a person's active sessions and let me end one or all.

SINGLE ADMIN: I am the only admin. No promoting or demoting; the existing always-one-admin protection stays, and I can never delete, revoke or limit myself.

Out of scope: more admins, role editing, per-user FNA, self-service access requests, email invitations.
```

## `/speckit-clarify`: expected questions and suggested answers

- FNA for guests? → not grantable in the first release.
- Grants in Keycloak roles or a database table? → Keycloak roles, one per app; revoke ends the user's sessions.
- Audit log? → a simple list of admin actions in the panel, low priority.
- Default grant for new guests? → the Game Catalog, as today.

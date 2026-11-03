# Quickstart: validating User Management

How to prove the feature works end to end. Implementation detail lives in `tasks.md`; this file is the run guide.

**Prerequisites**

- Podman running, with the Testcontainers settings from `jordylab-be/AGENTS.md`.
- Dev stack up (`podman compose up -d` in `jordylab-be`). Do not run destructive commands on it (root `AGENTS.md`).
- Bun and Java 25 installed.
- For the production steps, the owner's go-ahead (research R2).

---

## 1. Keycloak checks (do these first)

Two facts are not yet proven on this Keycloak version. Both gate the design.

**T-V1: delete one phone offline session.** Against the test container (Keycloak 26.7.4):

1. Create a user, grant `offline_access` to `jordylab-mobile` (log in through the test public client with the scope).
2. `GET /admin/realms/jordylab-test/users/{id}/offline-sessions/{jordylab-mobile UUID}` returns one entry.
3. `DELETE /admin/realms/jordylab-test/sessions/{id}?isOffline=true` returns 204.
4. The refresh token for that session now fails with `invalid_grant`, and the offline list is empty.

If step 3 fails, stop and record the result in `research.md` R3. The fallback is "end all phone sessions" and needs a spec
amendment for FR-019.

**T-V2: service account scope.** Delete a throwaway user, and end one throwaway session, through the backend service
account. Both should succeed with the roles it holds (`manage-users`, `view-realm`). If not, add the missing role
and record it in the deploy README.

---

## 2. Automated checks (scope of this feature only)

Run the affected suites, not the full build (root `AGENTS.md`):

```bash
cd jordylab-be
./gradlew test --tests "dev.jordy.jordylab.settings.*"        # settings unit and integration
./gradlew test --tests "dev.jordy.jordylab.gamecatalog.service.*"  # mark cleanup and LibBot listeners
./gradlew test --tests "*ModularityTests*"                     # module boundaries (after structural change)

cd ../jordylab-fe
bunx nx test settings-api
bunx nx test settings-ui
bunx nx test shared-auth
bunx nx test jordylab
```

What the suites must prove:

| Story | Proof |
|---|---|
| US1 grants | `RoleMatrixTest` extended: a guest with `app-gamecatalog` reaches the Game Catalog, a guest without it is refused, a grant of FNA is refused by the server |
| US1 timing (FR-007) | an integration test sets a 60 s token lifetime in the test realm, removes the role, and confirms the next refresh loses it within 60 s |
| US2 FNA protection | grant FNA returns `APP_NOT_GRANTABLE`; FNA routes stay admin-only for guests |
| US3 limits | `GuestChatLimitIntegrationTest` extended: personal limit overrides the default, `0` blocks chat, admin unlimited, saved default applies the same day |
| US4 deletion (FR-015) | integration test: after delete, the account is gone, the refresh fails, usage and personal-limit rows are gone, the votes are gone, LibBot conversations are gone |
| US4 retry (FR-017) | simulated Keycloak failure at `DELETE_ACCOUNT`: person stays disabled, response carries `stage`, retry succeeds |
| US5 sessions | list shows web and phone sessions with kinds; ending one leaves the other (per T-V1 outcome) |
| Existing behaviour (FR-021) | the existing approve, reject, revoke and reset tests still pass unchanged, apart from the expected role assignments |

---

## 3. Manual validation on the dev stack

Use the real scanner and real sign-up, never hand-written rows (root `AGENTS.md`, Validation Data).

1. Sign in as the admin. Open **Settings → Users**. Every person shows status, apps, personal or default limit and session count.
2. **Grant**: pick a guest who has no Game Catalog access. Grant it. Within 60 s, sign in as that guest in a second
   browser: Library is visible and loads. Admin sees "granted".
3. **Remove access**: remove the Game Catalog from the same guest. Within 60 s, in the guest's browser: Library is gone
   and direct URLs show the "No apps yet" page. The guest stays signed in; they are not sent to the sign-in page (FR-007).
4. **FNA**: the FNA row shows "Holds the admin's private data" with a disabled control. Try the grant through the browser
   devtools request: the server refuses it.
5. **Default limit**: set the default to 2. As a guest with no personal limit, send two questions in LibBot; the third is
   refused with the friendly message. Raise the default to 20; the next question works the same day.
6. **Personal limit**: set this guest to 0; their next question is refused. Clear it; the default applies again.
7. **Sessions**: open the same guest on two browsers. The panel shows two web sessions. End one: that browser is signed out,
   the other stays. Pair a phone (if available) and check that its offline session appears as a phone row (T-V1).
8. **Delete**: delete a test guest who is signed in on two devices. Both devices are signed out within 60 s. The person no
   longer appears in the panel and cannot sign in with their old password. Their LibBot history is empty on a new
   sign-up.
9. **Admin protection**: the admin's own row has no Delete, no Revoke, no limit and no sessions list. A direct request for
   each returns the matching refusal.

---

## 4. Production roll-out order

Order matters: roles must exist and be assigned before the backend that checks them is deployed (plan, work streams A and F).

1. Create the realm role `app-gamecatalog` and grant it to every current approved guest (kcadm, idempotent).
2. Set `accessTokenLifespan` to 60 (owner sign-off, research R2).
3. Mirror both changes in `deploy/keycloak/realm-prod.json` and `jordylab-be/compose/keycloak-realm-export.json`, and record
   them in the deploy README table with the commands.
4. Deploy the backend (with the new `SecurityConfig` matchers and the settings migration).
5. Deploy the frontend.
6. Check: a current guest still sees the Game Catalog; a pending user still sees "awaiting approval"; `GET /api/fna/**` is still
   refused for a guest.

Verify the realm state before step 4:

```bash
kcadm.sh get realms/jordylab --fields accessTokenLifespan --config /tmp/kc.cfg
kcadm.sh get roles/app-gamecatalog -r jordylab --config /tmp/kc.cfg
kcadm.sh get roles/app-gamecatalog/users -r jordylab --config /tmp/kc.cfg   # every approved guest must be listed here
```

The commands run inside the Keycloak pod, as in the deploy README. No secret is printed (root `AGENTS.md`).

---

## 5. End-to-end journey (automated)

A Playwright journey in `jordylab-fe/apps/jordylab-e2e` covers section 3 steps 1 to 3 on the throwaway stack. Run it with:

```bash
cd jordylab-fe
e2e/run.sh web
```

The journey needs the 60 s wait once. It does not touch the dev stack or the dev database.

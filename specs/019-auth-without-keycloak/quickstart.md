# Quickstart: Validate Authentication Without Keycloak

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · Contracts: [identity-api.md](contracts/identity-api.md), [access-matrix.md](contracts/access-matrix.md)

A run guide for proving the feature end to end, mapping to SC-001…SC-008. Nothing here seeds the database by hand — accounts come from the real flows, catalog data from the real scan client.

## Prerequisites

- Podman running; Bun and Node 22; Java 25.
- **Back up the dev database before anything that applies migrations**:
  `podman exec jordylab-be-pgvector-1 sh -c 'pg_dump -U "$POSTGRES_USER" -Fc jordylab' > ~/jordylab-dev-$(date +%F).dump`
- Testcontainers on Podman: `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')` and `TESTCONTAINERS_RYUK_DISABLED=true`.

## §1 Register, approve, sign in — no Keycloak (SC-001)

1. `cd jordylab-be && podman compose up -d` — only Postgres starts (the Keycloak service is gone after release B; before that it may still be running and idle). `./gradlew bootRun` applies migrations and bootstraps the dev admin (`jordy`, dev password in `application-local.yaml`) **only while the user table is empty**.
2. `bunx nx serve jordylab` (port 4200) → sign in as the bootstrap admin at `/login` (the app's own form).
3. Sign out, register a second account at `/register` (username + password, no email). Sign in: lands on `/awaiting-approval`; the admin got an ntfy notification.
4. As the admin at `/settings/users`: **Approve** the account — it receives `guest` + Game Catalog access. Sign in as the new user: the app shell and the Game Catalog work; FNA and Settings are hidden.
5. Wrong password five times → `ACCOUNT_LOCKED`; unknown username and wrong password look identical.

## §2 Passkeys — fingerprint/face sign-in (SC-002)

1. As the guest, open Settings → App → **Passkeys** → **Add passkey** → complete the device's fingerprint/face prompt (desktop: platform authenticator or a security key). The passkey appears in the list.
2. Sign out. On `/login`, type the username, choose **Sign in with a passkey**, confirm with the device — signed in without a password.
3. On the phone (installed PWA): repeat 1–2 — the phone's fingerprint/face signs in.
4. Add a second passkey, remove the first; sign-in with the remaining one still works. Remove all → passkey sign-in shows "no passkey for this account"; the password still signs in.
5. A pending account with a passkey signs in to `/awaiting-approval`; a revoked account (test after §4) cannot sign in with either method.

## §3 Sessions survive restarts (SC-003)

1. Sign in; restart the backend (`Ctrl-C`, `./gradlew bootRun`); reload — still signed in (`identity.spring_session` row survived).
2. CSRF proof: `curl -X POST` a mutating endpoint with the session cookie but **without** `X-XSRF-TOKEN` → `403`; with the header → success.
3. Sign out → the old cookie is refused.

## §4 Admin user management parity (014) (SC-005)

1. As the admin: the Users page shows pending/approved/rejected groups, the Game Catalog grant toggle, per-user sessions, reset-password, revoke and delete.
2. Revoke the guest's Game Catalog grant → the guest's session ends; their next request is refused; the catalog is `403` after re-sign-in with the password.
3. Re-grant → the catalog works again.
4. List the guest's sessions, end one; end all; the app is signed out.
5. Delete a non-admin account → sessions ended, usage and personal chat limit cleared, marks cleaned up (check `/settings/users` and the catalog). Deleting yourself or the last admin → refused.

## §5 Machine tokens and the real scanner (SC-004)

1. As the admin at `/settings/machines`: **Add machine** (`MacBook`) — the `jlmt_…` token appears once. Copy it.
2. Download the client from the Sources page, run it on the real machine with `JORDYLAB_SCAN_TOKEN=<token>`; the scan registers/adopts the source and the catalog reflects the real library; the token is cached at `~/.config/jordylab/scan/token.json` (0600).
3. Re-run → cached token, `/check` short-circuits when nothing changed.
4. Revoke the machine → the next run is refused (`401`) and re-prompts.
5. `last_used_at` advances in the machine list (≤ once a minute).

## §6 Password recovery (SC-006, SC-007)

1. Sign out; on `/forgot-password` submit username + new password. The response is identical for a real and a nonexistent username.
2. As the admin: the pending request appears on the user card; **Approve** → sign in with the new password; the old one fails. Reject on a second request → old password keeps working.
3. Admin **Reset password** → a one-time temporary password is revealed and works.
4. Signed-in user changes their own password (current one confirmed) → a pending request is superseded.
5. Break-glass rehearsal (dev database): runbook one-liner (`htpasswd -bnBC 10 "" '…' | tr -d ':\n'`) + `UPDATE identity.user_account SET password_hash = …`, sign in.

## §7 Automated verification

```bash
cd jordylab-be && ./gradlew test            # identity unit/slice/integration tests, rewritten RoleMatrixTest,
                                            # user-management + session + CSRF + migration tests,
                                            # prod-absence test for the e2e-only sign-in
./gradlew :test --tests "*ModularityTests*" # boundaries with the new identity module
cd ../jordylab-fe && bunx nx affected -t test   # Vitest+Spectator: AuthService, webauthn, forms, users/machines

cd jordylab-fe/e2e && ./run.sh web          # Postgres-only stack; journeys use the real forms;
                                            # passkeys.spec.ts drives a CDP virtual authenticator
bash e2e/lib/cleanup.sh verify-all
```

Expect: no Keycloak image anywhere in the run; all journeys green — including sign-in/sign-out, registration → approval, the two-person marks journey, passkey enrollment + sign-in, and the PWA manifest journey.

## §8 Cutover and removal in production (SC-008) — two releases

1. **Release A (cutover)**: deploy. The migration copies real Keycloak users + `app-gamecatalog` grants into `identity` (ids = `sub`s). Verify read-only: user count matches the Keycloak persons; spot-check a `game_mark` still resolves to its owner.
2. First sign-ins: each migrated user (including the owner) resets their password via the admin-approved flow, then adds a passkey.
3. Take the **verified backup** (runbook §15; comparison now counts `identity.user_account`).
4. **Release B (removal)**: deploy — `keycloak` schema dropped; pod/route/config/secrets/manifests gone.
5. Post-checks: one fewer workload; `https://jordylab.be/auth/realms/jordylab` → 404; `grep -ri keycloak` over the repo (excluding `docs/testing/` and `specs/`) returns nothing; `e2e/run.sh web` green.

## §9 Read-only sanity queries (never write)

```sql
SELECT count(*) FROM identity.user_account;
SELECT count(*) FROM identity.user_app_grant WHERE app = 'GAME_CATALOG';
SELECT count(*) FROM identity.user_credentials;                -- enrolled passkeys
SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'keycloak';  -- 1 until release B
SELECT machine_id, source_type FROM gamecatalog.scan_source;   -- adoption intact
```

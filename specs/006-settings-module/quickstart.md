# Quickstart — Settings Module (Users & AI Models)

Manual, end-to-end validation of [spec.md](spec.md). Each scenario maps to the FR/SC it proves.
Interfaces: [settings-users-api](contracts/settings-users-api.md) · [settings-ai-models-api](contracts/settings-ai-models-api.md) · [access matrix](contracts/access-matrix.md) · [data model](data-model.md).

> **Validation data rule**: never seed users or catalog rows by hand. Users come from real Keycloak registration;
> catalog data comes from the real scan client. Synthetic data lives only in Testcontainers/WireMock tests.

## Prerequisites

- Podman + the compose stack (`jordylab-be/compose.yaml`): pgvector + Keycloak 26.3.2
- Env for the backend: `OPENROUTER_API_KEY`, `ANTHROPIC_API_KEY`, plus the existing compose vars; optional
  `NTFY_BASE_URL`, `NTFY_TOPIC`, `NTFY_TOKEN` (US7); optional overrides: `JORDYLAB_GUEST_CHAT_DAILY_LIMIT`,
  `OPENROUTER_BASE_URL`
- JDK 25 (backend), Bun + Node (frontend)

## One-time dev realm reset (required)

Realm-export changes apply **only on first import** (same gotcha as the known `loginTheme` note). The dev realm already
exists in the pgvector volume, so drop the Keycloak schema and let `--import-realm` rebuild it — `finance`/`gamecatalog`
schemas are untouched:

```bash
podman compose up -d pgvector
podman exec jordylab-pgvector psql -U "$POSTGRES_USER" -d jordylab \
  -c 'DROP SCHEMA keycloak CASCADE; CREATE SCHEMA keycloak;'
podman compose up -d   # keycloak re-imports the realm (registration on, admin/guest roles, jordylab-backend client)
```

Container/db names per `compose.yaml`. Then copy the generated `jordylab-backend` client secret (admin console →
Clients → `jordylab-backend` → Credentials) into `.env` as `KEYCLOAK_BACKEND_SECRET`.

Verify: Keycloak admin console → realm `jordylab` shows roles `admin`, `guest`, `gamecatalog-scanner`, user `jordy`
holding `admin`, and a Register link on the login page. **Dev login is email-as-username**: username
`jordy@jordylab.local`, password `nightlab-dev-2026` (the 12-character policy rejects the old short password).

## Start

```bash
# backend (from jordylab-be/)
./gradlew bootRun
# frontend (from jordylab-fe/)
bunx nx serve jordylab        # http://localhost:4200
```

## Scenarios

### 1. Registration & pending isolation (US1, FR-001–FR-005, SC-001)

1. Log out of the app → Keycloak login page shows **Register**.
2. Register (`friend@example.org`, names, a policy-passing password).
3. Log in as that account → **only** the "awaiting approval" page renders; the nav shell shows no domain tabs.
4. Expected: backend denies every API (matrix — verified automatically by the Keycloak Testcontainers suite for each
   group; manually: navigate to `/games/grid` and `/fna/articles` → awaiting-approval page, no data).

### 2. Approve → guest scope (US2, US3, SC-002)

1. As `jordy` (admin): open **Settings → Users** → the friend is listed as `PENDING`; the Settings tab shows the pending
   badge.
2. Click **Approve** (≤ 2 clicks from the tab — SC-002).
3. Log in as the friend → **only** the Game Catalog** tab** (Library, Chat; Sources hidden; FNA/Settings absent).
4. Direct-navigate to `/fna/articles` and `/settings/users` → denied per the guard contract (role-appropriate page, no
   data).

### 3. Reject (US2, FR-006)

Register a second account; as admin click **Reject**. Expected: the account is listed `REJECTED`; logging in is refused;
registering again with the same email gets Keycloak's duplicate-email error.

### 4. Revoke & last-admin guard (US2, FR-007)

1. As the friend, send a chat message (proves guest works).
2. As admin, **Revoke** the friend → their sessions end; they re-login to the awaiting-approval page.
3. As admin, try **Reject** on `jordy` → `400 LAST_ADMIN_PROTECTED`, nothing changes.

### 5. Reset password (US2, FR-006)

As admin, **Reset password** for the friend → the temporary password is shown once → log in as the friend → Keycloak
forces a password change before the app loads.

### 6. My account (US4, FR-008)

As the friend: account menu → **Change password** (re-authenticate, set new password, land back in the app), **Edit
name** (`UPDATE_PROFILE`) and **Change email** (`UPDATE_EMAIL`, re-authenticated; the realm must have that action
enabled — `deploy/keycloak/README.md`). Email is the username here, so after the change you sign in with the new
email.

### 7. Model catalog & per-feature model (US5, FR-013–FR-016, SC-003/SC-006)

1. **Settings → AI Models** loads with all four features, their defaults, read-only fallback (`claude-sonnet-5`), and
   last-run info; the model picker lists gateway models with vendor/pricing/context, searchable + vendor-filterable.
   First fetch is keyless (catalog contract).
2. Pick a different model for **Game Catalog chat answer writing**, save → ask a catalog question → the answer arrives
   and the row's last run shows the new model/provider — **no restart** (SC-003).
3. Picker reload after caching: page + catalog load < 2 s (SC-006).

### 8. Fallback (US6, FR-012, SC-004)

1. Stop the backend; run it with `OPENROUTER_BASE_URL=http://localhost:9` (unreachable gateway) — fallback key still
   real.
2. Ask a catalog question → the answer still arrives (Anthropic direct, `claude-sonnet-5`); the AI Models row shows
   `lastRun.fallbackUsed: true` (FR-016).
3. Restore the real gateway URL and restart. All failure reasons (timeout, 429, auth, model-not-found) are additionally
   proven by the WireMock suite — scenario 8 is the manual spot-check.

### 9. Model unavailable flag (US6, FR-015)

With a fresh catalog, `PUT /api/settings/ai-models/gamecatalog.chat.answer` with a bogus `modelId` →
`400 MODEL_UNAVAILABLE`. The saved-model-disappears path is covered by the automated suite (WireMock catalog swap).

### 10. Guest chat limit (US3, FR-009)

1. Restart backend with `JORDYLAB_GUEST_CHAT_DAILY_LIMIT=3`.
2. As the guest, send 3 chats (all answer) → the 4th shows the friendly "limit reached, resets at …" message and no AI
   call happens; the admin is unaffected.
3. Restart the backend mid-day → the count persists (rows keyed by date — data model).

### 11. Sign-up notification (US7, FR-018 — optional)

With `NTFY_*` set: register a new account → an Ntfy push arrives with the pending user's name/email within the poll
interval.

### 12. Scanner regression (FR-002)

As admin, download the client (**Sources** page) and run it on this machine against the running backend — scan/check
work exactly as before (device flow, `gamecatalog-scanner` role untouched; the client download is now admin-gated).

### 13. Ollama removal (FR-017, SC-005)

`grep -ri ollama jordylab-be/ jordylab-fe/` → only historical mentions (AGENTS.md history notes/spec folders); build +
compose contain none.

## Automated verification

```bash
# backend (jordylab-be/, Podman socket per AGENTS.md)
./gradlew :test --tests "*ModularityTests*"        # module boundaries
./gradlew :test --tests "*SettingsModuleTest*"     # settings module (Keycloak container + WireMock)
./gradlew test                                    # full suite after the Spring AI GA bump (research D1)

# frontend (jordylab-fe/)
bunx nx run-many -t test -p settings-api settings-ui
bunx nx affected -t test lint                      # app shell / auth / nav updates
```

Expected: all green; `ModularityTests` confirms the settings module keeps boundaries (`settings → shared` only).

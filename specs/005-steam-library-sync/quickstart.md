# Quickstart: Steam Library & Family Library Sync

End-to-end validation on real data. **No hand-seeding** — per repo rule, the catalog is populated by the
real scan client and real Steam syncs.

## Prerequisites

- `main` (with 004) + this branch, backend running: `./gradlew bootRun` in `jordylab-be` (Podman Compose
  up for Postgres/Keycloak).
- Env secrets set: `STEAM_WEB_API_KEY`, `STEAM_ID` (never echoed). Backend reachable by the scan client.
- A real Steam library on the scanning machine (macOS / CachyOS).

## 1. Migration + baseline

1. Start the backend; confirm `V20260928001__gamecatalog_library.sql` applied and
   `uq_game_steam_app_id`, `game_library_entry`, `library_sync_run` exist.
2. Download and run the scanner from Settings → Sources → Steam (as today) to populate installed games.

## 2. Owned library sync

1. `POST /api/gamecatalog/library/steam/sync` (authenticated as `jordylab-user`).
2. Expect `outcome: APPLIED`, `entriesAdded > 0`. Open the catalog with `installStatus=ALL` — owned,
   not-installed games appear with source **Owned** and status **Not installed**.
3. **SC-002**: repeat the same call — expect `outcome: NO_CHANGE`, all counts 0.
4. **SC-001**: pick an already-enriched installed game; from the API/logs confirm the sync caused 0
   metadata calls and 0 AI calls for it (it already had data). The game now has ≥1 installation and 1
   library entry, ID unchanged.

## 3. Filters

1. `installStatus=INSTALLED` (default) ⇒ exactly the pre-005 catalog (SC-006).
2. `installStatus=NOT_INSTALLED` ⇒ library-only games; the host filter is hidden in the UI (FR-023).
3. `installStatus=ALL` + `librarySource=OWNED` ⇒ owned games, installed or not.
4. Chat: ask *"which games I own but haven't installed support 4 local players?"* — the model must use
   `installStatus=NOT_INSTALLED`.

## 4. Family library sync

1. Obtain a short-lived Steam user access token (~24 h) out-of-band.
2. `POST /api/gamecatalog/library/steam-family/sync` `{ "accessToken": "…" }`.
3. Expect family-only games with source **Family**; owned-and-shared games stay **Owned**.
4. `GET /api/gamecatalog/library/status` — `family.lastSuccessAt` set, `familyTokenPresent: false`.
5. Failure drills: blank token → 400 `FAMILY_TOKEN_REQUIRED`; expired token → 502 `FAMILY_SYNC_FAILED`,
   **0 rows changed** (SC-008). Confirm no token appears in logs or any response (SC-007).

## 5. Cost economy

1. After a large first sync, confirm new not-installed games are visible immediately with artwork and a
   Steam `short_description`, and that batch metadata/enrichment trickle in (installed first).
2. **FR-022/SC-009**: no AI call is ever made for a game that is not installed. Install one such game,
   rescan, and confirm it then enriches.
3. Hit Steam's store rate limit (or simulate 429 with WireMock) — metadata batch stops without marking
   games `FAILED`; the next pass resumes.

## 6. Lifecycle

1. Uninstall an owned game from a host, rescan, wait past the 30-day grace (or lower
   `grace-period-days` in dev) → the game survives the purge because it is still in the library (SC-005).
2. Remove a game from the library (refund/family leaves), sync → entry soft-removed; with no
   installation it purges after grace. Re-add within grace → same game row and description reused.

## 7. Concurrency

- **SC-004**: trigger a host scan and `POST /library/steam/sync` concurrently for a game new to both →
  exactly one `game` row (enforced by `uq_game_steam_app_id`, verified by a Testcontainers test).

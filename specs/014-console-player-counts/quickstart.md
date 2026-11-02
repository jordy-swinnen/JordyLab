# Quickstart: validating console player counts

How to prove the feature works, locally first and then on production. Contracts: [thegamesdb-lookup.md](contracts/thegamesdb-lookup.md),
[catalog-api-changes.md](contracts/catalog-api-changes.md). Data model: [data-model.md](data-model.md).

**Real data only.** Never insert or edit rows by hand. The library comes from the real scan client; the counts come from the
real TheGamesDB responses. If no real console games are available locally, stop and report it.

## Prerequisites

- `jordylab-be/.env` has `THEGAMESDB_API_KEY` (the regenerated key) plus the usual IGDB keys. Check by behaviour only, never by
  printing it: the backend's start-up log must **not** say "TheGamesDB not configured".
- Dev stack running (`cd jordylab-be && podman compose up -d`) with the owner's real library, backed up first:
  `podman exec jordylab-be-pgvector-1 sh -c 'pg_dump -U "$POSTGRES_USER" -Fc jordylab' > ~/jordylab-dev-$(date +%F).dump`.
- On a Podman host, for the tests:
  `export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')` and
  `export TESTCONTAINERS_RYUK_DISABLED=true`.

## 1. Automated checks (changed code only)

```bash
cd jordylab-be && ./gradlew test --tests "*TheGamesDbClientTest" --tests "*MultiplayerServiceTest" --tests "*GameTest" --tests "*LibraryHealth*" --tests "*AnswerComposer*"
```

```bash
cd jordylab-be && ./gradlew test --tests "*ModularityTests*" --tests "*GameCatalogModuleTest*"
```

```bash
cd jordylab-be && ./gradlew theGamesDbCheck
```

```bash
cd jordylab-fe && bunx nx test gamecatalog-ui && bunx nx test gamecatalog-api
```

Expected: all green; `theGamesDbCheck` confirms every mapped console still has its TheGamesDB name (needs the key in the
environment, spends one lookup).

## 2. Local end-to-end

1. `cd jordylab-be && ./gradlew bootRun` and `cd jordylab-fe && bunx nx serve jordylab`; sign in as admin.
2. Sources → **Refresh game data** (the free bulk run). Wait for it to finish.
3. Sources → **Player counts**: each source's filled number, the unknown number split by reason, TheGamesDB "configured", the
   remaining allowance. Open "Entries disagree": on the owner's library this should list Mario Party 7 and PlayStation
   All-Stars Battle Royale if those ROMs are present.
4. Open a console game the step filled (for example Mario Party 6 or WWF No Mercy): "Local players: 4 · TheGamesDB", while
   "Multiplayer data" still names IGDB.
5. Open a Steam local-multiplayer game with an IGDB count: the count now says "IGDB", no longer implied to be Steam.
6. Ask LibBot "what can we play with 4 people?": the unknown-count note names fewer games than before the run.
7. Logs: `grep -i "thegamesdb"` shows no request URL; `grep -c "apikey"` on the backend log is 0.
8. Unset the key, restart, run the bulk refresh again: one "not configured" line, no lookups, the counts already stored stay,
   the Sources page shows "not configured".

## 3. Production (after release and the owner's secret change)

1. The owner adds `THEGAMESDB_API_KEY` under `jordylab-secrets.stringData` with
   `SOPS_EDITOR="idea --wait" sops deploy/k8s/overlays/prod/secrets.sops.yaml`, commits with `Refs: 014`, and the release
   deploys it.
2. Presence only, never the value: `kubectl -n jordylab exec deploy/backend -- sh -c 'test -n "$THEGAMESDB_API_KEY" && echo SET'`.
3. On https://jordylab.be as admin: **Refresh game data**, then the Sources page player-count section.
4. SC-001: fewer than 10 % of local-multiplayer console games without a count (expected 2 of 56); every remaining one listed
   with a reason.
5. SC-002: spot-check ten filled console games against their pages on thegamesdb.net; all agree.
6. SC-003: Mario Party 7 has no count.
7. SC-004: the 4-player LibBot question names fewer unknown games than "85".
8. SC-006: the remaining allowance dropped by at most 100 after the run.
9. Read-only check that every count names a source (SC-007):
   `SELECT count(*) FROM gamecatalog.game WHERE (max_local_players IS NULL) <> (max_local_players_source IS NULL);` → `0`.

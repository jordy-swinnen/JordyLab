# Quickstart: Game Catalog Validation Guide

**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)
**Contracts**: [ingest-api.md](./contracts/ingest-api.md) · [catalog-api.md](./contracts/catalog-api.md)

---

## Prerequisites

- Java 25, Bun, Podman + Podman Compose, `jq` (the downloaded scan script needs it)
- `ANTHROPIC_API_KEY` in `jordylab-be/.env`
- PostgreSQL 16 + pgvector and Keycloak: `podman compose up -d` from `jordylab-be/`

## 1. Backend — unit + module tests

```bash
cd jordylab-be
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew build
./gradlew :test --tests "*ModularityTests*"
```

**Expected**: all green, JaCoCo per-package instruction coverage ≥ 80%. Coverage: entity
builder/EqualsVerifier tests; `ScanService` (byte/game caps + rejection reasons, payload-hash
`NO_CHANGE`, exactly one attempt recorded); `ReconciliationService` (add/update/hide/grace-restore/
purge, `SCAN_FAILED` = zero reconciliation); the scan parsers (`VdfParser`, `SteamLibraryParser`,
`EmuDeckLibraryParser`); `ScriptService` rendering (placeholder substitution, bash expansions left
intact); `ResilientAiService` per-module model attribution; `EnrichmentService` (strict-JSON parse,
attempts → `FAILED`); `ChatService` (filter validation, grounded citations, `CHAT_UNAVAILABLE`);
`ArtworkService` (magic bytes, caps, external probe via WireMock); `@ApplicationModuleTest` slice
for the module; `@WebMvcTest` for the controllers.

## 2. Frontend — unit tests + lint

```bash
cd jordylab-fe
bunx nx run-many -t test
bunx nx run-many -t lint
```

**Expected**: green, ≥ 80% lines per lib. Coverage: api service HTTP contract via Spectator
(`expectOne` per endpoint); signal stores; grid container (loading/error/populated, search +
platform filter wiring); detail (enriched vs "description unavailable"); chat (answer + citation
links, unavailable state); source manager (list, toggle).

## 2. Scan-script end-to-end (local)

The scan flow is a downloaded shell script (not a sidecar): it performs the Keycloak Device
Authorization Grant, walks the library, and POSTs to `/api/gamecatalog/ingest/scan`.

Seed a fake library on the dev machine (stand-in for JordyBox):

```bash
/tmp/fake-lib/
├── steam/steamapps/libraryfolders.vdf + appmanifest_620.acf   # 1 Steam game
└── roms/snes/Super Mario World (USA) (Rev 1).sfc              # 1 ROM
```

1. Start Postgres + Keycloak: `podman compose up -d`; start the backend: `./gradlew bootRun`.
2. Download the rendered script (authenticated as `jordy`) from the Sources page in the web UI,
   or `curl -H "Authorization: Bearer $TOKEN" "localhost:8080/api/gamecatalog/ingest/script?libraryType=steam"`.
3. Run it: `./jordy-scan-steam.sh --path /tmp/fake-lib/steam` → authorize the printed device-code
   URL in a browser → expect `APPLIED` with `added: 1`.
4. Run it again unchanged → expect `NO_CHANGE` (payload-hash idempotency).
5. `curl localhost:8080/api/gamecatalog/games` → Portal 2 as a Steam card; the source is
   auto-registered as `(hostname, STEAM)`.
6. Repeat with the EmuDeck script (`?libraryType=emudeck`, `--path /tmp/fake-lib/roms`) → the
   SNES game appears with platform `SNES`.
7. Wait for the scheduled enrichment batch (or trigger it) → detail shows genre + multiplayer
   facts + description; chat answers cite only seeded games.
8. **Reality check**: rename `appmanifest_620.acf` → re-run the Steam script → game hidden from
   `/games` (DB row `UNINSTALLED`). Restore → re-run → game back with the same `id`.

## 3. Frontend browser smoke (agent-browser)

```bash
bunx nx serve jordylab   # http://localhost:4200
```

Log in through Keycloak, then exercise Library (`/games`), search + platform filter, a game
detail, grounded Chat, and Sources (list, toggle enable/disable, per-library script download).

## 4. Production smoke (VPS + JordyBox) — deferred until deploy

- Deploy the backend via the existing Compose stack.
- On JordyBox: download the scan script from the Sources page, run it against the real Steam and
  EmuDeck roots, and schedule it (cron/systemd timer).
- Verify with JordyBox's firewall denying inbound: games appear after a scan; `lastOutcome` is
  `APPLIED`/`NO_CHANGE` per source; grid, chat, and sources views work over the public URL.

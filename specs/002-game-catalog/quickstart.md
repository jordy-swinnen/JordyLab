# Quickstart: Game Catalog Validation Guide

**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)
**Contracts**: [ingest-api.md](./contracts/ingest-api.md) · [catalog-api.md](./contracts/catalog-api.md) · [agent-config.md](./contracts/agent-config.md)

---

## Prerequisites

- Java 25, Bun, Python 3.12, Docker
- `ANTHROPIC_API_KEY` and `GAMECATALOG_INGEST_TOKEN` in `jordylab-be/.env`
- PostgreSQL 16 + pgvector: `docker compose up -d` from `jordylab-be/`
- Agent deps: `cd gamecatalog-sync-service && python -m venv .venv && pip install -r requirements.txt`

## 1. Backend — unit + module tests

```bash
cd jordylab-be
./gradlew test
./gradlew :test --tests "*ModularityTests*"
```

**Expected**: all green, JaCoCo ≥ 80%. Coverage: entity builder/EqualsVerifier tests; `IngestionService` validation matrix (per-entry rejections, sanitize-vs-reject); `ReconciliationService` (add/update/hide/grace-restore/purge, `OUT_OF_ORDER`, `NO_CHANGE`, `SCAN_FAILED` = zero reconciliation); `IngestAuthFilter` (401/503/pass, constant-time path); `EnrichmentService` (strict-JSON parse, attempts → `FAILED`); `ChatService` (filter validation, grounded citations, `CHAT_UNAVAILABLE`); `ArtworkService` (magic bytes, caps, external probe via WireMock); `@ApplicationModuleTest` slice for the module; `@WebMvcTest` for all three controllers.

## 2. Agent — unit tests

```bash
cd gamecatalog-sync-service
python -m pytest --cov=src
ruff check .
```

**Expected**: green. Coverage: VDF parsing from fixture libraries; ROM scan (extension filter, `.m3u` + disc-pattern grouping, tag normalization); scan-failure classification (`UNMOUNTED` vs empty dir); sequence/hash state transitions incl. corrupt state recovery; server client via `respx` (401 propagation, config check-in merge, artwork upload flow).

## 3. Frontend — unit tests

```bash
cd jordylab-fe
bunx nx run-many -t test
bunx nx run-many -t lint
```

**Expected**: green, ≥ 80% lines per lib; lint passes with the new `scope:gamecatalog` depConstraint. Coverage: api service HTTP contract via Spectator (`expectOne` per endpoint); grid container (loading/error/populated, search + platform filter wiring); detail (enriched vs "description unavailable"); chat (answer + citation links, unavailable state); source manager (list, toggle optimistic update).

## 4. End-to-end (local, scripted)

Seed a fake library on the dev machine (stand-in for JordyBox):

```bash
/tmp/fake-lib/
├── steam/steamapps/libraryfolders.vdf + appmanifest_620.acf   # 1 Steam game
└── roms/snes/Super Mario World (USA).smc                      # 1 ROM
    └── downloaded_media/boxart/Super Mario World (USA).png
```

1. Start backend: `./gradlew bootRun` (Flyway creates the `gamecatalog` schema).
2. Point agent `config.yaml` at `http://localhost:8080` with sources `steam` and `snes` rooted at `/tmp/fake-lib`; export `GAMECATALOG_INGEST_TOKEN`.
3. `python -m gamecatalog_sync sync` → expect two `APPLIED` outcomes.
4. `curl localhost:8080/api/gamecatalog/games` → 2 games; SNES card has badge + title `Super Mario World`.
5. Trigger enrichment (or wait for the scheduled batch) → detail shows genre + multiplayer facts; `maxLocalPlayers = 2` for SMW.
6. `curl -X POST …/chat -d '{"question":"which games support local co-op?"}'` → answer names only the seeded games; `games[]` citations resolve.
7. **Reality check**: delete the ROM file → re-run sync → game hidden from `/games` (DB: `UNINSTALLED`). Restore the file within grace → sync → game back **with** its description (no re-enrichment).
8. **Trust checks**: `curl` the sync endpoint without a token → `401`, nothing written. POST a payload with a markup-laden title → stored sanitized; with an over-long title → entry in `rejections[]`, others applied.
9. **Scan-failure check**: `umount`/rename the ROM dir → sync → outcome `SCAN_FAILED`, catalog unchanged.
10. **Sources UI**: disable `snes` in the web app → its game vanishes everywhere (DB row intact); re-enable → sync → restored instantly.
11. Frontend: `bunx nx serve fna` → `/games` grid, `/games/:id` detail, `/games/chat`, `/games/sources` all functional through the nav.

## 5. Production smoke (VPS + JordyBox)

- Deploy backend via the existing Compose stack; set `GAMECATALOG_INGEST_TOKEN` and the artwork volume.
- On JordyBox: install the agent, write real `config.yaml` (Steam root + EmuDeck ROM dirs), provision the token env, schedule hourly (Task Scheduler/systemd timer).
- Verify with JordyBox's firewall denying inbound: games appear ≤ 1 h; `lastOutcome = APPLIED` per source; grid, chat, and sources views work over the public URL.

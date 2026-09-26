# Phase 1 Data Model: Game Catalog

**Date**: 2026-08-02
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

All tables live in the `gamecatalog` schema, created by `V20260802__gamecatalog_create_tables.sql` (`CREATE SCHEMA IF NOT EXISTS gamecatalog; SET search_path TO gamecatalog;`). Entities follow the canonical structure in `jordylab-be/AGENTS.md` (UUID ids, `BaseEntity` audit fields, builder-only creation with `Preconditions` in `build()`).

---

## Entities

### `scan_source` — aggregate `ScanSource`

One announced scan source from the JordyBox agent's local configuration.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID | PK | assigned in `build()` |
| `source_key` | TEXT | NOT NULL, UNIQUE, ≤ 100 chars | agent-config key, e.g. `steam`, `snes` |
| `path` | TEXT | NOT NULL, ≤ 500 chars | absolute path on JordyBox (display only; server never accesses it) |
| `source_type` | TEXT | NOT NULL, enum | `STEAM_LIBRARY` \| `ROM_FOLDER` |
| `platform` | TEXT | NOT NULL, ≤ 50 chars | `Steam`, `SNES`, `PlayStation 2`, … — drives badges, filters, artwork lookup |
| `enabled` | BOOLEAN | NOT NULL, default TRUE | web-app toggle; pulled by agent at check-in |
| `last_attempt_at` | TIMESTAMPTZ | nullable | last submission received (any outcome) |
| `last_success_at` | TIMESTAMPTZ | nullable | last `APPLIED` or `NO_CHANGE` outcome |
| `last_outcome` | TEXT | nullable, enum | mirrors latest `SyncReport.outcome` for cheap listing |
| `last_sequence` | BIGINT | NOT NULL, default 0 | last applied sequence (R8) |
| `last_payload_hash` | TEXT | nullable, 64 chars | SHA-256 hex of last applied payload (R8) |

Validation (builder `Preconditions`): `source_key`, `path`, `platform` non-blank; `source_type` non-null.
Named mutations: `announce(path, sourceType, platform)` (re-announce on config change), `setEnabled(boolean)`, `recordAttempt(outcome, at)`, `recordApplied(sequence, payloadHash, at)`.

### `game` — aggregate `Game`

One catalog entry = one installed game from one source (multi-disc sets grouped agent-side, R8/FR-009).

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID | PK | |
| `source_id` | UUID | NOT NULL, FK → `scan_source.id` | |
| `platform` | TEXT | NOT NULL, ≤ 50 chars | denormalized from source at intake (badge rendering without join) |
| `external_ref` | TEXT | NOT NULL, ≤ 500 chars | Steam: appid. ROM: source-relative primary file path. UNIQUE `(source_id, external_ref)` |
| `title` | TEXT | NOT NULL, ≤ 200 chars | sanitized at intake; display title (agent-normalized for ROMs) |
| `genre` | TEXT | nullable, ≤ 100 chars | from enrichment |
| `max_local_players` | INTEGER | nullable, 1–64 | NULL = no local co-op (or unknown pre-enrichment) |
| `online_multiplayer` | BOOLEAN | nullable | NULL until enriched |
| `single_player` | BOOLEAN | nullable | NULL until enriched |
| `description` | TEXT | nullable, ≤ 4000 chars | AI prose |
| `enrichment_status` | TEXT | NOT NULL, default `PENDING` | `PENDING` \| `ENRICHED` \| `FAILED` |
| `enrichment_attempts` | INTEGER | NOT NULL, default 0 | FAILED after 3 attempts; retried daily |
| `artwork_status` | TEXT | NOT NULL, default `PENDING` | `PENDING` \| `EXTERNAL_URL` \| `LOCAL_FALLBACK_REQUESTED` \| `LOCAL_UPLOAD` \| `PLACEHOLDER` |
| `artwork_ref` | TEXT | nullable, ≤ 1000 chars | external URL or server-relative file path |
| `presence` | TEXT | NOT NULL, default `INSTALLED` | `INSTALLED` \| `UNINSTALLED` |
| `first_seen_at` | TIMESTAMPTZ | NOT NULL | first discovery |
| `last_seen_at` | TIMESTAMPTZ | NOT NULL | latest applied snapshot containing it |
| `uninstalled_at` | TIMESTAMPTZ | nullable | set on transition to `UNINSTALLED`; purge clock |

Indexes: `(source_id, external_ref)` unique; `(presence, platform)`; `lower(title)` (search); `(enrichment_status)` where `PENDING` (worker poll); `(presence, uninstalled_at)` (purge).
Named mutations: `seenAgain(at)` (also restores `UNINSTALLED` → `INSTALLED`, clears `uninstalled_at`), `markUninstalled(at)`, `applyEnrichment(genre, maxLocalPlayers, online, singlePlayer, description)`, `recordEnrichmentFailure()`, `applyArtwork(status, ref)`, named setters avoided.

### `sync_report` — aggregate `SyncReport`

Immutable audit record of one submission (FR-008). No mutation methods after build.

| Field | Type | Constraints | Notes |
|-------|------|-------------|-------|
| `id` | UUID | PK | |
| `source_id` | UUID | NOT NULL, FK → `scan_source.id` | |
| `received_at` | TIMESTAMPTZ | NOT NULL | server receipt time |
| `sequence` | BIGINT | NOT NULL | agent sequence |
| `outcome` | TEXT | NOT NULL, enum | `APPLIED` \| `NO_CHANGE` \| `OUT_OF_ORDER` \| `SCAN_FAILED` \| `REJECTED` |
| `payload_hash` | TEXT | nullable, 64 chars | |
| `games_submitted` | INTEGER | NOT NULL, default 0 | |
| `games_added` / `games_updated` / `games_removed` / `games_rejected` | INTEGER | NOT NULL, default 0 | reconciliation counts |

---

## State transitions

### Presence lifecycle (clarify Q1=B, Q3=B)

```
            discovered / rediscovered
            ┌──────────────────────────┐
            ▼                          │
      ┌────────────┐   missing from    ┴───────────────┐   30 days elapsed   ┌─────────┐
      │ INSTALLED  │ ──── applied ───▶ │ UNINSTALLED   │ ──────────────────▶ │ PURGED  │
      └────────────┘     snapshot      └───────────────┘   (daily purge job)  (row deleted)
            ▲                          │
            └──────── seenAgain() ─────┘  (within grace: enrichment + artwork retained)
```

- Hidden-by-disabled-source is **not** a stored state: queries filter `WHERE source.enabled` (single source of truth — no dual state to desynchronize). Disabled games are thus retained indefinitely and reappear instantly on re-enable (FR-024).
- Purge deletes the row (and any locally stored artwork file); descriptions are lost only after the 30-day grace.

### Enrichment lifecycle

`PENDING` → (`EnrichmentService` batch, AI success + valid strict JSON) → `ENRICHED`
`PENDING` → (AI failure / invalid output, attempts < 3) → `PENDING` (attempts+1, next batch)
`PENDING` → (3rd failure) → `FAILED` — detail view shows explicit "description unavailable"; a daily job resets `FAILED` → `PENDING` for retry (FR-018 retryable).

### Artwork lifecycle

`PENDING` → (Steam appid or libretro probe hit) → `EXTERNAL_URL`
`PENDING` → (no external match) → `LOCAL_FALLBACK_REQUESTED` → (agent upload validates) → `LOCAL_UPLOAD`
`LOCAL_FALLBACK_REQUESTED` → (agent reports no local art, or N=3 syncs pass with no upload) → `PLACEHOLDER`

### Sync outcome decision (per submission, R8)

```
scanFailed=true ──────────────────────────────▶ SCAN_FAILED (no reconciliation)
unparseable body / oversize ──────────────────▶ REJECTED (nothing written)
sequence < lastSequence ──────────────────────▶ OUT_OF_ORDER (nothing written)
sequence == lastSequence && hash == lastHash ─▶ NO_CHANGE
otherwise ────────────────────────────────────▶ APPLIED (reconcile + counts)
```

---

## Server configuration model (`jordylab.gamecatalog.*`)

| Key | Default | Purpose |
|-----|---------|---------|
| `ingest.token` | `${GAMECATALOG_INGEST_TOKEN:}` | bearer credential (blank = fail-closed 503) |
| `ingest.max-games-per-source` | 10000 | payload bound (FR-003) |
| `artwork.dir` | `/var/jordylab/artwork` | local upload storage (Docker volume) |
| `artwork.max-bytes` | 2097152 | upload cap (2 MB) |
| `artwork.external-lookup-enabled` | true | Steam CDN / libretro probing toggle |
| `grace-period-days` | 30 | purge window for `UNINSTALLED` |
| `enrichment.batch-size` | 50 | games per enrichment run |
| `enrichment.max-attempts` | 3 | before `FAILED` |
| `chat.max-result-games` | 50 | rows sent to answer composition |

AI routing adds `jordylab.ai.modules.gamecatalog.{provider: anthropic, model: <configured>}` (R6).

## Agent-side persistence (not server schema)

- `config.yaml` (provisioned manually): `server_url`, `sources[]` (`key`, `type`, `path`, `platform`), scan options. Token via env `GAMECATALOG_INGEST_TOKEN` — never in the file.
- `state.json` (agent-managed): `{ "sources": { "<key>": { "sequence": N } } }` — survives runs; basis of R8 ordering.

Full field-level wire formats live in `contracts/`.

# Phase 1 Data Model: Game Catalog Python Scanner

**Date**: 2026-09-26
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

Additive changes to the `gamecatalog` schema owned by `jordylab-be` Flyway. Entities keep the canonical structure from `jordylab-be/AGENTS.md`.

---

## Schema changes

### `scan_source` — new columns (migration `V<date>__gamecatalog_client_check.sql`)

| Field (Java) | Column | Type | Constraints | Notes |
|---|---|---|---|---|
| `machineId` | `machine_id` | TEXT | nullable, ≤ 100 | stable per-machine id; identity = `(machine_id, source_type)` when present |
| `lastClientDigest` | `last_client_digest` | TEXT | nullable, ≤ 200 | opaque client fingerprint; recorded on `APPLIED`/`NO_CHANGE` only |
| `ingestVersion` | `ingest_version` | INTEGER | NOT NULL, default 0 | server ingest-logic version at last success; 0 = never |
| `lastCheckedAt` | `last_checked_at` | TIMESTAMPTZ | nullable | liveness from `POST /ingest/check`; never touches `lastAttemptAt`/`lastOutcome` |

Migration is `CREATE SCHEMA IF NOT EXISTS` untouched; `ALTER TABLE gamecatalog.scan_source ADD COLUMN …` with `NOT NULL DEFAULT 0` for `ingest_version` (safe on existing rows). No backfill needed for the nullable columns.

### `game` — guarded NFC normalization migration (`V<date+1>__gamecatalog_nfc_normalize.sql`)

```sql
SET search_path TO gamecatalog;
-- Abort loudly if normalizing would collide two existing rows for one source.
DO $$
DECLARE collisions INTEGER;
BEGIN
  SELECT count(*) INTO collisions FROM (
    SELECT source_id, normalize(external_ref, nfc) AS ref
    FROM game GROUP BY source_id, normalize(external_ref, nfc) HAVING count(*) > 1
  ) c;
  IF collisions > 0 THEN
    RAISE EXCEPTION 'NFC normalization would collide % (source_id, external_ref) group(s); resolve manually', collisions;
  END IF;
END $$;

UPDATE game SET external_ref = normalize(external_ref, nfc), title = normalize(title, nfc)
WHERE external_ref <> normalize(external_ref, nfc) OR title <> normalize(title, nfc);
```

Expected to affect zero rows in practice; the `DO` block turns a would-be unique-constraint failure into an explicit, actionable abort.

## Fingerprint (client-side, not stored as a schema type)

Canonical form hashed to a digest string; the server stores it opaquely.

```
canonical = json.dumps({
    "paths": [[relpath, size, mtime_ns] ...],         # relpath POSIX+NFC, sorted by relpath (codepoint)
    "manifest_contents": {relpath: raw_vdf_text},      # insertion/keys sorted; Steam only
    "games": [[external_ref, title, platform] ...],    # sorted by external_ref; EmuDeck only
}, sort_keys=True, separators=(",", ":"), ensure_ascii=True)
digest = "sha256:" + sha256(canonical.encode("utf-8")).hexdigest()
```

- `mtime_ns` = `st_mtime_ns` (integer). The payload itself still serializes `mtime` as ISO-8601 per the contract; the digest uses the integer form.
- No file contents are read except Steam `appmanifest_*.acf` (KB-sized) and playlist/disc text needed for grouping.
- Digest is equality-only: the client never derives invalidation from it.

## Types added to the wire model

- `ScanRequest` gains: `clientDigest` (string, optional), `machineId` (string, optional), `force` (boolean, default false), `games` (array of `ClientGame`, optional).
- `ClientGame(externalRef, title, platform)` — EmuDeck grouping/normalization output; validated with the same rules as server-parsed payloads (`REF_BLANK/TOO_LONG`, `TITLE_BLANK/TOO_LONG`, `PLATFORM_BLANK/TOO_LONG`).
- `ScanCheckRequest(machineId?, hostname, libraryType, clientDigest)` and `ScanCheckResponse(scanNeeded, sourceEnabled)`.

## Scan-needed decision (server)

```
unknown source                        → auto-register, scanNeeded = true
lastClientDigest == null              → scanNeeded = true
lastClientDigest != clientDigest      → scanNeeded = true
ingestVersion < CURRENT_INGEST_VERSION→ scanNeeded = true
lastOutcome ∉ {APPLIED, NO_CHANGE}    → scanNeeded = true
otherwise                             → scanNeeded = false
```

## Scan outcome decision (server, extends 002)

```
unparseable / oversize body            → REJECTED (nothing written)
shrink guard tripped && !force         → REJECTED reason=SNAPSHOT_SHRINK_SUSPECT (nothing written, digest NOT recorded)
digest matches && ingestVersion >= CURRENT && last outcome ok → NO_CHANGE (digest recorded)
otherwise                              → APPLIED (reconcile; digest + ingestVersion recorded)
```

## Client-side persistence (not server schema)

- `~/.config/jordylab/scan/config.json` — optional explicit `roots` per library type; only entries here (or `--path`) can cause a partial/exit-5 result.
- `~/.config/jordylab/scan/token.json` — offline access + refresh token (atomic write, 0600).
- `~/.config/jordylab/scan/machine-id` — UUID generated at `login`.
- `~/.local/state/jordylab-scan/last-run.json` — last result for `status`.

## Config additions (`jordylab.gamecatalog.scan.*`)

| Key | Default | Purpose |
|---|---|---|
| `max-shrink-fraction` | 0.50 | shrink-guard threshold (fraction of installed games that may disappear) |

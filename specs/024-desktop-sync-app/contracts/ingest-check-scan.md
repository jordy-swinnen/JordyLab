# Contract: Ingest Check & Scan (unchanged server API)

Reused as-is from spec 003; the app speaks exactly the frozen client's protocol. Serves US1 / FR-003/004/005, ticket D2/D9. **The server stays authoritative.**

## Endpoints (existing, no change)

| Endpoint | Auth | Purpose |
|----------|------|---------|
| `POST /api/gamecatalog/ingest/check` | `jlmt_` machine token, role `gamecatalog-scanner` | Ask whether a scan is needed |
| `POST /api/gamecatalog/ingest/scan` | `jlmt_` machine token, role `gamecatalog-scanner` | Upload scan content when the server said so |

## Check request (client → server)

```json
{
  "machineId": "<stable machine id>",
  "hostname": "<hostname>",
  "libraryType": "STEAM | EMUDECK | SUNSHINE",
  "clientVersion": "<app version>",
  "lastClientDigest": "sha256:<metadata fingerprint>"
}
```

## Check response (server → client)

- `scanNeeded: true|false` — fingerprint differs OR stored `ingest_version < CURRENT_INGEST_VERSION` OR `lastOutcome ∉ {APPLIED, NO_CHANGE}`
- `reason` — why (changed / version bump / previous outcome)

## Scan payload (client → server)

The shared core builds the same payload the frozen client does today:

```json
{
  "machineId": "...",
  "hostname": "...",
  "libraryType": "STEAM | EMUDECK | SUNSHINE",
  "paths": [...],
  "manifestContents": { "<relpath>": "<text>" },
  "games": [ { "title": "...", "platform": "...", "files": [...] } ],
  "clientDigest": "sha256:<canonical fingerprint>"
}
```

- Fingerprint hashing is byte-identical to today (`sort_keys`, `separators=(",", ":")`, `ensure_ascii=True`, `sha256:` prefix) — parity enforced by the shared core's fixture suite.
- `games[]` (client-grouped) takes precedence over server path inference (existing rule).
- `SUNSHINE` payload: one `games[]` entry per configured app from `apps.json` (title + command-derived platform), client-grouped.

## Server responses (unchanged semantics)

| Outcome | Meaning |
|---------|---------|
| `APPLIED` | Scan stored |
| `NO_CHANGE` | Same hash as before; no DB churn |
| `REJECTED` (`SNAPSHOT_SHRINK_SUSPECT`) | Would remove more than `max-shrink-fraction` — needs `force` |
| `413` / rejection | Client exits with its existing code (server-rejected) |

## App-side presentation (designs)

`D_Status.png` (up to date), `D_Syncing.png` (in progress), `D_Failed.png` (failed + skipped roots), `D_Empty.png` (no libraries found).

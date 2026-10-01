# Contract: Ingest API (scan client ↔ jordylab-be)

**Supersedes** `specs/002-game-catalog/contracts/ingest-api.md` (the shell-script `/script` endpoint is replaced by `/client`; `/scan` gains optional fields).

Base path: `/api/gamecatalog/ingest`. All endpoints require a Keycloak-issued bearer token validated by Spring Security's OAuth2 resource server against the `jordylab` realm.

| Endpoint | Method | Role | Description |
|----------|--------|------|-------------|
| `/check` | POST | `gamecatalog-scanner` | Ask whether a scan is needed; records liveness. |
| `/scan`  | POST | `gamecatalog-scanner` | Submit a listing (+ Steam VDF text, + client-grouped EmuDeck games). |
| `/client` | GET | `jordylab-user` | Stream the rendered Python client (`text/x-python`). |

---

## POST `/api/gamecatalog/ingest/check`

```json
{ "machineId": "9f1c…", "hostname": "jordybox", "libraryType": "STEAM", "clientDigest": "sha256:ab12…" }
```

`machineId` is optional during cutover; `hostname` is required and matches `^[A-Za-z0-9._-]+$`. The source is auto-registered if unknown.

`200 OK`:
```json
{ "scanNeeded": true, "sourceEnabled": true }
```

`scanNeeded` is true when the stored fingerprint differs, the stored ingest version is older than the server's current ingest version, or the last outcome was not `APPLIED`/`NO_CHANGE`. A source whose `enabled` flag is false returns `scanNeeded: false` so the client uploads nothing. Every call updates `last_checked_at`. The endpoint never starts a scan or writes catalog data.

---

## POST `/api/gamecatalog/ingest/scan`

### Request

```json
{
  "machineId": "9f1c…",
  "hostname": "jordybox",
  "libraryType": "EMUDECK",
  "capturedAt": "2026-09-26T09:00:00Z",
  "clientDigest": "sha256:ab12…",
  "force": false,
  "paths": [
    { "relpath": "snes/Super Mario World (USA) (Rev 1).sfc", "size": 524288, "mtime": "2026-09-26T08:59:00Z" }
  ],
  "manifestContents": {},
  "games": [
    { "externalRef": "snes/Super Mario World (USA) (Rev 1).sfc", "title": "Super Mario World", "platform": "SNES" }
  ]
}
```

| Field | Rule |
|-------|------|
| `machineId` | optional, ≤ 100 chars; stable machine identity |
| `hostname` | required, 1–100 chars, `^[A-Za-z0-9._-]+$` |
| `libraryType` | required, `STEAM` \| `EMUDECK` |
| `capturedAt` | required, ISO-8601 instant |
| `clientDigest` | optional; opaque client fingerprint. Absent for a source that has one → treated as pre-cutover (WARN + stored fingerprint cleared, scan still processed) |
| `force` | optional boolean, default false; bypasses the shrink guard |
| `paths[]` | required; `relpath` POSIX+NFC, `size` ≥ 0, `mtime` ISO-8601 |
| `manifestContents` | optional; Steam: `relpath → raw VDF text` |
| `games[]` | optional; client-produced entries (`externalRef`, `title`, `platform`). When present, they are the parsed game set (validated/sanitized server-side like any payload); when absent, the server infers games from `paths` (backstop) |
| size | `paths` + `manifestContents` ≤ 8 MiB (decompressed; raised from 1 MB by spec 011 BUG-047 — see 002). The client refuses to send more (`PayloadTooLarge`) and the server answers `PAYLOAD_TOO_LARGE`. At most 50,000 games per source (`TOO_MANY_GAMES`) |

### Outcomes

| Outcome | Meaning |
|---|---|
| `APPLIED` | Snapshot reconciled; fingerprint + ingest version recorded. |
| `NO_CHANGE` | Fingerprint matches; no DB churn; fingerprint re-recorded. |
| `REJECTED` | `PAYLOAD_TOO_LARGE` or `TOO_MANY_GAMES` (002) or **`SNAPSHOT_SHRINK_SUSPECT`** — the resulting installed set is empty, or the scan would remove more than `max(10, 50%)` of the source's installed games, and `force` is false. Nothing is written and **no fingerprint is recorded**. |

`200 OK` body is unchanged in shape from 002 (`outcome`, `sourceEnabled`, `counts`, `rejections[]`, optional `reason`).

### Idempotency

Two mechanisms, both server-side:
- Payload hash (`last_payload_hash`, 002) — identical content → `NO_CHANGE`.
- Client fingerprint (`last_client_digest`, 003) — lets the client *ask first*; the server compares it in `/check`.

---

## GET `/api/gamecatalog/ingest/client?libraryType=steam|emudeck`

Returns the rendered Python client as `text/x-python` with `Content-Disposition: attachment; filename="jordylab-scan-<library>.py"`. Rendered values (`KEYCLOAK_URL`, `REALM`, `CLIENT_ID`, `BACKEND_URL`, `SCAN_ENDPOINT`, `LIBRARY_TYPE`) are emitted as Python string literals inside a single constants header block. Unknown `libraryType` → `400` `ProblemDetail` (`detail`: `libraryType must be 'steam' or 'emudeck'`).

The old `/script` endpoint and the shell template are removed.

---

## Auth notes

- The `gamecatalog-script` client is public, device-grant enabled, **`fullScopeAllowed=false`**, and scope-mapped to `gamecatalog-scanner` only. Its tokens carry no other account roles.
- The device grant requests `scope=openid offline_access` for unattended startup runs; `uninstall` revokes the offline session via the realm's token-revocation endpoint.
- Tokens are cached by the client at `~/.config/jordylab/scan/token.json` (mode 0600).

# Contract: Ingest API (host script ↔ jordylab-be)

Base path: `/api/gamecatalog/ingest`. All endpoints require a Keycloak-issued bearer token, validated by Spring Security's OAuth2 resource server against the `jordylab` realm's JWKS endpoint.

| Endpoint | Method | Required role | Description |
|----------|--------|---------------|-------------|
| `/scan`  | POST   | `gamecatalog-scanner` | Receive a directory listing + per-source VDF contents from the script, auto-register a `ScanSource`, parse, reconcile. |
| `/script` | GET   | `jordylab-user`         | Stream the per-library shell script (`text/x-shellscript`). |
| `/sources/{id}/enabled` | PUT (under `/api/gamecatalog/sources/`) | `jordylab-user` | Toggle a scan source's enabled flag. (Documented in `catalog-api.md`.) |

`/actuator/health` and `/actuator/info` are unauthenticated; the H2 console is also unauthenticated in dev.

---

## POST `/api/gamecatalog/ingest/scan`

The script's only write path. One request per scan invocation.

### Request

```json
{
  "hostname": "jordybox",
  "libraryType": "STEAM",
  "capturedAt": "2026-08-06T09:00:00Z",
  "paths": [
    { "relpath": "steamapps/appmanifest_440.acf", "size": 842, "mtime": "2026-08-05T22:14:00Z" },
    { "relpath": "steamapps/appmanifest_620.acf", "size": 842, "mtime": "2026-08-05T22:14:00Z" }
  ],
  "manifestContents": {
    "steamapps/appmanifest_440.acf": "\"AppState\"\n{\n    appid         \"440\"\n    name          \"Team Fortress 2\"\n    installdir    \"Team Fortress 2\"\n}\n",
    "steamapps/appmanifest_620.acf": "\"AppState\"\n{\n    appid         \"620\"\n    name          \"Portal 2\"\n    installdir    \"Portal 2\"\n}\n"
  }
}
```

For `libraryType: "EMUDECK"` the `paths` array is the full recursive listing under `~/Emulation/roms/<emulator>/` and `manifestContents` is empty (EmuDeck games are inferred from filenames).

| Field | Rule |
|-------|------|
| `hostname` | required, 1–100 chars, matches `^[A-Za-z0-9._-]+$` |
| `libraryType` | required, `STEAM` \| `EMUDECK` |
| `capturedAt` | required, ISO-8601 instant |
| `paths[].relpath` | required, POSIX-style relative to the scanned root |
| `paths[].size` | required, non-negative |
| `paths[].mtime` | required, ISO-8601 instant |
| `manifestContents` | optional, Steam only; key is the `relpath` of an `appmanifest_*.acf`, value is the raw VDF text |
| `paths` + `manifestContents` combined size | ≤ 1 MB (configurable via `jordylab.gamecatalog.scan.max-payload-bytes`) |

### Outcome: `200 OK`

```json
{
  "outcome": "APPLIED",
  "sourceEnabled": true,
  "counts": { "submitted": 412, "added": 3, "updated": 1, "removed": 0, "rejected": 2 },
  "rejections": [
    { "externalRef": "bad.smc", "reason": "REF_BLANK" }
  ]
}
```

| Outcome | Meaning |
|---------|---------|
| `APPLIED` | New snapshot reconciled into the `game` table. |
| `NO_CHANGE` | Payload hash matches the last applied snapshot. No DB churn. |
| `REJECTED` | Body failed validation, or exceeded the per-source games cap, or the payload exceeded the byte cap. |

| `reason` value | When |
|---------------|------|
| `MALFORMED_BODY` | Body is not parseable JSON. |
| `VALIDATION_FAILED` | Body parsed but failed `@Valid` (e.g. missing `hostname`). |
| `PAYLOAD_TOO_LARGE` | Combined `paths` + `manifestContents` exceeded the byte cap. |
| `TOO_MANY_GAMES` | Parsed game count exceeded `jordylab.gamecatalog.scan.max-games-per-source`. |

| `EntryRejection.reason` | When |
|--------------------------|------|
| `REF_BLANK` | Game's `externalRef` was empty. |
| `REF_TOO_LONG` | Game's `externalRef` was longer than 500 chars. |
| `TITLE_BLANK` | Game's `title` was empty. |
| `TITLE_TOO_LONG` | Game's `title` was longer than 200 chars. |
| `PLATFORM_BLANK` | Game's `platform` was empty. |
| `PLATFORM_TOO_LONG` | Game's `platform` was longer than 50 chars. |

Idempotency: a second scan from the same `(hostname, libraryType)` with an identical `paths` + `manifestContents` returns `NO_CHANGE` without touching the games table. The backend hashes the request payload and stores the hash on the `ScanSource` row.

---

## GET `/api/gamecatalog/ingest/script?libraryType=steam|emudeck`

Returns the per-library shell script the user downloads from the web UI. The script template is committed at `jordylab-be/src/main/resources/scripts/jordylab-scan-template.sh` and the `ScriptService` does simple `${KEYCLOAK_URL}`, `${REALM}`, `${CLIENT_ID}`, `${BACKEND_URL}`, `${SCAN_ENDPOINT}`, `${LIBRARY_TYPE}` substitution. The generated script is then sent as `text/x-shellscript` with a `Content-Disposition: attachment; filename="jordylab-scan-<library>.sh"` header.

| `libraryType` | Result |
|---------------|--------|
| `steam` | Steam library scan script (walks `steamapps/`, reads each `appmanifest_<appid>.acf`) |
| `emudeck` | EmuDeck scan script (asks the user which emulator subfolders to scan, walks the chosen set) |
| anything else | `400` (validation error) |

The script handles its own Keycloak Device Authorization Grant. Tokens are cached at `~/.config/jordylab/scan/token.json` (mode 0600).

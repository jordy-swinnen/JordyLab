# Implementation Plan: Game Catalog Python Scanner

**Feature Branch**: `003-gamecatalog-python-scanner` | **Date**: 2026-09-26 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-gamecatalog-python-scanner/spec.md`

**Stacks on**: PR #11 (`002-game-catalog-completion`) — this branch is cut from that branch, so it includes the 002 remainder fixes (parser tests, per-module AI model, content-only idempotency hash, Podman docs). If #11 merges first, rebase is clean.

## Summary

Replace the downloaded shell script with a resident Python client, and extend the backend to support it. The client is a standard-library-only package (`gamecatalog-scanner/`) that is **frozen into a single `.py`** served by the backend, authenticated by a one-time Keycloak Device Authorization Grant with an **offline token scoped to a single role**, installed to start at machine startup on Linux (systemd user timer + linger) and macOS (LaunchAgent), and it **uploads only when the server says a scan is needed**. The server stores a content fingerprint (`last_client_digest`), its own `ingest_version`, and last-checked liveness on `scan_source`, and refuses suspiciously empty/diminished scans unless forced. Existing catalog identity is preserved by porting the server's title/platform rules into the client verbatim.

## Technical Context

**Language/Version**: Python 3.9+ (frozen client, stdlib only — the macOS system Python floor; verified `/usr/bin/python3` = 3.9.6 on the dev Mac), Java 25 (backend), Shell (installers)

**Primary Dependencies**: None at client runtime (stdlib `urllib`, `json`, `hashlib`, `unicodedata`, `os`, `pathlib`, `argparse`, `logging`). Dev-only: `pytest`, `pytest-cov`, `ruff`. Backend: existing Spring Boot 4 / Spring Modulith.

**Storage**: PostgreSQL 16 — new nullable columns on `gamecatalog.scan_source`; guard-normalized data migration on `gamecatalog.game`

**Testing**: pytest (client package + frozen artifact selftest under 3.9 and 3.13), JUnit/AssertJ/Mockito/Testcontainers (backend), agent-browser (E2E device grant)

**Target Platform**: Linux (CachyOS, systemd user units) and macOS (LaunchAgent); the backend on local dev / VPS

**Constraints**: No administrator/root; no inbound connections to scanning machines; TLS verification always on; payload limit unchanged (1 MB, decompressed) — compression deferred; client must never read file contents to decide whether to scan

**Scale/Scope**: single user; up to ~10k files per library; 2 grouping categories beyond Steam VDF (`.m3u`, `.cue`/`.gdi`/`.chd`)

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| Clean code discipline | Pass | Fixed package layout; server authority for the scan decision; client is a thin, auditable artifact |
| Fail fast, no silent failures | Pass | Named outcomes (`SNAPSHOT_SHRINK_SUSPECT`, distinct exit codes); missing roots and stale sessions fail explicitly |
| Immutable, builder-first design | Pass | Entity changes follow `jordylab-be/AGENTS.md`; migrations additive |
| Testing discipline | Pass | Identity-parity test vs the live 002 parsers; frozen-artifact selftest; shrink-guard and check tests |
| Language & tooling currency | Pass | Python 3.9 floor documented and enforced in CI; Java 25 unchanged |
| YAGNI (repo principle) | Pass | Windows, gzip, directory games, Switch DLC explicitly deferred; contract shapes additive |

## Project Structure

```text
gamecatalog-scanner/                         # NEW top-level Python project
├── src/jordylab_scan/
│   ├── __init__.py                          # __version__
│   ├── __main__.py                          # CLI: scan (default) | login | install | uninstall | status | version
│   ├── config.py                            # config file (~/.config/jordylab/scan/config.json): optional explicit roots
│   ├── auth.py                              # device grant (scope "openid offline_access"), refresh, token cache, revocation
│   ├── lock.py                              # cross-process scan lock (fcntl / msvcrt)
│   ├── manifest.py                          # symlink-safe walk, (relpath,size,mtime_ns), NFC, digest
│   ├── payload.py                           # builds the scan request body (paths, manifestContents, games)
│   ├── normalize.py                         # port of EmuDeckLibraryParser.titleFromFilename (verbatim rules)
│   ├── platforms.py                         # port of EMUDECK_PLATFORM_LOOKUPS
│   ├── grouping.py                          # Steam multi-library VDF, .m3u / .cue / .gdi / .chd grouping
│   ├── api.py                               # urllib client: POST /ingest/check, POST /ingest/scan
│   ├── schedule.py                          # systemd user service+timer (Linux) / LaunchAgent (macOS)
│   ├── paths.py                             # per-OS default roots (incl. Flatpak Steam)
│   └── status.py                            # last-run state + human-readable status
├── tools/build_client.py                    # freeze: concatenate modules → single .py with rendered header
├── tests/
│   ├── fixtures/                            # fake libs: steam (multi-library), roms (tags, m3u, cue/bin, gdi, chd), NFD
│   └── test_*.py
├── pyproject.toml                           # dev-only deps (pytest, pytest-cov, ruff); runtime deps: none
└── AGENTS.md

jordylab-be/
├── src/main/resources/scripts/jordylab-scan-template.py   # frozen artifact (replaces .sh template)
├── src/main/resources/db/migration/V<date>__gamecatalog_client_check.sql
└── .../gamecatalog/
    ├── rest/controller/IngestController.java              # + POST /check, GET /client
    ├── rest/controller/model/ScanCheckRequest.java
    ├── rest/controller/model/ScanCheckResponse.java
    ├── rest/controller/model/ScanRequest.java             # + clientDigest, machineId, force, games
    ├── rest/controller/model/ClientGame.java
    ├── service/ScanService.java                           # digest/ingestVersion, shrink guard, machine adoption, client games
    ├── service/ClientService.java                         # renders + serves the frozen client
    └── domain/ScanSource.java                             # + machineId, lastClientDigest, ingestVersion, lastCheckedAt

jordylab-fe/libs/gamecatalog/ui/.../source-manager/        # "Download client" button + last-check line
jordylab-be/compose/keycloak-realm-export.json             # least-privilege client, offline_access
```

## Design decisions

### Change detection (server-authoritative)

- **Fingerprint (client)**: `sha256` over canonical JSON of `{"paths": [[relpath, size, mtime_ns], …], "manifest_contents": {relpath: raw_vdf}, "games": [[external_ref, title, platform], …]}` with `sort_keys=True`, `separators=(",", ":")`, `ensure_ascii=True`, relpaths POSIX + NFC, lists sorted (paths by relpath; games by external_ref). **No file contents** are read except the KB-sized Steam VDF files (required in the payload). The value is opaque to the server and never controls invalidation.
- **Server state**: `scan_source.last_client_digest`, `scan_source.ingest_version`, `scan_source.last_checked_at`.
- **`POST /ingest/check`** → `scanNeeded = lastClientDigest == null || lastClientDigest != request.clientDigest || ingestVersion < CURRENT_INGEST_VERSION || lastOutcome ∉ {APPLIED, NO_CHANGE}`. Response also carries `sourceEnabled`. Records `lastCheckedAt` (liveness); does not touch `lastAttemptAt`/`lastOutcome`.
- **Digest recorded** on `APPLIED` and `NO_CHANGE` only; never on `REJECTED` (including the shrink guard).
- `CURRENT_INGEST_VERSION` is a server constant, bumped whenever parsing/validation/normalization changes.

### Shrink guard

- Reject with `SNAPSHOT_SHRINK_SUSPECT` when `force` is not set and either (a) the scan's resulting installed set is empty, or (b) it would remove more than `max(10, 50%)` of the source's currently installed games (threshold `jordylab.gamecatalog.scan.max-shrink-fraction`, default 0.50). Nothing is written and no digest is recorded. A first-ever scan of an empty library must be forced.

### Identity continuity

- Client ports `EmuDeckLibraryParser.titleFromFilename` (extension strip, `_`→space, `[`→`(`, `]`→`)`, remove `(…)`, whitespace collapse, trim) and `EMUDECK_PLATFORM_LOOKUPS` verbatim; Steam `externalRef` stays the appid, parsed server-side (Steam unaffected).
- Grouping keeps an existing identity: `.m3u` game → `externalRef` = the `.m3u` relpath; disc-numbered set without a playlist → `externalRef` = the lowest-numbered disc file. Components never appear as standalone games.
- NFC normalization on both client (paths/titles) and server (`ScanService.validate`); one-time guarded data migration on `game` (see data-model.md).
- A golden-refs fixture (recorded once from the live 002 parsers) is asserted against the client's payload builder.

### Auth & least privilege

- Device grant requests `scope=openid offline_access`. Realm export: `fullScopeAllowed=false` on `gamecatalog-script` + a scope mapping granting only `gamecatalog-scanner`; verified by decoding a fresh token (exactly one role). Live-verify that `offline_access` is available to the user; attach it to the client in the export as fallback.
- Token cache `~/.config/jordylab/scan/token.json` (atomic write, 0600). Non-interactive runs never start the device flow; refresh failure → exit 2 + `status` guidance.
- Cross-process lock around refresh and around the whole scan run (rotation is enabled).
- `uninstall` → RFC 7009 revoke with the refresh token, then delete the token file (`--purge` also removes machine-id/config/state).

### Startup installers

- **Linux**: `~/.config/systemd/user/jordylab-scan.service` (oneshot) + `.timer` (`OnBootSec=5min`, `OnUnitActiveSec=6h`, `RandomizedDelaySec=10min`); `install` probes `loginctl enable-linger` non-interactively and falls back to a login-triggered unit (upstream polkit gates self-linger), reporting the mode; `status` prints the one-line sudo hint if linger was not enabled.
- **macOS**: `~/Library/LaunchAgents/dev.jordylab.scan.plist` (`RunAtLoad`, login-time); `status` reports the TCC caveat when a library is on an external volume.
- Boot readiness: bounded retry/backoff on the check call (≤5 attempts, exponential to ~60 s, ≤5 min total) — no dependency on `network-online.target`.
- Install location: `~/.local/share/jordylab-scan/` with the absolute interpreter path (`sys.executable`) written into the unit/plist.

### Serving the client

- `ClientService` renders the frozen Python template (`src/main/resources/scripts/jordylab-scan-template.py`) with `${KEYCLOAK_URL}`, `${REALM}`, `${CLIENT_ID}`, `${BACKEND_URL}`, `${LIBRARY_TYPE}` — values are **Python-literal-escaped** and confined to a single constants header block. `GET /ingest/client?libraryType=steam|emudeck` → `text/x-python`, `jordylab-scan-<library>.py`. The `.sh` template and endpoint are removed.
- Freeze determinism + a drift test (regenerate == committed); the frozen artifact's `--selftest` runs in CI under Python 3.9 and 3.13.

### Cutover

- `install`/`status` detect old shell-script schedules (systemd user units/timers, cron lines, LaunchAgents referencing `jordylab-scan-*.sh`) — install removes them (logged), status warns.
- A scan without `clientDigest` for a source that has one → WARN at the server + clear the stored digest.

## Verification approach

- **Unit**: pytest for manifest/digest (incl. NFD fixture, mtime/size sensitivity), grouping fixtures (steam multi-library, m3u, cue/bin, gdi, chd standalone vs referenced), auth (mocked HTTP), schedule generation (unit/plist text), exit codes. Backend JUnit for check semantics, shrink guard, machine adoption, client games, controller, security role.
- **Frozen artifact**: `--selftest` (temp fixture → digest → local `http.server` mock → check/scan) under 3.9 and 3.13; drift test.
- **E2E (dev Mac)**: install/login via agent-browser, scan fixture `APPLIED`, re-run → no upload (`last_checked_at` moved, payload bytes 0), touch a file → upload, m3u/cue fixtures → one game each, remove/restore reality check, NFD filename, LaunchAgent install/uninstall, shrink guard with and without force.
- **E2E (Linux/CachyOS on JordyBox)**: SSH with owner approval (or a committed runbook) — timer + linger probe/fallback, boot simulation, symlinked root, unmounted-root boot scenario, uninstall.

## Out of scope

Windows, gzip request bodies, directory-based console games, Switch update/DLC grouping — additively deferred per spec.md.

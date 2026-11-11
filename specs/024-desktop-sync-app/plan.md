# Implementation Plan: Desktop Sync App

**Branch**: `024-desktop-sync-app` | **Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-desktop-sync-app/spec.md`

## Summary

Replace the downloaded Python scan scripts with a Qt (PySide6) desktop sync app in a new top-level `jordylab-desktop/` codebase. The app reuses the existing `gamecatalog-scanner` Python code as its shared scan core (identity parity inherited, not re-ported), connects machines through a browser sign-in with loopback callback issuing the existing machine token, syncs Steam and EmuDeck on a schedule against the unchanged server-authoritative ingest API, and updates itself from GitHub Releases with one click. P2 adds an account-level theme preference (web + desktop), a `SUNSHINE` source type read from Sunshine's `apps.json`, and Windows. Backend changes: theme preference in `identity`, a second OAuth public client for the desktop callback, `SourceType.SUNSHINE` + parser in `gamecatalog`; frontend changes: account-wide theme setting and an admin download page.

## Technical Context

**Language/Version**: Python 3.12 for the desktop app (PyInstaller-bundled; no system-Python dependency). The shared scan core keeps its Python 3.9 floor until the bundle retires it. Backend: Java 25 / Spring Boot 4.1.1. Frontend: Angular 22 / Nx 23 (unchanged).

**Primary Dependencies**: PySide6 (Qt widgets), PyInstaller (packaging), `tufup` or a hand-rolled verified swap (self-update — decide in Phase 0 research), `httpx` or stdlib `urllib` for ingest HTTP (reuse the scanner's existing stdlib client first). Backend: Spring Authorization Server (existing, spec 021). No new AI dependencies.

**Storage**: No new app-side storage beyond the existing token cache (`~/.config/jordylab/scan/token.json`) and a small app config (schedule, library roots). Backend: `identity` schema gains the per-account theme preference; `gamecatalog` schema gains the `SUNSHINE` source-type value (enum + parser, no new tables).

**Testing**: pytest + pytest-cov for `jordylab-desktop` and the shared core (the scanner's existing suite keeps running against the shared core — parity gate). Backend: JUnit 5 / AssertJ / Testcontainers per repo convention. Frontend: Vitest + Spectator. End-to-end: the app against the running backend using real scan data (Validation Data rule: never hand-seed).

**Target Platform**: macOS (aarch64) and Linux (x86_64) first; Windows with US5. Small windowed app (~440×640 per design), starts on login, runs in background.

**Project Type**: Desktop application (new top-level codebase) + additive changes to the existing web monolith and web app.

**Performance Goals**: Scan cadence keeps today's behaviour (server decides whether a scan is needed; unchanged content uploads nothing). UI stays responsive during scans (scan work off the UI thread). Update download does not block syncing.

**Constraints**: Identity continuity is non-negotiable — fingerprints, grouping and normalize/platform rules must behave byte-identically to the current scanner (protected by its existing pytest fixtures). The server stays authoritative on change detection, adoption and the suspect-shrink guard. Admin-only machine setup. macOS unsigned/ad-hoc (Gatekeeper right-click on first install).

**Scale/Scope**: Household scale: 2–3 machines, one admin account. ~5 desktop screens (per design: Connect, Status, Syncing, Failed, Empty, Update, Update Failed, Appearance), 2 web surfaces (theme setting, download page), ~3 backend areas (identity preference, OAuth client, SUNSHINE parser).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | PASS | Full descriptive names, early returns; Python code follows the scanner's existing style guide (`ruff`, `target-version` raised for the app) |
| II. Fail Fast, No Silent Failures | PASS | Update failures keep the old version and surface the error with retry (FR-007); sync failures show reason (US1.4); no swallowed exceptions in the updater |
| III. Immutable, Builder-First Design | PASS | Applies to Java changes (identity preference, SUNSHINE parser follow existing entity conventions); Python side uses the scanner's existing patterns |
| IV. Testing Discipline | PASS | The scanner's pytest suite is the parity gate and keeps running against the shared core; new app code gets pytest coverage ≥80% (scanner convention); backend/ frontend follow repo test rules |
| V. Language & Tooling Currency | PASS | Python 3.12 + current PySide6; backend/frontend stacks unchanged. Qt+Python is the owner's deliberate choice over Tauri+Rust (ticket D2) — documented deviation from "newest stack" in favour of reusing tested code |

**Verdict**: PASS. No violations requiring Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/024-desktop-sync-app/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
jordylab-desktop/                     # NEW top-level codebase (ticket D1)
├── pyproject.toml                    # PySide6, PyInstaller, updater dep; ruff + pytest config
├── AGENTS.md                         # conventions for the app (Qt patterns, packaging, updater rules)
├── src/jordylab_desktop/
│   ├── app.py                        # QApplication bootstrap, window, tray/autostart
│   ├── screens/                      # Connect, Status, Update, Appearance (per docs/design/JOR-78/)
│   ├── services/
│   │   ├── sync_service.py           # schedule + Sync now, runs scan off the UI thread
│   │   ├── auth_service.py           # browser sign-in + loopback callback, token store
│   │   ├── update_service.py         # check, download, verify, swap, restart (FR-006/007)
│   │   └── theme_service.py          # account theme preference → QSS
│   └── platform/                     # autostart, paths, per-OS bits
├── core/                             # shared scan core (vendored/extracted from gamecatalog-scanner)
│   └── jordylab_scan_core/           # normalize, platforms, grouping, manifest, fingerprint, paths
└── tests/                            # pytest: services + parity suite (fixtures from gamecatalog-scanner)

gamecatalog-scanner/                  # keeps its identity as the source of truth for scan rules;
                                      # core is extracted/shared, not ported (ticket D2)

jordylab-be/src/main/java/dev/jordy/jordylab/
├── identity/                         # + theme preference (per-account store + endpoints)
├── gamecatalog/                      # + SourceType.SUNSHINE, SunshineLibraryParser, ingest handling
└── mcp/ pattern → new OAuth public client config for the desktop callback (spec 021 pattern)

jordylab-fe/libs/settings/            # theme setting becomes account-wide (replace localStorage)
jordylab-fe/apps/jordylab/...         # + admin download page (latest installers per platform)
```

**Structure Decision**: Three existing sub-projects gain additive changes; the desktop app is a new fourth top-level codebase with a `src/` layout and its scan core extracted from `gamecatalog-scanner` (shared, not duplicated). The backend changes ride the existing module boundaries (`identity`, `gamecatalog`), no new modules.

## Complexity Tracking

No constitution violations. Recorded trade-offs live in the JOR-78 ticket Design notes (D1–D9): Qt+Python over Tauri+Rust (parity safety over updater convenience — updater kept in scope anyway), unsigned macOS, script endpoint retirement deferred to a final cleanup.

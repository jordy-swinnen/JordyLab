# Phase 0 Research: Desktop Sync App

**Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md) | **Input**: JOR-78 ticket Design notes (D1–D9)

All NEEDS CLARIFICATION items from the Technical Context are resolved here. Decisions already settled with the owner during refinement/design (recorded in the JOR-78 ticket) are cited, not re-opened.

## R1 — Desktop framework and scan engine (settled: ticket D2)

- **Decision**: Qt (PySide6) windowed app in Python; the app consumes `gamecatalog-scanner`'s `jordylab_scan` package as a **path dependency** (no code moves — the rules stay in their existing files, shared and never copied), so identity parity is inherited and its pytest suite keeps guarding it.
- **Rationale**: identity continuity (fingerprints → game identity → kept AI descriptions) depends on exact parity with the scanner's rules; reusing the tested code inherits that parity and its pytest suite. Python is already a repo language (garmin-sync-service). Owner decision 2026-11-11, revising an earlier Tauri+Rust choice.
- **Alternatives considered**: Tauri 2 + Rust port (parity risk, third stack); Tauri + Python sidecar (two-runtime glue); Qt WebEngine (web themes carried exactly but ~200 MB bundle); Electron (rejected earlier for footprint).

## R2 — Self-update mechanism (settled: ticket D3 — kept in scope; mechanism resolved here)

- **Decision**: Use **`tufup`** (The Update Framework for Python) if its GitHub-Releases integration fits; otherwise a hand-rolled verified swap: download to temp → verify against a signature/hash manifest published with the release → swap binary → restart → keep previous version until the new one starts; any failure keeps the old version and offers retry (FR-007).
- **Rationale**: FR-006/007 need signed, restart-safe updates; `tufup` provides TUF security without building key management by hand. The fallback is small and testable. The owner chose to attempt the updater despite the Qt switch ("try the self-updater anyways").
- **Alternatives considered**: `pyupdater` (largely unmaintained); `wolfwood/updater` style hand-rolled only (more code, weaker verification story); degrading to "check + open download page" (rejected — halves the ticket's value; fallback needs owner sign-off if the updater proves unworkable).
- **Open sub-decision for /speckit-tasks**: pin `tufup` after a spike that proves download → verify → swap → restart on macOS and Linux; if the spike fails, implement the hand-rolled flow.

## R3 — Machine-token bootstrap (settled: ticket D4)

- **Decision**: Browser sign-in + loopback callback. The app opens the JordyLab connect page (`/connect/desktop`) in the system browser; the admin signs in with the existing session, the page creates the machine token through the existing `POST /api/identity/machines` endpoint and redirects to the app's loopback with the token and the app's `state`. Admin-only through the route guard. (Simplified from an earlier second-OAuth-client plan: the machine token is what the app needs, and the existing endpoint already issues it.)
- **Rationale**: no secret through the clipboard; passkeys work in the browser; the public-client + PKCE pattern is proven in the codebase.
- **Alternatives considered**: paste token from Settings → Machines (rejected: secret via clipboard); in-app username/password form (rejected: duplicates auth flows, CSRF/session handling is browser-shaped).
- **Contract**: see `contracts/auth-connect.md`.

## R4 — Packaging and distribution (settled: ticket D5 + D8)

- **Decision**: PyInstaller one-file/one-dir builds per platform (macOS aarch64, Linux x86_64 first; Windows with US5). Tag-triggered GitHub Actions workflow publishes installers + an update manifest to GitHub Releases. macOS unsigned/ad-hoc (Gatekeeper right-click on first install — updates keep working because the updater swaps the app bundle in place).
- **Rationale**: PyInstaller is the standard Python bundler and covers all three targets; releases never touch the production VPS. Owner accepted unsigned macOS for now.
- **Alternatives considered**: `briefcase` (heavier toolchain); Homebrew tap (macOS-only, worse for Windows later); notarized macOS (cost deferred).

## R5 — Theme preference storage (settled: ticket D6)

- **Decision**: Per-account preference in the `identity` module (one of six themes or `system`), exposed through the existing user endpoints; web falls back to the localStorage value once (migration by first read); desktop reads the same preference and renders Qt stylesheets verified against `docs/design/JOR-78/`.
- **Rationale**: `identity` owns users; no new module boundary; both surfaces then share one source of truth (US3).
- **Alternatives considered**: settings module (cross-module read for a user attribute); per-device override (two sources of truth).

## R6 — Sunshine detection (settled: ticket D7)

- **Decision**: Read Sunshine's `apps.json` (`~/.config/sunshine/` on Linux and macOS — verified against LizardByte docs). New `SourceType.SUNSHINE` + `@Component("SUNSHINE")` `LibraryParser` on the backend; entries reconcile onto the existing catalog game (Steam/EmuDeck adoption pattern). The app reports Sunshine only when the config directory exists.
- **Rationale**: FR-010; one-game-many-places model; follows the documented `LibraryParser` extension path.
- **Alternatives considered**: separate games per source (rejected: duplicates); tag-only (rejected: not a source); manual "this is the Sunshine host" declaration (rejected: config presence answers it).

## R7 — Rollout and retirement (settled: ticket D9)

- **Decision**: App and script client coexist during rollout (same ingest endpoints, server authoritative). Final cleanup removes `GET /api/gamecatalog/ingest/client`; `gamecatalog-scanner/` survives as the shared core (D2).
- **Rationale**: safe coexistence, trivial rollback; SC-004 checks the endpoint removal.
- **Alternatives considered**: hard cutover (rejected: no rollback); keep endpoint forever (rejected: FR-012).

## R8 — Qt patterns and testing (resolved here)

- **Decision**: MVC-lite: screens (widgets) call services; services own all state and emit Qt signals; scan work runs in a worker thread/`QThreadPool` so the UI never blocks. pytest covers services and the parity suite; UI smoke tests via `pytest-qt` (offscreen platform in CI).
- **Rationale**: keeps testable logic out of widgets; `pytest-qt` is the standard Qt test harness and runs headless.
- **Alternatives considered**: full QML/QtQuick (overkill for 5 screens); testing through widget screenshots (brittle).

## R9 — Autostart and background behaviour (resolved here)

- **Decision**: Start on login via per-OS mechanisms (launchd agent on macOS, XDG autostart `.desktop` on Linux, Startup folder/registry with US5), keeping the app in the system tray with the window hidden rather than closed.
- **Rationale**: FR-001 "stays in the background after launch and starts on login"; the schedule must run even when the window is not open.
- **Alternatives considered**: daemon + separate UI (two processes, more complexity for a household app).

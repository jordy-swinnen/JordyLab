# Quickstart Validation: Desktop Sync App

**Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Runnable validation scenarios proving the feature end-to-end. Scope touched: desktop app, shared scan core, identity (theme preference, OAuth client), gamecatalog (SUNSHINE), web (theme setting, download page). Run only what changed; full-suite runs are not required.

**Validation Data rule (root AGENTS.md): never hand-seed the database.** Populate the catalog by running the real app/scan against the running backend.

## Prerequisites

- Dev backend running (`./gradlew bootRun` in `jordylab-be/`, Postgres via `podman compose up -d`)
- Dev web app (`jordylab-fe/`, `bun nx serve jordylab`)
- `jordylab-desktop/` set up: `python -m venv .venv && .venv/bin/pip install -e ".[dev]"`
- An admin account and a machine with Steam/EmuDeck libraries (dev Mac or JordyBox)

## Scenario 1 — Parity gate (blocks everything)

Proves the shared scan core still produces the frozen client's exact fingerprints (identity continuity).

```bash
cd jordylab-desktop && .venv/bin/pytest tests/parity -v
```

Expected: all fixtures pass byte-identical digests. **A failure here stops the feature** — no backend wiring until green.

## Scenario 2 — Connect + first sync (US1.1–1.2, FR-002/003)

1. Start the app → **Connect this PC** (compare `docs/design/JOR-78/D_Connect.png`).
2. Sign in as admin in the browser; confirm the machine appears in Settings → Machines and the first sync runs by itself.
3. Expected in the catalog: the machine's Steam and EmuDeck games, tagged by source, marks/descriptions intact, no duplicates (compare counts with a pre-switch snapshot).

## Scenario 3 — Sync now + failure states (US1.3–1.4)

1. Click **Sync now** → outcome shown (up to date / scanned).
2. Kill the backend or go offline → scheduled sync shows the failure with reason; restart the backend → next schedule succeeds.
3. Compare states with `D_Status.png`, `D_Syncing.png`, `D_Failed.png`, `D_Empty.png`.

## Scenario 4 — Self-update (US2, FR-006/007)

```bash
# build two versions and publish a local test release (see contracts/update-manifest.md)
```

1. Install vN; publish vN+1 to a test channel; the app notifies (compare `D_Update.png`).
2. Click **Update** → app restarts on vN+1.
3. Publish a broken asset (bad signature) → app stays on the working version and offers retry (compare `D_Update_Failed.png`).

## Scenario 5 — Theme across surfaces (US3, FR-009)

1. Pick a theme in the desktop app → open the web app signed in → same theme.
2. Pick a theme in the web app → relaunch the desktop app → same theme.
3. Choose "follow device" → web follows the browser device mode, desktop follows the OS mode.
4. Compare with `D_Theme.png`.

## Scenario 6 — Sunshine (US4, FR-010) — on the Sunshine host

1. On the machine with `~/.config/sunshine/apps.json`, run a sync → configured games appear with a Sunshine tag; a game also installed via Steam keeps one catalog entry with both tags.
2. On any other machine → nothing Sunshine-related is reported.

## Scenario 7 — Switchover cleanup (FR-012, SC-004)

After every machine runs the app:

1. Remove the script client download endpoint and the frozen template serving it.
2. Expected: `GET /api/gamecatalog/ingest/client` returns 404; no machine fetches it; existing scans keep working through the app.

## Regression checks (touched areas only)

- Backend: `./gradlew test --tests "*Identity*"` (preference + OAuth client), `./gradlew test --tests "*ScanService*"` (SUNSHINE parser), `./gradlew :test --tests "*ModularityTests*"` after any structural change.
- Web: `bun nx test jordylab` settings/theme + download page specs.
- Desktop: `pytest` (services + `pytest-qt` smoke, offscreen).

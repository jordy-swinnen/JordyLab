# jordylab-desktop

The JordyLab desktop sync app (spec 024, ticket JOR-78). A small Qt window that
connects a machine to JordyLab with the admin account, keeps its Steam and
EmuDeck libraries in the Game Catalog on a 6-hour schedule, and updates itself
from GitHub Releases.

The scan rules are **not** reimplemented here: the app imports the
`jordylab_scan` package from `core/` (the single source of truth for
normalization, grouping and fingerprints). Identity parity — same fingerprints,
same grouping — is what keeps existing games, marks and AI descriptions intact,
so never fork those modules.

## Commands

```bash
python3.12 -m venv .venv
.venv/bin/pip install -e ".[dev]"
.venv/bin/pytest                       # services + core rules + parity gate
.venv/bin/ruff check . && .venv/bin/ruff format --check .
.venv/bin/python -m jordylab_desktop   # run the app (from this directory)
```

The tests import `jordylab_scan` through pytest's `pythonpath` setting (`core`);
the editable install includes it, so a plain run needs no extra step.

## Layout

- `src/jordylab_desktop/services/` — Qt-free logic (auth, sync, update, theme,
  config). Keep Qt out of here so tests run headless.
- `src/jordylab_desktop/screens/` — widgets only; they render service state and
  call service methods.
- `src/jordylab_desktop/detect/` — Steam/EmuDeck/Sunshine discovery.
- `src/jordylab_desktop/platform/` — per-OS autostart and paths.
- `core/jordylab_scan/` — the scan rules (normalize, grouping, fingerprints, payload):
  the single source of truth, imported by the app and guarded by its tests.
- `packaging/` — PyInstaller specs and the release workflow input.
- `tests/core/` — the rules' own suite; `tests/parity/` — the gate: the app's
  collection and payloads must match the core rules exactly.

## Rules

- **Parity gate**: `tests/parity` must stay green. A fingerprint difference is
  silent data damage, not a cosmetic bug.
- The server stays authoritative: always ask `/ingest/check` before uploading.
- Never print or log the machine token (`~/.config/jordylab/scan/token.json`).
- Updates install only after the user clicks Update; a failed update keeps the
  running version.
- Designs live in `docs/design/JOR-78/`; the window is ~440 × 640 and uses the
  six JordyLab themes as Qt stylesheets (`src/jordylab_desktop/themes/`).

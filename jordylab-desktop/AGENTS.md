# jordylab-desktop

The JordyLab desktop sync app (spec 024, ticket JOR-78). A small Qt window that
connects a machine to JordyLab with the admin account, keeps its Steam and
EmuDeck libraries in the Game Catalog on a 6-hour schedule, and updates itself
from GitHub Releases.

The scan rules are **not** reimplemented here: the app imports the
`jordylab_scan` package from `../gamecatalog-scanner/src` (the single source of
truth, frozen into the served script template by `tools/build_client.py`).
Identity parity — same fingerprints, same grouping — is what keeps existing
games, marks and AI descriptions intact, so never copy those modules into this
tree.

## Commands

```bash
python3.12 -m venv .venv
.venv/bin/pip install -e ".[dev]"
.venv/bin/pip install -e ../gamecatalog-scanner   # the shared scan rules; the app imports jordylab_scan
.venv/bin/pytest                       # services + parity gate
.venv/bin/ruff check . && .venv/bin/ruff format --check .
.venv/bin/python -m jordylab_desktop   # run the app (from this directory)
```

The tests import `jordylab_scan` through pytest's `pythonpath` setting
(`../gamecatalog-scanner/src`), but running the app needs the package installed
in the venv (the command above).

## Layout

- `src/jordylab_desktop/services/` — Qt-free logic (auth, sync, update, theme,
  config). Keep Qt out of here so tests run headless.
- `src/jordylab_desktop/screens/` — widgets only; they render service state and
  call service methods.
- `src/jordylab_desktop/detect/` — Steam/EmuDeck/Sunshine discovery.
- `src/jordylab_desktop/platform/` — per-OS autostart and paths.
- `packaging/` — PyInstaller specs and the release workflow input.
- `tests/parity/` — the gate: payloads and fingerprints must match the scanner.

## Rules

- **Parity gate**: `tests/parity` must stay green. A fingerprint difference is
  silent data damage, not a cosmetic bug.
- The server stays authoritative: always ask `/ingest/check` before uploading.
- Never print or log the machine token (`~/.config/jordylab/scan/token.json`).
- Updates install only after the user clicks Update; a failed update keeps the
  running version.
- Designs live in `docs/design/JOR-78/`; the window is ~440 × 640 and uses the
  six JordyLab themes as Qt stylesheets (`src/jordylab_desktop/themes/`).

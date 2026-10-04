# gamecatalog-scanner

Resident Python client for the Game Catalog (feature 003). Standard-library only
at runtime; the package is frozen by `tools/build_client.py` into a single file
served by `jordylab-be` (`src/main/resources/scripts/jordylab-scan-template.py`).

## Commands

```bash
python -m venv .venv && .venv/bin/pip install -e ".[dev]"
.venv/bin/python -m pytest --cov=src
.venv/bin/ruff check . && .venv/bin/ruff format --check .
.venv/bin/python tools/build_client.py            # regenerate the frozen template
.venv/bin/python tools/build_client.py --check    # fail on drift
.venv/bin/python tools/build_client.py --local    # render local defaults for selftest
.venv/bin/python ../jordylab-be/src/main/resources/scripts/jordylab-scan-template.py --selftest
```

## Python style

- **Target Python 3.9** (macOS system Python floor). No `match`, no runtime
  `X | Y` unions — use `typing.Optional`/`Union`. `ruff` `target-version = "py39"`.
- Stdlib only at runtime. Dev deps: `pytest`, `pytest-cov`, `ruff` only.
- `src/` layout; tests in `tests/`; `ruff` for lint + format.

## Architecture notes

- Extracted PS3 discs (`<game>/PS3_GAME/...`) are grouped into one game in `grouping.py` (client-only; the server parser is
  just the path-inference backstop and does not know that layout; spec 011 BUG-054).
- `normalize.py` and `platforms.py` are **verbatim ports** of the server's
  `EmuDeckLibraryParser` / `TextSanitizer` rules. Do not "improve" them —
  identity continuity depends on exact parity.
- The canonical fingerprint (see `specs/003-gamecatalog-python-scanner/data-model.md`)
  hashes metadata only: `paths`, `manifest_contents`, `games`, with
  `sort_keys=True`, `separators=(",", ":")`, `ensure_ascii=True`, prefixed
  `sha256:`. The client never decides invalidation — the server does.
- The client never reads file contents to decide whether to scan (only KB-sized
  Steam manifests and playlist/disc text for grouping).
- Steam relpaths are namespaced `libraryfolders/<n>/steamapps/appmanifest_<appid>.acf`
  so they still satisfy the server regex `.*steamapps/appmanifest_(\d+)\.acf$`.
- Non-interactive runs never start the device flow: a failed refresh exits 2.
- Exit codes: 0 ok/no-change, 2 re-auth, 3 network, 4 server rejected/413,
  5 partial (skipped configured roots), 6 config/fatal.

## Freeze rules

- Fixed module order, LF endings, deterministic output.
- Intra-package imports are stripped; the flattened file resolves names globally.
- The frozen file **begins** with the rendered constants header. `--selftest`
  ignores that header and runs fully offline (no network, no Keycloak). The
  committed template keeps `${...}` placeholders; `--local` renders defaults.
- No literal `${` may appear outside the header (use `PLACEHOLDER_MARK`).

## Tests

- pytest + pytest-cov, target ≥80% on `src`.
- Cover: manifest/digest (NFD/NFC, size/mtime, symlink loop, realpath dedupe),
  normalize/platforms parity, grouping (steam multi-library, m3u, cue+bin, gdi,
  chd, disc sets), auth (local `http.server` mock), schedule text (no `${`),
  CLI exit codes, frozen-artifact build/compile.

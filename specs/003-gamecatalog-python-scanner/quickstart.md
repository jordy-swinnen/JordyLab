# Quickstart: Game Catalog Python Scanner Validation

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contract**: [ingest-api.md](./contracts/ingest-api.md)

---

## Prerequisites

- Linux (CachyOS) and/or macOS; Python 3.9+ (macOS system Python is sufficient)
- Backend running (`jordylab-be`, PostgreSQL + Keycloak via `podman compose up -d`)
- Realm imported with the least-privilege `gamecatalog-script` client (offline access enabled)
- A fake library on the test machine (steam `steamapps/` with manifests; `roms/snes/` with tagged names, an `.m3u`, a `.cue`+`.bin`, a `.gdi`, a standalone `.chd`)

## 1. Backend tests

```bash
cd jordylab-be
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true
./gradlew build
```

Expected: green, JaCoCo gate met. Covers check semantics, shrink guard (empty + fraction + force), machine adoption, client `games[]` validation, controller/security for `/check` and `/client`.

## 2. Client package tests + frozen artifact

```bash
cd gamecatalog-scanner
python -m pytest --cov=src                 # unit: manifest/digest (NFD), grouping, auth, schedule, exit codes
ruff check . && ruff format --check .
python tools/build_client.py --check       # drift: frozen artifact == committed template
python ../jordylab-be/src/main/resources/scripts/jordylab-scan-template.py --selftest   # under 3.9 and 3.13
```

## 3. End-to-end (dev Mac)

1. Download `jordylab-scan-emudeck.py` / `-steam.py` from the Sources page (or `GET /ingest/client`).
2. `python jordylab-scan-steam.py login` → complete the device grant in a browser (agent-browser can drive it) → token cached.
3. `python jordylab-scan-steam.py scan --path /tmp/fake-lib/steam` → `APPLIED`, added counts. Verify the decoded token carries **only** `gamecatalog-scanner`.
4. Re-run unchanged → `scanNeeded: false`; **zero upload bytes**; `last_checked_at` advanced (check via `/sources`).
5. Touch a manifest → re-run → upload + reconcile. `scan --force` also uploads.
6. EmuDeck fixtures: `.m3u` and `.cue`+`.bin` each yield **one** game; the standalone `.chd` yields one; components never appear standalone. Identity matches the golden refs recorded from the 002 server parsers.
7. NFD-named file → one game, same identity as its NFC twin.
8. Reality check: rename a manifest → re-run → game hidden (`UNINSTALLED`, row retained); restore → same `id`.
9. Shrink guard: point `--path` at a near-empty dir → `SNAPSHOT_SHRINK_SUSPECT`, no change; repeat with `--force` → applied. An entirely empty root is skipped by the client (nothing uploaded) and reported.
10. `install` → LaunchAgent written (`RunAtLoad`); `status` reports last run; `uninstall` removes it and revokes the session.

## 4. End-to-end (Linux / CachyOS on JordyBox)

Run over SSH (owner approval) or per the committed runbook:

1. `install` → systemd user service + timer written; linger probe result reported (fallback to login-triggered unit if polkit denies self-linger).
2. Simulate boot: `systemctl --user start jordylab-scan.service` → scan runs; `journalctl --user -u jordylab-scan*` shows the run.
3. Symlinked EmuDeck root → scanned, no loop, no duplicates.
4. Unmounted/renamed library root at "boot" → client skips + reports; catalog unchanged (server shrink guard not even reached).
5. `uninstall` → units removed, linger state reported, session revoked.

## 5. Production smoke

Deferred with 002 production deploy: run the client on JordyBox against the real Steam/EmuDeck roots over the public URL and confirm the timer keeps the catalog current.

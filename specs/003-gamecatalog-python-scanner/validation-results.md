# Game Catalog Python Scanner — Validation Results

**Date**: 2026-09-26
**Feature**: 003-gamecatalog-python-scanner
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contract**: [contracts/ingest-api.md](./contracts/ingest-api.md)

Records execution of [quickstart.md](./quickstart.md) plus the agent-browser UI pass.

## Summary

| # | Step | Result |
|---|------|--------|
| 1 | Backend `./gradlew build` (Testcontainers + JaCoCo) | PASS |
| 2 | Client pytest + ruff + frozen-artifact drift/selftest | PASS (129 tests, 85%) |
| 3 | Realm least-privilege + offline token | PASS (fixed during validation) |
| 4 | Client end-to-end on macOS (device grant, check, scan, grouping) | PASS |
| 5 | Frontend UI via agent-browser | PASS |
| 6 | Linux/CachyOS E2E on JordyBox (timer/linger, symlinked root, unmounted boot) | Deferred — runbook in quickstart §4 |

## 1. Backend

```
$ DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') \
  TESTCONTAINERS_RYUK_DISABLED=true ./gradlew build
BUILD SUCCESSFUL
```

Green including the two new Flyway migrations (`V20260926002__gamecatalog_client_check.sql`, `V20260926003__gamecatalog_nfc_normalize.sql`), the check/shrink-guard/machine-adoption service tests, `ClientServiceTest`, and the `/check` + `/client` controller tests.

## 2. Client package + frozen artifact

```
$ .venv/bin/python -m pytest --cov=src      # 129 passed, 85% coverage
$ .venv/bin/ruff check . && ruff format --check .
$ .venv/bin/python tools/build_client.py --check
OK: frozen client is up to date
$ /usr/bin/python3 ../jordylab-be/src/main/resources/scripts/jordylab-scan-template.py --selftest
selftest ok: 2 game(s), digest sha256:f225d4b…
```

The frozen artifact is Python 3.9 compatible and its only `${` occurrences are the six rendered-header placeholders.

## 3. Realm least privilege + offline token

Device grant with `scope=openid offline_access`; after the fix below, the decoded access token is:

```
roles: ['gamecatalog-scanner', 'offline_access']
scope: openid offline_access
refresh_expires_in: 0        # offline token (no expiry)
```

The consent screen lists only **Offline Access** (no profile/email/user-roles scopes).

### Bugs found and fixed in this step

| Bug | Fix |
|-----|-----|
| The imported `gamecatalog-script` client had **no default client scopes**, so the `realm_access` mapper never ran and tokens carried no roles (the `/scan` and `/check` calls would have been 403). | Export: realm `defaultDefaultClientScopes`/`defaultOptionalClientScopes`, and client `defaultClientScopes: ["roles"]`. |
| The role scope mapping didn't import. | Export uses the canonical `scopeMappings` (`{"client": "gamecatalog-script", "roles": […]}`) — verified against a `POST /partial-export` of the configured realm. |
| Offline tokens were denied (`not_allowed`). | The user must carry the `offline_access` realm role; added to the user's `realmRoles` in the export. |

## 4. Client end-to-end (macOS)

Fixture `/tmp/fake-lib-003`: `roms/snes/Super Mario World (USA) (Rev 1).sfc`, `roms/snes/Mega_Man_X.smc`, `roms/psx/Final Fantasy VII.{bin,cue}`, `steam/steamapps/appmanifest_620.acf`.

```
$ python -m jordylab_scan scan --library EMUDECK --path /tmp/fake-lib-003/roms
[jordylab] EMUDECK scan APPLIED: {"submitted": 3, "added": 0, "updated": 0, "removed": 0, "rejected": 0}

$ python -m jordylab_scan scan --library EMUDECK --path /tmp/fake-lib-003/roms   # unchanged
[jordylab] EMUDECK library unchanged since the last scan; nothing uploaded

$ touch roms/snes/Mega_Man_X.smc && python -m jordylab_scan scan …              # one file changed
[jordylab] EMUDECK scan APPLIED: {"submitted": 3, …}                            # digest change triggers upload
```

Verified in the database:

- Grouping: `Final Fantasy VII` is **one** PlayStation game (the `.bin` is a component, not a separate game); `Super Mario World` and `Mega Man X` are SNES.
- Normalization: tags stripped and underscores spaced (`Super Mario World`, `Mega Man X`).
- `scan_source.machine_id` set, `last_client_digest` recorded, `ingest_version = 1`; the second run returned `NO_CHANGE` (no payload built, no upload).
- Steam run adopted the pre-existing `Jordys-MacBook-Pro:STEAM` row (identity continuity — Portal 2 unchanged, `added: 0`).

### Bug found and fixed

| Bug | Fix |
|-----|-----|
| A successful scan logged nothing (silent on `APPLIED`/`NO_CHANGE`), so a boot-time run left no useful journal trace. | `run_scan` now logs the outcome + counts, and the unchanged path logs "nothing uploaded". |

## 5. Frontend via agent-browser

- Logged into the host app through Keycloak (`jordy`).
- `/games/sources`: both library cards show **"Download client"**; each source row shows `… installed · last sync … · checked …`.
- Clicking **Download client** issued `GET /api/gamecatalog/ingest/client?libraryType=steam` → **200** (captured from the browser network log; blob download itself is a browser side effect not exposed by the CDP download API).
- `/games`: **4 GAMES** — `Final Fantasy VII` (PlayStation), `Mega Man X` (SNES), `Portal 2` (Steam), `Super Mario World` (SNES) — confirming client-ingested data reaches the grid with correct per-platform badges, including the cue-grouped PlayStation entry.

Screenshots: `/tmp/jordylab-e2e/003-sources.png`, `/tmp/jordylab-e2e/003-grid.png`.

## 6. Deferred

Linux/CachyOS E2E on JordyBox (systemd user timer + linger probe/fallback, symlinked EmuDeck root, unmounted-root boot scenario, `uninstall`) — runbook in quickstart §4; to be executed on the real machine.

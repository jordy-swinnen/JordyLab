---
description: "Task list for Game Catalog Python Scanner (replaces the shell scan script)"
---

# Tasks: Game Catalog Python Scanner

**Input**: Design documents from `specs/003-gamecatalog-python-scanner/`

**Prerequisites**: [spec.md](./spec.md), [plan.md](./plan.md), [data-model.md](./data-model.md), [contracts/ingest-api.md](./contracts/ingest-api.md), [quickstart.md](./quickstart.md)

**Scope (003)**: Linux + macOS only. Deferred additively (see spec "Out of Scope"): Windows, compressed uploads, directory-based console games, Switch update/DLC grouping.

**Tests**: INCLUDED — entity tests are definition-of-done per `jordylab-be/AGENTS.md`; client package + frozen-artifact selftest; JaCoCo 80% and pytest coverage gates.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files)
- **[Story]**: US1–US4 from spec.md
- Backend root: `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog` (tests mirror under `src/test/...`)
- Client root: `gamecatalog-scanner/src/jordylab_scan` (tests under `gamecatalog-scanner/tests`)

---

## Phase 0: Scaffold

- [x] T001 Feature branch `003-gamecatalog-python-scanner` (cut from the 002 branch; includes PR #11)
- [x] T002 Spec, plan, data-model, quickstart, contract, checklist under `specs/003-gamecatalog-python-scanner/`

## Phase 1: Backend (blocks the client)

### Data
- [ ] T003 Flyway `V<date>__gamecatalog_client_check.sql`: add `machine_id`, `last_client_digest`, `ingest_version` (NOT NULL DEFAULT 0), `last_checked_at` to `gamecatalog.scan_source`
- [ ] T004 Flyway guarded NFC migration on `gamecatalog.game` (collision pre-check → `RAISE EXCEPTION`, else idempotent `normalize(..., nfc)` UPDATE)
- [ ] T005 [P] `ScanSource` new fields + named mutations (`adoptMachine`, `recordClientDigest`, `recordCheck`) + `ScanSourceTest`/builder updates

### Contract & service
- [ ] T006 [P] `ScanRequest` gains `machineId`, `clientDigest`, `force`, `games`; new `ClientGame` record; bean validation
- [ ] T007 [P] `ScanCheckRequest` / `ScanCheckResponse` records
- [ ] T008 `ScanService`: `CURRENT_INGEST_VERSION`; resolve-or-adopt source by `(machineId, type)` then `(hostname, type)`; digest recorded on `APPLIED`/`NO_CHANGE` only; pre-cutover digest-less scan → WARN + clear stored digest
- [ ] T009 `ScanService.submitCheck`: scan-needed decision (digest / ingest version / last outcome / source disabled) + `last_checked_at`
- [ ] T010 `ScanService`: shrink guard (`SNAPSHOT_SHRINK_SUSPECT`: empty set always, or > `max-shrink-fraction` of installed) with `force` override; `GameCatalogProperties.Scan` gains `maxShrinkFraction`
- [ ] T011 `ScanService`: prefer client `games[]` when present (validated/sanitized identically); else existing path-inference parsers
- [ ] T012 NFC-normalize `externalRef`/`title` at intake

### REST & security
- [x] T013 `IngestController`: `POST /check` (the `/client` endpoint + `/script` removal happen in Phase 5 with the frozen artifact)
- [ ] T014 `ClientService` (Phase 5, with the frozen template — replaces `ScriptService`)
- [x] T015 `SecurityConfig`: `POST /check` → `gamecatalog-scanner`; `GET /client` gate added with T014; `SecurityConfigTest` updated

### Tests & config
- [ ] T016 [P] `ScanServiceTest` additions: check decision matrix, shrink guard (empty/fraction/force), digest recording rules, machine adoption, client games, NFC
- [ ] T017 [P] `IngestControllerTest` additions: `/check` 200/400/403, `/client` content-type/filename/400
- [ ] T018 [P] `ClientServiceTest`: placeholder rendering, literal escaping (hostile values), no secrets, case-insensitive library type, unknown type rejected
- [ ] T019 [P] `GameCatalogModuleTest` still green; `ModularityTests` green
- [ ] T020 Realm export: `fullScopeAllowed=false` on `gamecatalog-script` + `scopeMappings` for `gamecatalog-scanner` only; verify offline access available (attach if missing)
- [ ] T021 Contract doc `specs/002-game-catalog/contracts/ingest-api.md` points to the 003 supersession; root/be AGENTS scan-flow text updated

## Phase 2: Client core (US1–US3)

- [ ] T022 Project skeleton: `gamecatalog-scanner/` (`src/jordylab_scan`, `tests`, `pyproject.toml` dev deps only, `AGENTS.md`)
- [ ] T023 [US1] `auth.py`: device grant (`openid offline_access`), refresh, atomic 0600 token cache, revocation, non-interactive no-prompt rule
- [ ] T024 [US1] `lock.py`: cross-process lock around refresh and the whole run
- [ ] T025 [US1] `config.py` + `paths.py`: explicit roots config; per-OS default roots incl. Flatpak Steam
- [ ] T026 [US2] `manifest.py`: symlink-safe walk (followlinks + realpath cycle set), realpath root dedupe, NFC, `(relpath,size,mtime_ns)`, digest
- [ ] T027 [US2] `payload.py`: build `{paths, manifestContents, games}`; canonical digest; 1 MB guard
- [ ] T028 [US1/US2] `api.py`: urllib check/scan with timeouts, TLS on, proxy env, distinct exit codes
- [ ] T029 [US1] `__main__.py` CLI: `scan` (default) / `login` / `install` / `uninstall` / `status` / `version`; `--path`, `--force`, `--library`
- [ ] T030 [US1] `status.py`: last-run state + human messages per exit code
- [ ] T031 [US2] Retry/backoff for boot-time DNS/connection failures (≤5 tries, ≤5 min)
- [ ] T032 [P] [US1] Tests: auth (mocked HTTP), lock, paths/config, exit-code mapping
- [ ] T033 [P] [US2] Tests: digest stability, NFD fixture, mtime/size sensitivity, symlink loop, explicit-vs-probed roots

## Phase 3: Grouping & identity (US3)

- [ ] T034 [P] [US3] `normalize.py` + `platforms.py`: verbatim port of server title/platform rules
- [ ] T035 [US3] `grouping.py`: Steam all-library discovery via `libraryfolders.vdf` (namespaced relpaths); `.m3u`; `.cue`/`.gdi` (+ referenced components); standalone vs referenced `.chd`; identity anchored to existing refs
- [ ] T036 [P] [US3] Grouping fixtures + tests: steam multi-library, m3u, cue/bin, gdi, chd standalone/referenced
- [ ] T037 [US3] Golden-refs parity test: fixture tree through the live 002 parsers (recorded once) vs client payload builder → identical refs/titles/platforms

## Phase 4: Installers (US1)

- [ ] T038 [US1] `schedule.py` Linux: systemd user oneshot + timer (`OnBootSec=5min`, `OnUnitActiveSec=6h`, `RandomizedDelaySec=10min`), linger probe + login-triggered fallback
- [ ] T039 [US1] `schedule.py` macOS: LaunchAgent plist (`RunAtLoad`), TCC caveat surfaced by `status`
- [ ] T040 [US1] Stable install dir + absolute interpreter path (`sys.executable`) in unit/plist
- [ ] T041 [P] [US1] Tests: generated unit/plist text, install-dir contents

## Phase 5: Freeze, cutover, frontend

- [ ] T042 `tools/build_client.py`: deterministic freeze → `jordylab-be/src/main/resources/scripts/jordylab-scan-template.py`; drift check mode
- [ ] T043 Frozen `--selftest` path; CI runs it under Python 3.9 and 3.13 (Linux + macOS runners)
- [ ] T044 Delete the shell template; `ClientService` served via `/client`; FE source-manager: "Download client" button + last-check line
- [ ] T045 Cutover: `install`/`status` detect + remove old shell-script schedules; report
- [ ] T046 [P] FE spec updates for the source-manager changes

## Phase 6: Validation

- [ ] T047 E2E dev Mac per quickstart §3 (login via agent-browser, check/scan/no-upload, fixtures, NFD, reality check, shrink guard, LaunchAgent install/uninstall)
- [ ] T048 E2E Linux/CachyOS on JordyBox per quickstart §4 (timer+linger, boot simulation, symlinked root, unmounted-root boot, uninstall) — SSH with owner approval or committed runbook
- [ ] T049 Record results in `specs/003-gamecatalog-python-scanner/validation-results.md`

## Phase 7: Polish & PR

- [ ] T050 Backend `./gradlew build` + client pytest/ruff + frozen selftest all green; coverage gates met
- [ ] T051 Docs: root `AGENTS.md` + `jordylab-be/AGENTS.md` scan-flow rewritten to the client model; README updated
- [ ] T052 Open PR to `main` (note stacking on PR #11); watch CI
- [ ] T053 **agent-browser verification of every new user-facing flow** — download the client from the Sources page, complete the device grant, confirm the catalog reflects the new scan (grid/detail/chat/sources), and exercise each new/changed UI surface end-to-end. Record every bug found here in the validation results and fix it before the PR is considered done.

---

## Dependencies & Execution Order

T003–T005 (data) → T006–T015 (service/REST) → T016–T021 (tests/config) block Phase 2. Client core (T022–T033) blocks grouping (T034–T037) and installers (T038–T041). Freeze (T042–T043) requires the client to be complete. `--selftest` is written before the freeze so CI can exercise the frozen artifact immediately. E2E (T047) requires Phases 1–5. Linux E2E (T048) requires an owner-approved JordyBox session.

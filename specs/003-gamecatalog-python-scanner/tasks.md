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

---

## Phase 0: Scaffold

- [x] T001 Feature branch `003-gamecatalog-python-scanner` (cut from the 002 branch; includes PR #11)
- [x] T002 Spec, plan, data-model, research, quickstart, contract, checklist under `specs/003-gamecatalog-python-scanner/`

## Phase 1: Backend

- [x] T003 Flyway `V20260926002__gamecatalog_client_check.sql`: `machine_id`, `last_client_digest`, `ingest_version`, `last_checked_at`
- [x] T004 Flyway guarded NFC migration on `gamecatalog.game`
- [x] T005 [P] `ScanSource` fields + mutations (`adoptMachine`, `recordClientDigest`, `clearClientDigest`, `recordCheck`) + tests
- [x] T006 [P] `ScanRequest` gains `machineId`/`clientDigest`/`force`/`games`; `ClientGame`
- [x] T007 [P] `ScanCheckRequest` / `ScanCheckResponse`
- [x] T008 `ScanService`: `CURRENT_INGEST_VERSION`; machine adoption; digest recording rules; pre-cutover digest clear
- [x] T009 `ScanService.submitCheck`: scan-needed decision + liveness
- [x] T010 `ScanService`: shrink guard (`SNAPSHOT_SHRINK_SUSPECT`) + `force` + `maxShrinkFraction`
- [x] T011 `ScanService`: client `games[]` precedence over parsers
- [x] T012 NFC normalization at intake
- [x] T013 `IngestController`: `POST /check`
- [x] T014 `ClientService` (replaces `ScriptService`): literal-safe rendering + `GET /client`
- [x] T015 `SecurityConfig`: `/check` and `/client` gates; `SecurityConfigTest`
- [x] T016 [P] `ScanServiceTest` additions (check matrix, shrink guard, digest rules, adoption, client games, NFC)
- [x] T017 [P] `IngestControllerTest` additions (`/check`, `/client`)
- [x] T018 [P] `ClientServiceTest` (rendering, escaping, no unresolved placeholders, rejection)
- [x] T019 [P] `GameCatalogModuleTest` + `ModularityTests` green
- [x] T020 Realm export least-privilege (`fullScopeAllowed=false`, `scopeMappings`, `defaultClientScopes: ["roles"]`, offline_access role)
- [x] T021 Contract docs + root/be/frontend AGENTS scan-flow text updated

## Phase 2: Client core

- [x] T022 `gamecatalog-scanner/` project skeleton
- [x] T023 [US1] `auth.py`: device grant (`openid offline_access`), refresh, atomic 0600 cache, revocation, no-prompt rule
- [x] T024 [US1] `lock.py`: cross-process lock
- [x] T025 [US1] `config.py` + `paths.py`: explicit roots, per-OS defaults incl. Flatpak Steam
- [x] T026 [US2] `manifest.py`: symlink-safe walk, realpath dedupe, NFC, digest
- [x] T027 [US2] `payload.py`: canonical digest + 1 MB guard
- [x] T028 [US1/US2] `api.py`: timeouts, TLS on, exit-code mapping
- [x] T029 [US1] `__main__.py` CLI
- [x] T030 [US1] `status.py`
- [x] T031 [US2] Boot retry/backoff
- [x] T032 [P] [US1] Auth/lock/paths/exit-code tests
- [x] T033 [P] [US2] Digest/NFC/symlink/root tests

## Phase 3: Grouping & identity

- [x] T034 [P] [US3] `normalize.py` + `platforms.py` (verbatim server parity)
- [x] T035 [US3] `grouping.py`: Steam multi-library, m3u, cue/gdi, chd
- [x] T036 [P] [US3] Grouping fixtures + tests
- [x] T037 [US3] Golden-refs parity test

## Phase 4: Installers

- [x] T038 [US1] `schedule.py` Linux: systemd user service + timer (`OnBootSec`/`OnUnitActiveSec`), linger probe + fallback
- [x] T039 [US1] `schedule.py` macOS: LaunchAgent `RunAtLoad`
- [x] T040 [US1] Stable install dir + absolute interpreter path
- [x] T041 [P] [US1] Schedule generation tests

## Phase 5: Freeze, cutover, frontend

- [x] T042 `tools/build_client.py` deterministic freeze + `--check`
- [x] T043 Frozen `--selftest` under Python 3.9 (verified locally; CI matrix is a follow-up)
- [x] T044 Delete the shell template; serve `/client`; FE "Download client" + last-check line
- [x] T045 Cutover: `install`/`status` detect + remove old shell schedules
- [x] T046 [P] FE spec updates

## Phase 6: Validation

- [x] T047 E2E macOS (device grant, check/scan/no-upload, grouping, Steam adoption)
- [ ] T048 E2E Linux/CachyOS on JordyBox (timer + linger, boot simulation, symlinked root, unmounted-root boot, uninstall) — runbook in quickstart §4; to run on the real machine
- [x] T049 Results recorded in [validation-results.md](./validation-results.md)

## Phase 7: Polish & PR

- [x] T050 Backend build + client pytest/ruff + frozen selftest green
- [x] T051 Docs: root/be/frontend AGENTS + 002 contract supersession
- [ ] T052 Open PR to `main` (note stacking on PR #11); watch CI
- [x] T053 **agent-browser verification** of the new flows (sources page download, login, grid reflects client scans); bugs found logged in validation-results.md and fixed

---

## Dependencies & Execution Order

Phase 1 blocks Phase 2. Client core blocks grouping and installers. Freeze requires the client; `--selftest` was authored before the freeze. E2E macOS requires Phases 1–5. Linux E2E (T048) requires an owner-approved JordyBox session.

## Bugs found during validation (fixed)

1. Realm export: missing default client scopes → no `realm_access`; wrong scope-mapping key; `offline_access` not on the user → offline tokens denied.
2. Client: a successful scan logged nothing.
3. FE dev-server stuck on a mid-edit compile error (tooling) — resolved by restart; no code change.

---

description: "Task list for the Desktop Sync App (JOR-78 / spec 024)"
---

# Tasks: Desktop Sync App

**Input**: Design documents from `/specs/024-desktop-sync-app/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included where the repo constitution mandates them (parity gate, entity tests) or where the quickstart requires end-to-end validation. Each user story ends with its quickstart scenario run against real scan data (Validation Data rule: never hand-seed).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: New `jordylab-desktop/` codebase skeleton and build wiring

- [x] T001 Create `jordylab-desktop/` project structure per plan.md: `pyproject.toml` (Python 3.12, PySide6, pytest, ruff), `src/jordylab_desktop/` package, `core/`, `tests/`, and `jordylab-desktop/AGENTS.md` with conventions (Qt patterns, packaging, updater rules)
- [x] T002 Configure lint/test tooling in `jordylab-desktop/pyproject.toml` (ruff target py312 for the app, pytest + pytest-cov ≥80% on `src/`, pytest-qt for offscreen UI smoke)
- [x] T003 [P] Create PyInstaller build configuration in `jordylab-desktop/packaging/` (macOS aarch64 + Linux x86_64 spec files, app metadata)
- [x] T004 [P] Create tag-triggered GitHub Actions workflow skeleton `.github/workflows/desktop-release.yml` (build PyInstaller artifacts, publish to GitHub Releases)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared scan core + app shell. **⚠️ CRITICAL**: no user story work begins until the parity gate is green.

- [x] T005 Wire `jordylab-desktop` to consume `gamecatalog-scanner` as a library: add `gamecatalog-scanner/src` to `jordylab-desktop/pyproject.toml` as a path dependency so `jordylab_scan` (normalize, platforms, grouping, manifest, fingerprint, paths) imports unchanged — rules stay byte-identical in `gamecatalog-scanner`, frozen-template build untouched (research R1)
- [x] T006 Build the parity gate: port `gamecatalog-scanner/tests/` fixtures as golden files into `jordylab-desktop/tests/parity/` and verify identical fingerprints/payloads (quickstart Scenario 1) — **a failure here blocks all further work** (research R1, plan constraints)
- [x] T007 App bootstrap in `jordylab-desktop/src/jordylab_desktop/app.py`: QApplication, main window shell (440×640 per design), system tray (window hides, never quits), worker-thread pattern for scan work (research R8)
- [x] T008 App config store in `jordylab-desktop/src/jordylab_desktop/services/config.py` (`~/.config/jordylab/desktop/config.json`: schedule, extra_roots, window state) plus integration with the existing token cache `~/.config/jordylab/scan/token.json` (data-model.md)

**Checkpoint**: Parity green + app shell runs → user stories can begin (US1 first)

---

## Phase 3: User Story 1 - Install once, the machine syncs itself (Priority: P1) 🎯 MVP

**Goal**: Install on macOS/Linux, sign in once via the browser, and the machine's Steam/EmuDeck libraries sync automatically — no script download

**Independent test** (quickstart Scenarios 2 + 3): install on a machine with Steam and EmuDeck libraries, connect it, watch games appear; Sync now works; offline shows the failure with reason

- [x] T009 [US1] Admin connect page `/connect/desktop` in `jordylab-fe/apps/jordylab/src/app/connect-desktop/` (authGuard + admin roleGuard, reads `state`/`port`/`machine`, then hands the machine token back over loopback) — contract: `contracts/auth-connect.md`
- [x] T010 [US1] Reuse the existing `POST /api/identity/machines` token issuance for the connect page (admin-only by the security matrix); no new backend endpoint needed
- [x] T011 [US1] `auth_service` in `jordylab-desktop/src/jordylab_desktop/services/auth_service.py`: open system browser, loopback callback listener, PKCE state/verifier, code exchange, token write to `~/.config/jordylab/scan/token.json` (0600)
- [x] T012 [US1] Connect screen in `jordylab-desktop/src/jordylab_desktop/screens/connect.py` per `docs/design/JOR-78/D_Connect.png`, including non-admin ("Only the admin account can connect a machine.") and callback-timeout error states (contract error table)
- [x] T013 [US1] Library detection in `jordylab-desktop/src/jordylab_desktop/detect/` + `jordylab-desktop/src/jordylab_desktop/platform/`, using the `jordylab_scan` paths module: default Steam and EmuDeck roots per OS plus extra roots from config (FR-003)
- [x] T014 [US1] `sync_service` in `jordylab-desktop/src/jordylab_desktop/services/sync_service.py`: background schedule (default 6h, configurable) + Sync now, off the UI thread, check-then-scan against the unchanged ingest API (`contracts/ingest-check-scan.md`, FR-004/005)
- [x] T015 [US1] Status screen in `jordylab-desktop/src/jordylab_desktop/screens/status.py` per `docs/design/JOR-78/D_Status.png`, `D_Syncing.png`, `D_Failed.png`, `D_Empty.png`: per-source outcome (up to date / scanned / failed with reason / skipped roots), counts, next scheduled check
- [x] T016 [US1] Autostart + background behaviour in `jordylab-desktop/src/jordylab_desktop/platform/`: launchd agent (macOS), XDG autostart `.desktop` (Linux); app runs from login with the window hidden (FR-001, research R9)
- [x] T017 [US1] Edge-case handling in `sync_service` + screens: backend unreachable (show reason, retry next schedule), revoked token ("This machine must be reconnected."), missing/unreadable roots skipped and reported (spec Edge cases)
- [x] T018 [US1] Admin download page (web, FR-008) in `jordylab-fe` (settings/admin area) per `docs/design/JOR-78/W_Downloads.png`: latest installers per platform linking the GitHub Releases assets; stub the assets list until the first release exists
- [ ] T019 [US1] End-to-end validation on the dev Mac against the running backend: quickstart Scenarios 2 + 3 with real scan data; verify marks/descriptions/duplicates unchanged vs a pre-switch snapshot

---

## Phase 4: User Story 2 - Update the app from inside the app (Priority: P1)

**Goal**: Notify + one-click self-update from GitHub Releases; failed updates keep the working version

**Independent test** (quickstart Scenario 4): publish vN+1, see the notification, click Update, app restarts on vN+1; a broken asset leaves the old version running with a retry

- [ ] T020 [US2] Updater spike: prove `tufup` download → verify → swap → restart on macOS and Linux; if it fails, implement the hand-rolled verified swap from research R2; record the outcome in `specs/024-desktop-sync-app/research.md` (decision gate before T021–T022)
- [x] T021 [US2] `update_service` in `jordylab-desktop/src/jordylab_desktop/services/update_service.py`: fetch `update-manifest.json` for the platform, compare versions, schedule checks (`contracts/update-manifest.md`, FR-006)
- [x] T022 [US2] Verified install flow in `update_service`: download to temp, verify sha256 + signature, swap + restart, keep previous version until the new one starts; failure keeps the running version with Retry (FR-007)
- [x] T023 [US2] Update UI in `jordylab-desktop/src/jordylab_desktop/screens/` per `docs/design/JOR-78/D_Update.png` and `D_Update_Failed.png`: notification naming the version, one-click Update, progress, failure + retry
- [x] T024 [US2] Complete `.github/workflows/desktop-release.yml`: per-platform artifacts + `update-manifest.json` + signature produced from the SOPS updater key (CI-only); repo convention for "latest" (ticket D8)
- [ ] T025 [US2] End-to-end validation: quickstart Scenario 4 — two local test releases (good + broken signature); verify Gatekeeper approval survives updates on macOS (plan risk)

---

## Phase 5: User Story 3 - One theme, everywhere (Priority: P2)

**Goal**: Account-level theme preference shared by web and desktop

**Independent test** (quickstart Scenario 5): pick a theme in one surface, see it in the other; `system` resolves per device

- [x] T026 [US3] Theme preference in `jordylab-be/src/main/java/dev/jordy/jordylab/identity/`: per-account store + Flyway migration (`identity` schema, default `system`) + `GET`/`PUT /api/identity/me/preferences` + entity/service tests per repo conventions (`contracts/theme-preference.md`, data-model.md)
- [x] T027 [US3] Web theme setting becomes account-wide in `jordylab-fe/libs/settings/`: read/write the account preference, localStorage fallback-once migration, picker re-labelled as account-wide (`contracts/theme-preference.md`)
- [x] T028 [US3] `theme_service` in `jordylab-desktop/src/jordylab_desktop/services/theme_service.py`: read account preference at sign-in, apply Qt stylesheets, `system` resolves to OS mode, offline start renders last known theme (US3.3)
- [x] T029 [US3] Appearance screen + six Qt stylesheets in `jordylab-desktop/src/jordylab_desktop/screens/appearance.py` + `themes/` per `docs/design/JOR-78/D_Theme.png`; verify all six against the design exports (plan risk: theme fidelity)
- [ ] T030 [US3] End-to-end validation: quickstart Scenario 5 across web + desktop, both directions, plus system on a light and a dark device

---

## Phase 6: User Story 4 - Sunshine detection on the host (Priority: P2)

**Goal**: Games configured in Sunshine appear with their own source tag, reconciled onto the existing catalog game

**Independent test** (quickstart Scenario 6): sync on the Sunshine host → configured games tagged Sunshine; non-host machines report nothing

- [x] T031 [US4] `SourceType.SUNSHINE` + `SunshineLibraryParser` (`@Component("SUNSHINE")`) in `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/` + tests (existing `LibraryParser` extension path, data-model.md)
- [x] T032 [US4] Sunshine reader in `jordylab-desktop/src/jordylab_desktop/detect/sunshine.py`: parse `~/.config/sunshine/apps.json` (Linux + macOS paths, verified LizardByte docs) into client-grouped `games[]` payload entries
- [x] T033 [US4] Source gating in `jordylab-desktop/src/jordylab_desktop/services/sync_service.py`: report `SUNSHINE` only when Sunshine's config directory exists on the machine (ticket D7)
- [ ] T034 [US4] End-to-end validation on the Sunshine host: quickstart Scenario 6 — one catalog game with Steam + Sunshine tags, no duplicates; non-host machine reports nothing

---

## Phase 7: User Story 5 - Windows (Priority: P2)

**Goal**: The same app installs and runs on Windows (Steam library first)

**Independent test**: install on Windows; US1 scenarios 1–4 and US2 scenarios pass

- [x] T035 [US5] Windows support in `jordylab-desktop/src/jordylab_desktop/platform/` and the `jordylab_scan` paths module: paths, autostart (Startup folder/registry), token cache locations
- [x] T036 [US5] Windows packaging + release runner in `jordylab-desktop/packaging/` + `.github/workflows/desktop-release.yml` (Windows build + installer + update-manifest entry + in-app update on Windows)
- [ ] T037 [US5] End-to-end validation on Windows: quickstart Scenarios 2–4 on a Windows PC with a Steam library

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T038 Switchover cleanup in `jordylab-be`: remove `GET /api/gamecatalog/ingest/client` and the frozen-template serving (`src/main/resources/scripts/jordylab-scan-template.py`) once every machine runs the app (FR-012, SC-004) — **last task, after real-machine validation**
- [x] T039 [P] Update repo docs: root `AGENTS.md` structure line + module table (`jordylab-desktop/`, script client retired), `docs/status.md` row + end-goal entry, README.md architecture drawing (Mermaid) — same change as the work (root AGENTS.md rule)
- [x] T040 [P] Finalize `jordylab-desktop/AGENTS.md` conventions from what implementation taught (ruff/pytest commands, packaging, updater rules) and `docs/design/JOR-78/` reference in it
- [ ] T041 Regression pass on touched areas only: backend `./gradlew :test --tests "*ModularityTests*"` + identity/gamecatalog tests, web settings tests + lint, desktop `pytest` green

---

## Dependencies

```text
Phase 1 (T001–T004) ──▶ Phase 2 (T005–T008, parity gate T006 blocks all)
                              │
                              ▼
                     Phase 3 US1 (T009–T019) ──▶ Phase 4 US2 (T020–T025)
                              │                        │
                              │                        └── T038 cleanup waits for real-machine validation (US1+US2 green)
                              ▼
                     Phase 5 US3 (T026–T030)   [independent of US2; can start after US1 or in parallel]
                              │
                              ▼
                     Phase 6 US4 (T031–T034)   [independent; needs only Phase 2]
                              │
                              ▼
                     Phase 7 US5 (T035–T037)   [independent; after US1/US2 scenarios exist to reuse]
```

- **US1 is the MVP**: shippable alone; US2 completes the ticket's core pain.
- US3, US4, US5 are independent of each other and of US2 (they need the app shell + parity gate only).
- T020 (updater spike) gates T021–T023: the spike decides `tufup` vs hand-rolled.
- T038 (endpoint removal) is deliberately last: rollback is "keep using the script" until then (ticket D9).

## Parallel Execution Examples

- **Phase 1**: T003 ∥ T004 after T001–T002 (packaging config ∥ workflow skeleton)
- **US1**: T009 ∥ T013 (backend OAuth client ∥ library detection) after Phase 2; T012 ∥ T014 ∥ T016 after T011 (screens ∥ sync service ∥ autostart)
- **US3**: T026 ∥ T028 (backend preference ∥ desktop theme_service)
- **US4**: T031 ∥ T032 (backend parser ∥ core reader)
- **Polish**: T039 ∥ T040

## Implementation Strategy

**MVP first**: Phase 1 + 2 → US1 alone already removes the manual-update pain on macOS/Linux (quickstart Scenarios 1–3 as the demo). Ship US2 immediately after to complete the ticket's two P1 stories. US3–US5 land as independent increments afterwards.

**Rollout**: app + script client coexist (same ingest endpoints, server authoritative); T038's cleanup runs only after the dev Mac and JordyBox have run the app green — rollback until then is trivial (ticket D9).

---

## Phase 9: Convergence

Appended by `/speckit-converge` after the first real-machine run (2026-11-11). Each item traces to the artifact it leaves unsatisfied.

- [ ] T042 [US3] Persist the desktop theme choice to the account: add `PUT /api/identity/machines/preferences` (machine-token chain) and call it from `jordylab_desktop/window.py::_theme_chosen` via `theme_service`, so a theme picked in the app reaches the web app too per FR-009 (partial)
- [ ] T043 [FR-008] Align the Downloads page asset names with the release workflow (`jordylab-desktop-linux-x86_64.tar.gz`, and add the Windows entry with US5) in `jordylab-fe/apps/jordylab/src/app/downloads/downloads-page.component.ts` per FR-008 (partial)
- [ ] T044 [contract update-manifest] Either verify the release signature in `update_service` (public key embedded, signature produced in CI from the SOPS key) or remove the signature field from the manifest contract and document hash-only verification per `contracts/update-manifest.md` (partial)
- [ ] T045 [US1] Add backend tests for `UserPreferenceController` (session GET/PUT and the machine-token read) and `UserAccountService.changeOwnTheme`/`themeOf` per US1/FR-009 (missing)
- [ ] T046 [US1] Add a Vitest/Spectator spec for `ConnectDesktopComponent` (missing state/port, token callback URL, error state) in `jordylab-fe/apps/jordylab/src/app/connect-desktop/` per US1/FR-002 (missing)
- [ ] T047 [plan R2] Remove the unused `tufup` dependency from `jordylab-desktop/pyproject.toml` (the hand-rolled verified swap was chosen) or wire it, per plan research R2 (unrequested)

# Tasks: Technical Improvements: Oxlint + Agent Hook, AI Integration Conventions and E2E Testing

**Input**: Design documents from `/specs/012-technical-improvements/` (spec.md, plan.md, research.md, data-model.md, contracts/, quickstart.md)

**Prerequisites**: plan.md, spec.md. Clarification answers are recorded in spec.md.

**Tests**: Requested by the spec: hook fixtures (FR-018), the E2E suites themselves (Part C), cleanup proofs (SC-011) and the quickstart validations. No extra unit-test tasks are added where nothing but tooling changes.

**Organization**: grouped by user story. Three parts, independently releasable: **A** (US1-US5), **B** (US6-US9), **C** (US10-US15), plus **US16** (backend currency). Each phase header says which PR/branch it belongs to.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[HANDOFF]**: needs Jordy; the task ends by posting a HANDOFF-## block (see the root rules), never silently skipped
- Every commit carries `Refs: 012/T###`. Agent configuration (hooks, `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.opencode/`, skills, agents, settings) is edited through `/dual-agent-config` (FR-003).
- Commands use `bun`/`bunx`, never `npm`/`npx`. Never print secret values. Never hand-seed rows.
- Version raises (FR-002): the proposal is shown in chat and Jordy confirms before anything is installed.

## Phase 1: Setup (baseline and gates; no code changes)

**Purpose**: measure before touching anything; settle the version gate.

- [X] T001 Time the lint baseline (full `bunx nx run-many -t lint` cold and cached; single-file `bunx eslint libs/shared/auth/src/lib/pkce.ts`; single-file Oxlint is added later) in `jordylab-fe/`, and record the numbers in `specs/012-technical-improvements/research.md` section A3 (FR-005)
- [X] T002 [P] Record the test/build baseline in `specs/012-technical-improvements/research.md`: `bunx nx run-many -t test --all --coverage` result per project (pass count and coverage-gate result), production build and `--configuration=mobile` build bundle sizes, and `bunx nx graph --file=` output saved outside the repo for later diffs (FR-009)
- [X] T003 [P] Check the Node versions that matter: what CI's `test-frontend` job runs, what `deploy/containers/frontend/Containerfile` (`oven/bun:1`) provides for `bunx nx`, and the Mac's `node -v`; record in `research.md` section A1 (FR-007)
- [X] T004 [HANDOFF] Post HANDOFF-## asking Jordy to confirm target versions for steps 1a/1b/1c from the plan's Version approval gate table (Nx 23.2.1, Angular 22.2.1 + TypeScript 6.0, angular-eslint 22.5.0, Analog 2.8.0, ng-packagr 22.2.4, Boot 4.1.1 / Modulith 2.1.1 / Gradle 9.8.0), re-resolved on the day (FR-002)

---

## Phase 2: US1 - Upgrade Nx and Angular without changing behaviour (Priority: P1) 🎯 foundation for Parts A and C

**Goal**: workspace on Nx 23, then on Angular 22, each as its own reviewed PR with identical lint/test/build/graph results.

**Independent Test**: after each PR, run the quickstart "After the upgrade" block; results match the T001/T002 baseline; a deliberate boundary violation fails lint.

### PR 1a: Nx 23 (branch `chore/nx-23-upgrade`, Angular 21.2 and TypeScript 5.9 unchanged)

- [X] T005 [US1] Create branch `chore/nx-23-upgrade` from current `main` in a clean worktree and confirm `bun install --frozen-lockfile` is green before changes (`jordylab-fe/`)
- [X] T006 [US1] Run `bunx nx migrate 23.2.1` in `jordylab-fe/`; review the generated `package.json` and `migrations.json` diff; confirm it does NOT move Angular or TypeScript (if it does, stop and report); record the review in `specs/012-technical-improvements/research.md`
- [X] T007 [US1] `bun install`, then `bunx nx migrate --run-migrations`; review every file each migration changed; keep migration notes in `research.md`; delete `migrations.json` afterwards (FR-006)
- [X] T008 [P] [US1] Add an explicit Node pin (`node-version: 22`, which floats to the newest 22.x and so also satisfies Angular 22's 22.22.3 floor, per T003) with `actions/setup-node` before dependency install in `.github/workflows/build.yml` (`test-frontend` job)
- [X] T009 [P] [US1] Replace `npx prettier` with `bunx prettier` (run from `jordylab-fe/`) in `.claude/hooks/post-edit-format.sh` as its own commit, via `/dual-agent-config`
- [X] T010 [US1] Amend the Nx part of principle V in `.specify/memory/constitution.md` ("Nx 22" becomes "Nx 23"), bump version and "Last Amended" (FR-010)
- [X] T011 [US1] Verify behaviour-neutral: run lint, tests with coverage gate, production build, `--configuration=mobile` build, and `nx graph` diff against the T001/T002 baseline in `jordylab-fe/`; record results (FR-009 definition: same tests, same coverage-gate result, identical graph, bundle sizes within 5%)
- [X] T012 [US1] Prove a deliberate boundary violation still fails: add a scratch import crossing `scope:fna` into `scope:gamecatalog` in a scratch file, run `bunx nx lint`, confirm failure, discard the file (`jordylab-fe/libs/`)
- [X] T013 [US1] Build the frontend image locally (`deploy/containers/frontend/Containerfile`) and confirm `nx build jordylab --configuration=production` works on its Node/Bun runtime; if not, fix the image in this PR
- [X] T014 [US1] Verify spartan generators and the `serve` targets still work (`bunx nx serve jordylab` boots; `bunx nx g @spartan-ng/nx:ui --help` runs) and record in `research.md`
- [X] T015 [US1] Push the branch and open PR 1a with a description listing versions, declined/accepted migrations and the verification record; wait for CI; resolve review comments; merge per the merge-authority rule

### PR 1b: Angular 22 (branch `chore/angular-22-upgrade`, after 1a is merged)

- [X] T016 [HANDOFF] [US1] Post HANDOFF-## asking Jordy to raise local Node to 24.15 or newer (Angular 22 needs `^22.22.3 || ^24.15.0 || >=26`; the Mac has 24.13.1), then confirm `node -v`
- [X] T017 [US1] Create branch `chore/angular-22-upgrade` from updated `main`; re-check the A6 table in `research.md` against the registry on the day and stop-and-report if any tool rejects TypeScript 6.0 or Angular 22 (edge cases in spec)
- [X] T018 [US1] Run the Angular 22 migration in `jordylab-fe/` (`bunx nx migrate latest`; if it does not move `@angular/*`, `bunx nx migrate @angular/core@22`), review `package.json`/`migrations.json`, install, `--run-migrations`, review every migration's file changes; TypeScript moves to `~6.0` here (FR-008)
- [X] T019 [US1] Bump the Angular-coupled tooling in `jordylab-fe/package.json`: `angular-eslint` 22.x, `@analogjs/vite-plugin-angular` and `@analogjs/vitest-angular` 2.8.x, `ng-packagr` 22.x; fix compile and config fallout (`tsconfig.base.json`, per-project `tsconfig.*.json`, `eslint.config.mjs`) (FR-008)
- [X] T020 [US1] Fix TypeScript 6 / Angular 22 fallout in source and specs under `jordylab-fe/libs/` and `jordylab-fe/apps/` until lint, tests and builds are green; no behaviour changes
- [X] T021 [P] [US1] Verified, no file change needed: `.github/workflows/build.yml` already pins `node-version: 22` (floats to the newest 22.x, which meets Angular 22's 22.22.3 floor) and `deploy/containers/frontend/Containerfile` builds on `oven/bun:1`, which has no Node (`bunx nx` runs on Bun); the image was rebuilt locally with Angular 22 and the result is recorded in `research.md` (PR 1b record)
- [X] T022 [US1] Amend the Angular part of principle V in `.specify/memory/constitution.md` ("Angular 21" becomes "Angular 22") and the stack lines in `AGENTS.md` and `jordylab-fe/AGENTS.md` that name versions (FR-010)
- [X] T023 [US1] Re-run the T011/T012/T014 verification set for this PR; also confirm the Capacitor `mobile` build output still syncs (`bunx nx run jordylab-mobile:sync` or the project's sync target) and record results
- [X] T024 [US1] Open PR 1b, wait for CI, resolve review comments, merge. If Angular 22 cannot be made green, stop, record the blocking tool in `research.md`, and leave the workspace on Nx 23 + Angular 21.2 (spec edge case)

**Checkpoint**: Nx 23 (and Angular 22 if green) merged; Parts A (Oxlint) and C can start.

---

## Phase 3: US4 - One owner per rule, and US2 - Agents see lint errors right after an edit (Priority: P1/P2) (PR 2 + PR 3)

**Goal**: Oxlint installed next to ESLint with exclusive rule ownership, a shared command, a Claude Code hook and an OpenCode instruction.

**Independent Test**: quickstart "Oxlint and hook" block; a deliberate lint error reaches the agent in the same turn in Claude Code; fixtures pass.

### Oxlint and shared command (PR 2, branch `feat/oxlint-fast-lint`)

- [X] T025 [US2] Measure single-file Oxlint (`bunx oxlint <file>`) versus the T001 single-file ESLint numbers and record in `research.md`; apply the decision rule: if ESLint is within about 2x and under about 2 s, the shared command uses ESLint (FR-020); if borderline, ask Jordy
- [X] T026 [HANDOFF] [US2] Post HANDOFF-## asking Jordy to confirm the exact Oxlint and `@nx/oxlint` versions (proposal: oxlint 1.86.0, `@nx/oxlint` 23.2.1) (FR-002)
- [X] T027 [US2] Install `oxlint` (exact pin) and run `bunx nx add @nx/oxlint` in `jordylab-fe/`; confirm Bun works, the plugin picks task name `oxlint`, and the `lint` target is untouched (inspect `bunx nx show project <lib>`); record in `research.md` section A2
- [X] T028 [US4] Create `jordylab-fe/.oxlintrc.json`: enable only rules ESLint does not own; explicitly turn off every overlapping `typescript`/`correctness` rule ESLint already enables; do not register the boundary rule or any JS plugin (FR-011, FR-012)
- [X] T029 [US4] Write `jordylab-fe/tools/check-lint-ownership.sh` per `contracts/lint-ownership.md` (resolved Oxlint rules vs ESLint `--print-config` for one `.ts` file per library type; fail on any rule enabled in both; check ESLint still owns `@angular-eslint/*`, template rules and `@nx/enforce-module-boundaries`); run it and fix `.oxlintrc.json` until it exits 0 (SC-004)
- [X] T030 [US2] Write `jordylab-fe/tools/lint-changed.sh` per `contracts/lint-changed-command.md` (path filtering incl. ESLint ignores, `bunx oxlint` or ESLint per T025, `path:line:col  rule  message` output, internal 2 s time guard (budget in the contract) that works on macOS without `timeout`, silent on missing linter/parse failure, `--strict` for manual use)
- [X] T031 [US3] Add an "Oxlint" step before the "Lint" step in the `test-frontend` job of `.github/workflows/build.yml` (also running `tools/check-lint-ownership.sh`); both steps fail the job (FR-021)
- [X] T032 [US3] Prove CI ordering with a scratch branch: one error both linters see fails on the Oxlint step; an Angular template violation and a boundary violation fail only on ESLint (record run links in `research.md`; do not merge the scratch branch)
- [X] T033 [P] [US2] Document the commands and the division of labour (one short paragraph, rule-ownership pointer) in `jordylab-fe/AGENTS.md`, plus a pointer line in root `AGENTS.md`, via `/dual-agent-config` (FR-023)
- [ ] T034 [US2] Open PR 2, wait for CI, resolve review comments, merge

### Agent hook (PR 3, branch `feat/lint-agent-hook`; may be combined with PR 2)

- [X] T035 [US2] Verify against the current Claude Code hooks documentation (via the claude-code-guide agent or docs): same-event hooks run in parallel, the `PostToolUse` JSON `hookSpecificOutput.additionalContext` field, the per-hook `timeout` field; record in `research.md` section A4 (corrects the plan if different)
- [X] T036 [P] [US2] Write the fixture script `.claude/hooks/tests/lint-cases.sh` (error file, clean file, ignored path, non-TypeScript file, path outside `jordylab-fe`, missing linter binary via a stubbed `PATH`, time limit exceeded via a slow stub, concurrent run with the format hook); it must pass without Bun (stub the linter) so `hook-tests.yml` needs no extra setup (FR-018)
- [X] T037 [US2] Write `.claude/hooks/post-lint-check.sh` per `contracts/lint-changed-command.md` (read stdin with `jq`, take the format lock for at most 1 s (budget in the contract), call `jordylab-fe/tools/lint-changed.sh`, emit the `additionalContext` JSON, always exit 0), via `/dual-agent-config`; make `lint-cases.sh` pass
- [X] T038 [US2] Add the lock-directory acquire/release around the Prettier call in `.claude/hooks/post-edit-format.sh` so the two hooks cannot overlap on one file (FR-017)
- [X] T039 [US2] Register the hook in `.claude/settings.json` as a `PostToolUse` entry with matcher `Write|Edit|MultiEdit` and `timeout` 5 next to the existing three, via `/dual-agent-config`
- [X] T040 [US2] Add a step running `bash .claude/hooks/tests/lint-cases.sh` to `.github/workflows/hook-tests.yml` (it already triggers on `.claude/hooks/**`)
- [X] T041 [P] [US2] Add the OpenCode instruction to `jordylab-fe/AGENTS.md` ("after editing a TypeScript file under `jordylab-fe/`, run `tools/lint-changed.sh <file>` and fix what it reports"), via `/dual-agent-config` (FR-019)
- [X] T042 [US2] Verify live in Claude Code: edit a frontend `.ts` file with a deliberate lint error and confirm the diagnostic reaches the agent in the same turn within about a second; also edit a clean file and a `.md` file and confirm silence; record in `research.md`
- [X] T043 [HANDOFF] [US2] Post HANDOFF-## asking Jordy to run the OpenCode check (fresh OpenCode session in `jordylab-fe/`, edit a `.ts` file with a deliberate error, confirm it runs `tools/lint-changed.sh` and fixes it); if unreliable, a plugin calling the same script is the follow-up (FR-019)
- [X] T044 [US2] Open PR 3, wait for CI (including Hook Tests), resolve review comments, merge

---

## Phase 4: US5 - Easy to back out (Priority: P3)

**Goal**: removing Oxlint is one commit.

**Independent Test**: on a scratch branch, one commit removes Oxlint; lint, tests, builds, hook fixtures stay green.

- [X] T045 [US5] Rehearse removal on a scratch branch: one commit removing `oxlint`/`@nx/oxlint` from `jordylab-fe/package.json` and `nx.json`, `jordylab-fe/.oxlintrc.json`, `jordylab-fe/tools/check-lint-ownership.sh`, the CI Oxlint steps, and the Oxlint branch in `jordylab-fe/tools/lint-changed.sh` (it falls back to ESLint or silence); run lint, tests, builds and `lint-cases.sh`; record the commit's file list in `jordylab-fe/AGENTS.md` under the Oxlint paragraph so the next person can do it (FR-022, SC-006)

---

## Phase 5: US8, US6, US7, US9 - AI integration conventions (Priority: P1/P2) (PR 4, branch `docs/ai-integration-conventions`, independent of Parts A and C)

**Goal**: corrected reference doc, loaded rules, gap analysis, checklist in skill and reviewer agents.

**Independent Test**: quickstart Part B block; Claude Code `/context` and a fresh OpenCode session show the rules.

- [X] T046 [US8] `git mv specs/_drafts/ai-integration-conventions/research-report.md docs/research/spring-ai-architecture.md`; apply the corrections (GA 12 June 2026, baseline Spring Boot 4.0 and 4.1) with the spring.io announcement as source; leave the rest of the draft folder in place (FR-024)
- [X] T047 [US8] Answer the starter-dependency question in the doc: from `jordylab-be/` run `./gradlew dependencies --configuration runtimeClasspath` (after US16 if it has landed, else on the current versions), check whether any `org.springframework.boot:*` artifact resolves to 4.1.x under Boot 4.0.3 / Spring AI 2.0.1, record the command and finding in the doc and note issue spring-projects/spring-ai#6465 (FR-024, FR-050)
- [X] T048 [US8] Add one row for the reference doc to the Reference Docs table in root `AGENTS.md` via `/dual-agent-config`; confirm no always-loaded file imports it (FR-025, SC-009)
- [X] T049 [US6] Write `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md` (about 25 lines): only high-confidence report findings that fit this repo (one ResilientAiService path, typed output validated right after `.entity()`, prompts as resources under `src/main/resources/prompts/`, no model call inside a database transaction, no `fallback` tool resolution, tool authorization inside secured services, content logging off, token usage metrics, evals before prompt changes), nothing the report marks unverified, via `/dual-agent-config` (FR-026, FR-028)
- [X] T050 [US6] Create `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/CLAUDE.md` containing exactly `@AGENTS.md`, and add one pointer line to the Spring AI section of `jordylab-be/AGENTS.md` naming the rules file and the reference doc (FR-026, FR-027)
- [X] T051 [US7] Read `ResilientAiService`, `AiProperties`, `ConfiguredAiModelResolver`, `ProviderHealthCache` in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/`, the AI call sites in gamecatalog and fna, `jordylab-be/src/main/resources/prompts/`, and all `@Transactional` methods that call AI; list findings per research topic for T052
- [X] T052 [US7] Write `docs/research/spring-ai-gap-analysis.md` per `data-model.md` "Gap entry": every checklist topic from the report (retry, fallback, circuit breaking/bulkheads, token budgets, typed outputs and validation, prompts as resources, no model call in transactions, tool authorization, human approval for write actions, observability content logging, evals, plus the remaining checklist items) with report claim, repo behaviour citing files, verdict and reason; link it from the reference doc (FR-029)
- [X] T053 [US7] For each `worth building` gap relevant to today's code, create a draft folder `specs/_drafts/<gap-name>/` (spec-draft.md, research.md, speckit-prompts.md in the style of the existing drafts); gaps that only matter for unbuilt modules are `defer` entries naming the triggering module and get no draft; no production code changes (FR-030)
- [X] T054 [US9] Add the AI checklist block (delimited by `BEGIN AI CHECKLIST` / `END AI CHECKLIST`) to `.claude/skills/ai-endpoint/SKILL.md` (scaffold typed output, prompt resource, ResilientAiService, new `AiFeature`) via `/dual-agent-config`
- [X] T055 [P] [US9] Add the identical checklist block to `.claude/agents/code-reviewer.md` and `.opencode/agents/code-reviewer.md` via `/dual-agent-config`; confirm the two blocks diff empty (FR-031)
- [X] T056 [US9] Validate the checklist: scaffold a throwaway sample AI feature with `/ai-endpoint` in a scratch worktree (discard it) and confirm typed output + prompt resource; run each `code-reviewer` copy on a scratch change that calls a model inside `@Transactional` and confirm it is reported
- [X] T057 [US6] Verify loading in Claude Code: start/continue a session with the package as the working context and run `/context`; if it cannot be run from this session, say so and hand it over (FR-032)
- [X] T058 [HANDOFF] [US6] Post HANDOFF-## asking Jordy to start a fresh OpenCode session in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/` and confirm the rules load (and to run `/context` in Claude Code if T057 could not) (FR-032)
- [X] T059 [US6] Open PR 4, wait for CI, resolve review comments, merge

---

## Phase 6: US11 - Every run cleans up after itself (Priority: P1) (PR 5, branch `test/e2e-web`)

**Goal**: a throwaway Postgres + Keycloak per run, always removed, always verified clean.

**Independent Test**: three runs (pass, fail, interrupt) each leave nothing; the dev stack is untouched.

- [X] T060 [US11] Create `jordylab-fe/e2e/compose.e2e.yaml`: pgvector image and Keycloak (same images as `jordylab-be/compose.yaml`), no fixed host ports (random free ports), no volumes, label `dev.jordylab.e2e.run=${E2E_RUN_ID}` on every service, `KC_HOSTNAME` matching the run's port (FR-041)
- [X] T061 [P] [US11] Derive the throwaway realm at run time from `jordylab-be/compose/keycloak-realm-export.json` with `jordylab-fe/e2e/lib/make-realm.py` (no checked-in copy to drift): one imported admin test user, a service-account client for the ingest/publish API calls, per-run secrets for the backend and ingest clients, `jordylab-host` redirect URIs and web origin for the run's port, `jordylab-mobile` callback; guest users are created through the sign-up UI by the tests, not imported; the dev and prod realm files are not modified (FR-041)
- [X] T062 [US11] Write `jordylab-fe/e2e/run.sh <web|android>` per `contracts/e2e-environment.md`: sweep, unique run id and compose project `jordylab-e2e-<runId>`, start, readiness waits with time limits (Postgres, Keycloak realm discovery, backend health; no fixed sleeps), backend jar started against the throwaway services with no AI provider keys, `trap` cleanup on EXIT/INT/TERM, final leftover check by project name and label that fails the run (FR-042, FR-043, FR-044)
- [X] T063 [US11] Generate per-run test credentials into an untracked env file (add its pattern to `.gitignore`), never printing values; wire them into the realm import and into the test runners (FR-040, root secrets rule)
- [X] T064 [US11] Prove cleanup: run the runner with a trivial passing test command, then a failing one, then Ctrl-C mid-run; after each, `podman ps -a`, `podman volume ls`, `podman network ls` filtered by the label show nothing; record outputs in `research.md` (SC-011)
- [X] T065 [US11] Prove the dev stack is untouched and unaffected: start the dev compose stack, run the runner, confirm no port conflict, the dev containers keep running, and dev database row counts for the app schemas are unchanged before/after (SC-013); record
- [X] T066 [US11] Prove hard-kill recovery: kill the runner with `SIGKILL` mid-run, start another run and confirm the sweep removes the earlier run's leftovers and the leftover check passes

---

## Phase 7: US10 - The main web journeys are tested automatically (Priority: P1) (PR 5)

**Goal**: five Playwright journeys on a fresh build against the throwaway environment.

**Independent Test**: `jordylab-fe/e2e/run.sh web` is green; breaking a covered journey makes it fail with a readable report.

- [X] T067 [HANDOFF] [US10] Post HANDOFF-## asking Jordy to confirm the Playwright version (proposal 1.63.0, plus its browser download) (FR-002)
- [ ] T068 [US10] Add `@nx/playwright` and `@playwright/test` at the confirmed versions and generate the Nx project `jordylab-fe/apps/jordylab-e2e` (project.json with `e2e` target, `playwright.config.ts` reading base URL and credentials from the runner's env file)
- [ ] T069 [US10] Decide and implement how the catalog gets data without database writes: through the app's ingest API with a token from the throwaway realm and a synthetic library folder created by the test (spike in `jordylab-fe/apps/jordylab-e2e/src/support/`; record the decision in `research.md` C3); if only the real downloaded scanner can do it, stop and report (repo validation-data rule)
- [ ] T070 [US10] Write the global setup in `jordylab-fe/apps/jordylab-e2e/src/global-setup.ts`: log in once through the Keycloak login page as the admin test user and save storage state reused by every test (FR-033)
- [ ] T071 [P] [US10] Add `data-testid` attributes only where roles/names are insufficient, in the components used by the journeys under `jordylab-fe/libs/gamecatalog/ui/src/lib/`, `jordylab-fe/libs/fna/ui/src/lib/` and `jordylab-fe/libs/settings/ui/src/lib/`; update existing unit specs if templates change (FR-034)
- [ ] T072 [US10] Journey test: sign-in and session reuse, signed-in shell and sign-out in `jordylab-fe/apps/jordylab-e2e/src/auth.spec.ts`
- [ ] T073 [P] [US10] Journey test: game catalog grid and detail with data created through the app's API in `jordylab-fe/apps/jordylab-e2e/src/gamecatalog.spec.ts`
- [ ] T074 [P] [US10] Journey test: admin Settings including approving a user created through the app (guest sign-up through the UI, approve as admin) in `jordylab-fe/apps/jordylab-e2e/src/settings.spec.ts`
- [ ] T075 [P] [US10] Journey test: FNA briefing view, read-only, no AI generation, in `jordylab-fe/apps/jordylab-e2e/src/fna.spec.ts`
- [ ] T076 [P] [US10] Journey test: catalog chat up to the model call (sends a question, asserts the pending state and the graceful failure/empty state since the throwaway backend has no AI keys; no paid call) in `jordylab-fe/apps/jordylab-e2e/src/gamecatalog-chat.spec.ts` (FR-035)
- [ ] T077 [US10] Run `jordylab-fe/e2e/run.sh web` end to end; confirm green on a fresh build; break one covered journey on purpose and confirm the suite fails with a readable report, then revert (SC-012 evidence)

---

## Phase 8: US12 - Native login works in the installed Android app (Priority: P1) (PR 6, branch `test/e2e-android`)

**Goal**: Custom Tab login, App Link return and WebView assertion on an emulator against a debug build.

**Independent Test**: the login test passes on an emulator and fails fast with both versions named when the WebView does not match the pins.

- [X] T078 [US12] Add the `e2e` Angular build configuration: `jordylab-fe/apps/jordylab/src/environments/environment.e2e.ts` (Keycloak address injected at run time as `window.__JORDYLAB_E2E__`, fixed logical fallback `http://localhost:18180` for the Android build, where adb reverse maps it) plus the `e2e` configuration in `jordylab-fe/apps/jordylab/project.json`; production output proven unchanged (initial 612.59 kB / 151.12 kB, no e2e strings in the bundle). The Android-specific build values are part of T079
- [ ] T079 [US12] Make the debug Android build point at the e2e web build and allow cleartext/mixed content in debug only: App Link host placeholder in `jordylab-fe/apps/jordylab-mobile/android/app/src/main/AndroidManifest.xml` (release value stays `jordylab.be`), debug config in `jordylab-fe/apps/jordylab-mobile/android/app/build.gradle` and an e2e Capacitor config next to `jordylab-fe/apps/jordylab-mobile/capacitor.config.ts`; release APK output unchanged (verify with the existing release build steps)
- [ ] T080 [HANDOFF] [US12] Post HANDOFF-## asking Jordy to confirm Appium 3.8.0, WebdriverIO 9.32.0 and the emulator image/API level for the pins (FR-002, FR-039)
- [ ] T081 [US12] Generate the Nx project `jordylab-fe/apps/jordylab-mobile-e2e` (WebdriverIO + Appium 3 + UiAutomator2, TypeScript, `wdio.conf.ts`); pin the UiAutomator2 driver and chromedriver to the emulator's WebView version and write `jordylab-fe/apps/jordylab-mobile-e2e/PINS.md` (FR-036, FR-039)
- [ ] T082 [US12] Write the preflight in `jordylab-fe/apps/jordylab-mobile-e2e/src/support/preflight.ts`: read the emulator's WebView version with `adb`, compare with `PINS.md`, fail fast naming both versions (FR-039)
- [ ] T083 [US12] Write the context helper in `jordylab-fe/apps/jordylab-mobile-e2e/src/support/contexts.ts`: switch between `NATIVE_APP` and the WebView context with a time limit and a clear error when the WebView context is missing (FR-036)
- [ ] T084 [US12] Write the emulator setup in `jordylab-fe/e2e/android-setup.sh`: `adb reverse` for Keycloak, backend and web ports, grant the App Link for the debug host (`pm set-app-links`); if the link cannot be approved, fall back to delivering the callback by `adb` intent and record the reduced fidelity in `research.md` (plan risk)
- [ ] T085 [US12] Login test in `jordylab-fe/apps/jordylab-mobile-e2e/src/login.e2e.ts`: tap sign in, drive the Custom Tab sign-in as the test user, App Link returns to the app, switch to the WebView context, assert the signed-in home (FR-037)
- [ ] T086 [US12] Run the login test on an emulator (locally if an Android SDK and emulator exist, else in the CI job from T093) and record the result; if the Mac cannot run it, say so plainly

---

## Phase 9: US13 - Other WebView-only behaviour is covered (Priority: P2) (PR 6)

**Goal**: install prompt, update check and share target each have a passing Android test.

**Independent Test**: each test passes; breaking its behaviour fails it.

- [ ] T087 [P] [US13] Install prompt test in `jordylab-fe/apps/jordylab-mobile-e2e/src/install-prompt.e2e.ts` (use the app's real install-prompt trigger; assert the prompt in the WebView)
- [ ] T088 [P] [US13] Update check test in `jordylab-fe/apps/jordylab-mobile-e2e/src/update-check.e2e.ts`: publish a newer test APK release through the throwaway backend's own publish API using the e2e service-account token, then assert the update notice in the WebView
- [ ] T089 [P] [US13] Share target test in `jordylab-fe/apps/jordylab-mobile-e2e/src/share-target.e2e.ts`: send `ACTION_SEND text/plain` via `adb shell am start`, switch to the WebView and assert the shared text arrives
- [ ] T090 [US13] Write the manual biometric checklist in `jordylab-fe/apps/jordylab-mobile-e2e/README.md` (biometric unlock stays manual, explicitly not automated) without the phrase the licence check rejects (FR-038, FR-047)

---

## Phase 10: US14 - CI runs both layers (Priority: P2) (PR 5 for the web job, PR 6 for the Android job)

**Goal**: web job gates merges; Android job runs on releases and manual start; both always clean up.

**Independent Test**: a PR with a broken web journey cannot merge; the Android job runs on `workflow_dispatch` and reports.

- [ ] T091 [US14] Add the `e2e-web` job to `.github/workflows/build.yml` (Node pin, Bun, install, fresh build, `jordylab-fe/e2e/run.sh web`, upload Playwright report and traces on failure, an `if: always()` cleanup and leftover-check step) (FR-045)
- [ ] T092 [HANDOFF] [US14] Post HANDOFF-## asking Jordy to mark the `e2e-web` check as required in the repository's branch protection (a repo setting only they can change); verify later with a deliberately broken journey PR (SC-012)
- [ ] T093 [US14] Create `.github/workflows/e2e-android.yml`: triggers `workflow_dispatch` and `workflow_call` (called from `.github/workflows/release.yml` after the `apk` job), `reactivecircus/android-emulator-runner` on the pinned API level, build the e2e debug APK, run `jordylab-fe/e2e/run.sh android`, an `if: always()` cleanup and leftover-check step (FR-045)
- [ ] T094 [US14] Wire the Android job into `.github/workflows/release.yml` without blocking the release publish if the job fails (it reports; decision recorded in `research.md`), then trigger it once with `workflow_dispatch` and record the run

---

## Phase 11: US15 - Agents know how to run the E2E suites (Priority: P3) (PR 5/6)

**Goal**: a short E2E section both agent tools read.

**Independent Test**: fresh Claude Code and OpenCode sessions each name the run command and when to prefer agent-browser.

- [ ] T095 [US15] Add a short E2E section to `jordylab-fe/AGENTS.md` (commands `e2e/run.sh web|android`, what each covers, cleanup guarantee, no hand-seeding, versions pinned in `PINS.md`, and that agent-browser/the browser pane stays for exploratory and one-off checks while the suites are the regression gate) and a pointer line in root `AGENTS.md`, via `/dual-agent-config` (FR-046)
- [ ] T096 [US15] Update `docs/testing/e2e-test-plan.md` section 2 line about "No browser-test agent / agent-browser" only if it conflicts; add a one-line link to the new suites

---

## Phase 12: US16 - Backend platform currency (Priority: P3) (PR 1c, branch `chore/backend-platform-upgrade`, independent)

**Goal**: Spring Boot, Modulith and Gradle checked and upgraded if safe.

**Independent Test**: `./gradlew check` is green and the dependency tree has one Boot minor.

- [X] T097 [US16] Look up and record current vs newest Spring Boot, Spring AI, Spring Modulith, Gradle and Java toolchain in `specs/012-technical-improvements/research.md` section A7, including the Boot 4.1 release notes and whether Modulith 2.1.x pairs with Boot 4.1 and Spring AI 2.0.1 runs on Boot 4.1 (FR-048)
- [X] T098 [US16] Run `./gradlew check --no-daemon` on the current versions in `jordylab-be/` as the baseline (tests, `ModularityTests`, JaCoCo 80% gate); record results
- [X] T099 [US16] Apply the confirmed versions: `jordylab-be/build.gradle.kts` (`org.springframework.boot` plugin, `springModulithVersion`, `springAiVersion` if it changes) and `jordylab-be/gradle/wrapper/gradle-wrapper.properties`; fix compile/config fallout (FR-049)
- [X] T100 [US16] Run `./gradlew check --no-daemon`; confirm green with unchanged behaviour; run `./gradlew dependencies --configuration runtimeClasspath` and confirm a single Spring Boot minor in the tree (SC-014)
- [X] T101 [US16] Start the backend against the local compose stack and confirm it boots and `/actuator/health` is up (`jordylab-be/`); if the check fails in a way that is not a small fix, fall back to the patch-only versions (Boot 4.0.8, Modulith 2.0.8) and record why (spec scenario 3)
- [X] T102 [US16] Update version mentions in `AGENTS.md`, `jordylab-be/AGENTS.md` and `.specify/memory/constitution.md` (Java/Spring lines only if they name versions) and feed the dependency-tree finding into T047
- [X] T103 [US16] Open PR 1c, wait for CI (`test-backend`), resolve review comments, merge

---

## Phase 13: Polish and cross-cutting

- [X] T104 [P] Run the licence-phrase grep from `.github/workflows/build.yml` locally over all new docs and READMEs (`grep -ril` outside `specs/`) and fix any hit (FR-047)
- [ ] T105 [P] Check every commit of this feature carries a valid `Refs:` trailer (`.githooks/commit-msg` enabled via `git config core.hooksPath .githooks`; never `--no-verify`) (FR-004)
- [ ] T106 Run the full `quickstart.md` validation for Parts A, B and C and record pass/fail per item in `research.md`; anything not runnable here (OpenCode, emulator on the Mac) is listed as not run with its HANDOFF
- [ ] T107 Update `specs/012-technical-improvements/checklists/requirements.md` notes and mark the feature's status in `spec.md` when all parts are merged; list remaining HANDOFFs

---

## Dependencies and execution order

- Phase 1 (baseline, gate) first. T004 gates every version-raising task.
- **Phase 2 (US1)** blocks Phase 3 (Oxlint install needs `@nx/oxlint` 23.x), Phase 4, and Phases 6-11 (E2E tooling is added on the new Nx). PR 1a merges before PR 1b; both before PRs 2, 5, 6.
- **Phase 5 (Part B)** and **Phase 12 (US16)** are independent of everything else and can run at any time, in parallel with Part A. T047 uses Phase 12's result if it has landed, else the current versions.
- Phase 3: Oxlint (PR 2) before the hook (PR 3); US4 ownership (T028-T029) before CI (T031).
- Phase 4 after Phase 3.
- Phase 6 (runner) before Phase 7 (web journeys) and Phase 8 (Android); Phase 7 and 8 both need the runner.
- Phase 8 before Phase 9; Phase 10 web job (T091) after T077; Android job (T093) after Phase 8/9.
- Phase 11 after Phases 7-10 so documented commands exist.
- Phase 13 last.

### Parallel opportunities

- After T015/T024: PR 2 (Phase 3), PR 5 (Phase 6-7) and PR 6 (Phase 8-9) can proceed in separate worktrees; Part B and Phase 12 at any time.
- Within a phase, tasks marked [P] touch different files (for example T073-T076, T087-T089, T055).

## Implementation strategy

- **MVP**: Phase 1 + Phase 2 (US1 PR 1a only): the Nx upgrade alone is releasable and unblocks the rest. Then pick by value: Part A's hook (Phase 3) for agent feedback, Part C's runner + web suite (Phases 6-7) for regression protection.
- **Incremental delivery**: one PR per bracketed branch in the phase headers; each leaves lint, tests and builds green and is attributable to one step.
- **Stop conditions** (report, never force): Angular 22 or TypeScript 6 rejected by a tool (T017/T024); Oxlint plugin unsupported with Bun (T027); the catalog cannot get data without database writes (T069); App Link cannot be approved on the emulator (T084, fallback recorded).

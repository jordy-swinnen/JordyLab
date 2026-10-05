# Research: Technical Improvements (012)

Date: 2026-10-05. Findings come from the repo on this branch and from live npm registry metadata (checked today). Items
marked **VERIFY** could not be settled by reading and become the first task of the part they belong to. Background
drafts: `specs/_drafts/oxlint-fast-lint/`, `specs/_drafts/ai-integration-conventions/`, `specs/_drafts/e2e-testing/`.

## Part A: Nx upgrade, Oxlint, agent hook

### A1. Prerequisites for Nx 23 (spec FR-007)

| Question | Finding | Source |
|---|---|---|
| Does Nx 23 support Angular 21.2? | **Yes.** `@nx/angular` 23.2.1 accepts `@angular/build`, `@schematics/angular`, `@angular-devkit/*` `>= 20 < 23`. The repo is on `~21.2`. No Angular upgrade is needed. | npm registry, `@nx/angular@23.2.1` peer deps |
| Is the TypeScript 6 migration compatible? | Angular 21.2.25 accepts TypeScript `>=5.9 <6.1`, so TS 6.0 is allowed there too. `typescript-eslint` 8.71.0 accepts `>=4.8.4 <6.1.0`; `@nx/eslint-plugin` has no TS peer. Because the Angular 22 step *requires* TS 6.0 (see A6), **Nx step (PR 1a): stay on `~5.9`** to keep it behaviour-neutral; **Angular step (PR 1b): move to 6.0 with Angular**. | npm registry |
| Analog | `@analogjs/vite-plugin-angular` latest 2.8.0 accepts Vite 6–8 and `@angular/build` 18–22. Repo is on `~2.1.2` with Vite 7. No Analog bump is required by Nx 23; a bump is optional and separate. | npm registry |
| spartan | `@spartan-ng/nx` (latest `0.0.1-alpha.322`, same as the repo range) has only `tslib` as peer. No Nx coupling. **VERIFY** its generators still run after migration (not exercised by lint/test/build). | npm registry |
| Vitest integration | `@nx/vitest` 23.2.1 accepts Vite 5–8 and Vitest 3–4 (repo: Vite 7, Vitest 4). | npm registry |
| Node 22+ | `@nx/*` 23 requires Node 22+ (Nx 23 release notes, per the draft). Dev machine: Node 24.13.1. CI `test-frontend` installs only Bun and relies on the runner's default Node; the frontend image is `oven/bun:1`. **VERIFY** the Node version that CI and the image actually run, and pin Node 22 in CI explicitly. | `.github/workflows/build.yml`, `deploy/containers/frontend/Containerfile` |
| Capacitor / `mobile` build | Capacitor 8 is unrelated to Nx; the `mobile` configuration is an Angular build configuration. **VERIFY** by building it after migration (FR-009). | repo |
| Bun with `nx migrate` | **VERIFY** during the upgrade; the repo has `bun.lock`. | repo |

Target: **Nx 23.2.1** (latest 23.x today; `nx`, `@nx/angular`, `@nx/eslint`, `@nx/eslint-plugin`, `@nx/js`, `@nx/vite`,
`@nx/vitest`, `@nx/web`, `@nx/workspace` move together). Confirmed with the developer before installing (FR-002).
`@nx/vite` is still listed in `package.json` although the executor in use is `@nx/vitest:test`; whether it can be
dropped is a finding for the PR, not a goal.

### A6. Angular 22 (directive after planning)

Feasible by registry metadata (2026-10-05); done as its own PR **after** the Nx upgrade (PR 1b):

| Package | Repo now | Needed / available | Note |
|---|---|---|---|
| `@angular/*` | `~21.2.25` | `22.2.1` (latest) | `@angular/build` 22 peers `typescript >=6.0 <6.1`, Vitest `^4.0.8 \|\| ^5` (repo: Vitest 4 ok) |
| TypeScript | `~5.9.2` | `~6.0` | forced by Angular 22 (`>=6.0 <6.1`); `typescript-eslint` 8.71.0 supports it |
| Node | Mac 24.13.1 | `^22.22.3 \|\| ^24.15.0 \|\| >=26` | **Mac Node is too old**: raise to 24.15+ before the upgrade; pin CI to 22.22.3+ or 24.15+ and check the `oven/bun:1` image provides an accepted Node |
| `angular-eslint` | 21.3.0 | 22.5.0 | bump with Angular |
| `@analogjs/*` | 2.1.x | 2.8.0 (accepts `@angular/build` 22, Vitest 5) | bump with Angular |
| `@ngneat/spectator` | 22.1.0 | peers `@angular/* >= 20` | no change needed |
| `@angular/cdk` | `>=21 <23` | 22.2.1 fits | no change needed |
| `@spartan-ng/brain` | alpha.380 | peers `>=18` (also 1.5.0 exists, `>=21 <23`) | stay on the alpha line; moving to 1.x is a separate decision |
| `ng-packagr` | `~21.2.7` | 22.2.4 | bump with Angular |
| `@nx/angular` | 22.7.12 | 23.2.1 accepts `<23` | satisfied by PR 1a |
| Capacitor 8 plugins | | no Angular peers | unaffected; `mobile` build verified |

Mechanism: `nx migrate latest` (PR 1a) brings Nx; the Angular bump is applied by the Angular migrations Nx runs for
`@nx/angular`, or `nx migrate @angular/core@22` if `latest` does not move it (**VERIFY**). Every migration reviewed.
Stop-and-report conditions are in the spec's Edge Cases.

### A7. Backend platform check (User Story 16)

Current: Spring Boot `4.0.3`, Spring AI `2.0.1`, Spring Modulith `2.0.3` (pinned), Gradle wrapper `9.3.1`, Java 25
(installed 25.0.2). Newest on Maven Central / Gradle today: Boot **4.1.1** (4.0 line: 4.0.8), Spring AI **2.0.1**
(already latest), Modulith **2.1.1** (2.0 line: 2.0.8), Gradle **9.8.0**.

Options, smallest first: (a) patch only: Boot 4.0.8 + Modulith 2.0.8 + Gradle 9.8.0; (b) Boot 4.1.1 + Modulith 2.1.1 +
Gradle 9.8.0, with Spring AI 2.0.1 unchanged (its announcement says it targets Boot 4.0 and 4.1). **Recommendation: (b)**,
because it removes the Boot 4.0 / Spring AI 4.1-dependency mismatch the report warns about, and falls back to (a)
if the backend check fails. Validation: `./gradlew check` (tests, `ModularityTests`, JaCoCo 80% gate), a dependency
tree showing one Boot minor, and a local start. Whether Modulith 2.1.x pairs with Boot 4.1 and what Boot 4.1 changed
(release notes) are checked at the start of the task. The result also answers the starter-dependency question in B2.

#### PR 1c record: backend platform upgrade (2026-10-05, branch `chore/backend-platform-upgrade`)

- Targets (confirmed 2026-10-05): Spring Boot 4.0.3 → **4.1.1**, Spring Modulith 2.0.3 → **2.1.1**, Gradle 9.3.1 → **9.8.0**; Spring AI
  stays **2.0.1** (already the newest, designed for Boot 4.0 and 4.1 per its announcement). Boot 4.1 notes read: deprecated 4.0
  APIs removed; Hibernate 7.4, Flyway 12.4, Spring Security 7.1, Spring Framework 7.0.8; Spring Data JPA bootstrap-executor changes.
- Compiles with one fix: Boot 4.1 moved `OAuth2ResourceServerAutoConfiguration` from `...resource.autoconfigure.servlet` to
  `...resource.autoconfigure`; two test imports updated (`TestSecurityConfig`, `SwitchGameControllerSecurityTest`).
- Local full `./gradlew check`: 662 tests; **8 fail the same way on unchanged main**: `JordylabApplicationTests`,
  `GuestChatLimitIntegrationTest`, `RoleMatrixTest` x6, all `ContainerLaunchException` / `localhost:2375 failed to respond` from the
  Podman socket under full-suite container load. Run alone, those classes pass on both main and Boot 4.1.1. So the failures are a local
  Podman limitation, not an upgrade effect; `test-backend` in CI (Docker) is the arbiter. Testcontainers moves 2.0.3 → 2.0.5 with Boot 4.1.1;
  a local experiment pinning it back did not change the full-suite result, so no pin was added.
- Dependency tree: see the reference report section 14 (before: Spring AI 2.0.1 starters *request* Boot starters 4.1.1 while the Boot 4.0.3
  BOM resolves them to 4.0.3, no 4.1 jar on the classpath; after: one Boot minor).

### A2. Oxlint

- Latest `oxlint` is **1.86.0**; `@nx/oxlint` 23.2.1 peers `oxlint ^1.43.0` (docs say 1.70.0 or later). Pin an exact
  Oxlint version and let the developer confirm it (FR-002).
- `@nx/oxlint` is experimental and uses an inference plugin that detects Oxlint from config files; `lint` is already
  taken by `@nx/eslint:lint`, so the plugin should pick the task name `oxlint`. **VERIFY** that it does not touch the
  `lint` target and that `nx add @nx/oxlint` works with Bun.
- Oxlint cannot lint Angular templates; ESLint keeps `flat/angular`, `flat/angular-template`, and
  `@nx/enforce-module-boundaries`. Oxlint's JS plugin API is alpha: not used.
- **Rule ownership**: ESLint config today is `@nx/eslint-plugin` `flat/base|typescript|javascript` plus per-lib Angular
  presets. Oxlint's `typescript` and `correctness` categories overlap with `typescript-eslint`. Decision: the Oxlint
  config is written as *only the rules ESLint does not enable*, and every ESLint-owned overlapping rule is explicitly
  `off` in `.oxlintrc.json`. A small script check (see contract `lint-ownership.md`) compares the two resolved rule
  sets and fails on any rule enabled in both (SC-004). Oxlint's `--rules`/config output and ESLint's `--print-config`
  provide both sides.
- Type-aware linting (`oxlint-tsgolint`) stays out of the hook (speed) and out of this feature's CI step (not asked);
  noted as a later option.

### A3. Baseline measurement (FR-005, SC-002)

Not measured yet. First task of the Part A branch, before any change. Measure: full `nx run-many -t lint` cold and
cached, single-file `eslint <file>` (flat config loading dominates), single-file `oxlint <file>`, on the Mac and in CI.
Record the numbers in this file. Decision rule: if single-file ESLint is within roughly 2x of Oxlint and under about
2 s, the shared command calls ESLint (FR-020) and Oxlint is limited to the CI first step. Decision is the developer's
if the numbers are borderline.

#### Measured baseline (2026-10-05, Mac, Node 24.13.1, Bun 1.3.10, Nx 22.7.12, `NX_DAEMON=false`, branch = main + spec files)

| Measurement | Result |
|---|---|
| Full `nx run-many -t lint` (14 projects), `--skip-nx-cache` | 7.3 s, all green |
| Same, second run (cache; Nx Cloud prints a 401 "not connected" notice, harmless) | 6.3 s |
| Single-file `bunx eslint libs/shared/auth/src/lib/pkce.ts` | 1.1 s first run, **0.6 s** warm |
| Single-file ESLint, three game-grid files together | 0.6 s |
| Single-file Oxlint | not measured yet (T025) |
| `nx run-many -t test --all --coverage` (14 projects) | 90 s, 441 tests, all green, every project passes its 80% line gate (workspace "All files" rows 34.1%–100% lines; the 34% row is a project without a gate hit in the output, record per-project in the PR) |
| `nx build jordylab --configuration=production` | 64 s; initial total 599.28 kB (152.01 kB transfer); budget warning (+99.28 kB over 500 kB) and the `qrcode` CommonJS warning exist today; `dist/apps/jordylab/browser` 984 kB |
| `nx build jordylab --configuration=mobile` | 64 s; initial total 599.37 kB (152.06 kB transfer); same two warnings; 984 kB |
| `nx graph` | 20 nodes, 32 dependency edges (snapshot saved outside the repo for the diff) |

**Finding for the decision in T025 (FR-020):** single-file ESLint is already about 0.6 s warm, inside the 1 s target.
Oxlint will be faster in absolute terms (tens of milliseconds expected) but the agent gain over ESLint may be small.
The decision rule in A3 (within about 2x and under about 2 s means use ESLint) points to ESLint for the hook unless the
Oxlint measurement shows a larger gap or Jordy prefers Oxlint's CI fail-fast value. To be decided with the numbers.

#### Node in CI and the frontend image (T003)

- `oven/bun:1` (pulled today) has **no Node binary**: `bunx nx` already runs on Bun's runtime there, so Nx's Node
  engine requirement does not apply inside that image; the image build in T013 proves it for Nx 23 and Angular 22.
- CI `test-frontend` uses only `oven-sh/setup-bun` and relies on the runner's preinstalled Node; no version is pinned.
  An explicit `actions/setup-node` pin is added in T008 (Node 22.22.3 or newer) so Nx and Angular 22 never depend on
  the runner image's default.
- Mac Node is 24.13.1: fine for Nx 23, **below** Angular 22's `^24.15.0` (handoff in T016).

#### PR 1a record: Nx 22.7.12 → 23.2.1 (2026-10-05, branch `chore/nx-23-upgrade`)

- `bunx nx migrate 23.2.1` is **not** Nx-only: it also proposed Angular 22.1.8, TypeScript 6.0.3, Vite 8.3.2, Analog 2.6.4,
  `angular-eslint` 22.5.0 and 5 AI-prompt migrations. That is the "stop and report" case of T006; since the plan keeps Nx and
  Angular as separate PRs, only the Nx packages were bumped (`nx` and every `@nx/*` to 23.2.1) and the generated
  `migrations.json` was narrowed to Nx's own migrations. Full unfiltered output kept outside the repo for PR 1b.
- Applied 26 migrations; changes: 11 `vite.config.mts` files (`__dirname` → `import.meta.dirname`) and `.nx/migrate-runs` in
  `jordylab-fe/.gitignore`; the rest were no-ops. **Skipped on purpose** (for PR 1b or never): `@nx/js` TypeScript-6
  migrations (`ignoreDeprecations: "6.0"` is invalid on TypeScript 5.9), `@nx/vite` Vite-8 and `rollupOptions`→`rolldownOptions`
  migrations, the Vitest 3/4 prompt migrations (already on Vitest 4), the `@nx/eslint` prompt migrations (already flat
  config), and all `@angular/*` migrations.
- Nx 23 prints no task output for passing runs by default; `--output-style=stream` restores it (used for the test comparison).
- Results versus the baseline: lint green (14 projects, 5.4 s); **441 tests, identical per-project coverage, every gate green**;
  production build initial total 599.28 kB (152.03 kB transfer; was 599.28 / 152.01); mobile 599.37 kB (was 599.37); dist
  984 kB both; the same two warnings as before plus a new deprecation notice (`@nx/angular/tailwind` removed in Nx 24);
  dependency graph identical (20 nodes). A scratch import from `scope:fna` into `scope:gamecatalog` fails lint with
  `@nx/enforce-module-boundaries` (the file was discarded).
- Frontend image built from `deploy/containers/frontend/Containerfile` with Nx 23 (`oven/bun:1`, no Node): success (the
  test image was removed). `bunx nx serve jordylab` boots (HTTP 200); `nx g @spartan-ng/nx:ui --help` runs.
- Follow-up for Nx 24: replace `@nx/angular/tailwind` usage before upgrading past Nx 23.

### A4. Agent hook mechanics

- Claude Code runs all hooks matching one event **in parallel**, so the existing `post-edit-format.sh` (Prettier) and a
  new lint hook can touch the same file at once. **VERIFY** against the current hook docs while implementing.
  **Decision:** serialise with a lock directory (`mkdir`-based, atomic, portable on macOS and Linux) that both scripts
  take around their file access; the lint hook waits for it for at most 1 s and otherwise skips silently (time budget: see `contracts/lint-changed-command.md`). This keeps
  the new script separate (as specified) and removes the race without merging the two.
- **Feedback channel**: the existing hooks print to stdout and exit 0, which only reaches the transcript view. For the
  agent to *see* the finding, the hook emits the JSON form with `hookSpecificOutput.additionalContext` and exits 0
  (advisory, never blocks). **VERIFY** the field name and behaviour against the current docs and with a live edit.
- **Time limit**: the `timeout` field in the hook's `settings.json` entry (seconds) plus an internal guard in the
  script (macOS has no `timeout` by default; use `gtimeout` if present, else a bash background-and-kill).
- **Existing defect found**: `post-edit-format.sh` calls `npx prettier` from the repo root (rule is `bunx`, and the
  Prettier install lives in `jordylab-fe`). Fixed in the same PR as the lock change (separate commit).
- **OpenCode**: no hook equivalent is translated. `jordylab-fe/AGENTS.md` gets a rule: after editing a TypeScript file,
  run `jordylab-fe/tools/lint-changed.sh <file>`. A plugin only if the rule proves unreliable (FR-019).
- Everything agent-config goes through `/dual-agent-config` (FR-003), including `.claude/settings.json`, the hook, both
  AGENTS.md files and the CLAUDE.md import.
- Hook tests: `.claude/hooks/tests/lint-cases.sh` run by `hook-tests.yml` (already triggers on `.claude/hooks/**`). The
  workflow currently installs only Python; the fixture script must stub the linter binary so CI does not need Bun, or
  the workflow gains a Bun step. Prefer the stub (fast, deterministic) plus one real-binary case run locally.

### A5. Removability (FR-022)

Everything Oxlint-specific is one set: `.oxlintrc.json`, the `@nx/oxlint` plugin entry in `nx.json`, the dev
dependencies, the CI step, the `oxlint` branch inside `lint-changed.sh`, the AGENTS.md paragraph. The hook calls only
`lint-changed.sh`, so the hook and its tests survive removal (the shared command falls back to ESLint or to silence).
Proved by a scratch-branch removal in the verification task.

## Part B: AI integration conventions

### B1. File layout (follows the draft's three-layer decision)

| Layer | Path | Loads |
|---|---|---|
| Corrected report | `docs/research/spring-ai-architecture.md` (moved from `specs/_drafts/ai-integration-conventions/research-report.md` with `git mv`) | On demand; one row in root `AGENTS.md` Reference Docs |
| Rules | `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md` + one-line `CLAUDE.md` containing `@AGENTS.md` | When an agent works in that package (OpenCode reads nested `AGENTS.md`; Claude Code needs the `CLAUDE.md` import) |
| Pointer | Spring AI section of `jordylab-be/AGENTS.md` | With backend instructions |
| Checklist | `.claude/skills/ai-endpoint/` (shared skill) and both `code-reviewer.md` copies | Scaffolding / review |

The `shared/ai` package currently has no `AGENTS.md`. `.claude/rules/` was rejected (Claude-only).

### B2. Report corrections

- GA date 12 June 2026 (not 28 May); baseline Spring Boot 4.0 **and** 4.1. Primary source:
  https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/ (cited in the draft; re-check when editing).
- **Boot 4.0.3 + Spring AI 2.0.1 starter-dependency question**: not yet answered. Method (task): resolve the
  dependency tree (`./gradlew dependencies --configuration runtimeClasspath`), check whether any `spring-boot-*` or
  `spring-boot-starter-*` artifact resolves to a 4.1.x version while the Boot plugin is 4.0.3, compare against the
  Spring Boot BOM, and record result and command in the reference doc. Issue spring-projects/spring-ai#6465 (title
  only seen) is the lead. No upgrade is proposed by this feature; if the problem applies, it is reported.

### B3. Gap analysis inputs (written during implementation, not now)

Code to read per topic: `ResilientAiService` (retry-once fallback on any failure, virtual-thread timeout, metrics and
`AiCallCompleted` event, never throws), `AiProperties`/`ConfiguredAiModelResolver`, `ProviderHealthCache`,
`src/main/resources/prompts/` (prompts are already resources), gamecatalog enrichment/chat, fna briefing, and
`@Transactional` call sites (check no model call inside one). Per decision Q4, gaps that only matter for unbuilt
modules are marked defer with the triggering module and get no draft.

### B4. Verification of loading (FR-032)

Claude Code: `/context` in a session started in the package (needs the user's interactive session; I cannot run
`/context` myself). OpenCode: a fresh session in the package (needs OpenCode installed and a manual start). Both are
**handed to the developer** if they cannot be run here; the result is reported plainly.

## Part C: E2E testing

### C1. Versions (all confirmed with the developer before install, FR-002)

Latest on npm today: `@playwright/test` 1.63.0, `appium` 3.8.0, `webdriverio` 9.32.0, `@wdio/cli` 9.32.0. Not yet
looked up: `appium-uiautomator2-driver` and `appium-chromedriver` versions matching the emulator's WebView.
Playwright browser binaries are downloaded on install (size confirmed when asking).

### C2. Layout

- Web suite: new Nx project `apps/jordylab-e2e` (Playwright), using `@nx/playwright` (peer-aligned with Nx 23.2.1) so
  Nx caching and `nx affected` apply. Test ids added to components as `data-testid` (none exist today: zero hits in
  `libs/` and `apps/`); roles/names are preferred, test ids only where no accessible name exists.
- Android suite: new Nx project `apps/jordylab-mobile-e2e` (decision Q3) with WebdriverIO + Appium, TypeScript.
- Throwaway environment, shared by both suites: `jordylab-fe/e2e/` directory (or repo `e2e/`) holding the compose file,
  realm import, orchestration scripts (see contract `e2e-environment.md`).

### C3. Throwaway environment

- Dev compose (`jordylab-be/compose.yaml`) uses fixed ports 5432/8180 and imports the dev realm. E2E uses its own
  `compose.e2e.yaml`, unique project name `jordylab-e2e-<runId>`, **no host port mapping fixed** (random free host
  ports resolved after start), no volumes, and a labeled set (`dev.jordylab.e2e.run=<runId>`) so cleanup and the
  leftover check can find everything by label.
- Backend runs from the same build the web suite tests: the Spring Boot jar started by the orchestration script (not a
  container), pointed at the throwaway Postgres and Keycloak. The Angular app is built fresh and served by a static
  server with the `/api` proxy, or the backend serves it; decided in design (see data-model run record).
- E2E realm: `compose/e2e-realm.json` derived from the dev realm, containing a **test user per needed role** (admin,
  guest) with fixed throwaway credentials that exist only in this realm, the `jordylab-host` and `jordylab-mobile`
  redirect URIs for the run's ports. Test credentials are generated per run into an untracked env file (never echoed),
  per the repo's test-credential rules. The dev realm export is not modified.
- **No hand-seeded rows**: users are created through Keycloak import (identity, not application data); everything in
  the application's tables comes from the app's API or UI. Games for the catalog journeys come from the real scan
  endpoint driven with the Python scanner against a synthetic library directory created by the test (synthetic files
  are test fixtures, not database rows) or through the app's own source/ingest API; settled in design, never SQL.
- Cleanup: `trap` on EXIT/INT/TERM in the runner; `compose down --volumes --remove-orphans`; then a **leftover check**
  listing containers, volumes and networks by project name and label and failing on any hit. Podman (Ryuk disabled
  locally) is the runtime; compose is Podman Compose or `podman compose` as in dev.
- A hard kill (terminal closed) cannot run a trap: the next run's pre-step and the CI `always()` step run the same
  leftover sweep for the `jordylab-e2e-` prefix.
- AI-calling journeys: the AI endpoints are called without model access. The throwaway backend runs with no OpenRouter
  or Anthropic keys; chat and briefing journeys assert up to the call and the app's graceful failure/empty states
  (decision Q1: no paid calls in the merge gate).

### C4. Android specifics

- The mobile build hardcodes production (`environment.mobile.ts`: `https://jordylab.be`) and the manifest App Link is
  `https://jordylab.be/mobile` with `autoVerify`. A debug build cannot point at the throwaway Keycloak without changes
  and the production App Link cannot be verified for a local host. **Decision:** add an `e2e` Angular configuration
  (`environment.e2e.ts`, URLs injected at build time) and a debug-only manifest placeholder for the App Link host;
  grant the link with `adb shell pm set-app-links`/verify-and-approve on the emulator. This is the one place the
  feature touches product code (build configuration only; production output unchanged, proven by comparing the
  `mobile` build before and after).
- The emulator reaches the host through `adb reverse` so `localhost:<port>` is the same address for Keycloak, the
  backend and the Custom Tab. The WebView origin is `https://localhost` (Capacitor `androidScheme`), so plain-HTTP
  calls need `allowMixedContent`/cleartext in the debug configuration only. **VERIFY** on the emulator.
- Keycloak `jordylab-mobile` is a public client with PKCE S256 and redirect URI `https://<host>/mobile/callback` in
  production; the E2E realm registers the e2e callback.
- Behaviours to cover: native login (Custom Tab then App Link), install prompt, update check (the app polls the
  backend for a newer APK; the throwaway backend can publish a test release through its own publish API using a
  service-account token from the E2E realm), share target (`ACTION_SEND text/plain` intent via `adb shell am start`,
  then assert inside the WebView).
- Biometric unlock stays on the manual checklist (documented, FR-038).
- WebView/chromedriver pin: record emulator image (API level), its WebView version, the matching chromedriver and
  Appium driver versions in `apps/jordylab-mobile-e2e/PINS.md`; a preflight check reads the WebView version via adb and
  fails fast with both versions named (FR-039). The developer Mac has no Android SDK/adb; local emulator runs are
  documented, CI is the primary runner (spec Assumptions).
- CI: `reactivecircus/android-emulator-runner` on `ubuntu-latest`; job triggered by release and `workflow_dispatch`
  (decision Q2). It needs the debug APK built in the job (Gradle + Android SDK on the runner; the existing `apk` job
  in `release.yml` already builds release APKs on a hosted runner).

### C5. CI

- Web job `e2e-web` in `build.yml` on `pull_request` and `push` to main: install, build app fresh, start environment,
  run Playwright, upload report/trace on failure, `always()` cleanup + leftover check. Made a **required check** in
  branch protection by the developer (a repo setting, handed over).
- Android job `e2e-android` in a new `e2e-android.yml` (`workflow_call` from `release.yml` after the `apk` job or on
  `release` event, plus `workflow_dispatch`). Same `always()` cleanup.
- Licence check: new docs/READMEs must not contain the phrase the `licence-check` job rejects (FR-047); a pre-commit
  grep in the task checklist.

### C6. Relationship to agent-browser

The built-in browser pane / agent-browser stays the tool for exploratory checks, bug reproduction and anything needing
judgement. The Playwright suites are the repeatable regression gate. Documented in a short E2E section of
`jordylab-fe/AGENTS.md` (Claude Code reads it through the existing `@AGENTS.md` import; OpenCode natively), with a
pointer from the root `AGENTS.md`.

## Constitution and repo-rule implications

- Constitution principle V names "Nx 22": amended to the new Nx version (FR-010), noted under Last Amended.
- Principle IV (testing): the E2E suites are Playwright and WebdriverIO, a different layer from the Vitest unit tests;
  unit-test rules are unchanged. Test code follows principles I-III in spirit (descriptive names, fail fast).
- Root AGENTS.md "no hand-seeding" and "no secrets echoed" apply to the E2E tooling.
- Commit trailers: `Refs: 012` or `012/T###` on every commit; the Nx upgrade PR is its own branch.

## Decisions summary

| # | Decision | Rationale | Alternative rejected |
|---|---|---|---|
| D1 | PR 1a: Nx 23.2.1 on Angular 21.2 + TS 5.9. PR 1b: Angular 22.2.1 + TS 6.0 + Angular-coupled tooling. Backend: Boot 4.1.1 / Modulith 2.1.1 / Gradle 9.8.0 (fallback 4.0.8 / 2.0.8) | Two small reviewable steps; Angular 22 forces TS 6; each step attributable | One big `nx migrate latest` PR (hard to bisect) |
| D2 | Oxlint rules = ESLint-off list; one-owner check script | Guarantees SC-004 mechanically | Hand-maintained ownership table only |
| D3 | `tools/lint-changed.sh` shared command, linter-agnostic name | Hook, OpenCode rule and CI call one thing; ESLint fallback needs no rename | Linter-named script |
| D4 | Lock-directory serialisation between format and lint hooks | Parallel hooks; keeps scripts separate | Merging hooks; sleeping |
| D5 | Hook feedback via JSON `additionalContext`, exit 0 | Advisory and visible to the agent | stdout echo (transcript-only); exit 2 (blocks-style) |
| D6 | Rules file at `shared/ai/AGENTS.md` + `CLAUDE.md` import | Loads for both tools in the package | `.claude/rules/` (Claude-only) |
| D7 | Own compose project, label-based cleanup, no fixed ports | Coexists with dev stack; sweep works after hard kill | Reusing dev compose |
| D8 | `e2e` Angular configuration + debug manifest placeholder for App Link host | Only way to test login against a throwaway Keycloak | Testing against production |
| D9 | `adb reverse` for localhost parity | One origin string for emulator and host | `10.0.2.2` hostname mismatch with Keycloak issuer |

# Feature Specification: Technical Improvements: Oxlint + Agent Hook, AI Integration Conventions and E2E Testing

**Feature Branch**: `012-technical-improvements`

**Created**: 2026-10-05

**Status**: Draft

**Depends On**: 007 (Android app: Capacitor shell, native login, install prompt, update check, share target), 011 (manual E2E campaign whose coverage matrix feeds the web journeys)

**Input**: User description: "Short name: technical-improvements. Spec title: Technical Improvements: Oxlint + Agent Hook,
AI Integration Conventions and E2E Testing. Three technical improvements to how JordyLab is built and checked, delivered
as one feature with three independent parts. Each part must be testable and releasable on its own, and none changes
product behaviour. I develop mostly with coding agents (Claude Code and OpenCode), so every part is judged by how well it
helps agents and CI catch problems early. [...] PART: FAST LINT FEEDBACK WITH OXLINT, AN AGENT HOOK AND THE NX UPGRADE
[...] PART: AI INTEGRATION CONVENTIONS [...] PART: E2E TESTING [...] Use /dual-agent-config for ALL agentic coding config
related changes."
(full description in the session prompt; background in `specs/_drafts/oxlint-fast-lint/`,
`specs/_drafts/ai-integration-conventions/` and `specs/_drafts/e2e-testing/`)

---

## Overview

Three tooling improvements, one feature, three independently releasable parts. None of them changes what a JordyLab user
sees; all of them are judged by how early they let a coding agent (Claude Code or OpenCode) or CI catch a problem.

| Part | What it delivers | Order |
|------|------------------|-------|
| **A. Fast lint feedback** | The frontend workspace on the current Nx 23 release (behaviour-neutral, own change), then Oxlint as a fast first pass next to ESLint, and an agent hook that reports lint errors right after an edit | Nx upgrade first, before anything else in this feature |
| **B. AI integration conventions** | The Spring AI research report filed as a corrected reference doc, short repo-specific rules that both agent tools load next to the shared AI code, a written gap analysis, and the checklist in `/ai-endpoint` and `code-reviewer` | Independent |
| **C. E2E testing** | Automated web journeys (Playwright) and a small Android suite (Appium) for what only breaks inside the installed app, each run on its own throwaway Postgres + Keycloak that is always removed | After the Nx upgrade (its tooling is added on the new Nx version) |

Two cross-cutting rules apply to all parts:

- **Version upgrades are welcome (security), but the developer is asked first.** Before any dependency or tool version is
  raised (Nx, TypeScript, Oxlint, Playwright, Appium, drivers, images), the proposed versions and what they pull in are
  presented and confirmed.
- **All agent configuration changes go through `/dual-agent-config`**: hooks, `AGENTS.md`/`CLAUDE.md`, `.claude/`,
  `.opencode/`, skills and agents. Hooks are authored separately for each tool, never translated.

---

## Clarifications

### Session 2026-10-05

- Q: Which user journeys should the first automated web tests cover? → A: Core set: sign-in with session reuse, game catalog grid and detail, admin Settings (including approving a user), FNA briefing view (read-only, no AI generation), and catalog chat only up to the model call.
- Q: When should the Android E2E job run in CI? → A: On releases only, plus a manual run on demand (not on every merge to main).

---

## User Scenarios & Testing *(mandatory)*

### Part A: Fast lint feedback

### User Story 1 - Upgrade Nx without changing behaviour (Priority: P1)

As the developer, I want the frontend workspace on the current Nx 23 release, in its own reviewable change, so that the
Oxlint integration and the new E2E tooling can land on a current, patched toolchain, with nothing else changing.

**Why this priority**: it is the prerequisite for the Oxlint plugin and the E2E tooling, and it carries security value
on its own. It must be merged and green before the other Part A and Part C work starts.

**Independent Test**: on the upgraded commit, lint, unit tests with the coverage gate, the production build, the mobile
build and the dependency graph give the same results as on the commit before it, and a deliberate module-boundary
violation still fails lint.

**Acceptance Scenarios**:

1. **Given** the workspace on Nx 22.7.12, **When** the prerequisites are checked (Nx 23 supports Angular 21.2; the
   TypeScript 6 migration is compatible with Angular 21.2; Analog, spartan and the Vitest integration work; Node 22 or
   newer locally, in CI and in the images that build the frontend), **Then** the findings are reported to the developer
   and the target versions are confirmed by them before anything is installed.
2. **Given** a prerequisite fails in a way that would require an Angular upgrade, **When** the upgrade is attempted,
   **Then** work stops and the finding is reported; Angular is never upgraded silently.
3. **Given** the TypeScript 6 migration is offered and Angular 21.2 does not support it, **When** migrations are run,
   **Then** that migration is declined and the decision is recorded.
4. **Given** the upgraded workspace, **When** lint, unit tests (with the coverage gate), the production build, the mobile
   build and the dependency graph are run, **Then** each result matches the pre-upgrade baseline.
5. **Given** the upgraded workspace, **When** a deliberate module-boundary violation is introduced, **Then** lint fails.

---

### User Story 2 - Agents see lint errors right after an edit (Priority: P1)

As the developer working with coding agents, I want a TypeScript file an agent just edited to be checked within about a
second, with the result fed back to the agent, so that the agent fixes the error in the same turn instead of CI finding
it minutes later.

**Why this priority**: this is the point of Part A; the upgrade and Oxlint exist to make it possible.

**Independent Test**: have an agent introduce a known lint error in a frontend TypeScript file; the diagnostic reaches
the agent immediately after the edit, in Claude Code (through the hook) and in OpenCode (through the instruction).

**Acceptance Scenarios**:

1. **Given** the current lint baseline has been measured (full run and single-file run, cold and cached, locally and in
   CI), **When** a single-file ESLint run turns out to be nearly as fast as a single-file Oxlint run, **Then** the hook
   calls ESLint instead and the rest of the feature is unaffected.
2. **Given** an agent edits a TypeScript file under the frontend workspace that contains a lint error, **When** the edit
   completes, **Then** the diagnostic is shown to the agent within about one second, and the edit itself is not undone
   or blocked.
3. **Given** an agent edits a file that is not TypeScript, is outside the frontend workspace, or lies in a path ESLint
   also ignores (build output, generated UI primitives, dependencies), **When** the edit completes, **Then** the hook is
   silent.
4. **Given** the linter is not installed, cannot parse the file, or exceeds its time limit, **When** the hook runs,
   **Then** it exits silently without error and without stalling the agent.
5. **Given** the formatting hook and the lint hook both fire after the same edit, **When** they run, **Then** the lint
   check never reads a half-written file (they are proven not to race, or are chained in one script).
6. **Given** an OpenCode session working in the frontend, **When** it edits a TypeScript file, **Then** the frontend
   instructions direct it to run the same shared command the Claude Code hook calls.

---

### User Story 3 - CI runs the fast pass first, ESLint still gates (Priority: P2)

As the developer, I want CI to fail fast on Oxlint errors while ESLint still checks everything Oxlint cannot, so that
nothing slips through the gap between the two tools.

**Why this priority**: it extends the fast feedback to pull requests; ESLint gating already exists, so the risk is low.

**Independent Test**: push one change with an error both linters can see and one with an error only ESLint can see (an
Angular template rule, a module-boundary violation); the first fails on the Oxlint step, the second fails on ESLint.

**Acceptance Scenarios**:

1. **Given** a pull request with an Oxlint error, **When** CI runs, **Then** the Oxlint step runs before ESLint and fails.
2. **Given** a pull request with only an Angular template or boundary violation, **When** CI runs, **Then** ESLint fails it.

---

### User Story 4 - One owner per rule (Priority: P2)

As the developer, I want each lint rule owned by exactly one linter, so that agents never get duplicate or contradictory
feedback.

**Why this priority**: conflicting feedback makes agents oscillate; it must hold before the hook is relied on.

**Independent Test**: run both linters over the whole frontend; no rule is reported by both.

**Acceptance Scenarios**:

1. **Given** both linters configured, **When** they run over the whole frontend, **Then** no rule is enabled in both, and
   ESLint alone owns the Angular rules, the Angular template rules and module-boundary enforcement.

---

### User Story 5 - Easy to back out (Priority: P3)

As the developer, I want to remove Oxlint again in one commit if it turns out not to be worth it.

**Why this priority**: a safety valve, only needed if Oxlint disappoints.

**Independent Test**: on a scratch branch, remove the Oxlint dependency, config, CI step and its use in the hook in one
commit; lint, tests and builds stay green.

**Acceptance Scenarios**:

1. **Given** Oxlint is installed, **When** a single commit removes it, **Then** lint, unit tests and builds still pass and
   the agent hook falls back to silence (or to ESLint, if that was chosen in Story 2).

---

### Part B: AI integration conventions

### User Story 6 - Agents follow the same AI rules every time (Priority: P1)

As the developer, I want agents working on AI code to load short, repo-specific rules automatically, in both Claude Code
and OpenCode, so that every new AI feature is built the same proven way.

**Why this priority**: more AI features are coming (recipe, trading); the rules pay off on each one.

**Independent Test**: start a Claude Code session and a fresh OpenCode session in the shared AI package; both show the
rules as loaded.

**Acceptance Scenarios**:

1. **Given** the rules file next to the shared AI code, **When** a Claude Code session works in that package, **Then**
   the rules appear in its loaded context (a one-line Claude-specific file imports the shared rules file, because Claude
   Code does not read nested agent instruction files on its own).
2. **Given** the same rules file, **When** a fresh OpenCode session is started in that package, **Then** OpenCode reports
   the rules as loaded.
3. **Given** AI code in another module (for example the game catalog), **When** an agent reads the backend instructions,
   **Then** they point it to the rules file and to the full reference doc.
4. **Given** a check that cannot be run from the agent session (for example the OpenCode check), **When** verification is
   reported, **Then** it says plainly which check was not run and hands it to the developer.

---

### User Story 7 - I know which recommendations we don't follow yet (Priority: P1)

As the developer, I want a written gap analysis between the research and the repo's actual AI code, so that I can decide
what is worth building.

**Why this priority**: it turns a generic report into concrete decisions; it is the main input for future AI work.

**Independent Test**: every topic in the report's checklist appears in the gap list, marked worth building, defer or not
applicable, with a reason; each worth-building gap has its own draft folder.

**Acceptance Scenarios**:

1. **Given** the report and the repo's AI code, **When** the gap analysis is written, **Then** it covers at least: retry,
   provider fallback, circuit breaking/bulkheads, token budgets and cost limits, typed outputs with validation, prompts as
   versioned resources, no model calls inside database transactions, tool authorization and least privilege, human
   approval for write actions, observability content logging, and evals; each entry cites the code it checked.
2. **Given** a gap marked worth building, **When** the analysis is done, **Then** a draft exists for it under
   `specs/_drafts/` and no production code was changed for it.

---

### User Story 8 - The full research is available when needed (Priority: P2)

As the developer, I want the corrected report in the repo as a reference that agents read on demand, not in every
session.

**Why this priority**: the rules carry the essentials; the full report is for deeper questions.

**Independent Test**: the report lives at `docs/research/spring-ai-architecture.md`, the root agent instructions list it
under Reference Docs, and no always-loaded instruction file imports it.

**Acceptance Scenarios**:

1. **Given** the draft report, **When** it is moved, **Then** it states that Spring AI 2.0 went GA on 12 June 2026 (not
   28 May) and that the baseline is Spring Boot 4.0 and 4.1.
2. **Given** reports that the Spring AI 2.0 starters pull in Spring Boot 4.1 dependencies, **When** the repo's actual
   combination (Spring Boot 4.0.3 with Spring AI 2.0.1) is checked, **Then** the doc records whether it is affected and
   how that was established.
3. **Given** items the report marks unverified, **When** the rules file is written, **Then** none of them appear in it;
   they stay in the reference doc only.

---

### User Story 9 - New AI endpoints and reviews apply the checklist (Priority: P2)

As the developer, I want the `/ai-endpoint` skill to scaffold with the conventions and the `code-reviewer` agent to check
them, in both agent tools.

**Why this priority**: it enforces the rules at the two moments they matter: creation and review.

**Independent Test**: scaffold a sample AI feature with `/ai-endpoint` (discarded afterwards) and confirm it follows the
checklist; give `code-reviewer` (each tool's copy) code that breaks a convention and confirm it reports it.

**Acceptance Scenarios**:

1. **Given** the updated skill, **When** a sample AI feature is scaffolded, **Then** it uses typed output and a prompt
   kept as a resource, and routes through the shared resilient AI service.
2. **Given** both copies of `code-reviewer`, **When** each reviews code that calls a model inside a database transaction,
   **Then** each reports it; the two copies carry the same checklist.

---

### Part C: E2E testing

### User Story 10 - The main web journeys are tested automatically (Priority: P1)

As the developer, I want the most important journeys from the manual E2E campaign covered by automated browser tests
against a freshly built app, so that agents and CI catch regressions without a manual pass.

**Why this priority**: the web build is also the Android app's content, so this layer covers the most behaviour per test.

**Independent Test**: break a covered journey on purpose; the web suite fails locally and in CI.

**Acceptance Scenarios**:

1. **Given** a freshly built app and a throwaway environment, **When** the web suite runs, **Then** it logs in once
   through Keycloak as a test user and reuses that session across the journeys.
2. **Given** the agreed journeys, **When** they are automated, **Then** every selector is a test id or an accessible
   role/name, never an internal class name or DOM position.
3. **Given** a deliberately broken journey, **When** the suite runs, **Then** that journey fails with a readable report.

---

### User Story 11 - Every run cleans up after itself (Priority: P1)

As the developer, I want each E2E run to use its own throwaway Postgres and Keycloak and remove them afterwards, whatever
happens, so that tests never touch my dev database and nothing piles up on my machine.

**Why this priority**: the repo forbids hand-seeding and touching the dev database; this is a hard precondition for
running anything.

**Independent Test**: run the suite and let it pass; run it and make it fail; start it and interrupt it. After each, no
container, volume or network from the run exists, and the dev stack is untouched.

**Acceptance Scenarios**:

1. **Given** the dev stack is running on its usual ports, **When** an E2E run starts, **Then** it starts its own stack
   under a unique project name on free ports, with nothing persisted, and the dev stack is unaffected.
2. **Given** an E2E run, **When** test data is needed, **Then** it is created only through the app's own API or UI; no
   row is inserted directly into any database.
3. **Given** a run that passes, fails, or is interrupted, **When** it ends, **Then** everything it started is removed.
4. **Given** the run has ended, **When** the final leftover check runs, **Then** it fails the run if any container,
   volume or network from that run still exists.
5. **Given** Keycloak or the backend is slow to start, **When** the suite waits, **Then** it waits on a readiness check
   with a time limit, not a fixed sleep, and fails clearly when the limit is reached.

---

### User Story 12 - Native login works in the installed Android app (Priority: P1)

As the developer, I want native Keycloak login (Custom Tab, then App Link back into the app) tested on an emulator
against a debug build, because it is the part of the app most likely to break on Android only.

**Why this priority**: the Android app has no automated coverage at all, and login gates everything else.

**Independent Test**: on an emulator with a debug build pointing at the throwaway environment, the suite logs in through
the Custom Tab, returns through the App Link and lands on the signed-in home screen.

**Acceptance Scenarios**:

1. **Given** a debug build on an emulator, **When** the suite logs in, **Then** it drives the native Custom Tab, switches
   back into the WebView context after the App Link, and asserts the signed-in home screen.
2. **Given** the emulator's WebView version does not match the pinned driver versions, **When** the suite starts,
   **Then** it fails fast with a message naming both versions, not a hang.

---

### User Story 13 - Other WebView-only behaviour is covered (Priority: P2)

As the developer, I want the install prompt, the update check and the share target tested in the installed app.

**Why this priority**: these only exist inside the native shell and were previously device-checklist only.

**Independent Test**: each behaviour has one passing Android test; breaking it makes that test fail.

**Acceptance Scenarios**:

1. **Given** the installed debug build, **When** the install prompt, update check and share target tests run, **Then**
   each passes, switching between native and WebView contexts as needed.
2. **Given** biometric unlock, **When** the Android layer is documented, **Then** biometrics stays on the manual device
   checklist and is explicitly listed as not automated.

---

### User Story 14 - CI runs both layers (Priority: P2)

As the developer, I want the web suite to gate merges and the Android suite to run separately on releases and on demand,
both ending with the cleanup step.

**Why this priority**: automation only catches regressions if it runs without being asked.

**Independent Test**: a pull request with a broken web journey cannot merge; the Android job reports its result on a
release and can be started by hand at any time.

**Acceptance Scenarios**:

1. **Given** a pull request, **When** CI runs, **Then** the web E2E job is a required check and blocks the merge on
   failure.
2. **Given** a release, or a manual start by the developer, **When** CI runs, **Then** the Android E2E job runs on an
   emulator and reports its result; a plain merge to main does not start it.
3. **Given** any step of either job fails, **When** the job ends, **Then** the cleanup step and the leftover check still
   run.

---

### User Story 15 - Agents know how to run the E2E suites (Priority: P3)

As the developer, I want a short E2E section where both agent tools read it, including how the suites relate to
agent-browser checks, so that agents run the right thing at the right time.

**Why this priority**: documentation; useful once the suites exist.

**Independent Test**: a fresh Claude Code and OpenCode session can each name the command to run the web suite and when to
prefer agent-browser instead.

**Acceptance Scenarios**:

1. **Given** the E2E docs, **When** an agent reads the frontend instructions, **Then** it finds how to run each suite,
   what each covers, and that agent-browser remains the tool for exploratory and one-off checks while the suites are the
   regression gate.

---

### Edge Cases

- **Nx upgrade drags in Angular**: work stops and is reported; Angular is not upgraded as part of this feature.
- **TypeScript 6 migration incompatible**: declined and recorded.
- **Hook on a file Oxlint cannot parse** (unusual syntax, template in a decorator): silent skip, never a blocking error.
- **Hook while the formatter is still writing**: never lints a partial file.
- **Hook on a very large file or a slow machine**: the hard time limit ends it silently.
- **Both linters report the same rule**: the rule is switched off in one of them; the ownership list in the Oxlint
  configuration is the single place that settles it.
- **A research claim marked unverified**: stays out of the rules file.
- **A gap only relevant to a future module** (RAG, chat memory, tools): marked defer with the module that would trigger
  it, unless the developer decides otherwise in clarify.
- **Dev stack running during an E2E run**: no port, project name, network or volume collision.
- **E2E run killed hard** (terminal closed, CI cancelled): the next run, or the CI `always` step, detects and removes
  leftovers from that run's unique project name; the leftover check reports them.
- **Emulator WebView version drifts** after an image update: the run fails fast naming the mismatch.
- **Keycloak realm for E2E**: the test user exists only in the throwaway realm import; no dev or prod realm is changed.
- **Licence check**: new docs and READMEs must not contain the phrase the `licence-check` job rejects.

## Requirements *(mandatory)*

### Functional Requirements

**Cross-cutting**

- **FR-001**: Each part (A, B, C) MUST be testable and releasable on its own, and none MAY change product behaviour.
- **FR-002**: Before any dependency, tool or image version is raised, the proposed versions and their transitive effects
  MUST be presented to the developer and confirmed.
- **FR-003**: Every change to agent configuration (hooks, `AGENTS.md`, `CLAUDE.md`, `.claude/`, `.opencode/`, skills,
  agents, `opencode.json`, `.mcp.json`, settings) MUST follow the `/dual-agent-config` skill.
- **FR-004**: Commits MUST carry `Refs: 012` (with task ids once tasks exist), per the root commit rules.

**Part A: Nx upgrade**

- **FR-005**: The current lint baseline (full run and single-file run, cold and cached, on the developer machine and in
  CI) MUST be measured and recorded before anything changes.
- **FR-006**: The frontend workspace MUST be upgraded from Nx 22.7.12 to the current Nx 23 release in its own reviewable
  change, merged before any other Part A or Part C change.
- **FR-007**: Before upgrading, compatibility MUST be verified and reported for: Angular 21.2 on Nx 23, the TypeScript 6
  migration against Angular 21.2, Analog, spartan, the Vitest integration, Node 22 or newer (local, CI, frontend build
  images), Bun as package manager, and the Capacitor/mobile build.
- **FR-008**: The TypeScript 6 migration MUST be declined if Angular 21.2 does not support it; Angular MUST NOT be
  upgraded as part of this feature.
- **FR-009**: After the upgrade, lint, unit tests with the coverage gate, the production build, the mobile build and the
  dependency graph MUST give the same results as before, and a deliberate module-boundary violation MUST still fail lint.
- **FR-010**: The constitution's tooling-currency principle (which names Nx 22) MUST be amended to the new Nx version.

**Part A: Oxlint and the agent hook**

- **FR-011**: Oxlint MUST run next to ESLint, never instead of it. ESLint MUST remain the owner of the Angular rules, the
  Angular template rules and module-boundary enforcement.
- **FR-012**: Every lint rule MUST have exactly one owner; no rule MAY be enabled in both linters.
- **FR-013**: Formatting MUST stay with Prettier.
- **FR-014**: One shared command MUST lint a given set of frontend files and print readable diagnostics; it MUST NOT
  depend on either agent tool.
- **FR-015**: In Claude Code, a new post-edit hook script MUST be registered next to the existing post-edit hooks (same
  Write/Edit/MultiEdit matcher, tool input read from stdin) and call the shared command for TypeScript files under the
  frontend workspace only, skipping the paths ESLint also ignores.
- **FR-016**: The hook MUST be advisory (it reports to the agent, never blocks or reverts the edit), silent when there
  is nothing to check or the linter is not installed, and MUST end within a hard time limit.
- **FR-017**: The hook MUST NOT race with the Prettier post-edit hook; if same-event hooks can run concurrently, the two
  MUST be chained.
- **FR-018**: The hook MUST have fixture tests (error file, clean file, ignored path, non-TypeScript file, missing
  linter, timeout) run by the existing hook-test workflow.
- **FR-019**: In OpenCode, the frontend agent instructions MUST tell agents to run the shared command on each TypeScript
  file they edit; a plugin MUST only be added if the instruction proves unreliable.
- **FR-020**: If the baseline shows a single-file ESLint run is nearly as fast as Oxlint, the hook and the shared command
  MUST call ESLint instead; the rest of Part A is unaffected.
- **FR-021**: CI MUST run Oxlint before ESLint and MUST fail when either reports an error.
- **FR-022**: Removing Oxlint MUST take one commit and leave lint, tests and builds working.
- **FR-023**: The new commands and the division of labour between the linters MUST be documented where both agent tools
  read them.

**Part B: AI integration conventions**

- **FR-024**: `specs/_drafts/ai-integration-conventions/research-report.md` MUST be moved to
  `docs/research/spring-ai-architecture.md` with these corrections: GA on 12 June 2026, baseline Spring Boot 4.0 and 4.1,
  and a recorded finding on whether Spring Boot 4.0.3 with Spring AI 2.0.1 has the starter-dependency problem.
- **FR-025**: The root agent instructions MUST list the reference doc under Reference Docs; no always-loaded instruction
  file MAY import it.
- **FR-026**: A short, repo-specific rules file MUST live next to the shared AI code in the backend, readable by both
  tools, with a one-line Claude-specific file beside it that imports it.
- **FR-027**: The backend agent instructions MUST point to the rules file and to the reference doc.
- **FR-028**: The rules MUST contain only findings the research rates high-confidence and that fit this repo; unverified
  items MUST stay in the reference doc only.
- **FR-029**: A gap analysis MUST compare the research with the shared resilient AI service and all AI code in the repo,
  covering at least the topics in User Story 7, and mark each topic worth building, defer or not applicable, with a
  reason and the code checked.
- **FR-030**: Each worth-building gap MUST become a draft under `specs/_drafts/`; no gap MAY be built in this feature.
- **FR-031**: The `/ai-endpoint` skill and both copies of the `code-reviewer` agent (Claude Code and OpenCode) MUST carry
  the checklist, and the two reviewer copies MUST carry the same checklist.
- **FR-032**: Loading of the rules MUST be verified in Claude Code and in a fresh OpenCode session started in the AI
  package; any check that could not be run MUST be stated plainly and handed to the developer.

**Part C: E2E testing**

- **FR-033**: Web E2E tests MUST use Playwright, run against a freshly built app, log in once through Keycloak as a test
  user and reuse that session.
- **FR-034**: Web tests MUST select elements by test id or accessible role/name only.
- **FR-035**: The web suite MUST cover these journeys from the manual E2E campaign: sign-in with session reuse, game
  catalog grid and detail, admin Settings (including approving a user), the FNA briefing view (read-only, no AI
  generation), and catalog chat only up to the model call. No journey in the merge gate MAY trigger a paid model call.
- **FR-036**: The Android suite MUST use Appium 3, WebdriverIO and UiAutomator2 in TypeScript, run on an emulator against
  a debug build, and switch between native and WebView contexts.
- **FR-037**: The Android suite MUST cover native Keycloak login (Custom Tab then App Link), the install prompt, the
  update check and the share target, and nothing that the web layer already covers.
- **FR-038**: Biometric unlock MUST stay a manual device checklist.
- **FR-039**: The Appium, driver and chromedriver versions MUST be pinned to the emulator's WebView version and
  documented; a mismatch MUST fail fast with both versions named.
- **FR-040**: E2E runs MUST NOT touch the dev database and MUST NOT insert rows directly into any database; test data
  MUST be created only through the app's own API or UI.
- **FR-041**: Each run MUST start its own Postgres and Keycloak from a separate compose file, under a unique project name,
  on free ports, with nothing persisted, and with a test user that exists only in that run's realm.
- **FR-042**: Each run MUST remove everything it started on success, failure and interrupt, and in CI even when a step
  fails.
- **FR-043**: A final check MUST fail the run if any container, volume or network from that run is left over.
- **FR-044**: Waiting for services MUST use readiness checks with a time limit, never fixed sleeps.
- **FR-045**: A web E2E CI job MUST run on pull requests and gate merges; a separate Android E2E CI job MUST run on
  releases and on manual start (not on every merge); both MUST end with the cleanup step and the leftover check.
- **FR-046**: A short E2E section MUST be added where both agent tools read it, including how the suites relate to
  agent-browser checks.
- **FR-047**: New docs MUST NOT contain the phrase the `licence-check` job rejects.

### Key Entities

- **Lint rule ownership list**: for each rule, which linter owns it; the single place that settles overlaps.
- **Shared lint command**: the tool-agnostic entry point both the Claude Code hook and the OpenCode instruction call.
- **AI rules file**: the short, high-confidence conventions next to the shared AI code.
- **Reference doc**: the corrected Spring AI research report, loaded on demand.
- **Gap entry**: a research topic, the repo code checked, a verdict (worth building / defer / not applicable), a reason,
  and a draft link when worth building.
- **E2E run**: one execution with its own unique project name, ports, throwaway Postgres + Keycloak, test user, and a
  cleanup + leftover check that always runs.
- **Web journey**: a user journey from the manual campaign automated as a browser test.
- **Version pin record**: emulator image and WebView version with the matching Appium, driver and chromedriver versions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After the Nx upgrade, lint, unit test (including coverage gate), production build, mobile build and
  dependency graph results are identical to the pre-upgrade baseline on the same commit: zero regressions.
- **SC-002**: A lint error introduced by an agent edit reaches the agent within about one second of the edit (target
  under one second on the developer machine, confirmed against the measured baseline), in 100% of fixture cases.
- **SC-003**: The hook never adds more than its time limit to an edit and never blocks one, across all fixture cases.
- **SC-004**: Running both linters over the whole frontend reports zero rules from both.
- **SC-005**: A deliberate boundary violation and a deliberate template-rule violation still fail CI after Part A.
- **SC-006**: Removing Oxlint takes one commit and leaves lint, tests and builds green.
- **SC-007**: Both agent tools load the AI rules in the shared AI package (or the unrun check is named and handed off).
- **SC-008**: The gap list covers 100% of the research checklist topics, each with a verdict and reason; every
  worth-building gap has a draft.
- **SC-009**: No always-loaded instruction file grows by more than a pointer line for Part B.
- **SC-010**: The agreed web journeys and the four Android behaviours each have a passing automated test on main.
- **SC-011**: Zero leftover containers, volumes or networks after passed, failed and interrupted runs, locally and in CI.
- **SC-012**: A pull request with a deliberately broken covered journey cannot merge.
- **SC-013**: The dev database row counts are unchanged by any number of E2E runs.

## Assumptions

- **Order**: the Nx upgrade is its own pull request and merges first; Oxlint, the hook and the E2E tooling build on it.
  Part B can proceed in parallel at any time.
- **Nx target**: the latest Nx 23 release at the time of work, unless the Oxlint plugin needs a specific one; final
  versions are confirmed with the developer before installing (FR-002).
- **Hook mode**: advisory only; CI is the blocking gate. A type-aware lint pass stays in CI only, the hook stays untyped
  for speed.
- **Web journeys**: fixed by clarification (see FR-035); further journeys can be added later as separate work.
- **Android CI trigger**: fixed by clarification (releases plus manual start). A local emulator run on the Mac is documented but not required, because the Mac has no Android SDK yet.
- **Android suite location**: its own project in the frontend workspace, so caching and affected runs apply.
- **E2E realm**: a dedicated throwaway realm import with a test user, derived from the dev realm export; the dev and
  prod realms are not changed.
- **Gaps for future modules** (RAG, chat memory, tools, HITL for tools): marked defer, naming the module that would make
  them relevant, unless clarify decides to draft them now.
- **Evals in CI**: if worth building, recorded as a draft with the cost trade-off (every merge versus nightly) left open
  for that draft.
- Hosted GitHub Actions Linux runners can run the Android emulator for this repo; Podman's Ryuk is disabled locally, so
  cleanup is explicit and never relies on Ryuk.
- The research report attached in the session is identical to
  `specs/_drafts/ai-integration-conventions/research-report.md`.

## Out of Scope

- Vite+, Oxfmt, Oxlint on the Java or Python code, custom Oxlint rules.
- Upgrading Angular itself (reported, not done, if Nx 23 requires it).
- Building any AI gap, changing models or routing, adding AI features.
- iOS, biometric automation, visual regression testing, load testing, running E2E against production.

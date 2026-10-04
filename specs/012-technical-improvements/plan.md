# Implementation Plan: Technical Improvements: Oxlint + Agent Hook, AI Integration Conventions and E2E Testing

**Branch**: `012-technical-improvements` (planning) | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/012-technical-improvements/spec.md`

## Summary

Three independently releasable parts, none changing product behaviour:

- **A. Fast lint feedback.** Upgrade `jordylab-fe` from Nx 22.7.12 to Nx 23.2.1 as its own pull request (Angular 21.2
  and TypeScript 5.9 unchanged), then Angular 22.2.1 with the TypeScript 6.0 it requires as a second pull request
  (needs Node 24.15+ locally). Then add Oxlint next to ESLint with one
  owner per rule, one shared lint command, a Claude Code post-edit hook (advisory, time-limited, serialised with the
  Prettier hook), an OpenCode instruction, and an Oxlint step before ESLint in CI.
- **B. AI integration conventions.** Move the corrected Spring AI report to `docs/research/`, add a short rules file in
  `shared/ai` (with the `CLAUDE.md` import), write the gap analysis, draft the worth-building gaps, and add the
  checklist to `/ai-endpoint` and both `code-reviewer` copies.
- **Platform currency.** Check Spring Boot, Spring AI, Spring Modulith and Gradle for upgrades; recommended: Boot 4.1.1,
  Modulith 2.1.1, Gradle 9.8.0 (Spring AI 2.0.1 is already latest), validated by the full backend check.
- **C. E2E testing.** Playwright web suite (five agreed journeys) and a small Appium Android suite, each run on its own
  throwaway Postgres + Keycloak that is always removed and verified clean; a merge-gating web CI job and an Android job
  on releases and manual start.

Technical approach and rejected alternatives are in [research.md](research.md).

## Technical Context

**Language/Version**: TypeScript ~5.9 → ~6.0 (with Angular 22), Angular ~21.2 → 22.2.1, Nx 22.7.12 → 23.2.1, Node
22.22.3+ or 24.15+ (CI pinned; Mac has 24.13.1 and must be raised), Bun 1.3, Bash for hooks/runner, Java 25, Spring Boot
4.0.3 → 4.1.1, Spring AI 2.0.1 (unchanged), Spring Modulith 2.0.3 → 2.1.1, Gradle 9.3.1 → 9.8.0

**Primary Dependencies**: `@nx/oxlint` 23.2.1 + `oxlint` (latest 1.86.0, exact pin), `@nx/playwright` +
`@playwright/test` (latest 1.63.0), `appium` 3.x (latest 3.8.0) + UiAutomator2 driver + `webdriverio` 9.x +
`@wdio/cli`, `appium-chromedriver` pinned to the emulator WebView. All versions confirmed with the developer before
install (FR-002).

**Storage**: none new in the application. E2E runs use a throwaway Postgres (pgvector image) and Keycloak per run,
nothing persisted.

**Testing**: existing Vitest + Spectator unchanged; new Playwright (web) and WebdriverIO/Appium (Android); hook
fixtures in `.claude/hooks/tests/` run by `hook-tests.yml`.

**Target Platform**: macOS dev machine; GitHub Actions `ubuntu-latest` (web job; Android job with emulator); Podman for
local containers.

**Project Type**: monorepo tooling across `jordylab-fe` (Nx), `.claude/` + `.opencode/` agent config, `jordylab-be`
(docs/rules only), CI.

**Performance Goals**: single-file lint reaches the agent in under 1 s (confirmed against the measured baseline);
hook hard limit 5 s; web E2E job target under 10 minutes; Android job about 10–15 minutes.

**Constraints**: no behaviour change; no hand-seeded rows; no secrets printed; `bun`/`bunx` only; Angular not
upgraded; all agent-config edits through `/dual-agent-config`; version raises confirmed first; licence-check phrase
absent from new docs.

**Scale/Scope**: ~25 frontend libs/apps in one Nx workspace; five web journeys; four Android behaviours; 11+ gap
topics.

**Open items resolved by VERIFY tasks** (listed in research.md): Bun with `nx migrate`/`nx add @nx/oxlint`, actual
Node version in CI and the frontend image, hook parallelism and `additionalContext` field, Boot 4.0.3 + Spring AI 2.0.1
dependency question, WebView version of the chosen emulator image, mixed-content settings in the debug WebView.

## Constitution Check

*GATE: passes before Phase 0; re-checked after Phase 1.*

| Principle | Status |
|-----------|--------|
| I. Clean code (descriptive names, early returns) | Pass: applies to scripts and test code; no abbreviations in new names |
| II. Fail fast, no silent failures | Pass with a deliberate exception: the *advisory hook* skips silently when Oxlint is missing or times out (FR-016, spec requirement). The runner and CI jobs fail loudly (readiness timeouts, leftover check, WebView mismatch) |
| III. Immutable, builder-first | N/A (no Java production code) |
| IV. Testing discipline | Pass: unit-test conventions unchanged; E2E uses different tools at a different layer; hook scripts get fixture tests |
| V. Language & tooling currency | **Amendment required**: names "Angular 21 / Nx 22". FR-010 updates it in the upgrade PR that makes each true, with the "Last Amended" note |

No unjustified violations; Complexity Tracking is empty.

**Post-design re-check**: the design adds no new principle conflicts. The only product-code touch is the `e2e` Angular
build configuration and a debug-only manifest placeholder, proven output-neutral for production builds.

## Project Structure

### Documentation (this feature)

```text
specs/012-technical-improvements/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── lint-changed-command.md     # shared command + Claude hook + OpenCode instruction
│   ├── lint-ownership.md
│   ├── ai-rules-file.md
│   └── e2e-environment.md          # runner, web suite, android suite
├── checklists/requirements.md
└── tasks.md                        # created by /speckit-tasks
```

### Source code (what the feature adds or changes)

```text
jordylab-fe/
├── package.json, bun.lock, nx.json          # Nx 23.2.1 (PR 1); oxlint + @nx/oxlint (PR 2); e2e tooling (PR 4+)
├── .oxlintrc.json                           # PR 2: only rules ESLint does not own
├── tools/
│   ├── lint-changed.sh                      # shared command
│   └── check-lint-ownership.sh
├── apps/
│   ├── jordylab-e2e/                        # Playwright project (web journeys, global login setup)
│   ├── jordylab-mobile-e2e/                 # WebdriverIO + Appium project, PINS.md
│   └── jordylab/src/environments/environment.e2e.ts   # e2e build configuration only
└── e2e/
    ├── run.sh                               # runner: sweep, start, ready, test, clean, verify-clean
    ├── compose.e2e.yaml
    └── realm/e2e-realm.json                 # throwaway realm with admin + guest test users

.claude/
├── settings.json                            # registers post-lint-check.sh (via /dual-agent-config)
├── hooks/post-lint-check.sh                 # new; post-edit-format.sh gains the lock + bunx fix
├── hooks/tests/lint-cases.sh
├── skills/ai-endpoint/SKILL.md              # checklist
└── agents/code-reviewer.md                  # checklist
.opencode/agents/code-reviewer.md            # same checklist
.github/workflows/
├── build.yml                                # Oxlint step before Lint; e2e-web job; Node 22 pin
├── e2e-android.yml                          # release + workflow_dispatch
└── hook-tests.yml                           # runs lint-cases.sh

jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/{AGENTS.md,CLAUDE.md}   # rules (no Java change)
jordylab-be/AGENTS.md, jordylab-fe/AGENTS.md, AGENTS.md                          # pointers, commands, E2E section
docs/research/{spring-ai-architecture.md,spring-ai-gap-analysis.md}
specs/_drafts/<one folder per worth-building gap>/
.specify/memory/constitution.md                                                  # Nx version amendment
```

**Structure Decision**: tooling lives where each tool already lives (`jordylab-fe` Nx workspace, `.claude/`,
`.opencode/`, `.github/workflows/`). E2E projects are Nx projects in `jordylab-fe/apps` so caching and `affected`
apply (clarification Q3). The runner and compose file sit in `jordylab-fe/e2e/` because both suites share them and the
dev compose file stays untouched.

## Delivery order and branches

Parts are releasable alone. Steps 1a then 1b merge first; everything that adds frontend tooling builds on them. 1c and
step 4 are independent (4's starter-dependency answer uses 1c's result if 1c lands first).

| # | Change | Branch / PR | Needs before start |
|---|--------|-------------|--------------------|
| 1a | Baseline measurement + prerequisite check + Nx 23.2.1 upgrade (Angular 21.2, TS 5.9) + constitution amendment (Nx) + `npx`→`bunx` fix (separate commit) | own branch, own PR | **Developer confirms target versions** |
| 1b | Angular 22.2.1 + TypeScript 6.0 + `angular-eslint` 22, Analog 2.8, `ng-packagr` 22, CI Node pin, constitution amendment (Angular) | own PR after 1a | Developer confirms versions; **Mac Node raised to 24.15+** |
| 1c | Backend: Spring Boot 4.1.1 + Modulith 2.1.1 + Gradle 9.8.0 (fallback 4.0.8 / 2.0.8), dependency-tree record for the report | independent PR, any time | Developer confirms versions |
| 2 | Oxlint, `.oxlintrc.json`, shared command, ownership check, CI Oxlint step, docs | own PR after 1 | Developer confirms the Oxlint version; baseline decision (Oxlint or ESLint in the command) |
| 3 | Agent hook + lock in the format hook + fixtures + OpenCode instruction (all via `/dual-agent-config`) | own PR after 2 (or with 2) | none |
| 4 | Part B: doc move + corrections, rules file, gap analysis, drafts, skill/agent checklist, loading verification | independent PR, any time | none |
| 5 | E2E environment runner + web suite + web CI job | own PR after 1 | Developer confirms Playwright version; sets the required check in branch protection |
| 6 | Android: e2e build configuration, Appium project, PINS.md, Android CI | own PR after 5 | Developer confirms Appium/driver versions; emulator image choice |

Each commit carries `Refs: 012/T###` once tasks exist. The Nx upgrade PR leaves lint, tests with coverage, both
builds and the dependency graph unchanged (FR-009 definition from clarification).

## Version approval gate (FR-002)

Before steps 1a, 1b, 1c, 2, 5 and 6 install anything, the exact versions and what they pull in are presented in chat and
confirmed. Current proposals (re-resolved on the day):

| Tool | Proposed | Note |
|------|----------|------|
| Nx family | 23.2.1 | Angular 21.2 in range; no Angular change |
| Angular | 22.2.1 (step 1b) | requires TypeScript `>=6.0 <6.1` and Node 22.22.3+ / 24.15+ |
| TypeScript | `~5.9` in 1a, `~6.0` in 1b | `typescript-eslint` 8.71.0 supports `<6.1` |
| angular-eslint / Analog / ng-packagr | 22.5.0 / 2.8.0 / 22.2.4 | Angular-coupled, move with 1b |
| Spring Boot / Modulith / Gradle | 4.1.1 / 2.1.1 / 9.8.0 (fallback 4.0.8 / 2.0.8) | Spring AI stays 2.0.1 (latest) |
| oxlint / @nx/oxlint | 1.86.0 / 23.2.1 | plugin peer `^1.43`; docs say 1.70+ |
| Playwright | 1.63.0 | browser binaries download |
| Appium / WebdriverIO | 3.8.0 / 9.32.0 | driver and chromedriver pinned to the WebView |

## Risks

- Nx 23 migration changes more than expected (generated config, cache inputs): reviewed in its own PR (1a).
- Angular 22 / TypeScript 6 may break a tool in the chain (spartan alpha, Analog, Spectator, generated code): 1b is its
  own PR; if it cannot be made green it is reported and the workspace stays on Nx 23 + Angular 21.2.
- Mac Node 24.13.1 is below Angular 22's requirement: raising it is a developer handoff before 1b.
- Spring Boot 4.1 may break the backend check (Modulith pairing, Spring AI 2.0.1 on Boot 4.1): fallback to 4.0.8.
- `@nx/oxlint` is experimental: pin it; the hook and shared command do not depend on the plugin.
- The two linters double-report: the ownership check fails the build on any overlap.
- Hooks run in parallel: lock-directory serialisation plus a concurrent fixture case.
- Android login against a throwaway Keycloak needs build-time URL and App Link host changes and cleartext in the debug
  WebView; if the App Link cannot be approved on the emulator, fall back to delivering the callback by `adb` intent
  and report the reduced fidelity.
- Hosted emulator job is slow and can be flaky; it is not a merge gate (clarification Q2).
- `oven/bun:1` image may lack a Node 22+ runtime for Nx 23: verified in step 1 by building the frontend image.

## Complexity Tracking

No constitution violations to justify.

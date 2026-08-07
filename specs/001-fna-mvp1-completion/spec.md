# Feature Specification: Complete FNA MVP1

**Feature Branch**: `001-fna-mvp1-completion`
**Created**: 2026-08-01
**Status**: Draft
**Input**: Repository audit of 2026-08-01 identifying gaps between documented and actual behaviour in the `fna` and `shared` modules.

---

## Overview

FNA is the only real module in JordyLab and it works end to end: RSS ingestion, deduplication, article scraping, live prices, a daily AI briefing, and three routed Angular views. This feature does not add capability. It closes the gap between what `AGENTS.md` claims the system does and what the code actually does, so that MVP1 can be called finished without an asterisk.

Three gaps, in priority order:

1. **AI routing is documented as local-primary but should be cloud-primary.** The original design assumed local inference with cloud fallback. The decision for MVP1 is reversed: FNA pins to the cloud provider (Anthropic Claude), and local inference is out of scope. The remaining AI work is to route every call through a single resilient service that supports future providers, to record provider attribution per call, and to make provider selection configurable without a code change. Fallback to a secondary provider is **not** implemented in MVP1 because there is no second provider.
2. **Frontend module boundaries are documented but not enforced.** The Nx `depConstraints` rule is a wildcard, so the architecture rules exist only in prose.
3. **Frontend has no test discipline.** One spec file covers 17 source files; none of the three views or the API service are tested.

---

## Clarifications

These block a complete plan and should be resolved before `/plan`.

### Session 2026-08-01

- Q: When the daily briefing job cannot reach the cloud provider, how should it retry before giving up for the day? → A: Fail the run and wait for the next scheduled cron tick (next day 06:30) — no retry logic.
- Q: Where does the provider failure reason come from when recording a failure event (FR-007)? → A: Normalize to a fixed, bounded set of error reasons (e.g., `unreachable`, `timeout`, `rate-limited`, `auth-failed`, `unknown`) — not raw exception text.
- Q: What mechanism enforces the dependency boundary rules (FR-011, FR-012)? → A: Extend existing ESLint/Nx lint configuration (`enforce-module-boundaries`, `depConstraints`) — single tool, no separate check script.

- **FNA prefers the cloud provider (Anthropic Claude).** Local inference is out of scope for MVP1. Briefing generation, and every other AI call in FNA, routes to the cloud. The existing `AGENTS.md` and `jordylab-infrastructure-guide.md` text describing local-primary routing for FNA must be updated to reflect this decision as part of the feature's documentation-reality reconciliation.

- **Per-module provider configuration is in scope.** Only one module (`fna`) and one provider (Anthropic) are wired in MVP1, but the resilient service MUST select the provider per module from configuration, not via a global default. This prepares the ground for `gamecatalog` (Ollama, local) and `recipe` (Ollama, local) without a re-architecture, and matches the per-module routing table in the infrastructure guide.

- **Streaming is out of scope.** Briefings are generated on a 06:30 cron and read later — there is no consumer for a streaming variant. The infrastructure guide's streaming option will not be built in MVP1.

- **Both frontend and backend enforce a minimum 80% test coverage threshold in CI.** The threshold is a percentage measured by the coverage tool, applied to both the `jordylab-fe` and `jordylab-be` codebases, and must fail the build when any module drops below 80%. This is paired with the structural expectation that every routed view, component, and service has a corresponding spec file — the percentage is the enforced backstop, the structural rule is the discipline.

---

## User Scenarios & Testing

### Primary user story

As the sole operator and reader of FNA, I open the briefing each morning and expect it to be there. The AI provider is the cloud service; if it is unavailable overnight, the system surfaces that explicitly and retries on a schedule rather than silently skipping the briefing. Provider attribution is recorded in metrics so I can see what served each call.

### Acceptance scenarios

1. **Given** the cloud provider is reachable, **when** the daily briefing job runs, **then** a briefing is produced and metrics attribute the call to the cloud provider.
2. **Given** the cloud provider is unreachable, **when** the daily briefing job runs, **then** the job records an explicit, named error (not a silent missing briefing), does not mark the briefing as complete, and does not retry until the next scheduled cron tick (the following 06:30).
3. **Given** a cloud call fails mid-request, **when** the briefing job handles it, **then** the system records the failure with a reason and surfaces an explicit error; it does not silently degrade.
4. **Given** a provider recently failed and its unhealthy status is still within the cached TTL, **when** another AI call is attempted against that provider, **then** the call fails fast from the cache without a new attempt against the provider.
5. **Given** a developer writes an import that violates the documented library boundaries, **when** lint runs, **then** it fails and names the violated constraint.
6. **Given** the frontend test suite runs, **when** any of the three views or the API service is exercised, **then** rendering, populated state, empty state, and error state are all covered.

### Edge cases

- The cloud provider rate-limits the briefing request: the system must surface the limit explicitly as a named error and surface it as a failed briefing for that tick, not silently skip it. The next attempt is the following 06:30 cron tick.
- The real AI call itself hangs: it must time out (`call-timeout-seconds`, FR-008a) rather than block the caller indefinitely.
- Backend unreachable from the frontend: each view degrades to a stated error, not an empty screen.

---

## Requirements

### Functional — AI provider resilience

- **FR-001**: The system MUST route all AI calls through a single resilient service that selects the provider based on per-module configuration. MVP1 wires one provider (Anthropic) for the `fna` module; the service is designed to accept additional providers without a re-architecture.
- **FR-002**: The system MUST cache provider health status for a configurable TTL so that a
  provider known to be currently failing is short-circuited to an immediate failure result,
  rather than being retried against on every call until the TTL expires. There is no separate
  active health probe — health status is derived passively from the outcome of real AI calls
  (see `ProviderHealth` in `data-model.md`).
- **FR-003**: The system MUST treat a mid-request failure of the provider as an explicit, named error, not a silent missing briefing, and MUST surface the failure reason.
- **FR-004**: The system MUST refresh cached health status when a runtime failure is observed.
- **FR-005**: The system MUST fail with an explicit named error when the configured provider is unavailable, rather than silently degrading. The briefing job makes a single attempt per cron tick and does not retry until the next scheduled tick; there is no in-job retry or fallback path.
- **FR-006**: The system MUST record, per AI call, which provider served it and which module originated it.
- **FR-007**: The system MUST record provider failure events with the provider name and a normalized reason drawn from a fixed, bounded set (e.g., `unreachable`, `timeout`, `rate-limited`, `auth-failed`, `unknown`). Raw exception text MUST NOT be used as the recorded reason.
- **FR-008**: Reading cached provider health status MUST be a fast, in-memory, non-blocking
  operation that never itself issues a network call. There is no separate active health probe
  distinct from a real AI call (FR-002) — this requirement covers only the cache read; the bound
  on the real call itself is FR-008a.
- **FR-008a**: The real AI generation call MUST be bounded by its own configured timeout
  (`call-timeout-seconds`). On timeout, the in-flight call MUST be cancelled rather than left
  running on a leaked thread. Thread interruption alone is not a guaranteed socket-level abort
  for the underlying HTTP client (Reactor Netty via `ReactorClientHttpRequestFactory`) — the
  call MUST also be bounded at the HTTP-client level (`spring.http.clients.read-timeout`) as a
  backstop.
- **FR-009**: All AI calls in the system MUST route through the resilient service. No module may call a chat model directly.
- **FR-010**: Provider selection MUST be configurable per module without a code change. Each module declares its provider in configuration; the resilient service routes accordingly.

### Functional — frontend boundary enforcement

- **FR-011**: Lint MUST fail when a library tagged `type:api` imports from a library tagged `type:ui`. Enforcement is via the existing ESLint/Nx `enforce-module-boundaries` and `depConstraints` configuration — no separate check script.
- **FR-012**: Lint MUST fail when a library imports across domain scopes, except when importing from the shared scope. Enforcement is via the same ESLint/Nx configuration.
- **FR-013**: Boundary enforcement MUST run in CI, not only locally.

### Functional — test coverage and discipline

- **FR-014**: Every routed view MUST have tests covering render, populated state, empty state, and error state.
- **FR-015**: The API service MUST have tests covering success and HTTP error paths.
- **FR-016**: The frontend test suite MUST run in CI and MUST fail the build on failure.
- **FR-016a**: Frontend test coverage MUST meet a minimum 80% threshold enforced in CI; the build MUST fail when any library drops below the threshold.
- **FR-016b**: Backend test coverage MUST meet a minimum 80% threshold enforced in CI; the build MUST fail when any module drops below the threshold.
- **FR-016c**: Coverage thresholds MUST be measured per module (backend) and per library (frontend), not only as a workspace-wide aggregate, so that a high-coverage module cannot mask an untested one.

### Functional — housekeeping

- **FR-017**: Unused test infrastructure MUST be removed so the test configuration reflects what is actually used.
- **FR-018**: The project MUST make a recorded decision on its use of pre-release upstream dependencies — either an explicit accepted risk with pinned versions, or a plan to move to stable releases. An undocumented default is not acceptable.

### Non-functional

- **NFR-001**: The health-check mechanism MUST NOT add meaningful latency to AI calls on the cached path.
- **NFR-002**: Provider failure MUST NOT cause a silently missing briefing. The system MUST surface the failure explicitly and allow the caller's schedule to retry.
- **NFR-003**: Existing backend conventions apply unchanged: explicit types, builder with validation, no direct chat-model access, module-owned Flyway schemas.

---

## Success Criteria

- **SC-001**: A full ingest-and-briefing cycle completes successfully with the cloud provider reachable. Metrics attribute the AI call to the cloud provider.
- **SC-002**: Metrics show provider attribution for every AI call, and provider failures are individually countable with a reason.
- **SC-003**: A deliberately introduced boundary violation fails lint in CI.
- **SC-004**: Every frontend component and service has a corresponding spec file, the suite passes in CI, and both frontend and backend coverage meet the 80% threshold per module/library.
- **SC-005**: `AGENTS.md` and `jordylab-infrastructure-guide.md` describe no behaviour that the code does not implement. This is the actual definition of done for this feature.

---

## Out of Scope

- MVP2 features: web search tool calling, feed expansion beyond current sources, feed management UI, portfolio snapshots, report feedback.
- The Flyway migration naming inconsistency. Already-applied migrations stay as they are; the dated convention applies to new migrations only.
- Every module other than `fna` and `shared`.
- Local inference integration (Ollama on the main desktop). The resilient service is designed to accept additional providers, and per-module config can route a module to Ollama when added, but wiring Ollama — health checks, model loading, LAN reachability — is a separate feature.
- Multi-provider fallback. MVP1 wires one provider (Anthropic) for `fna`. Fallback to a secondary provider is deferred until a second provider is integrated.
- An eval harness for comparing briefing quality across providers. With a single provider in MVP1 this has no comparison baseline; it becomes relevant when local inference is added.
- LLM tracing and observability tooling beyond the existing metrics.

---

## Assumptions

- The cloud provider (Anthropic) credentials are already configured and reachable from the application; this feature consumes that, it does not set it up.
- The existing briefing prompt and its Belgian-retail-investor persona are unchanged by this work.
- No database schema changes are required.

---

## Review Checklist

- [x] All clarifications resolved
- [x] No requirement describes an implementation choice
- [x] Every requirement is testable
- [x] Success criteria are measurable without reading the code
- [x] Scope boundaries are explicit

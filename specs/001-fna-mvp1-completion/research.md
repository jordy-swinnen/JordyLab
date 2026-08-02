# Phase 0 Research: Complete FNA MVP1

**Date**: 2026-08-01
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

---

## R1: Per-module AI provider configuration in Spring AI 2.0.0-M2

**Decision**: Use a Spring `@ConfigurationProperties` record (`AiModuleConfig`) keyed by module name, with each module declaring its provider name and model. `ResilientAiService` reads the active provider for the calling module from this config at call time.

**Rationale**: Spring AI 2.0.0-M2 auto-configures `AnthropicChatModel` from `spring.ai.anthropic.*` properties. For per-module routing, the resilient service needs a map of module → provider lookup that is decoupled from Spring AI's own auto-configuration. A `@ConfigurationProperties` record is idiomatic Spring Boot 4, immutable, and supports `records` per the constitution. MVP1 wires one entry (`fna → anthropic`); adding `gamecatalog → ollama` later is a config-only change.

**Alternatives considered**:
- *Global single property* (`spring.ai.provider=anthropic`): Rejected — spec FR-010 requires per-module config.
- *Spring AI's own advisor/registry*: Rejected — Spring AI 2.0.0-M2 does not expose a per-module provider registry; building on top of `ChatModel` directly is simpler and keeps the abstraction in `shared/ai`.

---

## R2: Health-check-and-cache pattern for a single cloud provider

**Decision** (revised during PR #4 review — see Alternatives): `ProviderHealthCache` is a simple in-memory `ConcurrentHashMap<ProviderName, HealthStatus>` with a configurable TTL (default 30s). Health status is derived passively from the outcome of real AI calls — `recordSuccess`/`recordFailure` are called from `ResilientAiService.call()` after every real attempt. There is no separate active probe. On each AI call, the cache is consulted first: if the cached status is unhealthy and within TTL, the call short-circuits to a failure result without attempting the provider; otherwise (healthy, stale, or absent) the real call proceeds and its outcome updates the cache. On runtime failure (FR-004), the cache is invalidated immediately.

**Rationale**: With a single cloud provider and no fallback path, a separate reachability probe adds an extra API call and its own timeout/config surface for no real benefit — the real AI call itself is the most accurate health signal available, and skipping it entirely when a provider is known-down (within TTL) already achieves the goal ("avoid paying for a full inference call when the provider is known-down"). The `ConcurrentHashMap` + TTL is the simplest correct implementation for a single-instance monolith.

**Alternatives considered**:
- *Active out-of-band HTTP probe (originally designed here)*: Superseded during PR #4 review. The original design called for a bounded-timeout reachability probe (e.g., a TCP/HTTP HEAD to the provider's API base URL) distinct from the real call, with its own `health-check-timeout-seconds` config. This was never actually implemented — the shipped code derives health from real call outcomes instead — and the review concluded the passive approach is strictly simpler (no extra API call, no extra timeout to configure or keep in sync) with no loss of accuracy for a single-provider, no-fallback system. `health-check-timeout-seconds` was removed as dead configuration.
- *Caffeine cache*: Rejected as over-engineering — the map has one entry and a TTL; `ConcurrentHashMap` with `Instant` timestamps is sufficient and has no dependency.
- *No health check at all*: Rejected — FR-002 requires short-circuiting calls to a known-down provider; NFR-001 requires the cache read itself not add latency on the cached path.
- *Spring Boot Actuator health indicator*: Rejected for MVP1 — Actuator is not wired and the health check here is internal to the AI service, not an externally exposed indicator.

---

## R3: Normalized failure reasons — bounded enum

**Decision**: `ProviderFailureReason` is a Java enum with exactly five values: `UNREACHABLE`, `TIMEOUT`, `RATE_LIMITED`, `AUTH_FAILED`, `UNKNOWN`. The `ResilientAiService` maps caught exceptions to these values at catch time using exception-type inspection (e.g., `SocketTimeoutException → TIMEOUT`, `HttpException(429) → RATE_LIMITED`, `HttpException(401/403) → AUTH_FAILED`, `UnknownHostException → UNREACHABLE`, fallback → `UNKNOWN`).

**Rationale**: FR-007 requires a normalized, bounded set (clarification Q2, answer B). Five values cover the realistic failure modes of a single cloud HTTP API without exploding into provider-specific error codes. The enum is the test contract: tests assert against enum values, never raw exception text.

**Alternatives considered**:
- *Six+ values with `OVERLOADED`, `INVALID_REQUEST`, `SERVER_ERROR`*: Rejected — adds complexity without test value for MVP1; `UNKNOWN` covers the long tail.
- *String constants instead of enum*: Rejected — enums give exhaustiveness in `switch` and compile-time safety, per Clean Code discipline.

---

## R4: Briefing job failure handling — single attempt, no retry

**Decision**: `BriefingGeneratorService.generateBriefing()` calls `ResilientAiService`, inspects the returned `AiCallResult`. On failure, it logs the normalized reason, does not save a `Briefing` entity, and returns without throwing (or throws a caught domain exception handled by the scheduler). The `@Scheduled(cron = "0 30 6 * * *")` fires once daily; the next attempt is the following 06:30.

**Rationale**: Clarification Q1 answer A — fail the run, wait for the next cron tick. No retry logic in the job. The current implementation throws `RuntimeException` on failure; the new implementation must surface an explicit named error (FR-005) without silent degradation. The job must not save an incomplete briefing.

**Alternatives considered**:
- *Retry-with-backoff*: Rejected by the user (Q1: A).
- *Spring `@Retryable`*: Rejected — adds Spring AOP complexity for a behaviour the user explicitly opted out of.

---

## R5: Nx ESLint boundary enforcement — concrete depConstraints

**Decision**: Replace the wildcard `depConstraints` in `eslint.config.mjs` (line 18-21) with three concrete rules:
1. `sourceTag: 'type:api'` → `onlyDependOnLibsWithTags: ['type:api', 'type:shared']`
2. `sourceTag: 'type:ui'` → `onlyDependOnLibsWithTags: ['type:api', 'type:ui', 'type:shared']`
3. `sourceTag: 'scope:fna'` → `onlyDependOnLibsWithTags: ['scope:fna', 'scope:shared']`

Where `type:shared` / `scope:shared` are tags for any future shared utility libs (none exist yet — the rule is forward-compatible). The `allow` array for ESLint config files stays as-is.

**Rationale**: FR-011 (api must not import ui) and FR-012 (no cross-scope imports except shared) map directly to Nx `depConstraints`. The existing `sourceTag: '*' → onlyDependOnLibsWithTags: ['*']` wildcard is the documented gap. The three rules above are the exact enforcement of the AGENTS.md boundary policy (type:ui can depend on type:api; type:api cannot depend on type:ui; scopes are isolated except shared).

**Alternatives considered**:
- *Custom boundary-check script*: Rejected by the user (Q3: A — use existing ESLint config).
- *Spring Modulith-style verification tests*: Rejected — backend only; frontend has no equivalent.

---

## R6: Coverage tooling — JaCoCo (backend) + Vitest coverage (frontend)

**Decision**:
- **Backend**: Add the `jacoco` Gradle plugin to `build.gradle.kts`. Configure `jacocoTestCoverageVerification` with `minimum = 0.80` at the class/package level. Wire `check` to depend on `jacocoTestCoverageVerification` so the build fails below 80%.
- **Frontend**: Enable coverage in the Vitest executor options (`@nx/vitest:test` already supports `coverage.enabled`, `coverage.thresholds.lines = 80`). Add a `coverage` target or extend the existing `test` target to include coverage thresholds. Configure per-lib thresholds in each `project.json` or in `nx.json` `targetDefaults`.

**Rationale**: FR-016a/b/c require an 80% threshold enforced in CI, measured per module/library. JaCoCo is the standard JVM coverage tool and integrates with `./gradlew check`. Vitest coverage uses `istanbul` or `v8` provider and supports thresholds natively via `@nx/vitest`. Per-library measurement on the frontend is achieved by the fact that each lib has its own `test` target with its own coverage report directory (already configured in `project.json` — `reportsDirectory: "../../../coverage/libs/fna/api"`).

**Alternatives considered**:
- *Kover* (Kotlin): Rejected — backend is Java, not Kotlin source; JaCoCo is the JVM standard.
- *c8* (Node): Rejected — Vitest's built-in coverage is configured per-project and integrates with Nx targets.
- *Aggregate-only threshold*: Rejected — FR-016c explicitly requires per-module/library measurement to prevent masking.
# Implementation Plan: Complete FNA MVP1

**Branch**: `001-fna-mvp1-completion` | **Date**: 2026-08-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-fna-mvp1-completion/spec.md`

## Summary

Close the gap between documented and actual behaviour in the `fna` and `shared` modules so MVP1 can be called finished. Three work streams: (1) rebuild `ResilientAiService` as a per-module-routed, health-check-and-cache service with normalized failure recording, pinning `fna` to the cloud provider (Anthropic) — local inference and multi-provider fallback are out of scope; (2) replace the wildcard Nx `depConstraints` with concrete `scope:*` / `type:*` boundary rules enforced in CI; (3) add frontend tests for every view, component, and service, plus an 80% coverage threshold on both frontend and backend enforced in CI.

## Technical Context

**Language/Version**: Java 25 (backend), Angular 21 / Nx 22 (frontend), TypeScript 5.x, Bun

**Primary Dependencies**:
- Backend: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring AI 2.0.0-M2 (Anthropic), Lombok, Flyway
- Frontend: Nx 22, spartan/ui (brain + helm), `@ngneat/spectator/vitest`, Vitest
- Coverage (to add): JaCoCo (backend), Vitest coverage / `@nx/vitest` coverage (frontend)

**Storage**: PostgreSQL 16 + pgvector (no schema changes for this feature)

**Testing**:
- Backend: JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, MockMvc
- Frontend: Vitest + `@ngneat/spectator/vitest`, Marble testing for observables

**Target Platform**: Hetzner VPS (Docker Compose) for backend; local dev for frontend

**Project Type**: Web application (Spring Boot monolith + Angular SPA)

**Performance Goals**: Health-check cache MUST NOT add meaningful latency to AI calls on the cached path (NFR-001)

**Constraints**: Single AI call per briefing cron tick; no intra-job retry; no fallback path

**Scale/Scope**: Single operator; one AI module (`fna`); three routed Angular views; one API service

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | Pass | SOLID/KISS applied: per-module config via single resilient service, early returns, builder-first for any new DTOs |
| II. Fail Fast, No Silent Failures | Pass | FR-003/FR-005/FR-007 enforce explicit named errors and normalized failure reasons — no silent degradation |
| III. Immutable, Builder-First Design | Pass | New DTOs (`AiCallResult`, `ProviderFailure`) use record or `@Builder`; config is immutable per-module map |
| IV. Testing Discipline | Pass | Spec demands tests for every view/component/service plus 80% coverage both sides; AssertJ `assertSoftly`, TestBuilders, Vitest + Spectator |
| V. Language & Tooling Currency | Pass | Java 25, Angular 21 / Nx 22, Spring AI 2.0.0-M2 — all current |

No constitution violations — no complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/001-fna-mvp1-completion/
├── plan.md              # This file
├── research.md          # Phase 0: research findings
├── data-model.md        # Phase 1: entities, config model, failure model
├── quickstart.md        # Phase 1: end-to-end validation guide
├── contracts/           # Phase 1: interface contracts
│   └── resilient-ai-service.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
jordylab-be/                                   # Spring Boot 4 monolith
├── src/main/java/dev/jordy/jordylab/
│   ├── shared/
│   │   └── ai/
│   │       ├── ResilientAiService.java        # REWRITE: per-module routing, health cache, failure recording
│   │       ├── AiModuleConfig.java            # NEW: per-module provider config properties
│   │       ├── AiCallResult.java              # NEW: record — provider name, module, success/failure, reason
│   │       ├── ProviderHealthCache.java       # NEW: TTL-cached health status per provider
│   │       └── ProviderFailureReason.java      # NEW: enum — normalized failure reasons
│   └── fna/
│       └── service/
│           └── BriefingGeneratorService.java   # UPDATE: handle AiCallResult explicitly, no retry
├── src/test/java/dev/jordy/jordylab/shared/ai/
│   ├── ResilientAiServiceTest.java            # REWRITE
│   ├── AiModuleConfigTest.java                # NEW
│   ├── ProviderHealthCacheTest.java           # NEW
│   └── ProviderFailureReasonTest.java         # NEW
├── build.gradle.kts                           # UPDATE: add JaCoCo plugin + 80% threshold
└── src/main/resources/application.yml          # UPDATE: per-module provider config

jordylab-fe/                                   # Nx Angular 21 monorepo
├── eslint.config.mjs                          # UPDATE: replace wildcard depConstraints with concrete rules
├── nx.json                                     # UPDATE: add coverage threshold target defaults
├── libs/fna/api/
│   ├── src/                                    # EXISTING — service stays
│   └── src/test/                               # NEW: API service tests (success + HTTP error)
├── libs/fna/ui/
│   ├── src/                                    # EXISTING — views stay
│   └── src/test/                               # NEW: view tests (render, populated, empty, error)
├── apps/fna/
│   └── project.json                           # UPDATE: add scope tag if missing for boundary rules
└── vitest coverage config                     # UPDATE: enable coverage + 80% threshold
```

**Structure Decision**: The existing two-project layout (backend monolith + frontend monorepo) is unchanged. This feature modifies the `shared/ai` package and the `fna` module's briefing service, plus frontend config and test files. No new modules, no new top-level projects.
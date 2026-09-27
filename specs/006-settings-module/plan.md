# Implementation Plan: Settings Module (Users & AI Models)

**Branch**: `006-settings-module` | **Date**: 2026-09-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-settings-module/spec.md`; tech context from
`specs/_drafts/006-settings/speckit-prompts.md` §3, verified against the live codebase and vendor docs
in [research.md](research.md) (decisions D1–D12).

## Summary

A new admin-only **Settings** module (`dev.jordy.jordylab.settings`, its own `settings` Flyway schema, its own frontend
domain libs, its own nav tab) with two sub-pages:

1. **Users** — Keycloak-backed user administration: self-registration (email as login name, password policy, no default
   app role → pending), in-app approve (grant `guest`) / reject (disable) / revoke (drop `guest` + end sessions) / reset
   password (temporary, shown once), last-admin protection, and a pending-count badge.
2. **AI Models** — per-AI-feature model selection over a live OpenRouter model catalog, replacing the per-module AI
   config: `ResilientAiService` is refactored to call the primary gateway (OpenRouter via `OpenAiChatModel`) and fall
   back to Anthropic direct (`claude-sonnet-5`) on any primary failure, recording provider/model/fallback per call;
   Ollama is removed entirely.

Authorization moves from "any authenticated user" to a **deny-by-default role matrix** (`admin`/`guest` realm roles;
guests = Game Catalog read + chat only), enforced in the backend and mirrored by role-aware frontend guards and nav.
Guests get a persisted daily chat-message limit (20/day, calendar-day reset). Sign-ups can notify the admin via Ntfy (
P3). Spring AI is bumped 2.0.0-M2 → **2.0.1 GA** as part of the AI refactor (research D1).

## Technical Context

**Language/Version**: Java 25 (backend) · TypeScript + Angular 21, zoneless (frontend) · Bun + Nx 22.5.4

**Primary Dependencies**: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring Security (OAuth2 resource server), Spring AI
**2.0.1 GA** (bumped from 2.0.0-M2, research D1; `spring-ai-starter-model-openai` + `spring-ai-starter-model-anthropic`,
`spring.ai.model.chat=openai`), Flyway, Lombok · keycloak-js 26.2.4 · spartan/ui + Night Lab tokens · Keycloak 26.3.2 (
server) + Admin REST via a thin RestClient client (research D3) · OpenRouter gateway (OpenAI-compatible, base URL incl.
`/v1`, research D2) + Anthropic direct fallback (`claude-sonnet-5`) · Ntfy (optional) · test:
`com.github.dasniko:testcontainers-keycloak` 4.3.1, WireMock 3.13.0, Vitest + Spectator

**Storage**: PostgreSQL 16 + pgvector — new `settings` schema (3 tables, [data-model.md](data-model.md)); Keycloak
remains the user store (no user rows in JordyLab)

**Testing**: JUnit 5 / AssertJ (`assertSoftly`) / Mockito (explicit values, assigned captors) / Testcontainers (
Postgres, Keycloak via dasniko) / WireMock (gateway failure modes, model catalog) / MockMvc (`@Language("JSON")`) on the
backend; Vitest + Spectator (`useValue` + `vi.fn` + real `signal(...)` mocks) on the frontend

**Target Platform**: Podman compose in dev (same files drop into the prod stack); OVHcloud k8s manifests, TLS and prod
Keycloak hostname out of scope (separate deployment spec)

**Project Type**: modular-monolith web service + SPA

**Performance Goals**: AI Models page + cached catalog < 2 s (SC-006) · model change takes effect with 0 restarts (
SC-003) · guest limit check is one indexed read + one upsert · users list is a live small-realm read (single-digit
users)

**Constraints**: deny-by-default authorization (FR-002) · admin service credentials never reach the browser (FR-010) ·
no SMTP (no email flows; UPDATE_EMAIL AIA unusable) · guest chat limit 20/day persisted (FR-009) · 30-minute
access-token lifespan (revocation lag) · scan client's 60 s read timeout and device flow unchanged

**Scale/Scope**: 2 app roles + 1 device role · single-digit users · 4 AI features · ~458 gateway models (cached,
trimmed) · 3 new tables · 1 new backend module + 2 new frontend libs

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| #   | Principle (constitution)                              | How the design satisfies it                                                                                                                                                                                       | Status                              |
|-----|-------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|
| I   | Clean Code (SOLID/KISS/YAGNI/DRY)                     | Status is derived, not stored (D6); one filter, one client per external API; the port inversion (D4) is the minimal mechanism that keeps `shared` persistence-free — no heavier event bus, no reflection registry | PASS                                |
| II  | Fail Fast, No Silent Failures                         | Both providers fail → explicit `AiCallResult.failure`; empty catalog cache → `503 GATEWAY_CATALOG_UNAVAILABLE`, never a silent empty list; Keycloak failures → `503 KEYCLOAK_UNAVAILABLE`, no partial state       | PASS                                |
| III | Immutable, Builder-First                              | Entities follow the canonical structure (partial builder with `Preconditions` in `build()`, UUID ids, `BaseEntity` audit); DTOs are records                                                                       | PASS (enforced per entity in tasks) |
| IV  | Testing Discipline                                    | TestBuilder fixtures; Testcontainers Keycloak with real JWTs for the role matrix; WireMock for every failure reason; Vitest/Spectator with `useValue` + `vi.fn` + real signals                                    | PASS                                |
| V   | Language & Tooling Currency                           | Java 25 / Angular 21 / Nx 22 / signals (no NgRx); Spring AI moved off a milestone to GA (D1) — the repo's own documented plan                                                                                     | PASS                                |
| —   | Module boundaries (root AGENTS + Modulith)            | One-way `settings → shared` only (D4/D5); gamecatalog/fna internals untouched except the 4 AI call sites; `ModularityTests` stays green; schema-per-module via one Flyway migration                               | PASS                                |
| —   | Container–Presentation / signal store in api lib (FE) | Users + AI Models pages as container/presentation pairs; stores in `libs/settings/api`                                                                                                                            | PASS                                |

**Post-Phase-1 re-check (after data-model, contracts, quickstart): unchanged — PASS.** No complexity-tracking entries
are needed; the only design mechanisms that could look like added complexity (port + event, filter) are the
boundary-compliant alternatives to direct cross-module imports and were chosen over simpler-but-boundary-breaking
options (research D4/D5 record the rejected alternatives).

## Project Structure

### Documentation (this feature)

```text
specs/006-settings-module/
├── plan.md                      # this file
├── research.md                   # Phase 0 — verified facts + decisions D1–D12
├── data-model.md                 # Phase 1 — entities, status derivation, migration
├── quickstart.md                 # Phase 1 — end-to-end validation guide
├── contracts/
│   ├── access-matrix.md          # route→role matrix, error shapes, FE role contract
│   ├── settings-users-api.md      # Users REST contract
│   └── settings-ai-models-api.md  # AI Models REST contract
└── tasks.md                      # Phase 2 — /speckit-tasks (not created by /speckit-plan)
```

### Source Code (repository root)

Web application: modular-monolith backend + Nx SPA frontend. New code in the settings module/libs; refactors
concentrated in `shared/ai`, `shared/config`, the app shell and the realm export.

```text
jordylab-be/src/main/java/dev/jordy/jordylab/
├── settings/                              # NEW module — public root + internals per repo layout
│   ├── SettingsProperties.java            # jordylab.settings.* (guest-chat limit, model-catalog TTL, ntfy)
│   ├── domain/                            # AiFeatureModelSetting, AiFeatureLastRun, GuestChatUsage + repository/
│   ├── rest/
│   │   ├── client/                        # KeycloakAdminClient (RestClient + client-credentials, D3),
│   │   │                                  # OpenRouterModelCatalogClient (cached /models), NtfyClient
│   │   └── controller/                    # SettingsUsersController, SettingsAiModelsController,
│   │                                      # GuestChatLimitFilter (web-layer placement, no new sub-package), model/
│   ├── service/                          # KeycloakUserAdministrationService, AiModelSettingsService
│   │                                      # (@Primary AiModelResolver, cache invalidation, last-run listener,
│   │                                      # pending-signup poll/notifier)
│   └── util/
├── shared/
│   ├── ai/                                # REFACTOR — AiFeature enum (registry), AiModelResolver port,
│   │                                      # AiCallCompleted event, ResilientAiService (primary→fallback, metrics,
│   │                                      # MODEL_NOT_FOUND), gateway config; jordylab.ai.modules removed
│   └── config/SecurityConfig.java        # REWRITE — deny-by-default role matrix (contracts/access-matrix.md)
├── fna/service/BriefingGeneratorService.java        # call-site: AiFeature instead of module string
└── gamecatalog/service/{EnrichmentService,ChatService}.java  # call-sites ×3

jordylab-be/src/main/resources/
├── application.yaml                       # gateway/feature/fallback config (research D2), settings props
├── db/migration/V<yyyyMMdd>__settings_create_tables.sql   # NEW
└── prompts/…                              # unchanged

jordylab-be/…
├── build.gradle.kts                       # Spring AI 2.0.1 GA; +openai starter, dasniko; −ollama, −testcontainers-ollama
├── compose/keycloak-realm-export.json     # registrationAllowed, registrationEmailAsUsername, password policy,
│                                          # admin/guest roles, jordy→admin, confidential jordylab-backend client,
│                                          # −jordylab-user (STOP-AND-REPORT gate, research D10)
├── compose/keycloak-theme/jordylab/       # style the registration page (CSS-only, keycloak.v2 base)
├── compose.yaml                           # − commented Ollama service
├── tools/build_client.py + src/main/resources/scripts/jordylab-scan-template.py
│                                          # regenerate: client-download gate text now says admin (was jordylab-user)
└── src/test/…                             # SettingsModuleTest (Keycloak container), ResilientAiServiceTest
                                           # (WireMock primary), TestcontainersConfiguration (−OllamaContainer)

jordylab-fe/
├── libs/settings/api/                     # NEW — service, models, users.store, ai-models.store (+ pending badge feed)
├── libs/settings/ui/                      # NEW — settings-shell, users page, ai-models page (container/presentation),
│                                          # settingsRoutes (roleGuard('admin'))
├── libs/shared/auth/                      # + roles signal (tokenParsed.realm_access.roles), roleGuard, UserMenuComponent
│                                          #   (AIA: UPDATE_PASSWORD / UPDATE_PROFILE), register() passthrough
├── apps/jordylab/                         # settings lazy route, awaiting-approval page, role-filtered nav +
│                                          #   pending badge, user menu in shell; app.spec.ts nav assertions updated
├── apps/fna/, apps/gamecatalog/           # inherit guards from domain route libs (D12)
├── eslint.config.mjs                      # + scope:settings in the type:app constraint
└── tsconfig.base.json                     # + @jordylab-fe/settings/{api,ui} paths

# docs
AGENTS.md (root)                           # Ollama/WireGuard guidance removed, AI-routing table rewritten, Hetzner→OVHcloud
jordylab-be/AGENTS.md, jordylab-fe/AGENTS.md  # settings sections (label: "feature 006 — settings module"), schema list + settings,
jordylab-fe/AGENTS.md                      # eslint/tsconfig gotchas for new scope
```

**Structure Decision**: web application (backend + frontend) on the existing modular monolith; the settings module
follows the exact repo package layout (no new sub-packages; the chat-limit filter sits in `rest/controller/` as the
closest existing web-layer home). Frontend mirrors the fna/gamecatalog domain-lib pattern; guards live in the domain
route libs so all three apps inherit them. The full route→role contract is
in [contracts/access-matrix.md](contracts/access-matrix.md).

## Complexity Tracking

No constitution violations to justify — table left empty by design.

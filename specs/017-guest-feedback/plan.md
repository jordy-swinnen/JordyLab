# Implementation Plan: Guest Feedback and Admin Triage

**Branch**: `017-guest-feedback` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/017-guest-feedback/spec.md` (promoted from the `guest-feedback` draft; decisions recorded in JOR-33 on 2026-11-02 and the clarify pass)

## Summary

Guests get a Feedback tab in Settings (text-only tickets: app, type, title, description, five per day) and see their own tickets' status plus the admin's reply. The admin gets a guest-free Feedback inbox (the idea box): Accept / Backlog / Reject with a guest-visible reply and a private note; Rejected is final. Accepting creates exactly one issue in Linear team `JOR` (Triage state, App + Type labels, `Source: idea box`, no guest personal data, idempotent) through a small GraphQL-over-RestClient client authenticated with Jordy's personal API key. A new ticket notifies the admin through the existing Ntfy path, without the ticket's text.

Technically: a new Spring Modulith `feedback` module (own `feedback` schema, one aggregate `FeedbackTicket`), guest/admin REST endpoints under `/api/feedback/**` gated in `SecurityConfig` (Settings' `/api/settings/**` stays admin-only), a Modulith event for the Ntfy notification, and a new `libs/feedback/{api,ui}` frontend domain pair whose routes the host composes under `/settings` with per-tab role guards.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1, Spring Modulith 2.1.1) · Angular 22 / Nx 23 / Bun · TypeScript

**Primary Dependencies**: Spring Security OAuth2 resource server (Keycloak JWT), Spring Data JPA (existing `BaseEntity`), Flyway, `RestClient` (shared bean + module-private instance for Linear), Lombok; frontend: spartan/ui (brain + helm), Angular signals + Custom Signal Store pattern, `keycloak-js` via `libs/shared/auth`. No AI calls — `ResilientAiService` is not involved. No new dependency is added: Linear's GraphQL is called as JSON over `RestClient` with Jackson, like the existing `IgdbClient`/`KeycloakAdminClient` pattern.

**Storage**: PostgreSQL, new `feedback` schema (Flyway `V20261103001__feedback_create_tables.sql`, `CREATE SCHEMA IF NOT EXISTS` + `SET search_path`), one table `feedback_ticket`.

**Testing**: Backend — JUnit 5, AssertJ (`assertSoftly`), Mockito (explicit values / assigned `ArgumentCaptor`), Testcontainers pgvector (`@ApplicationModuleTest`), WireMock for `LinearClient` (next free `999x` port), MockMvc + `@Language("JSON")`, `RoleMatrixTest` (Keycloak container) for the new access matrix, `EqualsVerifier` entity test, canonical `FeedbackTicketTestBuilder`. Frontend — Vitest + `@ngneat/spectator/vitest` (`useValue` + `vi.fn()`, real `signal(...)` for stores, mocks via `aFooMock()` factories), Playwright journey `feedback.spec.ts` (throwaway stack; Accept asserts the graceful "Linear unavailable" state because the throwaway backend has no `LINEAR_API_KEY`).

**Target Platform**: The existing k3s deployment (backend Deployment + Angular web build); browsers on desktop and the mobile WebView (same build).

**Project Type**: web application (Spring Boot modular monolith + Nx Angular monorepo).

**Performance Goals**: personal-platform scale (a handful of users, a few tickets per week). Accept responds within the Linear client's timeout budget (short connect/read timeouts so a hanging Linear fails fast); ticket list and inbox render instantly at this volume (no pagination).

**Constraints**: Linear Free plan (≈250-issue cap — only accepted tickets reach Linear); no guest personal data in Linear; the personal API key is a backend-only secret (SOPS in prod, never sent to the browser); no status sync from Linear in v1; Settings' existing Users/AI-Models endpoints stay admin-only.

**Scale/Scope**: 1 backend module (≈10 classes + tests), 1 Flyway migration, 2 frontend libs + host-route/navigation edits, 1 e2e journey, deploy secret threading.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Verdict | Notes |
|---|---|---|
| I. Clean Code (SOLID/KISS/YAGNI/DRY) | Pass | One small module following the canonical package layout; reuses the Ntfy/Modulith-event, TestBuilder, `BaseEntity` and signal-store patterns instead of inventing new ones. No GraphQL client library — hand-built JSON over `RestClient` (one mutation + three lookups, KISS). |
| II. Fail Fast, No Silent Failures | Pass, one documented exception | Linear failures surface immediately as `503 LINEAR_UNAVAILABLE` and are recorded on the ticket (`accept_error`) for retry. Ntfy delivery failure is logged-and-swallowed by the existing `NtfyClient` **by design** (spec FR-017: a notification failure must never fail the submission) — same intentional exception as `UserSignUpPending`/`BriefingReady`. |
| III. Immutable, Builder-First | Pass | `FeedbackTicket` follows the canonical entity structure (`@Builder` + partial builder with `Preconditions` in `build()`, `@NoArgsConstructor(PROTECTED)`, `@AllArgsConstructor(PRIVATE)`, named mutation methods, `registerEvent`). All DTOs are records. |
| IV. Testing Discipline | Pass | AssertJ `assertSoftly`, no `any()` (explicit values or an assigned, asserted `ArgumentCaptor`), Testcontainers + WireMock + MockMvc with `@Language("JSON")`, `@ApplicationModuleTest`, `EqualsVerifier` with the three suppressions, TestBuilder with `public static final` defaults; frontend Vitest + Spectator per the fe rules (`useValue`/`vi.fn()`, real `signal(...)`, mocks from `aXMock()` factories, barrel imports). |
| V. Language & Tooling Currency | Pass | Java 25, no `var`, blank line before `return`, method order; Angular 22 signals, `inject()`, `#fields`, zoneless. |
| Architecture patterns | Pass | Container–Presentation components; state in signal stores inside `libs/feedback/api` (no NgRx); barrel imports; module boundaries via `ModularityTests` + `@NamedInterface`; cross-module wiring for the notification is event-only (`@ApplicationModuleListener`), never a direct call. |
| Human-in-the-loop (trading) | N/A | No trading involvement. |

No violations → Complexity Tracking stays empty.

## Project Structure

### Documentation (this feature)

```text
specs/017-guest-feedback/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── rest-api.md      # guest + admin REST contract
│   └── linear-client.md # Linear GraphQL contract, issue template, idempotency
└── tasks.md             # Phase 2 output (/speckit-tasks — not created by /speckit-plan)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/
├── feedback/                          # NEW module (schema: feedback)
│   ├── FeedbackTicketCreated.java     # module-root event (public API): sender, app, type — never the ticket text
│   ├── FeedbackConfiguration.java
│   ├── FeedbackProperties.java        # jordylab.feedback.* (limits, linear settings)
│   ├── domain/
│   │   ├── FeedbackTicket.java        # aggregate root, canonical entity structure
│   │   ├── FeedbackTicketState.java   # NEW → ACCEPTED | BACKLOG | REJECTED (terminal: ACCEPTED, REJECTED)
│   │   ├── FeedbackApp.java           # GAME_CATALOG (display + Linear App label; grows with per-app access)
│   │   ├── FeedbackType.java          # BUG_REPORT | FEATURE_REQUEST | IMPROVEMENT | IDEA (+ display + Linear Type label)
│   │   └── repository/FeedbackTicketRepository.java   # incl. pessimistic-lock finder + daily-count + own-list queries
│   ├── rest/
│   │   ├── client/LinearClient.java   # GraphQL over module-private RestClient; team/state/label lookups cached
│   │   └── controller/
│   │       ├── FeedbackTicketController.java   # guest: POST/GET /api/feedback/tickets, GET /api/feedback/apps
│   │       ├── FeedbackInboxController.java    # admin: /api/feedback/inbox/**
│   │       ├── FeedbackExceptionHandler.java   # stable error codes (see contracts/rest-api.md)
│   │       └── model/                          # request/response records
│   └── service/
│       ├── FeedbackSubmissionService.java  # validation, app allow-list, daily limit, registers the event
│       ├── FeedbackAppAccessService.java   # apps the caller may use (roles today; grants source later)
│       ├── FeedbackTriageService.java      # inbox list, state transitions, reply/private note
│       └── AcceptTicketService.java        # accept: lock, idempotency, Linear create/adopt, error recording
├── shared/config/SecurityConfig.java        # EDIT: /api/feedback/** matchers (guest vs admin)
├── mobile/service/MobileNotificationListener.java  # EDIT: @ApplicationModuleListener for FeedbackTicketCreated
└── (settings/, gamecatalog/, fna/, mobile/ unchanged apart from the two edits above)

jordylab-be/src/main/resources/
├── db/migration/V20261103001__feedback_create_tables.sql   # NEW
└── application.yaml                                          # EDIT: jordylab.feedback.* block

jordylab-fe/
├── libs/feedback/                            # NEW domain pair (tags scope:feedback,type:api|ui)
│   ├── api/src/lib/
│   │   ├── feedback.models.ts                # wire types + display helpers
│   │   ├── feedback-api.service.ts           # guest endpoints
│   │   ├── inbox-api.service.ts              # admin endpoints
│   │   ├── feedback.store.ts                 # guest: apps, own tickets, submit()
│   │   ├── inbox.store.ts                    # admin: tickets, accept/backlog/reject, updateTexts()
│   │   └── mocks/                            # aFeedbackTicketMock() etc.
│   └── ui/src/lib/
│       ├── feedback.routes.ts                # guestFeedbackRoutes + adminInboxRoutes (self-guarded)
│       ├── feedback-page/                    # guest tab: form + own ticket list (container + view)
│       └── inbox-page/                       # admin tab: triage list, accept dialog, reply/note (container + view)
├── apps/jordylab/src/app/
│   ├── app.routes.ts                         # EDIT: /settings becomes a composed children array (admin+guest)
│   └── navigation.ts                         # EDIT: "Feedback" item per persona (guest → /settings/feedback, admin → /settings/inbox)
├── libs/settings/ui/src/lib/settings.routes.ts  # EDIT: export per-tab routes for host composition; per-tab role guards
└── apps/jordylab-e2e/src/feedback.spec.ts    # NEW journey

deploy/k8s/overlays/prod/secrets.sops.yaml    # EDIT: LINEAR_API_KEY stringData entry (SOPS; value added by the owner)
```

**Structure Decision**: The backend follows the canonical module layout exactly (module root = public API: the `FeedbackTicketCreated` event + properties; no facade — nothing calls into `feedback` directly, matching every existing module). The notification stays event-only: `feedback` publishes, `mobile`'s existing listener reacts — no new module dependency, `ModularityTests` stays green. On the frontend, feedback is its own domain pair because its audience, guards and stores differ from the admin Settings tabs; the host app owns the `/settings` path composition (app-level routes may depend on any domain lib), so `libs/settings` never imports `libs/feedback` and the Nx tag boundaries hold (one new `scope:feedback` tag with the standard self+shared rules, added to the app's allowed scopes).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

None — no violations.

---

## Phase 0 — Research

See [research.md](research.md). All Technical Context unknowns resolved: Linear auth header + GraphQL operations (verified shapes, marked for live verification), accept idempotency design (row lock + adopt-on-retry), the Settings-opening mechanics on both sides of the stack, the daily-limit and notification patterns to mirror, and the secret threading to prod. The draft's own `research.md` (Linear API details, decisions history) is input history; this file is the spec's canonical research.

## Phase 1 — Design & Contracts

- [data-model.md](data-model.md): the `FeedbackTicket` aggregate, its state machine, validation rules, queries and the anonymisation rule.
- [contracts/rest-api.md](contracts/rest-api.md): guest and admin endpoints, request/response shapes, error codes, and the guest/admin view split (what each role may ever see).
- [contracts/linear-client.md](contracts/linear-client.md): GraphQL operations, label/state/team resolution and caching, the exact issue template (guest-marked text, `Source: idea box`, no personal data), and the idempotency search.
- [quickstart.md](quickstart.md): runnable validation — automated suites plus the manual end-to-end path with a real Linear key.

**Constitution Check re-evaluated after Phase 1 design**: still passing. The design adds no violations; the only "swallowed" failure (Ntfy delivery) remains the documented, intentional exception required by FR-017, and every other failure path (Linear down, invalid credential, limit reached, invalid state, app not allowed) fails fast with a stable code the UI can show.

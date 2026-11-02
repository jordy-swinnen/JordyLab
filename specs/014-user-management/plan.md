# Implementation Plan: User Management Control Panel

**Branch**: `014-user-management` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/014-user-management/spec.md` (five clarifications recorded, no open questions)

**Research**: [research.md](research.md) · **Data model**: [data-model.md](data-model.md) · **Contract**:
[contracts/user-management-api.md](contracts/user-management-api.md) · **Run guide**: [quickstart.md](quickstart.md)

## Summary

Settings → Users becomes the admin's control panel for everyone who uses JordyLab. It manages per-app access, the guest
chat limit (a default and per-person overrides), person deletion with session ending, and a list of each person's
sessions. Approve, reject, revoke and password reset keep working.

The approach keeps the existing role model rather than adding a second one:

- **Per-app access is a Keycloak realm role per grantable app** (`app-gamecatalog`). Spring Security matchers and route
  guards already check roles, and approve and revoke already change them. FNA and Settings have no role, so they cannot
  be granted (research R1).
- **Changes reach people within one minute** because the realm's access-token lifetime drops from 30 minutes to 60
  seconds. Backend JWT checks are stateless, so this bound is the only way a removed role or a deleted account stops
  working before the token expires. The change also closes today's 30-minute gap after a Revoke (research R2).
- **Limits and cleanup live in the `settings` schema.** One new table holds the saved default and personal overrides.
  Deletion removes usage, limits and votes, and fires events so `gamecatalog` clears its own data (research R4, R5, R7).
- **Phone offline sessions** are listed and ended through Keycloak's offline-session API. One part of this is still
  unverified and gates a single task (T-V1, research R3).

No new module is created. Cross-module wiring uses two new `shared.event` records, as the mobile and settings modules
already do.

## Technical Context

**Language/Version**: Java 25 with Spring Boot 4.1.1 and Spring Modulith 2.1.1 (backend); TypeScript with Angular 22 and
Nx 23, run with Bun (frontend)

**Primary Dependencies**: Spring Security OAuth2 resource server, Spring Data JPA, Flyway (with
`spring-boot-starter-flyway`), Spring Modulith application events, `RestClient` for the Keycloak Admin REST API; Angular
signals, spartan/ui (brain and helm), keycloak-js

**Storage**: PostgreSQL 16 with pgvector, new table `settings.guest_chat_limit` (one migration). Identity state (users,
realm roles, sessions, offline sessions, consents) stays in Keycloak 26.7.4

**Testing**: JUnit 5, AssertJ (`assertSoftly`), Mockito (no `any()`), Testcontainers (PostgreSQL and a Keycloak 26.7.4
realm fixture), MockMvc, `ModularityTests`; Vitest with `@ngneat/spectator/vitest`; Playwright journeys through
`e2e/run.sh web`

**Target Platform**: Linux server (k3s) for the backend; web browsers and the Android WebView (same build) for the
frontend

**Project Type**: web application: modular-monolith backend and an Nx Angular SPA

**Performance Goals**: a grant, a removal, a limit change or a deletion is effective within 60 seconds for the affected
person (FR-007, SC-001, SC-002). The Users list loads within 2 seconds for up to 20 people (assumption, measured in the
quickstart)

**Constraints**: the Keycloak realm import runs once, so live changes go through `kcadm` and are recorded in the deploy
README (research R2). The backend stays deny-by-default. Cross-module wiring is event-only. Schema ownership: one
new table, in `settings`

**Scale/Scope**: one admin; about 10 to 20 people; three apps (Game Catalog grantable, FNA and Settings not)

## Constitution Check

*GATE: passed before Phase 0; re-checked after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Clean Code | Full names, early returns, small single-purpose services (`AppAccessService`, `GuestChatLimitService`, `UserDeletionService`, `UserSessionService`) | Pass |
| II. Fail Fast | Keycloak failures stop the action and change nothing (FR-023). Deletion reports the failing stage and leaves the person blocked (FR-017) | Pass |
| III. Builder-First | `GuestChatLimit` uses the canonical entity structure with preconditions in `build()`. DTOs are records | Pass |
| IV. Testing Discipline | Test Builder for `GuestChatLimit`. `assertSoftly` in matrix tests. Keycloak integration for every enforcement point. Frontend mocks use `useValue` and `vi.fn()` | Pass |
| V. Currency | Java 25 features and Angular signals with `#` fields and `inject()` (existing `UsersStore` pattern) | Pass |
| Architecture (constitution) | Container–Presentation; signal stores in the `api` lib; barrel imports | Pass |
| Module boundaries (`jordylab-be/AGENTS.md`) | No new module. `settings` depends on `gamecatalog`'s public root constant (allowed: it already depends on `gamecatalog`). `gamecatalog` never depends on `settings`. New events live in `shared.event` | Pass (verify with `ModularityTests`) |
| Schema ownership | One migration, in `settings`, named `V20261102001__…` | Pass |
| Frontend boundaries (`eslint.config.mjs`) | `scope:settings` may depend on `scope:settings` and `scope:shared`. The app shell imports `@jordylab-fe/settings/*` already (`PendingCountBadgeComponent`) | Pass (verify with lint) |
| Secrets | No new secrets. Keycloak service-account scopes are already documented | Pass |

**Deviations**: none from the constitution. One change to the realm (access-token lifetime) is needed for the spec's
timing requirements. It is a documented operational step, explained in research R2 and gated on the owner's sign-off.

**Gate status**: no blocking gate. Task T-V1 (quickstart section 1) must pass before the phone single-session feature
(FR-019 for phone offline sessions) is implemented. If it fails, the spec amendment described below applies.

## Project Structure

### Documentation (this feature)

```text
specs/014-user-management/
├── spec.md
├── plan.md                  # this file
├── research.md              # Phase 0: decisions R1–R10
├── data-model.md            # Phase 1: table, roles, events, sessions
├── quickstart.md            # Phase 1: validation run guide
├── contracts/
│   └── user-management-api.md   # Phase 1: endpoints, errors, access matrix changes
├── checklists/
│   └── requirements.md
└── tasks.md                 # Phase 2 (/speckit-tasks; not created by /speckit-plan)
```

### Source code (repository root)

Backend, `jordylab-be/src/main/java/dev/jordy/jordylab/` (new = added, changed = modified):

```text
shared/
├── config/SecurityConfig.java                     changed: Game Catalog matchers use app-gamecatalog
└── event/
    ├── AppAccessRemoved.java                      new: (userSubject, appKey)
    └── UserDeleted.java                           new: (userSubject)

settings/
├── SettingsConfiguration.java                     changed: guest limit filter uses the resolver
├── service/
│   ├── ManagedApp.java                            new: enum of apps, grantable flag, role, reason
│   ├── AppAccessService.java                      new: grant, remove, default set, validation
│   ├── GuestChatLimitService.java                 new: effective limit resolution, default and personal
│   ├── GuestChatQuotaService.java                 changed: uses GuestChatLimitService
│   ├── UserDeletionService.java                   new: ordered, retry-safe deletion (R4)
│   ├── UserSessionService.java                    new: list and end sessions
│   └── KeycloakUserAdministrationService.java     changed: approve grants defaults; revoke removes roles, limit
├── domain/
│   ├── GuestChatLimit.java                        new: default row (subject null) or personal row
│   ├── GuestChatUsage.java                        unchanged
│   └── repository/
│       ├── GuestChatLimitRepository.java          new
│       └── GuestChatUsageRepository.java          changed: deleteByUserSubject
├── rest/
│   ├── client/KeycloakAdminClient.java            changed: sessions, offline sessions, client uuid, delete user and session
│   └── controller/
│       ├── SettingsUsersController.java           changed: richer list, delete
│       ├── SettingsUserAppsController.java        new: grant and remove app
│       ├── SettingsUserSessionsController.java    new: list, end one, end all
│       ├── SettingsGuestChatLimitController.java  new: default and personal limit
│       ├── GuestChatLimitFilter.java              changed: uses GuestChatLimitService
│       └── model/                                 new request and response records per controller
└── …

gamecatalog/
├── GameCatalogAccess.java                         new: public constant APP_KEY = "gamecatalog"
└── service/
    ├── MarkCleanupListener.java                   changed: also reacts to AppAccessRemoved
    └── libbot/
        ├── ConversationStore.java                 changed: forget(userSubject)
        └── LibBotConversationCleanupListener.java new: UserDeleted → forget

resources/db/migration/V20261102003__settings_guest_chat_limit.sql    new
```

Backend tests, `jordylab-be/src/test/java/dev/jordy/jordylab/`:

```text
settings/
├── RoleMatrixTest.java                            extended: app-gamecatalog, no-app guest, FNA refusals
├── GuestChatLimitIntegrationTest.java             extended: personal, zero, default, admin
├── UserManagementIntegrationTest.java             new: deletion, timing, sessions, retry (Keycloak)
├── domain/GuestChatLimitTest.java                 new (+ GuestChatLimitTestBuilder)
└── service/
    ├── AppAccessServiceTest.java                  new
    ├── GuestChatLimitServiceTest.java             new
    ├── UserDeletionServiceTest.java               new
    ├── UserSessionServiceTest.java                new
    └── KeycloakUserAdministrationServiceTest.java extended
gamecatalog/service/
├── MarkCleanupListenerTest.java                   extended
└── libbot/ConversationStoreTest.java              extended
resources/keycloak/jordylab-test-realm.json        changed: app-gamecatalog role; guest-user gets it; guest-no-apps user; 60 s lifetime
```

Frontend, `jordylab-fe/`:

```text
libs/shared/auth/src/lib/
├── auth.service.ts                                changed: AppRole adds 'app-gamecatalog'
└── role.guard.ts                                  changed: guest without app role → /no-apps

apps/jordylab/src/app/
├── app.routes.ts                                  changed: games guard; new /no-apps route
├── app.ts / app.html                              changed: nav item shown for app-gamecatalog holders
└── no-apps/no-apps.component.ts                   new: "No apps yet" page, like awaiting approval

libs/settings/api/src/lib/
├── settings.models.ts                             changed: AppUser; new AppAccess, ChatLimit, UserSession types
├── settings-users-api.service.ts                  changed: new endpoints
├── users.store.ts                                 changed: grant, remove, delete, end sessions
├── guest-chat-limit.store.ts                      new
├── user-sessions.store.ts                         new (loaded per opened person)
└── mocks/                                         app-user (changed), guest-chat-limit (new), user-session (new)

libs/settings/ui/src/lib/users-page/               changed: per-person apps, limit, sessions, delete; default limit card
libs/ui/helm/ui-alert-dialog-helm/                 new: spartan alert-dialog helm for the delete confirmation
```

Deploy and operations:

```text
deploy/keycloak/realm-prod.json                    changed: app-gamecatalog role; accessTokenLifespan 60
deploy/keycloak/README.md                          changed: dated rows and kcadm commands for the live realm
jordylab-be/compose/keycloak-realm-export.json     changed: same as realm-prod for local dev
```

**Structure Decision**: extend the existing `settings` module (it already owns the Users page, guest limits and the
Keycloak admin client) and the existing `gamecatalog` listeners. No new module, no new frontend lib. The one new frontend
lib piece is the spartan dialog helm, added with the CLI as the constitution requires.

## Work streams

The order of the streams is a dependency order. Stream A must reach production before stream C ships, because roles must exist before the backend checks them.

**A. Identity prerequisites (owner sign-off before prod)**

- A1. Add realm role `app-gamecatalog` in the realm files and on the live realm. Grant it to every current approved
  guest, so no one loses the Game Catalog when the backend changes.
- A2. Set `accessTokenLifespan` to 60 on the live realm and in both realm files. Record both in the deploy README table.
- A3. Test realm fixtures: `app-gamecatalog` role, `guest-user` gets it, new `guest-no-apps` user, lifespan 60.
- A4. Verify T-V1 (offline session delete) and T-V2 (service-account scopes) on Keycloak 26.7.4.

**B. Settings backend**

- B1. Migration `V20261102003__settings_guest_chat_limit.sql` and the `GuestChatLimit` entity with its builder and test.
- B2. `GuestChatLimitService` and the resolver; route `GuestChatQuotaService` and `GuestChatLimitFilter` through it.
  Saved zero must not be coerced to 20.
- B3. `ManagedApp` and `AppAccessService`: grant, remove, default set, validation (grantable, approved, own account).
- B4. `KeycloakAdminClient` additions: sessions, offline sessions, client uuid, delete session, delete user.
- B5. `UserSessionService` (list, end one, end all) and its controller.
- B6. `UserDeletionService` in the order from research R4, with per-stage errors.
- B7. `KeycloakUserAdministrationService`: approve grants the default set; revoke removes roles and the personal limit;
  allow a cleanup retry after a partial revoke (R6).
- B8. Controllers and request and response records; error reasons as in the contract.

**C. Cross-module and security**

- C1. `shared.event.AppAccessRemoved` and `UserDeleted`.
- C2. `GameCatalogAccess.APP_KEY`; `MarkCleanupListener` handles `AppAccessRemoved`; `ConversationStore.forget` and its listener.
- C3. `SecurityConfig`: Game Catalog reads, marks, ROM status and LibBot require `admin` or `app-gamecatalog`. Mobile and
  settings rows unchanged.
- C4. `ModularityTests` after these structural changes.

**D. Frontend**

- D1. `AppRole` and `roleGuard` for the `games` route and the no-app landing page; `/no-apps` route and page.
- D2. Navigation item visibility for the app role. Admins still see everything.
- D3. `settings/api`: models, API service endpoints, `UsersStore` actions, `GuestChatLimitStore`, `UserSessionsStore`, mocks.
- D4. `settings/ui`: per-person apps (FNA disabled with its reason), limit editor, sessions list with end actions, delete
  behind a confirmation dialog, default limit card. Container–presentation split as in the existing Users page.
- D5. Add the spartan alert-dialog helm (`bunx @spartan-ng/cli@latest add alert-dialog`).

**E. Tests and verification**

- E1. Backend unit and integration tests as listed under Project Structure; extended `RoleMatrixTest`.
- E2. Frontend specs: stores, the users view, `roleGuard` (no-app case), nav visibility.
- E3. Playwright journey for grant, remove and the 60-second wait (quickstart section 5).
- E4. Manual checks on the dev stack (quickstart section 3), with real sign-ups and the real scanner.

**F. Roll-out**

Follow quickstart section 4: identity changes, then backend, then frontend, then the checks.

## Dependencies

- **Feedback tickets (FR-016)**: the feedback feature does not exist yet. Its spec must listen to `UserDeleted` and
  anonymise tickets. Until then, deletion leaves no tickets to anonymise, which is correct only because none exist.
  The guest-feedback spec and this feature are both first-release items (drafts), so they should be planned together.
- **Keycloak live changes**: the roll-out needs the owner's sign-off for the lifetime change in production.
- **T-V1**: the phone single-session behaviour depends on it.

## Spec follow-ups

These need a spec update, not just a code decision. They are listed here so they are not lost. None blocks planning.

1. **Edge case, zero apps**: an approved guest whose apps have all been removed sees a "No apps yet" page (contract,
   frontend role contract). Add this to the spec's Edge Cases.
2. **FR-019, phone sessions**: if T-V1 fails, phone offline sessions can only be ended together. The requirement then reads
   "ending phone sessions ends all of them". Amend before implementing B5.
3. **Assumptions**: record that the access-token lifetime is 60 seconds, which is the bound behind FR-007, SC-001 and
   SC-002. Today's 30-minute window after a Revoke is closed by the same change.
4. **Revoke retry** (R6): the existing `USER_NOT_APPROVED` contract changes only for a cleanup retry. This is an
   implementation detail. No spec wording change is needed beyond FR-021 staying true.

## Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Access tokens refresh about every 30 s per open tab | more Keycloak traffic | acceptable for ~20 people; watch the Keycloak logs after roll-out |
| The lifetime change is applied to production before the roles | guests lose the Game Catalog | roll-out order (A before C); quickstart step 6 |
| `kcadm` changes diverge from the realm files (import runs once) | fresh environments differ from production | record every change in the deploy README table with its command (as earlier entries do) |
| Phone single-session behaviour unverified (T-V1) | FR-019 cannot be met as written | fallback and spec amendment (follow-up 2) |
| A failed deletion leaves a blocked account | admin must retry | per-stage error, retry-safe steps, quickstart section 2 |
| Per-person Keycloak calls for the list (roles, sessions, offline sessions) | slower list with more people | measured in quickstart; batch later only if needed |
| Event listener fails after commit | marks or conversations linger | Modulith event publication log retries the listener |

## Complexity Tracking

None. The constitution check has no violations to justify.

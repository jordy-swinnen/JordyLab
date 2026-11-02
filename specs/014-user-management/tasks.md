# Tasks: User Management Control Panel

**Input**: [plan.md](plan.md), [spec.md](spec.md) (5 user stories, 24 functional requirements, 8 success criteria),
[research.md](research.md), [data-model.md](data-model.md), [contracts/user-management-api.md](contracts/user-management-api.md),
[quickstart.md](quickstart.md)

**Tests**: included. The spec's success criteria are stated as "verified by automated tests" (SC-001, SC-003, SC-004,
SC-006), and the constitution's testing discipline applies to every entity and every enforcement point. The quickstart
names the suites each story must pass.

**Organization**: grouped by user story (US1 to US5, priority order from the spec) so each story can be implemented and
tested on its own. Each task names its file path.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different file from the other tasks in its group, and no dependency on an unfinished task
- **[Story]**: US1 to US5, only in user-story phases
- Paths are relative to the repository root. Backend main code is under `jordylab-be/src/main/java/dev/jordy/jordylab/`,
  backend tests under `jordylab-be/src/test/java/dev/jordy/jordylab/`, frontend under `jordylab-fe/`.

**MVP**: Setup, Foundational, US1 and US2 together. Both are P1. US1 alone would let a direct request grant FNA, so US2
must ship with it.

**Deploy order**: the production roll-out (Phase 9) must complete before the backend or frontend changes from this
feature are deployed.

---

## Phase 1: Setup (realm files, gate checks, UI helm)

**Purpose**: the identity changes the rest of the feature depends on, and the Keycloak checks that gate US5.

- [x] T001 Add realm role `app-gamecatalog` (non-composite, with a description) and set `accessTokenLifespan` to 60 in `jordylab-be/compose/keycloak-realm-export.json` (the dev export; lifespan is currently 1800)
- [x] T002 [P] Make the same two changes in `deploy/keycloak/realm-prod.json`
- [x] T003 [P] In `jordylab-be/src/test/resources/keycloak/jordylab-test-realm.json`: add realm role `app-gamecatalog`; set `accessTokenLifespan` to 60; give `guest-user` the `app-gamecatalog` role; add user `guest-no-apps` with the guest role and no app role, using the same password convention as the other test users; enable `offline_access` as an optional scope for `test-public` if T005 needs it
- [x] T004 Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RealmConfigurationTest.java` so both real realm files must contain role `app-gamecatalog` (non-composite, not in the `admin` composite) and `accessTokenLifespan` equal to 60
- [x] T005 **GATE** Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakSessionVerificationIntegrationTest.java` (extends `KeycloakIntegrationTest`, raw Admin REST via `RestClient`, not the production client). It proves **T-V1**: list a person's offline sessions by the `jordylab-mobile` client UUID, delete one with `?isOffline=true`, and see its refresh token fail. It also proves **T-V2**: the backend service account can end a session and delete a throwaway user. Run it and record the T-V1 result in `specs/014-user-management/research.md` (R3). If T-V1 fails, stop and amend FR-019 before any US5 task
- [x] T006 [P] Add the spartan alert-dialog helm from `jordylab-fe` with `bunx @spartan-ng/cli@latest add alert-dialog`; check that the generated `jordylab-fe/libs/ui/helm/ui-alert-dialog-helm` is tagged `scope:shared,type:ui` and ignored by ESLint like the other helm libs

**Checkpoint**: realm files and test realm agree; T-V1 and T-V2 are known. Setup tests pass.

---

## Phase 2: Foundational (blocks every user story)

**Purpose**: the table, the entity, the app registry, the events, the shared exceptions and the frontend models.

- [x] T007 Create `jordylab-be/src/main/resources/db/migration/V20261102001__settings_guest_chat_limit.sql`: `CREATE SCHEMA IF NOT EXISTS settings`, `SET search_path TO settings`, table `guest_chat_limit` per `data-model.md` §1, `CHECK (daily_limit >= 0)`, `UNIQUE (user_subject)`, and a partial unique index allowing at most one row where `user_subject IS NULL`
- [x] T008 Create entity `jordylab-be/src/main/java/dev/jordy/jordylab/settings/domain/GuestChatLimit.java` in the canonical entity structure: UUID id, nullable `userSubject` (null means the saved default), `dailyLimit` int, `updatedBySubject`; preconditions only in `build()`: limit at least 0, subject for updated-by present
- [x] T009 [P] Create test builder `jordylab-be/src/test/java/dev/jordy/jordylab/settings/domain/GuestChatLimitTestBuilder.java` (`@UtilityClass`, fixed `DEFAULT_*` constants, `aDefaultGuestChatLimit()` and `aGuestChatLimit()`)
- [x] T010 Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/domain/GuestChatLimitTest.java`: builds with every field, default row with null subject, negative limit rejected, blank updated-by rejected, `EqualsVerifier` with the three standard suppressions (depends on T008, T009)
- [x] T011 Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/domain/repository/GuestChatLimitRepository.java`: `findByUserSubject`, `findDefault` (rows where `user_subject IS NULL`), `deleteByUserSubject` with `@Modifying` (depends on T008)
- [x] T012 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/shared/event/AppAccessRemoved.java`: record `(String userSubject, String appKey)`, with the same javadoc style as `UserAccessRemoved`
- [x] T013 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/shared/event/UserDeleted.java`: record `(String userSubject)`
- [x] T014 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/GameCatalogAccess.java`: public `@UtilityClass` exposing `APP_KEY = "gamecatalog"`; the only Game Catalog symbol `settings` may read
- [x] T015 [P] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/CannotChangeOwnAccountException.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/AdminAccountProtectedException.java` (runtime exceptions used by every service that changes a person)
- [x] T016 Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/ManagedApp.java`: enum `GAME_CATALOG` (key from `GameCatalogAccess.APP_KEY`, name "Game Catalog", grantable, default grant, role `app-gamecatalog`), `FNA` (key `fna`, not grantable, reason "Holds the admin's private data", no role), `SETTINGS` (key `settings`, never grantable, reason "Administration", no role); lookup by key returns `Optional`; `grantRole()` returns `Optional`; `defaultGrants()` (depends on T014)
- [x] T017 Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/ManagedAppTest.java`: keys are unique; Game Catalog key equals `GameCatalogAccess.APP_KEY`; only Game Catalog is grantable and a default grant; an unknown key returns empty (depends on T016)
- [x] T018 [P] Update `jordylab-fe/libs/settings/api/src/lib/settings.models.ts`: `AppUser` gains `apps: AppAccess[]` and `isAdmin: boolean`; new `AppAccess` interface (`key`, `name`, `granted`, `grantable`, `reason`). Update `jordylab-fe/libs/settings/api/src/lib/mocks/app-user.model.mock.ts` so `aAppUserMock()` returns the new fields

**Checkpoint**: schema, entity, registry, events, exceptions and frontend types exist. Nothing is wired to behaviour yet.

---

## Phase 3: User Story 1 - Choose which apps each person can use (Priority: P1) 🎯 MVP

**Goal**: the admin grants and removes access per app per person. Enforcement is on the server, and removing an app does
not sign the person out of their other apps.

**Independent Test**: grant Game Catalog to a guest who has none; their navigation and direct calls allow it. Remove it;
within the token lifetime they lose it, their session still works, and other apps are unaffected.

### Tests for User Story 1

- [x] T019 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RoleMatrixTest.java`: `guest-user` (holds `app-gamecatalog`) reaches Game Catalog reads, marks, LibBot and the quota; `guest-no-apps` is refused on Game Catalog reads, marks and LibBot; `pending-user` is still denied everywhere; `guest-user` is still refused FNA and settings
- [x] T020 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/AppAccessServiceTest.java`: granting Game Catalog to an approved person adds the role; to a pending or rejected person it refuses with `USER_NOT_APPROVED`; to the admin's own account it refuses with `CANNOT_CHANGE_OWN_ACCOUNT`; removing it publishes `AppAccessRemoved` for the Game Catalog key only; removing a role not held is a no-op
- [x] T021 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationServiceTest.java`: approve grants the default set and skips roles already held; revoke removes `guest` and every app role, then ends sessions and revokes the mobile consent
- [x] T022 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/MarkCleanupListenerTest.java`: `AppAccessRemoved` for the Game Catalog key deletes that person's marks; for any other key it does nothing
- [x] T023 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserAppsControllerTest.java` (`@WebMvcTest` style of `SettingsUsersControllerTest`): `GET /api/settings/apps` lists three apps; `PUT .../apps/gamecatalog` returns 204; `DELETE` returns 204; unknown person gives 404 `USER_NOT_FOUND`; own account gives 400 `CANNOT_CHANGE_OWN_ACCOUNT`
- [x] T024 [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/UserManagementIntegrationTest.java` (extends `KeycloakIntegrationTest`): admin grants Game Catalog; the guest's token, after refresh, carries `app-gamecatalog` and reaches the catalog. Admin removes it; after the 60-second token lifetime the guest is refused on the catalog, and the same session still refreshes (not signed out). Depends on T003
- [x] T025 [P] [US1] Extend `jordylab-fe/libs/shared/auth/src/lib/role.guard.spec.ts`: a guest holding `app-gamecatalog` passes `roleGuard('admin', 'app-gamecatalog')`; a guest without it is sent to `/no-apps`; an admin passes
- [x] T026 [P] [US1] Extend `jordylab-fe/libs/settings/api/src/lib/settings-users-api.service.spec.ts`: `getApps`, `grantApp` (PUT) and `removeApp` (DELETE) call the right URLs
- [x] T027 [P] [US1] Extend `jordylab-fe/libs/settings/api/src/lib/users.store.spec.ts`: grant and remove reload the list on success; `USER_NOT_APPROVED` and `CANNOT_CHANGE_OWN_ACCOUNT` map to their messages
- [x] T028 [P] [US1] Create `jordylab-fe/apps/jordylab/src/app/no-apps/no-apps.component.spec.ts`: shows "No apps yet" and offers sign-out only
- [x] T029 [P] [US1] Extend `jordylab-fe/apps/jordylab/src/app/app.spec.ts`: the Library item is hidden without `app-gamecatalog`, shown with it, and admins see every item

### Implementation for User Story 1

- [x] T030 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/AppAccessService.java`: `grantApp(userId, appKey)` and `removeApp(userId, appKey)`; approved check (`guest` or `admin`); own-account check; role grant and revoke through `KeycloakAdminClient`; `AppAccessRemoved` published inside `TransactionOperations` as `KeycloakUserAdministrationService` does (depends on T016)
- [x] T031 [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserAppsController.java` with `GET /api/settings/apps`, `PUT` and `DELETE /api/settings/users/{id}/apps/{appKey}`; models `rest/controller/model/AppsResponse.java` and `rest/controller/model/AppView.java` (key, name, grantable, defaultGrant, reason); handlers for `USER_NOT_FOUND` and `CANNOT_CHANGE_OWN_ACCOUNT` (depends on T030)
- [x] T032 [US1] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUsersController.java` and `rest/controller/model/UserResponse.java`: each person carries `apps` (every `ManagedApp` with `granted` derived from realm roles, `grantable` and `reason`) and `isAdmin`
- [x] T033 [US1] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationService.java`: approve grants every default-grant app role, skipping roles already held; revoke removes `guest` and every app role before ending sessions (same file as T034 and later tasks, so do not run in parallel with them)
- [x] T034 [US1] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`: Game Catalog `GET games/**`, `platforms`, `places`, `PUT .../mark`, `PUT .../rom-status` and `/api/gamecatalog/libbot/**` require `hasAnyRole("admin", "app-gamecatalog")`. Mobile rows and `/api/settings/**` stay as they are
- [x] T035 [US1] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/MarkCleanupListener.java`: `@ApplicationModuleListener` on `AppAccessRemoved` deletes the person's marks when `appKey` equals `GameCatalogAccess.APP_KEY`
- [x] T036 [P] [US1] Edit `jordylab-fe/libs/shared/auth/src/lib/auth.service.ts`: `AppRole` gains `'app-gamecatalog'`; add a `hasGameCatalogAccess` computed (admin or `app-gamecatalog`); `hasAppRole` keeps its approved meaning
- [x] T037 [US1] Edit `jordylab-fe/libs/shared/auth/src/lib/role.guard.ts`: a guest without an allowed role goes to `/no-apps` when they hold no app role, otherwise to their home route; admins unchanged (depends on T036)
- [x] T038 [P] [US1] Create `jordylab-fe/apps/jordylab/src/app/no-apps/no-apps.component.ts` and its template, mirroring `awaiting-approval`: "No apps yet", sign-out only, no navigation links
- [x] T039 [US1] Edit `jordylab-fe/apps/jordylab/src/app/app.routes.ts`: the `games` route uses `roleGuard('admin', 'app-gamecatalog')`; add a `no-apps` route with `authGuard` only (depends on T037, T038)
- [x] T040 [US1] Edit `jordylab-fe/apps/jordylab/src/app/app.ts` and `app.html`: `NavItem` gains an optional `requiredAppRole`; `visibleGroups` filters on it (admins see everything); the Library item sets it (depends on T036)
- [x] T041 [US1] Edit `jordylab-fe/libs/settings/api/src/lib/settings-users-api.service.ts`: `getApps()`, `grantApp(userId, appKey)` (PUT), `removeApp(userId, appKey)` (DELETE) (depends on T018)
- [x] T042 [US1] Edit `jordylab-fe/libs/settings/api/src/lib/users.store.ts`: `grantApp` and `removeApp` actions that reload on success and map reasons (depends on T041)
- [x] T043 [US1] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/person-access.component.ts` and its spec `person-access.component.spec.ts`: presentation component with a Game Catalog toggle per person; emits grant and remove
- [x] T044 [US1] Edit `jordylab-fe/libs/settings/ui/src/lib/users-page/users-page.component.ts` and `users-page-view.component.ts`: pass person access inputs and wire grant and remove outputs to the store, rendering `PersonAccessComponent` for each approved person

**Checkpoint**: US1 works. Run quickstart section 2 for US1 and the manual grant and remove steps in quickstart section 3 (steps 2 and 3).

---

## Phase 4: User Story 2 - Apps with my private data cannot be granted (Priority: P1)

**Goal**: FNA and Settings are never grantable. The panel explains why, and the server refuses direct requests.

**Independent Test**: open a person's access controls; FNA is disabled with its reason. A direct grant request for FNA is refused and nothing changes.

### Tests for User Story 2

- [x] T045 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/AppAccessServiceTest.java`: granting FNA or Settings is refused with `APP_NOT_GRANTABLE` and changes no role; an unknown key is refused with `APP_NOT_FOUND`
- [x] T046 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserAppsControllerTest.java`: `PUT .../apps/fna` returns 400 `APP_NOT_GRANTABLE`; `PUT .../apps/unknown` returns 404 `APP_NOT_FOUND`
- [x] T047 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/UserManagementIntegrationTest.java`: a direct `PUT` for FNA by the admin is refused and changes nothing; `guest-user` still gets 403 on `/api/fna`
- [x] T048 [P] [US2] Extend `jordylab-fe/libs/settings/ui/src/lib/users-page/person-access.component.spec.ts`: FNA shows disabled with "Holds the admin's private data"; Settings is never shown as grantable; Game Catalog toggle stays enabled

### Implementation for User Story 2

- [x] T049 [US2] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/AppNotGrantableException.java` and `AppNotFoundException.java`; extend `AppAccessService.java` to refuse unknown keys and non-grantable apps on every grant, before any Keycloak call (depends on T030)
- [x] T050 [US2] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserAppsController.java`: handlers mapping `AppNotGrantableException` to 400 `APP_NOT_GRANTABLE` and `AppNotFoundException` to 404 `APP_NOT_FOUND` (depends on T049)
- [x] T051 [US2] Edit `jordylab-fe/libs/settings/ui/src/lib/users-page/person-access.component.ts`: a non-grantable app renders a disabled control with its reason text
- [x] T052 [US2] Edit `jordylab-fe/libs/settings/api/src/lib/users.store.ts`: map `APP_NOT_GRANTABLE` and `APP_NOT_FOUND` to readable messages (same file as T042, so run after it)

**Checkpoint**: US1 and US2 together are the MVP. Run quickstart section 2 for US2, and step 4 of quickstart section 3.

---

## Phase 5: User Story 3 - Set the guest limit and per-person limits (Priority: P1)

**Goal**: the admin edits the default daily guest chat limit, sets or clears a personal limit for a guest, and the admin is never limited.

**Independent Test**: change the default; a guest without an override gets it the same day. Set a higher override for one guest; only that guest gets it. The admin stays unlimited.

### Tests for User Story 3

- [x] T053 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/GuestChatLimitServiceTest.java`: personal limit beats the saved default, which beats the environment value; a personal limit of 0 blocks; clearing the personal limit returns to the default; a saved default replaces the environment value; the admin is never limited; a saved 0 is not coerced to 20
- [x] T054 [P] [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/GuestChatQuotaServiceTest.java`: the quota uses the resolver, so a personal limit shows; the admin is exempt
- [x] T055 [P] [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilterTest.java`: a personal limit of 0 returns 429 with `CHAT_LIMIT_REACHED`; no personal limit uses the default; the admin passes
- [x] T056 [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/GuestChatLimitIntegrationTest.java`: a default changed through the API applies the same day; a personal limit affects only that guest; the admin is unlimited
- [x] T057 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsGuestChatLimitControllerTest.java`: `GET` reports source `ENVIRONMENT` before any save; `PUT` saves and reports `DEFAULT_SAVED`; a negative value gives 400 `INVALID_LIMIT`
- [x] T058 [P] [US3] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserChatLimitControllerTest.java`: `PUT` personal returns 204; `DELETE` returns 204 and is idempotent; own account gives 400; an admin gives 400 `ADMIN_PROTECTED`; unknown person gives 404
- [x] T059 [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationServiceTest.java`: revoke clears the personal limit after the Keycloak steps (same file as T021, so run after it)
- [x] T060 [P] [US3] Create `jordylab-fe/libs/settings/api/src/lib/mocks/guest-chat-limit.model.mock.ts` (factory `aGuestChatLimitMock`) and `jordylab-fe/libs/settings/api/src/lib/settings-guest-chat-limit-api.service.spec.ts`
- [x] T061 [P] [US3] Create `jordylab-fe/libs/settings/api/src/lib/guest-chat-limit.store.spec.ts` and `jordylab-fe/libs/settings/ui/src/lib/users-page/guest-chat-limit-card.component.spec.ts` and `person-chat-limit.component.spec.ts`

### Implementation for User Story 3

- [x] T062 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/GuestChatLimitService.java` with `effectiveFor(subject)` returning record `EffectiveGuestChatLimit(limit, source)`, `saveDefault`, `savePersonal`, `clearPersonal`; whole numbers of at least 0 only; create `settings/service/InvalidLimitException.java` (400 `INVALID_LIMIT`)
- [x] T063 [US3] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/GuestChatQuotaService.java` to take its limit from `GuestChatLimitService.effectiveFor` (depends on T062)
- [x] T064 [US3] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/GuestChatLimitFilter.java` to use `GuestChatLimitService` (keep the 429 body), and `jordylab-be/src/main/java/dev/jordy/jordylab/settings/SettingsConfiguration.java` to pass the service into the filter bean (depends on T062)
- [x] T065 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsGuestChatLimitController.java` (`GET` and `PUT /api/settings/guest-chat-limit`) with models `GuestChatLimitResponse.java` and `SaveGuestChatLimitRequest.java`
- [x] T066 [US3] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserChatLimitController.java` (`PUT` and `DELETE /api/settings/users/{id}/chat-limit`) with model `SavePersonalChatLimitRequest.java`. The plan grouped personal limits with the default controller; they are split out so each controller has one base route
- [x] T067 [US3] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/KeycloakUserAdministrationService.java`: revoke calls `GuestChatLimitService.clearPersonal` after the Keycloak steps, inside a transaction (same file as T033, so run after it)
- [x] T068 [US3] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/model/UserResponse.java` and `SettingsUsersController.java`: each person carries `chatLimit` (effective limit and source), `null` for the admin (depends on T062)
- [x] T069 [P] [US3] Create `jordylab-fe/libs/settings/api/src/lib/settings-guest-chat-limit-api.service.ts`: get and put the default; put and delete a personal limit
- [x] T070 [US3] Create `jordylab-fe/libs/settings/api/src/lib/guest-chat-limit.store.ts`: default limit state, save, personal save and clear, with `INVALID_LIMIT` mapping (depends on T069)
- [x] T071 [P] [US3] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/guest-chat-limit-card.component.ts`: presentation card for the saved default, showing the source, with a whole-number input
- [x] T072 [US3] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/person-chat-limit.component.ts`: shows the effective limit and its source for each guest, with edit and clear actions
- [x] T073 [US3] Wire the card and the per-person limit into `jordylab-fe/libs/settings/ui/src/lib/users-page/users-page.component.ts` and `users-page-view.component.ts` (depends on T070, T071, T072)

**Checkpoint**: US3 works. Run quickstart section 2 for US3, and steps 5 and 6 of quickstart section 3.

---

## Phase 6: User Story 4 - Remove a person completely (Priority: P2)

**Goal**: the admin deletes a person and every session they have, including phone offline sessions. Their usage, personal limit, votes and conversations go too. Failed deletions can be retried.

**Independent Test**: delete a guest signed in on the web and on the phone. Both sessions end, the account is gone from Keycloak, usage and votes are gone, and a failed attempt can be retried.

### Tests for User Story 4

- [x] T074 [P] [US4] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/UserDeletionServiceTest.java`: refuses the admin's own account (`CANNOT_CHANGE_OWN_ACCOUNT`) and any admin (`ADMIN_PROTECTED`) before any change; steps run in the research R4 order (disable, end sessions, revoke consent, local cleanup, delete account); a failure at each stage reports that stage and leaves the person disabled; a retry completes; `UserAccessRemoved` and `UserDeleted` are published
- [x] T075 [P] [US4] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConversationStoreTest.java`: `forget(subject)` removes that person's conversations and keeps others
- [x] T076 [P] [US4] Create `jordylab-be/src/test/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotConversationCleanupListenerTest.java`: `UserDeleted` forgets that person's conversations
- [x] T077 [P] [US4] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUsersControllerTest.java`: `DELETE` returns 204; own account gives 400; admin gives 400 `ADMIN_PROTECTED`; a Keycloak failure gives 503 with `stage`
- [x] T078 [US4] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/UserManagementIntegrationTest.java`: delete a guest signed in on two devices. Both refreshes fail and the account is gone. Usage and personal-limit rows are gone; votes are gone; LibBot conversations are empty. A simulated Keycloak failure at `DELETE_ACCOUNT` leaves the person blocked, reports the stage, and a retry completes
- [x] T079 [P] [US4] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/delete-person-dialog.component.spec.ts`; extend `jordylab-fe/libs/settings/api/src/lib/users.store.spec.ts` for `deletePerson` and stage messages (the store spec is also edited by T027, so run after it)

### Implementation for User Story 4

- [x] T080 [P] [US4] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/settings/domain/repository/GuestChatUsageRepository.java`: add `deleteByUserSubject` with `@Modifying`
- [x] T081 [US4] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClient.java` with `deleteUser(userId)` (`DELETE /users/{id}`) and add its test in `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClientTest.java`
- [x] T082 [US4] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/UserDeletionService.java` with stages `DISABLE`, `END_SESSIONS`, `LOCAL_CLEANUP`, `DELETE_ACCOUNT`; create `UserDeletionFailedException.java` carrying the stage (depends on T080, T081, T015)
- [x] T083 [US4] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/ConversationStore.java`: `forget(userSubject)` removes every conversation of that person
- [x] T084 [P] [US4] Create `jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/service/libbot/LibBotConversationCleanupListener.java`: `@ApplicationModuleListener` on `UserDeleted` calls `ConversationStore.forget`
- [x] T085 [US4] Edit `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUsersController.java`: `DELETE /api/settings/users/{id}` calls `UserDeletionService`; handlers for own and admin refusals and for `UserDeletionFailedException` (503 `KEYCLOAK_UNAVAILABLE` with `stage`) (depends on T082)
- [x] T086 [US4] Edit `jordylab-fe/libs/settings/api/src/lib/users.store.ts`: `deletePerson` action with stage-aware messages (after T052, same file)
- [x] T087 [US4] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/delete-person-dialog.component.ts` on the spartan alert dialog from T006; the confirmation names the person; add a Delete action to each row in `users-page-view.component.ts` (depends on T006, T086)

**Checkpoint**: US4 works. Run quickstart section 2 for US4, and step 8 of quickstart section 3.

---

## Phase 7: User Story 5 - See and end sessions per person (Priority: P3)

**Goal**: the admin sees each person's web and phone sessions and ends one or all of them. Ending one signs out only that device.

**Independent Test**: sign a guest in on two devices. The panel shows two sessions. End one; only that device signs out. End all; the other goes too.

**Gate**: the phone single-session behaviour (T-V1, task T005) must pass first. If it failed, use the fallback in
research R3 and the spec amendment in the plan, and skip the phone single-session parts marked below.

### Tests for User Story 5

- [x] T088 [P] [US5] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/service/UserSessionServiceTest.java`: classifies regular and offline sessions as web or phone; ending a web session deletes it without the offline flag; ending a phone offline session deletes it with the offline flag; ending all logs out and revokes the mobile consent; an unknown session gives `SESSION_NOT_FOUND`; own account is refused
- [x] T089 [P] [US5] Create `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserSessionsControllerTest.java`: list shape; `DELETE` with `kind`; end-all returns 204; own account gives 400
- [x] T090 [US5] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/UserManagementIntegrationTest.java`: two web sessions show as `WEB`; ending one keeps the other working. The phone offline row and ending one phone session depend on T005's T-V1 result (quickstart step 7)
- [x] T091 [P] [US5] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClientTest.java` (same file as T081, so run after it) with stubs for `listUserSessions`, `listOfflineSessions`, cached `findClientUuid`, and `deleteSession` with and without the offline flag
- [x] T092 [P] [US5] Create `jordylab-fe/libs/settings/api/src/lib/mocks/user-session.model.mock.ts` (factory `aUserSessionMock`) and `jordylab-fe/libs/settings/api/src/lib/user-sessions.store.spec.ts`
- [x] T093 [P] [US5] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/person-sessions.component.spec.ts`: lists sessions with kind labels; "end all" needs confirmation

### Implementation for User Story 5

- [x] T094 [US5] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClient.java` with `listUserSessions(userId)`, `listOfflineSessions(userId, clientUuid)`, `findClientUuid(clientId)` (cached, like the realm role cache), `deleteSession(sessionId, offline)`, and record `KeycloakSession`; same file as T081, so run after it
- [x] T095 [US5] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/service/UserSessionService.java` (classification, end one, end all, own-account refusal) and `UserSessionNotFoundException.java` (depends on T094)
- [x] T096 [US5] Create `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/SettingsUserSessionsController.java` (`GET`, `DELETE .../sessions/{sessionId}?kind=`, `POST .../sessions/end-all`) with models `UserSessionsResponse.java` and `UserSessionView.java`
- [x] T097 [US5] Extend `jordylab-be/src/main/java/dev/jordy/jordylab/settings/rest/controller/model/UserResponse.java` and `SettingsUsersController.java`: each person carries `sessionCount` (regular and phone offline), `null` when Keycloak cannot be reached for that person (depends on T095)
- [x] T098 [US5] Edit `jordylab-fe/libs/settings/api/src/lib/settings-users-api.service.ts`: `getSessions`, `endSession(userId, sessionId, kind)`, `endAllSessions` (after T086, same file)
- [x] T099 [US5] Create `jordylab-fe/libs/settings/api/src/lib/user-sessions.store.ts`: loads a person's sessions when their entry opens; end one; end all (depends on T098)
- [x] T100 [US5] Create `jordylab-fe/libs/settings/ui/src/lib/users-page/person-sessions.component.ts`: list with kind labels, end-one, and end-all behind the confirmation dialog pattern from T087
- [x] T101 [US5] Wire the sessions list into `jordylab-fe/libs/settings/ui/src/lib/users-page/users-page-view.component.ts` (after T087)

**Checkpoint**: US5 works. Run quickstart section 2 for US5, and step 7 of quickstart section 3.

---

## Phase 8: Polish and cross-cutting checks

**Purpose**: documentation, the end-to-end journey, module boundaries, lint, and the manual checks.

- [x] T102 [P] Update `jordylab-be/AGENTS.md` (Security / Keycloak section): endpoint gates for `app-gamecatalog`, the settings user routes, and the 60-second token lifetime
- [x] T103 [P] Update `jordylab-fe/AGENTS.md` (Auth via Keycloak): add `app-gamecatalog` to the role list and mention the `no-apps` page
- [x] T104 [P] Add a pointer at the top of `specs/006-settings-module/contracts/access-matrix.md`: Game Catalog rows are superseded by `specs/014-user-management/contracts/user-management-api.md`
- [x] T105 [P] Extend `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts` `PAGES` with the `no-apps` page and the Users panel states
- [ ] T106 Create `jordylab-fe/apps/jordylab-e2e/src/user-management.spec.ts`: admin grants and removes Game Catalog for a signed-up guest (the 60-second wait happens once), opens sessions, deletes a test guest. UI actions only, no SQL (root `AGENTS.md`, Validation Data)
- [x] T107 Run `cd jordylab-be && ./gradlew test --tests "*ModularityTests*"` (`jordylab-be/src/test/java/dev/jordy/jordylab/ModularityTests.java`) and fix any boundary violation (the `gamecatalog` root constant and the new listeners are the likely places)
- [x] T108 Run the settings and gamecatalog suites listed in quickstart section 2 (`specs/014-user-management/quickstart.md`)
- [x] T109 Run `jordylab-fe/tools/lint-changed.sh <file>` on every changed TypeScript file, then `bunx nx affected -t test lint` for `settings-api`, `settings-ui`, `shared-auth` and `jordylab`
- [x] T110 Run `RealmConfigurationTest` (`jordylab-be/src/test/java/dev/jordy/jordylab/settings/RealmConfigurationTest.java`) and `KeycloakAdminClientRealmExportIntegrationTest` (`jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClientRealmExportIntegrationTest.java`) against the dev realm export, confirming the service-account permissions for T-V2 on the dev realm
- [ ] T111 Measure the Users list with 20 people on the dev stack against the 2-second budget (quickstart section 2). Record the result in `specs/014-user-management/research.md` R9. Optimise only if it fails
- [ ] T112 Run the manual checks in quickstart section 3 (steps 1 to 9, `specs/014-user-management/quickstart.md`) on the dev stack with real sign-ups and the real scanner, and record the results in that file

---

## Phase 9: Production roll-out (owner sign-off; before any deploy of this feature)

**Purpose**: the live identity changes. Order matters: roles must exist and be assigned before the backend that checks
them is deployed (plan, work streams A and F).

- [x] T113 Create realm role `app-gamecatalog` on the live `jordylab` realm with kcadm, idempotently (command in `deploy/keycloak/README.md`, run inside the Keycloak pod, no secrets printed)
- [x] T114 Assign `app-gamecatalog` to every approved guest on the live realm (list the `guest` role members with kcadm; add the role to each). Confirm with `kcadm.sh get roles/app-gamecatalog/users`. Commands in `deploy/keycloak/README.md`
- [x] T115 Set `accessTokenLifespan` to 60 on the live realm (`kcadm.sh update realms/jordylab -s accessTokenLifespan=60`), only after the owner's sign-off. Command recorded in `deploy/keycloak/README.md`
- [x] T116 Record T113 to T115 in the changes table of `deploy/keycloak/README.md`, dated, with the commands used. Mirror in the realm files already changed in Phase 1
- [x] T117 Check the live realm against quickstart section 4 (`specs/014-user-management/quickstart.md`) before any backend deploy
- [ ] T118 Deploy the backend (`jordylab-be/src/main/resources/db/migration/V20261102001__settings_guest_chat_limit.sql`, `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`, settings and gamecatalog changes). Only after T117
- [ ] T119 Deploy the frontend (`jordylab-fe/apps/jordylab/src/app/app.routes.ts`, no-apps route, guards, navigation, Users panel). Only after T118
- [ ] T120 Run the post-deploy checks from quickstart section 4, step 6 (`specs/014-user-management/quickstart.md`): a current guest still sees the catalog; a pending user sees "awaiting approval"; a guest is refused FNA; the panel loads; one grant round trip works

---

## Dependencies and execution order

### Phase dependencies

- **Setup (Phase 1)**: none. T005 gates US5.
- **Foundational (Phase 2)**: needs Setup. Blocks every user story.
- **US1 and US2 (Phases 3 and 4)**: both P1 and both need Foundational. US2 depends on US1's `AppAccessService`.
- **US3 (Phase 5)**: needs Foundational. Independent of US1 and US2 except where the same files are edited (see below).
- **US4 (Phase 6)**: needs Foundational and the `GuestChatUsageRepository` and `KeycloakAdminClient` work it contains.
- **US5 (Phase 7)**: needs Foundational and the T005 gate. Its `KeycloakAdminClient` work follows US4's (same file).
- **Polish (Phase 8)**: needs the stories it checks.
- **Roll-out (Phase 9)**: needs Phase 8 checks. Must complete before any deploy.

### Same-file sequencing (do not run these in parallel)

- `KeycloakUserAdministrationService.java`: T033 (US1), then T067 (US3)
- `KeycloakUserAdministrationServiceTest.java`: T021 (US1), then T059 (US3)
- `users.store.ts`: T042 (US1), T052 (US2), T086 (US4)
- `users.store.spec.ts`: T027 (US1), then T079 (US4)
- `settings-users-api.service.ts`: T041 (US1), then T098 (US5)
- `KeycloakAdminClient.java` and its test: T081 (US4), then T094 (US5), with T091 after T081
- `SettingsUsersController.java`: T032 (US1), T068 (US3), T085 (US4), T097 (US5)
- `users-page-view.component.ts`: T044, T073, T087, T101 in that order
- `AppAccessServiceTest.java`: T020 (US1), then T045 (US2)

### Parallel opportunities

- Setup: T002 and T003 and T006 in parallel once T001 is done.
- Foundational: T009, T012, T013, T014, T015 and T018 in parallel.
- Once Foundational completes, US1, US3 and US4 can start in parallel by three people, with the same-file sequence above.
- Within a story, every `[P]` test task can run together before its implementation.

---

## Parallel example: User Story 1 tests

```text
T019 RoleMatrixTest extension
T020 AppAccessServiceTest (new)
T022 MarkCleanupListenerTest extension
T023 SettingsUserAppsControllerTest (new)
T025 role.guard.spec.ts extension
T026 settings-users-api.service.spec.ts extension
T028 no-apps.component.spec.ts (new)
T029 app.spec.ts extension
```

---

## Implementation strategy

### MVP first (US1 and US2)

1. Phase 1: Setup. Run T005 and record T-V1.
2. Phase 2: Foundational.
3. Phase 3 (US1) and Phase 4 (US2).
4. Stop and validate with quickstart section 2 (US1, US2) and section 3 (steps 2 to 4).

### Incremental delivery

1. MVP above.
2. US3 (limits): the default and personal limits.
3. US4 (deletion), then US5 (sessions). US5 waits for T005.
4. Polish, then the roll-out in Phase 9. Deploy only after T117.

---

## Notes

- Commit after each task or logical group. Every commit carries a `Refs:` trailer such as `Refs: 014/T019` (AGENTS.md
  commit rules, checked against `specs/014-user-management`).
- A task that fails its test is not finished. Re-run the story's suites before moving on.
- If T005 fails on T-V1, skip the phone single-session parts of T090 and T101, and follow research R3's fallback and the
  plan's spec follow-up 2 before implementing them.
- Do not run `./gradlew build` for each task; run the named suites, and the full build once at the end of Phase 8.
- **T006 deviation**: `@spartan-ng/brain` already ships an accessible alert-dialog, so the delete confirmation uses it
  directly instead of generating a new helm lib. No new Nx project was needed.
- **Not executed yet**: T106 (the Playwright journey) and T111 (the 20-person measurement); T113–T120 (production
  roll-out) need the owner's sign-off and are not applied. T112's manual checks were done in a browser on a throwaway
  stack (below), except the phone offline session and real-scanner parts.
- **Browser verification (2026-11-02, throwaway Postgres + Keycloak 26.7.4, `e2e` build)**: signed in as the admin;
  approved a pending user (default Game Catalog grant applied); removed and re-granted the Game Catalog; FNA stayed
  disabled with its reason and a direct grant was refused by the server; saved a default limit of 5 and a personal
  limit of 7, then cleared the personal limit back to the default; sessions loaded ("No active sessions." for a user
  who never signed in); deleted the person through the confirmation dialog (204, gone from the list); the admin's own
  row offered no Revoke/Sessions/Delete and its app control was disabled. As a granted guest, only Library and LibBot
  were in the nav and FNA redirected away; after the admin removed the grant, the guest landed on "No apps yet".
- **Fixes found by that run**: the admin row had offered the forbidden actions (now hidden) and an enabled grant toggle
  (now disabled); the new app, limit and session controllers mapped Keycloak failures to 500 instead of 503 (now 503);
  a realm import did not apply `view-clients` because the service-account user's `clientRoles` list lacked it — fixed in
  both realm files and proven on a fresh import; the live realm still needs the explicit kcadm step in the roll-out.
- **Second pass (2026-11-02), after the browser run**: the admin's own row no longer offers Revoke/Sessions/Delete and
  shows "Admin access" instead of a grant toggle; `/no-apps` is guarded to approved people so a pending sign-up still
  sees "awaiting approval"; a deletion that fails at `DISABLE` says nothing changed instead of claiming the person is
  blocked; and the app, limit and session controllers return `503 KEYCLOAK_UNAVAILABLE` (each covered by a test).
- Stay inside the `settings` schema and the listed files. Any new package or module needs a decision first
  (`jordylab-be/AGENTS.md`: if a new sub-package feels necessary, stop and ask).

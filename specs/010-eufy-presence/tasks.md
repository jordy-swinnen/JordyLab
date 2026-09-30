# Tasks: Eufy Presence

**Input**: Design documents from `/specs/010-eufy-presence/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`, `quickstart.md`

**Organization**: Tasks are grouped by phase. The Phase 2 spike (User Story 0) is a go/no-go gate: if it fails after one
retry, only User Story 6 is built.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Maps to the user story in `spec.md` (US0 = spike, US1–US6)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Tooling, gateway packaging, and k8s manifest scaffolding needed before the spike.

- [ ] T001 Clone `eufy-mega-security` repository to `external/eufy-mega-security/` for spike reference
- [ ] T002 Create `deploy/containers/eufy-gateway/Containerfile` (Node 24 standalone gateway image)
- [ ] T003 Create base `deploy/k8s/base/eufy-gateway.yaml` (Deployment + ClusterIP Service + PVC)
- [ ] T004 [P] Add `eufy-gateway.yaml` reference to `deploy/k8s/base/kustomization.yaml`
- [ ] T005 [P] Add SOPS schema placeholders for `EUFY_USERNAME`, `EUFY_PASSWORD`, `EUFY_COUNTRY`, and
  `EUFY_GATEWAY_API_TOKEN` in `deploy/k8s/overlays/prod/secrets.sops.yaml`
- [ ] T006 [P] Create spike probe script at `tools/eufy-spike-probe.sh` to read and switch HomeBase 2 guard mode

---

## Phase 2: User Story 0 — Prove it can work (go/no-go spike) (Priority: P0) 🎯 GATE

**Goal**: Prove JordyLab can read and switch the HomeBase 2 mode through the gateway running in the production cluster.

**Independent Test**: A 7-day trial with ≥2 switches per day; ≥95% success passes. One retry is allowed if the first run
fails.

- [ ] T007 [US0] Run gateway locally with `EUFY_GATEWAY_PROVIDER=simulated` and verify `GET /health` using
  `tools/eufy-spike-probe.sh`
- [ ] T008 [US0] Create the Eufy guest account and share the home with it; update
  `specs/010-eufy-presence/quickstart.md` if account steps differ (stop-and-report gate)
- [ ] T009 [US0] Run gateway locally with real guest credentials, complete CAPTCHA/email challenge via the gateway Web
  UI, and document the flow in `docs/runbook.md`
- [ ] T010 [US0] Discover the guard-mode read/write HTTP endpoint from `src/server.ts`/`client.py` and document it in
  `specs/010-eufy-presence/research.md`
- [ ] T011 [US0] Deploy gateway to the k3s cluster using `deploy/k8s/base/eufy-gateway.yaml` and verify `GET /health`
  from a backend pod
- [ ] T012 [US0] Run the 7-day spike trial and record firmware version, gateway version, host, and success rate in
  `specs/010-eufy-presence/research.md`
- [ ] T013 [US0] Record go/no-go decision in `specs/010-eufy-presence/research.md`: pass ≥95%, one retry allowed,
  otherwise stop and implement only User Story 6

---

## Phase 3: Foundational (Blocking Prerequisites)

**Purpose**: Core module structure, entities, and adapters that all user stories depend on. **Only start if the spike
passes.**

- [ ] T014 Create Flyway migration
  `jordylab-be/src/main/resources/db/migration/V20260930001__presence_create_tables.sql`
- [ ] T015 [P] Implement `RegisteredDevice` entity + repository + `RegisteredDeviceTestBuilder` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/`
- [ ] T016 [P] Implement `PresenceEvent` entity + repository + `PresenceEventTestBuilder` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/`
- [ ] T017 [P] Implement `ModeChange` entity + repository + `ModeChangeTestBuilder` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/`
- [ ] T018 [P] Implement `PresenceSettings` entity + repository + `PresenceSettingsTestBuilder` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/`
- [ ] T019 Implement `GuardModePort` interface in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/GuardModePort.java`
- [ ] T020 [P] Implement `FakeGuardModeAdapter` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/infrastructure/FakeGuardModeAdapter.java`
- [ ] T021 Implement `ArmKeyVerifier` and `DisarmKeyVerifier` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/domain/signature/`
- [ ] T022 Implement nonce/timestamp replay protection in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/NonceStore.java`
- [ ] T023 Create `PresenceConfiguration.java` in `jordylab-be/src/main/java/dev/jordy/jordylab/presence/`
- [ ] T024 [P] Scaffold `jordylab-fe/libs/presence/api` (`project.json`, `src/index.ts`, `presence-api.service.ts`,
  `presence.store.ts`)
- [ ] T025 [P] Scaffold `jordylab-fe/libs/presence/ui` (`project.json`, `src/index.ts`, `presence.routes.ts`)
- [ ] T026 [P] Add `presence` route lazy-load to `jordylab-fe/apps/jordylab/src/app/app.routes.ts`
- [ ] T027 [P] Create Android plugin package
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/java/dev/jordylab/mobile/presence/`

---

## Phase 4: User Story 4 — Only the admin, only the phone (Priority: P1)

**Goal**: Ensure the feature is invisible to guests and only registered devices can send events.

**Independent Test**: Access-control tests return 403 for non-admin callers and unknown/revoked devices; no Eufy
hardware needed.

- [ ] T028 [US4] Create `PresenceController` with admin-only authorization in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/rest/PresenceController.java`
- [ ] T029 [P] [US4] Implement device registration endpoint `POST /api/presence/devices` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/DeviceRegistrationService.java`
- [ ] T030 [P] [US4] Implement device revocation endpoint `DELETE /api/presence/devices/{id}` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/rest/PresenceController.java`
- [ ] T031 [US4] Expose device revocation in web Settings via existing `libs/settings/ui` components
- [ ] T032 [P] [US4] Add access-matrix contract tests in `specs/010-eufy-presence/contracts/access-matrix.md` and
  backend authorization tests
- [ ] T033 [US4] Hide presence nav/route from non-admin and non-native platform in
  `jordylab-fe/libs/presence/ui/src/lib/presence.routes.ts`

---

## Phase 5: User Story 1 — Leaving home arms Eufy automatically (Priority: P1)

**Goal**: Automatically arm Eufy (Away) after a confirmed leave event and debounce.

**Independent Test**: A manual leave test triggers the arming notification after the configured radius and debounce.

- [ ] T034 [US1] Implement `PresenceStateMachine` with HOME→LEAVING→AWAY transitions in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/PresenceStateMachine.java`
- [ ] T035 [P] [US1] Implement `POST /api/presence/events` handler for `GEOFENCE_EXIT` and `WIFI_LOST` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/PresenceEventHandler.java`
- [ ] T036 [P] [US1] Add debounce scheduling for LEAVING→AWAY transition using Spring scheduler in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/PresenceStateMachine.java`
- [ ] T037 [P] [US1] Implement `EufyGatewayAdapter` calling the discovered guard-mode endpoint in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/infrastructure/EufyGatewayAdapter.java`
- [ ] T038 [US1] Wire failure notification with Retry/Open Eufy actions using
  `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/service/MobileNotificationListener.java` / Ntfy
- [ ] T039 [P] [US1] Add state-machine unit tests for debounce and Wi-Fi-wins rule in
  `jordylab-be/src/test/java/dev/jordy/jordylab/presence/application/PresenceStateMachineTest.java`
- [ ] T040 [P] [US1] Add integration test for arming flow with `FakeGuardModeAdapter` in
  `jordylab-be/src/test/java/dev/jordy/jordylab/presence/application/PresenceArmingIntegrationTest.java`

---

## Phase 6: User Story 2 — Arriving home asks me to disarm (Priority: P1)

**Goal**: Prompt for biometrically-gated disarm on arrival; never disarm without fingerprint.

**Independent Test**: Put Eufy in Away, trigger arrival, tap notification, pass fingerprint, verify mode change.

- [ ] T041 [US2] Extend `PresenceEventHandler` in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/PresenceEventHandler.java` to handle
  `GEOFENCE_ENTER` and `WIFI_CONNECTED` events
- [ ] T042 [P] [US2] Implement local "Disarm Eufy?" notification with tap action in
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/java/dev/jordylab/mobile/presence/GeofencePlugin.java`
- [ ] T043 [P] [US2] Implement `BiometricPrompt` flow and DISARM-key signing in
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/java/dev/jordylab/mobile/presence/BiometricDisarmActivity.java`
- [ ] T044 [US2] Implement `POST /api/presence/disarm` endpoint with signature/timestamp/nonce verification in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/rest/PresenceController.java`
- [ ] T045 [US2] Wire disarm success/failure notifications via Ntfy in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/PresenceService.java`
- [ ] T046 [P] [US2] Add disarm signature verification tests in
  `jordylab-be/src/test/java/dev/jordy/jordylab/presence/domain/signature/DisarmKeyVerifierTest.java`
- [ ] T047 [P] [US2] Add integration test for confirmed disarm flow in
  `jordylab-be/src/test/java/dev/jordy/jordylab/presence/application/PresenceDisarmIntegrationTest.java`

---

## Phase 7: User Story 5 — The Eufy bridge is locked down (Priority: P1)

**Goal**: Ensure the gateway is reachable only by the backend and all mode changes are audit-logged.

**Independent Test**: Network reachability tests confirm the gateway is not public and is restricted to backend pods.

- [ ] T048 [US5] Add NetworkPolicy to `deploy/k8s/base/eufy-gateway.yaml` limiting ingress to backend pods
- [ ] T049 [US5] Verify k3s Network Policy controller is enabled and document server flags in
  `specs/010-eufy-presence/research.md`
- [ ] T050 [US5] Run port-scan test from outside the cluster and record results in `specs/010-eufy-presence/research.md`
- [ ] T051 [US5] Confirm `EUFY_USERNAME`, `EUFY_PASSWORD`, `EUFY_COUNTRY`, and `EUFY_GATEWAY_API_TOKEN` exist only in
  SOPS-encrypted `deploy/k8s/overlays/prod/secrets.sops.yaml`
- [ ] T052 [US5] Implement `AuditLogger` and ensure every `ModeChange` records who/what triggered it and the result in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/application/AuditLogger.java`

---

## Phase 8: User Story 3 — Manual control and status at a glance (Priority: P2)

**Goal**: Provide manual Arm/Home controls and a status screen showing real Eufy mode and recent events.

**Independent Test**: The status screen and Quick Settings tile can be validated without the automatic arm/disarm flows
running.

- [ ] T053 [P] [US3] Implement `GET /api/presence/status` endpoint in
  `jordylab-be/src/main/java/dev/jordy/jordylab/presence/rest/PresenceController.java`
- [ ] T054 [P] [US3] Build status screen component in `jordylab-fe/libs/presence/ui/src/lib/status/`
- [ ] T055 [US3] Implement manual Arm (Away) and Home (biometric) actions in
  `jordylab-fe/libs/presence/api/src/lib/presence.store.ts` and UI
- [ ] T056 [US3] Implement Android Quick Settings `PresenceTileService` in
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/java/dev/jordylab/mobile/presence/PresenceTileService.java`
- [ ] T057 [P] [US3] Add status-screen unit tests in
  `jordylab-fe/libs/presence/ui/src/lib/status/status.component.spec.ts`

---

## Phase 9: User Story 6 — Keep the phone from killing it (Priority: P2)

**Goal**: Walk the user through the OnePlus battery/location settings and detect reverted settings.

**Independent Test**: The checklist UI and settings-state detection work without any Eufy gateway connection.

- [ ] T058 [P] [US6] Build phone-settings checklist component in `jordylab-fe/libs/presence/ui/src/lib/checklist/`
- [ ] T059 [US6] Add runtime detection of location permission, battery optimisation, locked-in-Recents, and OxygenOS
  toggles in
  `jordylab-fe/apps/jordylab-mobile/android/app/src/main/java/dev/jordylab/mobile/presence/GeofencePlugin.java`
- [ ] T060 [US6] Implement reverted-setting warning after OxygenOS update in
  `jordylab-fe/libs/presence/ui/src/lib/checklist/checklist.component.ts`
- [ ] T061 [US6] Add Android deep links to settings screens from
  `jordylab-fe/libs/presence/ui/src/lib/checklist/checklist.component.ts`
- [ ] T062 [P] [US6] Add checklist unit tests in
  `jordylab-fe/libs/presence/ui/src/lib/checklist/checklist.component.spec.ts`

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Final validation, field test, documentation, and repo-wide checks.

- [ ] T063 [P] Run end-to-end validation steps from `specs/010-eufy-presence/quickstart.md`
- [ ] T064 Run two-week field test and record results in `specs/010-eufy-presence/validation-results.md`
- [ ] T065 Disable Eufy's own geofencing in the Eufy app and record the step in `docs/runbook.md`
- [ ] T066 Run Spring Modulith boundary check via `jordylab-be/build.gradle` or the `/modularity-check` skill
- [ ] T067 [P] Update `docs/runbook.md` with gateway re-auth procedure
- [ ] T068 Final lint/test pass: run `bunx nx run-many -t test` from `jordylab-fe/package.json` and `./gradlew test`
  from `jordylab-be/build.gradle`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately.
- **Phase 2 (Spike / US0)**: Depends on Phase 1. **Gates Phase 3–9**: if the spike fails after one retry, only Phase 9 (
  US6) is built.
- **Phase 3 (Foundational)**: Depends on spike go — provides entities, ports, and scaffolds for all user stories.
- **Phase 4–9 (User Stories)**: Depend on Phase 3 completion.
- **Phase 10 (Polish)**: Depends on the desired user stories being complete.

### User Story Dependencies

- **US0 (P0)**: No dependencies; gates the rest.
- **US4 (P1)**: Depends on Phase 3; must be implemented before US1/US2 because endpoints require a registered device and
  admin JWT.
- **US1 (P1)**: Depends on Phase 3 and US4.
- **US2 (P1)**: Depends on Phase 3, US4, and US1 (state machine).
- **US5 (P1)**: Depends on Phase 1/3 deployment artifacts; can run in parallel with US4–US2.
- **US3 (P2)**: Depends on Phase 3, US1, and US2 (status reads state machine/mode changes).
- **US6 (P2)**: Depends on Phase 3; can run in parallel with US3–US5.

### Within Each User Story

- Models/endpoints first, then integration/wiring, then tests.
- Core implementation before UI or tile integration.

### Parallel Opportunities

- All Phase 1 tasks marked [P] can run in parallel.
- All Phase 3 entity tasks (T015–T018) can run in parallel.
- US4, US5, and US6 can largely proceed in parallel once foundational scaffolding is ready.
- US1 and US2 have a sequential dependency (state machine → disarm) but their tests can run in parallel with
  implementation.

---

## Parallel Example: User Story 1

```bash
# Backend state machine + adapter can be built in parallel with event handler:
T034 Implement PresenceStateMachine
T037 Implement EufyGatewayAdapter

# Then wire the event handler and notifications:
T035 Implement POST /api/presence/events handler
T038 Wire failure notifications

# Tests in parallel:
T039 Add state-machine unit tests
T040 Add integration test for arming flow
```

---

## Implementation Strategy

### MVP First (Spike + US1 + US4)

1. Complete Phase 1 (Setup).
2. Complete Phase 2 (Spike / US0) and stop if it fails.
3. Complete Phase 3 (Foundational).
4. Complete Phase 4 (US4 — device registration/security).
5. Complete Phase 5 (US1 — automatic arming on leave).
6. **STOP and VALIDATE**: a real leave event arms Eufy automatically.

### Incremental Delivery

After the MVP, add US2 (disarm on arrival), then US5 (gateway hardening), then US3 (manual/status), then US6 (phone
checklist). Each story adds value without breaking previous stories.

### Parallel Team Strategy

With multiple developers after Phase 3:

- Developer A: US1 + US2 (state machine and arrival/disarm flow)
- Developer B: US4 + US5 (security, access control, gateway deployment hardening)
- Developer C: US3 + US6 (UI, tile, checklist)

---

## Notes

- `[P]` tasks = different files, no dependencies on incomplete tasks.
- `[Story]` label maps each task to its user story for traceability.
- Each user story is independently completable and testable where dependencies are already in place.
- Stop at any checkpoint to validate a story independently.
- Avoid cross-story dependencies that break independence; US4 is intentionally placed before US1/US2 because device
  registration is a hard prerequisite.

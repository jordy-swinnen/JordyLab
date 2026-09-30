# Implementation Plan: Eufy Presence

**Branch**: `010-eufy-presence` | **Date**: 2026-09-29 | **Spec**: [../spec.md](../spec.md)

**Input**: Feature specification from `/specs/010-eufy-presence/spec.md`

## Summary

Build a `presence` Spring Modulith module in `jordylab-be` that receives signed presence events from a registered
OnePlus Android device and switches a Eufy HomeBase 2 between Away and Home modes via the community `eufy-mega-security`
gateway. The feature is admin-only, mobile-only, and starts with a 7-day go/no-go spike that proves the gateway can
reliably switch the real HomeBase 2 mode from the production k3s cluster. If the spike fails (with one allowed retry),
only the phone-settings checklist is delivered.

## Technical Context

**Language/Version**: Java 25 / Spring Boot 4 on the backend; Node.js 24 for the external gateway container;
TypeScript/Angular 21 + Capacitor 8 on the frontend; Kotlin for the Android plugin.

**Primary Dependencies**:

- Backend: Spring Modulith, Spring Security OAuth2 resource server, Spring RestClient, Flyway, PostgreSQL, Micrometer,
  JUnit 5 + AssertJ + Mockito + WireMock + Testcontainers.
- Gateway: `eufy-mega-security` standalone gateway (MIT, Node.js 24), deployed from source in a project Containerfile.
- Frontend: Angular signals, custom signal stores (`/angular-signal-store`), spartan/ui, `@ngneat/spectator/vitest`.
- Android: Capacitor 8, `GeofencingClient`, `WifiManager`/`ConnectivityManager` NetworkCallback, `WorkManager`, Android
  Keystore (ECDSA P-256), `BiometricPrompt`, `TileService`.

**Storage**: PostgreSQL `presence` schema (Flyway) for RegisteredDevice, PresenceEvent, ModeChange, PresenceSettings,
and audit/nonces.

**Testing**: Backend unit + integration tests with Testcontainers/WireMock; frontend Vitest/Spectator; Android
instrumented tests where feasible; field test against SC-001/SC-002.

**Target Platform**: Production OVH k3s cluster (VPS-2) for backend and gateway; Android 14+ on OnePlus 12; no iOS, no
web UI except device revocation in Settings.

**Project Type**: Mobile + backend API + internal cluster service.

**Performance Goals**: Mode change visible in the Eufy app within 30 seconds; failure notifications within 1 minute; arm
debounce default 3 minutes; geofence radius default 535 m.

**Constraints**:

- Gateway is cluster-internal only: ClusterIP Service, no HTTPRoute, NetworkPolicy limiting ingress to backend pods.
- Eufy credentials live in SOPS-encrypted secrets only; gateway session persisted on a PVC.
- Arm credential can only arm; disarm credential requires biometric auth per use.
- Location stored as discrete enter/exit events only — no continuous tracking.

**Scale/Scope**: Single admin user, 1–2 registered phones. Not built for multi-resident or multi-home.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle                                  | Plan Compliance                                                                                                                  |
|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------|
| I. Clean Code / full names / early returns | Modulith module with small, named classes; state machine extracted into explicit transitions.                                    |
| II. Fail fast, no silent failures          | Every mode change produces a notification; gateway failures throw and are recorded in ModeChange result; audit log retained.     |
| III. Immutable / builder-first             | Entities use Lombok `@Builder`/`@Record` where possible; value objects for signed events.                                        |
| IV. Testing discipline                     | TestBuilder fixtures for entities; explicit Mockito values / ArgumentCaptor assigned and asserted; Vitest/Spectator for Angular. |
| V. Current language/features               | Java 25, `inject(Service)`, Angular signals, JS `#field`.                                                                        |
| Angular signal stores                      | `libs/presence/api` holds the signal store; container components inject it; no NgRx.                                             |

No violations. The gateway runs as an external dependency container in the existing cluster, not a new microservice,
respecting monolith-first.

## Project Structure

### Documentation (this feature)

```text
specs/010-eufy-presence/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
jordylab-be/
└── src/main/java/dev/jordy/jordylab/presence/
    ├── PresenceConfiguration.java
    ├── application/
    │   ├── PresenceService.java
    │   ├── PresenceStateMachine.java
    │   ├── PresenceEventHandler.java
    │   ├── DeviceRegistrationService.java
    │   └── AuditLogger.java
    ├── domain/
    │   ├── RegisteredDevice.java
    │   ├── PresenceEvent.java
    │   ├── ModeChange.java
    │   ├── PresenceSettings.java
    │   ├── GuardModePort.java
    │   └── signature/
    │       ├── ArmKeyVerifier.java
    │       └── DisarmKeyVerifier.java
    ├── infrastructure/
    │   ├── EufyGatewayAdapter.java
    │   ├── FakeGuardModeAdapter.java
    │   └── persistence/
    │       └── ...
    └── rest/
        └── PresenceController.java

deploy/
└── containers/eufy-gateway/
    └── Containerfile
└── k8s/base/eufy-gateway.yaml       # Deployment + ClusterIP + PVC + NetworkPolicy

jordylab-fe/
├── apps/jordylab-mobile/android/
│   └── app/src/main/java/dev/jordylab/mobile/presence/
│       ├── GeofencePlugin.java
│       ├── GeofenceBroadcastReceiver.java
│       ├── PresenceWorker.java
│       ├── KeystoreHelper.java
│       ├── BiometricDisarmActivity.java
│       └── PresenceTileService.java
└── libs/presence/
    ├── api/                         # signal store + HTTP service
    └── ui/                          # routes, status, settings, checklist
```

**Structure Decision**: One new Modulith module (`presence`) in the existing monolith; one new frontend domain lib
pair (`libs/presence/{api,ui}`); one custom Capacitor plugin embedded in `apps/jordylab-mobile/android`; one gateway
Containerfile + k8s manifest in `deploy/`. No new microservice; the gateway is a community dependency deployed as an
internal cluster workload.

## Complexity Tracking

No violations. The gateway is not extracted into a JordyLab-owned microservice — it is an external community component
deployed as an internal, dependency-isolated container justified by the absence of an official Eufy API and the P0
spike.

## Build Order

0. **Spike (go/no-go)** — create guest account, run gateway locally (Podman), then as a k3s Deployment in the cluster,
   discover the guard-mode HTTP endpoint, run the 7-day trial, record results. Stop if the retry round also fails.
1. **Backend `presence` module** — entities, Flyway migration, state machine, signature verification, GuardModePort +
   adapters, REST endpoints, audit log, counters.
2. **Gateway deployment** — Containerfile, k8s Deployment + ClusterIP + PVC + NetworkPolicy, SOPS secret wiring.
3. **Android plugin** — geofence registration/re-registration, Wi-Fi callback, WorkManager, Keystore keys, biometric
   disarm, Quick Settings tile.
4. **Angular presence libs** — status, device registration, settings (location/radius/debounce/SSID/mode mapping), phone
   checklist.
5. **Hardening and two-week field test** against the success criteria; only then disable Eufy's own geofencing.

## Stop-and-Report Gates

- Before creating the Eufy guest account or sharing devices.
- Before storing any Eufy credential in the repo or secrets system.
- Before disabling Eufy's own geofencing.
- After the spike, for the go/no-go decision.

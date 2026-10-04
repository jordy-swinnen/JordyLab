# Feature Specification: Eufy Presence (reliable arm/disarm from the JordyLab app)

**Feature Branch**: `010-eufy-presence`

**Created**: 2026-09-29

**Status**: Draft

**Depends On**: 007 (Android app: biometrics, notifications, sideloaded APK), 008 (production cluster, SOPS secrets)

**Input**: User description: "Use feature number 009 (short name: eufy-presence). Make my Eufy home security (HomeBase 2
with cameras) arm and disarm reliably based on whether I'm home, using the JordyLab Android app (spec 007). Admin only (
I live alone), mobile only — no web UI except revoking a lost phone in Settings. WHY: Eufy's built-in geofencing fails
for me — it doesn't arm when I leave, doesn't disarm when I return, and is late or random. My phone is a OnePlus, which
is known to kill background apps aggressively. GO/NO-GO FIRST: Eufy has no official API. Before any feature work, prove
JordyLab can read and switch my HomeBase 2's mode through the community eufy-mega-security gateway, using a separate
Eufy guest account, running where it will run in production. Pass = ≥95% successful switches over a 7-day trial. If it
fails, stop and only deliver the phone-settings checklist. BEHAVIOUR: leaving home (OS-managed geofence, confirmed by
being off my home Wi-Fi, after a short debounce) arms Eufy (Away) automatically, even if the app isn't running.
Arriving (geofence or home Wi-Fi, whichever first) shows a notification 'Disarm Eufy?'; only a tap plus my fingerprint
on my registered phone disarms. Every mode change — success or failure — notifies me; failures offer Retry and Open
Eufy. Manual Arm/Home via the app and an Android Quick Settings tile (Home needs fingerprint). The app shows the real
current Eufy mode and recent events. SECURITY: background presence/arm uses a device-bound credential that can only arm;
disarm needs a separate fingerprint-protected device key the server verifies. Phones are registered once and revocable.
The Eufy gateway is internal-only, has its own secret, uses a dedicated Eufy guest account (never my primary account),
and every command is audit-logged. Location is only stored as enter/exit events, never tracks. PHONE RELIABILITY: an
in-app checklist for location 'allow all the time', notifications, battery 'not optimized', locking the app in Recents
and the OxygenOS optimisation toggles, warning me when an update resets them; the same advice applies to the Eufy app.
Out of scope: other residents, camera streams/events in JordyLab, other Eufy devices, automations beyond arm/disarm,
iOS."
(full description in `specs/_drafts/eufy-presence/speckit-prompts.md` §1)

---

## Overview

Eufy's own geofencing doesn't reliably arm the HomeBase 2 when Jordy leaves or disarm it when he returns. This feature
lets the JordyLab Android app (admin only) detect leaving and arriving, and has JordyLab **arm automatically on leaving
** and **disarm only after a notification tap + fingerprint on arrival**. Eufy has no official API, so the feature
starts with a **go/no-go spike** proving that JordyLab can switch the real HomeBase 2 mode through the community
gateway.

---

## User Scenarios & Testing

### User Story 0: Prove it can work (go/no-go spike) (Priority: P0, blocks everything else)

As Jordy, I want proof that JordyLab can change my HomeBase 2's mode before any feature work starts, so that I don't
build on something that can't work.

**Why this priority**: If the gateway cannot switch the HomeBase 2 reliably, the rest of the feature is valueless. This
story gates every other story.

**Independent Test**: The spike can be run and evaluated on its own — a working gateway trial is a standalone
deliverable, and failure stops all other work.

**Acceptance Scenarios**:

1. **Given** a separate Eufy guest account shared with my home, **When** the gateway runs in the production cluster, *
   *Then** it can read the HomeBase 2's current mode and switch Away → Home → Away, with each change visible in the Eufy
   app within 30 seconds.
2. **Given** a first 7-day trial with at least 2 switches per day, **When** it ends below 95% success, **Then** one
   retry round with an adjusted gateway setup (e.g. different host or account) is allowed; if the retry also ends below
   95%, this feature stops at Phase 0 and only the phone-settings checklist (Story 6) is delivered.
3. **Given** the spike result, **When** it's recorded, **Then** research notes state the firmware version, the gateway
   version, where the gateway ran, and the success rate.

---

### User Story 1: Leaving home arms Eufy automatically (Priority: P1)

**Why this priority**: This is the core automation promise — replacing the broken Eufy geofencing with a reliable "left
home → armed" flow.

**Independent Test**: A manual test walk/drive can verify the arming notification fires after leaving the configured
zone and debounce.

**Acceptance Scenarios**:

1. **Given** I'm home and the system is in "home" mode, **When** I leave the home zone (default radius 535 m) and stay
   out for the debounce time (default 3 minutes), **Then** JordyLab switches Eufy to Away, and my phone shows "Eufy
   armed (Away)".
2. **Given** I step out briefly (letterbox) and come back within the debounce time, **When** that happens, **Then**
   nothing is armed.
3. **Given** the JordyLab app was closed or swiped away, **When** I leave, **Then** arming still happens (the OS-managed
   geofence wakes it).
4. **Given** arming fails (gateway or Eufy error), **When** it fails, **Then** I get a notification within 1 minute with
   Retry and "Open Eufy" actions. It never fails silently.

---

### User Story 2: Arriving home asks me to disarm (Priority: P1)

**Why this priority**: Safe disarm is the security-critical half of the feature — the system must never disarm without
explicit, biometric-gated confirmation.

**Independent Test**: A single arrival can be tested end-to-end without any leaving/arming: put Eufy in Away, trigger
the arrival signal, tap the notification, and verify fingerprint + mode change.

**Acceptance Scenarios**:

1. **Given** Eufy is Away, **When** I enter the home zone or connect to home Wi-Fi (whichever comes first), **Then** my
   phone shows "You're home. Disarm Eufy?"
2. **Given** that notification, **When** I tap it and pass the fingerprint check, **Then** Eufy switches to "home"
   mode (default: Eufy Home; mapping is adjustable in settings) and I see a confirmation.
3. **Given** the fingerprint check fails or I dismiss the notification, **When** that happens, **Then** nothing is
   disarmed.
4. **Given** anyone who has my JordyLab login but not my phone and fingerprint, **When** they try to disarm through the
   API, **Then** they're refused.

---

### User Story 3: Manual control and status at a glance (Priority: P2)

**Why this priority**: Background automation will never be 100% perfect; a manual fallback and clear status give Jordy
control and visibility.

**Independent Test**: The status screen and Quick Settings tile can be validated independently of the automatic
arm/disarm flows.

**Acceptance Scenarios**:

1. **Given** the app or an Android Quick Settings tile, **When** I tap "Arm (Away)", **Then** Eufy arms without a
   fingerprint. **When** I tap "Home", **Then** the fingerprint is required.
2. **Given** the app's Eufy screen, **When** I open it, **Then** I see the current Eufy mode (read from Eufy, not
   assumed), what JordyLab thinks my presence is, and the last 20 events with their outcomes.

---

### User Story 4: Only the admin, only the phone (Priority: P1)

**Why this priority**: The feature controls home security; it must never be reachable by guests or through the web.

**Independent Test**: Access-control tests can verify the API returns 403 for non-admin/unregistered devices without any
Eufy hardware.

**Acceptance Scenarios**:

1. **Given** a guest user (spec 006) or the website, **When** they look for this feature, **Then** it doesn't exist for
   them. There's no route, no nav entry, and the API returns 403.
2. **Given** my phone is registered once from inside the app, **When** events arrive from any other device, **Then**
   they're rejected.
3. **Given** I lose my phone, **When** I revoke the device from the website's Settings, **Then** its credentials stop
   working immediately.

---

### User Story 5: The Eufy bridge is locked down (Priority: P1)

**Why this priority**: The gateway can disarm the home; if it is reachable by anything except the backend, the security
model collapses.

**Independent Test**: Network reachability tests can validate the gateway is not exposed publicly and is restricted to
backend pods.

**Acceptance Scenarios**:

1. **Given** the gateway, **When** anything other than the JordyLab backend tries to reach it, **Then** it isn't
   reachable (not exposed publicly, and cluster-internal access is limited to backend pods).
2. **Given** the Eufy account used by the gateway, **When** it's set up, **Then** it's a separate guest account with
   only this home shared. My primary Eufy account is never stored in JordyLab.
3. **Given** every mode change, **When** it happens, **Then** it's audit-logged with who/what triggered it, and the
   result.

---

### User Story 6: Keep the phone from killing it (Priority: P2)

**Why this priority**: OnePlus/OxygenOS aggressively kills background apps; without the right settings, even OS-managed
geofences may be delayed or lost. This checklist is also useful on its own if the spike fails.

**Independent Test**: The checklist UI and settings-state detection can be tested without any Eufy gateway connection.

**Acceptance Scenarios**:

1. **Given** the first time I enable the feature, **When** the app checks my phone, **Then** it walks me through
   location "Allow all the time", notifications, battery "not optimized", locking the app in Recents, and the OxygenOS
   optimisation toggles, with a green or red state for each.
2. **Given** an OxygenOS update resets a setting, **When** the app next opens, **Then** it warns me.
3. **Given** the checklist, **When** I read it, **Then** it also explains how to apply the same fixes to the Eufy app (
   useful even if this feature stops at the spike).

---

### Edge Cases

- GPS drift at the zone edge causes rapid in/out: prevented by debounce and dwell time.
- Phone reboot or app update: geofences are re-registered automatically.
- No mobile data when leaving: the event is queued and sent when the connection returns. The notification tells me
  arming happened late.
- The gateway session expires or Eufy asks for verification (captcha/email code): arm fails loudly with a "
  re-authenticate gateway" hint, and the runbook explains how.
- Eufy mode changed manually in the Eufy app: JordyLab reads the real mode before acting and doesn't overwrite a manual
  Disarmed with Away unless I have actually left.
- Two conflicting signals (geofence says left, Wi-Fi still connected): the Wi-Fi connection wins, so it doesn't arm.
- Home Wi-Fi drops but no geofence exit has fired: the system does **not** arm on Wi-Fi loss alone; geofence exit beyond
  the configured radius is the only arming trigger.
- Eufy's own geofencing still enabled: the setup guide requires turning it off (to Home/Away/Disarmed modes) to avoid
  both systems fighting.

---

## Requirements

### Functional

- **FR-001**: Feature work MUST NOT start before the go/no-go spike (Story 0) passes. The spike is binding with exactly
  one retry round: if the first 7-day trial is below 95% success, one retry with an adjusted gateway setup is allowed;
  if the retry also fails, the feature stops at Phase 0 and only the phone-settings checklist (Story 6) is delivered.
- **FR-002**: The system MUST detect leaving and arriving using OS-managed geofences plus home Wi-Fi, and MUST work with
  the app not running. Leaving is a geofence exit beyond the configured radius (default 535 m); arriving is a geofence
  enter OR home Wi-Fi connected, whichever comes first. Wi-Fi loss only corroborates a pending exit.
- **FR-003**: Leaving MUST arm Eufy (Away) after a configurable debounce (default 3 minutes). Arriving MUST only prompt.
  Disarming MUST require a fingerprint on the registered phone. The default "home" mode is Eufy Home and "away" is Eufy
  Away; the mapping is adjustable in settings. All presence settings (home location, radius, debounce, home Wi-Fi name,
  mode mapping) MUST be easily editable in the app's settings UI.
- **FR-004**: Presence/arm events MUST use a device-bound credential that can only request arming. Disarm MUST use a
  separate, biometric-gated device key. The server MUST verify both.
- **FR-005**: Every mode change attempt MUST produce a phone notification with the outcome. Failures MUST offer Retry
  and "Open Eufy".
- **FR-006**: The system MUST read the real Eufy mode before and after each change and display it.
- **FR-007**: The feature MUST be available only to the admin, only in the Android app, only on registered devices, and
  devices MUST be revocable.
- **FR-008**: The Eufy gateway MUST be internal-only, authenticated with its own secret, use a dedicated Eufy guest
  account, and log every command.
- **FR-009**: Location MUST be stored only as enter/exit events with timestamps, never as continuous tracks. The home
  coordinates and radius are admin settings.
- **FR-010**: The app MUST provide a phone-settings checklist that detects reverted settings. The primary reference
  phone is OnePlus 12; the OxygenOS version is detected at runtime.
- **FR-011**: The Eufy integration MUST be isolated so the community gateway can be replaced without touching the
  presence logic.

### Key Entities

- **RegisteredDevice**: id, name, arm-key public key, disarm-key public key, registered at, revoked at.
- **PresenceEvent**: device, type (exit/enter/wifi-lost/wifi-connected/manual), occurred at, received at, nonce.
- **ModeChange**: requested mode, trigger (auto-leave/confirmed-arrival/manual), requested at, result, Eufy mode
  before/after, error.
- **PresenceSettings**: home latitude/longitude, radius (default 535 m, easily adjustable), debounce minutes (default 3,
  easily adjustable), home Wi-Fi SSID, the Eufy modes used for "home" (default Home) and "away" (default Away).

---

## Success Criteria

- **SC-001**: Over 2 weeks of normal life, ≥ 95% of departures arm Eufy within 5 minutes of leaving, with no manual
  action.
- **SC-002**: ≥ 95% of arrivals show the disarm prompt before I reach the door (the Wi-Fi or geofence trigger).
- **SC-003**: 0 disarms without a fingerprint on the registered phone (verified by tests and the audit log).
- **SC-004**: 100% of failed mode changes result in a notification within 1 minute.
- **SC-005**: The feature adds no public endpoint beyond the admin-only API, and the gateway is unreachable from outside
  the cluster (port scan + test).

---

## Assumptions

- HomeBase 2 on firmware supported for guard-mode writes by the community gateway (T8010). Code support is merged but
  real-hardware confirmation is the spike's job.
- The Android app from 007 exists (Capacitor 8, biometrics, notifications). No iOS support (007 gives iOS only a
  home-screen web app, which can't do background location).
- The gateway must run in the production cluster. If the spike shows it cannot (e.g. it needs the home LAN), that is
  treated as a failed spike — no home-hosted fallback.
- Eufy's own geofencing is turned off once this feature goes live.
- Before starting, the free mitigation (applying OnePlus battery settings to the Eufy app) is tried; if Eufy's own
  geofencing becomes reliable, this feature may be unnecessary.
- Out of scope: other users/residents, camera streams or events in JordyLab, other Eufy device types, automations beyond
  arm/disarm, iOS.

## Clarifications

### Session 2026-09-29

- **Q**: If the 7-day gateway spike lands below its 95% switch-success bar, should the feature stop entirely at Phase
  0?  
  **A**: Binding + one retry round: one retry with an adjusted gateway setup (e.g. different host or account) is allowed
  before stopping; if the retry also fails, the feature stops at Phase 0 and only the phone-settings checklist (Story 6)
  ships.
- **Q**: What should the default geofence radius and arm-debounce be?  
  **A**: 535 m / 3 minutes defaults — both easily configurable in the UI.
- **Q**: If the spike shows the gateway can't run in the production cluster, where should it run instead?  
  **A**: Drop the feature — the gateway must run in the cluster; a home-LAN requirement counts as a failed spike.
- **Q**: How should the Android app's geofence capability be provided?  
  **A**: A custom free Capacitor plugin (decision carried into `/speckit-plan`; the spec stays implementation-agnostic).
- **Q**: When a disarm is confirmed on arrival, which Eufy mode should be the default "home" mode?  
  **A**: Home (Eufy's in-house state; away = Away); the mapping stays adjustable in settings.
- **Q**: Which OnePlus phone is the primary registered phone?  
  **A**: OnePlus 12; the OxygenOS version is detected at runtime for the checklist paths.
- **Q**: If the home Wi-Fi drops but no geofence exit has fired, should the system arm anyway after a fallback delay?  
  **A**: No — geofence exit is the only arming trigger; Wi-Fi loss only corroborates a pending exit.

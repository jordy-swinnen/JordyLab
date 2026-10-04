# Feature Specification: Eufy Presence (reliable arm/disarm from the JordyLab app)

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-09-27
**Status**: Draft (reference for `/speckit-specify`; see `research.md` and `plan-draft.md`)
**Depends on**: mobile app (Android app: biometrics, notifications), deployment (production cluster, SOPS secrets)

---

## Overview

Eufy's own geofencing doesn't reliably arm the HomeBase 2 when Jordy leaves or disarm it when he returns. This feature
lets the JordyLab Android app (admin only) detect leaving and arriving, and has JordyLab **arm automatically on leaving
** and **disarm only after a notification tap + fingerprint on arrival**. Eufy has no official API, so the feature
starts with a **go/no-go spike** proving that JordyLab can switch the real HomeBase 2 mode through the community
gateway.

---

## User Scenarios & Testing

### User Story: Prove it can work (go/no-go spike) (Priority: Highest, blocks everything else)

As Jordy, I want proof that JordyLab can change my HomeBase 2's mode before any feature work starts, so that I don't
build on something that can't work.

**Acceptance Scenarios**:

1. **Given** a separate Eufy guest account shared with my home, **When** the gateway runs where it would run in
   production, **Then** it can read the HomeBase 2's current mode and switch Away → Home → Away, with each change
   visible in the Eufy app within 30 seconds.
2. **Given** a 7-day trial with at least 2 switches per day, **When** it ends, **Then** at least 95% of switches
   succeeded, or Eufy presence stops here and only the phone-settings checklist (the keep-the-phone-from-killing-it story) is delivered.
3. **Given** the spike result, **When** it's recorded, **Then** research notes state the firmware version, the gateway
   version, where the gateway ran, and the success rate.

### User Story: Leaving home arms Eufy automatically (Priority: High)

**Acceptance Scenarios**:

1. **Given** I'm home and the system is in "home" mode, **When** I leave the home zone and stay out for the debounce
   time, **Then** JordyLab switches Eufy to Away, and my phone shows "Eufy armed (Away)".
2. **Given** I step out briefly (letterbox) and come back within the debounce time, **When** that happens, **Then**
   nothing is armed.
3. **Given** the JordyLab app was closed or swiped away, **When** I leave, **Then** arming still happens (the OS-managed
   geofence wakes it).
4. **Given** arming fails (gateway or Eufy error), **When** it fails, **Then** I get a notification within 1 minute with
   Retry and "Open Eufy" actions. It never fails silently.

### User Story: Arriving home asks me to disarm (Priority: High)

**Acceptance Scenarios**:

1. **Given** Eufy is Away, **When** I enter the home zone or connect to home Wi-Fi (whichever comes first), **Then** my
   phone shows "You're home. Disarm Eufy?"
2. **Given** that notification, **When** I tap it and pass the fingerprint check, **Then** Eufy switches to "home" mode
   and I see a confirmation.
3. **Given** the fingerprint check fails or I dismiss the notification, **When** that happens, **Then** nothing is
   disarmed.
4. **Given** anyone who has my JordyLab login but not my phone and fingerprint, **When** they try to disarm through the
   API, **Then** they're refused.

### User Story: Manual control and status at a glance (Priority: Medium)

**Acceptance Scenarios**:

1. **Given** the app or an Android Quick Settings tile, **When** I tap "Arm (Away)", **Then** Eufy arms without a
   fingerprint. **When** I tap "Home", **Then** the fingerprint is required.
2. **Given** the app's Eufy screen, **When** I open it, **Then** I see the current Eufy mode (read from Eufy, not
   assumed), what JordyLab thinks my presence is, and the last 20 events with their outcomes.

### User Story: Only the admin, only the phone (Priority: High)

**Acceptance Scenarios**:

1. **Given** a guest user (the Settings spec) or the website, **When** they look for this feature, **Then** it doesn't exist for
   them. There's no route, no nav entry, and the API returns 403.
2. **Given** my phone is registered once from inside the app, **When** events arrive from any other device, **Then**
   they're rejected.
3. **Given** I lose my phone, **When** I revoke the device from the website's Settings, **Then** its credentials stop
   working immediately.

### User Story: The Eufy bridge is locked down (Priority: High)

**Acceptance Scenarios**:

1. **Given** the gateway, **When** anything other than the JordyLab backend tries to reach it, **Then** it isn't
   reachable (not exposed publicly, and cluster-internal access is limited).
2. **Given** the Eufy account used by the gateway, **When** it's set up, **Then** it's a separate guest account with
   only this home shared. My primary Eufy account is never stored in JordyLab.
3. **Given** every mode change, **When** it happens, **Then** it's audit-logged with who/what triggered it, and the
   result.

### User Story: Keep the phone from killing it (Priority: Medium)

**Acceptance Scenarios**:

1. **Given** the first time I enable the feature, **When** the app checks my phone, **Then** it walks me through
   location "Allow all the time", notifications, battery "not optimized", locking the app in Recents, and the OxygenOS
   optimisation toggles, with a green or red state for each.
2. **Given** an OxygenOS update resets a setting, **When** the app next opens, **Then** it warns me.
3. **Given** the checklist, **When** I read it, **Then** it also explains how to apply the same fixes to the Eufy app (
   useful even if Eufy presence stops at the spike).

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
- Eufy's own geofencing still enabled: the setup guide requires turning it off (to Home/Away/Disarmed modes) to avoid
  both systems fighting.

---

## Requirements

### Functional

- Feature work MUST NOT start before the go/no-go spike (the go/no-go spike story) passes. The spike's result is recorded.
- The system MUST detect leaving and arriving using OS-managed geofences plus home Wi-Fi, and MUST work with
  the app not running.
- Leaving MUST arm Eufy (Away) after a configurable debounce. Arriving MUST only prompt. Disarming MUST
  require a fingerprint on the registered phone.
- Presence/arm events MUST use a device-bound credential that can only request arming. Disarm MUST use a
  separate, biometric-gated device key. The server MUST verify both.
- Every mode change attempt MUST produce a phone notification with the outcome. Failures MUST offer Retry
  and "Open Eufy".
- The system MUST read the real Eufy mode before and after each change and display it.
- The feature MUST be available only to the admin, only in the Android app, only on registered devices, and
  devices MUST be revocable.
- The Eufy gateway MUST be internal-only, authenticated with its own secret, use a dedicated Eufy guest
  account, and log every command.
- Location MUST be stored only as enter/exit events with timestamps, never as continuous tracks. The home
  coordinates and radius are admin settings.
- The app MUST provide a phone-settings checklist that detects reverted settings.
- The Eufy integration MUST sit behind an interface so the community gateway can be replaced without
  touching the presence logic.

### Key Entities

- **RegisteredDevice**: id, name, arm-key public key, disarm-key public key, registered at, revoked at.
- **PresenceEvent**: device, type (exit/enter/wifi-lost/wifi-connected/manual), occurred at, received at.
- **ModeChange**: requested mode, trigger (auto-leave/confirmed-arrival/manual), requested at, result, Eufy mode
  before/after, error.
- **PresenceSettings**: home latitude/longitude, radius, debounce minutes, home Wi-Fi SSID, the Eufy modes used for "
  home" and "away".

---

## Success Criteria

- Over 2 weeks of normal life, ≥ 95% of departures arm Eufy within 5 minutes of leaving, with no manual
  action.
- ≥ 95% of arrivals show the disarm prompt before I reach the door (the Wi-Fi or geofence trigger).
- 0 disarms without a fingerprint on the registered phone (verified by tests and the audit log).
- 100% of failed mode changes result in a notification within 1 minute.
- The feature adds no public endpoint beyond the admin-only API, and the gateway is unreachable from outside
  the cluster (port scan + test).

---

## Assumptions

- HomeBase 2 on firmware supported for guard-mode writes by the community gateway (T8010). Checked in the spike.
- The Android app from mobile app exists (Capacitor 8, biometrics, notifications). No iOS support (mobile app gives iOS only a
  home-screen web app, which can't do background location).
- Production (deployment) runs the backend and can host the gateway, if the spike shows it works away from the home LAN.
  Otherwise it runs at home (clarify).
- Eufy's own geofencing is turned off once this feature goes live.
- Out of scope: other users/residents, camera streams or events in JordyLab, other Eufy device types, automations beyond
  arm/disarm, iOS.

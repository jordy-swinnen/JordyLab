# Eufy Presence: SpecKit Prompts

Background is in `research.md`. `spec-draft.md` shows the expected spec, and `plan-draft.md` the expected plan shape.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** depends on mobile app (Android app) and deployment (production cluster). The **Phase 0 spike** can run earlier, locally
> with Podman, and decides whether the rest gets built at all.
> **Try first, for free:** apply the OnePlus battery settings (research §1) to the *Eufy* app. If Eufy's own geofencing
> then works reliably, Eufy presence may not be needed.

---

## 1. `/speckit-specify`

```
Short name: eufy-presence.

Make my Eufy home security (HomeBase 2 with cameras) arm and disarm reliably based on whether I'm home, using the JordyLab Android app (the mobile app spec). Admin only (I live alone), mobile only — no web UI except revoking a lost phone in Settings.

WHY: Eufy's built-in geofencing fails for me — it doesn't arm when I leave, doesn't disarm when I return, and is late or random. My phone is a OnePlus, which is known to kill background apps aggressively.

GO/NO-GO FIRST: Eufy has no official API. Before any feature work, prove JordyLab can read and switch my HomeBase 2's mode through the community eufy-mega-security gateway, using a separate Eufy guest account, running where it will run in production. Pass = ≥95% successful switches over a 7-day trial. If it fails, stop and only deliver the phone-settings checklist.

BEHAVIOUR: leaving home (OS-managed geofence, confirmed by being off my home Wi-Fi, after a short debounce) arms Eufy (Away) automatically, even if the app isn't running. Arriving (geofence or home Wi-Fi, whichever first) shows a notification "Disarm Eufy?"; only a tap plus my fingerprint on my registered phone disarms. Every mode change — success or failure — notifies me; failures offer Retry and Open Eufy. Manual Arm/Home via the app and an Android Quick Settings tile (Home needs fingerprint). The app shows the real current Eufy mode and recent events.

SECURITY: background presence/arm uses a device-bound credential that can only arm; disarm needs a separate fingerprint-protected device key the server verifies. Phones are registered once and revocable. The Eufy gateway is internal-only, has its own secret, uses a dedicated Eufy guest account (never my primary account), and every command is audit-logged. Location is only stored as enter/exit events, never tracks.

PHONE RELIABILITY: an in-app checklist for location "allow all the time", notifications, battery "not optimized", locking the app in Recents and the OxygenOS optimisation toggles, warning me when an update resets them; the same advice applies to the Eufy app.

Out of scope: other residents, camera streams/events in JordyLab, other Eufy devices, automations beyond arm/disarm, iOS.
```

---

## 2. `/speckit-clarify`: expected questions and suggested answers

1. Is the spike result binding? → Yes (≥ 95% over 7 days, otherwise stop).
2. Geofence radius / debounce → 200 m / 3 minutes, adjustable in settings.
3. If the gateway needs the home LAN → run it on JordyBox with Podman and connect it to the cluster over WireGuard.
4. Geofence plugin → a custom Kotlin Capacitor plugin (free), not Capawesome Insiders or Transistorsoft.
5. Eufy mode for "home" → `Home` (or `Disarmed` if cameras should stay off when I'm in); "away" → `Away`.
6. Phone model/OxygenOS version → confirm (there's no "OnePlus 12 Pro"; likely OnePlus 12).
7. Fallback arming when Wi-Fi is lost but no geofence exit has fired within 15 minutes → yes/no.

---

## 3. `/speckit-plan`

```
Tech context for Eufy presence (read AGENTS.md, the constitution, and specs/_drafts/eufy-presence/research.md + plan-draft.md first; carry their findings into this feature's research.md; verify every library, gateway endpoint, Android API and version against live docs and the real hardware — report instead of guessing):

- Phase 0 spike first and stop for my go/no-go: eufy-mega-security gateway (github.com/mscodemonkey/eufy-mega-security) run with Podman locally, then as a k3s Deployment; dedicated Eufy guest account; find and document the guard-mode read/write API for HomeBase 2 (T8010) and whether a guest member may change modes; document where it must run (cluster vs home LAN); a small script to switch Away↔Home for the 7-day trial. Do NOT use bropat/eufy-security-client (deprecated, legacy API being shut down).
- Backend: new Modulith module `presence` (use /new-module, /entity, /flyway-migration, /test-builder; /modularity-check at the end). Entities RegisteredDevice, PresenceEvent, ModeChange, PresenceSettings. State machine with debounce. GuardModePort + EufyGatewayAdapter (RestClient, bearer token) + FakeGuardModeAdapter. Endpoints under /api/presence (admin role + registered device): register/revoke device, signed presence events (ARM key, ECDSA P-256), signed disarm (DISARM key, nonce + ≤60s timestamp, replay protection), status. Audit log + Micrometer counters. Notify outcomes via the mobile app notification channel.
- Gateway in the deployment cluster: Deployment + ClusterIP Service only (no HTTPRoute), PVC for the session cache, token + Eufy guest credentials in secrets.sops.yaml, NetworkPolicy allowing only backend pods (verify enforcement on k3s), resource limits.
- Android (apps/jordylab-mobile): custom Capacitor plugin in Kotlin — GeofencingClient (enter/exit/dwell, re-register on BOOT_COMPLETED and app update), Wi-Fi NetworkCallback for the home SSID, WorkManager queue with retry, Android Keystore keys (ARM: no user auth; DISARM: biometric-required per use), local notifications with actions, Quick Settings TileService.
- Angular: mobile-only libs/presence/{api,ui} (signal stores via /angular-signal-store, tests via /angular-test): status screen, device registration, settings (home location, radius, debounce, SSID, Eufy mode mapping), phone-settings checklist with deep links; hidden unless admin + native platform. Web Settings gets only "revoke device".
- Tests: state machine (debounce, conflicting signals, manual override), signature/nonce verification, 403 for guests/unregistered devices, gateway adapter against WireMock, failure → notification.

Stop and report before: creating Eufy accounts or sharing devices, storing any Eufy credential, disabling Eufy's own geofencing, and after the spike (for my go/no-go).
```

---

## 4. Then `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

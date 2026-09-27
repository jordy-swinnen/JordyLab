# 009 Plan Draft: How We'll Build It

A reference for `/speckit-plan`. Every library, gateway endpoint and Android API must be checked against live docs and the real hardware during the spike and plan.

## 1. Architecture

```
OnePlus (JordyLab Android app, 007)
 ├─ Custom Capacitor plugin (Kotlin): GeofencingClient + Wi-Fi NetworkCallback
 │    └─ BroadcastReceiver → WorkManager → POST /api/presence/events  (signed with ARM key, no biometric)
 ├─ Local notification "Disarm Eufy?" → tap → BiometricPrompt → sign with DISARM key
 │    └─ POST /api/presence/disarm  (signed, nonce + timestamp)
 └─ Quick Settings tile: Arm (Away) / Home (Home needs biometric)

k3s (008)                                  internal only, NetworkPolicy: backend → gateway
 backend: module `presence` ──────────────► eufy-gateway (eufy-mega-security, Node) ──► Eufy cloud (Mega) / PPCS ──► HomeBase 2
   state machine, debounce, audit log         bearer token (SOPS), guest Eufy account,
   GuardModePort → EufyGatewayAdapter         session cache on a PVC
```

If the spike shows the gateway needs the home LAN, it runs on JordyBox (Podman) instead and the backend reaches it over WireGuard. The adapter only needs its URL to change.

## 2. Build order
0. **Spike (go/no-go):**
   1. Create the Eufy guest account and share the home with it (admin permission if required).
   2. Run the eufy-mega-security gateway locally with Podman, then as a k3s Deployment.
   3. Find and document the gateway's guard-mode read/write endpoint.
   4. Script Away ↔ Home switches.
   5. Run the 7-day trial and record the results in research.md.
   - **Stop here if it fails.**
1. **Backend module `presence`** (use /new-module, /entity, /flyway-migration, /test-builder):
   - Entities: RegisteredDevice, PresenceEvent, ModeChange, PresenceSettings.
   - State machine + debounce (Spring scheduler or a delayed Modulith event).
   - `GuardModePort` + `EufyGatewayAdapter` (RestClient) + a `FakeGuardModeAdapter` for tests and local dev.
   - Endpoints (admin role + registered device):
     - `POST /api/presence/devices` (register public keys; needs an admin JWT and a fingerprint on the phone)
     - `DELETE /api/presence/devices/{id}` (revoke, also from the web Settings)
     - `POST /api/presence/events` (arm-key signature)
     - `POST /api/presence/disarm` (disarm-key signature, nonce, ≤ 60 s old)
     - `GET /api/presence/status` (real Eufy mode + recent events)
   - Signature verification: Android Keystore keys are EC P-256 (ECDSA). Store nonces for replay protection.
   - Audit logging and Micrometer counters (arm success/fail, latency).
2. **Gateway deployment (008 cluster):** Deployment + ClusterIP Service (no HTTPRoute), a PVC for the session cache, the token and Eufy guest credentials in `secrets.sops.yaml`, a NetworkPolicy allowing only backend pods (verify that k3s's bundled network policy controller enforces it), and resource limits.
3. **Android plugin (Kotlin) in `apps/jordylab-mobile/android`:**
   - Register the geofence (enter/exit/dwell, 200 m) and re-register on `BOOT_COMPLETED` and app update.
   - Wi-Fi `NetworkCallback` for the home SSID.
   - WorkManager queue with retry.
   - Keystore keys: ARM (no user auth) and DISARM (`setUserAuthenticationRequired(true)`, biometric strong, per-use).
   - Local notifications with actions.
   - Quick Settings `TileService`.
4. **Angular (mobile-only lib `libs/presence/{api,ui}`):** status screen, device registration flow, settings (home location picked on a map or "use current location", radius, debounce, SSID, Eufy modes), and the phone-settings checklist with deep links (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`, app details, location). Hidden unless admin + native platform (`Capacitor.isNativePlatform()`).
5. **Hardening and two-week field test** against the success criteria. Then turn off Eufy's own geofencing.

## 3. Shape examples

**State machine (conceptual)**
```
HOME  --exit(geofence) & !homeWifi-->  LEAVING(start debounce T)
LEAVING --enter|wifiConnected before T--> HOME          (nothing armed)
LEAVING --T elapsed--> read Eufy mode → set AWAY → AWAY  (notify result)
AWAY  --enter|wifiConnected--> ARRIVING  (phone raises local "Disarm?" notification)
ARRIVING --valid disarm signature--> set HOME → HOME
ARRIVING --no confirmation--> stays ARRIVING (Eufy stays Away)
```

**Signed presence event (request body)**
```json
{
  "deviceId": "8f3c…",
  "type": "GEOFENCE_EXIT",
  "occurredAt": "2026-10-03T08:14:22Z",
  "nonce": "b64…",
  "signature": "b64(ECDSA-P256(SHA-256(deviceId|type|occurredAt|nonce)))"
}
```

## 4. Things to verify (don't guess)
- The gateway's actual API for guard mode (the endpoint names and the mode values it accepts on HomeBase 2 firmware), and whether a *guest/shared* Eufy member can change guard mode.
- Whether the gateway works outside the home LAN (cloud + PPCS relay) or needs LAN P2P.
- Eufy verification prompts (captcha/email code) on a headless server, and the session lifetime.
- Android: `GeofencingClient` background delivery latency on OxygenOS; `BOOT_COMPLETED` re-registration; SSID access rules (needs `ACCESS_FINE_LOCATION` + background location); `TileService` + BiometricPrompt from a tile.
- k3s: NetworkPolicy enforcement by the bundled controller.
- Capacitor 8: custom local plugin structure inside an Nx app.

## 5. Cost
€0 extra. The gateway runs in the existing cluster (a few hundred MB RAM, which fits VPS-2's headroom; verify) and the plugin is custom. Capawesome Insiders ($99/month) or Transistorsoft ($399+) are rejected alternatives.

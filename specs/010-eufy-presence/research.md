# Eufy Presence: Research & Verified Findings

Date: 2026-09-29. Verified against live docs and the community gateway repository. Hardware confirmation remains the job
of the Phase 0 spike.

## Decisions from Jordy (clarified 2026-09-29)

| Topic                     | Decision                                                                                 |
|---------------------------|------------------------------------------------------------------------------------------|
| Hardware                  | Eufy HomeBase 2 with cameras paired to it                                                |
| Symptoms                  | Eufy geofencing doesn't arm on leave, doesn't disarm on arrival, is late or random       |
| Policy                    | Arm automatically on leave; on arrival, ask to disarm (notification → tap → fingerprint) |
| Phone                     | OnePlus 12; OxygenOS version detected at runtime                                         |
| Scope                     | Mobile-only, admin-only (Jordy lives alone); depends on 007 and 008                      |
| Spike binding             | Binding + one retry round; second sub-95% result stops the feature                       |
| Default radius / debounce | 535 m / 3 minutes; both easily configurable in the UI                                    |
| Default modes             | home → Eufy Home; away → Eufy Away; mapping adjustable                                   |
| Gateway fallback          | None — must run in the production cluster; home-LAN need = failed spike                  |
| Geofence plugin           | Custom free Capacitor plugin (plan-level; Capawesome/Transistorsoft rejected on cost)    |
| Wi-Fi-loss fallback       | No — geofence exit is the only arming trigger                                            |

## Verdict: possible, with conditions

| Question                                                     | Answer                                                                                                                                                                                                                                                                                                                | Confidence                                  |
|--------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------|
| Is there an official Eufy API?                               | **No.** Eufy has no public developer API; all integrations are community reverse-engineering.                                                                                                                                                                                                                         | High                                        |
| Can software switch a HomeBase 2 between Home/Away/Disarmed? | **Yes, in code.** PR #113 in `eufy-mega-security` (merged 2026-09-21) enables confirmed guard-mode reads/writes for HomeBase 2 (T8010 firmware) using the current wrapped command protocol. Older/unidentified HB2 firmware stays read-only. **Real HB2 hardware confirmation is still required** — that's the spike. | Medium: code is new and hardware-unverified |
| Old route: bropat `eufy-security-client`?                    | **Avoid.** Its README says Eufy is shutting down the legacy APIs it is built on.                                                                                                                                                                                                                                      | High                                        |
| Can a phone detect leaving/arriving reliably?                | **Yes, if set up carefully.** OS-managed geofences plus a home Wi-Fi signal, combined with a one-time phone-settings checklist, avoid dependence on the app staying alive.                                                                                                                                            | Medium–High                                 |
| Where does the Eufy bridge run?                              | **In the production k3s cluster.** The standalone gateway mode accepts env-based credentials and a bearer token; session is persisted in a private data dir (PVC). If the spike shows it needs the home LAN, the feature stops.                                                                                       | Medium: cluster routing to be proven        |

## 1. Why Eufy's geofencing likely fails for you

Eufy geofencing relies on the Eufy app getting location updates in the background. OxygenOS kills background apps
aggressively, and battery settings reportedly revert after firmware updates. The free mitigation (applying the same
OnePlus battery settings to the Eufy app) should be tried first; if Eufy's own geofencing becomes reliable, this feature
may be unnecessary. If it doesn't (or reverts), JordyLab adds redundancy and visibility.

## 2. The gateway (`eufy-mega-security`)

Live verification (2026-09-29):

- The project is a Home Assistant app + integration pair, **but also provides a standalone Node.js 24 gateway** for Home
  Assistant Container/Core users.
- Standalone env vars: `EUFY_USERNAME`, `EUFY_PASSWORD`, `EUFY_COUNTRY` (two-letter code), `EUFY_GATEWAY_API_TOKEN` (≥
  32 chars, mandatory for non-loopback), `EUFY_GATEWAY_HOST`.
- The gateway exposes a bearer-authenticated HTTP/SSE API (`GET /health`, `/api/cameras*`, `/api/events`,
  `/api/diagnostics/*`).
- **A guard-mode HTTP endpoint is not listed in the public docs** — the Home Assistant integration drives guard mode
  through `alarm_control_panel.py` and `select.py`. The real endpoint names must be discovered in `src/server.ts` and
  `custom_components/eufy_event_gateway/client.py` during the spike.
- Authentication uses Eufy's current "Mega" service: regional discovery, ECDH `prime256v1` identity exchange, encrypted
  login, per-request signed envelopes. The session is persisted atomically in `mega-session.json` inside a private data
  directory.
- Challenges: CAPTCHA or six-digit email code. The gateway serves a local Web UI challenge page; for headless k3s,
  port-forward the pod to complete the initial challenge, then rely on the persisted session across restarts.
- A simulated provider (`EUFY_GATEWAY_PROVIDER=simulated`) exists for account-free local testing.
- The gateway's API port is LAN-closed by default; non-loopback start requires the bearer token.

## 3. Phone side (Android via the 007 Capacitor app)

- OS-managed geofences (`GeofencingClient`) let Play Services watch the region and wake the app. The JordyLab app
  doesn't need to keep running. Geofences must be re-registered after reboot and app update.
- Home Wi-Fi via `ConnectivityManager` / `WifiManager` `NetworkCallback` is a fast arrival signal and a leaving hint.
  SSID access requires location permission.
- Leaving = geofence exit beyond the configured radius; Wi-Fi loss only corroborates a pending exit. Arriving = geofence
  enter **or** home Wi-Fi connected, whichever first.
- Two Android Keystore keys: ARM key (no user auth, signs presence events); DISARM key (
  `setUserAuthenticationRequired(true)`, strong biometric, per-use, signs disarm requests with nonce + timestamp).
- Quick Settings `TileService` and local notifications with actions provide manual fallbacks.

## 4. Backend side (JordyLab)

- New Spring Modulith module `presence` with entities RegisteredDevice, PresenceEvent, ModeChange, PresenceSettings.
- State machine: `HOME ⇄ LEAVING (debounce) → AWAY`, `AWAY → ARRIVING (awaiting confirmation) → HOME`. Wi-Fi connected
  wins over a pending geofence exit.
- `GuardModePort` + `EufyGatewayAdapter` isolates the Eufy dependency; a fake adapter enables local testing.
- Signature verification (ECDSA P-256), nonce + timestamp freshness (≤ 60 s), replay protection via stored nonces.
- Audit logging + Micrometer counters.
- Notifications use the existing 007 mobile notification channel (Ntfy).

## 5. Risks

1. **Eufy can break it at any time** — unofficial reverse-engineered API. Mitigations: GuardModePort abstraction, health
   checks, failure notifications, manual fallback.
2. **Account risk** — Eufy terms may not allow automated access. A separate guest account limits impact.
3. **Phone OS limits** — mitigated by OS geofences, Wi-Fi signal, settings checklist, and manual tile, but not
   eliminated.
4. **Gateway location** — if it needs the home LAN, the feature stops (cluster-only decision from clarify).
5. **Dependency on 007 and 008** — needs the Android app and production cluster.

## Sources

- `eufy-mega-security` README, `docs/DEVELOPERS_START_HERE.md`, `docs/FILE_MAP.md` (live, 2026-09-29)
- PR #113: *Enable HomeBase 2 guard mode control* (merged 2026-09-21)
- `bropat/eufy-security-client` deprecation notice
- k3s networking docs: embedded kube-router Network Policy Controller
- dontkillmyapp.com: OnePlus
- Capacitor / Android platform documentation (GeofencingClient, WorkManager, Keystore, BiometricPrompt, TileService)

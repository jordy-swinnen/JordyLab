# 010 Eufy Presence (JordyLab geofencing): Research & Feasibility

Date: 2026-09-27. Checked against the live docs and community projects. Nothing here has been tested against Jordy's
hardware yet; that's what the Phase 0 spike is for.

## Decisions so far (from Jordy)

- Hardware: **Eufy HomeBase 2** with cameras paired to it.
- Symptoms of Eufy's own geofencing: **doesn't arm when leaving, doesn't disarm when returning, and is late or random.**
- Policy: **arm automatically on leaving; on arrival, ask to disarm** (notification → tap → fingerprint).
- Phone: **OnePlus** (stated as "OnePlus 12 Pro"; OnePlus sells a *OnePlus 12* and *12R*, not a 12 Pro, so confirm the
  model in clarify; it only matters for settings paths).
- Scope: **mobile only, admin only** (Jordy lives alone). Builds on 007 (Android app) and 008 (production).

## Verdict: possible, with conditions

| Question                                                                          | Answer                                                                                                                                                                                                                                                                                                  | Confidence                     |
|-----------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------|
| Is there an official Eufy API?                                                    | **No.** Eufy has no public developer API; all integrations are community reverse-engineering                                                                                                                                                                                                            | High                           |
| Can software switch a HomeBase 2 between Home/Away/Disarmed?                      | **Yes, in principle.** The community **eufy-mega-security** project merged confirmed guard-mode reads/writes for **HomeBase 2 (T8010 firmware)** on **2026-09-21**. Older/unidentified HB2 firmware stays **read-only**                                                                                 | Medium: brand new (6 days old) |
| The old route (bropat `eufy-security-client`, used by Home Assistant/Homebridge)? | **Avoid.** Its README says *"Eufy is shutting down the legacy APIs this library is built on"* and *"once the legacy API is fully shut down, this library will stop functioning."* Eufy's current app no longer uses that API                                                                            | High                           |
| Can a phone detect leaving/arriving reliably?                                     | **Yes, if set up carefully.** OnePlus/OxygenOS is rated **5/5 (most aggressive)** at killing background apps on dontkillmyapp.com, which very likely explains the Eufy app's failures too. The fix is OS-managed geofences + a Wi-Fi signal + a one-time phone-settings checklist                       | Medium–High                    |
| Where would the Eufy bridge run?                                                  | The eufy-mega-security **gateway** is a standalone Node/TypeScript service with an HTTP API (bearer token ≥ 32 chars), talking to Eufy's cloud ("Mega") and the device protocol (PPCS). Its docs mention **no home-LAN requirement**, so it may run in the k3s cluster. **Must be proven** in the spike | Low–Medium                     |

**So the feature starts with a go/no-go spike (Phase 0).** Nothing else gets built until JordyLab has switched the real
HomeBase 2 from Away to Home and back through the gateway, running where it will run in production.

## 1. Why Eufy's geofencing likely fails for you

- Eufy geofencing relies on the Eufy app getting location updates in the background. OxygenOS kills background apps
  aggressively, and **battery settings reportedly revert after firmware updates**.
- dontkillmyapp.com's OnePlus steps: set the app's battery optimisation to *"not optimized"*, **lock the app in Recents
  **, turn off *Advanced/Deep optimisation* and *Sleep standby optimisation*, and check the auto-launch settings.
- **Try this on the Eufy app first; it costs nothing.** If Eufy geofencing starts working, 010 may be unnecessary. If it
  doesn't (or reverts), 010 still helps, because JordyLab adds redundancy and visibility that Eufy's app doesn't have.
  The same OS limits apply to the JordyLab app, so the 010 design must not depend on the app staying alive.

## 2. Phone side (Android via the 007 Capacitor app)

- **OS-managed geofences** (Android `GeofencingClient`, Google Play Services; the OnePlus has them): Play Services
  watches the region and wakes the app with a broadcast. The JordyLab app **doesn't need to keep running**, which is the
  key difference from continuous-tracking plugins.
    - Caveats: geofences must be re-registered after reboot and app update. Background delivery is typically **delayed
      by a couple of minutes** (Android background location limits). A radius of about 150–250 m and a short "
      dwell/loiter" window avoid flapping.
- **Plugin options:**
    - Transistorsoft Background Geolocation: $399–$999 licence, geofencing add-on extra. Too expensive for a hobby
      project.
    - Capawesome Geofences: part of Capawesome Insiders, $99/month or $990/year.
    - **A small custom Capacitor plugin (Kotlin)** wrapping `GeofencingClient` + a `BroadcastReceiver` + WorkManager to
      POST the event: free, and a good learning exercise. **Suggestion: custom plugin**, decided in plan.
- **Second signal: home Wi-Fi.** Connecting to or losing the home Wi-Fi is a fast, reliable "arrived" signal, and a
  useful "maybe left" hint. Reading the Wi-Fi name (SSID) needs location permission on Android, and background callbacks
  are also subject to OnePlus limits. **Leaving = geofence exit (confirmed by Wi-Fi being gone)**; **arriving = geofence
  enter OR home Wi-Fi connected**, whichever comes first.
- **Always-available manual fallback:** an Android **Quick Settings tile** and/or home-screen widget: "Arm (Away)" / "
  Home".
- **Onboarding checklist in the app:** location "Allow all the time", notifications on, battery "not optimized", app
  locked in Recents, with deep links to the right settings screens where Android allows. Re-check after OxygenOS
  updates, since settings reportedly revert.

## 3. The security design (the most important part)

- Whoever can call "disarm" can switch off your home security. Your policy (auto-arm, confirmed disarm) fits that risk
  well.
- **Two separate permissions:**
    - **Presence/arm (low risk):** the phone sends "left home" or "arrived home" in the background. It must work *
      *without a fingerprint** (the phone may be in your pocket), so it uses a **device-bound, arm-only credential**: a
      key in the Android Keystore that can only sign presence events. It can make JordyLab set Away, never
      Disarmed/Home.
    - **Disarm (high risk):** only after a **notification tap + fingerprint**. The phone signs the disarm request with a
      Keystore key that requires biometric authentication for every use. The backend checks that signature, the admin
      role and freshness (timestamp/nonce), and only then asks the gateway to switch to Home/Disarmed.
- **The gateway is the crown jewel.** It holds the Eufy session and can disarm on its own, so it:
    - runs cluster-internal only (no Service exposed via Traefik);
    - has its own bearer token (SOPS secret from 008);
    - is reachable only from the backend (a NetworkPolicy, verified in plan for k3s);
    - logs every mode change.
- **Dedicated Eufy account:** the gateway docs say to create **a separate Eufy guest account**, share only the needed
  home/devices with it, and **never use the primary account** (sessions interfere). *To verify in the spike:* that a
  shared/guest member of the home is allowed to change guard mode on HomeBase 2.
- **Audit log:** every presence event, mode request, result and confirmation is stored. Location is stored only as
  enter/exit events, never as tracks.

## 4. Backend side (JordyLab)

- A new Modulith module (working name `presence`, confirmed in clarify) with:
    - A **state machine**: `HOME ⇄ LEAVING (debounce) → AWAY`, `AWAY → ARRIVING (awaiting confirmation) → HOME`.
    - **Debounce:** only arm after the "left" signal has held for N minutes (e.g. 3) without an "arrived" signal, to
      avoid arming when you just walk to the letterbox.
    - A **GuardModePort** with a gateway adapter. This isolates the fragile Eufy dependency, and a fake adapter makes
      testing easy.
    - **Visible failure:** if an arm command fails, the phone gets a notification ("Couldn't arm Eufy; tap to retry /
      open Eufy") instead of failing silently.
- **Notifications:**
    - Arrival confirmation is a **local notification raised on the phone itself** when it detects arrival, so no server
      push is needed.
    - Server → phone messages ("armed ✓", "arm failed") use 007's push channel (Ntfy suggestion) or a response to the
      phone's own request.
- **Turn off Eufy's own geofencing** (use Home/Away/Disarmed modes) so the two systems don't fight.

## 5. Risks (worth being honest about)

1. **Eufy can break it at any time.** It's an unofficial, reverse-engineered API. The HomeBase 3 mode-reading bug opened
   2026-09-26 in the same project shows how young it is. Mitigations: the GuardModePort abstraction, a health check that
   verifies the current mode, failure notifications, and a manual fallback (the Eufy app itself).
2. **Account risk:** Eufy's terms may not allow automated or third-party access. Using a separate guest account limits
   the impact if Eufy blocks it.
3. **Phone OS limits** (OnePlus): mitigated by OS-managed geofences, the Wi-Fi signal, the settings checklist and the
   manual tile, but not eliminated.
4. **Gateway location:** if it turns out to need the home LAN (P2P to the HomeBase), it has to run at home (e.g. on
   JordyBox) and connect to the cluster, likely over WireGuard. The spike decides.
5. **Dependency on 007 and 008:** needs the Android app (Capacitor, biometric, local notifications) and the production
   cluster.

## Open questions for `/speckit-clarify`

1. Is the spike's go/no-go result binding? (Suggestion: yes. If the gateway can't switch modes reliably over a 7-day
   trial, 010 stops at Phase 0 and only the Eufy-app phone-settings checklist is delivered.)
2. Geofence radius and debounce: 200 m and 3 minutes to start?
3. Where does the gateway run if the cloud route fails: at home on JordyBox (via WireGuard), or drop the feature?
4. Custom Kotlin geofence plugin (free, suggested) vs Capawesome Insiders ($99/month)?
5. Which Eufy mode means "home": `Home` or `Disarmed`? And "away": `Away`?
6. Phone model/OxygenOS version, for the settings checklist.
7. Should arming also apply when Wi-Fi is lost but the geofence hasn't fired within X minutes (a fallback)?

## Sources

- Eufy integration state:
    - [bropat/eufy-security-client (deprecation notice)](https://github.com/bropat/eufy-security-client)
    - [eufy-security-ws guard mode values](https://github.com/bropat/eufy-security-ws/issues/38)
    - [mscodemonkey/eufy-mega-security (gateway, guest-account advice)](https://github.com/mscodemonkey/eufy-mega-security)
    - [PR #113: HomeBase 2 guard mode read/write, merged 2026-09-21](https://github.com/mscodemonkey/eufy-mega-security/pull/113)
    - [Issue #213: HomeBase 3 mode unavailable, 2026-09-26](https://github.com/mscodemonkey/eufy-mega-security/issues/213)
    - [eufy-mega-client programme](https://github.com/keesmod/eufy-mega-client/issues/7)
    - [Eufy + Home Assistant issues, June 2026](https://quvii.com/blog/eufy-home-assistant-integration-issues-2026-6-4/)
    - [Eufy Community: API requests](https://community.eufy.com/t/api-for-eufy-security/5759489)
- Eufy
  geofencing: [How geofencing works (Eufy)](https://service.eufy.com/article-description/How-Does-Geofencing-Work-for-eufy-Devices) · [Geofencing troubleshooting](https://scos.co.uk/troubleshooting/eufy/eufy-geofencing-not-working/)
- Phone: [dontkillmyapp.com: OnePlus](https://dontkillmyapp.com/oneplus)
- Capacitor geofencing
  plugins: [Capawesome: alternatives to Transistorsoft](https://capawesome.io/blog/alternative-to-transistorsoft-background-geolocation/) · [Transistorsoft licence](https://shop.transistorsoft.com/products/capacitor-background-geolocation-premium-license) · [Cap-go background geolocation](https://github.com/Cap-go/capacitor-background-geolocation)

# Feature Draft: Replace the Capacitor app with a PWA

**Status**: Draft, conditional (starts only after the decision gate in `presence-ha-prototype`)
**Target path**: `specs/_drafts/pwa-replaces-capacitor/spec.md`
**Supersedes**: spec 007 (Mobile app, Capacitor Android sideloaded APK)
**Interacts with**: `presence-ha-prototype`, `auth-without-keycloak`, `continuous-deploy`

> Written without repo access. What the `mobile` module and Capacitor app actually do is taken from `jordylab-current-state.md` and needs checking against the code.

## Problem

The Android app is the same web build wrapped by Capacitor 8 and sideloaded as a signed APK. That costs a whole stack: APK signing, hosting and short-lived download links in the `mobile` module, a `mobile-release-ci` Keycloak client, an Appium + WebdriverIO e2e suite, and the `jordylab-mobile` and `jordylab-mobile-e2e` Nx apps. The WebView also runs on its own origin, which complicates cookie-based sessions. The one thing it provides that an installed PWA cannot is native background geofencing (spec 010).

## Trigger

Start when the presence spike shows the geofence need is met without JordyLab (Home Assistant works), or when a native JordyLab geofence fix has failed. If the native fix is what works, Capacitor stays and this draft is shelved.

## Requirements

- **FR-001**: The `jordylab` web build is installable on Android (web app manifest, icons, a minimal service worker for installability) and passes an installability audit.
- **FR-002**: Every journey the Capacitor app supports today has a PWA equivalent or is deliberately dropped, recorded in a table (journey, Capacitor plugin or API used, PWA replacement). The Android share flow (MRB-06) is covered by a Web Share Target, which only works for an installed PWA.
- **FR-003**: Notifications: decide between keeping the existing ntfy server and its Android app, or adding Web Push (needs VAPID keys and a subscription store). The default is to keep ntfy, since it is already deployed.
- **FR-004**: Remove Capacitor 8, the `jordylab-mobile` and `jordylab-mobile-e2e` apps, the `e2e-android` workflow, APK signing keys and secrets, the APK publish steps and the `mobile-release-ci` client.
- **FR-005**: Reduce or remove the `mobile` module: delete APK release hosting, `POST /api/mobile/releases`, download links and the APK volume. Move ntfy dispatch somewhere appropriate if other modules depend on it, without breaking `ModularityTests`.
- **FR-006**: Existing users get install instructions (uninstall the APK, install from the site). No data migration is needed if the data is server-side.
- **FR-007**: Playwright web journeys (`e2e-web`) include the installable-manifest check and the share-target route.

## Success criteria

- **SC-001**: A fresh Android install from the browser reaches the same screens the APK did, on the target phone.
- **SC-002**: After removal, `jordylab-fe` has no Capacitor dependency and the CI has no Android job.
- **SC-003**: No journey is lost that was not listed as dropped in FR-002.

## Out of scope

iOS, Play Store listing, offline-first behavior, native background geofencing (this is exactly what is given up).

## Open questions

1. Which Capacitor plugins are in use today? The mapping in FR-002 depends on the answer.
2. What does the `mobile` module's notification dispatch serve, and which modules call it?
3. Does ntfy's Android app already handle every push the Capacitor app receives?
4. Timing against `auth-without-keycloak`: same-origin PWA makes cookie sessions straightforward, so this should land before auth is rebuilt.

## Facts to verify in the repo

- Capacitor plugin list in `jordylab-fe/package.json` and native config.
- What MRB-06 (Android share) shares and into which screen.
- Everything that references the `mobile-release-ci` client or the APK volume (`deploy/`, workflows, Keycloak realm JSON).

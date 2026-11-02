# Feature Specification: Replace the Native Android Wrapper with an Installable Web App

**Feature Branch**: `jordyswinnen/jor-38-replace-the-capacitor-app-with-a-pwa`

**Created**: 2026-11-02

**Status**: Draft

**Tracking**: Linear `JOR-38`

**Supersedes**: `007-mobile-app` (sideloaded Android package, release hosting, short-lived download links, native login and biometric unlock)

**Interacts with**: `016-app-redesign-themes` (app icons, manifest colours and phone layout), `JOR-36` (`specs/_drafts/presence-ha-prototype/` — presence automation handled outside the mobile app), `specs/_drafts/auth-without-keycloak/` (same-origin web app enables first-party cookie sessions)

**Input**: User description: "Work on Linear issue JOR-38: Replace the Capacitor app with a PWA. The Android app is the same web build wrapped by Capacitor 8 and sideloaded as a signed APK. That costs a whole stack: APK signing, hosting and short-lived download links in the `mobile` module, a Keycloak client for release CI, an Appium e2e suite, and two Nx apps. The WebView also runs on its own origin, which complicates cookie-based sessions. The one thing it provides that an installed PWA cannot is native background geofencing. Draft: `specs/_drafts/pwa-replaces-capacitor/`"

---

## Context

Today the Android app packages the exact same web interface into a sideloaded native wrapper. Maintaining that wrapper carries a disproportionate ongoing cost across the product:

- A dedicated release-hosting backend area for uploading signed Android packages, verifying signing certificates, storing package files on a persistent volume, issuing short-lived download links, checking minimum versions, and serving domain-verification files.
- Separate identity-provider clients and secrets for native sign-in redirects and automated release publishing.
- Separate native build, signing, and emulator end-to-end test pipelines.
- A separate local origin inside the native web view, which forces request-URL rewriting, custom browser-tab login redirects, and device-specific session recovery instead of standard first-party web sessions.

With background home-presence automation handled outside the JordyLab mobile app, every remaining phone journey can either be served directly by an installed web app on the main site origin or be retired in favour of standard browser capabilities.

### Journey Mapping (Native Wrapper → Installable Web App)

| Journey | Previous Native Approach | Installable Web App Replacement | Outcome |
|---------|--------------------------|---------------------------------|---------|
| **1. Install on Android** (007 US1) | Suppress browser install prompt; show custom package-download dialog; issue short-lived download link; user enables unknown-source installs and sideloads signed package; desktop shows QR code to download page | Browser-native web app installation backed by the web app manifest, icons and service worker; in-app install prompt triggers the browser install flow on Android, keeps the Add-to-Home-Screen guide on iPhone/iPad, and offers a desktop QR code to open the site on a phone; instructions explain how existing users replace the sideloaded app | **Replaced** |
| **2. Sign in and use all role-allowed screens** (007 US2) | Open sign-in in an external custom browser tab, return via verified deep link, and rewrite relative data and image requests from the wrapper's local origin to the production domain | Run directly on the site's own origin in standalone display mode, sharing the standard web sign-in, session persistence, role guards and relative URLs | **Replaced** |
| **3. Receive app updates** (007 US3) | Poll latest-release endpoint on app start and resume, show update banner or mandatory-update blocker, and download a newly signed package | Load updates directly from the deployed web app through the service worker without manual package downloads or version-blocking screens | **Replaced** |
| **4. Biometric unlock (`Unlock with fingerprint`)** (007 US4) | Store a long-lived refresh token in native hardware storage and prompt for a fingerprint on cold start because the native web view had no shared browser session | Relies on the browser's persistent first-party session and the device's built-in password manager / passkey biometric unlock; custom in-app fingerprint toggle and native token storage are retired | **Deliberately dropped** |
| **5. Share links or text from other Android apps into JordyLab (MRB-06)** (007 US5) | Native share-intent plugin receives shared text and opens `/mobile/share` in memory ("Ask LibBot" for everyone, "Save to FNA" for administrators) | Standard Web Share Target declared in the web app manifest opens the share landing screen with the shared title, text or link; preserves the shared item across sign-in if the user was logged out | **Replaced** |
| **6. Admin push notifications and tap-through (MRB-07)** (007 US6) | Publish sign-up and briefing events to the self-hosted notification service with a `/mobile/open?screen=...` deep link intercepted by the native app | Keep the existing self-hosted notification service and its phone app; notification tap-through links point directly to the web routes (`/settings/users`, `/fna/briefing`), which open inside the installed web app | **Replaced** |
| **7. iPhone home-screen web app** (007 US7) | One-time Add-to-Home-Screen instruction sheet on iPhone/iPad Safari and standalone display mode | Kept unchanged alongside the Android web app install flow | **Kept** |
| **8. Native background geofencing** (010) | Planned native background location tracking (never shipped) | Handled outside the JordyLab app per the home-presence prototype decision | **Out of scope (dropped)** |

---

## Clarifications

### Session 2026-11-02

- Q: Should we delete the `mobile` backend module completely and move push notification sending into `shared`, or keep a dedicated listener module for `ntfy` notifications? → A: Delete the `mobile` module completely; move `NtfyClient` (and its `ntfy` configuration) into `shared`, and have `settings` and `fna` send their own admin push notifications directly.
- Q: Where should phone users find the manual "Install app" action and APK-replacement instructions once they dismiss the one-time bottom sheet? → A: Add an "Install on this phone" section in `Settings → App` (visible on phone browsers whenever not already running standalone, with the install button/steps and the note to uninstall the old APK), while keeping the QR code button in the desktop sidebar.
- Q: Is the JOR-36 presence decision gate resolved so this feature may start? → A: Yes (2026-11-02) — JOR-36 was re-scoped to a quick custom Android home-screen widget spike for Eufy arm/disarm, which removes the need for native background geofencing in the JordyLab app; the PWA replacement proceeds on that basis.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Install and launch JordyLab as a web app on Android and iPhone (Priority: P1)

As an approved user (administrator or guest) on a phone, I can install JordyLab directly from my browser onto my home screen and launch it in a full-screen standalone window without downloading package files or enabling "install unknown apps". Existing users who previously sideloaded the Android app receive clear instructions to uninstall the old app and install the web app from the site.

**Why this priority**: Installing and launching the app from the browser is the foundation that replaces sideloaded packages while preserving a first-class phone experience.

**Independent Test**: Open JordyLab in Android Chrome (or simulate an installable mobile browser in automated browser checks), verify the app meets web-installability criteria, trigger installation from the in-app prompt or user menu, and confirm it opens in standalone display mode on the site's own origin with all role-appropriate screens working.

**Acceptance Scenarios**:

1. **Given** the web app is served over HTTPS (or local development localhost), **When** a browser checks installability, **Then** the web app manifest, icons, start URL, standalone display mode, and service worker pass installability checks without errors.
2. **Given** an approved user visiting in an eligible Android browser who is not already running the installed web app and has not dismissed the prompt in the last 30 days, **When** the app loads and the browser signals that installation is available, **Then** JordyLab offers an install prompt that triggers the browser's native install confirmation rather than downloading an installation file.
3. **Given** the install prompt on Android or iPhone, **When** the user chooses "Not now", **Then** the automatic prompt stays hidden on that device for 30 days, while `Settings → App` (`More → App` on a phone) still shows an "Install on this phone" section whenever not running standalone so the user can trigger installation or read the install and APK-removal instructions on demand.
4. **Given** an approved user on a desktop browser, **When** they click the phone-install button in the desktop sidebar, **Then** a QR code pointing to the JordyLab web app URL is shown with brief instructions for installing on Android or iPhone (and how to remove the old sideloaded Android app if still installed).
5. **Given** an approved user who launches the installed web app from their home screen, **When** they sign in and browse Library, LibBot, Finance (administrators), or More / Settings, **Then** every screen, image, and action works identically to the browser tab on the same origin.

---

### User Story 2 - Share links or text from other Android apps into the installed web app (Priority: P1)

As an approved user with the JordyLab web app installed on Android, I can share a link or text from another app's share menu straight into JordyLab, and either ask LibBot about it (all approved users) or save the link as a financial news article (administrators only).

**Why this priority**: Receiving shared content from other phone apps is the one mobile-specific interaction in active use that depends on an installed app entry in the operating system's share sheet.

**Independent Test**: Open the share target route with shared title, text, and URL parameters (as an installed Android web app receives from the system share sheet) as a guest, as an administrator, and while signed out, and verify role-filtered actions and post-login resumption.

**Acceptance Scenarios**:

1. **Given** the JordyLab web app is installed on an Android phone, **When** the user opens the system share sheet in another app for a link or plain text, **Then** JordyLab appears as a share destination.
2. **Given** a signed-in guest shares text or a link to JordyLab, **When** the share landing screen opens, **Then** the shared content is displayed and only "Ask LibBot" is offered, which navigates to LibBot with the shared content prefilled.
3. **Given** a signed-in administrator shares a URL or text containing a URL to JordyLab, **When** the share landing screen opens, **Then** both "Ask LibBot" and "Save to FNA" are offered, and choosing "Save to FNA" submits the URL to the financial news list and navigates to the Articles screen.
4. **Given** a user is signed out when they share content to JordyLab, **When** the share landing route is opened, **Then** the user is taken through sign-in first and returned to the share landing screen with the shared content intact afterward.
5. **Given** a user opens the share landing screen directly with no shared title, text, or URL parameters, **When** the page renders, **Then** it shows a calm empty state or redirects cleanly to the home screen rather than a blank page.

---

### User Story 3 - Keep admin push notifications working with direct web links (Priority: P2)

As the administrator, I continue to receive push notifications on my phone when a new user sign-up is waiting for approval or when the scheduled financial briefing is ready, and tapping a notification opens the corresponding screen in the installed JordyLab web app (or browser).

**Why this priority**: Admin notifications are an established operational convenience that must survive the removal of the backend's package-hosting module without requiring a new push infrastructure.

**Independent Test**: Trigger a pending user sign-up in `settings` and a briefing completion in `fna`, and verify that each module publishes its notification through the shared `NtfyClient` to the configured notification server with click-through links that resolve to `/settings/users` and `/fna/briefing`.

**Acceptance Scenarios**:

1. **Given** a new user registers and awaits approval, **When** `settings` detects the pending sign-up, **Then** a push notification is sent via the shared notification client with a click-through link that opens the Users settings screen (`/settings/users`).
2. **Given** a scheduled or manual financial briefing finishes generating, **When** `fna` completes the briefing, **Then** a push notification is sent via the shared notification client with a click-through link that opens the Briefing screen (`/fna/briefing`).
3. **Given** the notification service configuration is blank or the notification server is temporarily unreachable, **When** either notification is sent, **Then** the missing configuration is logged at startup and any delivery failure is logged as a warning without failing the sign-up or briefing flow.
4. **Given** all backend module boundary checks run after removing the `mobile` module, **When** `ModularityTests` executes, **Then** every module boundary rule passes with `settings` and `fna` calling the shared notification client and no `mobile` module remaining.

---

### User Story 4 - Retire the native wrapper, package hosting, and Android CI pipeline (Priority: P2)

As the platform owner, I want the native Android wrapper, signed package hosting, download-link endpoints, native-only identity clients, and emulator test jobs completely removed from the codebase, deployment manifests, and continuous integration workflows, so that every release ships as a single web deployment with no Android build or signing overhead.

**Why this priority**: Removing the redundant stack delivers the main maintenance and architectural payoff of this feature and unblocks moving to same-origin cookie sessions later.

**Independent Test**: Build and test the frontend and backend, inspect the workspace projects, dependencies, deployment manifests, and CI workflows, and confirm that no native wrapper dependencies, package-hosting endpoints, Android release jobs, or Android emulator workflows remain.

**Acceptance Scenarios**:

1. **Given** the frontend workspace, **When** projects and dependencies are inspected, **Then** the native wrapper project, the Android emulator test project, all native wrapper packages, native biometric unlock code, package-download services, update-check banners, and local-origin URL rewriting are gone.
2. **Given** the backend application, **When** endpoints and database migrations are inspected, **Then** the package upload, latest-release, download-link, package-download, and digital-asset-links endpoints are removed, the obsolete release table is dropped cleanly by a migration, and revoking a user no longer calls the retired native identity client.
3. **Given** the deployment manifests and identity-provider configuration, **When** they are inspected, **Then** the persistent volume for mobile packages, the package-signing and download-link settings, the native app identity client (`jordylab-mobile`), the release-publisher service account (`mobile-release-ci`), and the `mobile-release-publisher` role are removed.
4. **Given** the continuous integration and release workflows, **When** a commit is pushed or a release is tagged, **Then** no Android emulator workflow exists, the release workflow no longer builds, signs, or uploads an Android package, and the automated web end-to-end suite (`e2e-web`) verifies the web app manifest and share-target journey.

---

### Edge Cases

- **Shared URL arrives inside the `text` field instead of `url`**: Many Android apps place the shared URL at the end of the `text` parameter rather than in `url`. When extracting the link for "Save to FNA" or display, the share landing screen extracts the first `http://` or `https://` URL from `url` or `text`, while keeping the full shared text available for "Ask LibBot".
- **User shares content while signed out**: The query parameters (`title`, `text`, `url`) on the share route must survive the sign-in redirect so that after signing in, the user lands on the share screen with their shared content intact.
- **Browser does not support programmatic install prompt (e.g., iOS Safari, Firefox on Android, or prompt already used)**: The app falls back to showing concise step-by-step instructions ("Tap the browser menu and choose Install app / Add to Home Screen") instead of an action button that would do nothing.
- **User already has the old sideloaded Android app installed**: The install instructions and desktop QR modal include a one-line note telling returning Android users to uninstall the old sideloaded JordyLab app so they do not see two icons on their home screen or share sheet.
- **Service worker and API / auth requests**: The minimal service worker exists to satisfy browser installability and keep static shell assets fresh; it must never cache authenticated `/api/**` responses, streaming LibBot responses, or `/auth/**` sign-in flows.
- **Legacy notification click-through URLs (`/mobile/open?screen=...`)**: Any already-delivered notification on the admin's phone or bookmarked link using `/mobile/open?screen=settings-users` or `/mobile/open?screen=fna-briefing` either redirects to `/settings/users` / `/fna/briefing` or is replaced by direct routes so tapping an older notification never lands on a broken page.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The web app MUST include a valid web app manifest (`manifest.webmanifest`) served from the root with `name`, `short_name`, `start_url`, `scope`, `display: "standalone"`, `background_color`, `theme_color`, standard and maskable icons (192×192 and 512×512), and a `share_target` definition.
- **FR-002**: The web app MUST register a minimal service worker in production builds that satisfies browser installability criteria without caching authenticated `/api/**` requests, server-sent event streams, or `/auth/**` identity flows.
- **FR-003**: On Android browsers, the app MUST NOT suppress the browser's `beforeinstallprompt` event for the purpose of forcing a package download; instead, it MUST capture the event when available so approved users can trigger browser installation from the one-time install banner or from `Settings → App`.
- **FR-004**: When an approved user on Android or iOS is already running in standalone display mode (`display-mode: standalone` or iOS standalone), the app MUST hide the automatic install prompt and the "Install on this phone" section in `Settings → App`.
- **FR-005**: Dismissal of the automatic install prompt ("Not now") MUST be remembered on that device for 30 days, while manual access to the "Install on this phone" section in `Settings → App` (on phone browsers) and the QR code entry in the desktop sidebar remains available whenever the app is not running standalone.
- **FR-006**: The install instructions (in the bottom sheet, in `Settings → App`, and in the desktop QR entry) MUST guide users to install the web app from the browser and include a short note for existing Android users to uninstall the previous sideloaded app.
- **FR-007**: The web app manifest's `share_target` MUST accept `GET` requests to the share route with `title`, `text`, and `url` query parameters so that an installed Android web app appears in the system share sheet for shared text and links.
- **FR-008**: The share landing screen MUST read shared content from the route's `title`, `text`, and `url` query parameters, preserve those parameters across the sign-in flow when a user is unauthenticated, offer "Ask LibBot" to all approved users, and offer "Save to FNA" (using the shared URL or the first `http(s)` URL found in the shared text) to administrators only.
- **FR-009**: Admin push notifications for pending user sign-ups and completed financial briefings MUST continue to be published to the self-hosted notification server (`ntfy`), with click-through URLs that open `/settings/users` and `/fna/briefing` on the production web domain.
- **FR-010**: The `mobile` backend module MUST be deleted completely; `NtfyClient` and its `ntfy` configuration MUST move into `shared` (exposed via a named interface), and `settings` and `fna` MUST invoke `NtfyClient` directly when a sign-up is pending or a briefing is ready, keeping `ModularityTests` green.
- **FR-011**: All native Android wrapper code and dependencies (`apps/jordylab-mobile`, `apps/jordylab-mobile-e2e`, `@capacitor/*`, `@capgo/*`), native-only frontend services (`ApkDownloadService`, `UpdateCheckStore`, `AppLinkService`, `BiometricUnlockService`, `BiometricUnlockToggleComponent`, `UpdateAvailableBannerComponent`, `UpdateRequiredComponent`, local-origin URL rewriting), and the `mobile` build configuration in `apps/jordylab` MUST be removed.
- **FR-012**: In `Settings → App`, the native-only fingerprint unlock section MUST be removed and replaced on phone browsers (when not running standalone) by the "Install on this phone" section, while the theme selector stays available to all approved users on web and phone.
- **FR-013**: All backend package-hosting endpoints (`POST /api/mobile/releases`, `GET /api/mobile/releases/latest`, `POST /api/mobile/releases/latest/download-link`, `POST /api/mobile/releases/{id}/download-link`, `GET /api/mobile/download/{token}`, `GET /.well-known/assetlinks.json`), their services, entities, and signing-certificate utilities MUST be removed with the `mobile` module, and a Flyway migration MUST drop the obsolete `mobile.mobile_release` table and `mobile` schema.
- **FR-014**: User access revocation in `settings` MUST no longer attempt to revoke offline consent for the retired `jordylab-mobile` client.
- **FR-015**: Deployment manifests and identity-provider exports (`deploy/k8s/`, `deploy/keycloak/realm-prod.json`, `jordylab-be/compose/keycloak-realm-export.json`) MUST remove the `mobile-releases` persistent volume claim and mount, mobile package/signing environment variables, and the `jordylab-mobile` and `mobile-release-ci` clients and `mobile-release-publisher` role.
- **FR-016**: Continuous integration workflows MUST remove `.github/workflows/e2e-android.yml` and the `apk` job (and Android version-code derivation) from `.github/workflows/release.yml`, and the web end-to-end test suite (`e2e-web`) MUST verify the installable web app manifest (including `share_target`) and the share-target landing journey (guest, admin, and unauthenticated redirect recovery).

### Key Entities

- **Web App Manifest**: The public metadata document declaring the app's name, icons, standalone display mode, theme/background colours, scope, and Web Share Target configuration.
- **Shared Payload**: A transient set of query parameters (`title`, `text`, `url`) passed by the operating system's share sheet to the share landing route, resolved into display text and an optional target URL.
- **Admin Notification Event**: A domain event (`UserSignUpPending` or `BriefingReady`) translated into an outbound push message with a title, body, and web click-through URL.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An approved Android user can install JordyLab from the browser in 3 taps or fewer without enabling unknown-source package installation, and launching the installed app opens in standalone display mode on the production origin.
- **SC-002**: 100% of journeys marked "Replaced" or "Kept" in the Journey Mapping table (install, sign-in and browsing, automatic updates, sharing to LibBot and FNA, admin push notifications, and iOS home-screen install) work end-to-end without the native wrapper.
- **SC-003**: After removal, the frontend has 0 native-wrapper dependencies, the backend exposes 0 package-hosting endpoints, and CI runs 0 Android build or emulator jobs, reducing release pipeline execution by eliminating the entire Android build and upload stage.
- **SC-004**: Automated web end-to-end tests (`e2e-web`) verify that the manifest is served with valid installability and share-target fields and that sharing a link routes guests to LibBot and allows administrators to save the link to FNA, including when the user must sign in first.

---

## Assumptions

- **Presence spike gate**: This specification assumes the decision gate in `JOR-36` (`presence-ha-prototype`) has been resolved in favour of handling home-presence geofencing outside the JordyLab mobile app (or dropping native JordyLab geofencing), so native background location tracking is not required. **Resolved 2026-11-02**: JOR-36 was re-scoped to a quick custom Android home-screen widget spike for Eufy arm/disarm — native background geofencing is not built into the JordyLab app, and this feature proceeds.
- **Push notifications stay on ntfy**: The existing self-hosted `ntfy` server and the ntfy Android app already deliver admin notifications reliably on production (verified in MRB-07). Web Push with VAPID keys and a browser subscription store are not needed and are out of scope.
- **No user data migration required**: All user data (game catalog marks, sources, financial portfolio, wishlist, briefings, and AI model settings) is stored server-side; the only client-side preference (`jordylab.theme`) is per-device in browser storage, which the installed web app shares with the browser.
- **Same-origin web app prepares for `auth-without-keycloak`**: Because the installed web app runs on `https://jordylab.be` rather than a separate `https://localhost` WebView origin, all API calls and future HttpOnly cookie sessions work first-party without cross-origin workarounds.

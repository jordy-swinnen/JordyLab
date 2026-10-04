# Feature Specification: JordyLab Mobile App (Android via Capacitor, iOS home-screen web app)

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-09-27
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Depends on**: Settings (admin/guest roles), production HTTPS deployment

---

## Overview

Approved JordyLab users can install an Android app straight from the website, with no Play Store. The app is the existing JordyLab UI bundled in a Capacitor shell, plus three native extras: biometric unlock, "share to JordyLab", and tap-to-open notifications. iPhone users can add JordyLab to their home screen as a web app. The website nudges each platform toward the right install path exactly once.

---

## User Scenarios & Testing

### User Story: Install the Android app from the website (Priority: High)

As an approved user on an Android phone, I want the website to offer me the app, so that I can install it in a minute without the Play Store.

**Independent Test**: On an Android phone, log in to the website as a guest, accept the dialog, install the APK and open it.

**Acceptance Scenarios**:
1. **Given** an approved user on an Android browser (not in the app), **When** the website loads for the first time, **Then** a "Get the JordyLab app" dialog shows a download button and 3 steps for allowing the install.
2. **Given** the dialog, **When** the user taps Download, **Then** a short-lived link downloads the latest signed APK.
3. **Given** the dialog, **When** the user taps "Not now", **Then** it doesn't appear again on that device for 30 days. The user menu still offers "Get the Android app".
4. **Given** a pending user or a logged-out visitor, **When** they visit, **Then** no dialog is shown, and requesting a download link returns 403.
5. **Given** an expired or tampered download link, **When** it's opened, **Then** the download is refused.
6. **Given** a desktop browser, **When** the admin or a guest opens the user menu, **Then** "Get the Android app" shows a QR code to the download page. No automatic popup appears.
7. **Given** Android Chrome, **When** the site is eligible for the browser's own "install web app" prompt, **Then** that prompt is suppressed so only the APK dialog shows.

### User Story: Log in and use the app like the website (Priority: High)

**Acceptance Scenarios**:
1. **Given** a fresh install, **When** the user taps Log in, **Then** the Keycloak login opens in the phone's browser and returns to the app once login succeeds.
2. **Given** a logged-in guest, **When** the app renders, **Then** they see only the Game Catalog, the same as on the web. Admin sees everything.
3. **Given** a pending user, **When** they log in, **Then** they see the "awaiting approval" screen.
4. **Given** the app, **When** it loads artwork and calls the API, **Then** everything works the same as on the website (no broken images or failed calls).

### User Story: Update prompt (Priority: High)

**Acceptance Scenarios**:
1. **Given** a newer release is published, **When** the app starts or comes back to the foreground, **Then** it shows "Update available: vX.Y" with release notes, and tapping it downloads the new APK.
2. **Given** the installed version is below the minimum supported version, **When** the app starts, **Then** a blocking "Update required" screen is shown.
3. **Given** CI builds a tagged release, **When** it finishes, **Then** the release shows up in JordyLab with version, notes, size and SHA-256, signed with the same key as every earlier release.

### User Story: Biometric unlock (Priority: Medium)

**Acceptance Scenarios**:
1. **Given** a first successful login, **When** the user enables "Unlock with fingerprint", **Then** later app opens only need a biometric check, with no password.
2. **Given** biometric unlock is on, **When** the biometric check fails, is cancelled, or the phone's enrolled biometrics change, **Then** the app falls back to the normal login.
3. **Given** the user logs out, turns biometrics off, or the admin revokes them, **When** the app is next opened, **Then** the stored session no longer works and a full login is required.

### User Story: Share to JordyLab (Priority: Low)

**Acceptance Scenarios**:
1. **Given** any Android app's share menu, **When** a user shares a link or text, **Then** JordyLab appears as a target.
2. **Given** a guest shares something, **When** JordyLab opens, **Then** only "Ask the catalog" is offered, and it opens chat with the shared text prefilled.
3. **Given** the admin shares a link, **When** JordyLab opens, **Then** "Save to FNA" is offered as well, and confirming it queues the link as an article for the next briefing.
4. **Given** the user isn't logged in, **When** they share, **Then** they log in first and then continue with the share.

### User Story: Notifications (Priority: Low)

**Acceptance Scenarios**:
1. **Given** a new sign-up is pending, **When** it's created, **Then** the admin gets a notification, and tapping it opens Settings → Users in the app.
2. **Given** the daily FNA briefing is generated, **When** it's ready, **Then** the admin gets a notification, and tapping it opens the briefing in the app.
3. Delivery channel: [NEEDS CLARIFICATION: Ntfy + deep links / Firebase Cloud Messaging / custom UnifiedPush]

### User Story: iPhone home-screen web app (Priority: Low)

**Acceptance Scenarios**:
1. **Given** an approved user on iPhone/iPad Safari, **When** the website loads for the first time, **Then** a one-time sheet explains Share → Add to Home Screen.
2. **Given** the site is added to the home screen, **When** it's opened, **Then** it runs full-screen with the JordyLab name and icon.
3. **Given** the site is already running as a home-screen app, **When** it loads, **Then** no install sheet is shown.

### Edge Cases
- The user installs the APK on a phone without "Install unknown apps" allowed: the dialog's steps cover how to enable it.
- An APK signed with a different key: Android refuses the update. This is prevented by the single-keystore rule, and CI fails if the signing-cert fingerprint differs from the configured one.
- The download link is shared outside the group: it expires after minutes. The APK is useless without an approved account anyway.
- The app is offline: it shows a clear "can't reach JordyLab" state, not a blank screen.
- The admin revokes a guest who has biometric unlock: the next refresh fails and the app returns to login.
- 2027 Android developer verification: the app must be registered under the Limited Distribution account before the global rollout, or friends face the 24-hour advanced install flow.
- Android Chrome after "Not now" still shows the browser's own PWA install prompt: it must stay suppressed.

---

## Requirements

### Functional: Distribution
- The system MUST publish signed Android releases with version name, version code, release notes, SHA-256, size, minimum supported version code and publish date.
- Only approved users (admin, guest) MAY obtain a download link. Links MUST expire within minutes and MUST NOT be reusable after expiry.
- All releases MUST be signed with the same key. CI MUST fail on a signing-cert mismatch.
- Publishing a release MUST be restricted to the CI service identity.

### Functional: Install prompts (web)
- The website MUST tell Android browsers, iOS/iPadOS Safari, desktop and the native app apart, and MUST show at most one install prompt that fits the platform.
- Dismissal MUST be remembered per device for 30 days.
- The browser's own web-app install prompt MUST be suppressed on Android.
- The website MUST be installable as a home-screen web app on iOS (manifest, icons, standalone display).

### Functional: App
- The app MUST bundle the web UI and apply the same role rules as the website.
- Login MUST use the platform browser and return to the app. Credentials never pass through app code.
- The app MUST check for updates on start and resume, and MUST block versions below the minimum supported one.
- Biometric unlock MUST be opt-in. Its stored session MUST live in hardware-backed secure storage and MUST be wiped on logout, when biometrics are disabled, or when enrolled biometrics change.
- Revoking a user (the Settings spec) MUST also invalidate their mobile long-lived sessions.
- The app MUST register as a share target for text and URLs, and MUST only offer the destinations the user's role allows.
- Notification taps MUST open the matching screen in the app.

### Key Entities
- **MobileRelease**: version name, version code, release notes, SHA-256, size, minimum supported version code, published at, storage reference.
- **DownloadLink** (not stored, signed): release id, user subject, expiry.
- **ShareDestination** (client-side): key, label, allowed roles.

---

## Success Criteria
- A guest on a fresh Android phone goes from opening the website to a logged-in app in under 3 minutes, following only the on-screen steps.
- 100% of API calls and artwork images load in the app (no relative-URL failures), checked with the device test checklist.
- With biometric unlock on, reopening the app after 7 days needs no password.
- A new tagged release reaches the "Update available" prompt on an installed device with no manual steps beyond pushing the tag.
- Pending users, logged-out visitors and expired links get 403 on 100% of download attempts (automated tests).

---

## Assumptions
- Capacitor 8.x (stable). No v9 pre-release.
- Production runs on a public HTTPS domain (separate deployment spec). The domain hosts `/.well-known/assetlinks.json`.
- The Keycloak realm from the Settings spec gets a new public client for the mobile app, with offline access allowed.
- Ntfy is already running and reachable from the backend (if chosen for notifications).
- No Play Store, F-Droid or native iOS distribution.

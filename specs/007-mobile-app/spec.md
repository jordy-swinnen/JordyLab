# Feature Specification: Mobile App (Android via Capacitor, iOS Home-Screen Web App)

**Feature Branch**: `007-mobile-app`

**Created**: 2026-09-28

**Status**: Draft

**Depends On**: 006 Settings (admin/guest roles, approved users), 008 production HTTPS deployment (not yet specified)

**Input**: User description: "Add an Android app for JordyLab built with Capacitor, installable without the Play
Store, plus an install experience in the web app. iPhone gets a home-screen web app instead of a native app. WHY: I and
my approved friends (spec 006 roles admin/guest) want JordyLab as a real app on our phones, with a few things the
website can't do: notifications, fingerprint unlock, and sharing links from other apps into JordyLab. The Android app
bundles the existing JordyLab web UI with the same role rules as the website; login opens in the phone's browser and
returns to the app; biometric unlock is opt-in and removed on logout/revoke; a share target lets users send text or
links into JordyLab (chat for everyone, save-to-FNA for admin only); the admin is notified on new sign-ups and when the
daily briefing is ready; the app checks for and can be blocked below a minimum version. The website offers a one-time
install dialog for the APK to approved Android users, a one-time Add-to-Home-Screen sheet for iOS Safari, and a
QR-code entry on desktop; the APK is only downloadable by approved users via a short-lived link. Releases are built,
signed once and for good, and published by CI. Out of scope: Play Store/F-Droid publishing, a native iOS app,
TestFlight, over-the-air web-bundle updates, offline mode, tablet/foldable-specific layouts, notifications for guests."
(full description in `specs/_drafts/007-mobile-app/speckit-prompts.md` §1)

---

## Overview

Approved JordyLab users can install an Android app straight from the website, with no Play Store. The app is the
existing JordyLab UI bundled in a Capacitor shell, plus three native extras: biometric unlock, "share to JordyLab",
and tap-to-open notifications. iPhone users can add JordyLab to their home screen as a web app instead. The website
nudges each platform toward the right install path exactly once.

---

## Clarifications

### Session 2026-09-28

- Q: Which channel should deliver push notifications to the admin (new sign-up pending, briefing ready)? → A: Ntfy +
  deep links — the backend publishes to the existing Ntfy server with a click-through link that opens the matching
  app screen.
- Q: Should "Save to FNA" (the admin-only share destination) stay in this feature's scope? → A: Keep it, at P3 — it
  needs a small new FNA endpoint to accept a manually shared article URL.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Install the Android app from the website (Priority: P1)

As an approved user on an Android phone, I want the website to offer me the app, so that I can install it in a minute
without the Play Store.

**Why this priority**: Without a working install path there is no app to use — this is the entry point for every
other story.

**Independent Test**: On an Android phone, log in to the website as a guest, accept the dialog, install the APK and
open it.

**Acceptance Scenarios**:

1. **Given** an approved user on an Android browser (not in the app), **When** the website loads for the first time,
   **Then** a "Get the JordyLab app" dialog shows a download button and 3 steps for allowing the install.
2. **Given** the dialog, **When** the user taps Download, **Then** a short-lived link downloads the latest signed APK.
3. **Given** the dialog, **When** the user taps "Not now", **Then** it doesn't appear again on that device for 30
   days. The user menu still offers "Get the Android app".
4. **Given** a pending user or a logged-out visitor, **When** they visit, **Then** no dialog is shown, and requesting
   a download link returns 403.
5. **Given** an expired or tampered download link, **When** it's opened, **Then** the download is refused.
6. **Given** a desktop browser, **When** the admin or a guest opens the user menu, **Then** "Get the Android app"
   shows a QR code to the download page. No automatic popup appears.
7. **Given** Android Chrome, **When** the site is eligible for the browser's own "install web app" prompt, **Then**
   that prompt is suppressed so only the APK dialog shows.

---

### User Story 2 - Log in and use the app like the website (Priority: P1)

As an approved user with the app installed, I want to log in and see the same JordyLab I use on the web, so that the
app is a trustworthy substitute rather than a stripped-down copy.

**Why this priority**: The app is worthless if login or basic use doesn't work; this is the core experience.

**Independent Test**: Install the app, log in as a guest and as the admin, and confirm each sees the same
role-appropriate screens and data as the website.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user taps Log in, **Then** the Keycloak login opens in the phone's browser
   and returns to the app once login succeeds.
2. **Given** a logged-in guest, **When** the app renders, **Then** they see only the Game Catalog, the same as on the
   web. Admin sees everything.
3. **Given** a pending user, **When** they log in, **Then** they see the "awaiting approval" screen.
4. **Given** the app, **When** it loads artwork and calls the API, **Then** everything works the same as on the
   website (no broken images or failed calls).

---

### User Story 3 - Update prompt (Priority: P1)

As the admin publishing a new release, I want installed apps to be told about it and old versions to be blocked, so
that everyone stays on a working, supported build.

**Why this priority**: Without an update path, a bundled-UI app can never fix bugs or ship features once installed.

**Independent Test**: Publish a new release via CI, open an installed app, and confirm the update prompt (or the
mandatory-update block for an out-of-date version) appears.

**Acceptance Scenarios**:

1. **Given** a newer release is published, **When** the app starts or comes back to the foreground, **Then** it
   shows "Update available: vX.Y" with release notes, and tapping it downloads the new APK.
2. **Given** the installed version is below the minimum supported version, **When** the app starts, **Then** a
   blocking "Update required" screen is shown.
3. **Given** CI builds a tagged release, **When** it finishes, **Then** the release shows up in JordyLab with
   version, notes, size and SHA-256, signed with the same key as every earlier release.

---

### User Story 4 - Biometric unlock (Priority: P2)

As a returning user, I want to unlock the app with my fingerprint or face instead of typing my password every time,
so that daily use is fast without weakening account security.

**Why this priority**: A real convenience win over the website, but the app is fully usable without it.

**Independent Test**: Log in, enable biometric unlock, close and reopen the app, and confirm a biometric check
replaces the password prompt; then revoke the user and confirm the app returns to login.

**Acceptance Scenarios**:

1. **Given** a first successful login, **When** the user enables "Unlock with fingerprint", **Then** later app opens
   only need a biometric check, with no password.
2. **Given** biometric unlock is on, **When** the biometric check fails, is cancelled, or the phone's enrolled
   biometrics change, **Then** the app falls back to the normal login.
3. **Given** the user logs out, turns biometrics off, or the admin revokes them, **When** the app is next opened,
   **Then** the stored session no longer works and a full login is required.

---

### User Story 5 - Share to JordyLab (Priority: P3)

As a user browsing another app, I want to share a link or text straight into JordyLab, so that I don't have to
switch apps and retype it.

**Why this priority**: A nice-to-have that depends on the app already working; lowest priority of the native extras.

**Independent Test**: From another Android app's share menu, share a link to JordyLab as a guest and as the admin,
and confirm each sees only the destinations their role allows.

**Acceptance Scenarios**:

1. **Given** any Android app's share menu, **When** a user shares a link or text, **Then** JordyLab appears as a
   target.
2. **Given** a guest shares something, **When** JordyLab opens, **Then** only "Ask the catalog" is offered, and it
   opens chat with the shared text prefilled.
3. **Given** the admin shares a link, **When** JordyLab opens, **Then** "Save to FNA" is offered as well, and
   confirming it queues the link as an article for the next briefing.
4. **Given** the user isn't logged in, **When** they share, **Then** they log in first and then continue with the
   share.

---

### User Story 6 - Notifications (Priority: P3)

As the admin, I want to be notified when a new sign-up is waiting or the daily briefing is ready, so that I don't
have to keep checking the app.

**Why this priority**: A convenience for the admin only; no other role depends on it, and the app works without it.

**Independent Test**: Trigger a new sign-up and a briefing generation, and confirm the admin receives a notification
for each that opens the right screen when tapped.

**Acceptance Scenarios**:

1. **Given** a new sign-up is pending, **When** it's created, **Then** the admin gets a notification, and tapping it
   opens Settings → Users in the app.
2. **Given** the daily FNA briefing is generated, **When** it's ready, **Then** the admin gets a notification, and
   tapping it opens the briefing in the app.
3. **Given** either event, **When** the backend publishes it, **Then** delivery goes through the existing Ntfy server
   with a click-through link that opens the matching app screen.

---

### User Story 7 - iPhone home-screen web app (Priority: P3)

As an approved user on an iPhone, I want to add JordyLab to my home screen, so that I have an app-like way to open it
even without a native app.

**Why this priority**: Covers the iOS audience without the cost of a native iOS build; not on the critical path for
Android users.

**Independent Test**: On iPhone/iPad Safari, log in as an approved user, follow the one-time sheet to add JordyLab to
the home screen, and confirm it opens full-screen with no further prompt.

**Acceptance Scenarios**:

1. **Given** an approved user on iPhone/iPad Safari, **When** the website loads for the first time, **Then** a
   one-time sheet explains Share → Add to Home Screen.
2. **Given** the site is added to the home screen, **When** it's opened, **Then** it runs full-screen with the
   JordyLab name and icon.
3. **Given** the site is already running as a home-screen app, **When** it loads, **Then** no install sheet is
   shown.

---

### Edge Cases

- The user installs the APK on a phone without "Install unknown apps" allowed: the dialog's steps cover how to
  enable it.
- An APK signed with a different key: Android refuses the update. This is prevented by the single-keystore rule, and
  the release pipeline fails if the signing-cert fingerprint differs from the configured one.
- The download link is shared outside the group: it expires after minutes. The APK is useless without an approved
  account anyway.
- The app is offline: it shows a clear "can't reach JordyLab" state, not a blank screen.
- The admin revokes a guest who has biometric unlock: the next refresh fails and the app returns to login.
- 2027 Android developer verification: the app must be registered under a Limited Distribution account before the
  global rollout, or friends face a 24-hour advanced-install flow.
- Android Chrome after "Not now" still tries to show the browser's own PWA install prompt: it must stay suppressed.

---

## Requirements *(mandatory)*

### Functional Requirements

**Distribution**

- **FR-001**: The system MUST publish signed Android releases with version name, version code, release notes,
  SHA-256, size, minimum supported version code and publish date.
- **FR-002**: Only approved users (admin, guest) MAY obtain a download link. Links MUST expire within minutes and
  MUST NOT be reusable after expiry.
- **FR-003**: All releases MUST be signed with the same key, created once and never rotated. The release pipeline
  MUST fail on a signing-cert mismatch.
- **FR-004**: Publishing a release MUST be restricted to the CI/build identity — never available to end users.

**Install prompts (web)**

- **FR-005**: The website MUST tell Android browsers, iOS/iPadOS Safari, desktop and the native app apart, and MUST
  show at most one install prompt that fits the platform.
- **FR-006**: Dismissal of the install prompt MUST be remembered per device for 30 days.
- **FR-007**: The browser's own web-app install prompt MUST be suppressed on Android.
- **FR-008**: The website MUST be installable as a home-screen web app on iOS (name, icon, standalone display).

**App**

- **FR-009**: The app MUST bundle the web UI and apply the same role rules as the website.
- **FR-010**: Login MUST use the platform browser and return to the app. Credentials MUST never pass through app
  code.
- **FR-011**: The app MUST check for updates on start and resume, and MUST block versions below the minimum
  supported one with a mandatory-update screen.
- **FR-012**: Biometric unlock MUST be opt-in. Its stored session MUST be held in hardware-backed secure storage and
  MUST be wiped on logout, when biometrics are disabled, or when enrolled biometrics change.
- **FR-013**: Revoking a user (spec 006) MUST also invalidate their mobile long-lived sessions.
- **FR-014**: The app MUST register as a share target for text and URLs, and MUST only offer the destinations the
  user's role allows.
- **FR-015**: Notification taps MUST open the matching screen in the app.
- **FR-017**: Confirming "Save to FNA" on a share MUST queue the shared link as an article candidate for the next
  daily briefing. This destination MUST remain admin-only.
- **FR-016**: The system MUST notify the admin when a new sign-up is pending and when the daily briefing is ready, via
  the existing Ntfy server with a click-through link that opens the matching app screen.

### Key Entities *(include if feature involves data)*

- **MobileRelease**: version name, version code, release notes, SHA-256, size, minimum supported version code,
  published at, storage reference.
- **DownloadLink** (not persisted, signed): release id, user subject, expiry.
- **ShareDestination** (client-side): key, label, allowed roles.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A guest on a fresh Android phone goes from opening the website to a logged-in app in under 3 minutes,
  following only the on-screen steps.
- **SC-002**: 100% of API calls and artwork images load correctly in the app (no relative-URL failures), verified by
  a device test pass.
- **SC-003**: With biometric unlock on, reopening the app after 7 days needs no password.
- **SC-004**: A new tagged release reaches the "Update available" prompt on an installed device with no manual steps
  beyond publishing the release.
- **SC-005**: Pending users, logged-out visitors and expired links are denied on 100% of download attempts, verified
  by automated tests.

---

## Assumptions

- The Android app is delivered as a sideloaded APK bundling the current web UI; no Play Store, F-Droid, or native
  iOS distribution is in scope.
- iPhone/iPad users get a home-screen web app (Add to Home Screen) rather than a native app.
- Production runs on a public HTTPS domain, delivered by feature 008 (not yet specified). That domain is needed for
  API calls from the phone, the Keycloak login redirect back to the app, and the APK download link, so 007 cannot be
  validated end-to-end on a real device until 008 exists.
- The Keycloak realm and the admin/guest roles from spec 006 already exist; the mobile app reuses the same accounts
  and approval state, including revocation.
- Notifications are delivered through the existing Ntfy server (see FR-016), reused rather than replaced. Guests have
  no notification use case in this feature; FCM or a UnifiedPush plugin remain options for a future feature if guests
  need push.
- Losing the release signing key would force every installed app to be uninstalled and reinstalled; the key is
  created once and backed up outside of source control.

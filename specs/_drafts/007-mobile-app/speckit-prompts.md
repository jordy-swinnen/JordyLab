# 007 Mobile App: SpecKit Prompts

Run these in order. Background is in `research.md`, and `spec-draft.md` shows the expected shape.

> **Numbering:** 005 is on another branch and 006 (Settings) may not exist in `specs/` yet, so SpecKit could pick the wrong number. The prompt below forces 007 (the script accepts `--number 7`). Check that the created folder/branch is `007-mobile-app` before continuing.
>
> **Order:** 007 depends on 006 (roles, approved users) and on a public HTTPS deployment. Specifying now is fine. Implement after 006.

---

## 1. `/speckit-specify`

```
Use feature number 007 (short name: mobile-app). 005 and 006 are taken by other features.

Add an Android app for JordyLab built with Capacitor, installable without the Play Store, plus an install experience in the web app. iPhone gets a home-screen web app instead of a native app.

WHY: I and my approved friends (spec 006 roles admin/guest) want JordyLab as a real app on our phones, with a few things the website can't do: notifications, fingerprint unlock, and sharing links from other apps into JordyLab.

THE APP
- The Android app is the existing JordyLab web UI (same features, same role rules as the website: guests only see the Game Catalog, admin sees everything). The UI is bundled inside the app, not loaded from the website.
- Login uses the same Keycloak accounts. The login screen opens in the phone's browser and returns to the app afterwards. Pending (unapproved) users see the same "awaiting approval" screen as on the web.
- Fingerprint/face unlock: after the first successful login, the user can turn on biometric unlock, so they don't need to type their password every time they open the app. Turning it off, logging out, or the admin revoking the user (spec 006) removes the stored session from the phone.
- Share to JordyLab: from any Android app's share menu, a user can share a link or text to JordyLab and choose a destination they're allowed to use: "Ask the catalog" (chat opens with the shared text prefilled, everyone) and, for the admin only, "Save to FNA" (the link is queued as an article for the next briefing).
- Notifications: the admin gets notified when a new sign-up is pending and when the daily FNA briefing is ready. Tapping a notification opens the matching screen in the app. [NEEDS CLARIFICATION: delivery channel — existing Ntfy with deep links (Google-free, needs ntfy app), Firebase Cloud Messaging, or a custom UnifiedPush plugin]
- Updates: when a newer app version is published, the app shows "Update available" with the version and release notes. Tapping it downloads and installs the new APK. Old versions below a minimum supported version are blocked with a mandatory-update screen.

INSTALL FROM THE WEB APP
- The APK can only be downloaded by logged-in approved users (admin or guest). The download link is short-lived.
- When an approved user opens the website in a browser on an Android phone (not inside the app), they get a one-time install dialog: "Get the JordyLab app", with a download button and 3 short steps explaining how to allow the install. "Not now" hides it for 30 days. The browser's own "install this web app" prompt is suppressed on Android so there aren't two competing prompts.
- On iPhone/iPad Safari, approved users get a one-time sheet explaining Share → Add to Home Screen. The website becomes installable as a home-screen web app (name, icon, standalone display).
- On desktop there's no popup, only a "Get the Android app" entry in the user menu showing a QR code to the download page.
- Inside the Android app, none of these install prompts appear.

RELEASES
- Each app release is built and signed automatically by CI, and published to JordyLab with version name, version number, release notes, file size and SHA-256 checksum. The admin can see the published versions in Settings.
- The signing key is created once and never changes (losing it would force everyone to reinstall).

Out of scope: Play Store / F-Droid publishing, a native iOS app, TestFlight, over-the-air web-bundle updates, offline mode, tablets/foldables-specific layouts, notifications for guests.
```

---

## 2. `/speckit-clarify`: expected questions and suggested answers
1. Push channel → **Ntfy + App Link deep links** for now (see research §4). FCM or UnifiedPush later if guests need pushes.
2. "Save to FNA" needs a new FNA endpoint for manual article URLs. Keep it in 007, or cut to "Ask the catalog" only? → Suggest keep, P3.
3. Minimum Android version → Capacitor 8's floor (API 24 / Android 7) is fine. Or raise it to API 29 (Android 10) to cut the test matrix.
4. Where APK files are stored in production (object storage vs. backend volume) → plan phase.
5. The Android developer verification Limited Distribution account (20 devices) needs to be registered before the 2027 global rollout → note as an operational task.

---

## 3. `/speckit-plan`

```
Tech context for 007 mobile app (read AGENTS.md, the constitution, and specs/_drafts/007-mobile-app/research.md first, carry its findings into this feature's research.md, and verify every library/version claim against live docs before committing — report rather than guess on mismatches):

Frontend (jordylab-fe, Angular 21 zoneless, Nx 22.5, Bun):
- Capacitor 8 (verify latest 8.x; do NOT adopt v9 pre-release). New Nx app `apps/jordylab-mobile` holding capacitor.config.ts + android/ (commit android/, gitignore build outputs). webDir = output of a new `jordylab:build:mobile` configuration. App id: pick a reverse-DNS id on the real production domain and record it; it can never change after the first release.
- Mobile environment: absolute apiBaseUrl, keycloakClientId `jordylab-mobile`. Add an HTTP interceptor that prefixes relative `/api/` URLs when running natively (Capacitor.isNativePlatform()), plus a URL helper/pipe for <img> artwork URLs that interceptors can't reach. Web behaviour must stay unchanged (tests prove both).
- Auth: login via system browser (@capacitor/browser) + Android App Link callback (@capacitor/app appUrlOpen), PKCE, implemented as a custom keycloak-js KeycloakAdapter inside libs/shared/auth — or a maintained native OIDC plugin if it's clearly better; justify. Request offline_access for biometric unlock.
- Biometrics: @capgo/capacitor-native-biometric (verify Capacitor 8 support) storing the offline token in the Android Keystore; fall back to password login on biometric change/failure.
- Share target: @capgo/capacitor-share-target (or Capawesome's; pick one, justify); a share-destination sheet filtered by role.
- Update check: on app start/resume call GET /api/mobile/releases/latest; compare versionCode; enforce minSupportedVersionCode.
- Install prompts (web only, not native): a shared install-prompt feature in libs/shared (UA-CH navigator.userAgentData with UA fallback for Android vs iOS Safari vs desktop), suppress beforeinstallprompt on Android, remember dismissals in localStorage (wrapped in try/catch). Add web app manifest + icons + apple-touch-icon for iOS home screen. Use Night Lab tokens + spartan/ui. Signal stores via /angular-signal-store, tests via /angular-test.

Backend (jordylab-be): new Modulith module `mobile` (use /new-module, /entity, /flyway-migration, /test-builder; /modularity-check at the end):
- MobileRelease entity (versionName, versionCode, releaseNotes, sha256, sizeBytes, minSupportedVersionCode, publishedAt, storageKey).
- GET /api/mobile/releases/latest (admin|guest); POST /api/mobile/releases/{id}/download-link → short-lived signed URL (e.g. HMAC token, 5 min) served by an unauthenticated GET that validates the token and streams the APK with Content-Type application/vnd.android.package-archive and Content-Disposition attachment; POST /api/mobile/releases (CI only, via a dedicated Keycloak service-account role, like gamecatalog-scanner).
- Serve /.well-known/assetlinks.json (package name + release cert SHA-256 from config) at the public domain.
- CORS + Keycloak Web Origins: allow https://localhost for jordylab-mobile. Realm export: new public client jordylab-mobile (PKCE S256, redirect URI = App Link callback, offline_access allowed), offline session idle configured; revoke in spec 006 must also revoke offline sessions.
- Notifications (if clarify picks Ntfy): publish to the existing Ntfy with a Click header = App Link URL for sign-up-pending and briefing-ready events (listen to Modulith events; no direct cross-module calls).

CI (.github/workflows/android-release.yml): on tag `mobile-v*` — setup Bun/Node 22+, JDK, Android SDK; nx build jordylab --configuration=mobile; npx cap sync android; ./gradlew assembleRelease with keystore from GitHub secrets (base64); compute sha256; publish via POST /api/mobile/releases. Document keystore creation + offline backup in quickstart.md.

Tests: Vitest for interceptor/URL helper/platform detection/install-prompt rules; MockMvc + Testcontainers Keycloak for release endpoints and role gating (pending → 403, guest → 200 latest, expired/tampered download token → 403); a manual device test checklist in quickstart.md (install from browser, login round-trip, biometric, share, update).

Stop and report before: generating the release keystore, choosing the final appId, or changing the realm export.
```

---

## 4. Then `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

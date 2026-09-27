# 007 Mobile App: Research & Sanity Check

Date: 2026-09-27. Checked against the `jordylab-fe` repo state and the live vendor docs.

## Decisions so far (from Jordy)
- Framework: **Capacitor** (current stable **v8**; v9 is in pre-release).
- Native features wanted: **push notifications, biometric unlock, share to JordyLab**.
- Web UI updates: **bundled in the APK**. CI builds a signed APK per release, and the app prompts to update.
- APK download: **approved users only** (admin + guest from spec 006).
- iPhone: **PWA "Add to Home Screen"** only. No native iOS build.

## TL;DR: sanity check

| # | Assumption | Verdict | Impact |
|---|---|---|---|
| 1 | Install an APK from my web app without the Play Store | **Works**, with friction and a 2027 change | The user has to allow "Install unknown apps" for their browser once. See §1 for Google's developer verification |
| 2 | iPhone is much harder | **Correct.** EU Web Distribution needs Apple authorisation, notarisation and a paid account, so it's not realistic for a friends app | iOS gets a home-screen web app (PWA). Apple kept PWAs in the EU after its 2024 reversal |
| 3 | A supported browser gets an install popup | **Partly.** Android: we show our own dialog (browsers have no native "install APK" prompt). iOS Safari has **no** programmatic install prompt | We build an in-app banner. On iOS it shows the steps (Share → Add to Home Screen) |
| 4 | Capacitor = my Angular app as an app | **Yes, but 3 code changes are needed** | Relative `/api` URLs, Keycloak login and CORS all break inside the app. See §3 |
| 5 | Push notifications "just work" | **Not without a Google dependency.** Capacitor's official push plugin uses Firebase Cloud Messaging. I found no maintained Capacitor plugin for UnifiedPush (the ntfy-based, Google-free standard) | Decision needed. See §4 |
| 6 | Biometric unlock | **Feasible**, but it needs a long-lived Keycloak token | See §5 |
| 7 | Share to JordyLab | **Feasible** with a maintained Capacitor 8 plugin | Needs a decision on where shared content goes. See §6 |

## 1. Android sideloading in 2026–2027

- **Sideloading stays allowed.** Google's developer verification goes live on **30 Sep 2026 in Brazil, Indonesia, Singapore and Thailand**, and **globally (including Belgium) in 2027** on certified Android devices.
- From then on, apps from **unverified** developers can only be installed through an "advanced flow" with a **24-hour lock** and extra steps. ADB installs stay exempt.
- **Fix: a free "Limited Distribution" account.** No government ID, no fee, **up to 20 devices**, unlimited apps. That fits a friends group. (A Full Distribution account costs $25 and needs ID.)
  - *To verify before 2027:* how devices get enrolled in the 20-device list.
- **Signing:** create one release keystore and keep it forever (as a GitHub Actions secret plus an offline backup). **If you lose it, every friend has to uninstall and reinstall**, because Android refuses an update signed with a different key. `versionCode` must go up with every release.
- **What users see:** the browser downloads the `.apk`. Android asks once to allow installs from that browser, and Play Protect may show an "unknown app" scan. JordyLab asks for no sensitive permissions (SMS, accessibility), so Play Protect shouldn't block it.

## 2. iPhone
- **EU Web Distribution** (DMA): Apple has to authorise the developer, and the app must be notarised and installed from a domain registered in App Store Connect (iOS 17.5+). It needs a paid Apple Developer account, and historically an established developer with 1M+ EU installs. Not realistic here.
- **Home Screen web apps are still supported in the EU.** Apple dropped its 2024 plan to remove them.
- There's **no** `beforeinstallprompt` on iOS, so we detect iOS Safari and show a short instruction sheet.
- Future option (out of scope): TestFlight. It needs the paid account, builds expire after 90 days, and external testers need beta review.

## 3. What breaks when the Angular app runs inside Capacitor

Inside the app, the page origin is `https://localhost` (Capacitor's default `androidScheme: https`, `hostname: localhost`).

1. **API URLs.** Stores use relative URLs (`'/api/fna/portfolio'`), which inside the app would point at the phone itself. Fix: a mobile build configuration with an absolute `apiBaseUrl`, and an HTTP interceptor that prefixes `/api/`. **`<img src="/api/...">` artwork URLs bypass interceptors**, so they need a URL pipe or helper as well.
2. **CORS.** The backend and the Keycloak client's Web Origins must allow `https://localhost`.
3. **Keycloak login.** Opening the login page inside the WebView breaks (Capacitor sends external URLs to the system browser, and keycloak-js loses its state). `server.url` and `server.allowNavigation` are documented as *"not intended for production"*. The recommended pattern:
   - A separate **public client `jordylab-mobile`** (PKCE).
   - The login opens in the **system browser / Custom Tab** (`@capacitor/browser`).
   - The redirect comes back through an **Android App Link** (`https://<domain>/mobile/callback`, verified with `/.well-known/assetlinks.json` containing the signing-cert SHA-256) or a custom scheme, caught with `@capacitor/app` `appUrlOpen`.
   - Wire it in either through a **custom `KeycloakAdapter`** for keycloak-js (supported extension point) or a native OIDC plugin. The plan phase picks one. Community `keycloak-capacitor` packages exist but are small; verify maintenance before using one.

## 4. Push notifications: decision needed

| Option | How | Pros | Cons |
|---|---|---|---|
| **A. Ntfy app + deep links** | The backend publishes to your existing Ntfy with a click URL. Tapping the notification opens the JordyLab app through an App Link | Google-free, reuses Ntfy, almost no app code | Users need the ntfy app plus a subscription. Fine for you, clunky for friends |
| **B. FCM** (`@capacitor/push-notifications`) | Firebase project, `google-services.json`, backend sends through the FCM HTTP v1 API | Standard, works for friends out of the box | Google dependency (conflicts with your de-Googled goals); doesn't work on de-Googled phones |
| **C. UnifiedPush plugin (own)** | Write a small Capacitor plugin around the UnifiedPush Android connector. Ntfy acts as the distributor | Google-free, native in-app push | Custom native Kotlin work. Users still need a distributor app (ntfy) |

Which notifications? The candidates found so far are admin-only: "FNA briefing ready" and "new sign-up pending" (spec 006). Guests have no notification use case yet. **Suggestion: A for now.** It meets the need with near-zero app code, and B or C can follow once guests need pushes.

## 5. Biometric unlock
- Plugin: `@capgo/capacitor-native-biometric` (maintained for Capacitor 8; stores credentials in the Android Keystore behind a biometric prompt). Alternative: `@aparajita/capacitor-biometric-auth`.
- Pattern: after the first login, store a **Keycloak offline token** (`offline_access` scope) behind biometrics. Opening the app → fingerprint → refresh → access token. Without an offline token, Keycloak's default SSO session (30 min idle / 10 h max) would force a password login most days.
- Realm changes: allow `offline_access` for `jordylab-mobile` and set an offline-session idle time (the Keycloak default is 30 days). Logout and revoke (spec 006) must also revoke offline sessions.

## 6. Share to JordyLab
- Plugin: `@capgo/capacitor-share-target` (releases 8.0.x, Capacitor 8) or Capawesome's Share Target. It registers an Android `ACTION_SEND` intent filter for text/URLs.
- Destination: **open question.** A candidate:
  - Admin: "Save to FNA" (an article URL for the next briefing; needs a new FNA endpoint).
  - Everyone: "Ask the catalog" with the shared text prefilled (e.g. a Steam store link → "do I own this / is it co-op?").

## 7. Project layout & build
- Capacitor v8 needs Node 22+, Android Studio 2025.2.1+ and Android SDK API 24+ (the latest is Android 16 / API 36). Only a JDK and the SDK are needed in CI (GitHub Actions `ubuntu-latest` + `setup-java` + `android-actions/setup-android`).
- Suggested Nx layout: a new app **`apps/jordylab-mobile`** holding `capacitor.config.ts` + `android/`, with `webDir` pointing at the `jordylab` build output from a new `mobile` configuration (absolute `apiBaseUrl`, `keycloakClientId: jordylab-mobile`). The web app code stays shared.
- There is **no PWA setup yet** (no manifest, no service worker). iOS home-screen support needs at least a web app manifest, icons and `apple-touch-icon`. A service worker is optional.
- There is no frontend Dockerfile and no build CI yet. The APK pipeline is the first frontend build workflow.

## 8. Dependencies on other specs
- **006 Settings/Users:** the `admin`/`guest` roles and approved-only access are needed for the gated APK download and role-based share destinations.
- **Production deployment (OVHcloud):** a public HTTPS domain is required for API calls from the phone, the Keycloak redirect, App Links (`assetlinks.json`) and the APK download. Not in scope of 007, but a prerequisite for testing on a real phone outside the LAN.

## Sources
- [Capacitor docs](https://capacitorjs.com/docs) · [Environment setup](https://capacitorjs.com/docs/getting-started/environment-setup) · [Config (`server.url` "not intended for production")](https://capacitorjs.com/docs/config) · [Push Notifications plugin](https://capacitorjs.com/docs/apis/push-notifications)
- [Google: Understanding Android developer verification](https://support.google.com/android-developer-console/answer/16561738?hl=en) · [Android Authority: timeline](https://www.androidauthority.com/android-sideloading-changes-timeline-3679204/) · [Help Net Security](https://www.helpnetsecurity.com/2026/03/31/android-developer-verification-requirement/)
- [Apple: Changes for apps in the EU](https://developer.apple.com/support/dma-and-apps-in-the-eu/) · [TechCrunch: web distribution](https://techcrunch.com/2024/03/12/apple-to-allow-web-distribution-for-ios-apps-in-latest-dma-tweaks/amp)
- [Keycloak JavaScript adapter (custom adapters, native/deep-link guidance)](https://www.keycloak.org/securing-apps/javascript-adapter) · [Ionic Forum: Keycloak login in Capacitor](https://forum.ionicframework.com/t/keycloak-login-opens-in-external-browser-which-breaks-the-flow/247846) · [keycloak-capacitor (npm)](https://www.npmjs.com/package/keycloak-capacitor)
- [UnifiedPush: ntfy distributor](https://unifiedpush.org/users/distributors/ntfy/)
- [@capgo/capacitor-native-biometric](https://github.com/Cap-go/capacitor-native-biometric) · [aparajita/capacitor-biometric-auth](https://github.com/aparajita/capacitor-biometric-auth)
- [@capgo/capacitor-share-target](https://github.com/Cap-go/capacitor-share-target) · [Capawesome Share Target](https://capawesome.io/docs/sdks/capacitor/share-target/)
- [Capawesome: Appflow shutdown alternatives](https://capawesome.io/alternatives/ionic-appflow/) (live updates were considered and not chosen)

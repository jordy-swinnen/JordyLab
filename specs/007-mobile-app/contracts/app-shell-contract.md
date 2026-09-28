# Contract — App Shell (Frontend Platform Concerns)

What `libs/shared/platform` and the app shell must do, independent of any specific backend endpoint (spec FR-005–
FR-010; research D5). None of this exists in the codebase today — see research §2.2.

## Base-URL interceptor & artwork URL helper

- New `HttpInterceptorFn` (same shape as the existing `authInterceptor`), registered **after** `authInterceptor`:
  when `Capacitor.isNativePlatform()` is `true`, rewrites any request whose URL starts with `/api/` to
  `${apiBaseUrl}${url}`, where `apiBaseUrl` comes from the `mobile` build configuration's environment file. When
  `false` (every web build — `production`, `development`, or a future non-native `mobile`-web variant), the request
  passes through unchanged.
- A new `artworkUrl` pipe applies the exact same resolution logic for `<img [src]="artworkUrl(game.coverUrl)">`
  bindings, since interceptors only see requests made through `HttpClient` (research §2.2).
- **Web behavior must be provably unchanged**: both paths get a Vitest test asserting the interceptor/pipe are
  no-ops when `isNativePlatform()` is `false` and prefix correctly when `true`.

## Platform detection

- A `platform` signal in `libs/shared/platform/api`: `'web-android' | 'web-ios' | 'web-desktop' | 'native-android'`.
- Native: `Capacitor.isNativePlatform()`.
- Web sub-platforms: `navigator.userAgentData` (Client Hints) when available, falling back to a UA-string check for
  browsers that don't implement UA-CH (notably Safari) — Android vs iOS/iPadOS vs everything else (desktop).

## Install prompts (web only — never inside the native app, FR-005)

| Platform      | Prompt                                                                                          | Dismissal                          |
|-----------------|----------------------------------------------------------------------------------------------------|---------------------------------------|
| `web-android`   | "Get the JordyLab app" dialog: download button + 3 steps for allowing the install (spec US1-1)     | "Not now" → 30-day per-device `localStorage` dismissal (FR-006); user menu still offers the entry |
| `web-ios`       | One-time sheet: Share → Add to Home Screen (spec US7-1)                                            | Shown once; no re-prompt once the site is already running as a home-screen app (US7-3) |
| `web-desktop`   | No automatic popup — only a "Get the Android app" entry in the user menu showing a QR code to the download page (spec US1-6) | N/A |
| `native-android`| Nothing — all of the above are suppressed entirely (FR-005)                                        | N/A |

- On `web-android`, `beforeinstallprompt` is captured and `event.preventDefault()`'d unconditionally, whether or not
  the custom dialog is currently shown (FR-007) — this must hold even after "Not now" (spec Edge Cases).
- A pending or logged-out visitor never sees any install prompt (spec scenario 4) — the store checks
  `AuthService.isApproved` (derived from `isAdmin || isGuest`) before deciding to show anything.

## Web app manifest (iOS home-screen support, FR-008)

- First manifest in this repo: `manifest.webmanifest` (name, short_name, icons, `display: standalone`,
  `start_url`), plus `<link rel="apple-touch-icon">` and the `apple-mobile-web-app-*` meta tags Safari still requires
  independent of the manifest spec. No service worker is required by this feature (out of scope: offline mode).
- Detecting "already running as a home-screen app" (to suppress the sheet, US7-3): `navigator.standalone === true` on
  iOS Safari (the only reliable signal there; `display-mode: standalone` media query is the cross-browser equivalent,
  checked as a fallback).

## Update-check store

- On app start and on resume (Capacitor's `App.addListener('resume', ...)`), calls
  `GET /api/mobile/releases/latest?installedVersionCode={current}` (see
  [mobile-releases-api.md](mobile-releases-api.md)).
- `updateRequired: true` → renders a blocking screen, no dismissal, no other UI reachable (spec US3-2).
- `updateAvailable: true` (and not required) → a dismissible banner/toast with version + release notes; tapping
  triggers the signed download-link flow, same as the web install dialog's download button.
- The installed app's own `versionCode` is read from the native build (Capacitor exposes it via `@capacitor/app`'s
  `App.getInfo()`), never hardcoded in TypeScript, so it can't drift from what was actually built.

## App Link → in-app screen routing (notification taps, research D9)

The Android app's manifest declares an `autoVerify` intent filter for `https://{PRODUCTION_DOMAIN}/mobile/*`
(domain pending feature 008). `@capacitor/app`'s `appUrlOpen` listener parses the `screen` query param and routes
in-app — this table is the contract between the backend's notification click-URLs (data-model.md Events table) and
the frontend router:

| `screen` value     | In-app route                          | Triggered by (spec)                          |
|----------------------|------------------------------------------|--------------------------------------------------|
| `settings-users`     | `/settings/users`                         | US6-1 — new sign-up pending notification tap      |
| `fna-briefing`        | `/fna/briefing` (or the app's existing briefing route) | US6-2 — briefing-ready notification tap  |

The same `appUrlOpen` listener also handles the **login callback** (`/mobile/callback`, research D2) — the router
must distinguish the two by path, not just presence of the App Link, before this table's `screen`-based dispatch
applies.

## Share target landing (spec US5, research D4/D6)

- `@capgo/capacitor-share-target`'s `shareReceived` event carries the shared text/URL. A landing screen offers
  role-filtered destinations (`ShareDestination`, spec Key Entities): "Ask the catalog" (everyone — opens
  `gamecatalog`'s existing chat UI with the text prefilled) and "Save to FNA" (admin only — calls the new
  `POST /api/fna/articles/manual`, [access-matrix.md](access-matrix.md)).
- If the user isn't logged in when a share arrives, the login flow (D2) runs first and the share payload is held in
  memory until it completes (spec US5-4) — not persisted across an app restart, since a restart before completing
  login means the share attempt is abandoned by the OS anyway.

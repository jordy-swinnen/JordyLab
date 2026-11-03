# Contract: PWA Manifest, Service Worker and Install Flow

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02 · Spec: FR-001–FR-006, US1

## Web App Manifest

**Location**: `apps/jordylab/public/manifest.webmanifest` (copied to build root; linked from `index.html`).

```json
{
  "name": "JordyLab",
  "short_name": "JordyLab",
  "id": "/",
  "start_url": "/",
  "scope": "/",
  "display": "standalone",
  "background_color": "#131314",
  "theme_color": "#131314",
  "icons": [
    { "src": "icons/icon-192.png", "sizes": "192x192", "type": "image/png", "purpose": "any maskable" },
    { "src": "icons/icon-512.png", "sizes": "512x512", "type": "image/png", "purpose": "any maskable" }
  ],
  "share_target": {
    "action": "/share",
    "method": "GET",
    "params": { "title": "title", "text": "text", "url": "url" }
  }
}
```

Rules:

- Served with `Content-Type: application/manifest+json` (prod nginx already has the `.webmanifest` location; the e2e static server serves the file as-is — the Playwright journey asserts the type).
- `theme_color`/`background_color` follow the 016 default theme (Content Led `#131314`); changing them is a 016 concern, not this feature's.
- `scope` stays `/`: the fna/gamecatalog harness apps never register a SW or claim installability.

## Service worker (`apps/jordylab/public/sw.js`)

- `install` → `self.skipWaiting()`; `activate` → `clients.claim()`.
- A `fetch` listener that **never calls `respondWith`** — registered to satisfy Chrome's automatic-install-prompt criterion (research D1) while caching nothing.
- MUST NOT intercept (i.e. must not `respondWith`) for any request — in particular `/api/**`, `/libbot` SSE, and `/auth/**` are structurally network-only (FR-002).
- Registered from the `jordylab` app bootstrap at `/sw.js`, after the Angular bootstrap settles; registration failure is logged, never fatal to the app.
- No `push`/`sync` handlers (ntfy is the notification channel; offline is out of scope).

## Install surfaces (`InstallPromptStore` + components)

| Surface | Who sees it | Trigger | Behaviour |
|---|---|---|---|
| Bottom sheet (Android) | `web-android`, approved role, not standalone, not dismissed < 30 days | `beforeinstallprompt` captured | "Install app" calls `event.prompt()`; "Not now" stores 30-day dismissal |
| Bottom sheet (iOS) | `web-ios`, approved role, not standalone, not dismissed < 30 days | platform detection | Instruction sheet (Share → Add to Home Screen); "Got it" = same 30-day dismissal |
| `Settings → App` card | `web-android`/`web-ios`, not standalone | always available | "Install on this phone" — `promptInstall()` when the event is captured, otherwise per-browser fallback instructions + the uninstall-old-APK note |
| Desktop sidebar entry | `web-desktop` | always | QR code of the site origin + install/uninstall instructions |

- `beforeinstallprompt` is **captured**, never `preventDefault`-suppressed for blocking purposes (the old FR-007 suppression is deleted; calling `preventDefault` only to stop the automatic banner is allowed *after* the event is stashed, so the sheet, not Chrome's mini-infobar, carries the UX).
- Standalone check: `matchMedia('(display-mode: standalone)')` or iOS `navigator.standalone === true` (existing `isRunningStandalone()` logic, kept).
- Pending/logged-out visitors see no install surface (unchanged `hasAppRole` gate).
- Dismissal key `jordylab.install-prompt.dismissed-until` keeps its name and 30-day semantics — existing devices stay suppressed across the cutover.

## Acceptance mapping

- FR-001 manifest fields ⇔ journey asserts each field above.
- FR-002 SW exclusions ⇔ the handler is a no-op by construction; journey asserts `sw.js` 200 + registration.
- FR-003/004/005/006 ⇔ unit tests on `InstallPromptStore` (capture, standalone, 30-day dismissal, role gate) + the surfaces table above.

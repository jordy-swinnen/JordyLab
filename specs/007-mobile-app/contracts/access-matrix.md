# Contract — Access Matrix (Mobile)

The authorization contract this feature adds to the existing deny-by-default matrix (spec FR-002/FR-004; research
D8/D12/D13). Everything not listed here is unaffected by this feature — see 006's
[access-matrix.md](../006-settings-module/contracts/access-matrix.md) for the routes that already exist.

## Backend route → role matrix (new routes only)

| Route                                              | Role                              | Notes                                                                                          |
|-----------------------------------------------------|-------------------------------------|--------------------------------------------------------------------------------------------------|
| `GET /api/mobile/releases/latest`                    | `admin` or `guest`                  | update-check call (FR-011); also usable to render the admin's release list in Settings          |
| `POST /api/mobile/releases/{id}/download-link`       | `admin` or `guest`                  | mints a signed, 5-minute download token (D8); pending/logged-out → 401/403 (spec scenario 4)     |
| `GET /api/mobile/download/{token}`                    | **permitAll**, token-validated      | public route — Android's download manager can't carry a bearer token; invalid/expired token → `403 DOWNLOAD_LINK_INVALID` (D8) |
| `POST /api/mobile/releases`                           | `mobile-release-publisher` (new role, service account only) | CI-only (FR-004); STOP-AND-REPORT gate before the client/role exist (D13)                        |
| `GET /.well-known/assetlinks.json`                    | **permitAll**                       | Android App Link verification; must be served with no auth, no redirects (research §1.2)         |
| `POST /api/fna/articles/manual`                       | `admin`                             | new `fna`-owned endpoint for "Save to FNA" (D6) — not a `mobile` route, listed here for completeness since it's part of this feature |

The scanner device flow, the settings user matrix, and every existing route are unchanged.

## Standard error shapes (new reasons only)

| Status | Body                                          | Meaning                                                    |
|--------|------------------------------------------------|---------------------------------------------------------------|
| 400    | `{"reason": "VERSION_CODE_NOT_MONOTONIC"}`      | publish call's `versionCode` <= current latest                |
| 400    | `{"reason": "SIGNING_CERT_MISMATCH"}`           | uploaded APK's signing-cert SHA-256 doesn't match config (D7) |
| 403    | `{"reason": "DOWNLOAD_LINK_INVALID"}`           | expired, tampered, or unknown download token (D8)             |

All other error shapes (401 missing/expired token, 403 role-denied, resource-server defaults) are unchanged from the
existing contract.

## Keycloak clients & roles (STOP-AND-REPORT gate — research D13)

Not yet applied — proposed here for review before the realm export is touched:

| Client               | Type         | Purpose                                                             | Key config                                                                                     |
|------------------------|--------------|-------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `jordylab-mobile`      | public       | the Android app's login client (D2)                                    | PKCE S256, redirect URI = `https://{PRODUCTION_DOMAIN}/mobile/callback` (blocked on feature 008), `offline_access` allowed, `standardFlowEnabled=true`, `directAccessGrantsEnabled=false` |
| `mobile-release-ci`    | confidential | CI's identity for `POST /api/mobile/releases` (FR-004)                 | `serviceAccountsEnabled=true`, secret in GitHub Actions secrets, granted realm role `mobile-release-publisher` |

New realm role: `mobile-release-publisher` (granted only to `mobile-release-ci`'s service account).

## Frontend platform contract

- `libs/shared/platform` exposes a `platform` signal (`'web-android' | 'web-ios' | 'web-desktop' | 'native-android'`)
  computed from `Capacitor.isNativePlatform()` + `navigator.userAgentData`/UA-string detection (research D5).
- The install-prompt store shows **at most one** prompt, chosen by `platform`, and never inside `native-android`
  (FR-005): the Android APK dialog (`web-android`), the iOS Add-to-Home-Screen sheet (`web-ios`), or nothing
  automatic — only the user-menu QR entry (`web-desktop`).
- Dismissal ("Not now") is stored per-device in `localStorage` (wrapped in try/catch) with a 30-day expiry (FR-006);
  the user-menu "Get the Android app" entry always remains available regardless of dismissal state.
- On Android, the browser's own `beforeinstallprompt` event is captured and never shown (`event.preventDefault()`),
  so only the custom APK dialog appears (FR-007).

# Research — Mobile App (Android via Capacitor, iOS Home-Screen Web App)

Date: 2026-09-28. Baseline: branch `007-mobile-app`, verified against live vendor docs and the actual codebase
(backend and frontend maps below). This carries forward `specs/_drafts/007-mobile-app/research.md` (the sanity-check
draft) — its Android-sideloading, iOS, and library findings are re-verified here rather than repeated; §1 below only
records what changed or was newly confirmed during planning.

## TL;DR of new findings

| # | Question                                                                 | Verdict |
|---|---------------------------------------------------------------------------|---------|
| 1 | Is Capacitor 8 still the current stable (not superseded)?                | **Yes — 8.5.1**, released within the last week of the plan date (D1) |
| 2 | Do the two chosen Capgo plugins actually support Capacitor 8?             | **Yes**, both track Capacitor's major version and are current at v8.x (D3/D4) |
| 3 | Is a custom `keycloak-js` adapter really a supported extension point?     | **Yes** — Keycloak's own docs list Capacitor by name as a target environment for custom adapters (D2) |
| 4 | Does the codebase already have *any* absolute-API-URL or PWA scaffolding? | **No to both.** All API calls use relative paths relying on same-origin/dev-proxy; there is no manifest, service worker, or `@angular/pwa` anywhere (D5) |
| 5 | Does a Ntfy sender already exist from spec 006?                          | **No** — only unused config keys (`jordylab.settings.notifications.ntfy.*`); no `NtfyClient`/notifier class exists in the codebase (D9/D10) |
| 6 | Does regular Keycloak logout revoke offline (biometric-unlock) tokens?   | **No** — offline tokens survive normal logout/session-end; they need an explicit consent/offline-session revocation call (D12) |
| 7 | Does any module today make a real cross-module call or publish events?  | **No** — Spring Modulith's event infrastructure exists (`event_publication` table, `BaseEntity extends AbstractAggregateRoot`) but is completely unused; no module has a public facade either (D9) |
| 8 | Where should the signed APK files live?                                  | **Filesystem volume**, mirroring gamecatalog's `artwork.dir` config pattern — no object storage or DB blob exists anywhere to reuse (D7) |

## 1. Verified external facts

### 1.1 Capacitor & plugins

- **`@capacitor/core` 8.5.1** is current (npm, checked 2026-09-28); `@capacitor/cli` 8.5.1, `@capacitor/ios` 8.5.2. No
  v9 exists yet — confirms the draft's "v8 stable, not the v9 pre-release" instruction.
- **`@capgo/capacitor-native-biometric`**: major version tracks Capacitor's; current release **8.6.8/8.7.0** line,
  example app migrated to Capacitor 8.5 and the iOS `UIScene` lifecycle. Confirmed compatible.
- **`@capgo/capacitor-share-target`**: same version-tracking convention; current releases in the **8.0.5x** line,
  actively maintained, `addListener('shareReceived', ...)` API confirmed. Confirmed compatible.
- **Capawesome's Share Target plugin** (alternative): also current for Capacitor 8, equally viable — kept as the
  documented fallback (D4) rather than rejected outright.
- **`android/variables.gradle`**: Capacitor's `minSdkVersion` floor has risen with each major version (22 → 23 across
  Capacitor 4–7); it is an ordinary Gradle variable, safely overridable to **29** (Android 10, per spec FR-018) —
  confirmed this is a supported, documented override, not a hack.

### 1.2 Keycloak custom adapter & App Links

- Keycloak's own JavaScript-adapter docs describe the `KeycloakAdapter` interface (`login`, `logout`, `register`,
  `accountManagement`, `redirectUri`) as an explicit extension point for "environments... not supported by default,
  such as Capacitor" — this is a documented, intended use, not a workaround (D2).
- **Digital Asset Links / `assetlinks.json`**: must be served at exactly `/.well-known/assetlinks.json`, `Content-Type:
  application/json`, no redirects, no auth, containing `sha256_cert_fingerprints` (colon-separated hex, one entry per
  signing cert). Confirms the well-known-endpoint design in the plan (permitAll, outside `/api/**`).
- **Offline tokens vs. logout**: confirmed via Keycloak forum/docs — a normal logout or session end does **not**
  revoke an `offline_access` grant; the offline token is not subject to SSO session idle/expiry and survives both
  logout and server restart. Revoking it requires either deleting the specific offline session or revoking the
  client consent (`DELETE /admin/realms/{realm}/users/{id}/consents/{clientId}`). This directly affects FR-013 — see
  D12.

## 2. Codebase baseline (as mapped on 2026-09-28, via `architect` exploration)

### 2.1 Backend (`jordylab-be`)

- **No `apiBaseUrl`, no absolute-URL mechanism anywhere.** Every HTTP call in `libs/*/api` uses a relative path (e.g.
  `this.#http.get('/api/gamecatalog/games', ...)`); dev routing is a Nx dev-server proxy
  (`apps/jordylab/proxy.conf.json` → `http://localhost:8080`); production presumably relies on same-origin serving
  behind Traefik. A Capacitor app has neither a dev proxy nor a shared origin with the backend — this is genuinely new
  plumbing, not a wire-up (confirms the draft's §3 risk analysis).
- **No PWA/manifest/service-worker setup at all** — zero hits for `manifest.webmanifest`, `manifest.json`,
  `@angular/pwa`, or any service-worker config. FR-008 (iOS home-screen) and the Android install-prompt suppression
  (FR-007, which needs `beforeinstallprompt` to exist in order to suppress it) both start from nothing.
- **Keycloak realm** (`compose/keycloak-realm-export.json`): confirmed roles are `admin`, `guest`,
  `gamecatalog-scanner` only — **`jordylab-be/AGENTS.md`'s Security section describing a `jordylab-user` role is
  stale**; the actual `SecurityConfig.java` gates `ingest/client` on `hasRole("admin")`. Trust the realm export +
  `SecurityConfig.java`, not that AGENTS.md paragraph, when building `mobile`'s matchers.
- `jordylab-host` client (frontend): public, PKCE via keycloak-js client config (no server-side PKCE attribute
  override), `redirectUris`/`webOrigins` limited to the three dev ports — nothing for a Capacitor origin or an App
  Link redirect today.
- A **confidential client `jordylab-backend`** already exists (`serviceAccountsEnabled=true`), used today by
  `KeycloakAdminClient` for the settings module's own Keycloak Admin API calls — i.e. *backend calling out to
  Keycloak*. This is the closest existing precedent for a service-account client, but it is the opposite direction
  from what FR-004 needs (an *external CI caller authenticating into the backend*) — a genuinely new client is
  required (D13).
- **`SecurityConfig.java`** centralizes all authorization (no per-controller `@PreAuthorize`), deny-by-default,
  explicit `requestMatchers` per route group, realm roles mapped to `ROLE_*` via a custom
  `jwtAuthenticationConverter()`. `/api/mobile/**` matchers slot into this exact style.
- **Ntfy**: only `SettingsProperties.Notifications.Ntfy(baseUrl, topic, token)` exists, with a Javadoc referencing "
  the notifier" that was never built — no `NtfyService`, no HTTP client, no message format, anywhere in the codebase.
  This must be built from scratch (D9/D10) — 006 speced the *intent*, not the implementation.
- **No binary/blob storage precedent** except gamecatalog's artwork **filesystem volume**
  (`jordylab.gamecatalog.artwork.dir`, default `/var/jordylab/artwork`, with a `max-bytes` cap) — no DB BLOB column
  anywhere, no object-storage wiring (S3/MinIO/etc.) in the repo at all. This is the closest and only precedent for
  APK storage (D7).
- **No module has a public `{Module}Facade.java`** despite the convention being documented in `jordylab-be/AGENTS.md`
  — `fna`, `gamecatalog`, and `settings` all have empty or config-only root packages. **No module publishes or listens
  to a Modulith application event either**, though the infrastructure (`event_publication` table, `BaseEntity extends
  AbstractAggregateRoot`) exists and is idle. `mobile`'s notification listeners are the first real use of this
  infrastructure (D9).
- **User status/revocation** (`settings.service.KeycloakUserAdministrationService`): status is derived live from
  Keycloak (`enabled` + `realmRoles`), never stored; `revoke()` removes `guest` and ends the user's regular sessions —
  it does **not** touch offline consents/sessions today (gap relevant to D12).
- **Closest "serve a generated artifact" precedent**: `gamecatalog.service.ClientService` renders a **text** Python
  template from a classpath resource and returns it as a `String` — not a binary stream. `mobile`'s download endpoint
  needs real binary streaming (`Content-Type: application/vnd.android.package-archive`,
  `Content-Disposition: attachment`), which has no precedent to mirror beyond the general RestController/response
  pattern.
- **CI**: only two GitHub Actions workflows exist today (`hook-tests.yml`, `claude-pr-review.yml`), neither a
  build/release pipeline. `android-release.yml` is the first of its kind — no naming/trigger convention to inherit
  beyond general repo style (kebab-case, minimal `permissions:`, a `concurrency` group if re-triggerable).

### 2.2 Frontend (`jordylab-fe`)

- **Nx apps**: `apps/jordylab` (host, `/`), `apps/fna` (dev harness, `/fna`), `apps/gamecatalog` (dev harness,
  `/games`). Each app's `project.json` build target has exactly two configurations — `production` and `development`
  (swapped via `fileReplacements`). **No `mobile` configuration exists anywhere** — it must be added new to
  `apps/jordylab/project.json` with its own `environment.mobile.ts` (absolute `apiBaseUrl`,
  `keycloakClientId: 'jordylab-mobile'`).
- **Auth** (`libs/shared/auth`): `keycloak-js` (not `keycloak-angular`) wrapped by `AuthService` behind signals
  (`isAuthenticated`, `username`, `token`, `roles`, computed `isAdmin`/`isGuest`). Init uses `onLoad: 'check-sso'`,
  `pkceMethod: 'S256'`, a `silentCheckSsoRedirectUri`. Redirect origin is read from `window.location.origin` at
  runtime — inside the Capacitor app this origin is the WebView's own scheme (`https://localhost` with Capacitor's
  default `androidScheme: https, hostname: localhost`), not a real HTTPS domain, which is exactly why login must
  happen in the **system browser**, not the WebView (D2 confirms the mechanism).
- **Artwork URLs**: `coverUrl()`/`bannerUrl()` in `gamecatalog-api.service.ts` just pass through whatever
  relative/absolute string the backend returns — no client-side URL-building pipe exists. Same base-URL problem as
  API calls; the interceptor fix (D5) must also cover `<img>` bindings via a small pipe, since Angular
  `HttpInterceptor`s only see requests made through `HttpClient`.
- **Signal store pattern** (`libs/gamecatalog/api/game-library.store.ts`): private writable `#signals`, public
  `readonly` via `.asReadonly()`, `computed()` for derived state, RxJS `Subject`s piped with
  `debounceTime`/`switchMap`/`takeUntilDestroyed()` inside the constructor for reactive API-driven state. The new
  install-prompt and update-check stores in `libs/shared/platform/api` follow this exact shape.
- **Tooling gotchas**: `eslint.config.mjs`'s `type:app` dependency constraint hardcodes the scope list
  (`scope:fna|gamecatalog|settings|shared`) — `scope:platform` (or reusing `scope:shared`) must be added or the host
  app can't depend on the new lib; `tsconfig.base.json` needs the new lib's paths.

## 3. Decisions

Each: Decision / Rationale / Alternatives considered.

- **D1 — Pin Capacitor to the current 8.5.1 line, not v9.** Rationale: matches the user's explicit instruction and the
  verified npm state (§1.1); the app id and signing key are permanent once the first release ships, so starting on an
  unstable major would be reckless. Alternatives: wait for v9 (rejected — no ship date, blocks the whole feature);
  pin an older 8.x (rejected — no reason to, nothing in the plan needs a specific patch below latest).

- **D2 — Login via a custom `keycloak-js` `KeycloakAdapter` inside the existing `libs/shared/auth`, not a native OIDC
  plugin.** The adapter's `login()` opens `@capacitor/browser` (Custom Tab / SFSafariViewController-equivalent) with
  the standard Keycloak auth URL (PKCE S256); the redirect comes back through an Android **App Link**
  (`https://{PRODUCTION_DOMAIN}/mobile/callback` — domain pending feature 008, D13) caught by `@capacitor/app`'s
  `appUrlOpen`, which hands the authorization code back to keycloak-js to complete the code exchange. `offline_access`
  is requested in the scope so biometric unlock has a long-lived token to protect (spec US4). Rationale: this is
  Keycloak's own documented extension point for exactly this environment (§1.2); it reuses the *entire* existing
  `AuthService` signal surface (`isAuthenticated`, `roles`, `isAdmin`/`isGuest`) that `fna`/`gamecatalog`/`settings`
  guards already depend on, so **no parallel auth implementation is needed for the native app** — only the adapter's
  transport changes. Alternatives: a native OIDC plugin (e.g. a Capacitor OAuth2 plugin) — rejected: none is
  Keycloak-endorsed, and it would require a second, diverging auth implementation (its own role-signal mapping, its
  own guards) purely to avoid ~150 lines of adapter code that Keycloak's docs describe as the intended path.

- **D3 — Biometric unlock via `@capgo/capacitor-native-biometric`.** Stores the `offline_access` refresh token behind
  a biometric prompt in the Android Keystore; app open → biometric check → token refresh → access token, with no
  password most days (spec SC-003, 7-day reopen with no password). Falls back to password login on biometric
  failure/cancel/enrollment change (FR-012). Alternative: `@aparajita/capacitor-biometric-auth` — not rejected on
  capability, kept as a documented fallback if Capgo's plugin shows instability during implementation; chosen
  primarily because pairing it with D4 (also a Capgo plugin) keeps the native-plugin vendor surface to one publisher.

- **D4 — Share target via `@capgo/capacitor-share-target`.** Registers an Android `ACTION_SEND` intent filter for
  text/URLs; a role-filtered destination sheet offers "Ask the catalog" (everyone) and "Save to FNA" (admin only,
  FR-014/FR-017). Rationale: actively maintained, current for Capacitor 8, simple single-event API sufficient for two
  destinations; pairing with D3's publisher (Capgo) reduces the number of distinct native-plugin build/Gradle
  configurations to reason about. Alternative: Capawesome's Share Target plugin — equally current and capable, kept
  as the documented fallback.

- **D5 — New `libs/shared/platform` lib owns everything that breaks inside a WebView.** An `HttpInterceptorFn` (same
  shape as the existing `authInterceptor`) prefixes relative `/api/...` URLs with an absolute `apiBaseUrl` **only**
  when `Capacitor.isNativePlatform()` is true (web behavior is untouched — both are covered by Vitest); a small
  `artworkUrl` pipe covers `<img>` bindings that interceptors can't reach, calling the same resolution logic. The lib
  also holds platform detection (native/Android/iOS/desktop, via `navigator.userAgentData` with a UA-string
  fallback), the install-prompt signal store (per-device 30-day dismissal in `localStorage`, wrapped in try/catch per
  the artifact-storage convention), the update-check store (`GET /api/mobile/releases/latest` on start/resume), and
  the web app manifest + icons for iOS home-screen support (FR-008) — none of which exist anywhere today (§2.2).
  Rationale: every one of these concerns is genuinely cross-cutting (used by the host app on both web and native, by
  every domain's `<img>` bindings) and none belongs to `fna`/`gamecatalog`/`settings`. Alternative: spread these
  across existing libs (rejected — `libs/shared/auth` already has a clear, narrower job; mixing in unrelated
  platform-detection concerns would violate the Container–Presentation/single-responsibility principle for what's
  meant to be one focused auth lib).

- **D6 — Share-to-JordyLab is NOT a `mobile`-module backend concern.** "Ask the catalog" opens the existing chat UI
  with shared text prefilled — pure frontend routing, zero backend change. "Save to FNA" needs exactly one new
  endpoint, added directly to the **`fna`** module (e.g. `POST /api/fna/articles/manual`, admin-only, queues the URL
  as an article candidate for the next daily briefing — FR-017), called straight from the Android app's share-sheet
  handler. Rationale: the share target is a native Android UI feature; brokering it through a `mobile` backend module
  would add a dependency edge (`mobile → fna`) for no reason — the app already talks to `fna`'s and `gamecatalog`'s
  existing APIs directly for everything else. Alternative considered (from the original tech-context draft): route
  "Save to FNA" through the `mobile` module — rejected once the actual data flow was worked out: there is nothing
  mobile-specific about "queue an article URL," and adding the extra hop would only exist to satisfy an assumption
  that all mobile-triggered actions must pass through the `mobile` module, which is not true.

- **D7 — Signed APK files live on a filesystem volume**, config key `jordylab.mobile.release.storage-dir` (pattern:
  `jordylab.gamecatalog.artwork.dir`), not a DB blob or object storage. Rationale: it is the only binary-storage
  pattern that exists anywhere in this codebase (§2.1) and this feature's release volume (a handful of files, single
  digit MB each, single-digit friends) has none of object storage's justifying scale. CI's publish call streams the
  APK to this path and records `storageKey` (relative filename) + `sha256` + `sizeBytes` on the `MobileRelease` row;
  the signing-cert fingerprint check (FR-003) happens at publish time by comparing the uploaded APK's cert SHA-256
  against `jordylab.mobile.release.signing-cert-sha256` (config, set once — see D13/D14) and rejecting the publish
  (`400 SIGNING_CERT_MISMATCH`) on mismatch, so CI itself fails loudly rather than serving a broken update.
  Alternative: object storage (S3/MinIO) — rejected, nothing wired today and it would be new infrastructure for a
  handful of small files; DB blob — rejected, no precedent and Postgres blob storage is a poor fit for streaming
  downloads through Spring MVC.

- **D8 — Download links are HMAC-signed, short-lived, single-purpose tokens — not stored rows.** Token payload:
  `releaseId`, requesting user's `sub`, `expiresAt` (issued with a **5-minute** TTL — a concrete default chosen here;
  the spec only commits to "within minutes," FR-002), HMAC-SHA256 signed with a server-held secret
  (`jordylab.mobile.download-link.secret`), base64url-encoded into the path of a **public** (`permitAll`)
  `GET /api/mobile/download/{token}` endpoint that validates the signature and expiry before streaming the file.
  Issuing a link (`POST /api/mobile/releases/{id}/download-link`) stays behind the normal role matrix
  (`admin`/`guest`), so a pending/logged-out caller gets 401/403 there (spec scenario 4); the download endpoint itself
  is public because Android's download manager / a plain browser download can't carry a bearer token, but an
  invalid/expired/tampered token still gets an explicit `403 DOWNLOAD_LINK_INVALID` (fail fast, no silent partial
  download). Rationale: no server-side state to clean up, trivially horizontally scalable (irrelevant at this scale
  but free), and matches the "signed URL" pattern named in the original tech-context draft. Alternative: a persisted
  `DownloadLink` table with a cleanup job — rejected as pure overhead for a token that self-expires.

- **D9 — Cross-module notification wiring uses Spring Modulith application events, with `mobile` as the sole Ntfy
  sender.** `settings`' pending-signup detection publishes a `UserSignUpPending` event (instead of calling Ntfy
  directly, which was only ever planned, never built — §2.1); `fna`'s `BriefingGeneratorService` publishes a
  `BriefingReady` event on completion (entirely new). `mobile.service.MobileNotificationListener` has two
  `@ApplicationModuleListener` methods, one per event, each building an App-Link click-through URL
  (`https://{PRODUCTION_DOMAIN}/mobile/open?screen=settings-users` /
  `.../open?screen=fna-briefing`) and calling the module's own `NtfyClient`. Rationale: this is exactly the design in
  the original tech-context draft ("listen to Modulith events; no direct cross-module calls"), it is the first real
  use of the event infrastructure 006's research explicitly flagged as unused, and it keeps `mobile` from depending on
  `settings`/`fna` (event listening is a `mobile → shared`-only dependency direction, same as every other module) —
  while `settings`/`fna` gain a reusable "this business thing happened" event they may want other listeners for
  later. A push failure inside the listener is logged and swallowed — it must never fail the sign-up or briefing flow
  that triggered it (Constitution II — fail fast, but not fail the wrong thing). Alternative: keep 006's direct-Ntfy
  design in `settings` and have `mobile` add its own separate, richer notification — rejected: two push notifications
  for one sign-up is confusing and exactly the kind of thing "no silent failures" doesn't fix, it just duplicates
  noise; a `settings → mobile` or `fna → mobile` direct call — rejected: wrong dependency direction, and the user's
  own draft explicitly asked for events instead.

- **D10 — Ntfy config moves from `jordylab.settings.notifications.ntfy.*` to `jordylab.mobile.notifications.ntfy.*`.**
  Rationale: config ownership should sit with the code that actually sends the notification (D9); since no working
  code reads the settings-scoped keys today (§2.1), this is a rename with zero runtime behavior change, done before
  the capability is ever used rather than as a later cleanup. `SettingsProperties.Notifications` is removed as part
  of this feature. Alternative: leave the config where it is and have `mobile` read it via a cross-module properties
  reference — rejected: reading another module's `@ConfigurationProperties` class is itself a boundary violation, and
  worse than a same-day rename of unused keys.

- **D11 — Minimum Android version enforced two ways.** The spec's Android 10+ floor (FR-018, from clarification) is
  enforced at the OS level by `android/variables.gradle`'s `minSdkVersion = 29` (Android itself refuses installation
  below this — no in-app messaging needed, spec Edge Cases); the **separate** app-version floor (FR-011,
  `minSupportedVersionCode`) is an application-level check against `MobileRelease.minSupportedVersionCode` on every
  start/resume, blocking with a mandatory-update screen. These are two different axes (OS version vs. app version) and
  must not be conflated in implementation — confirmed here explicitly since both use the word "minimum."

- **D12 — `settings.revoke()` gains an offline-consent revocation call for the `jordylab-mobile` client.** Confirmed
  (§1.2) that Keycloak's normal session logout does not revoke `offline_access` grants; without this, a revoked
  guest's biometric-unlock token would keep refreshing indefinitely (violates FR-013 outright). The existing
  `KeycloakAdminClient` (RestClient-based, from 006) gains one more call:
  `DELETE /admin/realms/{realm}/users/{id}/consents/jordylab-mobile`, invoked alongside the existing session-logout
  call inside `revoke()`. Rationale: this is the only documented way to kill an offline grant server-side; doing it
  inside `settings.revoke()` (rather than a new endpoint in `mobile`) keeps the single source of truth for "what does
  revoking a user mean" in one place. Alternative: a `mobile`-owned revocation check on every API call (e.g. a stored
  denylist) — rejected: reinvents session state Keycloak already owns, for a problem Keycloak's own admin API already
  solves in one call.

- **D13 — Realm/Keycloak changes are a STOP-AND-REPORT gate, per the user's own instruction.** Needed additions (to
  be proposed, not applied, before explicit sign-off): public client `jordylab-mobile` (PKCE S256, redirect URI =
  `https://{PRODUCTION_DOMAIN}/mobile/callback`, `offline_access` allowed, `standardFlowEnabled=true`,
  `directAccessGrantsEnabled=false` — mirrors `jordylab-host`); confidential client `mobile-release-ci`
  (`serviceAccountsEnabled=true`, secret from a GitHub Actions secret) for CI publishing; new realm role
  `mobile-release-publisher` granted only to that service account. **Blocked on**: the production domain (feature 008)
  for the redirect URI and CORS/Web-Origins — this cannot be finalized before 008 exists.

- **D14 — Application id and signing key are STOP-AND-REPORT gates, per the user's own instruction.** The Android
  application id must be a reverse-DNS id on the real production domain (e.g. `<tld>.<domain>.mobile` — exact value
  depends on feature 008's domain) and, once the first release ships, **can never change**. The signing keystore must
  be generated once, stored as a GitHub Actions secret (base64) plus an offline backup, and never regenerated — losing
  it forces every installed app to be uninstalled and reinstalled (spec Assumptions). Neither is decided in this plan;
  both require explicit user sign-off immediately before the first CI release run, not before writing tasks or code
  that merely reference a placeholder value.

## 4. Verify at implementation time (carried into tasks)

1. **`CapacitorHttp` native-bypass plugin**: verify whether enabling Capacitor's native HTTP patch
   (`plugins: { CapacitorHttp: { enabled: true } }`) is still current in 8.5.1 and, if enabled, whether it removes the
   need to add the WebView's origin (`https://localhost`) to production CORS/Web-Origins entirely (native HTTP calls
   bypass browser CORS). If confirmed, prefer it — it avoids adding an odd-looking origin to prod config for a reason
   unrelated to real web traffic.
2. **`assetlinks.json` propagation delay**: Google's Digital Asset Links verification is not always instant after
   first publishing the file — confirm the expected delay so the first-release device test isn't mistaken for a bug.
3. **`registerReceiver`/App Link intent-filter verification** on the generated `android/` project: confirm
   `autoVerify="true"` on the `<intent-filter>` for `https://{PRODUCTION_DOMAIN}/mobile/*` actually round-trips once
   008's domain exists — this can only be verified against a real, publicly reachable domain, not locally.
4. **Ntfy click-header format**: confirm the exact header/field Ntfy expects for a tap-through URL (the original
   draft assumed a `Click` header) against the specific Ntfy server version in use.
5. **`@capgo/capacitor-native-biometric` failure/cancel callback shapes**: confirm the exact error codes for "user
   cancelled," "no biometrics enrolled," and "biometrics changed since enrollment" so FR-012's fallback logic branches
   correctly rather than treating every failure the same way.

## 5. Sources

- Capacitor npm registry (`@capacitor/core`, `@capacitor/cli`, `@capacitor/ios`) — versions checked 2026-09-28
- `@capgo/capacitor-native-biometric` / `@capgo/capacitor-share-target` — npm + GitHub releases, Capacitor-8
  compatibility confirmed
- Capawesome Share Target plugin docs — Capacitor-8 current, kept as documented fallback
- Keycloak JavaScript adapter docs (`keycloak-js` `KeycloakAdapter` interface, Capacitor named as a target
  environment)
- Google Digital Asset Links / `assetlinks.json` format docs (developers.google.com, developer.android.com)
- Keycloak forum + docs on offline token behavior vs. logout/session revocation
- Carried forward from `specs/_drafts/007-mobile-app/research.md`: Android sideloading/developer-verification
  timeline, iOS EU Web Distribution analysis, the original library shortlist
- Codebase baseline: live exploration of `jordylab-fe` and `jordylab-be` on 2026-09-28 (apps/libs structure, realm
  export, `SecurityConfig.java`, `application.yaml`, entity/module patterns, Ntfy config scaffold, artwork storage
  config, existing GitHub Actions workflows)

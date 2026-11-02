# Research: Replace the Native Android Wrapper with an Installable Web App

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02 · Status: complete (all decisions resolved against the repository and current web-platform behaviour)

Every decision below was verified against the repo unless marked otherwise. Sources for platform behaviour: the Chrome team's installability-criteria post (developer.chrome.com/blog/update-install-criteria) and MDN's "Making PWAs installable", checked 2026-11-02.

---

## D1 — The minimal service worker exists only for installability and is a pure pass-through

**Decision**: `apps/jordylab/public/sw.js` registers `install`, `activate` (with `skipWaiting` + `clients.claim` so deploys update cleanly) and a `fetch` listener that never calls `respondWith` — it satisfies Chrome's fetch-handler criterion while caching nothing. Registered once from the `jordylab` app bootstrap (`main.ts`, after `bootstrapApplication` resolves) at scope `/`.

**Rationale**: Chrome removed the service-worker requirement for *menu* installs (Chrome 108 mobile / 112 desktop) but the blog states the automatic install-prompt algorithm "still requires the presence of a `fetch()` handler" — and our install journey (US1-AS2) depends on capturing `beforeinstallprompt`. A listener that never calls `respondWith` is a documented pass-through: the browser falls back to network for every request, so `/api/**`, LibBot SSE and `/auth/**` are structurally impossible to cache (FR-002), and prod deploys stay trivial (nginx `no-cache` on `index.html` already guards the shell).

**Alternatives considered**: (a) No service worker at all — rejected: loses the automatic prompt on Android, the centrepiece of US1. (b) Workbox/runtime caching of the app shell — rejected: offline-first is explicitly out of scope, and cache invalidation across deploys adds a failure mode the spec does not ask for. (c) A `fetch` handler that `respondWith(fetch(request))` for GET navigations — rejected: intercepting is strictly riskier than not intercepting; a transparent no-op achieves the same criteria.

**Verification note**: e2e asserts `sw.js` is served and registers; the unit test asserts the fetch handler never calls `respondWith` is unnecessary (there is nothing to assert on a no-op) — the Playwright journey asserts the manifest and SW respond 200 with correct media types.

---

## D2 — Web Share Target uses `GET` with `title`/`text`/`url` query params on a `/share` route

**Decision**: `manifest.webmanifest` gains `"share_target": { "action": "/share", "method": "GET", "params": { "title": "title", "text": "text", "url": "url" } }`. The existing `mobile/share` route is renamed to `/share` and the landing component reads `title`/`text`/`url` from `ActivatedRoute.queryParamMap` instead of the in-memory Capacitor event.

**Rationale**: `GET` share targets need no service-worker `POST` handling — the OS share sheet simply navigates to the action URL with query params, which the Angular router already handles behind `authGuard`. Android apps commonly put the shared URL inside `text` rather than `url`, so the landing screen resolves the target URL as `url` param, else the first `http(s)://` occurrence in `text` (spec Edge Cases), keeping the full text for "Ask LibBot". The old in-memory payload (dropped when the app was killed) is replaced by URL state, which additionally survives sign-in: `authGuard` already carries `returnUrl` through `/login`, and D5 makes the Keycloak redirect honour it.

**Alternatives considered**: (a) `POST` share target handled by the service worker — rejected: needs a `fetch` handler that *does* intercept and a form-parsing response path, reintroducing exactly the caching/complexity risk D1 avoids, for zero user-visible gain at our scale. (b) Keep the path `/mobile/share` — rejected: every other "mobile" name is being removed; `/share` is the one rename that touches nothing external (the old share flow was in-memory only, no bookmarks or server links point at it).

---

## D3 — Install prompt: capture `beforeinstallprompt`, keep the 30-day dismissal, add a permanent `Settings → App` entry

**Decision**: `InstallPromptStore` is rewritten to *capture* the `beforeinstallprompt` event (instead of suppressing it), keep the existing `jordylab.install-prompt.dismissed-until` 30-day localStorage dismissal, and expose `promptInstall()` that calls the stashed event's `prompt()`. The bottom sheet offers the native install on Android; iOS keeps the instruction sheet (Safari has no `beforeinstallprompt`). A new "Install on this phone" section in `Settings → App` (shown on `web-android`/`web-ios` when not running standalone) re-opens the flow at any time, with per-browser fallback instructions; the desktop sidebar QR entry stays (retitled "Get the phone app") and points at the site origin.

**Rationale**: Spec FR-003–FR-006 (clarification session 2026-11-02, answer A). `PlatformService` keeps its UA-based `web-android` / `web-ios` / `web-desktop` detection — only the Capacitor `isNativePlatform()` branch is deleted. `display-mode: standalone` (plus the iOS `standalone` flag, already in `isRunningStandalone()`) hides all install surfaces. Standalone detection also gates the automatic sheet.

**Alternatives considered**: (a) Suppress the browser prompt entirely and only ever show instructions — rejected: the native prompt is one tap and the whole point of US1. (b) Put the manual entry in the account menu — rejected in clarification (Settings → App won; the removed fingerprint section is its natural slot and `More → App` is reachable on a phone by every approved user).

---

## D4 — `NtfyClient` moves to `shared/rest/client/`, `settings` and `fna` call it directly; the two notification events are deleted

**Decision**: `NtfyClient` (unchanged delivery semantics: no-op + startup warning while unconfigured, log-and-swallow failures) moves from `mobile.rest.client` to `shared.rest.client`, taking a new `NotificationProperties` record (`@ConfigurationProperties("jordylab.notifications")`: `ntfy.base-url/topic/token` ← same `NTFY_*` env vars, plus `app-base-url` ← new `JORDYLAB_APP_BASE_URL`). Its `publish(title, body, clickPath)` resolves the click-through URL from the app base URL, so callers pass only a path (`/settings/users`, `/fna/briefing`). `PendingSignupWatcherService` and `BriefingGeneratorService` call it directly; the `UserSignUpPending` and `BriefingReady` event records and `MobileNotificationListener` are deleted with the module.

**Rationale**: Clarification answer A (2026-11-02): delete `mobile` completely. `rest/client` is the canonical Modulith home for outbound HTTP clients (`settings/rest/client/KeycloakAdminClient` is the precedent), and every module may depend on `shared`, so `ModularityTests` stays green with no listener indirection left. The events had exactly one listener each — keeping them after the listener dies would be dead ceremony (KISS). Screen knowledge moves to the caller that owns the screen.

**Alternatives considered**: (a) A renamed `notifications` module keeping the `@ApplicationModuleListener` — rejected in clarification (option C): a whole module for one client is structure without domain. (b) Settings and fna each owning a private copy — rejected: DRY; the header set (`X-Title`, `X-Click`, optional bearer token) and the no-op-when-unconfigured rule must not fork. (c) Keep `jordylab.mobile.notifications.ntfy.*` key names — rejected: the property namespace must not survive the module it named; env var *names* stay stable so the prod ConfigMap change is one literal.

**Verification note**: WireMock-based tests move with the client; `@ApplicationModuleTest`s for settings/fna cover the direct calls; `ModularityTests` verifies the structure after deletion. Prod ConfigMap: `MOBILE_PRODUCTION_DOMAIN=jordylab.be` is replaced by `JORDYLAB_APP_BASE_URL=https://jordylab.be`.

---

## D5 — Web `login()` honours `returnUrl` so a signed-out share survives the Keycloak round-trip

**Decision**: `AuthService.login()` gains an optional redirect target (the safe in-app path), used by `LoginComponent` from its `returnUrl` query param; `requestAction` keeps `window.location.href` (re-auth on the same page). The native branches (`#loginNative`, `completeNativeLogin`, `#logoutNative`, `unlockWithRefreshToken`, `getRefreshToken`, `#applyNativeTokens`, `nativeFailure`), `BiometricUnlockService`, the fingerprint button on the login page, and `mobileCallbackUri` in `AUTH_CONFIG` are deleted; `keycloak-js`'s silent check-sso stays the sole session restore.

**Rationale**: Today web `login()` redirects to the bare origin, so `/share?text=…` would be lost after sign-in — FR-008/US2-AS4 require it to survive. `authGuard` already builds `/login?returnUrl=…` and `safeReturnUrl` already rejects non-in-app targets, so the login page only needs to forward what it already holds. With the WebView origin gone, every token flow is the standard browser one — the entire native token-exchange machinery (manual PKCE `fetch`, public-field assignment on the Keycloak instance, documented as an implementation-time workaround in 007) is deleted, not ported.

**Alternatives considered**: (a) Store the pending share in `sessionStorage` keyed by the login — rejected: URL state already round-trips via `returnUrl`; extra storage is a second source of truth. (b) Keep `getRefreshToken`/`unlockWithRefreshToken` for a future Web Push need — rejected: YAGNI; ntfy is the notification channel (spec Assumptions).

---

## D6 — Manifest: `id`, `scope`, maskable icons, `share_target`; colours stay the 016 defaults

**Decision**: `manifest.webmanifest` is extended to: `id: "/"`, `scope: "/"`, `start_url: "/"`, `display: "standalone"`, the existing `name`/`short_name`/colours (`#131314` ground and theme — the 016 Content Led default), 192/512 icons marked `"purpose": "any maskable"` (same PNGs; Android masks, other contexts use as-is), plus the D2 `share_target`. iOS keeps its dedicated tags in `index.html` (already present: `apple-touch-icon`, `apple-mobile-web-app-*`).

**Rationale**: `scope` + `id` make the installed app's boundary explicit and keep the install identity stable across deploys. Maskable purpose on the existing icons avoids shipping new artwork (016 owns the brand assets; nothing in this feature redraws them). The manifest is served with `application/manifest+json` by both prod nginx (`.webmanifest` location already exists) and the e2e static server (Bun serves the file; the Playwright journey asserts the content type).

**Alternatives considered**: (a) Separate maskable artwork with a safe zone — rejected for this feature: 016's icons already carry the brand; a dedicated maskable set is a 016 follow-up if the owner dislikes the mask. (b) `display: "minimal-ui"` — rejected: standalone is the app-like launch the spec demands (US1-AS5).

---

## D7 — Legacy `/mobile/open` notification links redirect instead of 404-ing silently

**Decision**: Two componentless routes with `canActivate` guards that return a `UrlTree`: `/mobile/open?screen=settings-users` → `/settings/users`, `/mobile/open?screen=fna-briefing` → `/fna/briefing`, other/missing screens → `/`; `/mobile/callback` → `/login`. Unauthenticated users hit `authGuard` semantics naturally because the redirect targets are guarded routes.

**Rationale**: Spec Edge Cases: an ntfy notification delivered before the cutover sits on the admin's phone for weeks and its `X-Click` URL is `https://jordylab.be/mobile/open?screen=…`. Without the redirect the router matches nothing and renders an empty outlet — a broken-feeling dead end. A functional guard returning `UrlTree` needs no component and no template; the mapping table lives where `AppLinkService`'s used to.

**Alternatives considered**: (a) Let it 404 — rejected: MRB-07 verified these taps work today; regressing a verified journey fails SC-002. (b) Server-side (nginx/ingress) rewrite — rejected: the SPA owns routing; a rewrite in two places (prod nginx + e2e static server) drifts.

---

## D8 — Flyway drops the `mobile` schema and cleans the stale event-publication rows

**Decision**: One migration, `V20261103001__mobile_drop_schema.sql`: `DROP TABLE IF EXISTS mobile.mobile_release;` then `DROP SCHEMA IF EXISTS mobile CASCADE;`, plus `DELETE`s on the Modulith `event_publication` table for `serialized_event_type` values ending in `UserSignUpPending` or `BriefingReady` (exact table/column names to be read from `V20260925__create_event_publication_table.sql` at implementation time — do not guess them into the migration).

**Rationale**: The schema-ownership rule says each module owns its schema; when the module dies its schema dies with it in a named migration (never edit `V20260928004`). The event-publication cleanup prevents Spring Modulith's registry from holding rows whose event classes no longer exist — a row that is still *incomplete* would try to deserialize a deleted class on resubmission and fail obscurely; deleting both complete and incomplete rows for those two types is safe because their only listener is gone.

**Alternatives considered**: (a) Leave the table as an orphan "in case" — rejected: dead data with no owner violates the module/schema rule and confuses every future schema audit. (b) Only delete incomplete rows — rejected: complete rows are archived history of events nobody can ever listen to again; both go.

---

## D9 — Keycloak realm cleanup in both exports + the e2e realm generator

**Decision**: From `jordylab-be/compose/keycloak-realm-export.json` and `deploy/keycloak/realm-prod.json`: delete the `jordylab-mobile` client (public, PKCE, `/mobile/callback`), the `mobile-release-ci` client + its service account, the `mobile-release-publisher` realm role and its scope mapping, and its mention in the `admin` composite description. In `e2e/lib/make-realm.py`: drop the `jordylab-mobile` branch (it disappears from the dev export), drop `mobile-release-publisher` from `e2e-ingest`'s roles, and drop `offline_access` from the e2e admin (comment there says it existed only for the Android app's code exchange).

**Rationale**: FR-015. The clients exist only for the native login redirect and the CI publisher; with both gone the realm entries are attack surface and import noise. The live prod realm is *not* touched by the file edits alone — realm JSON is import-on-first-boot; the live realm needs the kcadm deletions in D10. Dev compose: the export is re-imported on `compose down -v` only (dev DB rule: never recreate the dev stack without the owner), so the dev realm edit lands next time the owner recreates it; until then the extra client is inert.

**Alternatives considered**: (a) Keep the clients disabled instead of deleting — rejected: FR-015 says remove; disabled clients still surface in the admin console and imports. (b) Touch the live realm from this feature's code — no: live-realm changes are a runbook/ops step (D10), not code.

---

## D10 — Production cutover is a documented runbook sequence, not code

**Decision**: `docs/runbook.md` §20 gains/edits a "Removing the Android app stack" subsection listing the post-deploy steps, executed by the owner (agent may run them with the owner's approval, per the ops skill): (1) deploy the release containing this feature (backend boots with `jordylab.notifications.*`; images tagged); (2) `kubectl -n jordylab delete pvc mobile-releases` (after confirming no pod mounts it); (3) kcadm: delete `jordylab-mobile` and `mobile-release-ci` clients and the `mobile-release-publisher` role from the live realm; (4) `gh secret remove MOBILE_RELEASE_CI_CLIENT_SECRET --repo jordy-swinnen/JordyLab` (and the env-scoped one if present); (5) SOPS: remove `MOBILE_RELEASE_CI_CLIENT_SECRET` and `MOBILE_DOWNLOAD_LINK_SECRET` from `deploy/k8s/overlays/prod/secrets.sops.yaml` and re-encrypt; (6) verify: ntfy push arrives and its tap opens `/settings/users`, the manifest audit passes on `https://jordylab.be`.

**Rationale**: These are cluster/GitHub/secret mutations with blast radius — the repo's own rules (jordylab-ops skill, SOPS handling, "remove only what you created", prod approvals) put them behind explicit runbook steps with verification, exactly like every previous prod transition (rc-series handoffs). The APK files on the deleted PVC (12 files at the last audit, MRB-09) are dead weight after cutover; the volume is 2 Gi of a 75 Gi disk.

**Alternatives considered**: (a) Do the cluster deletions inside the deploy pipeline — rejected: the pipeline has no kcadm/GitHub-secret scope by design. (b) Keep the PVC "for a while" — rejected: nothing will ever read it again; the runbook order (deploy first, delete after) is the safety margin.

---

## D11 — e2e: new `pwa.spec.ts` journeys; android mode removed from the runner

**Decision**: `apps/jordylab-e2e/src/pwa.spec.ts` (Playwright, same support/session-reuse pattern as the other journeys) covers: (1) manifest journey — `GET /manifest.webmanifest` answers 200, `application/manifest+json`, with `name`, `start_url`, `scope`, `display: standalone`, 192+512 icons with maskable purpose, and the `share_target` (`GET`, `/share`, title/text/url); `GET /sw.js` answers 200 and a service worker registers; (2) share journeys — guest at `/share?text=…&url=…` sees only "Ask LibBot" and lands in LibBot with the prefill; admin also gets "Save to FNA" and the article submit lands in the list; signed-out visit to `/share?...` goes through login and returns with the payload intact (D5); bare `/share` without params shows the calm empty state; (3) install surfaces — the Android install sheet is asserted only where `beforeinstallprompt` can fire (Playwright cannot synthesize it; assert the store logic in unit tests instead and keep the e2e to manifest + share, which the runner can genuinely drive). `e2e/run.sh` loses the `android` mode, `prepare_android`, the fixed ANDROID_* ports, `E2E_DOWNLOAD_LINK_SECRET` and all `MOBILE_*` env plumbing; `e2e/android-setup.sh` and `apps/jordylab-mobile-e2e` are deleted. `e2e/lib/make-realm.py` changes per D9.

**Rationale**: FR-016. The Playwright suite proves exactly what the runner can prove on its own stack; everything device-bound (real share sheet, real install, ntfy tap) stays on the owner's phone checklist, same split as the existing suite (agent-browser vs suites rule in `jordylab-fe/AGENTS.md`). The static server already serves any `public/` file (including `sw.js`) — no runner change needed for the manifest/SW journey.

**Alternatives considered**: (a) Simulate `beforeinstallprompt` in Playwright by dispatching a fake event — rejected: it tests our listener against our mock, proving nothing about installability; the unit tests already cover the store logic. (b) Keep the android runner mode "in case Capacitor returns" — rejected: the spec's whole premise is that it does not (presence decision gate); dead modes rot (PINS.md, JDK pinning, emulator images).

---

## D12 — Frontend dependency and workspace cleanup

**Decision**: Remove from `jordylab-fe/package.json` + `bun.lock`: `@capacitor/android`, `@capacitor/app`, `@capacitor/browser`, `@capacitor/cli`, `@capacitor/core`, `@capgo/capacitor-native-biometric`, `@capgo/capacitor-share-target` (then `bun install`). Delete `apps/jordylab-mobile/` and `apps/jordylab-mobile-e2e/` (the latter has its own `package.json`/`bun.lock`). In `apps/jordylab/project.json`: delete the `mobile` and `mobile-e2e` build configurations; delete `environment.mobile.ts` and `environment.mobile-e2e.ts`. Sweep for stragglers with `grep -ri "capacitor\|jordylab-mobile\|@capgo" jordylab-fe --exclude-dir=node_modules --exclude-dir=dist` ending empty, plus `.gitignore` entries for the android build outputs.

**Rationale**: FR-011/FR-016. The two apps are the only things importing Capacitor at runtime (root `package.json` exists because the workspace predates per-app deps; the scanner-style Nx boundary lint still guards the rest). The `e2e` configuration stays — `e2e/run.sh web` builds with it. A grep gate in the e2e or a task-level check keeps the removal honest; docs and specs directories are exempt (history), matching how 006's Ollama removal was verified.

**Alternatives considered**: (a) Keep `@capacitor/cli` for " occasional native debugging" — rejected: no native artifact remains to debug. (b) Move Capacitor deps into the (deleted) mobile app — moot: the apps go.

---

## What was explicitly *not* decided here (plan-level, not research)

- Exact wording/design of the new install sheet and Settings card copy — 016's design system owns wording style; tasks will follow the existing sheet markup pattern.
- Whether a dedicated maskable icon set is drawn later — 016 follow-up, recorded in D6.
- ntfy Web Push / VAPID — permanently rejected by the spec's Assumptions (ntfy + its Android app stay).

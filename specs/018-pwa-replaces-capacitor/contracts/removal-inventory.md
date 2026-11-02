# Contract: Removal Inventory

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02 · Spec: FR-011–FR-016, US4

The authoritative list of everything this feature deletes. Every line must be gone (or explicitly rewritten) before the feature is done; the final grep gate is in [quickstart.md](../quickstart.md).

## Frontend — `jordylab-fe`

### Nx apps (deleted entirely)

- `apps/jordylab-mobile/` — `capacitor.config.ts`, `android/` (Gradle project, `AndroidManifest.xml`, App Link wiring), `project.json`
- `apps/jordylab-mobile-e2e/` — Appium 3 + WebdriverIO suite, `wdio.conf.ts`, `wdio.browser.conf.ts`, `PINS.md`, own `package.json`/`bun.lock`, `src/`

### Dependencies (`package.json` + `bun.lock`)

- `@capacitor/android`, `@capacitor/app`, `@capacitor/browser`, `@capacitor/cli`, `@capacitor/core`
- `@capgo/capacitor-native-biometric`, `@capgo/capacitor-share-target`

### `apps/jordylab`

- Build configurations `mobile` and `mobile-e2e` in `project.json`
- `src/environments/environment.mobile.ts`, `environment.mobile-e2e.ts`
- `app.ts` / `app.html`: `ApkDownloadService`, `UpdateCheckStore` wiring, `AppLinkService.listen()`, `ShareTargetService.listen()`, `#restoreNativeSession`, biometric wipe on logout, `lib-update-required` / `lib-update-available-banner` usage
- `app-settings/app-settings-page.component.ts`: native-only fingerprint section + `BiometricUnlockToggleComponent` import
- `.gitignore` entries for the Android build outputs (if any)

### `libs/shared/platform/api`

- `apk-download.service.ts` (+spec), `latest-release.model.ts`
- `update-check.store.ts` (+spec)
- `app-link.service.ts` (+spec)
- `share-target.service.ts` (+spec) — dissolved into the `/share` route component
- `api-base-url.interceptor.ts` (+spec), `api-base-url.token.ts`, `resolve-api-url.ts` (+spec) — the native-origin rewrite; web URLs are already correct relative paths
- `artwork-url.pipe.ts` (+spec) — becomes a plain passthrough or is inlined where used (decide at implementation: grep usages first)
- `platform.service.ts`: `Capacitor.isNativePlatform()` import + `isNative()`; UA platform detection **stays** (install surfaces need it)
- `install-prompt.store.ts`: `suppressBrowserInstallPrompt()` (replaced by capture)

### `libs/shared/platform/ui`

- `update-available-banner.component.ts` (+spec), `update-required.component.ts` (+spec)
- `install-prompt.component.ts` — rewritten (APK download steps → native install prompt)
- `android-app-qr-entry.component.ts` — kept, retitled ("Get the phone app"), QR target unchanged (site origin)
- `share-landing/` — rewritten to query params (contract: share-target-contract.md)

### `libs/shared/auth`

- `biometric-unlock.service.ts`, `biometric-unlock-toggle.component.ts` (+specs)
- `AuthService`: `Capacitor`/`Browser` imports, `#loginNative`, `completeNativeLogin`, `#logoutNative`, `getRefreshToken`, `unlockWithRefreshToken`, `clearNativeFailure`, `nativeFailure`, `#applyNativeTokens`, `mobileCallbackUri` handling
- `LoginComponent`: fingerprint button, `biometricEnabled`, `nativeFailure` merge
- `auth-config.ts`: `mobileCallbackUri` field

### e2e harness

- `e2e/run.sh`: `android` mode, `prepare_android`, `ANDROID_*` constants, `E2E_DOWNLOAD_LINK_SECRET`, `MOBILE_*` env vars, `signing_certificate_digest`
- `e2e/android-setup.sh`
- `e2e/lib/make-realm.py`: `jordylab-mobile` branch, `mobile-release-publisher` in e2e-ingest roles, `offline_access` on the e2e admin

## Backend — `jordylab-be`

- `src/main/java/dev/jordy/jordylab/mobile/` — all 23 files (controllers `MobileReleaseController`, `MobileDownloadController`, `AssetLinksController`; services `MobileReleaseService`, `DownloadLinkService`, `MobileNotificationListener` + 5 exceptions; `MobileRelease` + repository; `NtfyClient` → moved; `MobileProperties`, `MobileConfiguration`, `ApkSigningCertificateReader`, `InvalidApkException`; response models) and their tests
- `settings/KeycloakUserAdministrationService`: `MOBILE_CLIENT_ID`, `revokeMobileOfflineConsent`; `settings/rest/client/KeycloakAdminClient.revokeConsent`
- `settings/UserSignUpPending`, `fna/BriefingReady` (+ their publications in the poller/generator)
- `shared/config/SecurityConfig`: the four `/api/mobile/**` matchers + `/.well-known/assetlinks.json` `permitAll`; Javadoc paragraph
- `application.yaml`: `jordylab.mobile.*` block (replaced by `jordylab.notifications.*`), `spring.servlet.multipart` 200 MB/210 MB sizes (APK-upload only — revert to defaults)
- `build.gradle.kts`: `com.android.tools.build:apksig` + the `google()` repository block
- `compose/keycloak-realm-export.json`: `jordylab-mobile`, `mobile-release-ci` (+ service account), `mobile-release-publisher` role/scope-mapping, `admin` composite description mention
- **Migration added, not removed**: `V20261102003__mobile_drop_schema.sql` (research D8)

## Deploy + CI

- `deploy/k8s/base/backend.yaml`: `mobile-releases` volume + mount
- `deploy/k8s/base/artwork-pvc.yaml`: the `mobile-releases` PVC document
- `deploy/k8s/base/keycloak.yaml`: `MOBILE_RELEASE_CI_CLIENT_SECRET` env wiring
- `deploy/k8s/overlays/prod/kustomization.yaml`: `MOBILE_PRODUCTION_DOMAIN`, `MOBILE_APPLICATION_ID`, `MOBILE_RELEASE_SIGNING_CERT_SHA256` literals; `JORDYLAB_APP_BASE_URL` added
- `deploy/k8s/overlays/prod/secrets.sops.yaml`: `MOBILE_RELEASE_CI_CLIENT_SECRET`, `MOBILE_DOWNLOAD_LINK_SECRET` (SOPS re-encrypt — owner)
- `deploy/keycloak/realm-prod.json`: both clients, service account, role, scope mapping
- `deploy/keycloak/README.md`: mobile client/role creation commands + changelog note
- `.github/workflows/e2e-android.yml` — whole file
- `.github/workflows/release.yml`: the `apk` job, `version_code` derivation in `verify`, header comment
- `deploy/k8s/base/gateway.yaml` **stays** (ntfy route is kept)

## Docs

- Root `AGENTS.md`: `mobile` module row, Keycloak client list, mobile-app architecture bullet
- `jordylab-be/AGENTS.md`: Mobile module section, schema-ownership `mobile` row, endpoint-gate list, Keycloak bullet
- `jordylab-fe/AGENTS.md`: "Mobile app" section → PWA description; e2e "Android layer" paragraph
- `docs/runbook.md`: §20 "APK publish credentials", release-flow steps, §21 fingerprint manual test, Contents
- `docs/testing/manual-test-runbook.md`: MRB-06 (share target → phone PWA check), MRB-05/06/07/08 result lines stay as history

## Live production (runbook §20, owner-approved — research D10)

1. `kubectl -n jordylab delete pvc mobile-releases`
2. kcadm: delete clients `jordylab-mobile`, `mobile-release-ci`; delete role `mobile-release-publisher`
3. `gh secret remove MOBILE_RELEASE_CI_CLIENT_SECRET --repo jordy-swinnen/JordyLab` (repo + env scopes)
4. SOPS key removal + re-encrypt (`secrets.sops.yaml`)
5. Post-checks: ntfy push + tap → `/settings/users`; manifest audit on `https://jordylab.be`

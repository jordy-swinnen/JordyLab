# Quickstart: Validate the PWA Cutover

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02

Runnable checks that prove the feature end-to-end, cheapest first. Follow [contracts/pwa-manifest-contract.md](contracts/pwa-manifest-contract.md), [contracts/share-target-contract.md](contracts/share-target-contract.md), [contracts/notifications-contract.md](contracts/notifications-contract.md) and [contracts/removal-inventory.md](contracts/removal-inventory.md) for the expected shapes; this file only lists how to run them.

## Prerequisites

- Bun, Node 22, Java 25, Podman (for e2e); `bun install` once in `jordylab-fe/`
- Dev stack (`podman compose up -d` in `jordylab-be/`) for A2 — or use A3's throwaway stack instead

## A. Unit layers (fast, minutes)

1. **Frontend units (rewritten code only)** — from `jordylab-fe/`:
   ```bash
   bunx nx run-many -t test -p platform-api platform-ui shared-auth jordylab
   ```
   Expected: green, including the new `InstallPromptStore` capture/dismissal cases, the `/share` landing specs (params, URL-in-text, empty state, role gating) and the auth specs without the native branches.
2. **Backend units + module boundaries** — from `jordylab-be/` (Testcontainers needs the Podman socket env from `jordylab-be/AGENTS.md`):
   ```bash
   ./gradlew test --tests "*ModularityTests*"
   ./gradlew test --tests "*Ntfy*" --tests "*PendingSignup*" --tests "*BriefingGenerator*"
   ./gradlew check
   ```
   Expected: `ModularityTests` green with no `mobile` module; the moved `NtfyClient` WireMock tests pass; the direct-call tests prove publish + swallow-on-failure; JaCoCo package gate passes for `shared.rest.client`.
3. **Removal grep gate** — from repo root (add `--exclude-dir=.gradle --exclude-dir=.nx` for a clean output):
   ```bash
   grep -ri "capacitor\|@capgo\|jordylab-mobile\|mobile-release-ci" \
     jordylab-fe jordylab-be deploy .github/workflows \
     --exclude-dir=node_modules --exclude-dir=dist --exclude-dir=build
   ```
   Expected: only `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RealmConfigurationTest.java` — the guard that
   fails if the removed Keycloak clients or role ever come back. Docs and specs are history and excluded on purpose.

## B. End-to-end (throwaway stack)

From `jordylab-fe/`:

```bash
e2e/run.sh web
```

Expected: the whole suite green, including the new `pwa.spec.ts` journeys — manifest 200 + `application/manifest+json` + every field from the manifest contract (incl. `share_target`), `sw.js` 200 + a registered service worker, guest/admin/signed-out share journeys, and the empty `/share` state. Cleanup self-verifies (`exit 97` on leftovers).

While iterating: `E2E_SKIP_BACKEND_BUILD=1 E2E_SKIP_WEB_BUILD=1 e2e/run.sh web`.

## C. Manual phone checks (owner, ~15 min — only a real device proves these)

1. **Install (US1)**: on the Android phone, open the site in Chrome → the install sheet appears → Install → the launcher icon appears → launch opens standalone (no URL bar) on `https://jordylab.be`; sign in and browse the Library. In `Settings → App` ("More" tab → App) confirm the install card is gone while standalone.
2. **Share (US2, was MRB-06)**: from any app's share menu, share a link to JordyLab → the landing screen shows it; guest sees only Ask LibBot, admin also Save to FNA; sharing while signed out returns to the share screen after login with the payload intact.
3. **Uninstall the old APK**: remove the sideloaded JordyLab app; confirm only the new icon remains and the share sheet no longer offers two JordyLab entries.
4. **Notifications (US3, was MRB-07)**: register a throw-away account → the ntfy push arrives → tap opens `/settings/users` in the installed app (or browser). Delete the account afterwards.
5. **iOS spot-check**: Add to Home Screen on iPhone still opens full-screen (no regression — the iOS tags were untouched).

## D. Production cutover (owner-approved, after deploy)

Follow `docs/runbook.md` §20 "Removing the Android app stack" (research D10): delete the `mobile-releases` PVC, the two Keycloak clients + role, the GitHub secret, and re-encrypt SOPS. Final verification on `https://jordylab.be`: manifest audit passes (Lighthouse "Installable"), one ntfy push round-trip works, and `/.well-known/assetlinks.json` now 404s (expected — the endpoint is gone).

## What is deliberately NOT verified here

- No offline behaviour (out of scope — the SW caches nothing by design).
- No Play Store / iOS native packaging (out of scope).
- Biometric unlock (deliberately dropped journey — browser session + device password manager cover it; nothing to verify).

# Research: App Redesign with Six Themes

Facts were read from the code on 2026-11-02 (file references in brackets). Each decision lists what was rejected.

## Starting point (checked in the code)

- Tokens are one Tailwind plugin, `jordylab-fe/tailwind.theme.js` ("Night Lab"): HSL triplet variables under `:root`, read through `hsl(var(--x) / <alpha-value>)`; there is no `darkMode` setting and no `.dark` variant anywhere. Tailwind is version 3 (`package.json`: `^3.0.2`), shared by `apps/jordylab`, `apps/fna` and `apps/gamecatalog` through their `tailwind.config.js`.
- Fonts are three families loaded from Google Fonts in each app's `index.html` (Bricolage Grotesque, Hanken Grotesk, Martian Mono). No font package is installed.
- The production Content-Security-Policy is a Report-Only header in `deploy/containers/frontend/security-headers.conf`: `script-src 'self'` plus one inline-script hash, `style-src` allows `fonts.googleapis.com`, `font-src` allows `fonts.gstatic.com`. `apps/jordylab-e2e/src/csp.spec.ts` and `tools/app-boot-check.mjs` apply it as an enforcing header, so a new inline script or host would fail them.
- `apps/jordylab/src/app/app.ts` builds the navigation from a `NavGroup[]`: the `App` item (`/settings/app`) is `nativeOnly`, Users and AI Models are `requiredRole: 'admin'`, and the whole Finance group is admin-only. `AppSettingsPageComponent` holds only the biometric toggle.
- Hard-coded colours (outside the plugin) are in: `libs/gamecatalog/ui/src/lib/cover.ts`, `chips/mark-chip.component.ts`, `chips/rom-chip.component.ts`, `chips/status-chip.component.ts`, `marks/mark-buttons.component.ts`, `marks/vote-rail.component.html`, `libbot/libbot-view.component.html`, `dialogs/confirm-dialog.component.html`, `libs/fna/ui/.../portfolio-manager-view.component.ts`, `article-list-view.component.ts`, `libs/shared/auth/src/lib/user-menu.component.ts`, the three `index.html` files, and two backend-shaped mocks. About 30 values in 12 files.
- Style-by-class usage to retire: `eyebrow` 16, `mono-label` 56, `font-mono` 41, `font-display` 15, `page-title` 12, `section-title` 10, `panel` 41, `pill` 6, `plate` 6 (non-spec lines).
- Device-level preferences are stored in the browser's local storage already (`install-prompt.store.ts`); there is no shared storage helper. The app shell and platform detection live in `apps/jordylab/src/app/app.ts` and `libs/shared/platform/api/src/lib/platform.service.ts` (web or native).
- Native shell: Capacitor 8 around the same web build (`apps/jordylab-mobile/capacitor.config.ts`, `webDir` is the `jordylab` build). Plugins installed: app, browser, share-target, native-biometric. No status-bar or splash plugin. Launcher colours are in `android/app/src/main/res/values/ic_launcher_background.xml` (`ic_launcher_background #FF6A3D`, `splash_background #0D0B13`).
- The sign-in page is a CSS-only theme over Keycloak's v2 login theme: `compose/keycloak-theme/jordylab/login/theme.properties` pins dark (`darkMode=false`, `kcHtmlClass=login-pf pf-v5-theme-dark`) and `resources/css/jordylab.css` holds its own hex values, baked into the production image by `deploy/containers/keycloak/Containerfile`.
- End-to-end: `apps/jordylab-e2e` (Playwright) already runs axe (WCAG A and AA) on every page in `support/pages.ts` at 1280 and 390 wide, plus layout, CSP and journey specs; `e2e/run.sh web` starts a throwaway stack.

## Decisions

### D1. Themes are blocks of the existing variables, not a new mechanism
**Decision**: Six `[data-theme="…"]` blocks that redeclare the same HSL-triplet variables; `:root` aliases the default. Keep the existing variable names (`--background`, `--card`, `--primary`, `--border`, `--input`, …) and add only what the design needs (`--accent-text`, `--installed`, `--mark-*`, `--plate-*`, `--series-*`, `--platform-*`, `--rom-validated*`). Mapping table: [contracts/theme-tokens.md](contracts/theme-tokens.md).
**Why**: the helm components, the Tailwind preset and about 400 utility classes already read these names, so switching a theme changes no template. Variables switch at runtime with no rebuild and no re-render.
**Rejected**: Tailwind's `dark:` variant (only two modes, and every class would double); one CSS file per theme loaded on demand (a flash on switch, more requests); a JS theme object applied per component (re-render cost, easy to miss a component).

### D2. Tailwind stays at version 3; the plugin is split, not replaced
**Decision**: Keep `tailwind.theme.js` as the one entry point; move the theme values to a plain data module beside it (`themes.js`) that both the plugin and the tests read.
**Why**: a Tailwind 4 migration is a separate piece of work with its own risk; nothing in this feature needs it.

### D3. No flash on first paint: an external blocking boot script
**Decision**: `public/theme-boot.js`, loaded in the head before the stylesheets, reads the stored choice and sets `data-theme`. It holds no colour values: `index.html` carries two static `theme-color` metas selected by `prefers-color-scheme` (the default dark and light grounds), and `ThemeStore` replaces the content with the active theme's `--background` read from the computed style once the stylesheet is applied.
**Why**: first paint must already be right (spec FR-004, SC-003). An external same-origin file satisfies the existing `script-src 'self'`.
**Duplication kept small on purpose**: the boot script and the store each hold only the theme ids, names and kinds (no colours). A unit test fails if either list differs from `themes.js`, so there is one colour source and one list of ids checked in three places.
**Rejected**: an inline script (needs a new hash in `security-headers.conf` and in `tools/app-boot-check.mjs` on every change); applying the theme after Angular boots (a visible flash on every cold start).

### D4. The store is a signal store in a new shared api library
**Decision**: `libs/shared/theme/api` with `ThemeStore` (signals, no NgRx, `inject()`, `#private` fields), the theme list and the token data. The picker is a small container and view in the existing App settings page (`apps/jordylab/src/app/app-settings`).
**Why**: matches the constitution and the existing `libs/shared/*/api` layout (tags `scope:shared`, `type:api`). The picker belongs with the other device settings.
**Rejected**: storing the choice on the account (spec says per device; needs a backend change and a migration).

### D5. One bundled font family
**Decision**: Archivo (variable, with the width and weight axes), self-hosted in the app's own files and preloaded; remove the Google Fonts links; the same file is bundled with the sign-in theme. Candidate package: `@fontsource-variable/archivo` (confirm the width axis ships before adopting; otherwise download the font file from the upstream release and commit it).
**Why**: removes a third-party request from the critical path, works offline in the native app (spec FR-008), and lets the policy drop the two Google hosts afterwards.
**Rejected**: keeping three families (the design removes them); system fonts only (loses the identity).
**Follow-up**: once fonts are local and a full signed-in session shows no violation, tighten `style-src` and `font-src` in the policy; not part of this feature.

### D6. Shared presentational components for the repeated pieces
**Decision**: a new `libs/shared/layout` library with `PageHeader`, `EmptyState`, `ErrorState`, `SkeletonRows`, `Switch`, `SpecRow`, `TabBar` ([contracts/shared-ui.md](contracts/shared-ui.md)).
**Why**: the same markup is copied across the screens today (title block 12 times, empty and error boxes 8 each, two switches); the redesign changes all of it, so one copy each is cheaper than twelve edits and keeps the screens consistent.
**Rejected**: leaving the copies (the redesign would multiply the drift the design system already lists as a known gap).

### D7. Phone shell: a tab bar and a More page
**Decision**: below the large breakpoint the app shell shows a top bar and a tab bar with Library, LibBot, Finance and More; the existing scrolling nav row is removed. Finance is shown to administrators only (the Finance group is admin-only today), so other people see three tabs. A new `More` route lists Sources, Consoles, Users and AI models (administrators only, as today) and App settings. The role gating in `app.ts` is reused.
**Reachability of the theme choice**: the App settings navigation item is `nativeOnly` today (`apps/jordylab/src/app/app.ts`, the `App` item), so on the web the page is only reachable by typing its address. Because the theme picker lives there for everyone, the `nativeOnly` flag and its filter are removed, the page shows the picker to all, and only the fingerprint row checks `PlatformService.isNative()`. More therefore always has at least one item.
**Why**: spec FR-010. The nav array in `app.ts` already carries role gates; the tab bar and the More page read from it.
**Open design item** (spec Assumptions): the More page layout is not drawn yet; it is one list of rows using the shared list-row style, designed in the first task of that slice.

### D8. Hard-coded colours become variables, once
**Decision**: replace the ~30 values with the new variables; `cover.ts` returns a plate index, and the plate component reads `--plate-N` and `--plate-N-ink`. Platform chip colours that come from the backend stay as data and are not themed.
**Why**: spec FR-005 and FR-020. A repository check fails the build if a new hex value appears outside the token files and the brand assets.

### D9. Sign-in page follows the device, with two palettes
**Decision**: switch the login theme to follow `prefers-color-scheme` (`darkMode=true`, drop the pinned class) and write its stylesheet as two palettes, Content Led and Paper, in hex. A script compares them with the token table.
**Why**: the page is on another origin and cannot read the stored choice (spec Assumptions).
**Verification (T082)**: the running local Keycloak 26.7.4 serves `<html class="login-pf pf-v5-theme-dark">` today because the theme pins that class with `darkMode=false`; with `darkMode=true` the v2 template's own script is what adds and removes `pf-v5-theme-dark` from `prefers-color-scheme`. The dev Keycloak container is read-only for agents, so the new theme was not loaded into it from here. The stylesheet therefore does not rely on that class: both palettes are selected by `@media (prefers-color-scheme: light)` inside `jordylab.css`, so the page follows the device either way. `kcHtmlClass` is reduced to `login-pf`. The owner can confirm on the next local Keycloak restart (recreating the dev stack needs their yes) by opening the sign-in page with the device set to light and then dark.

### D10. Native shell: colours now, plugin only if needed
**Decision**: update the launcher background, the launcher foreground vector, and `splash_background` (default dark `ground`); regenerate the icon PNGs from the new mark. Add the Capacitor status-bar plugin only if the status bar clashes with a light theme on a real device; the default plan is to add it (one small dependency) because the app can now be light while the status bar stays dark.
**Why**: light themes on a phone need dark status-bar icons. JOR-38 may later remove the native shell; this work is kept to colours and one plugin so little is wasted.

**Status (T081)**: the status-bar plugin is **not** added yet. Whether the status bar clashes with a light theme can only be judged on a real Android device (`quickstart.md` section 6), and none is available to the agent. Launcher, splash and icon colours are done. Left for the owner: HANDOFF-36 (`docs/testing/e2e-test-plan.md`).

### D11. Brand assets
**Decision**: `favicon.svg` becomes the mark with bone tile and ink strokes, with an embedded light-scheme variant; `manifest.webmanifest` colours become the default dark `ground`; the PNG icons and the Keycloak `mark.svg` are regenerated from the same source mark. The wordmark component keeps its structure and takes its two colours from `--foreground` and `--accent-text`.
**Why**: spec FR-017 (no orange). The mark's geometry does not change.

### D12. Tests follow the existing structure
**Decision**: (a) a unit test over the token table checks the contrast contract for all six themes; (b) `accessibility.spec.ts` runs the existing pages in Content Led and Paper on desktop and phone, and the Library in all six; (c) a new `theme.spec.ts` covers choosing, persistence, following the device and no flash; (d) `layout.spec.ts` adds the 360 and 430 widths; (e) `ThemeStore` and each shared component get Spectator specs.
**Why**: matches the repository's tools and the way contrast bugs were found before (BUG-064). The theme under test is set with a stored value injected before load, so tests do not click through Settings for every page.

### D13. Dev harness apps
**Decision**: `apps/fna` and `apps/gamecatalog` pick up the themes through the shared plugin and the default `:root` block; they get the font link change and nothing else.
**Why**: they are test harnesses, not shipped.

## Risks

| Risk | Handling |
|---|---|
| Spartan components use `bg-accent` and `bg-secondary` for hover and fill, which both map to one value (`line`) | check in the first slice on the helm button, input and badge; adjust the mapping, not the components |
| Backend-supplied platform chip colours may not meet contrast on some themes | the chip draws its own fill and ink, so contrast does not depend on the theme; add a data check on the seeded platform list |
| Light themes expose low contrast that dark never showed (borders, placeholders) | the unit contrast test and per-theme axe run catch it before review |
| A boot script that fails silently leaves the default theme on a person's chosen light theme | the script is a few lines, covered by an end-to-end test on a stored light choice |
| Many files change at once | slices are ordered so each leaves the app working (see the plan) |
| Fonts: file size and width axis | confirm the package or file before adoption; subset to Latin |

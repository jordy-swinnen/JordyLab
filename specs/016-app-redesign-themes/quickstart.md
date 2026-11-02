# Quickstart: validating the redesign

How to prove the feature works, slice by slice. Commands run from `jordylab-fe/` unless noted. Pair every check with the matching requirement in [spec.md](spec.md); the data and contracts are in [data-model.md](data-model.md) and [contracts/](contracts/).

## Prerequisites

- Dependencies installed (`bun install`), as for any frontend work.
- For end-to-end checks: the throwaway stack from `e2e/run.sh` (Postgres and Keycloak in labelled containers; do not touch the `jordylab-be-*` dev stack).
- The designs open in a browser for side-by-side comparison (links in Linear JOR-55).

## 1. Theme data and contrast (FR-001, FR-006, FR-007, FR-020)

Run the unit tests of `shared-theme-api`. Expected: all six themes present; every pair in the contrast contract is at least 4.5:1; converting each HSL triplet back to hex equals the table in [contracts/theme-tokens.md](contracts/theme-tokens.md).

Run `tools/check-theme-tokens`. Expected: no raw colour value in application code, and the sign-in stylesheet's two palettes equal Content Led and Paper.

## 2. Choosing and keeping a theme (User Story 1)

Run the new `theme.spec.ts` through `e2e/run.sh web`. Expected, on the throwaway stack:

1. a dark browser with no stored value shows Content Led, a light one shows Paper;
2. choosing each theme in Settings → App changes the page at once and survives a reload;
3. with "Follow device", switching the emulated colour scheme switches the app without a reload;
4. a stored light choice is on the root element **before** the app bundle runs (read it from an init script) and the first screenshot has no dark frame.

By hand: open the app on a phone, choose Heather, close and reopen the app; it opens in Heather.

## 3. Library and detail (User Story 2)

Open `/games/grid` and a game at 390 and 1280 wide in Content Led and Paper. Compare with the design: four columns and two columns, titles clamped to two lines with aligned chips and marks, installed versus not installed told apart without colour, plate covers readable. Run the accessibility spec for the Library and the open Filters panel.

## 4. Other screens (User Story 3)

For each page in `support/pages.ts` plus App settings and the More page: open at 360, 390 and 430 wide; there must be no sideways scroll (`layout.spec.ts`); axe reports no violations in Content Led and Paper. Compare the list of actions on each screen with the old screen (screenshots from before are kept in the pull request description).

## 5. States (User Story 4)

For each data screen, force the three states: delay the API (loading), use a catalog with nothing in it (empty), make the API fail (error). Expected: title stays, placeholders match the design, error has a Try again button that reloads. For LibBot, also check first run, answering, and the daily limit with a guest account at its limit. With `reducedMotion: 'reduce'` emulated, placeholders do not pulse.

## 6. Brand and sign-in (User Story 5)

- Open the app in a browser tab, from the installed icon, and the sign-in page; none shows orange. Check the tab icon on a dark and a light browser.
- Sign in page: emulate dark and light; it must show Content Led and Paper. (First confirm in the local Keycloak that it follows the scheme, research D9.)
- Android: build the app, install on a device or emulator, check the launcher icon, the splash screen, and the status bar in a light theme.
- Android launcher icon follows the theme: change the theme in Settings → App and check the home-screen icon takes that theme's accent (a launcher may take a few seconds). Check it on a fresh install and as an upgrade over an existing install.
- Upgrade path: the launcher entry moved from `MainActivity` to one alias per theme, so a shortcut pinned to the home screen from an older version may stop working after the update and has to be pinned again from the app drawer; the app drawer entry itself stays. Say so in the release notes.

## 7. Whole-app gates

- `bun tools/app-boot-check.mjs` on the production build (CSP enforced): no start-up error, no policy violation.
- `csp.spec.ts` passes: the boot script and the local font raise no violation.
- Lint and unit tests of every touched project pass; the lint-ownership check passes.
- Commit trailer on every commit: `Refs: 016`.

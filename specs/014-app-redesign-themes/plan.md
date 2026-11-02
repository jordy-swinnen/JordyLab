# Implementation Plan: App Redesign with Six Themes

**Branch**: `014-app-redesign-themes` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/014-app-redesign-themes/spec.md`

## Summary

Make the app re-themeable first, then restyle it screen by screen. The foundation is six blocks of the existing colour variables selected by a `data-theme` attribute, a tiny external boot script that sets it before first paint, a signal store that holds the per-device choice, one self-hosted font family, and a unit-tested contrast contract. On that base, a small shared layout library replaces the copy-pasted page header, empty, error and switch markup; the app shell gains a phone tab bar and a More page; the twelve screens are restyled in slices (Library and detail first, then LibBot, Sources and Consoles, then the finance and settings screens), each with its loading, empty and error states. Brand assets, the Android launcher and splash colours, and the sign-in page follow the new default palettes with no orange. No backend, database or API change.

## Technical Context

**Language/Version**: TypeScript on Angular 22 and Nx 23 (Bun); plain JavaScript for the boot script and the token data module; CSS for the sign-in theme; Android resource XML for the native colours.

**Primary Dependencies**: Tailwind 3 with the spartan/ui preset and helm components (unchanged); Capacitor 8 (existing); new: a variable-font package or committed font file for Archivo (research D5) and, if needed, the Capacitor status-bar plugin (research D10).

**Storage**: the browser's local storage, key `jordylab.theme`, one device only. No server or database storage.

**Testing**: Vitest with Spectator for the store and each shared component; a unit test that computes the contrast contract from the token table; Playwright with axe on the existing end-to-end stack (`e2e/run.sh web`) for themes, accessibility, layout and the production Content-Security-Policy; `tools/app-boot-check.mjs` on the production build.

**Target Platform**: current desktop browsers; Android through the existing Capacitor shell; phone browsers (the same build). iOS home-screen use is unchanged.

**Project Type**: web application with a native shell (frontend only for this feature).

**Performance Goals**: no visible flash of a different theme on any load (SC-003); first text paint does not wait for a third-party font host; no added layout shift (the existing stable scrollbar gutter stays).

**Constraints**: the production Content-Security-Policy stays valid (`script-src 'self'` plus its one hash): the boot script is an external same-origin file; no new third-party host; no raw colour values outside the token files and brand assets; the sign-in page cannot read in-app storage; role gating of navigation is unchanged, except that Settings → App becomes reachable by every signed-in person (it holds the theme choice); Finance is the only tab that depends on the role.

**Scale/Scope**: 12 screens × desktop and phone × 6 themes; about 12 files with hard-coded colours; about 150 template usages of the retired label and font classes; 2 new libraries; 3 apps share the token plugin (one is shipped).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I. Clean code | Full descriptive names; small single-purpose components; the boot script is a few lines | Pass |
| II. Fail fast, no silent failures | Unknown stored theme falls back to `system` explicitly; a storage failure keeps the session value and is not hidden in the UI state (documented in the contract) | Pass |
| III. Immutable, builder-first | Theme data is constant and read-only; no Java in this feature | Pass |
| IV. Testing discipline | Vitest + Spectator, `useValue` plain objects with `vi.fn()` and real `signal(...)` instances, no hand-written mock classes; marble tests not needed (no observables added) | Pass |
| V. Language and tooling currency | Angular 22 and Nx 23 features; `inject()`; `#field` private fields | Pass |
| Container–Presentation | Screens keep their container and view split; new shared components are presentational only | Pass |
| Signals, no NgRx; API-backed state in a signal store in the domain `api` lib | `ThemeStore` is a signal store in `libs/shared/theme/api` | Pass |
| Barrel imports | Each new library exports through `index.ts` and a `tsconfig.base.json` path | Pass |

**Post-design re-check (after Phase 1)**: unchanged; the two new libraries are justified in Complexity Tracking. No gate is violated.

## Project Structure

### Documentation (this feature)

```text
specs/014-app-redesign-themes/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   ├── theme-tokens.md  # the six themes and the variable names
│   ├── theme-runtime.md # storage, boot script, store, native and sign-in behaviour
│   └── shared-ui.md     # the shared presentational components
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks, not created here)
```

### Source Code (repository root)

```text
jordylab-fe/
├── tailwind.theme.js                       # plugin entry; imports themes.js; adds the [data-theme] blocks,
│                                           # new component classes (label, chip), removes eyebrow, mono-label, pill caps
├── themes.js                               # NEW plain data: six themes, the 39 tokens each (shared by plugin and tests)
├── apps/jordylab/
│   ├── public/
│   │   ├── theme-boot.js                   # NEW external boot script: sets data-theme only, no colour values (contract theme-runtime)
│   │   ├── favicon.svg, manifest.webmanifest, icons/*.png   # new mark colours
│   │   └── fonts/                          # NEW self-hosted Archivo
│   ├── src/index.html                      # boot script first, font preload, no Google links, no fixed theme-color
│   └── src/app/
│       ├── app.html, app.ts                # phone top bar and tab bar; rail restyled; nav array reused for roles
│       ├── more/                           # NEW More page
│       └── app-settings/                   # theme picker for everyone; fingerprint row Android-only; the nav item no longer native-only
├── apps/fna, apps/gamecatalog/src/index.html   # font link change only (dev harnesses)
├── apps/jordylab-mobile/android/app/src/main/res/   # launcher background and foreground, splash colour; status-bar plugin wiring if needed
├── apps/jordylab-e2e/src/                  # theme.spec.ts NEW; accessibility.spec.ts, layout.spec.ts extended; support/pages.ts + More and App
├── libs/shared/theme/api/                  # NEW ThemeStore, theme list, token types; spec files incl. contrast test
├── libs/shared/layout/                     # NEW PageHeader, EmptyState, ErrorState, SkeletonRows, Switch, SpecRow, TabBar
├── libs/shared/brand/                      # wordmark and mark take colours from variables
├── libs/shared/auth/                       # user menu and biometric toggle restyled (toggle uses the shared Switch)
├── libs/shared/platform/ui/                # install and update prompts become bottom sheets on the new tokens
├── libs/gamecatalog/ui/                    # cover.ts, chips, marks, vote rail, filters, grid, detail, libbot, sources, consoles
├── libs/fna/ui/                            # articles, portfolio, briefing
├── libs/settings/ui/ and nav/              # users, AI models, pending-count badge
├── libs/ui/helm/                           # unchanged unless the variable mapping forces a class fix (risk 1)
└── tools/check-theme-tokens.mjs            # NEW: no raw colours outside token files; sign-in palettes equal the table

jordylab-be/compose/keycloak-theme/jordylab/login/
├── theme.properties                        # follow the device (darkMode)
└── resources/css/jordylab.css, img/mark.svg, fonts/   # two palettes, new mark, bundled font

deploy/containers/frontend/security-headers.conf   # no change in this feature (follow-up: drop the two Google hosts)
```

**Structure Decision**: frontend only. Tokens stay in the one Tailwind plugin that all three apps already use, with their values in a plain data module so tests can read them. Theme runtime state goes in a small `shared/theme/api` library following the repository's `api` and `ui` split; repeated markup goes in a `shared/layout` library. Domain libraries keep their own screens and only change styling and state handling. The native and sign-in work is limited to colours, the font and icons.

## Delivery slices

Each slice leaves the app working and is verified with the matching quickstart step. A slice is a pull-request-sized unit of tasks.

| # | Slice | Delivers | Quickstart |
|---|---|---|---|
| 1 | Theme foundation | `themes.js`, plugin blocks, `:root` default, `color-scheme`, boot script, `ThemeStore`, theme picker in App settings, Archivo self-hosted, contrast unit test, `check-theme-tokens`, first end-to-end theme spec. The old look maps onto Content Led and Flare Orange values. | 1, 2 |
| 2 | Shared layout and shell | `libs/shared/layout`, desktop rail restyle, phone top bar and tab bar (Finance for administrators only), More page, role gates unchanged except that App settings opens for everyone | 4 (shell) |
| 3 | Library and game detail | chips, marks, vote rail, cover plates on variables, grid, filters popover and sheet, detail page, their states | 3, 5 |
| 4 | LibBot, Sources, Consoles | restyle, switch, list rows, LibBot's five states, dialogs | 4, 5 |
| 5 | Finance and settings screens, sign-in in the app | Articles, Portfolio, Briefing, Users, AI models, App settings, awaiting-approval and login screens, user menu, install and update sheets | 4, 5 |
| 6 | Brand, native, sign-in page | favicon, manifest, PNG icons, launcher, splash, status bar, Keycloak theme (two palettes, font, mark) | 6 |
| 7 | Hardening | per-theme accessibility runs, 360 and 430 layout checks, docs (`jordylab-fe/AGENTS.md` theme rules, `ui-design.md` pointer), screenshot comparison with the designs, closing JOR-47 | 7 |

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Two new libraries (`shared/theme/api`, `shared/layout`) | The store and token data are used by the shell, settings, tests and the plugin; the repeated markup is used by every screen | Putting both in `apps/jordylab` would block the domain libraries from using the layout pieces under the module-boundary rules |
| A hand-kept second copy of the two sign-in palettes | The sign-in page lives in another origin and a CSS-only theme cannot import the token data | Generating the stylesheet adds a build step to the identity-provider image; a check script catches drift at lower cost |

# Tasks: App Redesign with Six Themes

**Input**: Design documents from `specs/014-app-redesign-themes/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [quickstart.md](quickstart.md), [contracts/](contracts/)

**Prerequisites**: plan.md and spec.md (required); the three contracts, data model and research (all read for these tasks)

**Tests**: included. The plan's testing approach (research D12) and the quickstart require unit, component and end-to-end checks, so they are tasks, not optional extras.

**Organization**: grouped by user story (spec.md priorities). Setup and Foundational come first and block every story.

**Paths**: all paths are relative to the repository root and were taken from the codebase survey of 2026-11-02. Before editing a file, confirm its path with `find`; if a component has moved, update the task's path in this file first.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 to US5, mapped to the spec's user stories; Setup, Foundational and Final Phase have none

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: create the files and libraries that the later phases fill in.

- [X] T001 Create the theme data module `jordylab-fe/themes.js`: for each of the six themes an `id`, a display `name`, a `kind` (`dark` or `light`) and the 39 colour tokens as hex values, exactly as in `specs/014-app-redesign-themes/contracts/theme-tokens.md`; export the list of themes and the default ids. It is the only place colour values are authored.
- [X] T002 [P] Add the Archivo font files (Latin subset) to `jordylab-fe/apps/jordylab/public/fonts/` and an `@font-face` rule with `font-display: swap` to `jordylab-fe/apps/jordylab/src/styles.css`. First confirm that the package (`@fontsource-variable/archivo`) or the upstream release ships a width axis; if not, use the weight axis only and record that in `specs/014-app-redesign-themes/research.md` (D5). The body font stack ends in `system-ui, sans-serif` so text stays readable if the file fails to load.
- [X] T003 [P] Scaffold the store library: `jordylab-fe/libs/shared/theme/api/project.json` (tags `scope:shared`, `type:api`), `jordylab-fe/libs/shared/theme/api/src/index.ts`, and the path alias `@jordylab-fe/shared/theme/api` in `jordylab-fe/tsconfig.base.json`; the repository's 80 percent line-coverage gate applies to this library.
- [X] T004 Scaffold the layout library: `jordylab-fe/libs/shared/layout/project.json` (tags `scope:shared`, `type:ui`), `jordylab-fe/libs/shared/layout/src/index.ts`, and the path alias `@jordylab-fe/shared/layout` in `jordylab-fe/tsconfig.base.json` (after T003 has edited that file); the 80 percent line-coverage gate applies to this library.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the token mechanism, the boot script, the shared components and the phone shell. Every user story depends on these.

**CRITICAL**: no user story work starts until this phase is complete.

- [X] T005 Rewrite `jordylab-fe/tailwind.theme.js` to read `jordylab-fe/themes.js`: convert each hex value to an HSL triplet when the plugin loads; emit `:root` with the Content Led values and one `[data-theme="<id>"]` block per theme with `color-scheme`; keep every existing variable name; add the new names from the contract (`--accent-text`, `--installed`, `--mark-want`, `--mark-liked`, `--mark-disliked`, `--plate-1`…`--plate-6`, `--plate-1-ink`…`--plate-6-ink`, `--series-1`…`--series-4`, `--platform-*`, `--rom-validated`, `--rom-validated-ink`); set `--destructive` equal to `--mark-liked` in each theme; remove `--iris` and `--deep`; keep `--rail`.
- [X] T006 Register the new tokens as Tailwind utilities in `jordylab-fe/tailwind.theme.js` (`extend.colors`, each as `hsl(var(--x) / <alpha-value>)`): `accent-text`, `installed`, `mark-want`, `mark-liked`, `mark-disliked`, `series-1`…`series-4`, `platform-steam`, `platform-ps`, `platform-snes` (and their `-ink` pairs), `rom-validated`, `rom-validated-ink`; add the component classes `.plate-1`…`.plate-6` (background `var(--plate-N)`, text `var(--plate-N-ink)`). Later tasks use these class names.
- [X] T007 Migrate every use of `iris` and `deep` in `jordylab-fe/apps` and `jordylab-fe/libs` (for example `text-iris`, `bg-iris`, `bg-deep` in `jordylab-fe/libs/fna/ui/src/lib/article-list/article-list-view.component.ts` and `jordylab-fe/libs/shared/auth/src/lib/user-menu.component.ts`) to `series-2` or `primary`, and delete only the `iris` and `deep` entries from the `colors` block of `jordylab-fe/tailwind.theme.js` once no use remains. Keep the `rail` colour: `bg-rail` is used by the app shell.
- [X] T008 Keep the old component classes working during the migration: in `jordylab-fe/tailwind.theme.js` make `.eyebrow` and `.mono-label` render the new `label` style and `.pill` the new pill style, so untouched screens still render. Add a comment marking them for removal in T086.
- [X] T009 [P] Create `jordylab-fe/apps/jordylab/public/theme-boot.js` as specified in `specs/014-app-redesign-themes/contracts/theme-runtime.md` (no imports, no inline script, no colour values; it only sets `data-theme`); in `jordylab-fe/apps/jordylab/src/index.html` load it before any stylesheet, preload the Archivo font file, remove the three Google Fonts links, and set `viewport-fit=cover` on the viewport meta tag so the safe-area inset is available, and replace the fixed `theme-color` meta tag with two static ones selected by `media="(prefers-color-scheme: dark)"` and `light` (the Content Led and Paper grounds).
- [X] T010 [P] Remove the Google Fonts links from `jordylab-fe/apps/fna/src/index.html` and `jordylab-fe/apps/gamecatalog/src/index.html`; the dev harnesses use the default `:root` theme and need no boot script.
- [X] T011 [P] Write `jordylab-fe/libs/shared/theme/api/src/lib/theme-contrast.spec.ts`: import the theme data from `jordylab-fe/themes.js`; for every theme assert each pair in the contrast contract of `specs/014-app-redesign-themes/contracts/theme-tokens.md` is at least 4.5:1 (WCAG relative luminance); assert each HSL triplet converts to the hex value in the table.
- [X] T012 [P] Create `jordylab-fe/tools/check-theme-tokens.mjs`: fail when a hex colour literal appears in `jordylab-fe/apps` or `jordylab-fe/libs` outside an allow list (`jordylab-fe/themes.js`, brand assets, the two static `theme-color` metas in `jordylab-fe/apps/jordylab/src/index.html`, the web app manifest, spec and mock files, platform colours supplied by the backend); report the file and line for each hit. The sign-in palette comparison is added in T085.
- [X] T013 Review the spartan helm variants against the new variable mapping (research risk 1): list which variables `jordylab-fe/libs/ui/helm/ui-badge-helm`, `ui-input-helm`, `ui-card-helm`, `ui-skeleton-helm` and the button helm (`@spartan-ng/ui-button-helm`) read (`bg-accent`, `bg-secondary`, `bg-muted`, `text-muted-foreground`, `ring`, …), add each text-on-fill pair to `jordylab-fe/libs/shared/theme/api/src/lib/theme-contrast.spec.ts`, and if a pair fails or a hover state vanishes, change the mapping in `specs/014-app-redesign-themes/contracts/theme-tokens.md` and `jordylab-fe/themes.js` rather than the helm files.
- [X] T014 Create `jordylab-fe/libs/shared/layout/src/lib/page-header.component.ts` (`PageHeaderComponent`: inputs `title` and optional `subtitle`, content projection for actions) per `specs/014-app-redesign-themes/contracts/shared-ui.md`, with `page-header.component.spec.ts` beside it (Spectator).
- [X] T015 [P] Create `jordylab-fe/libs/shared/layout/src/lib/empty-state.component.ts` (`EmptyStateComponent`: inputs `title`, `hint`, optional `actionLabel`, `actionKind` `primary` or `secondary`; output `actionClicked`) with its spec.
- [X] T016 [P] Create `jordylab-fe/libs/shared/layout/src/lib/error-state.component.ts` (`ErrorStateComponent`: input `message`, output `retry`, a "Try again" button, `role="alert"`) with its spec.
- [X] T017 [P] Create `jordylab-fe/libs/shared/layout/src/lib/skeleton-rows.component.ts` (`SkeletonRowsComponent`: inputs `count` default 4 and `height` default 84; composes the existing `hlm-skeleton` from `@spartan-ng/ui-skeleton-helm`) with its spec.
- [X] T018 [P] Create `jordylab-fe/libs/shared/layout/src/lib/switch.component.ts` (`SwitchComponent`: inputs `checked` and `label`; output `checkedChange`; a real `button` with `role="switch"` and `aria-checked`; 46 by 27 pixels) with its spec.
- [X] T019 [P] Create `jordylab-fe/libs/shared/layout/src/lib/spec-row.component.ts` (`SpecRowComponent`: input `label`, value in the content slot; a row with a bottom border) with its spec.
- [X] T020 [P] Create `jordylab-fe/libs/shared/layout/src/lib/tab-bar.component.ts` (`TabBarComponent`: input list of `{ label, icon, route, active }` supplied by the shell, so it draws three or four items; 76 pixels tall plus `env(safe-area-inset-bottom)` of bottom padding, every item at least 44 pixels, the active item marked with a 2-pixel accent bar and `aria-current`) with its spec.
- [X] T021 Export every layout component from `jordylab-fe/libs/shared/layout/src/index.ts`.
- [X] T022 Change the shell in `jordylab-fe/apps/jordylab/src/app/app.html` and `jordylab-fe/apps/jordylab/src/app/app.ts`: below the large breakpoint show a top bar (brand mark, wordmark, avatar) and the `TabBarComponent` with Library, LibBot, Finance (only when the person is an administrator, reusing the Finance group's role gate) and More; remove the scrolling nav row on phones; keep the desktop rail's groups; keep every other role gate of the existing nav array. Needs the route from T023 and the component from T020.
- [X] T023 Create the More page `jordylab-fe/apps/jordylab/src/app/more/more-page.component.ts` listing Sources, Consoles, Users and AI models (the administrator-only items, filtered by the same role gates as the nav array) and App settings (everyone); add its route at the path `more` in `jordylab-fe/apps/jordylab/src/app/app.routes.ts` (page title "More").
- [X] T024 [P] Write `jordylab-fe/apps/jordylab/src/app/more/more-page.component.spec.ts` (Spectator, `useValue` plain objects with `vi.fn()`): an administrator sees all five items; a guest sees only App settings; the shell shows three tabs for a guest and four for an administrator; the tab bar marks Finance active on `/fna` routes.
- [X] T025 Add the new pages to `jordylab-fe/apps/jordylab-e2e/src/support/pages.ts` so the accessibility, layout and policy specs visit them: `{ name: 'More', path: '/more', heading: 'More' }` and `{ name: 'Settings App', path: '/settings/app', heading: 'App' }`.

**Checkpoint**: tokens, boot script, shared components and the phone shell are in place. The app still renders the Content Led look everywhere.

---

## Phase 3: User Story 1 - See and choose a theme (Priority: P1) MVP

**Goal**: a person can pick Follow device or any of the six themes in Settings, and the choice applies at once and is remembered on that device.

**Independent Test**: open Settings → App, choose each theme, reload, and confirm the page, navigation and Library change immediately and keep the choice; choose Follow device and switch the emulated colour scheme.

### Tests for User Story 1

- [X] T026 [P] Write `jordylab-fe/libs/shared/theme/api/src/lib/theme.store.spec.ts`: a dark device with no stored value resolves to `content`; a light device to `paper`; a stored id wins; an unknown stored value is treated as `system`; a storage read or write that throws keeps the session value without an error; a device change re-resolves when following; the `theme-color` meta takes the computed `--background`; `THEME_DEFINITIONS` and the id list in `jordylab-fe/apps/jordylab/public/theme-boot.js` (read as text) equal the list in `jordylab-fe/themes.js` (a root file, not another lib). Use `vi.fn()` for storage and `matchMedia`, and real `signal(...)` where state is involved.

### Implementation for User Story 1

- [X] T027 [US1] Create `jordylab-fe/libs/shared/theme/api/src/lib/theme-preference.model.ts`: the `ThemeId` and `ThemePreference` types and the constant `THEME_DEFINITIONS` (id, name, kind; no colours), typed by hand in this file; do not import the root `themes.js` into library code (T026 checks the two lists stay equal).
- [X] T028 [US1] Create `jordylab-fe/libs/shared/theme/api/src/lib/theme.store.ts` (`ThemeStore`, `#private` signal fields, `inject()`): read-only `preference` and `activeTheme`, the `themes` list, and `choose()`; read and write the key `jordylab.theme` inside try/catch; follow `prefers-color-scheme` while the preference is `system`; set `data-theme`, and replace the `theme-color` metas with the computed `--background`, as in `contracts/theme-runtime.md`.
- [X] T029 [US1] Export `ThemeStore`, `ThemeId`, `ThemePreference` and `THEME_DEFINITIONS` from `jordylab-fe/libs/shared/theme/api/src/index.ts`.
- [X] T030 [US1] Start the store at boot in `jordylab-fe/apps/jordylab/src/main.ts` (or the application config it calls) so the theme-colour meta stays correct after the first load.
- [X] T031 [US1] Make Settings → App reachable on the web: in `jordylab-fe/apps/jordylab/src/app/app.ts` remove `nativeOnly` from the `App` nav item and delete the now unused `nativeOnly` field and its filter in `visibleGroups`, so every signed-in person sees the item on the desktop rail and under More (the item is the only user of the flag; check with `grep` first).
- [X] T032 [US1] Create `jordylab-fe/apps/jordylab/src/app/app-settings/theme-picker.component.ts`: a radio group with "Follow device", then the dark group (Content Led, Oxblood, Flare Orange), then the light group (Paper, Glasshouse, Heather); each choice calls `ThemeStore.choose`; with a spec beside it.
- [X] T033 [US1] Add the picker to `jordylab-fe/apps/jordylab/src/app/app-settings/app-settings-page.component.ts` above the biometric toggle, keep the page title "App", add an "Appearance" section heading for the picker and update the description, and render the biometric toggle only when `PlatformService.isNative()` is true. Needs T031.
- [ ] T034 [US1] Write `jordylab-fe/apps/jordylab-e2e/src/theme.spec.ts`: with no stored choice a dark-emulated browser shows Content Led and a light one shows Paper; in the browser (not the native app) an administrator and a guest (`support/guest.ts`) can both open Settings → App from the navigation and choose a theme; a choice survives reload; with Follow device, changing the emulated colour scheme switches the app without reload; a stored light choice is already on the root element before the bundle runs (read it from an init script); no first frame in another theme.

**Checkpoint**: User Story 1 works on its own. The theme can be chosen and is applied everywhere.

---

## Phase 4: User Story 2 - Browse the Library in the new look on phone and desktop (Priority: P1)

**Goal**: the Library and game detail use the new look with the same information and actions, four covers per row on desktop and two on a phone.

**Independent Test**: open the Library and a game on a 390-pixel and a 1280-pixel window in Content Led and Paper; every item from the spec is present, tappable at 44 pixels, and readable.

### Tests for User Story 2

- [X] T035 [P] [US2] Update `jordylab-fe/libs/gamecatalog/ui/src/lib/cover.spec.ts` for the plate index: the same title always gets the same index from 0 to 5.
- [X] T036 [US2] Write Spectator specs for the Library grid and game detail covering the loading, empty, error and loaded states, in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.spec.ts` and `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.spec.ts`.

### Implementation for User Story 2

- [X] T037 [US2] In `jordylab-fe/libs/gamecatalog/ui/src/lib/cover.ts`, replace the hex palette with `coverPlateIndex(title)` returning 0 to 5 (the same hash), and keep `coverInitials`.
- [X] T038 [US2] In `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.html`, render the cover fallback with the class `plate-{index}` using `var(--plate-N)` and `var(--plate-N-ink)`, and remove the inline `style` colours.
- [X] T039 [P] [US2] Restyle `jordylab-fe/libs/gamecatalog/ui/src/lib/chips/status-chip.component.ts`: installed uses `--installed` with a solid 1.5-pixel border and a check icon; not installed uses `--text-3` with a dashed border; remove the hex values.
- [X] T040 [P] [US2] Restyle `jordylab-fe/libs/gamecatalog/ui/src/lib/chips/rom-chip.component.ts`: validated uses `--rom-validated` with `--rom-validated-ink`; broken, unknown and mixed use the outline styles with variables; remove the hex values.
- [X] T041 [P] [US2] Restyle `jordylab-fe/libs/gamecatalog/ui/src/lib/chips/mark-chip.component.ts` and `jordylab-fe/libs/gamecatalog/ui/src/lib/marks/mark-buttons.component.ts`: the three marks use `--mark-want`, `--mark-liked` and `--mark-disliked` with a 15 percent tint; remove the hex values.
- [X] T042 [P] [US2] In `jordylab-fe/libs/gamecatalog/ui/src/lib/marks/vote-rail.component.html`, replace the `#0D0B13` backdrop with `--ground` at 85 percent.
- [X] T043 [US2] Restyle the filter bar in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/filter-bar.component.html`: on desktop one row (search, Filters with count badge, Sort); on a phone the search takes the full row, Filters becomes a 44-pixel icon button with the badge at its corner, and Sort and the active-filter chip share the next row with the count at the right; keep every `data-testid`.
- [X] T044 [US2] Restyle `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/filters-panel.component.html`: a popover from 641 pixels, a bottom sheet below it with a sticky "Show N games" primary button; keep the dialog labelled "Filters".
- [X] T045 [US2] Restyle the Library header and grid in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.html`: `PageHeaderComponent` in place of the eyebrow and title; four columns on desktop, two on a phone; titles clamped to two lines with a fixed height; the source line; platform and install chips; the three mark buttons filling the card width; the vote count on the cover where marks exist.
- [X] T046 [US2] Add the loading state to `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.html` with `SkeletonRowsComponent` in the shape of a 2:3 cover, ten on desktop and four on a phone.
- [X] T047 [US2] Add the empty and error states to `jordylab-fe/libs/gamecatalog/ui/src/lib/game-grid/game-grid-view.component.html`: `EmptyStateComponent` with "No games match these filters." and "Clear filters"; `ErrorStateComponent` for a failed load; the mark error banner uses `--mark-liked`.
- [X] T048 [US2] Restyle the game detail page in `jordylab-fe/libs/gamecatalog/ui/src/lib/game-detail/game-detail-view.component.html`: cover above the title on a phone, 250-pixel cover on desktop; mark buttons with text labels; spec rows via `SpecRowComponent`; Ask LibBot and Regenerate actions; loading, not-found ("This game is not in your catalog.") and error states with the designed copy.
- [X] T049 [P] [US2] Restyle `jordylab-fe/libs/gamecatalog/ui/src/lib/dialogs/confirm-dialog.component.html`: surface, `line-strong` border, `shadow-modal`; the warning button uses `--mark-liked` (the design system has no separate warning token) with `--accent-ink`-style text; remove the hex values.
- [ ] T050 [US2] Extend `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts`: run the Library and one game detail in all six themes on desktop and phone; set the theme with an init script.

**Checkpoint**: User Stories 1 and 2 both work. The MVP (themes plus the Library) can be shipped.

---

## Phase 5: User Story 3 - Every other screen in the new look (Priority: P2)

**Goal**: LibBot, Sources, Consoles, Articles, Portfolio, Briefing, Users, AI models, App settings, the sign-in screens and the install and update sheets use the same look, keep their actions, and stack on a phone.

**Independent Test**: for each screen compare the actions and information with the current app on desktop and phone, in two themes; no sideways scroll at 360, 390 and 430 pixels.

**Coordination**: several of these tasks share a file with the User Story 4 tasks. Do the two in one pass per file, or in order, never in parallel.

### Implementation for User Story 3

- [ ] T051 [US3] Restyle LibBot in `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot-view.component.html`: `PageHeaderComponent`; the user bubble in `--accent`; the answer without a bubble; applied-filter chips with a dashed `--accent-text` border; reference chips; the composer with a 48-pixel field and an Ask button; remove the amber and hex classes.
- [ ] T052 [US3] Restyle Sources in `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.html`: the two action panels stack on a phone; the stat tiles; scan source rows in the list-row style; platform chips; replace the hand-built enable switch with `SwitchComponent`; keep the Steam token help.
- [ ] T053 [US3] Restyle Consoles in `jordylab-fe/libs/gamecatalog/ui/src/lib/consoles/consoles-view.component.html`: one column on a phone; the console list rows with the selected ring; replace the hand-built removal dialog with `lib-confirm-dialog` from `jordylab-fe/libs/gamecatalog/ui/src/lib/dialogs/`; the search result rows.
- [ ] T054 [P] [US3] Restyle the bulk-add page `jordylab-fe/libs/gamecatalog/ui/src/lib/consoles/consoles-bulk.component.ts` with the panel style and the shared input style.
- [ ] T055 [P] [US3] Restyle Articles in `jordylab-fe/libs/fna/ui/src/lib/article-list/article-list-view.component.html`: day headings; rows with the time, a 21-pixel title (17 on a phone) and a feed tag in a token colour; keep the staggered fade-in; remove the inline hex colours from its `style` block.
- [ ] T056 [P] [US3] Restyle Portfolio in `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.ts`: the total value; the allocation bar with `--series-1` to `--series-4` and a text legend; holdings as a table on desktop and as rows on a phone; the add form stacks on a phone.
- [ ] T057 [P] [US3] Restyle Briefing in `jordylab-fe/libs/fna/ui/src/lib/briefing-display/briefing-display-view.component.html`: the spinner colours through tokens; the aside panel moves below on a phone; the dashed "Not financial advice." note; the `.prose` colours through variables in `jordylab-fe/tailwind.theme.js`.
- [ ] T058 [P] [US3] Restyle Users in `jordylab-fe/libs/settings/ui/src/lib/users-page/users-page-view.component.html`: the three groups; list rows; Approve and Reject buttons; the temporary-password notice in `--accent-text`.
- [ ] T059 [P] [US3] Restyle AI models in `jordylab-fe/libs/settings/ui/src/lib/ai-models-page/ai-models-page-view.component.html`: feature rows; the model picker panel; one column on a phone.
- [ ] T060 [US3] Restyle App settings in `jordylab-fe/apps/jordylab/src/app/app-settings/app-settings-page.component.ts`: spec rows; the biometric unlock as `SwitchComponent` (shown on the Android app only); the version row. Runs after T033 because both edit this file.
- [ ] T061 [P] [US3] Replace the biometric toggle in `jordylab-fe/libs/shared/auth/src/lib/biometric-unlock-toggle.component.ts` with `SwitchComponent` and restyle its panel; update its spec.
- [ ] T062 [P] [US3] Restyle the user menu in `jordylab-fe/libs/shared/auth/src/lib/user-menu.component.ts`: the avatar uses `--series-2`; the menu uses the card tokens; remove the hex values.
- [ ] T063 [P] [US3] Restyle the sign-in card in `jordylab-fe/libs/shared/auth/src/lib/login.component.ts`: a 400-pixel card on `--card`; the mark tile; the primary Sign in button; the outline fingerprint button; errors in `--mark-liked`.
- [ ] T064 [P] [US3] Restyle the awaiting-approval page in `jordylab-fe/apps/jordylab/src/app/awaiting-approval/` with `PageHeaderComponent` and the panel style.
- [ ] T065 [P] [US3] Restyle the install and update sheets in `jordylab-fe/libs/shared/platform/ui/src/lib/install-prompt.component.ts`, `update-available-banner.component.ts` and `update-required.component.ts`: a bottom sheet on `--card` with 18-pixel top corners and `shadow-modal`; the primary action full width. Keep the existing test hooks.
- [ ] T066 [US3] Extend `jordylab-fe/apps/jordylab-e2e/src/layout.spec.ts` (which already checks the pages in `jordylab-fe/apps/jordylab-e2e/src/support/pages.ts` at 360, 390 and 430 pixels for sideways scrolling, so the new pages need only the PAGES task above): on the phone size, every visible `button`, `a[href]`, `[role=switch]` and `[role=radio]` outside running text has a bounding box at least 44 pixels high and wide, and the failure names each element that is smaller.

**Checkpoint**: User Story 3 works on its own: every screen is in the new look.

---

## Phase 6: User Story 4 - Clear loading, empty and error states (Priority: P2)

**Goal**: every data screen shows the designed loading, empty and error states, and LibBot shows its five states; placeholders stop pulsing when reduced motion is requested.

**Independent Test**: for each screen force the loading state (delayed response), the empty state (no data) and the error state (failed request); compare with the designs.

**Coordination**: these tasks edit the same templates as User Story 3; run them after the User Story 3 task for the same file.

### Tests for User Story 4

- [ ] T067 [P] [US4] Write `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot.state.spec.ts` (or the existing LibBot container spec): the state is `firstRun` with no conversation, `answering` while a question is in flight, `limit` when the daily allowance is used, `error` on failure, and `loaded` otherwise.

### Implementation for User Story 4

- [ ] T068 [US4] Derive the LibBot state in its container `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot.component.ts` from the existing store signals and pass one `state` value to the view.
- [ ] T069 [US4] Render the five LibBot states in `jordylab-fe/libs/gamecatalog/ui/src/lib/libbot/libbot-view.component.html`: first run with three example questions; answering with the three-stage `role="status"` list (pulse only with motion allowed); limit with the disabled composer and the message; error with Try again. Runs after T051.
- [ ] T070 [P] [US4] Add loading, empty and error states to `jordylab-fe/libs/gamecatalog/ui/src/lib/source-manager/source-manager-view.component.html` with `SkeletonRowsComponent`, `EmptyStateComponent` ("No sources yet.", "Download scan client") and `ErrorStateComponent`. Runs after T052.
- [ ] T071 [P] [US4] Add loading, empty and error states to `jordylab-fe/libs/gamecatalog/ui/src/lib/consoles/consoles-view.component.html` ("No consoles yet.", "Add a console"). Runs after T053.
- [ ] T072 [P] [US4] Add loading, empty and error states to `jordylab-fe/libs/fna/ui/src/lib/article-list/article-list-view.component.html` ("No articles yet."), `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.ts` ("No holdings yet.", "Add holding") and `jordylab-fe/libs/fna/ui/src/lib/briefing-display/briefing-display-view.component.html` ("No briefing yet.", "Generate briefing"). Runs after T055 to T057.
- [ ] T073 [P] [US4] Add loading, empty and error states to `jordylab-fe/libs/settings/ui/src/lib/users-page/users-page-view.component.html` ("No one is waiting for approval.") and `jordylab-fe/libs/settings/ui/src/lib/ai-models-page/ai-models-page-view.component.html`. Runs after T058 and T059.
- [ ] T074 [US4] Honour reduced motion: `SkeletonRowsComponent` in `jordylab-fe/libs/shared/layout/src/lib/skeleton-rows.component.ts` and the spinner classes in `jordylab-fe/apps/jordylab/src/styles.css` stop animating under `prefers-reduced-motion: reduce`.
- [ ] T075 [US4] Write `jordylab-fe/apps/jordylab-e2e/src/states.spec.ts`: a delayed API response shows the loading state on Library and Sources; an aborted response shows the error state with Try again; an empty catalog shows the empty state; LibBot with no conversation shows first run; with reduced motion emulated the placeholders do not animate.

**Checkpoint**: User Story 4 works on its own: every state matches the design.

---

## Phase 7: User Story 5 - A new brand identity everywhere, with no orange (Priority: P3)

**Goal**: the browser icon, app icon, splash screen, sign-in page and brand components use the default theme's colours; the sign-in page follows the device's light or dark setting.

**Independent Test**: install the app and open it in a browser tab and on the sign-in page; none shows the old orange, and the sign-in page is dark or light with the device.

### Implementation for User Story 5

- [ ] T076 [US5] Redraw `jordylab-fe/apps/jordylab/public/favicon.svg` with a bone (`#ECE8DD`) tile and `#141414` strokes; make the same change to `jordylab-fe/apps/fna/public/favicon.svg` and `jordylab-fe/apps/gamecatalog/public/favicon.svg`.
- [ ] T077 [P] [US5] Update `jordylab-fe/apps/jordylab/public/manifest.webmanifest`: `background_color` and `theme_color` become `#131314`.
- [ ] T078 [US5] Add `@resvg/resvg-js` to `jordylab-fe/package.json` as a dev dependency and create `jordylab-fe/tools/render-icons.mjs` that uses it to render the new mark into `jordylab-fe/apps/jordylab/public/icons/icon-192.png`, `icon-512.png` and `apple-touch-icon.png`; run it and commit the output files.
- [ ] T079 [P] [US5] Make the brand mark use theme variables: `jordylab-fe/libs/shared/brand/src/lib/brand-mark.component.ts` defaults to `var(--accent)` for the tile and `var(--accent-ink)` for the strokes; `jordylab-fe/libs/shared/brand/src/lib/wordmark.component.ts` uses `var(--foreground)` and `var(--accent-text)`.
- [ ] T080 [P] [US5] Update the Android colours: `jordylab-fe/apps/jordylab-mobile/android/app/src/main/res/values/ic_launcher_background.xml` (`#ECE8DD`, and `splash_background` `#131314`); the ink colour in `jordylab-fe/apps/jordylab-mobile/android/app/src/main/res/drawable-v24/ic_launcher_foreground.xml` (`#141414`).
- [ ] T081 [US5] Status bar on the native app, only if needed: after the device check in `quickstart.md` section 6, add `@capacitor/status-bar` to `jordylab-fe/package.json` and `jordylab-fe/apps/jordylab-mobile`, and set its colour and icon style from `ThemeStore.choose` in `jordylab-fe/libs/shared/theme/api/src/lib/theme.store.ts` when `Capacitor.isNativePlatform()`. Skip this task and record the decision in `research.md` if the device check passes without it.
- [ ] T082 [US5] Verify, before any change, that the sign-in page can follow the device: in the local Keycloak 26.7 check that `darkMode=true` with the v2 theme switches with `prefers-color-scheme`, and what happens to `kcHtmlClass`; record the result in `specs/014-app-redesign-themes/research.md` (decision D9). If it does not follow the device, record the fallback chosen before the stylesheet task starts.
- [ ] T083 [P] [US5] Restyle the Keycloak login stylesheet `jordylab-be/compose/keycloak-theme/jordylab/login/resources/css/jordylab.css`: two palettes under `prefers-color-scheme` (Content Led and Paper); remove `#ff6a3d`, `#ff8a66` and the Bricolage and Martian Mono imports; add the Archivo `@font-face` pointing at a bundled file in `jordylab-be/compose/keycloak-theme/jordylab/login/resources/fonts/`; then, using the result of the verification task before it, change `jordylab-be/compose/keycloak-theme/jordylab/login/theme.properties` (`darkMode` and `kcHtmlClass`).
- [ ] T084 [P] [US5] Replace `jordylab-be/compose/keycloak-theme/jordylab/login/resources/img/mark.svg` with the new mark.
- [ ] T085 [US5] Add the sign-in palette check to `jordylab-fe/tools/check-theme-tokens.mjs`: read the two palettes from `jordylab-be/compose/keycloak-theme/jordylab/login/resources/css/jordylab.css` and fail when they differ from the Content Led and Paper values in `jordylab-fe/themes.js`. Run from the repository root.
- [ ] T086 [US5] Run the production checks: `bun tools/app-boot-check.mjs` on the built app with the boot script in place, and `jordylab-fe/apps/jordylab-e2e/src/csp.spec.ts`; no policy violation is allowed.

**Checkpoint**: User Story 5 works on its own: no orange anywhere in the default theme.

---

## Final Phase: Polish & Cross-Cutting Concerns

**Purpose**: remove the migration aliases, the old fonts and the old classes; verify every theme; document and close out.

- [ ] T087 Remove the alias classes `.eyebrow`, `.mono-label` and `.pill` (from T008) in `jordylab-fe/tailwind.theme.js`, and replace every remaining use in `jordylab-fe/apps` and `jordylab-fe/libs`: those classes, the `font-mono` and `font-display` utilities (56 uses) and the `uppercase` and wide-tracking label utilities (16 uses) become the `label` style or the page-title component, in sentence case; confirm `grep` finds none left.
- [ ] T088 Remove the Bricolage Grotesque, Martian Mono and Hanken Grotesk references from `jordylab-fe/tailwind.theme.js` (`fontFamily`, body and `.prose` rules) and confirm nothing else in `jordylab-fe/apps` or `jordylab-fe/libs` names them.
- [ ] T089 Extend `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts` to run every page in `jordylab-fe/apps/jordylab-e2e/src/support/pages.ts` in Content Led and Paper on desktop and phone. Runs after the User Story 2 accessibility task in the same file, which already covers the Library and a game detail in all six themes; add only the pages and themes not yet covered and remove any duplicate loop.
- [ ] T090 Update `jordylab-fe/AGENTS.md` with a short theme section (tokens live in `jordylab-fe/themes.js`, no raw colours, the boot script), and add a note at the top of `specs/013-gamecatalog-refinement-ai/ui-design.md` that its colour and filter direction is superseded by spec 014.
- [ ] T091 Write `specs/014-app-redesign-themes/checklists/visual-review.md`: compare each screen with the design system on desktop and phone, the Library in all six themes; record each difference and its agreement (SC-008). Also compare the number of steps to find a game and set a mark before and after the redesign, on desktop and on a phone (SC-007).
- [ ] T092 Write `jordylab-fe/apps/jordylab-e2e/src/edge-cases.spec.ts` for the spec's edge cases: with the font files blocked (route abort) the Library and Settings still show readable text and no sideways scroll; with `forcedColors: 'active'` emulated, buttons, links and the theme picker stay visible and identifiable; at 320 pixels wide the Library and Settings do not scroll sideways.
- [ ] T093 Run the unit tests and lint of every touched project, `jordylab-fe/tools/check-lint-ownership.sh`, and `jordylab-fe/tools/check-theme-tokens.mjs`; check that both new libraries meet the 80 percent line-coverage gate; fix every finding. Then record the closing commits on Linear JOR-47 (cover-plate contrast, closed by this work) and JOR-55.

---

## Dependencies & Execution Order

### Phase dependencies

- **Setup (Phase 1)**: no dependencies.
- **Foundational (Phase 2)**: depends on Setup; blocks every user story.
- **User Stories (Phases 3 to 7)**: start after Foundational. User Stories 1 and 2 are independent of each other. User Story 3 and User Story 4 share files and must be ordered per file. User Story 5 depends on the tokens (Foundational) and on `ThemeStore` (User Story 1) for T081 only.
- **Final Phase**: after every user story; T087 and T088 after all screens are migrated.

### Within each story

- Tests first where the task list has them, then the implementation.
- Each screen's task runs before its User Story 4 task.

### Parallel opportunities

- Setup: T002 and T003 together.
- Foundational: T009, T010, T011, T012 together; T015 to T020 together (each in its own file); then T014 before the barrel T021.
- User Story 1: T026 with the model T027; the picker T032 after the store.
- User Story 2: the chip and mark tasks T039 to T042 and T049 together; the grid tasks T045 to T048 are sequential because they share one template.
- User Story 3: the screens T054 to T059 and the auth and platform tasks T061 to T065 can run in parallel (different files).
- User Story 5: T077, T079, T080, T083, T084 together.

---

## Implementation Strategy

### MVP first (recommended)

1. Complete Setup and Foundational (T001 to T025).
2. Complete User Story 1 (T026 to T034): themes can be chosen and applied.
3. Complete User Story 2 (T035 to T050): the Library and detail are in the new look in all six themes.
4. Stop and validate with `quickstart.md` sections 1 to 3. This is the MVP: the app is re-themed and the most used screen is redesigned.

### Incremental delivery

1. MVP above.
2. User Story 3 per screen (each screen is its own pull request), then User Story 4 for that screen.
3. User Story 5 (brand, native, sign-in).
4. Final Phase.

### Notes

- Each task names one file or one tightly related set of files; a task that touches more than one area is split before it starts.
- `[P]` only when the files differ and no earlier task is unfinished.
- Every commit carries the trailer `Refs: 014` (and `Refs: 014/T###` where it helps), as the repository's commit rules require.

---

## Summary

- **Total tasks**: 93
- **Per phase**: Setup 4 · Foundational 21 · User Story 1 (P1) 9 · User Story 2 (P1) 16 · User Story 3 (P2) 16 · User Story 4 (P2) 9 · User Story 5 (P3) 11 · Final Phase 7
- **Parallel tasks** (marked `[P]`): 41
- **MVP scope**: User Stories 1 and 2 after Setup and Foundational (T001 to T050)
- **Format check**: every task line starts with `- [ ] T###`, carries `[US#]` in the story phases only, and names a file path.

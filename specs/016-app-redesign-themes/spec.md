# Feature Specification: App Redesign with Six Themes

**Feature Branch**: `016-app-redesign-themes`

**Created**: 2026-11-02

**Status**: Draft

**Tracking**: Linear JOR-55

**Module**: the web app and the Android app that wraps it (all screens), the sign-in page of the identity provider, and the brand assets

**Builds on**: 007 (the Android app is the same web build in a native shell), 013 (the catalog screens and the chip, mark and cover rules this spec restyles)

**Related**: JOR-47 (cover-plate labels fail contrast, closed by this spec), JOR-38 (a draft to replace the native app with an installable web app: it decides how much native icon and splash work remains)

**Input**: User description: "JOR-55. Give the whole JordyLab app a new look: no orange, a quieter and less generated-looking interface, and six themes the user can switch between (dark: Content Led as the default, Oxblood, Flare Orange; light: Paper, Glasshouse, Heather). It must work on desktop and on the phone, because the phone app is the same web build. Keep what the screens do today and the card grid the Library has now, but make it nicer. Design every screen and its loading, empty and error states first; the designs and a design system already exist."

## Context

The app has one look: dark ground, a single orange accent, three typefaces and many small decorative details (tiny capital labels above titles, numbered placeholders on covers). The owner finds it too orange and too much like a generated interface. It also cannot be re-themed: colours for status, marks and covers are fixed values written into the screens, and the sign-in page, the icons and the splash screen each repeat the orange on their own.

The designs for this spec were made before it was written and are the visual source of truth for planning:

- a design canvas with every screen in the default theme, desktop and phone side by side, the Library in all six themes, and loading, empty and error states for nine screens;
- a design system with the colours of all six themes, the type styles, spacing, radii, 18 component specifications, the screen inventory, the phone rules, the state rules and the process for adding a screen.

Both are private to the owner and are referenced from Linear JOR-55. Where this spec and the design system differ on a value (a hex colour, a pixel size), the design system wins and this spec is corrected.

### The six themes

| Theme | Kind | Accent | Notes |
|---|---|---|---|
| Content Led | Dark, **default** | Bone | Neutral chrome, so the game covers carry the colour |
| Oxblood | Dark | Rose | Warm, not orange |
| Flare Orange | Dark | Orange | Today's look, kept as an option because the owner asked for it |
| Paper | Light | Ultramarine | Warm paper ground |
| Glasshouse | Light | Forest green | Pale sage ground |
| Heather | Light | Plum | Cool lilac-grey ground |

### Screens in scope

Twelve screens, each on desktop and on a phone: Library, game detail, LibBot, Sources, Consoles, Articles, Portfolio, Briefing, Users, AI models, App settings and sign-in. The desktop layout keeps a sidebar; the phone layout has a top bar and a bottom tab bar with one tab per module (Games, Finance for administrators only, Settings). A tab with several pages opens them as a sheet: Games lists Library, LibBot, Sources and Consoles; Settings lists App settings for everyone plus Users and AI models for administrators. A tab with a single page opens it directly. There is no More page.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See and choose a theme (Priority: P1)

A person opens the app and sees it in a theme that suits their device: the default dark theme on a dark device, a light theme on a light device. In Settings they can pick any of the six themes or go back to following the device. The choice is remembered on that device and applies at once everywhere in the app.

**Why this priority**: Themes are the main request. Nothing else in the redesign is visible in more than one look until this exists, and it is useful on its own even if only a few screens have been restyled.

**Independent Test**: Open Settings → App, pick each theme in turn, and confirm that the page, the navigation and the Library change immediately and keep the choice after closing and reopening the app.

**Acceptance Scenarios**:

1. **Given** a device set to dark, **When** the app opens for the first time, **Then** it shows Content Led.
2. **Given** a device set to light, **When** the app opens for the first time, **Then** it shows Paper.
3. **Given** the person picks Heather in Settings, **When** they navigate to another screen and later reopen the app, **Then** Heather is still shown, with no flash of another theme while it loads.
4. **Given** the person chose "Follow device", **When** the device switches between light and dark, **Then** the app switches between Paper and Content Led without a reload.
5. **Given** a person on a second device, **When** they open the app there, **Then** that device uses its own choice, not the first device's.

---

### User Story 2 - Browse the Library in the new look on phone and desktop (Priority: P1)

A person opens the Library and sees the cover grid with the same information and buttons as today: cover, title, source, platform, install status and the three marks, plus search, filters, sort and the result count. On desktop it is four covers wide, on a phone two. They open a game and see its detail page with the same facts and actions as today.

**Why this priority**: The Library is the most used screen and the one the owner pointed to when asking for a nicer version of what exists. It proves the visual language, the six themes and the phone layout together.

**Independent Test**: Open the Library and a game on a phone-sized and a desktop-sized window in two themes and verify every item listed above is present, tappable at the required size and readable.

**Acceptance Scenarios**:

1. **Given** the Library on desktop, **When** it loads, **Then** covers are shown four per row with titles clipped to two lines, so that chips and mark buttons line up across a row.
2. **Given** the Library on a phone, **When** it loads, **Then** covers are shown two per row, search takes the full row, Filters is an icon button with a count, and sort and the active-filter chip share the next row.
3. **Given** a game with no artwork, **When** it appears, **Then** it shows a coloured cover with its initials in the colours of the active theme, readable at the required contrast.
4. **Given** an installed game and a not-installed game, **When** both are visible, **Then** the two can be told apart without using colour (a check and solid outline versus a dashed outline).
5. **Given** a game detail page on a phone, **When** it opens, **Then** the cover sits above the title, the three mark buttons carry text labels, and every action available today is still present.

---

### User Story 3 - Every other screen in the new look (Priority: P2)

A person uses LibBot, Sources, Consoles, Articles, Portfolio, Briefing, Users, AI models, App settings and the sign-in screen and finds them consistent with the Library: same colours, type, shapes and spacing, on desktop and on a phone, in all six themes. Nothing they could do before is missing.

**Why this priority**: The redesign is only convincing when the whole app follows it, but each screen can be delivered on its own after the Library.

**Independent Test**: For each screen, compare the list of information and actions before and after, on desktop and phone, in two themes.

**Acceptance Scenarios**:

1. **Given** each screen on a phone, **When** it is opened, **Then** it needs no sideways scrolling at 360, 390 and 430 pixels wide.
2. **Given** a screen that has two columns on desktop (Sources, Consoles, AI models, Briefing), **When** it is shown on a phone, **Then** the columns stack into one.
3. **Given** a person who is not an administrator, **When** they use the app, **Then** the same items are hidden from them as today.
4. **Given** a table of holdings on desktop, **When** it is shown on a phone, **Then** it becomes a list of rows with the value on the right and the detail underneath.

---

### User Story 4 - Clear loading, empty and error states (Priority: P2)

When a screen is loading, has nothing to show, or fails, the person sees a calm, consistent message or placeholder instead of a blank page: grey shapes that match what is coming, one sentence about what is missing and what to do, or a short error with a Try again button. LibBot also shows what it is doing while it answers, a first-run prompt with example questions, and a clear message when the daily question limit is used up.

**Why this priority**: These states are met often (first scan, slow network, a failed refresh) and today they vary from screen to screen.

**Independent Test**: For each screen, force each state (slow response, empty data, failed request) and compare with the design.

**Acceptance Scenarios**:

1. **Given** a screen that is loading, **When** the page title is known, **Then** the title shows at once and placeholders shaped like the content show below it.
2. **Given** a screen with no data, **When** it opens, **Then** it shows what is missing in one sentence and at most one action, and the action is the one that fixes the emptiness.
3. **Given** a failed request, **When** the screen reacts, **Then** a short message without technical terms and a Try again button are shown, and the page title stays.
4. **Given** a person who has used all their LibBot questions today, **When** they open LibBot, **Then** they see the limit message, their earlier conversation, and a disabled question field.
5. **Given** a device set to reduced motion, **When** placeholders are shown, **Then** they do not pulse.

---

### User Story 5 - A new brand identity everywhere, with no orange (Priority: P3)

The app's mark, wordmark and icons use the active default theme's colours instead of orange: the browser tab icon, the install icons, the phone launcher icon, the splash screen, the in-app sidebar and top bar, install and update prompts, and the sign-in page.

**Why this priority**: It completes the change, but the app is fully usable and themed without it.

**Independent Test**: Install the app on a phone and open it in a browser tab and the sign-in page, and confirm none shows the old orange mark.

**Acceptance Scenarios**:

1. **Given** the default theme, **When** any icon, splash screen or sign-in page is shown, **Then** none uses the old orange.
2. **Given** a device set to light, **When** the sign-in page opens, **Then** it is shown in the light default theme, and in dark it is shown in the dark default theme.
3. **Given** an app already installed with the old icon, **When** the app is updated, **Then** the new icon replaces it, or the person is told what to do to refresh it.

---

### Edge Cases

- A person changes theme while a form has unsaved input: the input and scroll position are kept.
- The font cannot be loaded: text falls back to a system font with no layout that breaks or hides content.
- A very long game title or source name: titles are clipped to two lines, sources to one line, and nothing overlaps.
- A game with no artwork, no platform colour or no source: it still renders a complete card.
- The device is set to a high-contrast or forced-colours mode: content stays readable and controls stay identifiable.
- A person with large text sizes: layouts grow without hiding controls.
- A phone with a bottom gesture area: the tab bar leaves room for it.
- The sign-in page opens before the app has stored a choice: it follows the device setting.
- Two themes are used on two devices by the same person: each device keeps its own.
- The theme is switched while a screen is loading or showing an error: the placeholder and message are re-coloured and stay in place.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST offer six themes: Content Led, Oxblood and Flare Orange (dark), Paper, Glasshouse and Heather (light).
- **FR-002**: With no stored choice the app MUST show Content Led on a device set to dark and Paper on a device set to light, and MUST follow later changes of the device setting.
- **FR-003**: People MUST be able to choose "Follow device" or any one of the six themes in Settings → App, on the web and in the Android app, and the choice MUST be remembered per device.
- **FR-004**: A theme change MUST apply to every visible element at once, without a reload, and the first paint on every load MUST already use the stored theme.
- **FR-005**: Every screen MUST take its colours from the active theme. The only colours that stay the same in every theme are the platform brand colours and the ROM-validated colour.
- **FR-006**: Text MUST meet a contrast ratio of at least 4.5:1 against its background in every theme (3:1 for large text), and icons and boundaries that carry meaning MUST meet 3:1 or be accompanied by a second cue.
- **FR-007**: Meaning MUST NOT depend on colour alone: install status, ROM status and the three marks MUST each have a shape or icon as well.
- **FR-008**: The app MUST use one type family for all text, bundled so that first paint does not wait for a download, and MUST write labels in sentence case with no capital-letter label style and no small label above page titles.
- **FR-009**: The phone layout MUST fit 390 by 844 pixels and MUST NOT scroll sideways at 360, 390 and 430 pixels wide; all touch targets MUST be at least 44 pixels.
- **FR-010**: The phone MUST show a top bar and a bottom bar with one tab per module (Games, Finance for administrators only, Settings). Tapping a tab with several pages MUST open them as a sheet that closes on choosing a page, on tapping the tab again, on the backdrop and on Escape, and moves focus into the sheet and back to the tab; a tab with one page MUST open it directly. Library, LibBot, Sources, Consoles, Users, AI models and App settings MUST each be reachable, shown to the same roles as today, except App settings, which MUST be reachable by every signed-in person on the web and on the phone. The top bar MUST sit below the system status bar, not under it.
- **FR-011**: The twelve screens in scope MUST be restyled for desktop and phone and MUST keep all information and actions they have today.
- **FR-012**: The Library MUST show covers in a grid, four across on desktop and two on a phone, each with cover, title clipped to two lines, source, platform chip, install-status chip and the three mark buttons, plus the vote counts on the cover where marks exist.
- **FR-013**: A game with no artwork MUST show a flat coloured cover with its initials, using one of six cover colour pairs of the active theme chosen from its title, so the same game always gets the same pair.
- **FR-014**: Filters MUST open as a popover on desktop and a bottom sheet on a phone, with a clearly labelled apply action showing the number of results.
- **FR-015**: Each data screen MUST have loading, empty and error states as designed; LibBot MUST also have first-run, answering, daily-limit and error states.
- **FR-016**: Placeholder animation MUST stop when the device asks for reduced motion.
- **FR-017**: The brand mark and icons (browser tab, install icons, launcher icon, splash screen, sidebar, top bar, prompts, sign-in) MUST use the default theme's colours and MUST NOT use the old orange. The mark in the app, the browser-tab icon and the Android launcher icon MUST also follow the theme the person has chosen; the install icons of the web app and the sign-in page stay on the default theme.
- **FR-018**: The sign-in page MUST match the new look and follow the device's light or dark setting.
- **FR-019**: Existing journeys, roles and data MUST behave as before; this feature MUST NOT add, remove or change any data or any server behaviour.
- **FR-020**: Colours used for status, marks, platforms, charts and cover plates MUST be defined once per theme and reused everywhere, so that adding or changing a theme is a change in one place.

### Key Entities

- **Theme**: a named set of colours (ground, surfaces, lines, three text levels, accent and its ink, install status, three mark colours, six cover pairs, four chart colours) with a kind, light or dark.
- **Theme preference**: what one device has chosen, either "follow device" or one theme.
- **Screen state**: for a screen that loads data, one of loaded, loading, empty or error (and, for LibBot, first run, answering and limit reached).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All twelve screens can be opened in all six themes on desktop and phone, and an automated contrast check finds no text below 4.5:1 on the Library, game detail, LibBot and Settings screens in any theme.
- **SC-002**: A person can change theme in no more than three steps from any screen.
- **SC-003**: On 100% of tested loads, including a cold start on a phone, the first visible frame uses the stored theme, with no flash of a different one.
- **SC-004**: The existing accessibility audit reports no serious or critical issues on every page in Content Led and Paper, and on the Library in all six themes.
- **SC-005**: No screen scrolls sideways at 360, 390 and 430 pixels wide.
- **SC-006**: No icon, splash screen, sign-in page or screen in the default theme shows the old orange.
- **SC-007**: Finding a game and setting a mark takes no more steps than before the redesign, on desktop and on a phone.
- **SC-008**: The owner, comparing the finished app against the designs screen by screen, accepts every screen as matching, with any difference recorded and agreed.

## Assumptions

- The owner is the main user. A theme choice is stored on each device, not synced between devices, and no user account setting is added.
- The six themes and their colours are final as designed. A seventh, a high-contrast theme and user-made themes are out of scope.
- Flare Orange stays as a selectable theme at the owner's request; it is not the default and no icon or brand asset uses it.
- The sign-in page belongs to the identity provider and cannot read the in-app choice, so it follows the device's light or dark setting with the default theme of each kind.
- Settings → App is open to every signed-in person on the web and in the Android app (today its navigation item is shown in the Android app only). It holds the theme choice for everyone; the fingerprint unlock row stays Android-only.
- The phone layout is the same web build at a narrow width; there is no separate phone design. Tablet-specific layouts are out of scope and use the desktop or phone layout by width.
- The designs show the loaded state of every screen and the other states for nine of them. The phone module sheet, the empty-library case before any filter, and row-level save errors are not drawn yet and are to be designed during planning, following the state rules in the design system.
- No new features, data or server behaviour are added; language and wording stay as they are except where the design system changes the style (sentence case, no label above titles).
- The amount of native icon and splash work depends on whether JOR-38 goes ahead; this spec covers the icons and splash colour of the app as it exists now.
- Placeholder content in the designs (example titles, holdings, articles, users) is illustrative and is not copied into the product.

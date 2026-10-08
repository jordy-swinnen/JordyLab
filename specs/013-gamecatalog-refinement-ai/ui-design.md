# UI design: filters, chips, cards, LibBot (spec 013)

Design reference for User Stories 7, 9, 10, 13 and the LibBot page. Produced with the `frontend-design` skill, **inside the
existing "Night Lab" language** (`jordylab-fe/tailwind.theme.js`): blackberry-ink ground, warm bone text, Flare orange for
action, Iris for secondary data, Bricolage Grotesque (display), Hanken Grotesk (body), Martian Mono (labels). The owner asked for
visibility over consistency for platform and status colors, so only those chips step outside the palette.

Runnable reference: [ui-mockup.html](ui-mockup.html) (static, open it in a browser; the Filters panel, chip removal, the steppers
and the 360 px preview all work). It is a design reference, not production code. The real components are spartan/ui + Tailwind in
`libs/gamecatalog/ui`.

## 1. Design idea: "quiet by default, loud when it matters"

The library today shows five rows of chips whether or not they are used. The new bar has **two permanent elements** (search and
one `Filters` button) and everything else appears only when it is **in use**:

- the **active-filter row** is the source of truth: one removable chip per active filter, always visible, with `Clear all` and a
  live result count;
- the **Filters panel** holds the choosing, and only offers values that exist in the library.

Nothing is hidden that is active; nothing is shown that is not.

## 2. Filter bar

### Desktop (≥ 641 px)

```text
GAME CATALOG · 228 TITLES
Library

[🔍 Search by title…                     ] [⚙ Filters (3)] [Sort: Title ▾]

[PLATFORM PlayStation 2 ×] [STATUS Installed ×] [PLAYERS 4+ ×]  CLEAR ALL        37 OF 228 GAMES
```

Panel (popover anchored under `Filters`, 680 px wide, max 70 vh, scrolls inside itself):

```text
┌ Filters ─────────────────────────────────────────────────────────────┐
│ PLATFORM · only platforms in your library                            │
│ (PlayStation 2) (Nintendo 64) (Nintendo Switch) (Steam) (Dreamcast)  │
│                                                                      │
│ WHERE                         │ STATUS                               │
│ (Living room PC) (MacBook Pro)│ [Installed|Not installed|All]        │
│ (Nintendo Switch)             │                                      │
│ SOURCE                        │ LOCAL PLAYERS                        │
│ (Steam (Owned)) (Steam (Fam…))│ [ − | 4+ | + ]                       │
│ (Emulated) (Console)          │                                      │
│ ROM STATUS · emulated only    │ SORT                                 │
│ (🛡 Validated) (⊗ Broken) (? ) │ [Title|Most wanted|Most liked]       │
│                                                                      │
│ COMMUNITY MARKS                                                      │
│ (🔖 Want to play) (👍 Played & liked) (👎 Played & disliked)          │
│ [Anyone's | Only mine]                                               │
├──────────────────────────────────────────────────────────────────────┤
│ CLEAR ALL                                        [ Show 37 games ]   │
└──────────────────────────────────────────────────────────────────────┘
```

### Phone (≤ 640 px, verified at 360 px)

The same panel becomes a **bottom sheet** (fixed, full width, max 82 vh, rounded top, grab handle, single column, sticky footer
with `Clear all` and `Show N games`). Search takes the full row, `Filters` and `Sort` sit below it, the active chips wrap, and the
result count drops to its own line. No element is wider than the viewport (checked in the mockup: `scrollWidth == clientWidth ==
360`).

### Rules

| Rule | Detail |
|---|---|
| Default state | `Status: Installed` is a real filter, so it shows as an active chip (removing it sets `All`). The badge on `Filters` counts active chips. "No filter" still shows only search + Filters; the one default chip is the honest cost of keeping Installed as the default. |
| Chip anatomy | `KEY` (mono, muted) + value + a 26 px × button inside a 36 px pill. Marks filters read `MARKS` or `MY MARKS` depending on the whose toggle. |
| Multi-select | Platform, Where, Source, ROM status and marks are multi-select (any-of inside a group, all-of across groups). Status and Sort are single-select segmented controls. |
| Only real values | Platform and Where list only what exists (from the platforms/hosts endpoints). ROM status is shown only when the library has emulated games. |
| Local players | Stepper `Any → 2+ … 8+`. Games with an unknown count are **not** silently dropped: the result line says "N more with unknown player count" (same honesty rule as LibBot). |
| URL state | Every filter, the sort and the search live in the query string, so reload and Back restore them (FR-038). |
| Empty result | "No games match" + the chip most likely to relax first ("Try removing STATUS Installed") + `Clear all`. |
| Keyboard | `Filters` is a button with `aria-expanded`/`aria-controls`; the panel is `role="dialog"`, focus moves to its first control, `Esc` closes and returns focus to the button. Group toggles are `<button aria-pressed>`; segmented groups use `role="group"` + label. Chip × buttons have `aria-label="Remove filter KEY: value"`. The result count and the active row are `aria-live="polite"`. |
| Touch | Every control is ≥ 40 px, chips' × and card mark buttons ≥ 44 px (WCAG 2.5.8 target size). |

## 3. Chip system: four families, four shapes

Colour is never the only signal. Each family has its own **shape** and the status families carry an **icon**.

| Family | Shape | Form |
|---|---|---|
| Platform | solid rounded rectangle (7 px), mono caps | brand background + brand text |
| Install status | outlined pill, icon, sentence case | coloured 1.5 px border and text on the card; `Not installed` has a **dashed** border and a dashed ring icon |
| ROM status | squared tag (4 px), icon, mono caps | filled with the status colour, dark text |
| Marks (votes) | soft pill with a count | tinted text; **your own vote has an outline** |

### 3.1 Platform colours (official brand colours, single source in the backend platform catalog)

The backend platform catalog (name, aliases, brand family, generation, external-database id, colour) is the **one place** the
palette lives; the frontend receives colours from the platforms endpoint and hard-codes none (FR-040). Brand families colour every
platform in them; the label text tells them apart.

| Brand family | Platforms (examples) | Background | Text | Contrast |
|---|---|---|---|---|
| PlayStation | PlayStation 1–5, PSP, Vita | `#0070D1` | `#FFFFFF` | **4.95** |
| Xbox | Xbox, 360, One, Series X\|S | `#107C10` | `#FFFFFF` | **5.37** |
| Nintendo | N64, GameCube, Wii, Wii U, Switch, Switch 2, GBA, DS, 3DS | `#E60012` | `#FFFFFF` | **4.80** |
| Sega | Saturn, Dreamcast | `#0089CF` | `#0B1220` | **4.89** |
| Steam | Steam | `#1B2838` + 1 px `#66C0F4` border | `#66C0F4` | **7.40** (border 9.07 on the card) |
| Neutral | anything without an official colour (custom console, Neo Geo Pocket) | `#3A3552` | `#F2EDE4` | **9.94** |

Notes from checking the numbers (not guessed): the official Sega blue `#0089CF` fails with white text (3.83), so it takes the
dark ink text; PlayStation's darker `#003791` is only 1.82 against the page background, so the lighter official PlayStation blue
`#0070D1` is used. The Steam chip is dark by design, so it gets its brand light-blue as border and text. All pairs are asserted by
a unit test that computes the WCAG ratio (≥ 4.5 for text) so a palette edit cannot regress, and the Playwright axe journey keeps
checking the rendered pages.

### 3.2 Status, ROM and mark colours (all different from each other and from the brand set)

| Chip | Colour | Icon | Contrast on card (`#161320`) |
|---|---|---|---|
| Installed | teal `#2DD4BF` | check | 9.83 |
| Not installed | amber `#F5B84A`, dashed border | dashed ring | 10.31 |
| ROM Validated | lime `#9BE564` | shield-check | 12.01 |
| ROM Broken | red `#FF6B6B` | circled ✕ | 6.59 |
| ROM Unknown | grey `#9A93AB`, outline only | circled ? | 6.21 |
| Want to play | iris `#A99BFF` | bookmark | 7.68 |
| Played & liked | rose `#FF8FB1` | thumbs up | 8.55 |
| Played & disliked | slate `#98A2B3` (quiet on purpose) | thumbs down | 7.10 |

Teal (not green) for Installed keeps it apart from the Xbox green. Red is reserved for Broken ROM (and form errors); Flare orange
stays the action colour and is not used for any status.

## 4. Game card

```text
┌────────────────────┐
│ № 004      🔖 5    │   vote rail on the cover: only non-zero totals,
│            👍 2    │   your own vote outlined
│                    │
│                    │
│ BGD                │
└────────────────────┘
Baldur's Gate: Dark Alliance            (wraps; never truncates the title)
[PlayStation 2] [Steam] (✓ Installed)   platform chips: one per platform the game lives on
STEAM (FAMILY)  EMULATED  [🛡 Validated on 1 of 2]
[ 🔖 ][ 👍 ][ 👎 ]                       44 px mutually exclusive marks, aria-pressed
```

- One card per game (FR-023). Platform chips come from the game's places; source labels from its sources (Steam (Owned) /
  Steam (Family) / Emulated / Console); install status is `Installed` if any enabled place has it.
- The ROM chip appears only for games that have an emulated copy; the summary is `Validated`, `Broken ROM`, or `Validated on 1
  of 2 hosts` when machines disagree. The detail page lists each machine with its own status control.
- Vote rail: totals for want / liked / disliked, shown only when > 0, so a much-wanted game is noticed while scanning. The mark
  buttons below set **my** vote; pressing another replaces it, pressing the current one clears it.
- The grid uses `repeat(auto-fill, minmax(min(100%, 230px), 1fr))`: one column at 360 px, no horizontal scroll. Titles and chip
  rows wrap (`overflow-wrap: anywhere`, `flex-wrap`).

## 5. LibBot page

```text
GAME CATALOG · GROUNDED IN YOUR LIBRARY
✦ LibBot                                14 OF 20 MESSAGES LEFT TODAY   [+ New conversation]

                         ┌ There are 6 people here tonight… ─────────────┐ (you, orange)
✦  Two games work for six on one screen: Overcooked! All You Can Eat …
   ┃ For 31 more games the player count isn't known yet…                    (amber note)
   APPLIED  [6+ local players] [local multiplayer] [installed]              (dashed chips)
   GAMES    (● Overcooked! All You Can Eat) (● Towerfall Ascension)         (≤ 10, all named in text)

                                       which of those are on the Switch? ┐
✦  ● Understood   ◉ Searching your library   ○ Writing the answer           (live stages)

[ Ask about your games… (English or Nederlands)                    ] [ Ask → ]
```

- **Name and icon**: `LibBot`, sparkle icon (replaces the chat bubble) in the nav, the page header and next to each answer.
- **Stages** (`Understood → Searching your library → Writing the answer`) appear within a second, are a `role="status"`
  region, and the pulse stops under `prefers-reduced-motion`. The page sets `aria-busy` while waiting.
- **Applied chips** use a dashed orange outline so they read as LibBot's interpretation, not as data. They are generated by the
  server from the derived constraints (in the question's language), never written by the model.
- **Unknown-data note** is its own amber-ruled block, never buried in the paragraph.
- **References**: at most ten, only games named in the answer, each linking to the game page; zero for declines and
  clarifying questions.
- **Errors**: an unreachable model shows one plain message with `Try again`, keeps the typed text, and does not spend a guest's
  message. When the guest limit is reached, the composer is replaced by "Limit reached, resets at 00:00".
- **Mobile**: user bubbles max 85 % wide, long unbroken text wraps, the composer is one row, `New conversation` and the quota
  sit above the thread.

## 6. Accessibility and layout checklist (feeds the e2e journeys)

- Every text/background pair in §3 ≥ 4.5:1 (unit-tested); non-text boundaries use outlines and icons.
- No horizontal scroll at 360, 390 and 430 px on every page (new Playwright check next to the axe journey, SC-019).
- Focus rings use `--ring` (Flare) with a 2 px offset; the panel traps no focus but returns it on close.
- Icon-only buttons have an `aria-label`; the vote rail has a text alternative ("5 people want to play this").

## 7. Game page (US15)

Same composition on every width. The cover overlaps the bottom-left corner of the banner; only the sizes change.

```text
Desktop (≥ 768)                                   Phone (360)
┌──────────────────────────────────────────┐      ┌────────────────────────┐
│ banner 16:5                              │      │ banner 16:9            │
│   ┌────────┐                             │      │                        │
│   │ cover  │                             │      │ ┌──────┐               │
└───│ 160 w  │─────────────────────────────┘      └─│cover │───────────────┘
    │        │                                      │ 96 w │
    └────────┘                                      └──────┘   (clears −40 px)
[STEAM] [ENRICHED]                              [STEAM] [ENRICHED]
Aseprite                                        Aseprite
```

Classes (reference): banner `aspect-[16/9] sm:aspect-[16/5]`; cover `absolute -bottom-10 left-4 w-24 sm:left-6 sm:w-32 md:left-8 md:w-40`; content below `mt-16`
on phones, the existing margins from `sm` up.

**About heading** (mono label), built from the detail response, never a literal vendor name:

| Case | Heading |
|---|---|
| AI text, model reported | `ABOUT · WRITTEN BY claude-haiku-4.5` (the provider prefix is dropped for display; full id in the tooltip) |
| AI text via a router | `ABOUT · WRITTEN BY claude-sonnet-5 · PICKED BY jev-router` |
| AI text, router did not report | `ABOUT · WRITTEN BY jev-router (MODEL NOT REPORTED)` |
| AI text from before the change | `ABOUT · WRITTEN BY AI` |
| Steam text | `ABOUT · DESCRIPTION FROM STEAM` |

The heading wraps on phones (the label is `flex-wrap`, the model id `overflow-wrap: anywhere`). "Facts from Steam/AI" is removed from it. The **Spec sheet** gets a source
note as its last row, in the same mono label style: `SOURCE · FACTS FROM STEAM · MULTIPLAYER FROM IGDB`.

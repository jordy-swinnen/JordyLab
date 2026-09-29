# Feature Specification: Nintendo Switch Games in the Game Catalog (Manual Add)

**Feature Branch**: `009-switch-games`

**Created**: 2026-09-29

**Status**: Draft

**Module**: `gamecatalog` (existing)

**Builds on**: 004 (multi-host model, artwork slots, metadata), 005 (library sources, "link, don't rebuild"), 006 (admin/guest roles)

**Input**: User description: "Use feature number 009 (short name: switch-games).

Add my Nintendo Switch games to the Game Catalog. Nintendo has no public API for a user's library (research confirmed: the unofficial routes only expose played games or digital purchases and need mitmproxy, parental-controls enrolment or third-party token services), so games are added manually by the admin — made fast with search.

ADD: admin types part of a title, sees matching Switch / Switch 2 games with cover, year and platform, picks one, chooses physical or digital, saves. Title, platform, portrait cover, wide banner, genres, developer, publisher, release year and local-multiplayer facts are filled in automatically from IGDB (already used by the catalog); the AI description is generated once like for any new game. If search has no match, add manually (title, platform, physical/digital) with placeholder art and link to a match later. Adding a game that's already there never creates a duplicate.

BULK: paste a list of titles (e.g. copied from the Nintendo Store app's Play Activity or my eShop purchase history); each line shows its best match / no match / already present; I fix, untick and set physical/digital, then confirm to add in one go.

BEHAVIOUR: Switch games appear everywhere other games do (grid, detail, platform and library-source filters, chat). Scans, library syncs and grace-period purges never rename or remove them; only I can edit or remove them. Editing physical/digital doesn't regenerate the AI description. Add/edit/remove are admin-only (spec 006); guests just see the games.

Out of scope: any Nintendo account login or import, play time, completion tracking, wishlists, prices, DLC tracking."

---

## Context

Nintendo has no public API for a user's library. The unofficial routes only show *played* games or *digital*
purchases and need mitmproxy, parental-controls enrolment or third-party token services — none of them list owned
games. For a library of a few dozen Switch cartridges and digital purchases, manual entry with a fast search is the
robust choice.

This feature lets the admin add Nintendo Switch games manually, made fast by searching IGDB (already configured for
the catalog) and by pasting a list of titles copied from the Nintendo Store app's Play Activity or eShop purchase
history. Once added, a Switch game behaves like any other game in JordyLab: it appears in the grid and on detail
pages, can be filtered by host, platform and library source, and is available to the catalog chat.

To fit the existing multi-host model without inventing a new install-status value, Switch games live on a virtual
"Nintendo Switch" host. This release is limited to the original Nintendo Switch platform; Nintendo Switch 2 is out of
scope and can be added later as a second virtual host.

---

## Clarifications

### Session 2026-09-29

- **Q**: How should manually added Switch games appear in the install-status filter?  
  **A**: As owned entries on a virtual "Nintendo Switch" host. The existing host, platform and library-source filters
cover them, and no new install-status value is introduced.
- **Q**: When an EmuDeck Switch ROM with the same title already exists, should the manual add link to it or create a
separate game?  
  **A**: Link to the existing game. A game exists once in the catalog and can be present on multiple hosts (e.g. an
EmuDeck ROM host and the Switch virtual host) — following the 005 "link, don't rebuild" rule.
- **Q**: Which personal fields should a Switch game carry in the MVP?  
  **A**: Physical/digital only. The Switch game detail page mirrors other games' detail pages; no new personal fields
such as notes or played/completed flags are added.
- **Q**: Is the paste-a-list bulk add in scope for this feature (P2) or a follow-up?  
  **A**: In scope, P2.
- **Q**: Do you own a Nintendo Switch 2 (this sets the default platform in the add form)?  
  **A**: Nintendo Switch 2 is out of scope for this feature. The add form adds one game at a time and uses an
autocomplete platform dropdown with a single "Nintendo Switch" entry today.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Add a Switch game by searching (Priority: P1)

As the admin, I want to type a few letters of a Switch game, pick it from the results and add it, so that it appears
in my catalog with proper artwork and details in seconds.

**Why this priority**: It is the core interaction of the feature. Without it, the admin has to add every game by hand,
which defeats the purpose of making manual entry fast.

**Independent Test**: Search "mario kart", pick "Mario Kart 8 Deluxe", mark it physical, save, and check it appears in
the grid with a portrait cover, and on its detail page with a banner, genres, developer, release year and
local-multiplayer info.

**Acceptance Scenarios**:

1. **Given** the admin opens "Add Switch game", **When** they type at least 3 characters, **Then** matching Nintendo
   Switch games appear with cover thumbnail, release year and platform.
2. **Given** a result is picked, **When** the admin chooses physical or digital and saves, **Then** the game is added as
   owned on the "Nintendo Switch" host, with title, platform, cover, banner, genres, developer, publisher, release year
   and local-multiplayer facts filled in automatically.
3. **Given** the game is new to the catalog, **When** it's added, **Then** an AI description is generated once, as for
   other games.
4. **Given** the game is already in the catalog (same Switch game), **When** the admin tries to add it again, **Then**
   they're told it's already there, and no duplicate is created.

---

### User Story 2 — Add a game the search can't find (Priority: P2)

As the admin, I want to add a Switch game that IGDB search doesn't match, so that niche or regional titles can still
be tracked.

**Why this priority**: Search will not cover every title. A manual fallback keeps the catalog complete without
blocking the common path.

**Independent Test**: Try to add a fictional title, choose "Add manually", enter the title and format, save, and check
it appears with the placeholder cover; then search again and link it to a real match.

**Acceptance Scenarios**:

1. **Given** no search result fits, **When** the admin chooses "Add manually", **Then** they can enter title, platform
   ("Nintendo Switch" via the autocomplete dropdown) and physical/digital, and the game is added with the styled
   placeholder artwork.
2. **Given** a manually entered game, **When** the admin later searches and links it to a match, **Then** metadata and
   artwork are filled in without creating a second game.

---

### User Story 3 — Add many games at once (Priority: P2)

As the admin, I want to paste a list of titles (e.g. copied from the Nintendo Store app's Play Activity or my eShop
purchase history), so that I don't have to add 40 games one by one.

**Why this priority**: A list pasted from the Nintendo Store app turns a tedious chore into a quick review step.

**Independent Test**: Paste 40 real Switch titles, review the proposed matches, confirm, and check that the added games
appear in the grid with a summary of added / skipped / already present.

**Acceptance Scenarios**:

1. **Given** a pasted list (one title per line), **When** submitted, **Then** each line shows its best match (with
   cover), "no match" or "already in catalog", and nothing is saved yet.
2. **Given** the review list, **When** the admin fixes wrong matches, unticks lines and sets physical/digital (per line
   or for all), and confirms, **Then** only the ticked games are added, with a summary (added / skipped / already
   present).

---

### User Story 4 — Switch games behave like any other game (Priority: P1)

As an admin or guest, I want Switch games to appear alongside my other games, so that the catalog feels like one
library.

**Why this priority**: The feature only delivers value if the added games are visible, filterable and usable in chat
like every other game.

**Independent Test**: Browse the grid, open a Switch game's detail page, filter by the "Nintendo Switch" host and
platform, and ask the catalog "which Switch games support 4-player local co-op?".

**Acceptance Scenarios**:

1. **Given** Switch games in the catalog, **When** anyone (admin or guest) browses, **Then** they appear in the grid and
   on detail pages and can be filtered by the "Nintendo Switch" host, platform and library source.
2. **Given** a Switch game, **When** someone uses "Ask the catalog" (e.g. "which Switch games support 4-player local
   co-op?"), **Then** chat uses its data like any other game.
3. **Given** the host filter, **When** the admin picks the "Nintendo Switch" host, **Then** Switch games are listed; no
   new install-status value is introduced.
4. **Given** a scan or library sync runs, **When** it finishes, **Then** Switch games are never removed or renamed by
   it.

---

### User Story 5 — Edit and remove (Priority: P2)

As the admin, I want to fix the format of a Switch game or remove it, so that the catalog stays accurate.

**Why this priority**: Physical/digital is a personal field that may change; mistakes happen and need removal.

**Independent Test**: Open a Switch game's detail page, change physical to digital, relink it to a different IGDB match,
remove a game with confirmation, and confirm guests see no edit/remove controls.

**Acceptance Scenarios**:

1. **Given** a Switch game, **When** the admin changes physical/digital or relinks it to a different search match,
   **Then** the change is saved. Metadata and artwork refresh only when the match changes, and the AI description is
   not regenerated for personal-field edits.
2. **Given** a Switch game, **When** the admin removes it (with confirmation), **Then** it disappears from the catalog.
3. **Given** a guest, **When** they view a Switch game, **Then** there are no add, edit or remove controls, and the API
   refuses such requests.

### Edge Cases

- The search service is unavailable or unconfigured: the admin can still add manually (US2), with a clear message.
- The same game exists as an EmuDeck Switch ROM: the manual add links to the existing game (005 "link, don't rebuild")
  — one game, two hosts, no duplicate.
- Regional title differences (e.g. EU vs US names): the search matches alternative names, and the title shown is the
  one the admin picked.
- The pasted list contains duplicates, empty lines or DLC names: duplicates are collapsed, empty lines ignored, and
  DLC/bundles flagged for review rather than auto-added.
- IGDB has no cover or banner: the styled placeholder plates are used (existing behaviour).

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The admin MUST be able to search Nintendo Switch games by title and add one as owned, choosing physical
  or digital.
- **FR-002**: Adding a searched game MUST fill title, platform, cover, banner, genres, developer, publisher, release
  year and local-multiplayer facts from the search source, using the existing metadata/artwork/multiplayer pipelines.
- **FR-003**: A Switch game MUST be uniquely identified (per platform and search-source ID, or by platform + normalised
  title for manual entries). Adding an existing one MUST NOT create a duplicate. The same catalog game MAY exist on
  multiple hosts (e.g. an EmuDeck ROM and the Switch virtual host).
- **FR-004**: The admin MUST be able to add a game manually when search has no match, and link it to a match later.
- **FR-005**: The admin MUST be able to paste a list of titles, review the proposed matches, and add the confirmed ones
  in one action.
- **FR-006**: Switch games MUST appear in all catalog views, filters and chat like other games, and MUST be filterable
  by the "Nintendo Switch" host, platform and library source.
- **FR-007**: Manually added titles MUST NOT be renamed or removed by scans, library syncs or grace-period purges. Only
  the admin removes them.
- **FR-008**: The AI description MUST be generated once per new game and MUST NOT be regenerated for edits to personal
  fields.
- **FR-009**: Add, edit and remove MUST be admin-only (006 roles). Guests have read and chat access only.
- **FR-010**: No Nintendo account, login or credential is used or stored.

### Key Entities

- **Game** (existing): gains an external IGDB id for Switch games; platform value "Nintendo Switch"; title authority
  `MANUAL` so scans never rename it.
- **Nintendo Switch host** (new): a virtual host that owns Switch games, so the existing host filter and visibility
  rules apply.
- **Installation** (existing, 005): Switch games use a per-host `GameInstallation` row on the virtual Switch host, marked
  as manual and carrying the **format** (physical/digital).
- **Library entry** (existing, 005): Switch games are owned entries (`OWNED`) for global library-source filtering; no
  schema change.
- **Bulk add review** (transient): pasted line, proposed match, status (match / no match / already present / needs
  review), include flag.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Adding one Switch game via search takes under 30 seconds.
- **SC-002**: A pasted list of 40 titles is reviewed and added in under 5 minutes, with ≥ 90% correct first matches for
  official titles.
- **SC-003**: 100% of added Switch games show a cover (or the placeholder when none exists), and appear under the
  "Nintendo Switch" host and platform filters.
- **SC-004**: Re-adding or re-pasting an existing game never creates a duplicate (automated test).
- **SC-005**: No scan, sync or purge ever changes or removes a manually added game (automated test).

---

## Assumptions

- IGDB (already configured for the catalog) is the search, metadata and artwork source for Switch games.
- There's no Nintendo integration of any kind. A Nintendo API or unofficial import is out of scope, and could be
  revisited if Nintendo ever publishes one.
- The number of Switch games is small (tens, not thousands).
- This feature is limited to the original Nintendo Switch. Nintendo Switch 2 support is a future pseudo-host addition
  and is out of scope.
- Personal fields are limited to physical/digital for the MVP. Notes, play time, completion tracking, wishlists, prices
  and DLC tracking are out of scope.
- The Switch game detail page mirrors the existing detail page layout; no new personal-field UI is introduced.

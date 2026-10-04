# Feature Specification: Nintendo Switch Games in the Game Catalog (manual)

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-09-28
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `gamecatalog`
**Builds on**: catalog refinements (multi-host model, artwork slots, metadata), Steam sync (library sources, "link, don't rebuild"), Settings (
admin/guest roles)

---

## Overview

Nintendo has no public API for a user's games, and the unofficial routes only show *played* games or *digital* purchases
and need fragile workarounds. So Switch games are **added manually by the admin**, with a search that fills in the
title, cover, banner, metadata and local-multiplayer facts from IGDB. IGDB is already used by the catalog. Once added, a
Switch game behaves like every other game: grid, detail page, filters, AI description and chat.

---

## User Scenarios & Testing

### User Story: Add a Switch game by searching (Priority: High)

As the admin, I want to type a few letters of a Switch game, pick it from the results and add it, so that it appears in
my catalog with proper artwork and details in seconds.

**Independent Test**: Search "mario kart", pick "Mario Kart 8 Deluxe", mark it physical, save, and check it appears in
the grid with a portrait cover, and on its detail page with a banner, genres, developer, release year and
local-multiplayer info.

**Acceptance Scenarios**:

1. **Given** the admin opens "Add Switch game", **When** they type at least 3 characters, **Then** matching Switch /
   Switch 2 games appear with cover thumbnail, release year and platform.
2. **Given** a result is picked, **When** the admin chooses physical or digital and saves, **Then** the game is added as
   owned, with title, platform, cover, banner, genres, developer, publisher, release year and local-multiplayer facts
   filled in automatically.
3. **Given** the game is new to the catalog, **When** it's added, **Then** an AI description is generated once, as for
   other games.
4. **Given** the game is already in the catalog (same Switch game), **When** the admin tries to add it again, **Then**
   they're told it's already there, and no duplicate is created.

### User Story: Add a game the search can't find (Priority: Medium)

**Acceptance Scenarios**:

1. **Given** no search result fits, **When** the admin chooses "Add manually", **Then** they can enter title, platform (
   Switch / Switch 2) and physical/digital, and the game is added with the styled placeholder artwork.
2. **Given** a manually entered game, **When** the admin later searches and links it to a match, **Then** metadata and
   artwork are filled in without creating a second game.

### User Story: Add many games at once (Priority: Medium)

As the admin, I want to paste a list of titles (e.g. copied from the Nintendo Store app's Play Activity or my eShop
purchase history), so that I don't have to add 40 games one by one.

**Acceptance Scenarios**:

1. **Given** a pasted list (one title per line), **When** submitted, **Then** each line shows its best match (with
   cover), "no match" or "already in catalog", and nothing is saved yet.
2. **Given** the review list, **When** the admin fixes wrong matches, unticks lines and sets physical/digital (per line
   or for all), and confirms, **Then** only the ticked games are added, with a summary (added / skipped / already
   present).

### User Story: Switch games behave like any other game (Priority: High)

**Acceptance Scenarios**:

1. **Given** Switch games in the catalog, **When** anyone (admin or guest) browses, **Then** they appear in the grid and
   on detail pages and can be filtered by platform (Nintendo Switch / Switch 2) and library source.
2. **Given** a Switch game, **When** someone uses "Ask the catalog" (e.g. "which Switch games support 4-player local
   co-op?"), **Then** chat uses its data like any other game.
3. **Given** the install-status filter, **When** Switch games are shown, **Then** they're treated
   consistently [NEEDS CLARIFICATION: "Not installed", a new "Not tracked" state, or a "Nintendo Switch" pseudo-host].
4. **Given** a scan or library sync runs, **When** it finishes, **Then** Switch games are never removed or renamed by
   it.

### User Story: Edit and remove (Priority: Medium)

**Acceptance Scenarios**:

1. **Given** a Switch game, **When** the admin changes physical/digital or relinks it to a different search match, *
   *Then** the change is saved. Metadata and artwork refresh only when the match changes, and the AI description is not
   regenerated for personal-field edits.
2. **Given** a Switch game, **When** the admin removes it (with confirmation), **Then** it disappears from the catalog.
3. **Given** a guest, **When** they view a Switch game, **Then** there are no add, edit or remove controls, and the API
   refuses such requests.

### Edge Cases

- The search service is unavailable or unconfigured: the admin can still add manually (the add-a-game-the-search-cannot-find story), with a clear message.
- The same game exists as an EmuDeck Switch
  ROM: [NEEDS CLARIFICATION: link to the existing game (Steam sync "link, don't rebuild") or keep separate].
- A Switch 1 game played on a Switch 2: its platform stays "Nintendo Switch".
- Regional title differences (e.g. EU vs US names): the search matches alternative names, and the title shown is the one
  the admin picked.
- The pasted list contains duplicates, empty lines or DLC names: duplicates are collapsed, empty lines ignored, and
  DLC/bundles flagged for review rather than auto-added.
- IGDB has no cover or banner: the styled placeholder plates are used (existing behaviour).

---

## Requirements

### Functional

- The admin MUST be able to search Switch and Switch 2 games by title and add one as owned, choosing
  physical or digital.
- Adding a searched game MUST fill title, platform, cover, banner, genres, developer, publisher, release
  year and local-multiplayer facts from the search source, using the existing metadata/artwork/multiplayer pipelines.
- A Switch game MUST be uniquely identified (per platform and search-source ID, or by platform + normalised
  title for manual entries). Adding an existing one MUST NOT create a duplicate.
- The admin MUST be able to add a game manually when search has no match, and link it to a match later.
- The admin MUST be able to paste a list of titles, review the proposed matches, and add the confirmed ones
  in one action.
- Switch games MUST appear in all catalog views, filters and chat like other games, and MUST be filterable
  by platform and library source.
- Manually added titles MUST NOT be renamed or removed by scans, library syncs or grace-period purges. Only
  the admin removes them.
- The AI description MUST be generated once per new game and MUST NOT be regenerated for edits to personal
  fields.
- Add, edit and remove MUST be admin-only (Settings roles). Guests have read and chat access only.
- No Nintendo account, login or credential is used or stored.

### Key Entities

- **Game** (existing): gains platform values "Nintendo Switch" / "Nintendo Switch 2", and an external search-source ID
  for Switch games.
- **Library entry** (existing, Steam sync): Switch games are owned entries marked as manually added, with a **format** (
  physical/digital).
- **Bulk add review** (transient): pasted line, proposed match, status (match / no match / already present / needs
  review), include flag.

---

## Success Criteria

- Adding one Switch game via search takes under 30 seconds.
- A pasted list of 40 titles is reviewed and added in under 5 minutes, with ≥ 90% correct first matches for
  official titles.
- 100% of added Switch games show a cover (or the placeholder when none exists), and appear under the Switch
  platform filter.
- Re-adding or re-pasting an existing game never creates a duplicate (automated test).
- No scan, sync or purge ever changes or removes a manually added game (automated test).

---

## Assumptions

- IGDB (already configured for the catalog) is the search, metadata and artwork source for Switch games.
- There's no Nintendo integration of any kind. A Nintendo API or unofficial import is out of scope, and could be
  revisited if Nintendo ever publishes one.
- The number of Switch games is small (tens, not thousands).
- Out of scope: play time, completion tracking, wishlists, prices, DLC tracking, importing via Nintendo accounts or
  intercepted app traffic.

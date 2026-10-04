# Nintendo Switch Games: SpecKit Prompts

Background is in `research.md`, and `spec-draft.md` shows the expected spec.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** builds on catalog refinements/Steam sync (merged) and Settings (admin role). It doesn't need mobile app or deployment.

---

## 1. `/speckit-specify`

```
Short name: switch-games.

Add my Nintendo Switch games to the Game Catalog. Nintendo has no public API for a user's library (research confirmed: the unofficial routes only expose played games or digital purchases and need mitmproxy, parental-controls enrolment or third-party token services), so games are added manually by the admin — made fast with search.

ADD: admin types part of a title, sees matching Switch / Switch 2 games with cover, year and platform, picks one, chooses physical or digital, saves. Title, platform, portrait cover, wide banner, genres, developer, publisher, release year and local-multiplayer facts are filled in automatically from IGDB (already used by the catalog); the AI description is generated once like for any new game. If search has no match, add manually (title, platform, physical/digital) with placeholder art and link to a match later. Adding a game that's already there never creates a duplicate.

BULK: paste a list of titles (e.g. copied from the Nintendo Store app's Play Activity or my eShop purchase history); each line shows its best match / no match / already present; I fix, untick and set physical/digital, then confirm to add in one go.

BEHAVIOUR: Switch games appear everywhere other games do (grid, detail, platform and library-source filters, chat). Scans, library syncs and grace-period purges never rename or remove them; only I can edit or remove them. Editing physical/digital doesn't regenerate the AI description. Add/edit/remove are admin-only (the Settings spec); guests just see the games.

Out of scope: any Nintendo account login or import, play time, completion tracking, wishlists, prices, DLC tracking.
```

---

## 2. `/speckit-clarify`: expected questions and suggested answers

1. Install status for Switch games → a new "Not tracked" value (or "Not installed" if you prefer fewer states).
2. Existing EmuDeck Switch ROM with the same title → link to that game (Steam sync "link, don't rebuild").
3. Personal fields → physical/digital only for MVP.
4. Bulk paste in scope → yes, medium priority.
5. Switch 2 owned? → sets the default platform in the add form.

---

## 3. `/speckit-plan`

```
Tech context for Switch games (read AGENTS.md, the constitution, the catalog refinements and Steam sync plans under specs/, and specs/_drafts/switch-games/research.md first; carry findings into this feature's research.md; verify IGDB fields/platform IDs against live docs — report instead of guessing):

- Reuse IgdbClient (gamecatalog/rest/client): add title search filtered to Switch + Switch 2 platforms (verify IGDB platform IDs), returning id, name, alternative names, first release year, cover image_id, platforms; fetch details for genres, involved companies (developer/publisher), artworks/screenshots, game modes → existing multiplayer mapping. Image URLs via images.igdb.com (t_cover_big for cover, t_1080p/t_screenshot_huge for banner); confirm this fits catalog refinements's artwork rule or record the exception.
- Domain: platform values "Nintendo Switch" / "Nintendo Switch 2"; external IGDB id on Game (or on a Switch-specific ref) with a uniqueness constraint per platform; manual entries identified by platform + normalised title. New TitleSource MANUAL (highest authority). GameLibraryEntry OWNED with a manual flag and a format (PHYSICAL/DIGITAL). Exclude manual entries from scan reconciliation and grace-period purge. Flyway migration via /flyway-migration; entity changes via /entity and /test-builder.
- API (admin role): GET /api/gamecatalog/switch/search?q=…; POST /api/gamecatalog/switch/games (igdbId or manual fields + format); PATCH (format, relink); DELETE; POST /api/gamecatalog/switch/bulk/preview (lines → proposed matches) and /bulk/confirm. Existing GET endpoints unchanged; the install-status filter handles Switch games per clarify.
- Frontend (libs/gamecatalog): admin-only "Add Switch game" dialog with debounced search and result cards, manual fallback form, bulk paste + review table, edit/remove on the detail page; signal store via /angular-signal-store; tests via /angular-test; Night Lab tokens + spartan/ui.
- Tests: duplicate prevention, manual→linked, scans/syncs/purges never touching manual games, AI enrichment only on creation, 403 for guests, IGDB client against WireMock (incl. unconfigured/unavailable).
- /modularity-check at the end.
```

---

## 4. Then `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

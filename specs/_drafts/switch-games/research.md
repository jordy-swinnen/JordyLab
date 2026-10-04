# Nintendo Switch Games in the Catalog: Research

Date: 2026-09-28. Checked against the repo (`main` after the Steam sync merge) and live sources.

## Verdict: no usable API, so add games manually (with smart search)

| Route                                                          | What it gives                                                                                                                                                           | Why not                                                                                                                                                                                                                                                                                   |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Official Nintendo API**                                      | **Doesn't exist.** Nintendo has no public developer API for a user's library or purchases                                                                               | n/a                                                                                                                                                                                                                                                                                       |
| **Nintendo Store app "Play Activity"** (official app feature)  | Every game **played** on Switch / Switch 2 (and 3DS/Wii U up to Feb 2020), with playtime and first played date. Covers physical and digital, because it's based on play | Only visible in the app, with **no export**. The only scripted access (e.g. NSPlayTime) calls Nintendo's private "mypage" endpoint. That needs intercepting the app's traffic with mitmproxy to extract a `client_id` + session token. Fragile, unofficial, and the project has 3 commits |
| **Parental Controls API** (pynintendoparental, nxapi)          | Titles played per day, with title ID, name, icon and playtime                                                                                                           | You'd have to enroll your own console in Parental Controls. It only sees games **played since** enrolment, not owned-but-unplayed ones. Unofficial                                                                                                                                        |
| **Nintendo Switch Online app API** (nxapi)                     | Friends, presence, game-specific services                                                                                                                               | No library or ownership list. Needs a third-party token service (nxapi sends access tokens to `nxapi-znca-api.fancy.org.uk`). nxapi's author notes Nintendo has banned a small number of users from SplatNet 3                                                                            |
| **eShop purchase history** (`ec.nintendo.com/my/transactions`) | **Digital** purchases (a userscript can export them to CSV)                                                                                                             | Web page only, no physical cartridges, userscript barely maintained                                                                                                                                                                                                                       |

**Conclusion:** there's no official route. Every unofficial route either needs traffic interception, enrolling your
console in Parental Controls, or third-party token services, **and none of them lists owned games** (only played games
or digital purchases). For a library of a few dozen Switch games, **manual entry with a good search** is the robust
choice.

**Cheap middle ground:** a **"paste a list"** bulk-add. You open *Play Activity* in the Nintendo Store app (or your
eShop purchase history), copy the titles, paste them into JordyLab, and it matches each one. That's still manual, needs
no Nintendo login, and turns 40 games into one screen.

## What the repo already has (so this is mostly reuse)

- **`IgdbClient`** (from catalog refinements/Steam sync, Twitch OAuth client-credentials, degrades gracefully) is already used for ROM
  multiplayer facts. IGDB has Switch and Switch 2 as platforms, portrait **covers** (
  `images.igdb.com/…/t_cover_big/<image_id>.jpg`) and **artworks/screenshots** for banners, genres, developer/publisher,
  release date and game modes. So search, metadata, artwork and local-multiplayer facts all come from one source that's
  already configured (`IGDB_CLIENT_ID/SECRET`).
- **Game model after Steam sync:**
    - A host-independent `game` (platform, title + `TitleSource` authority, metadata, artwork slots,
      `MultiplayerSource`, AI enrichment).
    - Per-host `game_installation`.
    - `GameLibraryEntry` with `LibrarySource` (`OWNED`/`FAMILY` stored, `LOCAL` derived).
    - Filters for host, platform, install status and library source.
    - The Steam sync rule applies here too: **"a game's data is produced once and reused by every source."**
- **Roles (Settings):** guests can read + chat only, so **adding, editing and removing Switch games is admin-only**. Guests
  see them like any other game.
- **catalog refinements artwork rule:** "no API-key-requiring provider". IGDB needs a key, but it's already a configured dependency
  since Steam sync, so there's nothing new to register. *Plan must confirm the rule was relaxed for IGDB or that IGDB image
  URLs (public CDN, no key) count as deterministic.*

## Design points to settle

1. **Identity:** a manual Switch game is identified by **platform + IGDB game ID** (stable), falling back to platform +
   normalised title when there's no IGDB match (a "custom" entry).
2. **Platforms:** "Nintendo Switch" and "Nintendo Switch 2" as separate platform values. Switch 1 games played on a
   Switch 2 stay "Nintendo Switch".
3. **Install status:** a Switch isn't a scanned host. Suggestion: manual Switch games have library source **OWNED** (
   with a *physical/digital* attribute), no installation rows, and appear under "Not installed" or a new "Not tracked"
   state. **Clarify.** An optional "Switch" pseudo-host would make the host filter work.
4. **Title authority:** manual entry should outrank scans (new `TitleSource.MANUAL`), so an EmuDeck scan can never
   rename it.
5. **EmuDeck overlap:** Switch ROMs through an emulator in EmuDeck may already exist as ROM games. Should a manual
   Switch game with the same normalised title **link** to that game instead of creating a duplicate? The Steam sync "link,
   don't rebuild" rule suggests yes. **Clarify.**
6. **AI description:** runs once per new game, as usual, using IGDB facts as grounding. Never re-run on edits of
   personal fields (format, notes).
7. **Removal:** a manual game is only removed by the admin, never by grace-period purges (those are for scanned
   installations).

## Open questions for `/speckit-clarify`

1. Install-status treatment of Switch games: "Not installed", a new "Not tracked" value, or a "Nintendo Switch"
   pseudo-host?
2. Link manual Switch games to existing EmuDeck Switch ROM entries with the same title?
3. Per-game personal fields: physical/digital, notes, "played/completed" flag? (Suggestion: physical/digital only for
   MVP.)
4. Is paste-a-list bulk add in scope (suggested medium priority) or a follow-up?
5. Do you own a Switch 2 (it affects the default platform in the form)?

## Sources

-
Nintendo: [How to View Play Activity in the Nintendo Store App (Nintendo UK)](https://www.nintendo.com/en-gb/Support/Purchases-Subscriptions/How-to-View-Play-Activity-in-the-Nintendo-Store-App-2954196.html) · [How to view eShop purchase history (Nintendo Support)](https://en-americas-support.nintendo.com/app/answers/detail/a_id/22465/~/how-to-view-nintendo-switch-eshop-purchase-history)
- Unofficial
  routes: [NSPlayTime (mypage play_histories endpoint, mitmproxy setup)](https://github.com/CafeAuLait-CC/NSPlayTime) · [pynintendoparental](https://github.com/pantherale0/pynintendoparental) · [nxapi](https://github.com/samuelthomas2774/nxapi) · [eshop-purchase-history userscript](https://github.com/redphx/eshop-purchase-history)
-
Coverage: [Pocket-lint: Play history on mobile](https://www.pocket-lint.com/how-to-view-nintendo-switch-play-history-on-mobile/) · [TheGamer: Play history in the Store app](https://www.thegamer.com/nintendo-store-app-play-history-nostalgia-3ds-wii-u/)

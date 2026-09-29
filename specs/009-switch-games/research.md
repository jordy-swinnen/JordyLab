> Carried from `specs/_drafts/009-switch-games/research.md` on branch `009-010-drafts`, then expanded with
> repository findings and live IGDB verification.

# 009 Nintendo Switch Games in the Catalog: Research

Date: 2026-09-28. Checked against the repo (`main` after the 005 merge) and live sources. Expanded 2026-09-29.

## Verdict: no usable API, so add games manually (with smart search)

| Route                                                          | What it gives                                                                                                                                                           | Why not                                                                                                                                                                                                                                                                                  |
|----------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Official Nintendo API**                                      | **Doesn't exist.** Nintendo has no public developer API for a user's library or purchases                                                                               | n/a                                                                                                                                                                                                         |
| **Nintendo Store app "Play Activity"** (official app feature)  | Every game **played** on Switch / Switch 2 (and 3DS/Wii U up to Feb 2020), with playtime and first played date. Covers physical and digital, because it's based on play | Only visible in the app, with **no export**. The only scripted access (e.g. NSPlayTime) calls Nintendo's private "mypage" endpoint. That needs intercepting the app's traffic with mitmproxy to extract a `client_id` + session token. Fragile, unofficial, and the project has 3 commits |
| **Parental Controls API** (pynintendoparental, nxapi)          | Titles played per day, with title ID, name, icon and playtime                                                                                                           | You'd have to enroll your own console in Parental Controls. It only sees games **played since** enrolment, not owned-but-unplayed ones. Unofficial                                                                                                                                        |
| **Nintendo Switch Online app API** (nxapi)                     | Friends, presence, game-specific services                                                                                                                               | No library or ownership list. Needs a third-party token service (nxapi sends access tokens to `nxapi-znca-api.fancy.org.uk`). nxapi's author notes Nintendo has banned a small number of users from SplatNet 3                                                                            |
| **eShop purchase history** (`ec.nintendo.com/my/transactions`) | **Digital** purchases (a userscript can export them to CSV)                                                                                                             | Web page only, no physical cartridges, userscript barely maintained                                                                                                                                                                                                         |

**Conclusion:** there's no official route. Every unofficial route either needs traffic interception, enrolling your
console in Parental Controls, or third-party token services, **and none of them lists owned games** (only played games
or digital purchases). For a library of a few dozen Switch games, **manual entry with a good search** is the robust
choice.

**Cheap middle ground:** a **"paste a list"** bulk-add. You open *Play Activity* in the Nintendo Store app (or your
eShop purchase history), copy the titles, paste them into JordyLab, and it matches each one. That's still manual, needs
no Nintendo login, and turns 40 games into one screen.

## IGDB verification

Verified against live documentation and community references (2026-09-29):

- **Platform ID for Nintendo Switch**: `130` ([IGDB platform ID gist](https://gist.github.com/ahmed-abdelazim/b533b443388baaafab3fc377e71e0109); also confirmed by
  [igdb-ts examples](https://github.com/AaronWLChan/igdb-ts)). Nintendo Switch 2 also exists on IGDB but is **out of
  scope** for this feature.
- **Search endpoint**: `POST https://api.igdb.com/v4/games` with an Apicalypse body such as:
  `search "mario kart"; fields name,cover.image_id,first_release_date,platforms; where platforms = (130) & version_parent = null & game_type = 0; limit 10;`
- **Cover image**: the `cover` object has `image_id` and `url`; public CDN URLs are built as
  `https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg` and `t_1080p`/`t_screenshot_huge` for banners.
  The CDN is keyless — only the Twitch client-credentials token is needed for the API call itself.
- **Metadata fields**: `genres`, `involved_companies` (with `developer`/`publisher` flags via the `company` expand),
  `first_release_date` (Unix seconds), `game_modes`, `artworks`, `screenshots`, `alternative_names`.
- **Important repo finding**: `jordylab-be` currently has **no IGDB image URL construction code**. Artwork today comes
  only from Steam CDN and libretro `Named_Boxarts`/`Named_Snaps` via `ArtworkLookupClient`. Building IGDB image URLs is
  net-new code in `IgdbClient`.

## What the repo already has (so this is mostly reuse)

- **`IgdbClient`** (`jordylab-be/.../gamecatalog/rest/client/IgdbClient.java`) already handles Twitch
  client-credentials auth, token cache, 401 retry, and degraded empty responses when unconfigured. Existing methods do
  title search and batched `multiplayer_modes` lookup for ROMs.
- **`TitleSource`** (`domain/TitleSource.java`) currently has `ROM(1)`, `MANIFEST(1)`, `LIBRARY(2)` with an `outranks`
  method. A new `MANUAL` value can slot in as the highest authority so scans never rename a manually added title.
- **`ScanSource`** / **`SourceType`** (`domain/ScanSource.java`, `domain/SourceType.java`) model hosts. `SourceType` has
  `STEAM` and `EMUDECK` today, each with a `platform()` override. A new non-scannable `SourceType` value is the natural
  fit for the virtual "Nintendo Switch" host. Scan ingestion keys off `(machineId|hostname, SourceType)`, so a value
  that scans never use is inert to reconciliation and purge by construction.
- **`GameInstallation`** (`domain/GameInstallation.java`) is the per-host unit keyed to a `ScanSource`. It already has
  `Presence.INSTALLED|UNINSTALLED` and a unique constraint on `(source_id, external_ref)`. This is the cleanest place
  to attach the Switch host linkage and the physical/digital format without changing `GameLibraryEntry`'s existing
  `UNIQUE (game_id, library_source)`.
- **`GameLibraryEntry`** (`domain/GameLibraryEntry.java`) represents global library membership (`OWNED`/`FAMILY`). Its
  unique key is `(game_id, library_source)` with **no host column**, so two `OWNED` rows for the same game on different
  hosts would collide today.
- **`ReconciliationService`** (`service/ReconciliationService.java`) marks missing installations `UNINSTALLED` and
  purges uninstalled games after the grace period. It only touches installations already `UNINSTALLED` and deletes a
  game only when it has no remaining installations **and** no active/within-grace library entry.
- **Security**: `SecurityConfig.java` has a deny-by-default catch-all `/api/gamecatalog/**` → `hasRole("admin")`. New
  Switch endpoints under `/api/gamecatalog/switch/**` are automatically admin-only; existing read/chat endpoints are
  explicitly `hasAnyRole("admin","guest")`.
- **Frontend**: `libs/gamecatalog/{api,ui}` use Angular signals, a signal store in `api/src/lib/game-library.store.ts`,
  hand-rolled `role="dialog"` components driven by stores, `AuthService.isAdmin` gating, and Vitest+Spectator tests.
  There is **no dialog library** installed — new dialogs follow the existing install-prompt pattern.

## Clarify decisions (2026-09-29)

The open questions from the draft were resolved as follows:

1. **Install-status treatment**: a virtual **"Nintendo Switch" host**. Switch games are owned entries on that host, so
   the existing host/platform/library-source filters cover them and no new install-status value is introduced.
2. **EmuDeck overlap**: **link, don't rebuild**. A game exists once in the catalog and can be present on multiple hosts
   (e.g. an EmuDeck ROM host and the Switch virtual host).
3. **Personal fields**: **physical/digital only**. The detail page mirrors other games; no notes or played/completed
   flags.
4. **Bulk paste**: **in scope, P2**.
5. **Switch 2**: **out of scope**. This feature is Switch 1 only; Switch 2 will be a future pseudo-host.

## Design decisions

### 1. Virtual host representation

**Decision**: add a new non-scannable `SourceType` value (e.g. `SWITCH`) with `platform()` returning `"Nintendo Switch"`,
and create a single well-known `ScanSource` row for the virtual host.

**Rationale**: scan ingestion gates on `ScanRequest.libraryType`/`SourceType`, so a value that scans never emit can
never be resolved/adopted, renamed, or purged by `ReconciliationService`. This satisfies the clarify decision with
minimal code change.

**Alternatives considered**:
- A boolean `virtual`/`manual` flag on `ScanSource`: more flexible but requires every scan/purge path to check it.
- A separate `ManualHost` table: duplicates host-filter and visibility logic.

### 2. Host linkage and format placement

**Decision**: attach Switch games to the catalog via a `GameInstallation` row on the virtual Switch `ScanSource`, and
store the `manual` flag and `format` (`PHYSICAL`/`DIGITAL`) on `GameInstallation`. Create a `GameLibraryEntry.OWNED`
row only when the game does not already have one.

**Rationale**:
- `GameInstallation` is already the per-host unit with a `(source_id, external_ref)` unique key.
- It avoids changing `GameLibraryEntry`'s existing `UNIQUE (game_id, library_source)` and backfilling host references
  for existing Steam-owned rows.
- Visibility and host filtering continue to work through the existing installed-on-enabled-source predicate.

**Alternatives considered**:
- Add `source_id` to `GameLibraryEntry` and change the unique key to `(game_id, library_source, source_id)`. Semantically
  cleaner, but requires a migration that reclassifies existing Steam-owned rows and complicates the global `OWNED`
  concept (Steam ownership is account-level, not per-host).

### 3. Title authority

**Decision**: add `TitleSource.MANUAL` with the highest authority (`outranks` all others).

**Rationale**: a manually added Switch title must never be renamed by a later EmuDeck or Steam scan. This mirrors the
005 title-authority pattern.

### 4. Identity and duplicate prevention

**Decision**: uniquely identify a Switch game by `(platform, igdbGameId)` when an IGDB match exists, and by
`(platform, normalised title)` for manual custom entries.

**Rationale**: IGDB IDs are stable; normalised titles are the best fallback when no IGDB match is available. The
"link, don't rebuild" rule means an EmuDeck ROM with the same normalised title and platform links to the same `Game`
rather than creating a duplicate.

### 5. Artwork rule

**Decision**: the 004 "no API-key-requiring provider" artwork rule is not violated because IGDB is already a configured
dependency since 005 (`IGDB_CLIENT_ID`/`SECRET`), and the resulting `images.igdb.com` CDN URLs are keyless.

## Sources

- Nintendo: [How to View Play Activity in the Nintendo Store App (Nintendo UK)](https://www.nintendo.com/en-gb/Support/Purchases-Subscriptions/How-to-View-Play-Activity-in-the-Nintendo-Store-App-2954196.html) · [How to view eShop purchase history (Nintendo Support)](https://en-americas-support.nintendo.com/app/answers/detail/a_id/22465/~/how-to-view-nintendo-switch-eshop-purchase-history)
- Unofficial routes: [NSPlayTime (mypage play_histories endpoint, mitmproxy setup)](https://github.com/CafeAuLait-CC/NSPlayTime) · [pynintendoparental](https://github.com/pantherale0/pynintendoparental) · [nxapi](https://github.com/samuelthomas2774/nxapi) · [eshop-purchase-history userscript](https://github.com/redphx/eshop-purchase-history)
- Coverage: [Pocket-lint: Play history on mobile](https://www.pocket-lint.com/how-to-view-nintendo-switch-play-history-on-mobile/) · [TheGamer: Play history in the Store app](https://www.thegamer.com/nintendo-store-app-play-history-nostalgia-3ds-wii-u/)
- IGDB: [IGDB platform ID gist](https://gist.github.com/ahmed-abdelazim/b533b443388baaafab3fc377e71e0109) · [igdb-ts examples](https://github.com/AaronWLChan/igdb-ts) · [IGDB Games API OpenAPI](https://apis.io/apis/igdb/igdb-games-api)

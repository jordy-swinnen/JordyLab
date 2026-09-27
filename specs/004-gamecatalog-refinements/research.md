# Phase 0 Research: Game Catalog Refinements

**Date**: 2026-09-27
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

Decisions and rejected alternatives for the 004 refinements. Evidence gathered against the running code (backend module, frontend libs, scan client), the Steam/libretro/SteamGridDB APIs, and the existing specs (002 + 003).

---

## R1 — Card artwork: Steam's official portrait library art, keyless

**Decision**: Card covers switch to portrait library art: Steam games resolve `https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/library_600x900.jpg` (with a `library_600x900_2x.jpg` retry), ROM games keep libretro `Named_Boxarts` (already portrait box art). No API key, no new dependency.

**Evidence**: The grid card is `aspect-[2/3]` portrait with `object-cover`; today Steam games get `header.jpg` (460×215, ratio 2.15:1) — `object-cover` crops ~92% of the width away, which is the "wrong kind of image". Steamworks documents the library capsule as the *primary* library-overview asset, 600×900 portrait — exactly the card ratio (2:3). Steam's CDN serves it per appid without auth (same host the current `header.jpg` URL already uses); some titles publish only the `_2x` variant, so probe both. Steam's own library grid, and SRM's "Steam images" default, use this exact asset — the "EmuDeck helper fetching images" effect the user described, without the API key.

**Rejected**: SteamGridDB grids (`api.steamgriddb.com/api/v2`, requires a user-registered Bearer API key; community art also varies in authenticity — deferred as an optional future provider behind the same slot model); keeping `header.jpg` and changing the card to landscape (the current card grid is designed portrait, matches box art, and the user asked for images fitted to the card, not the card fitted to the image); Google/scrape image search (nondeterministic, fragile, blocked by the spec's validation posture).

## R2 — Banner artwork: second slot, Steam hero + libretro snapshot

**Decision**: A second, separate persisted artwork slot per game — `banner`: Steam games resolve `library_hero.jpg` (1920×620 family, the wide art Steam itself shows at the top of a library detail page), ROM games resolve libretro `Named_Snaps` (landscape in-game screenshots, same repo structure as `Named_Boxarts`, one path segment apart). The banner is allowed to be a different image than the cover (they are different assets by construction for both providers). Missing banner → styled plate (the existing `plate` treatment); the cover is never stretched or center-cropped into the banner slot.

**Evidence**: Steamworks documents the Library Hero as the library-details-page top art (3840×1240 with an auto-generated 1920×620) — the same role this feature gives it. libretro thumbnails ship `Named_Snaps/` alongside `Named_Boxarts/` in every system repo, so the existing `ArtworkLookupClient` repo mapping is reused with a different segment and a HEAD probe. The existing single `artwork_status`/`artwork_ref` pair becomes `cover_*`, plus new `banner_*` — one `ArtworkStatus` enum serves both slots (PENDING → EXTERNAL_URL/PLACEHOLDER; LOCAL_UPLOAD/LOCAL_FALLBACK_REQUESTED remain valid for cover uploads).

**Rejected**: Reusing the cover in the banner slot with CSS cropping (looks broken for portrait covers — the exact defect being fixed, mirrored); a second image *type* on the same slot (no use case — cover and banner are never the same URL in practice); SteamGridDB heroes (API key, same rejection as R1); Steam `header.jpg` as the Steam banner (460×215 is a store capsule, not a hero; `library_hero` is the correct asset and the user asked for "appropriate").

## R3 — Stale cover URLs re-resolve without a re-scan wave

**Decision**: A guarded data migration (SQL, not a forced ingest-version bump) resets only the affected slot: `UPDATE game SET cover_status='PENDING', cover_ref=NULL WHERE cover_status='EXTERNAL_URL' AND platform='Steam'`. ROM covers (libretro box art URLs) are still valid and are left untouched. The next `ArtworkService` pass re-resolves Steam covers to portrait art; banner columns are born `PENDING`.

**Rationale**: Bumping `CURRENT_INGEST_VERSION` would force every source to re-upload full scan payloads for a purely server-side image-format change — a "full refresh", which the user explicitly does not want. The artwork pass already runs after every applied sync, and `resolveDiscoveredGame` handles PENDING games whenever a sync lands; a scheduled artwork sweep is not needed since every source syncs at least hourly and the migration itself flips the state in SQL.

**Rejected**: Ingest-version bump (unnecessary client-visible churn, contradicts FR-007); resolving new URLs on read in `GameQueryService` (URLs must be persisted per the 002 contract; on-read probing would put HEAD calls on the request path); leaving old rows pointing at `header.jpg` until each game happens to change (fix would not land for existing installs).

## R4 — Deterministic metadata: Steam store appdetails for Steam, extended enrichment for ROMs

**Decision**: Four new bounded, nullable columns on `game`: `genres` (≤ 200 chars, comma-separated), `developer` (≤ 100), `publisher` (≤ 100), `release_year` (int, 1950..now+2). Steam games: a new `SteamAppDetailsClient` (RestClient, like `ArtworkLookupClient`) fetches `https://store.steampowered.com/api/appdetails?appids={appid}&filters=basic` once per game — a scheduled `SteamMetadataService` mirroring `EnrichmentService` (status `PENDING`/`OK`/`FAILED`, attempts counter, daily reset of FAILED, batch size from a new `metadata()` properties record). ROM games: the existing enrichment system prompt is extended with the four fields, validated with the same bounds. Multiplayer facts stay AI-enriched for all platforms (Steam categories give booleans but not the max-local-players count the UI shows).

**Evidence**: The appdetails endpoint is keyless, official-hosted, and returns `genres[]`, `developers[]`, `publishers[]`, `release_date.date` — deterministic per appid, exactly "the more deterministic data we have, the more questions we can ask". The user's stated goal is chat questions; structured columns (not prose) are what `ChatService` can filter on (002 FR-020 already established the structured-facts rule).

**Rejected**: IGDB (requires Twitch OAuth client credentials — a new secret to provision for no Steam gain); RAWG (free tier requires an API key); MobyGames (paid); AI-only for the deterministic fields (the user explicitly asked for deterministic data — AI years/names are exactly what deterministic sourcing avoids); extending to ratings/ESRB/descriptions (unbounded scope creep — four fields already enable year-range, developer, and genre questions).

## R5 — Multi-host model: `GameInstallation` split, not duplicate-row merging

**Decision**: A new `game_installation` table (aggregate `GameInstallation`: game, source, `external_ref`, presence, first/last seen, `uninstalled_at`; unique (source, external_ref)). `game` keeps only host-independent data: title, platform, `steam_app_id` (new, for Steam games), enrichment fields, metadata fields, cover + banner slots. Reconciliation per source operates on installations; `Game` visibility = exists an installation that is installed on an enabled source. The 30-day grace-then-purge runs per installation; a game whose installations are all purged is deleted with its local artwork file (orphan cleanup in the same job). Backfill migration inserts one installation per existing `game` row from its (source, external_ref, presence, seen) columns, then drops those columns from `game`. The shrink guard, payload hash, digest, and source lifecycle (002/003) are untouched — they are per-source properties and now count installations via the join.

**Rationale**: This is the only model where "only the host is added to an existing game" is structural, not best-effort: a second host's snapshot finds the existing `Game` (see R6) and inserts/updates one `GameInstallation` row; enrichment and artwork state live on the untouched `Game` row, so a full refresh is impossible by construction. It also makes the disabled-source rule per-host (FR-006) fall out naturally: hiding a source's installations hides only that host's presence.

**Rejected**: Keeping per-source `game` rows and merging duplicates at query time (grid, chat citations, counts, and pagination all need dedup logic; chat would cite ambiguous rows; enrichment would still run per duplicate — every rejection criterion the user listed stays broken); a `game_hosts` join table while keeping presence on `game` (presence/seen/grace are intrinsically per-host — the split has to move the lifecycle columns, or FR-006 scenarios 3/5/6 cannot be expressed); per-host `Game` rows sharing an `Enrichment` table via a foreign key (extra join for the common path, and title/platform drift across hosts becomes unsynchronized state).

## R6 — Cross-host adoption keys: appid for Steam, platform + normalized title for ROMs

**Decision**: During reconciliation, for each submitted entry: (a) Steam entries match the `Game` with `platform = 'Steam'` and `steam_app_id = externalRef`; (b) ROM entries match the `Game` with same `platform` and `LOWER(title) = LOWER(entry.title)` (titles are already NFC-normalized + agent-normalized at intake). Match found → adopt (upsert that source's installation; never touch `Game` state); no match → create `Game` + its first installation. `steam_app_id` is set when a Steam game is created and is treated as identity (an appid is globally unique across hosts).

**Rationale**: A ROM's file path is host-specific, so `external_ref` cannot join across hosts; the platform + normalized title pair is the only stable cross-host key the pipeline already produces (the scan client normalizes titles before submission, per 002 FR-009). Same-platform title collisions for genuinely different games are accepted in v1 (spec Assumptions) — the dominant case the user described is the same game on two hosts.

**Rejected**: Matching ROMs on platform + title only when the platform has exactly one candidate (silent, nondeterministic adoption); a manual merge UI (out of scope, YAGNI); content-hash identity (rejected in 002 already); matching Steam games on title (appid exists and is exact — titles differ between hosts for the same appid).

## R7 — Chat attachment: `gameIds` on the request, context injection, query-param routing

**Decision**: `ChatRequest` gains `gameIds: List<UUID>` (optional, max 5, each must be visible → else `400`). `ChatService`: the translation call still runs (the question may contain a catalog-wide part); the composition prompt gains an "Attached games" section with the attached games' full rows (title, platform, hosts, multiplayer facts, genres/developer/publisher/release year, description) ahead of the grounded filter rows; cited refs = attached games ∪ filter rows; the `NO_MATCH` early return is suppressed when attachments exist (the answer composes from the attachments). Frontend: the detail page's "Ask the catalog" button navigates to `/games/chat?attach={gameId}`; `GameChatStore` loads and holds the attached game (a chip above the input, removable, max one in the UI — the API tolerates five for future extension), sends `gameIds` with every ask while attached.

**Evidence**: The two-call grounding pattern (002 R5) needs no change for attachments — the attachment only widens the composition context; the filter stays catalog-grounded, so "which of my other games are like this one?" still works. Session-scoped chat history is a 002 assumption; attachments are therefore session state, not persisted server-side.

**Rejected**: Server-side conversation/session state (contradicts the 002 assumption, needs storage for no v1 value); embedding the game data in the question text client-side (prompt injection surface — the backend must resolve ids to rows itself, never trust client-composed context); a separate per-game chat endpoint (`/chat?gameId=` duplicates the ask flow; the list shape covers future multi-attach); making the translation call skip when attachments exist (breaks mixed questions like "what else do I have like this?").

---

**Resolved NEEDS CLARIFICATION items from Technical Context**: none remained after research — the four stories map to seven concrete decisions above; no new dependency, project, or provider was introduced (deliberately — see R1/R4 rejections).

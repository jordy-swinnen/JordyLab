# Feature Specification: Steam Library & Family Library in the Game Catalog

**Feature Branch**: `005-steam-library-sync`

**Created**: 2026-09-27

**Status**: Draft

**Module**: `gamecatalog`

**Builds on**: `004-gamecatalog-refinements` (multi-host model: host-independent `game`, per-host `game_installation`, host filter, inline metadata/enrichment passes, no schedulers). 004 is merged.

**Input**: User description: "The catalog shows installed Steam and EmuDeck games with a host filter. I also want games in my Steam library and Steam Family library that are NOT installed, with an installation status I can filter on like the host. Common-sense architecture: if a game already exists and another device or source reports it, only the new link is added — no full refresh of game data and no new AI description."

---

## Context

After 004 the catalog has one **game** per real game (Steam: by app ID; ROMs: by platform + normalized title) and one **installation** per host that has it on disk. A game is only visible while it has at least one installed installation, and a game with no installations left is deleted after the grace period — together with its AI description and artwork.

This feature adds a second way a Steam game can belong to the catalog: **library membership** (owned by the user, or available through the Steam Family). A game is visible if it is installed somewhere **or** is in the library. Installation status and library source become filterable.

The guiding rule for the whole feature: **a game's data is produced once and reused by every source that points at it.** New hosts, new library sources and repeated syncs only add or update *links*; they never re-trigger metadata lookups, artwork resolution or AI enrichment for a game that already has them.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — A game reported by a second source is linked, not rebuilt (Priority: P1)

As the catalog owner, when a game that is already in the catalog is reported by another host or by the Steam library, I want only that new link to be recorded, so I don't pay again (time, AI cost, Steam rate limits) for data the catalog already has.

**Why this priority**: It is the foundation for everything else. Adding the library would otherwise create hundreds of duplicate or re-enriched games. 004 already does this for host-to-host; this story extends the guarantee to library sources and makes it enforceable.

**Independent Test**: Take an enriched Steam game installed on JordyBox. Run a scan from a second host that has it, then a library sync. Verify the game's ID, description, metadata and artwork are unchanged, no AI call and no Steam metadata call was made for it, and it now has two installations and one library entry.

**Acceptance Scenarios**:

1. **Given** an enriched game exists, **When** a new host reports it, **Then** exactly one new installation is linked to the existing game, and the game's enrichment, metadata and artwork are untouched.
2. **Given** an enriched game exists, **When** the library sync reports it, **Then** exactly one library entry is linked to the existing game, and nothing else about the game changes.
3. **Given** a game that is not yet in the catalog, **When** two sources report it at nearly the same time (e.g. a scan and a library sync running concurrently), **Then** exactly one game exists afterwards.
4. **Given** a library sync or scan whose content is identical to the previous one, **When** it is received, **Then** it is recorded as "no change" and does no per-game work at all.
5. **Given** a game already exists, **When** a source reports it with a slightly different title, **Then** the title follows a single defined authority (see FR-012) and the change does not re-trigger enrichment.
6. **Given** any game, **When** it is enriched, **Then** the AI is asked only for facts that no deterministic source has already provided for that game.

---

### User Story 2 — See my whole owned Steam library (Priority: P1)

As the catalog owner, I want every Steam game I own to appear in the catalog, including games not installed on any host.

**Why this priority**: The core of the request, and possible with Steam's official, documented API.

**Independent Test**: Uninstall a Steam game from JordyBox, rescan, wait past the grace period, run a library sync, and confirm the game is still in the catalog as "Not installed" with its original description.

**Acceptance Scenarios**:

1. **Given** an owned game that is not installed anywhere, **When** a library sync completes, **Then** it appears with status "Not installed" and source "Owned".
2. **Given** an owned game installed on JordyBox, **When** the scan and the library sync have both run, **Then** it appears once, "Installed", host JordyBox, source "Owned".
3. **Given** an owned game whose last installation passed the grace period, **When** the purge runs, **Then** the installation is removed but the game and its enrichment are kept because it is still in the library.
4. **Given** the library sync runs before any host scan, **When** I open the catalog, **Then** all owned games are listed as "Not installed".

---

### User Story 3 — Filter by installation status and library source (Priority: P1)

As the catalog owner, I want to filter on installation status (Installed / Not installed / All) and library source (Owned / Family / Local), combinable with the existing search, platform and host filters.

**Independent Test**: With a mixed catalog, apply every filter value and combination, and check the counts.

**Acceptance Scenarios**:

1. **Given** a mixed catalog, **When** I choose "Installed", **Then** only games with an installed installation on an enabled source are shown (today's behaviour).
2. **When** I choose "Not installed", **Then** only library games with no installed installation are shown.
3. **When** I choose "All", **Then** both are shown, and every card shows its status and source.
4. **Given** a host is selected, **When** the status is "Not installed", **Then** the host filter is hidden while "Not installed" is active — "Not installed" is always evaluated across all enabled sources (there is no per-host "not installed" semantics).
5. **Given** the default view, **When** I open the catalog for the first time after this feature, **Then** the status filter defaults to "Installed", so the catalog looks exactly like it does today until I choose otherwise.
6. **Given** filters are set, **When** I reload or share the URL, **Then** they behave the same way the host filter does.
7. **Given** the chat, **When** I ask "which games I own but haven't installed support 4 local players?", **Then** the chat can filter on installation status and library source.

---

### User Story 4 — See games from my Steam Family library (Priority: P2)

As the catalog owner, I want games shared through my Steam Family to appear, marked "Family".

**Why this priority**: Valuable, but only possible through an undocumented Steam endpoint that needs a short-lived user access token (see Constraints).

**Acceptance Scenarios**:

1. **Given** a game owned only by another family member, **When** a family sync completes, **Then** it appears with source "Family".
2. **Given** a game I own that is also in the family library, **When** both syncs complete, **Then** it shows source "Owned" (Owned outranks Family) and exists once.
3. **Given** a family title that Steam excludes from sharing, **When** a family sync completes, **Then** it is omitted from the catalog entirely — no entry, no enrichment, no cost.
4. **Given** a family game installed on JordyBox, **When** scan and family sync complete, **Then** it is "Installed" on JordyBox with source "Family".

---

### User Story 5 — Refresh the family library without the server holding my Steam login (Priority: P2)

As the catalog owner, I want to paste a short-lived Steam access token in settings and trigger a family sync, so the server never stores a credential with general access to my Steam account.

**Acceptance Scenarios**:

1. **Given** a valid token, **When** I trigger a family sync, **Then** it completes and settings show the time of the last successful family sync.
2. **Given** an expired or invalid token, **When** a family sync runs, **Then** it fails clearly and no catalog data changes.
3. **Given** family data older than a configurable number of days, **When** I open the catalog, **Then** I see an unobtrusive "family library may be out of date" hint.

---

### User Story 6 — Big first sync without a big bill (Priority: P2)

As the catalog owner, I want the first library sync (possibly many hundreds of games, more with family) to be processed gradually and cheaply, so it doesn't hammer Steam's rate limits and doesn't spend AI budget on a backlog nobody is waiting on.

**Acceptance Scenarios**:

1. **Given** a first library sync adds many new games, **When** it completes, **Then** the games are visible immediately with title and artwork, and metadata/enrichment are processed in bounded batches afterwards.
2. **Given** a backlog of games waiting for metadata, **When** batches are processed, **Then** installed games are processed before not-installed ones.
3. **Given** a library game that is not installed anywhere, **When** backlog processing runs, **Then** it receives only deterministic Steam metadata and artwork — no AI call is made for it; if it is later installed, it is enriched like any installed game.
4. **Given** Steam's store rate limit is hit, **When** metadata batches run, **Then** processing pauses and resumes later without marking games as failed.

---

### User Story 7 — Local multiplayer is a fact, never a guess (Priority: P2)

As the catalog owner, I want to know whether a game supports local co-op or split-screen, and I
want that answer to come from a real source — Steam's own listing, or IGDB for the ROMs Steam
doesn't cover — never from the AI making something up, so I don't plan a couch co-op night around
a feature the game doesn't actually have.

**Why this priority**: Extends this feature's core rule (§ Context: "a game's data is produced
once and reused") to a specific, previously AI-guessed field. Depends on the Steam metadata fetch
this feature already corrects (User Story 1), so it rides on the same PR/branch rather than a
separate one.

**Acceptance Scenarios**:

1. **Given** a Steam game whose store page lists a split-screen or local co-op category, **When**
   its metadata is fetched, **Then** `localMultiplayer`/`splitScreen` are set from that listing,
   attributed to source "Steam", with no extra Steam call beyond the metadata fetch already made.
2. **Given** a Steam game whose store page has no category data, or a ROM with no `steam_app_id`,
   **When** the multiplayer backlog is processed, **Then** IGDB is queried by title and, on a
   match, `localMultiplayer`/`splitScreen`/`maxLocalPlayers` are set from IGDB, attributed to
   source "IGDB".
3. **Given** neither Steam nor IGDB has data for a game (or IGDB is not configured), **When** the
   backlog is processed repeatedly, **Then** the game is retried a bounded number of times and
   then left "Unknown" rather than retried forever; a manual refresh resets it for one more try.
4. **Given** a game with no local-multiplayer data at all, **When** it is AI-enriched, **Then**
   the AI is never asked to guess `maxLocalPlayers` or imply local multiplayer support — only
   deterministically-known facts are ever shown.
5. **Given** the catalog, **When** I filter to "Local multiplayer only" (grid) or ask chat which
   games support local multiplayer, **Then** only games with a confirmed `localMultiplayer=true`
   are returned — never an unresolved or AI-guessed one.
6. **Given** Steam's store rate limit is hit while resolving the backlog, **When** the batch is
   paused, **Then** it resumes on the next pass without counting as a failed attempt (same
   behaviour as the metadata pass in User Story 6).

---

### Edge Cases

- **Empty or failed Steam response** (API error, privacy glitch, token expired): the sync is recorded as failed or suspicious, and no library entries are removed.
- **Library shrinks sharply compared with the previous sync**: rejected as suspicious, the same way scans are guarded against suspicious shrinks today. A manual "force" is possible.
- **Game leaves the library** (refund, family member leaves, sharing revoked): its library entry is marked removed. If the game also has no installations, it follows the same grace-period purge as uninstalled games. If it returns within the grace period, the existing game and its data are reused.
- **Installed but not in any library** (unplayed free-to-play, family game before a family sync, titles Steam omits from the owned list): shown as installed with source "Local/Unknown", never removed because of this.
- **Steam tools and runtimes** (Proton, Steam Linux Runtime, Steamworks redistributables): they have app manifests on Linux and are not games. They must not be catalogued or enriched from either source.
- **Title differs between the app manifest and the library**: the game keeps one title per the title-authority rule; neither source keeps overwriting the other on every sync.
- **Disabled host**: its installations don't count toward "Installed". A game known only through that host but in the library shows as "Not installed".
- **Same app ID reported under a different platform value**: treated as the same Steam game (app ID is the identity).

---

## Requirements *(mandatory)*

### Functional Requirements

**Identity & reuse (cost economy)**

- **FR-001**: A Steam game MUST have exactly one catalog entry per Steam app ID. This MUST be guaranteed by the data store, not only by application logic, so concurrent sources cannot create duplicates.
- **FR-002**: Every source (host scan, owned-library sync, family sync) MUST resolve an existing game before creating one, and on a match MUST only add or update its own link (installation or library entry).
- **FR-003**: Adding, updating or removing a link MUST NOT reset or re-run a game's metadata lookup, artwork resolution or AI enrichment. Only an explicit manual refresh of that game may do so.
- **FR-004**: A sync whose content is identical to the previous one from the same source MUST short-circuit as "no change", as scans already do.
- **FR-005**: Before enrichment, deterministic data available for a game (Steam store metadata, including genres and multiplayer categories) MUST be used, and the AI MUST only be asked for what is still missing. AI output MUST NOT overwrite values that came from a deterministic source.
- **FR-006**: New games MUST become visible immediately. Metadata and enrichment for them MUST run in bounded batches, installed games first, respecting Steam's store rate limit.
- **FR-007**: The number of AI calls and Steam store calls per sync run MUST be recorded, so the cost of a sync is visible.

**Library sync**

- **FR-008**: The system MUST retrieve the owned Steam library for one configured account, independent of any host being online.
- **FR-009**: The owned-library sync MUST be triggerable manually and MUST also run automatically at most once per configurable interval, piggybacking on an applied Steam scan, consistent with the "no schedulers" design from 004. [Assumption — change if a cron is preferred.]
- **FR-010**: The system MUST retrieve the Steam Family shared library when a valid short-lived user access token is supplied, via a manual trigger.
- **FR-011**: The system MUST NOT store a Steam password, Steam login cookie, or any credential with general account access. The family token MUST NOT be logged or returned by any API, and is used only for the sync it was supplied for.
- **FR-012**: For Steam games, the title authority MUST be the Steam library name when one is known, otherwise the app manifest name. A lower-authority source MUST NOT overwrite a higher-authority title.
- **FR-013**: Non-game Steam apps (tools, runtimes, redistributables) MUST be excluded from the catalog for both scans and library syncs.

**Lifecycle**

- **FR-014**: A game MUST be purged only when it has no installed installation, no installation within the grace period, and no active or grace-period library entry.
- **FR-015**: Library entries that disappear from a successful sync MUST be soft-removed with a timestamp and purged after the same grace period as installations.
- **FR-016**: A failed, suspicious or partial sync MUST NOT remove or modify any library entry.

**Status, source & filtering**

- **FR-017**: Installation status MUST be derived: `INSTALLED` if the game has at least one installed installation on an enabled source, otherwise `NOT_INSTALLED`.
- **FR-018**: Library source MUST be derived with precedence `OWNED` > `FAMILY` > `LOCAL`, where `LOCAL` means known only from a scan (includes all ROMs).
- **FR-019**: Game list, detail, platform list, host list and chat queries MUST treat a game as visible if it is installed or has an active library entry, and MUST support filtering by installation status and library source, combinable with the existing filters.
- **FR-020**: The status filter MUST default to "Installed" so the default view is unchanged from today.
- **FR-021**: Cards and the detail view MUST show installation status and library source. For family games, the detail view SHOULD show the owning family member's display name.
- **FR-022**: Library games that are not installed on any host MUST NOT be AI-enriched; they receive deterministic metadata and artwork only, and where a description is shown it is the deterministic Steam-provided one. If such a game is later installed, it is enriched like any installed game.
- **FR-023**: While the status filter is "Not installed", the host filter MUST NOT be offered or combined; "Not installed" is always evaluated across all enabled sources.

**Deterministic local multiplayer metadata**

- **FR-024**: `localMultiplayer`, `splitScreen` and `maxLocalPlayers` MUST be deterministic only — derived from Steam store categories or IGDB — and MUST NEVER be inferred or guessed by the AI. A game with no deterministic data available MUST show as unresolved rather than receive an AI guess.
- **FR-025**: When a Steam game's metadata fetch (FR-005) has category data, local-multiplayer facts MUST be derived from it in that same fetch, at no additional Steam store call.
- **FR-026**: When a Steam game has no category data, or the game has no `steamAppId` (a ROM), the system MUST fall back to querying IGDB by title for local-multiplayer facts, when IGDB is configured.
- **FR-027**: An unresolved local-multiplayer lookup MUST be retried a bounded number of times and then left "Unknown" until a manual refresh resets it; a Steam store rate limit during this pass MUST pause the batch without counting as a failed attempt (mirrors FR-006's metadata pass).
- **FR-028**: Game list, detail and chat queries MUST support filtering to games with confirmed `localMultiplayer=true`, and MUST derive an "online only" indicator (`onlineMultiplayer && !localMultiplayer`) rather than storing it.

### Key Entities *(include if feature involves data)*

- **Game** (exists): the host-independent catalog entry holding identity, title, deterministic metadata, AI enrichment and artwork. Unchanged in role. Gains a guaranteed-unique Steam app ID, a recorded title authority, and deterministic local-multiplayer facts (local co-op, split-screen, max local players, provenance source, retry attempts).
- **Installation** (exists): a game on disk on a scan source (host + library type). Unchanged.
- **Library entry** (new): a game available to the user through a library source — Owned or Family — with first seen, last seen, removed-at, and for Family the owner(s). At most one per game per library source. Family titles excluded from sharing are not catalogued at all.
- **Library sync run** (new): one owned or family sync — source, time, outcome (applied / no change / failed / suspicious), content hash, counts added/removed/unchanged, and the number of metadata and AI calls it caused.
- **Steam account configuration** (new): the account to sync and a reference to the Web API key (secret, outside the database). Family token state (present/expired) — never the value — is exposed to the UI.

---

## Success Criteria *(mandatory)*

- **SC-001**: Reporting an existing game from a new host or library source causes 0 AI calls and 0 Steam store metadata calls for that game, verified by test.
- **SC-002**: Running the same library sync twice in a row causes 0 per-game writes and 0 external calls on the second run.
- **SC-003**: After the first full library sync, the number of AI calls equals at most the number of games that had no enrichment before, and is lower than that number for Steam games whose facts Steam already provides.
- **SC-004**: No duplicate games exist for any Steam app ID after concurrent scan + library sync runs (checked by a database constraint and a concurrency test).
- **SC-005**: An owned but uninstalled game keeps its description through any number of uninstall/reinstall cycles and grace-period purges.
- **SC-006**: The default catalog view shows exactly the same games as before this feature.
- **SC-007**: No Steam credential or token appears in logs, API responses, the frontend bundle or the repository.
- **SC-008**: A failed, empty or expired-token sync removes or changes 0 catalog rows.
- **SC-009**: A first library sync of N not-installed games causes 0 AI calls; deterministic metadata and artwork only.
- **SC-010**: No game's shown local-multiplayer status is AI-derived, verified by test — every `localMultiplayer`/`splitScreen`/`maxLocalPlayers` value in the catalog traces to a Steam category fetch or an IGDB lookup, or is unresolved.

---

## Assumptions

- One Steam account is catalogued.
- 004-gamecatalog-refinements is merged before work starts.
- Family-sharing lock state (someone else is currently playing) is out of scope.
- Installing or launching games from the catalog is out of scope.
- Steam playtime/last-played data is out of scope, even though the sync responses contain it.
- Game Catalog AI enrichment switches from the Anthropic Sonnet model to the cheaper Haiku model as a cost decision; local (Ollama) inference remains out of scope for this feature.
- IGDB credentials (`IGDB_CLIENT_ID`/`IGDB_CLIENT_SECRET`) are optional configuration: the local-multiplayer fallback for ROMs/no-category Steam games degrades to "Unknown" rather than failing when unset. IGDB's field/endpoint shape is confirmed against live responses (its old `category` field is now `game_type`) rather than only documentation, mirroring the family-library research approach.

## Constraints discovered during research

- **Owned library**: Steam's documented `IPlayerService/GetOwnedGames` works with a standard Web API key and can read a private library when the key belongs to the same account. Some limited or unvetted titles are known not to be returned.
- **Family library**: there is no documented API. The known source is the undocumented `IFamilyGroupsService/GetSharedLibraryApps`, which authenticates with a user access token that is valid for roughly 24 hours rather than with the Web API key. It can change or disappear without notice.
- **Steam store metadata** (`store.steampowered.com/api/appdetails`) is keyless but rate-limited, reportedly around 200 requests per 5 minutes. This matters for a large first sync.

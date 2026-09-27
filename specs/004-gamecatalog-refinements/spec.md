# Feature Specification: Game Catalog Refinements

**Feature Branch**: `004-gamecatalog-refinements`

**Created**: 2026-09-27

**Status**: Draft

**Input**: User description: "Some finetuning I want for the library: The overview shows the wrong kind of image — the image shown in the overview should be an image that is fitted to the card (like EmuDeck's Steam ROM helper fetching images from image libraries). The detail page should also show a wide banner image, appropriate and possibly different from the overview card image. The data shown on the detail page is very minimal — we should find extra deterministic data to show there; the more deterministic data we have, the more questions we can ask about it in the chat function. I also want to filter on Host on the overview, and a game can have multiple hosts. Check the backend that only the host is added to an existing game and that a full refresh is not triggered if a host adds a game that already exists. Finally, the 'Ask the catalog' button should show an AI icon (the star thing), and when clicked it not only shows the chat screen but adds the game as an attachment — when the prompt is sent the game is added to the context so the response takes the game into account."

---

## Overview

Four refinements to the built Game Catalog (specs/002 + 003): (1) artwork that fits where it is shown — portrait card covers in the grid and a wide banner on the detail page, sourced like Steam's own library art; (2) richer deterministic metadata on the detail page (Steam store data for Steam games, validated AI facts for ROMs) that also becomes queryable by chat; (3) a multi-host catalog: a game installed on several machines is one entry with several host links, filterable by host, with backend adoption semantics so a new host adds a link — not a duplicate entry, re-enrichment, or full refresh; (4) an "Ask the catalog" action with the spark icon that opens chat with the game attached, and the attached game's data is injected into the chat context.

---

## User Scenarios & Testing

### User Story 1 - Card-fitted covers and a wide detail banner (Priority: P1)

As a user, the grid shows every game with a portrait cover that fits the card's shape — like Steam's own library grid and EmuDeck's Steam ROM Manager artwork — instead of a wide header image cropped into a portrait card. On the detail page, I see a wide banner image across the top (like Steam's library hero), which is allowed to be a different image than the card cover. Games whose art cannot be found show the deliberate styled placeholder plate, as today.

**Why this priority**: The most visible defect today — Steam cards render a cropped sliver of the 460×215 header. Artwork slotting is also independent of the other stories, so it can land first.

**Independent Test**: Open the grid and verify Steam games show portrait 600×900 library art (not wide headers) and ROM games show box art, both fitting the card ratio. Open several detail pages and verify a wide banner (Steam hero / ROM in-game snapshot), different from the cover where available, with a styled plate when no banner exists.

**Acceptance Scenarios**:

1. **Given** a Steam game with published Steam library art, **When** its card renders, **Then** the portrait library image (600×900 family) fills the portrait card without distorted cropping; no 460×215-style wide header is used on cards.
2. **Given** a ROM game with available box art, **When** its card renders, **Then** the box art fills the portrait card.
3. **Given** a game's detail page, **When** it renders, **Then** a wide banner image is shown at the top — Steam games use Steam's wide hero art and ROM games use a wide in-game screenshot, which may differ from the card cover.
4. **Given** no wide banner exists for a game, **When** the detail page renders, **Then** a styled placeholder plate is shown instead of a broken or distorted image.
5. **Given** no cover exists for a game, **When** the grid renders, **Then** the existing neutral placeholder plate is shown (unchanged behavior).

---

### User Story 2 - One game, many hosts (Priority: P2)

As the owner, I run the scan client on more than one machine (e.g., JordyBox and my desktop). A game installed on several hosts appears once in the catalog, not once per host. The backend links a host to the existing game when it recognizes it: a Steam appid is the same game everywhere, and a ROM with the same platform + normalized title is treated as the same game. Adding a host's copy must not create a duplicate entry, must not reset or re-run enrichment or artwork, and must not trigger a full refresh of the game's data.

**Why this priority**: Foundation for the host filter and host-aware chat; it removes duplicate cards and duplicated AI work while preserving the trust rules (per-host snapshots remain authoritative for what that host has installed).

**Independent Test**: Sync the same Steam game from two hosts and verify exactly one catalog entry with both hosts recorded, enrichment untouched (no re-generation, no status reset), and no second card. Uninstall the game on one host and verify it stays visible from the other.

**Acceptance Scenarios**:

1. **Given** a game already installed on host A, **When** host B's snapshot contains the same game (same Steam appid, or same platform + normalized title for ROMs), **Then** the backend records the new host link on the existing game — no new entry is created and enrichment/artwork state is untouched.
2. **Given** the same game installed on hosts A and B, **When** I browse the grid, **Then** the game appears as one card.
3. **Given** the same game installed on hosts A and B, **When** host B's next successful snapshot no longer contains it, **Then** only host B's link is marked uninstalled and the game remains visible via host A.
4. **Given** a game's last installed host link passes the 30-day grace period, **When** the purge job runs, **Then** the game and its locally stored artwork are deleted.
5. **Given** host A's source is disabled, **When** the game is also installed on enabled host B, **Then** the game remains visible (disabled sources hide only their own host link, not the shared game).
6. **Given** a game rediscovered on a host within the 30-day grace period, **When** reconciliation runs, **Then** that host link is restored installed without regenerating the game's retained data.

---

### User Story 3 - Filter the overview by host (Priority: P3)

As a user, I can narrow the grid by host in addition to the existing title search and platform filter — "show me only what's on JordyBox" — and the available hosts are derived from what is actually installed and visible.

**Why this priority**: Depends on the multi-host model (Story 2); small once that exists.

**Independent Test**: With games from at least two hosts, filter by one host and verify only that host's games show; clear the filter and verify all games return. Search + platform + host filters combine.

**Acceptance Scenarios**:

1. **Given** games installed on two hosts, **When** I select one host in the host filter, **Then** only games installed on that host remain visible.
2. **Given** combined filters, **When** search, platform, and host are all set, **Then** all three apply together (AND semantics).
3. **Given** no host selected, **When** the grid renders, **Then** all visible games show regardless of host.

---

### User Story 4 - Richer deterministic detail data (Priority: P4)

As a user, the detail page shows more than genre and multiplayer: genres, developer, publisher, release year, and which hosts the game is installed on — plus the existing prose and multiplayer facts. Steam games get their deterministic facts from Steam's public store data; ROM games get the same fields from validated AI enrichment. Every persisted field is structured and queryable, so chat can answer questions like "which 90s platformers do I own?" or "what Rare games do I have?" from the catalog rows only.

**Why this priority**: Multiplies the value of chat (Story 5) and the detail page; independent of the host model except for the hosts list display.

**Independent Test**: Open Steam game detail pages and verify genres, developer, publisher, and release year match Steam store data; open ROM detail pages and verify the same fields appear from enrichment; ask chat a question over a new field and verify the answer only names catalog games matching it.

**Acceptance Scenarios**:

1. **Given** a Steam game, **When** its metadata is fetched, **Then** genres, developer, publisher, and release year are persisted as discrete fields sourced from Steam's public store data — not AI-invented.
2. **Given** a ROM game, **When** enrichment runs, **Then** the same fields are produced and validated with the same bounds as any structured fact.
3. **Given** a detail page, **When** it renders, **Then** the spec sheet shows genres, developer, publisher, release year, hosts, and the existing multiplayer facts; unavailable fields are omitted, never fabricated.
4. **Given** Steam store data is temporarily unavailable, **When** the metadata fetch fails, **Then** the attempt is recorded and retried later, the game remains browsable, and the detail page omits the fields rather than showing an error or invented values.
5. **Given** the new fields are persisted, **When** chat translates a question over genres / developer / release-year range / host, **Then** the grounded query filters on those fields and cites the actual rows.

---

### User Story 5 - "Ask the catalog" with the game attached (Priority: P5)

As a user, the detail page's "Ask the catalog" button shows the spark icon (the four-pointed AI star), and clicking it opens the chat screen with that game attached — visibly, as an attachment chip I can remove. When I send my prompt, the attached game's full catalog data is injected into the chat context, so the answer takes the specific game into account (multiplayer facts, metadata, hosts) and still never invents games.

**Why this priority**: Delightful but depends on the chat API gaining attachment support and is better with the richer fields of Story 4.

**Independent Test**: From a game's detail page, click "Ask the catalog", verify the chat opens with the spark icon on the button and the game shown as an attachment; ask "is this good for 4 players on the couch?" and verify the answer draws on the attached game's facts and cites it; remove the attachment and verify a plain catalog-wide question behaves as before.

**Acceptance Scenarios**:

1. **Given** a game's detail page, **When** I view the "Ask the catalog" button, **Then** it shows the spark icon.
2. **Given** the button is clicked, **When** chat opens, **Then** the game is attached and shown as a removable attachment chip.
3. **Given** an attached game, **When** I send a prompt, **Then** the request carries the game id and the backend injects the attached game's catalog row into the answer's context; the attached game is always cited.
4. **Given** an attached game, **When** the answer composes, **Then** it is grounded in catalog data (including the attached row) and names no game outside the catalog.
5. **Given** the attachment is removed, **When** I ask a question, **Then** the request behaves exactly like plain catalog-wide chat today.

---

### Edge Cases

- **Steam game without a published portrait library image** → cover falls back to the existing chain (placeholder plate); never a wide header stretched into the card.
- **Steam game without a hero banner** → banner slot falls back to the styled plate; the cover is not cropped into the banner slot.
- **Same normalized ROM title on the same platform on two hosts** → one game, two host links (accepted rare-collision risk; see Assumptions).
- **A host with both a Steam and an EmuDeck source** → two sources, same hostname: the host filter selects games installed via either source.
- **Host's source disabled while another host still serves the game** → game stays visible (Story 2, scenario 5).
- **Attached game no longer visible (uninstalled between navigation and send)** → the request is rejected with an explicit error; chat shows the unavailable state, never a general-knowledge answer.
- **More attachments than the request allows** → rejected with an explicit validation error.
- **Steam store metadata endpoint rate-limits or errors** → per-game failure counter with later retry; no fabricated fields.
- **Question combines an attached game with a catalog-wide filter** → answer composes from the union: attached row + grounded filter rows, all cited.

---

## Requirements

### Functional — Artwork

- **FR-001**: The overview grid MUST render each card with portrait artwork fitted to the card's aspect ratio (no wide header-style images). Steam games MUST use Steam's official portrait library art (the `library_600x900` family on Steam's CDN, derived from the Steam appid); ROM games MUST continue to use box art (libretro `Named_Boxarts`). Missing cover art MUST render the existing placeholder plate.
- **FR-002**: The detail page MUST render a wide banner slot (hero-ratio) at the top, distinct from the card cover: Steam games use Steam's wide hero art (`library_hero` family, same appid derivation); ROM games use a wide in-game screenshot (libretro `Named_Snaps`). A missing banner MUST render a styled plate; the cover MUST NOT be stretched or cropped into the banner slot. Cover and banner are separate persisted artwork slots per game, each with its own status, resolved after sync like artwork today.
- **FR-003**: Existing resolved external cover URLs MUST be re-evaluated when the URL format changes (Steam covers move from `header.jpg` to portrait library art); re-evaluation MUST NOT re-trigger enrichment or host links.

### Functional — Multi-host catalog

- **FR-004**: A game MUST be a single catalog entry independent of how many hosts have it installed. Per-host presence (installed / uninstalled, first/last seen, grace timestamps) MUST be tracked as a per-host installation linked to the game; the game carries the shared catalog data (title, platform, enrichment, deterministic metadata, cover and banner artwork).
- **FR-005**: During reconciliation of a host's snapshot, a submitted game matching an existing visible-or-retained game MUST be adopted: Steam games match on platform `Steam` + Steam appid; ROM games match on platform + normalized title. Adoption MUST only add or refresh that host's installation and MUST NOT create a second entry, reset or re-run enrichment or metadata, or reset artwork (no full refresh).
- **FR-006**: Snapshot authority remains per-host: a game absent from a host's successful snapshot is uninstalled on that host only; the game is visible iff at least one of its installations is installed on an enabled source. The 30-day grace-then-purge applies per installation; a game whose installations are all purged is deleted together with its locally stored artwork. Disabled-source installations are hidden but retained indefinitely, exactly as today — per host link.
- **FR-007**: The scan payload hash / digest / shrink-guard semantics per source MUST be unchanged; the shrink guard counts that source's installations.

### Functional — Host filter

- **FR-008**: The grid query MUST support a `host` filter (exact hostname) alongside `search`, `platform`, and pagination, with AND semantics; results MUST be games with an installed installation on an enabled source of that hostname.
- **FR-009**: A hosts endpoint MUST return the distinct hostnames of visible games (sorted) to drive the host filter chips.

### Functional — Deterministic metadata

- **FR-010**: The game record MUST carry structured, queryable deterministic fields: genres, developer, publisher, release year — each nullable, bounded, sanitized, and omitted (never fabricated) when unknown.
- **FR-011**: For Steam games, the deterministic fields MUST be sourced from Steam's public store appdetails endpoint (keyless), fetched once per game after discovery with a bounded retry policy symmetrical to enrichment; the fetch result MUST be validated and length-bounded before persistence. For ROM games, the fields MUST be produced by the existing AI enrichment call, validated with the same bounds as all structured facts.
- **FR-012**: The detail response MUST include the new fields plus the game's installed hosts; the detail page MUST render them in the spec sheet, omitting absent fields.

### Functional — Chat attachment

- **FR-013**: The chat request MUST accept an optional bounded list of attached game ids (validated: visible games only, max 5; otherwise rejected with an explicit error).
- **FR-014**: When games are attached, the composition context MUST include their full catalog rows (title, platform, hosts, multiplayer facts, deterministic fields, description) in addition to the grounded filter rows; the translation call still runs so catalog-wide parts of the question keep working; attached games MUST be included in the cited references; when no rows match but attachments exist, the answer MUST compose from the attachments instead of returning no-match.
- **FR-015**: The detail page's "Ask the catalog" button MUST show the spark icon (the same four-pointed star used for the AI briefing nav item) and navigate to chat with the game attached; the chat UI MUST show the attached game as a removable chip and MUST include it in every ask while attached.
- **FR-016**: Chat answers MUST remain grounded exclusively in catalog data; the expanded deterministic fields (genres, developer, release year range, hosts) MUST be usable by the grounded filter, and the unavailability behavior (explicit state, no general-knowledge fallback) is unchanged.

### Key Entities

- **Game**: One catalog game independent of hosts. Attributes: title, platform, Steam appid (Steam games — deterministic identity + artwork/metadata key), enrichment fields (existing multiplayer facts, prose), deterministic metadata (genres, developer, publisher, release year) + metadata status, cover artwork slot (status + ref), banner artwork slot (status + ref), and its host installations.
- **GameInstallation** (new): A game installed via one scan source. Attributes: game, source, per-source external ref (Steam appid or ROM file path), presence, first/last seen, uninstalled-at (grace clock). Identity: (source, external ref).
- **ScanSource**: Unchanged from specs/003 (hostname + machine id + library type, enabled, sync metadata).
- **Host** (derived, not stored): the hostname of a source; hosts of a game = hostnames of its installed installations' sources.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of Steam games that have published Steam portrait library art render portrait-fitted cards; zero cards render a wide header image cropped into the portrait frame.
- **SC-002**: With the same game synced from two hosts: exactly 1 catalog entry, 2 host links, 0 enrichment resets, 0 artwork resets, 0 duplicate cards.
- **SC-003**: Host filter narrows the grid to games installed on that host in combination with search and platform, with response times within the existing grid performance envelope.
- **SC-004**: Deterministic metadata present for ≥ 95% of Steam games (Steam store uptime permitting) within 24 h of discovery; ROM games gain the same fields via enrichment within the existing enrichment SLA.
- **SC-005**: With a game attached, 100% of answers cite the attached game and every game named in the answer exists in the catalog; questions over genres / developer / release year / hosts are answered from structured fields only.

---

## Assumptions

- Single user (the owner); auth, scan flow, sources UI, and scan client protocol are as built in specs/002 + 003 — the scan client needs no protocol change for any story here.
- "Host" means a machine: identified by machine id (falling back to hostname) exactly as sources are today; the user-facing host label is the source hostname.
- Cross-host ROM identity is platform + normalized title (file paths differ per host); rare same-platform title collisions are accepted as one game in v1 — content-hash identity remains out of scope, as in specs/002.
- Steam's public store appdetails endpoint is treated as keyless and stable-but-unofficial: fetched once per game (bounded retries), never polled per view; a failure degrades only that game's missing fields.
- SteamGridDB (the community art library EmuDeck/SRM uses) is deliberately not a v1 dependency: it requires a user-registered API key. Steam CDN library art + libretro box art/snapshots cover both slots keylessly; SteamGridDB remains a future optional provider behind the same slot model.
- Chat conversation history stays session-scoped; attachments live in the current chat session (navigation query + store), not persisted server-side.
- Manual artwork override/upload, SteamGridDB integration, and content-hash ROM identity are out of scope for this feature.

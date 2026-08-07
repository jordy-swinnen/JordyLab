# Feature Specification: Game Catalog

**Feature Branch**: `002-game-catalog`

**Created**: 2026-08-02

**Status**: Draft

**Input**: User description: "Build a Game Catalog module for JordyLab that catalogs games installed on JordyBox (Steam library + EmuDeck ROM library) and makes them browsable and searchable from the JordyLab web app. Users can browse all discovered games as a card grid with platform badges and thumbnails; open a detail view with an AI-generated description covering genre and multiplayer support; ask natural-language questions across the catalog via a chat interface grounded in the actual catalog; and view/manage configured scan sources. Hard constraints: JordyBox must never accept inbound connections — all transfer is outbound-only, initiated by JordyBox on a schedule; every submission must be validated and sanitized server-side; the catalog must reflect reality over time — uninstalled games must not accumulate as stale entries. Out of scope: manual game entry/editing, remote launching, multi-user, save-game sync."

---

## Overview

JordyBox (the HTPC/gaming machine) holds two game libraries: a Steam library and an EmuDeck ROM library spanning multiple emulated systems. Today there is no way to see what is installed without sitting in front of that machine. This feature adds a Game Catalog module to JordyLab: a scheduled agent on JordyBox scans the configured sources and pushes snapshots to the JordyLab server over outbound-only connections; the server validates, reconciles, and enriches the catalog with AI-generated descriptions; the web app presents the catalog as a browsable, searchable card grid with a detail view per game and a natural-language chat interface that answers questions strictly from catalog data.

The defining constraints are trust and truth: JordyBox never listens for inbound connections, an authenticated sender is not a trusted payload (everything is validated and sanitized server-side before any write), and the catalog mirrors reality — a game that disappears from JordyBox disappears from the catalog.

---

## Clarifications

### Session 2026-08-02

- Q: Should multiplayer support facts be stored as structured, queryable data alongside the prose description, or exist only inside the free-text description? → A: Structured facts + prose — genre, max local co-op players, online multiplayer, and single-player flags are stored as queryable fields in addition to the human-readable description.
- Q: How do scan sources come to exist, and who is authoritative for their paths? → A: Agent-authoritative paths — the JordyBox agent reads source paths from a local configuration file and announces exactly those sources to the server; the web UI displays them and toggles enabled state, which the agent pulls at each scheduled check-in.
- Q: Should games of a disabled source follow the same 30-day grace-then-purge lifecycle as uninstalled games? → A: No — disabled is a distinct lifecycle state: games are hidden immediately and retained indefinitely while the source remains disabled (no purge clock); re-enabling restores them instantly with descriptions intact. The 30-day purge applies only to games genuinely uninstalled (missing from a successful snapshot).
- Q: How should ROM display titles be derived from raw filenames? → A: The agent normalizes titles on JordyBox before submission — stripping the file extension and standard region/revision/language tags (e.g., `(USA)`, `(Rev 1)`, `[!]`) — and the server stores and displays what it receives; the raw file location remains the stable identifier.
- Q: How should multi-file games (e.g., multi-disc sets) appear in the catalog? → A: One catalog entry per game — the agent groups multi-disc sets into a single entry (using `.m3u` playlists where EmuDeck provides them, disc-number filename patterns otherwise) with the primary file as identity; one card, one description, one artwork lookup.

## User Scenarios & Testing

### User Story 1 - Automatic catalog synchronization from JordyBox (Priority: P1)

As the owner, I want the catalog to populate and stay current automatically. A scheduled agent on JordyBox scans the configured sources (Steam library, EmuDeck ROM folders) and pushes an authoritative snapshot per source to the JordyLab server via outbound requests only. The server authenticates the sender, validates and sanitizes every entry before writing anything, and reconciles the snapshot against the stored catalog so that new games appear, changed games update, and uninstalled games stop showing. I never have to trigger anything by hand, and JordyBox never opens a port.

**Why this priority**: Without trustworthy, self-maintaining data there is no catalog at all. Every other story depends on this one, and it carries the two hard constraints (outbound-only transfer, server-side validation).

**Independent Test**: With a configured source and a seeded JordyBox library, wait for (or trigger) a sync run and verify via the catalog API/UI that the library appears. Then uninstall a game, let the next sync run, and verify it is gone. Attempt a malformed and an unauthenticated submission and verify nothing is written. Delivers a self-updating, trustworthy catalog dataset.

**Acceptance Scenarios**:

1. **Given** a configured and enabled scan source on JordyBox, **When** a scheduled sync runs, **Then** the server receives the snapshot, validates it, and the contained games become visible in the catalog with no manual action.
2. **Given** a game that was present in the previous snapshot, **When** a new complete snapshot for that source no longer contains it, **Then** the game immediately stops being presented as installed, its record (including the AI description) is retained for a 30-day grace period, and it is purged afterwards; a game rediscovered within the grace period is restored with its retained data.
3. **Given** JordyBox has been offline for several days, **When** no sync runs, **Then** the catalog retains the last known state and no games are falsely removed.
4. **Given** a submission containing malformed, oversized, or script-injected field values, **When** the server processes it, **Then** invalid entries are rejected with explicit errors, nothing invalid is persisted, and valid entries are unaffected.
5. **Given** an unauthenticated or incorrectly authenticated submission, **When** it reaches the server, **Then** it is rejected in full and nothing is written.
6. **Given** the scanner cannot read a source (e.g., library drive unmounted), **When** the sync runs, **Then** the agent reports an explicit scan failure for that source, the failed snapshot triggers no reconciliation, and the source's games remain as last known.
7. **Given** an identical snapshot submitted twice, **When** the server processes the duplicate, **Then** the catalog is unchanged (no duplicates, no spurious updates).

---

### User Story 2 - Browse the catalog as a card grid (Priority: P2)

As a user, I open the Game Catalog page in the JordyLab web app and see all currently installed games as a card grid. Each card shows the game title, a thumbnail, and a platform badge that distinguishes Steam from each specific emulated system (e.g., Steam vs. SNES vs. PlayStation 2 — not a generic "ROM" badge). I can search by title and filter by platform to narrow the grid.

**Why this priority**: This is the primary user-facing value — the answer to "what do I have installed?" at a glance.

**Independent Test**: With a synced catalog, open the catalog view and verify cards render with title, thumbnail, and correct per-platform badges; type a title fragment and verify the grid filters; select a platform filter and verify only that platform's games show. Delivers a browsable, searchable overview.

**Acceptance Scenarios**:

1. **Given** a synced catalog with games from Steam and two emulated systems, **When** the user opens the catalog view, **Then** every installed game appears as a card with title, thumbnail, and a badge naming its specific platform.
2. **Given** a catalog of many games, **When** the user types a partial title into search, **Then** only matching games remain visible.
3. **Given** a catalog of many games, **When** the user filters by one platform, **Then** only games of that platform are shown.
4. **Given** a game with no available thumbnail, **When** the grid renders, **Then** a neutral placeholder is shown instead of a broken image.
5. **Given** the catalog is empty (no sources synced yet), **When** the user opens the catalog view, **Then** an explicit empty state is shown explaining that no games have been discovered yet.

---

### User Story 3 - Game detail with AI-generated description (Priority: P3)

As a user, I open a game's detail view and read an AI-generated description that tells me the genre and the multiplayer support: whether it has local co-op and for how many players, whether it has online multiplayer, or whether it is single-player only. This is exactly the information I need when friends are over and we ask "what can we play together on the couch?"

**Why this priority**: The multiplayer/genre facts are the differentiating value of this catalog over a raw file listing, but the catalog is already useful (P2) before enrichment completes.

**Independent Test**: With a synced catalog, open several detail views and verify each shows a generated description explicitly covering genre and multiplayer mode(s) with player counts where applicable. Delivers enriched per-game insight.

**Acceptance Scenarios**:

1. **Given** a discovered game with no enrichment yet, **When** enrichment runs, **Then** structured multiplayer facts (genre, max local co-op players, online, single-player) are persisted as queryable fields and a prose description covering the same facts is generated and stored.
2. **Given** a generated description, **When** the user reopens the detail view, **Then** the persisted description is shown without regenerating it.
3. **Given** the AI provider is unavailable when enrichment is attempted, **When** the user opens the detail view, **Then** the game data (title, platform) is shown with an explicit "description unavailable" state rather than an error or fabricated text, and generation is retried later.
4. **Given** enrichment is pending or has failed for some games, **When** the user browses the grid, **Then** browsing and searching remain fully functional.

---

### User Story 4 - Natural-language questions across the catalog (Priority: P4)

As a user, I ask questions in a chat interface — "which games support 5-player local co-op?", "what racing games do I have?", "is there something like Mario Kart installed?" — and get answers grounded strictly in the actual catalog. When the catalog cannot answer, the system says so plainly instead of falling back on general knowledge about games I don't own.

**Why this priority**: Highest delight factor, but depends on the catalog (P1), and its best answers depend on enrichment (P3). The catalog is already useful without chat.

**Independent Test**: With a synced and enriched catalog, ask catalog-membership questions and multiplayer-filter questions; verify every game named in an answer exists in the catalog and every matching catalog game is findable. Ask about a game that is not installed and verify the system declines to invent an answer.

**Acceptance Scenarios**:

1. **Given** an enriched catalog, **When** the user asks "which games support 4+ player local co-op?", **Then** the answer names only games present in the catalog whose multiplayer data matches, and no games outside the catalog are mentioned.
2. **Given** a question about a game or genre not represented in the catalog, **When** the chat answers, **Then** it explicitly states the catalog contains no match rather than answering from general knowledge.
3. **Given** a chat answer listing games, **When** the user follows a listed game, **Then** they reach that game's detail view (or an equivalent identification of the catalog entries the answer was based on).
4. **Given** the AI provider is unavailable, **When** the user opens chat, **Then** an explicit unavailable state is shown while the rest of the catalog remains usable.

---

### User Story 5 - View and manage scan sources (Priority: P5)

As the owner, I can see which sources are configured — the path and type of each (Steam library, specific EmuDeck ROM folders), whether it is enabled, and when it was last synced (and whether that sync succeeded). Source paths live in a local configuration file on JordyBox and are announced by the agent; from the web app I enable or disable a source. Disabling a source stops future ingestion from it and removes its games from the visible catalog; re-enabling restores them on the next sync.

**Why this priority**: Needed to operate the feature long-term, but sensible defaults (sources defined in initial configuration) keep the catalog useful before this UI exists.

**Independent Test**: View the sources list and verify each source shows path, type, enabled state, and last-sync outcome. Disable one source, verify its games leave the catalog, re-enable, sync, and verify they return.

**Acceptance Scenarios**:

1. **Given** configured sources, **When** the user opens the sources view, **Then** each source shows its path, type, enabled state, and the time and outcome of its last sync.
2. **Given** an enabled source with games in the catalog, **When** the user disables it, **Then** no further submissions from it are ingested, its games are immediately hidden, and they are retained indefinitely — never purged — while the source remains disabled.
3. **Given** a disabled source, **When** the user re-enables it and the next sync completes, **Then** its games reappear in the catalog.
4. **Given** a new path added to the agent's local configuration file, **When** the next sync runs, **Then** the new source appears in the sources view without any web app action.

---

### Edge Cases

- **Scanner reads an empty directory because the library drive is unmounted**: the agent must report a scan failure, not an empty-but-valid snapshot; the server must never reconcile a source's games away on the basis of a failed scan.
- **JordyBox offline for an extended period**: catalog retains last known state indefinitely; removal only ever follows a successful, complete snapshot.
- **Truncated or partial upload (connection dropped mid-submission)**: rejected as invalid; previous catalog state for that source is retained.
- **Same game installed both on Steam and as a ROM**: appears as two distinct catalog entries with their respective platform badges.
- **ROM file renamed or moved between folders**: treated as an uninstall of the old path (hidden, subject to the 30-day grace period) and a new discovery of the new path (identity is tied to the file location).
- **Multi-disc game with a disc missing**: the entry remains with the files that exist; grouping is performed agent-side per sync run and a later-restored disc rejoins the same entry.
- **Game uninstalled and reinstalled within the grace period**: the entry is restored as installed with its retained AI description; no regeneration occurs.
- **AI provider down for days**: grid, search, and source management remain fully functional; affected games show an explicit "description unavailable" state and chat shows an explicit unavailable state.
- **Very large library (thousands of ROMs) in a single snapshot**: submission and processing complete within bounded payload limits; limits are explicit and rejected overflows are reported, not silently dropped.
- **Replayed old snapshot (stale agent clock or retry of an outdated run)**: server detects out-of-order snapshots and does not regress the catalog to an older state.
- **Thumbnail unavailable for a game**: neutral placeholder is displayed.

---

## Requirements

### Functional — Ingestion & trust

- **FR-001**: All catalog data transfer MUST be initiated by JordyBox via outbound requests on a configurable schedule. The system MUST NOT require, open, or attempt any inbound connection to JordyBox.
- **FR-002**: The server MUST authenticate every submission and MUST reject unauthenticated or incorrectly authenticated submissions in full, writing nothing.
- **FR-003**: The server MUST validate and sanitize every field of every submitted entry before any persistence: type, length, and format checks per field; neutralization of executable markup in any text later rendered in the UI; and bounded payload size and entry-count limits. Authentication MUST NOT be treated as evidence of payload trustworthiness.
- **FR-004**: Invalid entries within an otherwise valid submission MUST be rejected with explicit, countable errors while valid entries are processed; a submission that is itself malformed (unparseable, truncated) MUST be rejected in full.
- **FR-005**: Each successful submission MUST be treated as the authoritative, complete snapshot for its source as of that run. Reconciliation MUST handle: new games (add), unchanged games (no-op), changed games (update), and missing games (absent from a successful snapshot). A missing game MUST immediately stop being presented as installed (hidden from the grid, search, detail, and chat answers), but its record — including the AI-generated description — MUST be retained for a 30-day grace period and then purged. A game rediscovered within the grace period MUST be restored as installed with its retained data, without regenerating the description.
- **FR-006**: Ingestion MUST be idempotent: re-processing an identical snapshot produces no catalog changes. Out-of-order snapshots (older than the last applied snapshot for that source) MUST NOT regress the catalog.
- **FR-007**: A scan failure on JordyBox (source unreadable, unmounted, or path missing) MUST be reported as an explicit failure state for that source and MUST NEVER trigger reconciliation of that source's games.
- **FR-008**: The system MUST record per-source sync metadata: last successful sync time, last attempt time, outcome, and counts (added, updated, removed, rejected).
- **FR-009**: Steam games MUST be identified by their stable Steam application ID. ROM games MUST be identified by source, emulated system, and file location; the agent MUST group multi-file games (multi-disc sets) into a single catalog entry — using `.m3u` playlists where EmuDeck provides them and disc-number filename patterns otherwise — with the primary file location as the entry's identity, so that one game yields one card, one description, and one artwork lookup. For ROMs, the agent MUST derive a clean display title on JordyBox before submission by stripping the file extension and standard region/revision/language tags (e.g., `(USA)`, `(Rev 1)`, `[!]`); the server stores and displays the received title unchanged, while the raw file location remains the identity. For Steam games, the display title is the name from Steam's installation data.

### Functional — Browse & search

- **FR-010**: Users MUST be able to view all currently installed games as a card grid showing title, thumbnail, and a platform badge per card.
- **FR-011**: Platform badges MUST distinguish Steam from each specific emulated system by name (e.g., "Steam", "SNES", "PlayStation 2") — a single generic "emulated" badge is not acceptable.
- **FR-012**: Users MUST be able to search the grid by title (partial match) and filter by platform.
- **FR-013**: Games without an available thumbnail MUST render a neutral placeholder. Artwork sourcing is hybrid: the server first attempts to source artwork itself (derived from the Steam application ID for Steam games; external metadata lookup by title for ROMs); when the server cannot find artwork for a game, JordyBox MAY upload the game's locally scraped artwork (already curated on the machine by EmuDeck's scraper) as a fallback. Uploaded artwork is part of the submission and MUST pass the same validation and sanitization as all other payload data (FR-003), including image format and size limits.
- **FR-014**: An empty catalog MUST render an explicit empty state explaining that no games have been discovered yet.

### Functional — Detail & AI enrichment

- **FR-015**: Users MUST be able to open a detail view per game showing at minimum: title, platform, source it was discovered from, thumbnail, and AI-generated description.
- **FR-016**: AI enrichment MUST produce both (a) structured, queryable multiplayer facts — genre, maximum local co-op player count, online multiplayer support flag, and single-player flag — and (b) a human-readable prose description that explicitly covers genre and multiplayer support: local co-op including player count where applicable, online multiplayer, or single-player only. The structured facts MUST be persisted as discrete catalog fields, not embedded solely in the prose.
- **FR-017**: Descriptions MUST be generated after discovery, persisted, and reused — not regenerated on every view.
- **FR-018**: When the AI provider is unavailable, enrichment MUST fail explicitly (named error, retryable later), the detail view MUST show a clear "description unavailable" state, and no fabricated description may be shown. Browsing, search, and source management MUST remain functional during AI outages.

### Functional — Catalog chat

- **FR-019**: Users MUST be able to ask natural-language questions about the catalog in a chat interface.
- **FR-020**: Chat answers MUST be grounded exclusively in catalog data (including structured multiplayer facts and enriched descriptions). Player-count and multiplayer-mode questions MUST be answered from the structured multiplayer facts, not from prose interpretation. When the catalog cannot answer a question, the system MUST say so explicitly rather than answering from general game knowledge.
- **FR-021**: Chat answers that reference games MUST identify the specific catalog entries they are based on, in a way the user can navigate to or verify.
- **FR-022**: When the AI provider is unavailable, chat MUST show an explicit unavailable state and MUST NOT answer from cached general knowledge.

### Functional — Scan source management

- **FR-023**: Users MUST be able to view all configured scan sources with: path, source type (Steam library or a specific emulated system's ROM folder), enabled state, and last sync time and outcome.
- **FR-024**: Source paths are defined in the agent's local configuration file on JordyBox and announced to the server on sync; they are NOT editable from the web app. Users MUST be able to enable and disable an announced source from the web app. Disabling stops ingestion from that source and immediately hides its games from the catalog; disabled-source games are retained indefinitely (no purge clock) and are restored with their descriptions intact when the source is re-enabled and next syncs. The 30-day grace-then-purge lifecycle (FR-005) applies only to games missing from a successful snapshot, never to games hidden because their source is disabled.
- **FR-025**: Enabled-state changes made in the web app MUST take effect at the agent's next scheduled check-in (the agent pulls its configuration outbound; nothing is pushed to JordyBox). Path changes take effect on the next sync after the agent's local configuration file is edited on JordyBox.

### Key Entities

- **Game**: A single installed game as discovered from one source. Attributes: display title (agent-normalized for ROMs; Steam's name for Steam games), platform (Steam or a specific emulated system), stable identifier (Steam app ID or, for ROMs, the primary file location of a possibly multi-file game), thumbnail reference, structured multiplayer facts (genre, max local co-op players, online multiplayer flag, single-player flag), AI-generated prose description, presence state (installed / uninstalled-in-grace / hidden-by-disabled-source / purged), and first/last seen timestamps.
- **Scan Source**: A configured location on JordyBox that the agent scans. Attributes: path, type (Steam library / emulated system ROM folder), enabled flag, last sync attempt and success timestamps, last outcome, and last-run counts.
- **Sync Report**: The recorded outcome of one submission from JordyBox. Attributes: source, received-at time, outcome, and counts of added / updated / removed / rejected entries.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: A game installed on JordyBox appears in the web catalog within one sync interval (default: 1 hour) with zero manual steps, for 100% of successfully scanned games.
- **SC-002**: A game uninstalled from JordyBox is no longer presented as installed in the catalog within one sync interval of the next successful snapshot, for 100% of removals.
- **SC-003**: 100% of unauthenticated submissions and 100% of malformed submissions are rejected with zero catalog writes; in submissions mixing valid and invalid entries, 100% of invalid entries are rejected and reported while 100% of valid entries are processed.
- **SC-004**: The feature operates end-to-end with JordyBox's firewall denying all inbound connections — every data flow is verifiably initiated outbound from JordyBox.
- **SC-005**: 100% of games named in chat answers can be verified as present in the catalog at answer time; questions about games not in the catalog receive an explicit "no match" response rather than general-knowledge answers in at least 95% of evaluated cases.
- **SC-006**: With a library of 5,000 games, the catalog grid's initial view loads and becomes interactive in under 2 seconds, and title search returns filtered results in under 1 second.
- **SC-007**: AI descriptions are successfully generated for at least 95% of discovered games within 24 hours of first discovery (AI provider uptime permitting).
- **SC-008**: A scan failure on JordyBox (e.g., unmounted library drive) results in zero unintended game removals from the catalog.

---

## Assumptions

- Single user (the owner); multi-user support and per-user permissions are out of scope. Access control to the web app itself is an existing platform concern.
- JordyBox can run a lightweight scheduled agent with outbound HTTPS access to the JordyLab server (directly or via the existing WireGuard tunnel). The agent's submission credential and initial local configuration file (listing source paths) are provisioned once, manually, as part of setup.
- The Steam library is locally detectable on JordyBox (Steam's own installation manifests) and EmuDeck organizes ROMs in per-system folders; the agent reads local state only and needs no inbound connectivity.
- Sync cadence defaults to hourly per source and is configurable.
- The AI provider for this module is selected at plan time through the platform's existing per-module AI routing configuration; this specification is provider-agnostic. Descriptions and chat may use general game knowledge for *enrichment text* (genre/multiplayer facts about a specific catalog entry), but chat *answers about catalog membership* must come from catalog data only.
- Chat conversation history is ephemeral (session-scoped); persisted chat history is not required in this version.
- Manual game entry, manual editing of catalog data, remote game launching, and save-game synchronization are out of scope for this version.
- Game identity for ROMs is tied to file location; content-hash-based identity (surviving renames) is not required in this version.

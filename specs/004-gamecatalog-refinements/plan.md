# Implementation Plan: Game Catalog Refinements

**Branch**: `004-gamecatalog-refinements` | **Date**: 2026-09-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-gamecatalog-refinements/spec.md`

**Amendment (follow-up)**: the scheduled data jobs were removed. Deterministic metadata + AI enrichment are now fetched inline on every applied scan (capped to fit the scan client's 60s timeout), the 30-day purge also runs inline after each applied scan, and `CatalogRefreshService` exposes per-game and bulk manual refresh endpoints. No `@Scheduled` methods remain in `gamecatalog` (the shared `@EnableScheduling` stays — `fna` still uses it).

## Summary

Four refinements to the built Game Catalog, backend-first: (1) **Artwork slots** — the `game` row gets two artwork slots, `cover` (portrait, card-fitted) and `banner` (wide hero), resolved after sync like artwork today: Steam games use Steam CDN's keyless official library art (`library_600x900` for covers, `library_hero` for banners, derived from the appid — what Steam's own library grid uses), ROM games keep libretro `Named_Boxarts` covers and gain `Named_Snaps` banners; a data migration resets stale Steam `header.jpg` covers to PENDING so they re-resolve to portrait art. (2) **Multi-host model** — a new `GameInstallation` join entity moves per-host state (external ref, presence, seen/grace timestamps) off the `game` row; `Game` becomes the host-independent catalog entry (identity, enrichment, metadata, artwork). Reconciliation reconciles installations per source and adopts existing games across hosts (Steam: platform + appid; ROM: platform + normalized title) — a new host adds a link, never a duplicate entry, re-enrichment, or artwork reset. Visibility = any installation installed on an enabled source; purge is per installation, deleting the game when the last installation purges. (3) **Deterministic metadata** — new bounded columns `genres`, `developer`, `publisher`, `release_year`; Steam games get them from Steam's keyless public store appdetails endpoint via an on-scan fetch (symmetrical to enrichment: status, attempts, retry) plus manual refresh, ROM games from the extended enrichment prompt; all fields become chat-filterable (genres substring, developer substring, release-year range, hosts). (4) **Chat attachment** — `POST /chat` gains optional `gameIds` (max 5, visible only): the composition prompt injects the attached games' full rows alongside the grounded filter rows, attached games are always cited, and no-match is suppressed when attachments exist; the detail page's "Ask the catalog" button gets the spark icon and routes to chat with the game attached (query param → attachment chip in the chat UI, sent with every ask until removed).

## Technical Context

**Language/Version**: Java 25 (backend), Angular 21 / Nx 22 / TypeScript 5.x (frontend), Bun

**Primary Dependencies**: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring Data JPA, Flyway, Lombok, Guava (backend — no new dependencies); spartan/ui helm (badge, input, skeleton already installed) (frontend — no new dependencies); scan client (`gamecatalog-scanner/`) unchanged

**Storage**: PostgreSQL 16 — additive + reshaping migration in the `gamecatalog` schema (owned by `jordylab-be` Flyway); no schema owned by the frontend or client

**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers, WireMock (new Steam appdetails client), MockMvc (backend, 80% JaCoCo gate); Vitest + `@ngneat/spectator/vitest` (frontend, 80% gate); scan client untouched (existing pytest suite stays green)

**Target Platform**: Hetzner VPS Compose (backend), browser SPA (host app), no change to JordyBox

**Project Type**: Web application (existing monolith module + domain libs — no new projects)

**Performance Goals**: Grid + host filter stays within the existing SC-006 envelope (< 2 s initial, < 1 s search at 5,000 games — host filter joins through `game_installation` with an indexed hostname path); Steam appdetails fetch is once-per-game, batch-bounded, off the request path

**Constraints**:
- Scan protocol (`/ingest/check`, `/ingest/scan`, `/ingest/client`) and `gamecatalog-scanner` stay wire-compatible — no client change, no forced re-scan of existing sources beyond normal fingerprint flow
- Steam appdetails is unofficial-but-keyless: fetched once per game with bounded retries (like enrichment), never on the request path, degraded-missing (FR-011)
- All external artwork URLs remain HEAD-probed or deterministic-per-appid; no API-key-requiring provider (SteamGridDB rejected — needs a user-registered key)
- All AI calls route through the existing `ResilientAiService`; chat stays two-call grounded with strict JSON validation
- Migrations are append-only in the `gamecatalog` schema; existing rows must survive with correct reshaped state (installations backfilled from `game` rows)

**Scale/Scope**: Single user; 2–3 hosts; ~10 sources; up to ~5,000 games; 1 backend module reshaped; 4 frontend views touched; 0 new projects

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | Pass | Extends the existing module and libs in place; no new projects, no new patterns — two artwork slots reuse one status enum; `GameInstallation` is the one structural change the multi-host story requires (KISS over merging duplicate rows at query time) |
| II. Fail Fast, No Silent Failures | Pass | Metadata status + attempts mirror enrichment's explicit FAILED/RETRY states; adoption never silently resets enrichment/artwork; chat attachment validation rejects invisible ids and > 5 explicitly; banner/cover placeholders are explicit states |
| III. Immutable, Builder-First Design | Pass | `GameInstallation` follows the canonical entity structure (builder, guards in `build()`, events, EqualsVerifier); new DTOs are records; artwork slot mutation goes through named methods (`applyArtwork`, existing pattern) |
| IV. Testing Discipline | Pass | Entity tests + TestBuilders for `GameInstallation`; `@ApplicationModuleTest` extended for adoption/purge; WireMock for the appdetails client; `@WebMvcTest` for the new filter/attachment endpoints; Spectator specs per touched component; no `any()`, captors assigned |
| V. Language & Tooling Currency | Pass | Java 25 (no `var`), Angular 21 signals/zoneless, Spring Boot 4 APIs verified against the current codebase; no deprecated patterns introduced |

No constitution violations — no complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/004-gamecatalog-refinements/
├── plan.md              # This file
├── research.md          # Phase 0: decisions R1–R7
├── data-model.md        # Phase 1: GameInstallation split, artwork slots, metadata columns
├── quickstart.md        # Phase 1: end-to-end validation guide
├── contracts/
│   └── catalog-api.md   # Amends specs/002 catalog-api: host filter, hosts endpoint, detail
│                        #   fields, chat gameIds, two artwork slots
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/
├── GameCatalogProperties.java          # + metadata() record (batchSize, maxAttempts)
├── domain/
│   ├── Game.java                       # host-independent entry: identity, enrichment,
│   │                                   #   metadata fields, cover + banner slots, steamAppId
│   ├── GameInstallation.java           # NEW aggregate: per-host link (game, source, externalRef,
│   │                                   #   presence, seen/grace timestamps)
│   ├── repository/
│   │   └── GameInstallationRepository.java  # NEW (+ adoption lookups on GameRepository)
│   └── (ArtworkStatus, EnrichmentStatus, Presence, ScanSource, SourceType, SyncOutcome — kept)
├── rest/
│   ├── client/
│   │   ├── ArtworkLookupClient.java    # Steam: library_600x900 cover + library_hero banner;
│   │                                   #   libretro: Named_Boxarts cover + Named_Snaps banner
│   │   └── SteamAppDetailsClient.java  # NEW RestClient for store.steampowered.com appdetails
│   └── controller/
│       ├── GameCatalogController.java  # games?host=, /hosts, /chat with gameIds
│       └── model/                      # GameSummary/Detail (+banner, +metadata, +hosts),
│                                       │   HostsResponse, ChatRequest(+gameIds)
└── service/
    ├── ReconciliationService.java      # reconciles installations; adopts games across hosts;
    │                                   #   purge per installation + orphan-game cleanup
    ├── ArtworkService.java            # two slots; PENDING reset migration support
    ├── SteamMetadataService.java      # NEW appdetails fetch, inline on scan + manual refresh
    ├── CatalogRefreshService.java     # NEW per-game + bulk manual refresh orchestration
    ├── EnrichmentService.java         # extended prompt: genres/developer/publisher/releaseYear
    ├── ChatService.java               # attachment context + expanded filter fields
    └── GameQueryService.java          # host filter, hosts endpoint, detail hosts

jordylab-be/src/main/resources/db/migration/
└── V20260927__gamecatalog_multihost_refinements.sql   # game_installation table + backfill from
                                                        # game; game reshaped (metadata columns,
                                                        # cover/banner rename, steam_app_id)

jordylab-fe/libs/gamecatalog/
├── api/src/lib/
│   ├── gamecatalog.models.ts           # GameInstallation-ish types, banner fields, metadata,
│   │                                   #   hosts, ChatRequest attachment
│   ├── gamecatalog-api.service.ts     # getGames(host), getHosts(), chat(question, gameIds)
│   ├── game-library.store.ts          # selectedHost signal + host chips state
│   └── game-chat.store.ts             # attachedGame state (set/clear), sent with every ask
└── ui/src/lib/
    ├── game-grid/game-grid-view.*     # host filter chips row; cover slot only (portrait, unchanged
    │                                   #   ratio); card aspect stays aspect-[2/3]
    ├── game-detail/game-detail-view.* # wide banner slot at top (hero ratio), spec sheet rows for
    │                                   #   genres/developer/publisher/release year/hosts; spark icon
    │                                   #   on "Ask the catalog" → /games/chat?attach={id}
    └── game-chat/game-chat-view.*     # attachment chip above input (removable); cites as today

jordylab-fe/apps/jordylab/src/app/
└── (nav untouched — routes unchanged; spark icon path reused in gamecatalog ui lib)
```

**Structure Decision**: Everything extends the existing three components in place (backend `gamecatalog` module, `libs/gamecatalog/{api,ui}`, no scan-client change) — matching the repo's monolith-first rule and the 002/003 layout. The one new structural element is the `GameInstallation` aggregate inside the fixed `domain/` package, which the multi-host requirement demands; the one new client is `SteamAppDetailsClient` in the fixed `rest/client/` package, which the deterministic-metadata requirement demands. The spark icon lives inline in the gamecatalog ui lib (same SVG path the host app defines for its briefing nav), keeping the domain lib self-contained rather than importing a host-app constant.

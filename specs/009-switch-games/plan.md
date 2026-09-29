# Implementation Plan: Nintendo Switch Games in the Game Catalog (Manual Add)

**Branch**: `009-switch-games` | **Date**: 2026-09-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-switch-games/spec.md`

**Builds on**: `004-gamecatalog-refinements` (multi-host `Game`/`GameInstallation`, artwork slots, metadata), `005-steam-library-sync` (library sources, local-multiplayer facts from IGDB, visibility predicates), `006-settings-module` (admin/guest roles).

## Summary

Add a manual way for the admin to track Nintendo Switch games in the Game Catalog. Because Nintendo has no usable
public API, games are added by the admin through an IGDB-powered search (single add or paste-a-list bulk add) and live
on a virtual "Nintendo Switch" host. Switch games behave like all other games in the grid, detail, filters and chat,
but scans, library syncs and grace-period purges never rename or remove them. Add, edit and remove are admin-only.

This release is limited to the original Nintendo Switch platform; Nintendo Switch 2 is out of scope and can be added
later as a second virtual host.

## Technical Context

**Language/Version**: Java 25 (backend), Angular 21 / Nx 22 / TypeScript 5.x (frontend), Bun

**Primary Dependencies**: Spring Boot 4.0.3, Spring Modulith 2.0.3, Spring Data JPA, Flyway, Lombok (backend — no new
dependencies); spartan/ui helm (frontend — no new dependencies)

**Storage**: PostgreSQL 16 — additive migration in the `gamecatalog` schema

**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, MockMvc (backend, 80% JaCoCo gate); Vitest +
`@ngneat/spectator/vitest` (frontend, 80% gate)

**Target Platform**: Local development (Podman Compose + Spring Boot + Angular dev server); no change to deployment
manifests or the scan client

**Project Type**: Web application (existing monolith module + domain libs — no new projects)

**Performance Goals**: Grid/filter stays within 004/005 envelope (< 2 s initial, < 1 s search at 5,000 games); IGDB
calls are bounded and off the request path where possible; bulk preview for tens of titles is synchronous and fast
enough to review

**Constraints**:
- No Nintendo API, account, login or credential is used or stored
- IGDB unconfigured or unavailable must degrade gracefully: search returns empty and the admin can still add manually
- Admin-only writes are enforced by the existing SecurityConfig catch-all `/api/gamecatalog/**` → `hasRole("admin")`; no
  SecurityConfig change is needed for the new Switch endpoints
- The virtual "Nintendo Switch" host must never be resolvable through scan ingestion — gate by `SourceType` so scans
  cannot accidentally create, modify or purge it
- Migrations are append-only; existing Steam/ROM games and library entries must survive unchanged
- AI description runs once per new game through `ResilientAiService`; personal-field edits never re-trigger it
- The 004 "no API-key-requiring provider" artwork rule is already satisfied: IGDB is a configured dependency since 005
  and `images.igdb.com` image URLs are keyless CDN links
- No hand-seeding of the database for validation — use real scanner runs or Testcontainers fixtures only (repo rule)

**Scale/Scope**: Single admin + guests; tens of Switch games; one backend module extended; two frontend libs extended;
0 new projects; 0 scan-client change

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Clean Code Discipline | Pass | Extends the existing `gamecatalog` module in place; reuses `IgdbClient`, `ReconciliationService` and visibility patterns rather than forking parallel flows; one virtual host concept, one `TitleSource.MANUAL` authority |
| II. Fail Fast, No Silent Failures | Pass | Duplicate add returns explicit "already present"; unconfigured IGDB returns empty search results so the manual path is clear; purge/reconciliation exclusions are explicit guards, not silent skips |
| III. Immutable, Builder-First Design | Pass | New/updated entities (`Game`, `GameLibraryEntry`, `ScanSource`) follow the canonical Lombok `@Builder` structure with `Preconditions` guards in `build()` |
| IV. Testing Discipline | Pass | TestBuilders for touched entities; WireMock for `IgdbClient`; Testcontainers for repository/visibility tests; Spectator specs for frontend components; no `any()`, captors assigned |
| V. Language & Tooling Currency | Pass | Java 25 (no `var`), Angular 21 signals/zoneless, `inject()`, `#field`; Spring Boot 4 + Modulith 2 APIs verified against current codebase |

No constitution violations — no complexity tracking needed.

## Project Structure

### Documentation (this feature)

```text
specs/009-switch-games/
├── plan.md              # This file
├── spec.md              # Phase 0 input (/speckit-specify)
├── research.md          # Phase 0 output (/speckit-plan)
├── data-model.md        # Phase 1 output (/speckit-plan)
├── quickstart.md        # Phase 1 output (/speckit-plan)
├── contracts/
│   ├── switch-api.md    # Switch search/add/edit/delete/bulk endpoints
│   └── catalog-api.md   # Deltas to existing grid/detail/filter/chat contracts
├── checklists/
│   └── requirements.md  # Spec quality checklist (passed)
└── tasks.md             # Phase 2 output (/speckit-tasks)
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/gamecatalog/
├── domain/
│   ├── Game.java                         # + igdbGameId; TitleSource.MANUAL handling
│   ├── TitleSource.java                  # + MANUAL (highest authority)
│   ├── SourceType.java                   # + non-scannable Switch pseudo-host value
│   ├── ScanSource.java                   # virtual "Nintendo Switch" host rows
│   ├── GameLibraryEntry.java             # no schema change; reused for global OWNED membership
│   ├── GameInstallation.java             # + manual flag, format PHYSICAL/DIGITAL (per-host Switch linkage)
│   └── repository/
│       ├── GameRepository.java           # + findByIgdbGameId, visibility query updates
│       ├── GameLibraryEntryRepository.java
│       └── GameInstallationRepository.java
├── rest/client/
│   └── IgdbClient.java                   # + Switch title search, cover/artwork fetch, image URL builder
├── rest/controller/
│   ├── GameCatalogController.java        # + hostFormats in GameDetailResponse
│   ├── SwitchGameController.java         # NEW /api/gamecatalog/switch/* (admin-only by catch-all)
│   └── model/                            # SwitchSearchRequest/Result, SwitchGameRequest, BulkPreviewRequest, GameDetailResponse hostFormats
└── service/
    ├── SwitchGameService.java            # add/search/manual/bulk/link; enrichment orchestration
    └── ReconciliationService.java        # guards to exclude manual entries from purge/rename

jordylab-be/src/main/resources/db/migration/
└── V20260929__gamecatalog_switch_games.sql

jordylab-fe/libs/gamecatalog/
├── api/src/lib/
│   ├── gamecatalog-api.service.ts        # + Switch endpoints
│   ├── switch-game.store.ts              # NEW signal store (search, bulk review, add state)
│   ├── gamecatalog.models.ts             # + SwitchGameFormat, SwitchSearchResult, BulkReviewLine
│   └── mocks/                            # + switch-search/switch-game factories
└── ui/src/lib/
    ├── switch-add-dialog/                # NEW container + view (search cards, manual fallback)
    ├── switch-bulk-dialog/               # NEW container + view (paste + review table)
    ├── game-detail/game-detail-view.*    # + format display, relink, edit/remove (admin-only)
    └── game-grid/game-grid-view.*        # host/platform chips already generic; no structural change
```

**Structure Decision**: Everything extends the existing backend `gamecatalog` module and
`libs/gamecatalog/{api,ui}` in place, matching the monolith-first rule and the 002–006 layout. The only new structural
elements are the virtual Switch host, a new `TitleSource` authority value, and Switch-specific UI dialogs.

## Complexity Tracking

No constitution violations — complexity tracking not required.

## Phase 0 / Phase 1 outputs

- `research.md` — plan input, clarify decisions (Q1–Q5), live IGDB verification, repo findings, and the design decisions
  they drive (pseudo-host, one-game-multi-host, physical/digital only).
- `data-model.md` — `Game` changes, `TitleSource.MANUAL`, `ScanSource` pseudo-host, `GameLibraryEntry` manual/format
  changes, uniqueness rules, lifecycle transitions, and the open host-linkage decision.
- `contracts/switch-api.md` + `contracts/catalog-api.md` — REST surface for Switch endpoints and deltas to existing
  grid/detail/filter/chat contracts.
- `quickstart.md` — real-data end-to-end validation guide (no hand-seeding).

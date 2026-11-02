# Implementation Plan: Stock Wishlist and Briefing Advice in FNA

**Branch**: `016-fna-stock-wishlist` | **Date**: 2026-11-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/016-fna-stock-wishlist/spec.md`

## Summary

Allow the investor to track unowned stocks on a wishlist in FNA and receive commentary on them in the periodic investment briefing. On the backend, `finance.portfolio_position` makes `share_count` nullable with a positive check constraint (`share_count IS NULL OR share_count > 0`) so wishlist items (`share_count IS NULL`) share single-table ticker uniqueness, euro symbol resolution, and 30-minute price refreshes with held positions (`share_count IS NOT NULL`). `FnaController` and `FnaService` expose dedicated `/api/fna/wishlist` endpoints (`GET`, `PUT /{ticker}`, `DELETE /{id}`), reject adding an already-held ticker to the wishlist with `409 Conflict` (`TICKER_ALREADY_HELD`), and automatically promote a wishlisted ticker in place when `PUT /api/fna/portfolio/{ticker}?shares=...` is called with a positive share count. `BriefingGeneratorService` and `briefing-system.st` pass wishlist items as a separate context block and instruct the AI to evaluate the wishlisted tickers plus suggest one additional discovery ticker in `## Watchlist Suggestion`. On the frontend, `PortfolioStore` and `PortfolioManagerViewComponent` add a separate **Wishlist** panel below the held positions panel with a ticker-only add form, per-row remove action, and an inline per-row **Promote** action that prompts for a share count. Design decisions: [research.md](research.md).

## Technical Context

**Language/Version**: Java 25 (backend), TypeScript / Angular 22 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1, Spring Data JPA, Spring AI 2.0.1 (`ResilientAiService`), Flyway, Lombok, Guava `Preconditions`; Angular signals, spartan/ui (`@spartan-ng/ui-button-helm`)

**Storage**: PostgreSQL 16 (`finance` schema): `ALTER TABLE portfolio_position ALTER COLUMN share_count DROP NOT NULL` + `CHECK (share_count IS NULL OR share_count > 0)` via one Flyway migration (`V20261102002__finance_wishlist_nullable_share_count.sql`)

**Testing**: JUnit 5, AssertJ (`assertSoftly`), Mockito (never `any()`), Testcontainers (`FnaRepositoryTest`), MockMvc (`FnaControllerTest`), `EqualsVerifier` (`PortfolioPositionTest`); Vitest + `@ngneat/spectator/vitest` (`portfolio.store.spec.ts`, `portfolio-manager.component.spec.ts`)

**Target Platform**: `jordylab-be` Spring Boot monolith and `jordylab-fe` Angular web/mobile app

**Project Type**: web application (Spring Boot backend + Nx Angular frontend)

**Performance Goals**: Wishlist addition resolves euro price inline using the existing bounded Yahoo Finance lookup (3s connect / 5s read timeout) and renders in under 15 seconds (SC-001)

**Constraints**: Wishlist items must never affect `totalWorth`, `allocation`, or the held-positions count (FR-006, SC-002); advice is strictly informational with no trade order creation (FR-011); 80% minimum test coverage per module/library enforced in CI

**Scale/Scope**: Single-investor portfolio and wishlist (tens of rows); changes confined to the `fna` backend module and `libs/fna/{api,ui}` frontend libraries

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | How the design complies | Status |
|-----------|------------------------|--------|
| I. Clean Code | Single-responsibility additions in `fna`: `PortfolioPosition` owns the `isHeld()` / `isWishlisted()` domain predicates and positive `shareCount` guard; `FnaService` orchestrates portfolio vs wishlist persistence; `BriefingGeneratorService` builds the wishlist prompt block. Full descriptive names (`WishlistItemDto`, `TickerAlreadyHeldException`), early returns, blank line before every `return`. | Pass |
| II. Fail fast, no silent failures | Adding an already-held ticker to the wishlist fails fast with `TickerAlreadyHeldException` → `409 Conflict` (`TICKER_ALREADY_HELD`) and surfaces in the UI banner; non-positive share counts fail fast with `IllegalArgumentException` → `400 Bad Request`. | Pass |
| III. Immutable, builder-first | `WishlistItemDto` is a Java `record`; `PortfolioPosition` keeps Lombok `@Builder` with all `Preconditions.checkArgument` guards in `PortfolioPositionBuilder.build()` and named mutation method `updateShareCount`. | Pass |
| IV. Testing discipline | `PortfolioPositionTest` (`EqualsVerifier` + builder/mutation guards), `PortfolioPositionTestBuilder` + `WishlistItemDtoTestBuilder`, `FnaServiceTest`, `BriefingGeneratorServiceTest`, `FnaControllerTest` (`@Language("JSON")`, explicit Mockito matchers/captors, `assertSoftly`), `FnaRepositoryTest`; frontend Vitest + Spectator tests using `useValue` + `vi.fn()` and real `signal(...)` instances. | Pass |
| V. Language & tooling currency | Java 25 without `var`; Angular 22 with `inject(PortfolioStore)`, `#field` private fields, signals, and zoneless-compatible templates. | Pass |
| Architecture constraints | All backend changes stay within `dev.jordy.jordylab.fna` existing sub-packages (`domain`, `domain/repository`, `rest/controller`, `rest/controller/model`, `service`); all frontend changes stay in `libs/fna/api` and `libs/fna/ui` following Container–Presentation (`PortfolioManagerComponent` / `PortfolioManagerViewComponent`). | Pass |
| Project rules (AGENTS.md) | All AI calls continue to route through `ResilientAiService.call(AiFeature.FNA_BRIEFING, ...)`; Flyway migration starts with `CREATE SCHEMA IF NOT EXISTS finance; SET search_path TO finance;`; no hand-seeded dev DB data. | Pass |

**Post-design re-check (after Phase 1)**: still Pass. No new module, no new sub-package, and no cross-module dependency introduced. No Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/016-fna-stock-wishlist/
├── spec.md
├── plan.md                      # This file (/speckit-plan output)
├── research.md                  # Phase 0 output
├── data-model.md                # Phase 1 output
├── quickstart.md                # Phase 1 output
├── contracts/
│   └── fna-wishlist-api.md      # Phase 1 output
├── checklists/
│   └── requirements.md
└── tasks.md                     # Phase 2 output (/speckit-tasks - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
jordylab-be/
└── src/
    ├── main/java/dev/jordy/jordylab/fna/
    │   ├── domain/
    │   │   ├── PortfolioPosition.java                         # nullable shareCount, >0 check when present, isHeld()/isWishlisted()
    │   │   └── repository/PortfolioPositionRepository.java    # + findAllByShareCountIsNotNullOrderByTickerAsc, findAllByShareCountIsNullOrderByTickerAsc
    │   ├── rest/controller/
    │   │   ├── FnaController.java                             # + GET/PUT/DELETE /api/fna/wishlist
    │   │   ├── FnaExceptionHandler.java                       # + TickerAlreadyHeldException (409), IllegalArgumentException (400)
    │   │   └── model/WishlistItemDto.java                     # new record(id, ticker, lastPrice, lastPriceFetchedAt)
    │   └── service/
    │       ├── FnaService.java                                # getWishlistItems, addWishlistItem, removeWishlistItem, positive shares check, promotion
    │       ├── TickerAlreadyHeldException.java                # new domain/service exception for already-held ticker on wishlist add
    │       └── BriefingGeneratorService.java                  # separate held vs wishlist context + updated user prompt
    ├── main/resources/
    │   ├── db/migration/V20261102002__finance_wishlist_nullable_share_count.sql
    │   └── prompts/fna/briefing-system.st                     # clarify Watchlist Suggestion covers wishlist + 1 discovery ticker
    └── test/java/dev/jordy/jordylab/fna/
        ├── domain/
        │   ├── PortfolioPositionTest.java                     # + wishlist build, non-positive shareCount rejection, promotion
        │   ├── PortfolioPositionTestBuilder.java              # + aWishlistPosition() helper
        │   └── repository/FnaRepositoryTest.java              # + held vs wishlist query filtering
        ├── rest/controller/
        │   ├── FnaControllerTest.java                         # + wishlist endpoints, 409 conflict, 400 invalid position
        │   └── WishlistItemDtoTestBuilder.java                # new TestBuilder for WishlistItemDto
        └── service/
            ├── FnaServiceTest.java                            # + wishlist CRUD, idempotency, already-held rejection, promotion
            └── BriefingGeneratorServiceTest.java              # + wishlist context in prompt (non-empty and empty wishlist)

jordylab-fe/libs/fna/
├── api/src/
│   ├── index.ts                                               # export WishlistItem and aWishlistItemMock
│   └── lib/
│       ├── fna.models.ts                                      # + WishlistItem interface
│       ├── portfolio.store.ts                                 # + wishlist signal, addWishlistItem, removeWishlistItem, 409 error handling
│       ├── portfolio.store.spec.ts                            # + wishlist store specs
│       └── mocks/wishlist-item.model.mock.ts                  # new mock factory aWishlistItemMock
└── ui/src/lib/portfolio-manager/
    ├── portfolio-manager.component.ts                         # bind wishlist, addWishlistItem, deleteWishlistItem
    ├── portfolio-manager.component.html                       # pass wishlist inputs/outputs to view
    ├── portfolio-manager-view.component.ts                    # wishlist inputs/outputs, addWishlist form state, inline promote state
    ├── portfolio-manager-view.component.html                  # Wishlist panel, table, inline promote controls, Add to wishlist form
    └── portfolio-manager.component.spec.ts                    # + Wishlist panel, add, remove, promote, totalWorth/allocation isolation specs
```

**Structure Decision**: Web application using the existing `jordylab-be` (`fna` module) and `jordylab-fe` (`libs/fna/api` and `libs/fna/ui`) structure. No new modules, libraries, or sub-packages are introduced.

## Complexity Tracking

None. No constitution violations to justify.

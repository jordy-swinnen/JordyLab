---

description: "Task list for Stock Wishlist and Briefing Advice in FNA"
---

# Tasks: Stock Wishlist and Briefing Advice in FNA

**Input**: Design documents from `/specs/016-fna-stock-wishlist/`

**Prerequisites**: [plan.md](plan.md) (required), [spec.md](spec.md) (required for user stories), [research.md](research.md), [data-model.md](data-model.md), [contracts/fna-wishlist-api.md](contracts/fna-wishlist-api.md), [quickstart.md](quickstart.md)

**Tests**: Included per the JordyLab Constitution (IV. Testing Discipline) and `jordylab-be/AGENTS.md` / `jordylab-fe/AGENTS.md` (80% per-module/per-library coverage backstop and entity/service/controller/component spec requirements).

**Organization**: Tasks are grouped by user story (`US1`, `US2`, `US3`) to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`, `[US3]`)
- Exact file paths are included in every task description

## Rules that apply to every task

- **Java**: Never use `var`; add a blank line before every `return`; method order: public, package-private, private; Lombok `@Builder` / `@RequiredArgsConstructor`, records for DTOs, `Preconditions.checkArgument` only in `build()` (and named mutation methods).
- **Java Tests**: JUnit 5, AssertJ (`assertSoftly` for multiple assertions), Mockito without `any()` (use explicit values or assigned `ArgumentCaptor`), `@Language("JSON")` on inline JSON, canonical `@UtilityClass` `*TestBuilder` fixtures.
- **Angular**: `inject(Service)` over constructor injection, `#field` over `private`, signals for state, Container–Presentation separation, Vitest + `@ngneat/spectator/vitest` with `useValue` + `vi.fn()` and real `signal(...)` instances.
- **Commits**: Include `Refs: 016` (or `Refs: 016/T###`) trailer in commit messages.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify baseline module structure before applying schema and domain changes

- [X] T001 Verify baseline FNA backend and frontend module structure in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/` and `jordylab-fe/libs/fna/` per [plan.md](plan.md)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Database migration, `PortfolioPosition` domain entity invariants, and repository query methods required by all user stories

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Create Flyway migration `jordylab-be/src/main/resources/db/migration/V20261102002__finance_wishlist_nullable_share_count.sql` (`CREATE SCHEMA IF NOT EXISTS finance; SET search_path TO finance;`, drop `NOT NULL` on `portfolio_position.share_count`, and add `chk_portfolio_position_share_count_positive CHECK (share_count IS NULL OR share_count > 0)`)
- [X] T003 Update `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/PortfolioPosition.java` to allow nullable `shareCount` in `PortfolioPositionBuilder.build()` while guarding `shareCount == null || shareCount.compareTo(BigDecimal.ZERO) > 0`, require strictly positive `newShareCount` in `updateShareCount(BigDecimal)`, and add `isHeld()` and `isWishlisted()` domain methods
- [X] T004 Update `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/repository/PortfolioPositionRepository.java` to add `findAllByShareCountIsNotNullOrderByTickerAsc()` (held positions) and `findAllByShareCountIsNullOrderByTickerAsc()` (wishlist items)
- [X] T005 [P] Update `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/PortfolioPositionTestBuilder.java` with `DEFAULT_WISHLIST_ID`, `DEFAULT_WISHLIST_TICKER` (`"ASML.AS"`), `aDefaultWishlistPosition()`, and `aWishlistPosition()` pre-filled builder (`shareCount(null)`)
- [X] T006 [P] Update `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/PortfolioPositionTest.java` to cover building a wishlist item (`shareCount == null`, `isWishlisted()`, `!isHeld()`), rejecting zero or negative `shareCount` in `build()` and `updateShareCount(...)`, promoting a wishlist item via `updateShareCount(...)`, and `EqualsVerifier`
- [X] T007 Extend `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/repository/FnaRepositoryTest.java` to verify saving a wishlist item (`shareCount == null`) and filtering held positions vs wishlist items via `findAllByShareCountIsNotNullOrderByTickerAsc()` and `findAllByShareCountIsNullOrderByTickerAsc()`

**Checkpoint**: Foundation ready — `finance.portfolio_position` supports both held positions (`share_count > 0`) and wishlist items (`share_count IS NULL`) with full entity and repository test coverage.

---

## Phase 3: User Story 1 - Manage a stock wishlist alongside my portfolio (Priority: P1) 🎯 MVP

**Goal**: Allow the investor to add tickers to a wishlist without a share count, view them in a dedicated Wishlist panel below held positions without affecting total worth or allocation, remove them, and promote a wishlist ticker into a held position by entering a share count.

**Independent Test**: Open FNA Portfolio, add a ticker to the Wishlist panel, verify it renders with its euro price while total worth and allocation remain unchanged, verify adding an already-held ticker shows an error, promote a wishlist item with a positive share count, and remove a wishlist item.

### Tests for User Story 1 ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [X] T008 [P] [US1] Create `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/WishlistItemDtoTestBuilder.java` following the canonical `@UtilityClass` TestBuilder pattern
- [X] T009 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/FnaServiceTest.java` to test `getPortfolioPositions()` (held only), `getWishlistItems()`, `addWishlistItem(String)` (new wishlist item, idempotent re-add, case/whitespace normalisation, `TickerAlreadyHeldException` when already held, blank ticker rejection), `removeWishlistItem(UUID)`, `upsertPosition` promoting an existing wishlist item in place, and `upsertPosition` rejecting non-positive share counts
- [X] T010 [P] [US1] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/FnaControllerTest.java` to test `GET /api/fna/wishlist`, `PUT /api/fna/wishlist/{ticker}` (`200 OK` and `409 Conflict` `TICKER_ALREADY_HELD`), `DELETE /api/fna/wishlist/{id}` (`204 No Content`), and `400 Bad Request` (`INVALID_POSITION`) on invalid input
- [X] T011 [P] [US1] Create `jordylab-fe/libs/fna/api/src/lib/mocks/wishlist-item.model.mock.ts` exporting `aWishlistItemMock` and re-export it from `jordylab-fe/libs/fna/api/src/index.ts`
- [X] T012 [P] [US1] Extend `jordylab-fe/libs/fna/api/src/lib/portfolio.store.spec.ts` to test loading wishlist items from `/api/fna/wishlist`, adding and removing wishlist items, surfacing `'This ticker is already in your portfolio.'` on HTTP `409`, and reloading both `/api/fna/portfolio` and `/api/fna/wishlist` on `upsertPosition`
- [X] T013 [P] [US1] Extend `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.spec.ts` to test rendering the Wishlist panel, adding a wishlist ticker, removing a wishlist item, promoting a wishlist item by entering a share count on its row, and confirming wishlist items do not affect `totalWorth`, `allocation`, or the held-positions count

### Implementation for User Story 1

- [X] T014 [P] [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/WishlistItemDto.java` record (`UUID id`, `String ticker`, `BigDecimal lastPrice`, `Instant lastPriceFetchedAt`)
- [X] T015 [P] [US1] Create `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/TickerAlreadyHeldException.java` runtime exception for attempts to add an already-held portfolio ticker to the wishlist
- [X] T016 [US1] Update `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/FnaService.java` to use `findAllByShareCountIsNotNullOrderByTickerAsc()` in `getPortfolioPositions()`, validate blank tickers and positive `shareCount` in `upsertPosition`, and implement `getWishlistItems()`, `addWishlistItem(String typedTicker)`, and `removeWishlistItem(UUID id)`
- [X] T017 [US1] Update `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaController.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaExceptionHandler.java` to add `GET /api/fna/wishlist`, `PUT /api/fna/wishlist/{ticker}`, `DELETE /api/fna/wishlist/{id}` and map `TickerAlreadyHeldException` (`409 Conflict`, `TICKER_ALREADY_HELD`) and `IllegalArgumentException` (`400 Bad Request`, `INVALID_POSITION`)
- [X] T018 [P] [US1] Add `WishlistItem` interface to `jordylab-fe/libs/fna/api/src/lib/fna.models.ts` and export it in `jordylab-fe/libs/fna/api/src/index.ts`
- [X] T019 [US1] Update `jordylab-fe/libs/fna/api/src/lib/portfolio.store.ts` to add the `#wishlist` / `wishlist` signal, load `/api/fna/wishlist` alongside `/api/fna/portfolio` in `load()`, and implement `addWishlistItem(ticker: string)` (handling `409 Conflict` with a clear message) and `removeWishlistItem(id: string)`
- [X] T020 [US1] Update `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.ts`, `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.html`, `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.ts`, and `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.html` to display the separate Wishlist panel below held positions with its `Add to wishlist` form, `Remove` button, and per-row `Promote` share-count input flow

**Checkpoint**: User Story 1 is complete and independently testable across backend and frontend.

---

## Phase 4: User Story 2 - Receive briefing advice on wishlist stocks (Priority: P1)

**Goal**: Include wishlist stocks as a distinct group in the briefing prompt context and instruct the AI analyst to evaluate the wishlisted stocks plus suggest one additional discovery ticker in `## Watchlist Suggestion`.

**Independent Test**: Generate a briefing with both held positions and wishlist items (and with an empty wishlist) and verify the prompt context and instructions passed to `ResilientAiService`.

### Tests for User Story 2 ⚠️

- [X] T021 [P] [US2] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/BriefingGeneratorServiceTest.java` to test that `generateBriefing()` queries `findAllByShareCountIsNotNullOrderByTickerAsc()` and `findAllByShareCountIsNullOrderByTickerAsc()`, formats wishlist items under `My wishlist (stocks I do not own yet, considering):`, instructs the model to evaluate wishlist items and suggest one additional discovery ticker when non-empty, and uses `"No wishlist stocks configured."` when the wishlist is empty

### Implementation for User Story 2

- [X] T022 [P] [US2] Update `jordylab-be/src/main/resources/prompts/fna/briefing-system.st` to keep the three section headings (`## Portfolio Impact`, `## European Market Summary`, `## Watchlist Suggestion`) and specify that `## Watchlist Suggestion` evaluates the investor's wishlist stocks (when provided) and suggests one additional discovery ticker not in the portfolio or wishlist
- [X] T023 [US2] Update `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/BriefingGeneratorService.java` to query held positions and wishlist items separately, build the `My wishlist` context block (`buildWishlistContext`), and include the wishlist evaluation + extra discovery ticker instruction in `userPrompt`

**Checkpoint**: User Stories 1 and 2 are both complete and independently testable.

---

## Phase 5: User Story 3 - Keep wishlist prices fresh in euro (Priority: P2)

**Goal**: Ensure wishlist items have their euro-quoted Yahoo Finance symbol resolved on creation and their euro prices refreshed on the 30-minute schedule alongside held positions, degrading gracefully when unpriced.

**Independent Test**: Run `StockPriceServiceTest` to verify euro symbol resolution, scheduled refresh across both held positions and wishlist items (`shareCount == null`), and graceful preservation of unpriced wishlist items.

### Tests for User Story 3 ⚠️

- [X] T024 [P] [US3] Extend `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/StockPriceServiceTest.java` to test resolving and refreshing euro prices for wishlist items (`aWishlistPosition()`) in `refresh(...)` and `refreshAllPrices()`, and keeping unpriced wishlist items when Yahoo Finance returns no quote

### Implementation for User Story 3

- [X] T025 [US3] Verify `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/StockPriceService.java` and `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/FnaService.java` resolve and refresh wishlist items on creation (`addWishlistItem`) and during `refreshAllPrices()` without touching `shareCount`

**Checkpoint**: All user stories (`US1`, `US2`, `US3`) are complete and independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Run targeted verification, module boundary checks, and lint gates per [quickstart.md](quickstart.md)

- [X] T026 Run targeted backend tests and Spring Modulith boundary verification in `jordylab-be/` (`./gradlew test --tests "*PortfolioPositionTest" --tests "*FnaRepositoryTest" --tests "*FnaServiceTest" --tests "*StockPriceServiceTest" --tests "*BriefingGeneratorServiceTest" --tests "*FnaControllerTest" --tests "*ModularityTests*"`)
- [X] T027 [P] Run targeted frontend tests and linters in `jordylab-fe/` (`bunx nx test fna-api && bunx nx test fna-ui && bunx nx lint fna-api && bunx nx lint fna-ui && bunx nx oxlint fna-api && bunx nx oxlint fna-ui`)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — starts immediately (`T001`).
- **Foundational (Phase 2)**: Depends on Setup (`T002`–`T007`) — **BLOCKS all user stories**.
- **User Story 1 (Phase 3, P1)**: Depends on Phase 2 (`T008`–`T020`).
- **User Story 2 (Phase 4, P1)**: Depends on Phase 2 (`T021`–`T023`); can run in parallel with User Story 1 once Phase 2 is complete.
- **User Story 3 (Phase 5, P2)**: Depends on Phase 2 and `T016` (`T024`–`T025`).
- **Polish (Phase 6)**: Depends on Phases 3–5 (`T026`–`T027`).

### Parallel Opportunities

- **Phase 2**: `T005` ∥ `T006` after `T003` and `T004`.
- **Phase 3 (US1 Tests)**: `T008` ∥ `T009` ∥ `T010` ∥ `T011` ∥ `T012` ∥ `T013`.
- **Phase 3 (US1 Implementation)**: `T014` ∥ `T015` ∥ `T018` can run in parallel before service/controller/store wiring (`T016`, `T017`, `T019`, `T020`).
- **Phase 4 (US2) & Phase 5 (US3)**: `T021`, `T022`, and `T024` can run in parallel with US1 frontend tasks (`T018`–`T020`).

---

## Parallel Example: User Story 1

```bash
# Write backend and frontend test fixtures and specs in parallel:
Task: "T008 [P] [US1] Create jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/WishlistItemDtoTestBuilder.java"
Task: "T011 [P] [US1] Create jordylab-fe/libs/fna/api/src/lib/mocks/wishlist-item.model.mock.ts"
Task: "T012 [P] [US1] Extend jordylab-fe/libs/fna/api/src/lib/portfolio.store.spec.ts"
Task: "T013 [P] [US1] Extend jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.spec.ts"

# Create DTO, exception, and TS model in parallel:
Task: "T014 [P] [US1] Create jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/WishlistItemDto.java"
Task: "T015 [P] [US1] Create jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/TickerAlreadyHeldException.java"
Task: "T018 [P] [US1] Add WishlistItem interface to jordylab-fe/libs/fna/api/src/lib/fna.models.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational).
2. Complete Phase 3 (US1: Wishlist management, mutual exclusivity, and promote-to-held UI/API).
3. Complete Phase 4 (US2: Briefing wishlist advice + discovery pick).
4. Complete Phase 5 (US3: Price refresh verification) and Phase 6 (Polish & lint/test verification).

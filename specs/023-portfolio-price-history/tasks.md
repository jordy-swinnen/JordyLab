# Tasks: Stock Price History on the FNA Portfolio Page

**Input**: Design documents from `specs/023-portfolio-price-history/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included. The constitution (principle IV) and the entity rule in `jordylab-be/AGENTS.md` require tests for new classes. The spec does not ask for test-first ordering, so test tasks sit before the code they cover, but nothing is gated on them.

**Organization**: Tasks are grouped by user story. US1 (P1) is the MVP. US2 (P2) refines the panel that US1 builds, so it starts after US1.

**Accepted decision**: "maximum" is ten years of daily history (research section 10, spec FR-003).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1 or US2, on user story phases only
- Every task names its file or folder path

## Path Conventions

- Backend: `jordylab-be/src/main/java/dev/jordy/jordylab/fna/`, tests under `jordylab-be/src/test/java/dev/jordy/jordylab/fna/`
- Frontend: `jordylab-fe/libs/fna/api/src/lib/` and `jordylab-fe/libs/fna/ui/src/lib/`
- Browser journeys: `jordylab-fe/apps/jordylab-e2e/src/`

---

## Phase 1: Setup

**Purpose**: Know the baseline before anything changes

- [ ] T001 Record the FNA test baseline before any change: run the fna backend tests (`./gradlew test --tests "dev.jordy.jordylab.fna.*"` in `jordylab-be/`) and the fna frontend suites (`bunx nx test fna-api`, `bunx nx test fna-ui` in `jordylab-fe/`), and note any test that already fails so it is not blamed on this feature

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: Shared pieces every story needs: the table, the entity, the period enum, and the Yahoo client

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 Check for an injectable `java.time.Clock` bean in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/`. If none exists, add `@Bean Clock clock()` returning `Clock.systemUTC()` there, so price-history code can take a Clock (AGENTS.md: prefer an injected Clock)
- [X] T003 Create the Flyway migration `jordylab-be/src/main/resources/db/migration/V20261107001__finance_position_price_history.sql`: `CREATE SCHEMA IF NOT EXISTS finance;` and `SET search_path TO finance;`, then `CREATE TABLE IF NOT EXISTS position_price_history` with `id UUID PRIMARY KEY`, `price_symbol VARCHAR(30) NOT NULL`, `price_date DATE NOT NULL`, `price_in_euro NUMERIC(12,4) NOT NULL CHECK (price_in_euro > 0)`, `created_at TIMESTAMPTZ NOT NULL`, `updated_at TIMESTAMPTZ`, and `CONSTRAINT uq_position_price_history_symbol_date UNIQUE (price_symbol, price_date)`. Start with a comment header like `V20261005001__finance_price_symbol.sql`
- [X] T004 [P] Create the enum `PriceHistoryPeriod` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/PriceHistoryPeriod.java` with `ONE_MONTH`, `THREE_MONTHS`, `ONE_YEAR`, `MAX`, and the named method `windowStartDate(LocalDate today)` returning `today.minusMonths(1)`, `today.minusMonths(3)`, `today.minusYears(1)`, or `null` for `MAX` (data-model.md)
- [X] T005 [P] Create the entity `PositionPriceHistory` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/PositionPriceHistory.java` using the canonical structure in `jordylab-be/AGENTS.md`: `@Entity`, `@Table(schema = "finance", name = "position_price_history")`, `@Getter`, `@Builder`, `@AllArgsConstructor(access = AccessLevel.PRIVATE)`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, extends `BaseEntity<PositionPriceHistory>`. Fields: `UUID id`, `String priceSymbol`, `LocalDate priceDate`, `BigDecimal priceInEuro`. The partial `PositionPriceHistoryBuilder.build()` enforces `Preconditions` for a non-blank symbol, a non-null date and a positive price, and assigns a random UUID when none is given. No mutation methods (data-model.md)
- [X] T006 Create the repository `jordylab-be/src/main/java/dev/jordy/jordylab/fna/domain/repository/PositionPriceHistoryRepository.java` (depends on T005): a native `@Modifying` upsert `INSERT ... ON CONFLICT (price_symbol, price_date) DO UPDATE SET price_in_euro = EXCLUDED.price_in_euro, updated_at = EXCLUDED.updated_at`, taking id, symbol, date, price and one timestamp used for both `created_at` and `updated_at`; plus `findAllByPriceSymbolOrderByPriceDateAsc`, `findAllByPriceSymbolAndPriceDateGreaterThanEqualOrderByPriceDateAsc` and `findFirstByPriceSymbolOrderByPriceDateDesc`
- [X] T007 [P] Create the exception `PriceSourceRateLimitedException` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/client/PriceSourceRateLimitedException.java` (a `RuntimeException`), thrown when Yahoo answers HTTP 429
- [X] T008 Create `YahooFinanceChartClient` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/client/YahooFinanceChartClient.java` (depends on T007), following `contracts/yahoo-chart-history.md`. It is `RestClient`-based and exposes `fetchQuote(String symbol)`, returning a `YahooQuote` record (price and currency), and `fetchDailyHistory(String symbol)`, returning a `YahooDailyHistory` record (currency plus a list of `YahooBar` records with a date and a nullable close), all as nested records. It requests `/v8/finance/chart/{symbol}?interval=1d&range={range}` with path encoding and the browser-compatible User-Agent constant moved from `StockPriceService`. It has two request factories: quote (connect 3 s, read 5 s) and history (connect 3 s, read 10 s). A 404 returns `Optional.empty()` with a debug log, HTTP 429 throws `PriceSourceRateLimitedException`, and any other failure returns `Optional.empty()` with a warn log naming the symbol
- [X] T009 Move the quote HTTP call in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/StockPriceService.java` (`fetchQuote`) onto `YahooFinanceChartClient` with range `1d` and the quote factory. Keep the parsing rules, currency handling and log levels unchanged. Update `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/StockPriceServiceTest.java` setup only if it stubs the `RestClient` directly, and keep its assertions (depends on T008)

**Checkpoint**: Foundation ready. Story work can start.

---

## Phase 3: User Story 1 - See how a held stock's price has moved (Priority: P1) 🎯 MVP

**Goal**: From the Portfolio page, the investor opens a held stock's history, sees the euro price over a chosen period as a chart, and reads the change, high and low as text. The history is built from recorded daily prices.

**Independent Test**: With a held stock that has recorded history, open its history on the Portfolio page, switch between the periods, and check that the chart and figures agree with each other and with the period selected. A stock with no recorded history shows the no-history message.

### Tests for User Story 1 (write first, may run in parallel with the code they cover)

- [X] T010 [P] [US1] Create the TestBuilder `PositionPriceHistoryTestBuilder` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/PositionPriceHistoryTestBuilder.java` (`@UtilityClass`; `DEFAULT_ID`, `DEFAULT_PRICE_SYMBOL = "ASML.AS"`, `DEFAULT_PRICE_DATE = 2026-11-06`, `DEFAULT_PRICE_IN_EURO = 1250.0000` as `public static final`; `aDefaultPositionPriceHistory()` and `aPositionPriceHistory()`)
- [X] T011 [US1] Create `PositionPriceHistoryTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/PositionPriceHistoryTest.java` (depends on T010): a happy build asserting every field, a missing or blank symbol, a missing date, a zero and a negative price, each with `IllegalArgumentException`, and `EqualsVerifier` with the three suppressions from AGENTS.md
- [X] T012 [P] [US1] Create `PriceHistorySummaryCalculatorTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/util/PriceHistorySummaryCalculatorTest.java`: no points gives a null summary; one point gives start equal to end with null change fields; start, end, change and percent (two decimals); high and low over the window; the window start is inclusive. Use `assertSoftly`
- [X] T013 [P] [US1] Create `YahooFinanceChartClientTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/client/YahooFinanceChartClientTest.java` with WireMock: parses currency and bar dates; skips `null` closes; a 404 returns empty; a 429 throws `PriceSourceRateLimitedException`; a malformed body returns empty without an exception escaping
- [X] T014 [P] [US1] Create `PriceHistoryServiceTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/PriceHistoryServiceTest.java` (Mockito with explicit values; capture upsert arguments with an assigned `ArgumentCaptor` and assert on them). Cover: today's row uses the Europe/Brussels date from the injected Clock; a USD series converts each day with that day's rate; GBp divides by 100 and then uses GBPEUR; a weekend uses the previous Friday's rate; a missing FX series stores nothing; a wishlist or unknown id throws `PositionNotHeldException`; no rows gives `NO_HISTORY_YET`
- [X] T015 [P] [US1] Extend `StockPriceServiceTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/StockPriceServiceTest.java`: a refresh records today's price; a symbol with no rows is backfilled once; a 429 during the backfill stops the cycle; quote results are unchanged
- [X] T016 [P] [US1] Extend `FnaRepositoryTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/domain/repository/FnaRepositoryTest.java` (Testcontainers): the upsert inserts once and updates on a second call for the same symbol and date, leaving one row; the window query returns ascending rows from the start date; the newest-row query returns the latest date
- [X] T017 [P] [US1] Extend `FnaControllerTest` in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/controller/FnaControllerTest.java` (MockMvc): 200 with `AVAILABLE`, points and summary; 200 with `NO_HISTORY_YET`; 404 with `POSITION_NOT_HELD`; 400 for an unknown period; the JSON field names contain no recommendation field (FR-009)

### Implementation for User Story 1: backend

- [X] T018 [P] [US1] Create the enum `PriceHistoryStatus` (`AVAILABLE`, `NO_HISTORY_YET`) in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryStatus.java`
- [X] T019 [P] [US1] Create the record `PriceHistoryPointDto` (`LocalDate date`, `BigDecimal priceInEuro`) in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryPointDto.java`
- [X] T020 [P] [US1] Create the record `PriceHistorySummaryDto` (`startDate`, `startPriceInEuro`, `endDate`, `endPriceInEuro`, nullable `changeInEuro` and `changePercent`, `highestPriceInEuro`, `lowestPriceInEuro`) in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistorySummaryDto.java`
- [X] T021 [US1] Create the record `PriceHistoryDto` (`positionId`, `ticker`, `period`, `status`, `windowStartDate`, `dataStartDate`, `points`, `summary`, `lastRecordedAt`) in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryDto.java` (depends on T018, T019, T020)
- [X] T022 [P] [US1] Create `PositionNotHeldException` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/PositionNotHeldException.java`
- [X] T023 [US1] Map `PositionNotHeldException` to HTTP 404 with `new ErrorBody("POSITION_NOT_HELD")` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaExceptionHandler.java` (depends on T022)
- [X] T024 [US1] Create the `@UtilityClass` `PriceHistorySummaryCalculator` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/util/PriceHistorySummaryCalculator.java` following research section 5 (depends on T020)
- [X] T025 [US1] Create `PriceHistoryService` in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/PriceHistoryService.java` with `recordTodaysPrice(PortfolioPosition)`, which upserts today's row using the Europe/Brussels date from the injected Clock, and `readWindow(UUID positionId, PriceHistoryPeriod period)`, which throws `PositionNotHeldException` unless the position is held and otherwise builds a `PriceHistoryDto` from the repository and the calculator, with `NO_HISTORY_YET` when there are no rows (depends on T002, T006, T021, T022, T024)
- [X] T026 [US1] Add `backfillIfMissing(PortfolioPosition)` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/PriceHistoryService.java`: when the position's price symbol has no rows, fetch `range=10y` through `YahooFinanceChartClient`, convert each non-null close to euro (EUR as is; GBp ÷ 100 × GBPEUR=X; other `XXX` × `XXXEUR=X`, with rates carried forward across weekends and holidays), and upsert every day. A failed FX series stores nothing and logs a warning. `PriceSourceRateLimitedException` propagates to the caller (depends on T025, T008)
- [X] T027 [US1] Add `repairStaleSymbols()` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/PriceHistoryService.java`: once per Brussels day, for each symbol whose newest row is older than the previous weekday, re-run the ten-year pull and upsert the missing days (depends on T026)
- [X] T028 [US1] Hook recording and backfill into `jordylab-be/src/main/java/dev/jordy/jordylab/fna/service/StockPriceService.java`: after `refresh(position)` updates the cached price, call `recordTodaysPrice`; call `backfillIfMissing` for held positions with a price symbol and stop the cycle on `PriceSourceRateLimitedException`; call `repairStaleSymbols` once per cycle (depends on T009, T025, T026, T027)
- [X] T029 [US1] Add `GET /portfolio/{id}/price-history` to `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/FnaController.java` with `@RequestParam(defaultValue = "ONE_YEAR") PriceHistoryPeriod period`, returning `ResponseEntity<PriceHistoryDto>` from `PriceHistoryService.readWindow` (depends on T021, T025)

### Implementation for User Story 1: frontend

- [X] T030 [P] [US1] Add the `PriceHistoryPeriod`, `PriceHistoryStatus`, `PriceHistoryPoint`, `PriceHistorySummary` and `PriceHistory` types to `jordylab-fe/libs/fna/api/src/lib/fna.models.ts`, mirroring data-model.md in camelCase, with `number` for prices
- [X] T031 [P] [US1] Create `aPriceHistoryMock(overrides: Partial<PriceHistory> = {})` in `jordylab-fe/libs/fna/api/src/lib/mocks/price-history.model.mock.ts`, following the existing mock files in that folder
- [X] T032 [US1] Create `jordylab-fe/libs/fna/api/src/lib/price-history.store.spec.ts` (Vitest with `HttpTestingController`, as in `portfolio.store.spec.ts`): loads `AVAILABLE`; loads `NO_HISTORY_YET`; a 404 sets the error; selecting another position while a request is pending keeps only the latest response; changing the period reloads (depends on T030, T031)
- [X] T033 [US1] Create `PriceHistoryStore` in `jordylab-fe/libs/fna/api/src/lib/price-history.store.ts`: `@Injectable({ providedIn: 'root' })`, `inject(HttpClient)`, private `#history`, `#isLoading` and `#error` signals, public readonly signals, and `load(positionId, period)` that cancels any pending request (depends on T032)
- [X] T034 [US1] Export `PriceHistoryStore` and its types from the barrel `jordylab-fe/libs/fna/api/src/index.ts` (depends on T033)
- [X] T035 [P] [US1] Create the presentational `PriceHistoryChartComponent` in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-chart.component.ts` and `price-history-chart.component.html`: standalone, input `points`, one inline SVG `polyline` scaled to the points, colours from theme token classes and no hex values; fewer than two points renders no line
- [X] T036 [US1] Create `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-chart.component.spec.ts` (Vitest with Spectator): the polyline has one coordinate pair per point; fewer than two points renders no polyline (depends on T035)
- [X] T037 [US1] Create the presentational `PriceHistoryPanelComponent` in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.ts` and `price-history-panel.component.html`: inputs `ticker`, `history`, `isLoading`, `error`; outputs `periodSelected` and `closed`; spartan `hlmBtn` period buttons for one month, three months, one year and maximum; the figures in a `<dl>` (start, end, change in euro and percent, highest, lowest); messages for `NO_HISTORY_YET`, a single point and a later data start; a Close button. It uses the chart component (depends on T035)
- [X] T038 [US1] Create `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.spec.ts` (Vitest with Spectator): a period click emits that period; the `NO_HISTORY_YET` message is shown; the figures render as text; a single point shows no change (depends on T037)
- [X] T039 [US1] Create the barrel `jordylab-fe/libs/fna/ui/src/lib/price-history/index.ts` exporting the panel and the chart (depends on T035, T037)
- [X] T040 [US1] Wire the container `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.ts`: `inject()` the `PriceHistoryStore`, keep `selectedPositionId` as a signal, open the panel for a row (loading `ONE_YEAR`), forward period changes and close (depends on T033, T039)
- [X] T041 [US1] Update `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.ts` and `portfolio-manager-view.component.html`: a **History** button on every held row that is always visible (not `opacity-0` and not hover-only, unlike the Remove button), and the history panel under the positions table bound to the container's state (depends on T040)
- [X] T042 [US1] Extend `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.spec.ts`: opening history loads the store for that position; changing the period reloads it; closing hides the panel (depends on T040)

**Checkpoint**: User Story 1 works on its own. A held stock with recorded history shows its chart and figures for each period, and a stock without history shows the message.

---

## Phase 4: User Story 2 - Use the history on a phone and without a mouse (Priority: P2)

**Goal**: The history fits a 375 px screen, works with the keyboard alone, and gives its figures as text that a screen reader can read.

**Independent Test**: On a 375 px wide viewport and with the keyboard only, open a stock's history, change the period and close it. A screen reader can read the start, end, change, high and low figures.

**Note**: US2 changes the panel and view that US1 builds, so it runs after Phase 3.

- [ ] T043 [US2] Add focus tests to `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager.component.spec.ts`: focus moves to the panel heading when it opens, and returns to the History button of that row on Close (after T042, same file)
- [X] T044 [US2] Implement the focus handling: the panel heading receives focus on open (`afterNextRender`) in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.ts`, and Close returns focus to the row's History button in `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.ts` (depends on T043)
- [ ] T045 [US2] Make the figures accessible: in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-chart.component.html` give the SVG `role="img"` and an `aria-label` built from the start, end and change; in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.html` link the chart to the `<dl>` with `aria-describedby`, and mark the period buttons with `aria-pressed` (depends on T037). Update after T064: the History button disappears while its row is open, so it no longer carries `aria-expanded` or `aria-controls`; the panel heading takes focus on open and Close returns focus to the row's History button
- [ ] T046 [US2] Make the layout phone-safe: the chart is `w-full` with a `viewBox` and no fixed pixel width; the panel uses `min-w-0`; the period buttons wrap; the figures stack below 640 px. Edit `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.html` and `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/portfolio-manager-view.component.html` (depends on T041, T045). Update after T064: the rows live in `positions-table/holdings-table.component.html` and `wishlist-table.component.html`, and the chart is drawn at its measured pixel width (11 px axis values, 1.5 px line) instead of scaling a `viewBox`; the phone layout stays covered by `layout.spec.ts`
- [X] T047 [P] [US2] Add the Portfolio page with the history panel open to the `PAGES` list in `jordylab-fe/apps/jordylab-e2e/src/accessibility.spec.ts` (run at ship, needs a held position in the e2e stack)
- [X] T048 [P] [US2] Add the Portfolio page with the history panel open to the layout check at 360, 390 and 430 px in `jordylab-fe/apps/jordylab-e2e/src/layout.spec.ts`
- [X] T049 [US2] Create the journey `jordylab-fe/apps/jordylab-e2e/src/portfolio-history.spec.ts`: add a position through the UI, open its History, change the period and close it, using the keyboard only. Accept the `NO_HISTORY_YET` state when the e2e stack cannot reach the price source (depends on T044, T045, T046)

**Checkpoint**: User Story 2 works on its own. The history is usable at 375 px, with the keyboard alone, and its figures are text.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Checks across the feature, documentation, and the ship gate

- [X] T050 [P] Run `ModularityTests` (`./gradlew :test --tests "*ModularityTests*"` in `jordylab-be/`) and fix any module boundary violation in the fna package
- [X] T051 [P] Run the fna backend tests (`./gradlew test --tests "dev.jordy.jordylab.fna.*"` in `jordylab-be/`) and fix failures in `jordylab-be/src/test/java/dev/jordy/jordylab/fna/`
- [X] T052 [P] Run the fna frontend suites (`bunx nx test fna-api` and `bunx nx test fna-ui` in `jordylab-fe/`) and fix failures in `jordylab-fe/libs/fna/api/` and `jordylab-fe/libs/fna/ui/`
- [X] T053 Lint each changed TypeScript file with `jordylab-fe/tools/lint-changed.sh <file>` (files in `jordylab-fe/libs/fna/api/src/lib/`, `jordylab-fe/libs/fna/ui/src/lib/price-history/` and `jordylab-fe/libs/fna/ui/src/lib/portfolio-manager/`) and fix every finding (after T052)
- [X] T054 [P] Run `bun tools/check-theme-tokens.mjs` in `jordylab-fe/` and replace any hex colour in `jordylab-fe/libs/fna/ui/src/lib/price-history/` with a theme token
- [X] T055 Review every new user-facing string in `jordylab-fe/libs/fna/ui/src/lib/price-history/price-history-panel.component.html` and the field names in `jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryDto.java` and `PriceHistorySummaryDto.java` for buy, sell or hold wording (SC-005, FR-009), and remove any hit
- [ ] T056 Update `docs/status.md` (the spec 023 row and its end-goal entry) and the architecture Mermaid block in `README.md` (the Yahoo chart client and the history table), in the same change, per AGENTS.md "Keeping the drawing and status current"
- [ ] T057 Validate locally with `specs/023-portfolio-price-history/quickstart.md` sections 1 to 5, using the real refresh and no inserted rows. Record the outcome in the pull request description
- [X] T058 Run the browser journeys and the accessibility gate before shipping: `e2e/run.sh web` in `jordylab-fe/` (quickstart.md section 7). Depends on T047, T048, T049

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: After Setup. Blocks all user stories
- **User Story 1 (Phase 3)**: After Foundational. This is the MVP
- **User Story 2 (Phase 4)**: After US1, because it edits the US1 panel and view
- **Polish (Phase 5)**: After the stories it checks

### Within Each Story

- Write the story's tests first where they are listed first (they may run in parallel with the code they cover)
- Backend order: entity and enum, then repository and DTOs, then calculator, then service (record, backfill, repair), then the refresh hook, then the controller
- Frontend order: models and mocks, then store and its spec, then chart and panel, then the container and view, then the specs

### Parallel Opportunities

- Foundational: T004, T005 and T007 can run in parallel
- US1 tests: T012, T013, T014, T015, T016 and T017 can run in parallel once T010 and T011 exist
- US1 DTOs: T018, T019, T020 and T022 can run in parallel
- US1 frontend: T030 and T031 can run in parallel, and T035 can run alongside the store work (T032, T033)
- Polish: T050, T051, T052 and T054 can run in parallel

---

## Parallel Example: User Story 1

```bash
# Tests for US1 together, after the builder (T010) and the entity test (T011):
Task: "PriceHistorySummaryCalculatorTest in jordylab-be/src/test/java/dev/jordy/jordylab/fna/util/PriceHistorySummaryCalculatorTest.java"
Task: "YahooFinanceChartClientTest in jordylab-be/src/test/java/dev/jordy/jordylab/fna/rest/client/YahooFinanceChartClientTest.java"
Task: "PriceHistoryServiceTest in jordylab-be/src/test/java/dev/jordy/jordylab/fna/service/PriceHistoryServiceTest.java"

# Response records together:
Task: "PriceHistoryStatus in jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryStatus.java"
Task: "PriceHistoryPointDto in jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistoryPointDto.java"
Task: "PriceHistorySummaryDto in jordylab-be/src/main/java/dev/jordy/jordylab/fna/rest/controller/model/PriceHistorySummaryDto.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup (T001)
2. Phase 2: Foundational (T002–T009)
3. Phase 3: User Story 1 (T010–T042)
4. **STOP and VALIDATE**: run quickstart.md sections 1 to 5 for one held stock with recorded history
5. Demo if it passes

### Incremental Delivery

1. Setup and Foundational: foundation ready
2. US1: the chart and figures work (MVP)
3. US2: phone, keyboard and screen-reader support
4. Polish: module checks, lint, docs, and the ship gate (T058)

---

## Follow-up: wishlist history and hover readout (JOR-98)

Added after the first release: the wishlist gets the same history as holdings, and the chart shows the exact price under the pointer. T014, T017, T022, T023 and T025 are kept as written, though they name the old exception and reason code; `PositionNotHeldException` became `PositionNotFoundException` (T059).

- [X] T059 [FU] Drop the held-only rule in `PriceHistoryService` (recording, backfill, gap repair and reads cover every tracked position); repair uses `findAllByOrderByTickerAsc`. Rename the exception to `PositionNotFoundException`, answered as 404 `POSITION_NOT_FOUND`
- [X] T060 [FU] Show a **History** button on every wishlist row and open the panel under the wishlist table; the panel moves to whichever table holds the open position (`portfolio-manager-view.component.ts` and `.html`). Placement superseded by T064: the panel now opens in the row
- [X] T061 [FU] Add the hover readout to `price-history-chart.component.ts` and `.html`: the nearest recorded point, a dashed crosshair, a dot, its date and euro price (two decimals), pinned to the chart edge near either end; cleared on pointer leave (FR-015)
- [X] T062 [FU] Tests: `PriceHistoryServiceTest` (wishlist recorded, backfilled and read; unknown id rejected), `FnaControllerTest` (404 `POSITION_NOT_FOUND`), `price-history-chart.component.spec.ts` (nearest point, values, edge pin, leave), `portfolio-manager.component.spec.ts` (wishlist History opens and loads)
- [X] T064 [FU] Open the history **in the table row** instead of under the table: new `HoldingsTableComponent` and `WishlistTableComponent` in `jordylab-fe/libs/fna/ui/src/lib/positions-table/` (barrel `index.ts`), composed by `portfolio-manager-view.component`. The open row replaces the holding's or item's row (secondary surface, `input` rules, 3 px series-1 accent) with a fade-out of 200 ms and fade-in of 260 ms (`row-motion.css`, off for reduced motion); the chart draws itself and is sized in pixels. Closing returns focus to that row's History button. `aria-expanded` and `aria-controls` are dropped, because the button is gone while its panel is open
- [X] T065 [FU] Tests for T064: `holdings-table.component.spec.ts` and `wishlist-table.component.spec.ts` (rows, open and remove events, the open row replacing the row, period choice, close with focus return, and the promote flow with a missing, zero and valid share count)
- [X] T063 [FU] Check the hover readout by mouse and by touch, and the 375-pixel layout, in a real browser. Checked on production (jordylab.be) on 2026-11-08 after #247 deployed: mouse hover shows the exact date and euro price with a crosshair and dot; at 375 px the page does not scroll sideways, the chart is 301 px wide and the period and Close buttons are 44 px tall and inside the viewport; touch was driven with synthetic touch pointer events (readout follows the point and pins at the right edge, `touch-action: pan-y`), so a real finger on a real phone is still unchecked. T047 and T048 still need the open in-row panel added to the accessibility and layout lists

## Notes

- Java follows AGENTS.md: explicit types, no `var`, a blank line before each `return`, public methods before package-private before private
- Every history row comes from the real refresh and Yahoo, never from a hand-written `INSERT` (AGENTS.md, Validation Data)
- Open decision, not a task yet: the spec edge case "Price source unavailable" will take the wording in research section 8 only once the owner confirms it
- Stop at the US1 checkpoint and validate before starting US2
- [X] T066 [FU] Phone Remove and tidy-ups: the Remove button of a holding is always visible below 640 px (no hover there) and still appears on hover from 640 px up; the shared focus return lives in `positions-table/history-focus.ts`; the row fades move from an inline string to `positions-table/row-motion.css`; T047 and T048 cover the open in-row panel in `accessibility.spec.ts` and `layout.spec.ts`, and the Remove button is checked in `layout.spec.ts`; the table headers and the open panel's labels and Close button use `secondary-foreground` text, because `text-3` falls below 4.5:1 on the `secondary` row in some themes (found by the new open-panel check)

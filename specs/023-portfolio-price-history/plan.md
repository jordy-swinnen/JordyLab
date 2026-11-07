# Implementation Plan: Stock Price History on the FNA Portfolio Page

**Branch**: `023-portfolio-price-history` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/023-portfolio-price-history/spec.md`

## Summary

Show each position's euro price history (holdings and wishlist items) on the Portfolio page: a period choice (one month, three months, one year or maximum), an inline SVG line chart, and the figures as text (change, high, low). The history is recorded in our database. The existing 30-minute price refresh adds today's price, and a one-time pull from the same Yahoo chart endpoint backfills past daily closes. The page reads only recorded history and never calls Yahoo. Non-euro listings are converted at each day's euro rate. There is no new dependency and no recommendation logic.

## Technical Context

**Language/Version**: Java 25 with Spring Boot 4.1.1 (backend); TypeScript on Angular 22 and Nx 23, built with Bun (frontend)

**Primary Dependencies**: Backend: Spring Web `RestClient`, Spring Data JPA, Flyway, Lombok, Jackson, all existing. Frontend: Angular signals, spartan/ui, Spectator on Vitest, all existing. No new dependency.

**Storage**: PostgreSQL, `finance` schema. One new table, `position_price_history`, through a Flyway migration.

**Testing**: Backend: JUnit 5, AssertJ, Mockito, Testcontainers, MockMvc, `ModularityTests`. Frontend: Vitest with `@ngneat/spectator/vitest`. Journeys: Playwright in `apps/jordylab-e2e`, with axe.

**Target Platform**: Linux server (the Compose stack on the VPS, spec 020). Browsers, including the installed PWA on phones at 375 px width.

**Project Type**: Web application: a Spring Boot monolith and an Angular SPA, in the `fna` module.

**Performance Goals**: The default-period chart is visible within 2 seconds for 95% of views (SC-003). Views read recorded rows only. The refresh cycle stays every 30 minutes.

**Constraints**: The price source is the existing unofficial Yahoo Finance chart endpoint, which answers 429 when rate-limited. History is in euro. The feature is informational only (FR-009). The dev database is never hand-seeded (AGENTS.md → Validation Data). No hex colours in templates (`check-theme-tokens`).

**Scale/Scope**: One owner with tens of held positions. History is ten years of daily closes per symbol, about 2,560 rows (measured live for `ASML.AS`). One new table, one new endpoint, one new outbound client, one new panel.

## Constitution Check

*GATE before Phase 0: passed. Re-checked after Phase 1 design: passed.*

| Principle | How this plan complies |
|---|---|
| I. Clean Code Discipline | Small single-purpose classes: recorder, backfill, summary calculator, client. Full names, early returns. |
| II. Fail Fast, No Silent Failures | Yahoo failures are logged at warn with the symbol. A missing history is an explicit `NO_HISTORY_YET`. Nothing is invented. |
| III. Immutable, Builder-First | `PositionPriceHistory` uses `@Builder` with Preconditions only in `build()`. DTOs are records. No `@Data`. |
| IV. Testing Discipline | `assertSoftly`. TestBuilder for the entity. Mockito with explicit values, never `any()`. Angular stores tested with real signals and `vi.fn()` through `useValue`. |
| V. Language & Tooling Currency | Java 25 with explicit types, no `var`. Angular `inject()` and `#fields`. |
| Modulith boundaries | Everything lives in `fna`. The Yahoo client sits in `fna/rest/client`. No cross-module imports. `ModularityTests` runs. |
| Angular architecture | Container–presentation split. History state lives in a signal store in `libs/fna/api`. Barrel imports. Scope tags unchanged. |
| Spec FR-009 (informational only) | The response has no recommendation field. A controller test asserts the field set. |

No violations, so Complexity Tracking stays empty.

## Phase 0 and Phase 1 outputs

- [research.md](research.md): decisions, the price-source check with its evidence, and the alternatives.
- [data-model.md](data-model.md): the new table, the period enum, and the response records.
- [contracts/price-history-api.md](contracts/price-history-api.md): the REST endpoint the page calls.
- [contracts/yahoo-chart-history.md](contracts/yahoo-chart-history.md): the outbound price-source contract.
- [quickstart.md](quickstart.md): how to validate the feature locally, end to end.

## Project Structure

### Documentation (this feature)

```text
specs/023-portfolio-price-history/
├── spec.md
├── plan.md                    # this file
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── price-history-api.md
│   └── yahoo-chart-history.md
├── checklists/requirements.md
└── tasks.md                   # created by /speckit-tasks, not by this command
```

### Source Code (repository root)

```text
jordylab-be/src/main/java/dev/jordy/jordylab/fna/
├── domain/
│   ├── PositionPriceHistory.java                  NEW   entity, finance.position_price_history
│   ├── PriceHistoryPeriod.java                    NEW   enum: ONE_MONTH, THREE_MONTHS, ONE_YEAR, MAX
│   └── repository/PositionPriceHistoryRepository.java   NEW   native upsert + range queries
├── rest/
│   ├── client/YahooFinanceChartClient.java        NEW   daily closes and FX series, same endpoint as quotes
│   └── controller/
│       ├── FnaController.java                     CHANGED   GET /api/fna/portfolio/{id}/price-history
│       ├── FnaExceptionHandler.java               CHANGED   PositionNotFoundException → 404 POSITION_NOT_FOUND
│       └── model/
│           ├── PriceHistoryDto.java               NEW
│           ├── PriceHistoryPointDto.java          NEW
│           └── PriceHistorySummaryDto.java        NEW
├── service/
│   ├── PriceHistoryService.java                   NEW   record today, backfill, read a window
│   ├── PositionNotFoundException.java             NEW
│   └── StockPriceService.java                     CHANGED   refresh also records history; quote call moves to the client
└── util/PriceHistorySummaryCalculator.java        NEW   @UtilityClass, pure window statistics

jordylab-be/src/main/resources/db/migration/
└── V20261107001__finance_position_price_history.sql   NEW

jordylab-be/src/test/java/dev/jordy/jordylab/fna/
├── domain/PositionPriceHistoryTest.java, PositionPriceHistoryTestBuilder.java   NEW
├── domain/repository/FnaRepositoryTest.java      CHANGED   upsert and window queries
├── rest/client/YahooFinanceChartClientTest.java  NEW   WireMock: closes, nulls, FX, 429
├── rest/controller/FnaControllerTest.java        CHANGED   endpoint, 400, 404, field set
├── service/PriceHistoryServiceTest.java          NEW   backfill, daily record, gap repair
├── service/StockPriceServiceTest.java            CHANGED   quote behaviour unchanged after the move
└── util/PriceHistorySummaryCalculatorTest.java   NEW   boundaries: one point, gaps, MAX

jordylab-fe/libs/fna/api/src/lib/
├── fna.models.ts                                 CHANGED   PriceHistory types
├── price-history.store.ts                        NEW   signal store: load(positionId, period)
├── price-history.store.spec.ts                   NEW
└── mocks/price-history.model.mock.ts             NEW   aPriceHistoryMock()

jordylab-fe/libs/fna/ui/src/lib/
├── portfolio-manager/
│   ├── portfolio-manager.component.ts            CHANGED   container: selected position, opens history
│   ├── portfolio-manager-view.component.ts       CHANGED   inputs and outputs for the panel
│   └── portfolio-manager-view.component.html     CHANGED   History button per row (holdings and wishlist), panel under the table the row belongs to
└── price-history/                                NEW
    ├── price-history-panel.component.ts/.html     presentational: period buttons, figures, close
    ├── price-history-chart.component.ts/.html     presentational: inline SVG line chart
    └── *.spec.ts

jordylab-fe/apps/jordylab-e2e/src/                 CHANGED   accessibility PAGES entry; history journey (quickstart step 7)
```

**Structure Decision**: Extend the existing `fna` module on the backend and the existing `fna` libraries on the frontend. The change adds one table, one REST endpoint, one outbound client, one signal store and two presentational components. It adds no module, library, service or dependency.

## Complexity Tracking

None. The Constitution Check has no violations.

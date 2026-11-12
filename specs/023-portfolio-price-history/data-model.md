# Data Model: Stock Price History on the FNA Portfolio Page

**Spec**: [spec.md](spec.md) | **Research**: [research.md](research.md)

## Existing entity (unchanged): PortfolioPosition

`finance.portfolio_position` (`fna/domain/PortfolioPosition.java`). A held position has a positive `shareCount`; a wishlist item has none. This feature reads `priceSymbol` and `lastPrice`, and changes neither.

| Field | Use in this feature |
|---|---|
| `id` (UUID) | Identifies the position in the history endpoint. |
| `ticker` | Shown in the panel heading. |
| `priceSymbol` | The key into `position_price_history`. Null until the first refresh resolves it: no history yet. |
| `lastPrice`, `lastPriceFetchedAt` | Written by the refresh; the history's most recent point mirrors them (FR-007). |

## New entity: PositionPriceHistory

`finance.position_price_history` (`fna/domain/PositionPriceHistory.java`). One row per price symbol per date.

| Field | Type | Rule |
|---|---|---|
| `id` | UUID | Primary key. Random when not given (AGENTS: IDs are always UUID). |
| `priceSymbol` | String, `VARCHAR(30)` | Required. The Yahoo symbol, for example `ASML.AS`. |
| `priceDate` | `LocalDate` (`DATE`) | Required. Trading date (see the date rule in research §4). |
| `priceInEuro` | `BigDecimal`, `NUMERIC(12,4)` | Required, strictly positive. The euro value of that day's close, or the latest recorded price for today. |
| `createdAt`, `updatedAt` | `Instant` | Inherited from `BaseEntity` (`created_at`, `updated_at`). Never declared here. |

**Constraints**
- `UNIQUE (price_symbol, price_date)`. This is the upsert key and the index used by every query.
- `CHECK (price_in_euro > 0)`.

**Creation rule**: the `@Builder` `build()` enforces `Preconditions.checkArgument` for the required fields and the positive price, and assigns the UUID. Nowhere else (AGENTS, DDD entities).

**Writes**: upserts only, through a native `INSERT ... ON CONFLICT (price_symbol, price_date) DO UPDATE` in the repository interface. There is no mutation method on the entity, so no guard lives outside `build()`.

**Lifecycle**: rows are created by the backfill and by each refresh cycle, and updated when the same date is written again. Rows are never deleted by this feature. A position that is removed leaves its rows; they are cheap, and a later position on the same symbol reuses them.

**Equality**: `BaseEntity` supplies `equals`/`hashCode` by `id`. The entity test uses `EqualsVerifier` with the three suppressions from AGENTS.

## Value type: PriceHistoryPeriod

`fna/domain/PriceHistoryPeriod.java`, an enum with no persistence.

| Value | Window start (relative to today in Europe/Brussels) |
|---|---|
| `ONE_MONTH` | today minus one calendar month |
| `THREE_MONTHS` | today minus three calendar months |
| `ONE_YEAR` | today minus one calendar year (the default) |
| `MAX` | none: every recorded daily value, at most ten years back |

Named method: `windowStartDate(LocalDate today)`, returning `null` for `MAX`.

## Response records (fna/rest/controller/model)

These are controller-scoped DTOs, so they live in `rest/controller/model/`, as the existing `PortfolioPositionDto` does.

**PriceHistoryDto**

| Field | Type | Notes |
|---|---|---|
| `positionId` | UUID | |
| `ticker` | String | |
| `period` | `PriceHistoryPeriod` | The window that was asked for. |
| `status` | `PriceHistoryStatus` | `AVAILABLE` or `NO_HISTORY_YET`. |
| `windowStartDate` | `LocalDate`, nullable | Null for `MAX`. |
| `dataStartDate` | `LocalDate`, nullable | First returned point's date. |
| `points` | `List<PriceHistoryPointDto>` | Ascending by date. Empty when `NO_HISTORY_YET`. |
| `summary` | `PriceHistorySummaryDto`, nullable | Null when there are no points. |
| `lastRecordedAt` | `Instant`, nullable | The newest `updatedAt` among the position's rows. |

**PriceHistoryPointDto**: `date` (`LocalDate`), `priceInEuro` (`BigDecimal`).

**PriceHistorySummaryDto**

| Field | Type | Notes |
|---|---|---|
| `startDate`, `startPriceInEuro` | `LocalDate`, `BigDecimal` | First point of the window. |
| `endDate`, `endPriceInEuro` | `LocalDate`, `BigDecimal` | Last point of the window. |
| `changeInEuro` | `BigDecimal`, nullable | Null with a single point. |
| `changePercent` | `BigDecimal`, nullable | Two decimals. Null with a single point. |
| `highestPriceInEuro`, `lowestPriceInEuro` | `BigDecimal` | Over the window's points. |

**PriceHistoryStatus**: `AVAILABLE`, `NO_HISTORY_YET`.

The response has **no field that expresses a recommendation** (FR-009). A controller test asserts the set of field names.

## Frontend models (libs/fna/api/src/lib/fna.models.ts)

TypeScript mirrors of the records above, with the same names in camelCase. Numbers arrive as JSON numbers; the chart and figures format them for display only.

## Relationships

- `PortfolioPosition.priceSymbol` → `PositionPriceHistory.priceSymbol`. A string key with no foreign key, because many positions and wishlist items can share a symbol and because history must outlive a position.

## State transitions (per symbol)

1. **Unknown**: `priceSymbol` is null or has no rows → `NO_HISTORY_YET`.
2. **Backfilled**: the first `range=10y` pull has written rows → `AVAILABLE` for `MAX` and for every shorter window that contains a row.
3. **Growing**: each refresh cycle writes today's row; the daily gap repair fills missed days.

A position whose `priceSymbol` changes starts a new series under the new symbol. The old series stays in the table.

## Validation rules traced to the spec

| Rule | Where it is enforced |
|---|---|
| FR-003 periods | `PriceHistoryPeriod` (enum); unknown values are a 400 at the controller. |
| FR-004/005 figures from the same points | `PriceHistorySummaryCalculator` is the only place that computes them. |
| FR-006 short history stated | `dataStartDate` vs `windowStartDate`. |
| FR-007 last point equals current price | Daily recording writes the cycle's price (research §4). |
| FR-010 no invented values | Only stored closes are returned; nulls are skipped at ingestion. |
| FR-014 euro only | Ingestion converts before storing; the column is named `price_in_euro`. |

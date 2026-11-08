# Research: Stock Price History on the FNA Portfolio Page

**Spec**: [spec.md](spec.md) | **Date**: 2026-11-07

Every NEEDS CLARIFICATION from the Technical Context is resolved below. The main dependency, whether the existing price source can supply historical euro prices, is answered in section 1. Section 10 records one amendment the check forced on the spec.

## 1. Can the existing price source supply historical euro prices?

**Decision**: Yes. Use the same Yahoo Finance chart endpoint that already prices positions, with `interval=1d` and `range=10y` for the first backfill. The longest range that returns daily bars is ten years; longer ranges come back monthly (section 10).

**Evidence** (live read-only requests from the development machine on 2026-11-07, with the browser-compatible User-Agent the code already sends):

| Request | Result |
|---|---|
| `ASML.AS`, `range=1mo` | currency `EUR`, 24 daily points. The latest close is `null` (today has not closed yet). |
| `ASML.AS`, `range=1y` | currency `EUR`, 256 daily points, from 2025-11-06 to 2026-11-06. Median spacing 1 day. |
| `ASML.AS`, `range=5y` | currency `EUR`, 1,281 daily points, from 2021-11-08. Median spacing 1 day. |
| `ASML.AS`, `range=10y` | currency `EUR`, 2,560 daily points, from 2016-11-07. Median spacing 1 day. |
| `ASML.AS`, `range=max` | currency `EUR`, 341 points from 1998-07-31, but a median spacing of about 31 days. Monthly bars, so not used. |
| `AAPL`, `range=1mo` | currency `USD`, 23 daily points. Non-euro listings need conversion. |
| `USDEUR=X`, `range=1mo` | currency `EUR`, 23 daily points. Exchange-rate pairs have history too. |

The existing `StockPriceService.fetchQuote` already calls `/v8/finance/chart/{symbol}` with `range=1d` and reads only `meta`. The same response carries `timestamp[]` and `indicators.quote[0].close[]`, so history uses the same endpoint with a wider range.

Caveats the design handles:
- The endpoint is unofficial. The app already depends on it for quotes, so this adds no new source.
- Yahoo answers HTTP 429 to some clients. The existing User-Agent avoids it most of the time; a 429 pauses the backfill and retries on the next cycle.
- Close values can be `null` (today, or a missing day). They are skipped, never filled.
- Weekends and exchange holidays have no bar. The chart connects recorded points only (spec edge case "Missing days").
- Only ten years was measured as daily. Ranges between ten years and `max` were not tested, and section 10 explains why they are not used anyway.

**Alternatives considered**:
- *A paid market-data API.* Needs a new key, a cost, and a second source to maintain. Not needed while the current endpoint answers.
- *Fetch history on every page view.* Puts a Yahoo call on each view: 429 exposure, slower than the 2-second target (SC-003), and no record when Yahoo is down.
- *Record prices only from now on.* No history for weeks or months, which defeats the request.

## 2. Euro conversion for historical points

**Decision**: History points are in euro, the unit of the position's current price (FR-014).
- EUR-quoted symbols are used as returned.
- Other currencies: each day's close is converted with that day's close of the `<CCY>EUR=X` pair. A rate carries forward across weekends and holidays.
- Pence (`GBp`): divide by 100, then convert with `GBPEUR=X` (the same rule as the spot conversion in `StockPriceService.toEuro`).

**Rationale**: The investor's question is what each day's price was worth in euro. Applying today's rate to the whole history would move the trend by today's currency move, so the chart would not show what the position was worth then.

**Alternative considered**: apply the current rate to every point. One FX call instead of a series, and simpler. Kept as the fallback if the daily FX series proves unreliable for some currency; the trade-off is a chart that does not match what was paid or held at the time.

**Failure rule**: if the FX series for a currency cannot be fetched, the symbol's points are not stored, and the page shows `NO_HISTORY_YET` (spec edge case "Price in another currency"). Mixed-currency points are never stored.

## 3. Storage: record daily euro closes in the database

**Decision**: A new table `finance.position_price_history` holds one row per Yahoo price symbol per date, with the euro value as `NUMERIC(12,4)` (the same type as `portfolio_position.last_price`). The key is the price symbol, not the position.

**Rationale**:
- FR-008 (same schedule as held prices) is met by the existing 30-minute refresh, which already visits every position.
- SC-003: the page reads recorded rows only, so a view makes no Yahoo call and is not exposed to 429s.
- Rows survive restarts, so a restart does not start a new backfill.
- Positions that share a symbol share rows. A wishlist item promoted to a holding keeps its symbol (see `PortfolioPosition.updatePriceSymbol`), so its history carries over.

**Alternatives considered**:
- *In-memory cache.* Lost on every restart, and every restart would backfill again.
- *HTTP cache in front of Yahoo.* Still depends on Yahoo at view time.

## 4. Backfill, daily recording and gap repair

**Decision**:
- **Backfill.** When a refresh finds a held position with a price symbol and no rows for it, pull `range=10y` once and upsert every day with a non-null close. Pulls are one symbol per cycle when Yahoo has rate-limited the cycle, and the cycle stops at the first 429.
- **Daily recording.** Each refresh cycle upserts today's row with the price that cycle just fetched. The most recent point therefore equals the position's current price (FR-007). The last recorded price of a day stands as that day's value.
- **Gap repair.** Once a day, a symbol whose newest row is older than the previous weekday re-pulls `range=10y` and upserts the missing days. This covers downtime of the app.
- **Date rule.** A recorded row's date is the Europe/Brussels calendar date of the refresh that wrote it. Backfilled rows keep Yahoo's bar dates.

**Rationale**: Reuses the existing `@Scheduled` refresh (no new schedule, FR-008). Backfill runs after the first `refresh()` has resolved the price symbol, in the same cycle.

**Idempotency**: `upsert` on `(price_symbol, price_date)`, so repeated pulls and repeated refreshes never duplicate a day.

## 5. Period windows and summary statistics

**Decision**: Computed on the server from recorded rows.
- Windows: `ONE_MONTH` (today minus one calendar month), `THREE_MONTHS`, `ONE_YEAR` (the default), and `MAX` (every recorded daily value, at most ten years back).
- Points are the rows dated on or after the window start, in ascending date order.
- Start = first point, end = last point. Change in euro = end − start. Change in percent = change ÷ start × 100, rounded to 2 decimals. High and low = max and min of the window's closes.
- One point: the change fields are `null`, not zero (spec edge case "Single recorded price").
- No points: status `NO_HISTORY_YET`.
- `dataStartDate` is the first returned point's date. When it is later than `windowStartDate`, the page says the history starts there (FR-006).

**Rationale**: One place computes the figures, so the chart and the text cannot disagree (FR-005). The calculation is pure and lives in a `@UtilityClass`, which makes its boundary cases easy to test.

## 6. Chart: no new dependency

**Decision**: A hand-built inline SVG line chart, one `polyline` scaled to its window, with the figures beside it as text in a description list. Colours come from theme token utility classes, never a hex value (`bun tools/check-theme-tokens.mjs`).

**Rationale**: The frontend has no chart library, and this is the only chart. A library (chart.js, ngx-charts) adds a dependency and a theming layer for one view, and canvas output does not reach assistive technology (FR-012 needs the figures as text in any case). The constitution's YAGNI principle points the same way.

**Alternatives considered**: a chart library (rejected above).

## 7. Where the history opens

**Decision**: A **History** button on each row, holdings and wishlist alike, visible without hover, replaces that row with a history panel (an in-row panel with a fade, set apart by a secondary surface, input rules and a 3 px accent), on the same page. Choosing another row moves the panel. Focus moves to the panel heading on open and returns to the row's button on close.

**Why the button is always visible**: the existing **Remove** button in `portfolio-manager-view.component.html` is `opacity-0` until hover or focus, and phones have no hover. A history action hidden that way would be unreachable on the phone app (FR-013 and the phone-width success criterion).

**Alternative considered**: a route such as `/fna/portfolio/:id`. Rejected because FR-001 says the investor stays on the Portfolio page.

## 8. Spec alignment: one edge case to confirm

The spec's edge case *"Price source unavailable when the history is requested"* assumed that viewing history calls the price source. With recorded history, the view reads our database, so a Yahoo outage does not fail the view. It only stops new days from being added.

**Proposed wording** (to confirm, then update the spec): *"When the price source is unavailable, the view shows the recorded history and the date of its most recent point. It never shows a price that was not recorded."*

FR-010 holds unchanged under either wording.

## 9. Constraints checked

- **Modularity**: all new backend classes are in `fna`. The Yahoo call moves into `fna/rest/client`, so no other module imports it. `ModularityTests` must pass.
- **MCP connector**: `FnaMcpTools` is not changed. The history is not exposed through the connector in this feature.
- **Briefing**: `BriefingGeneratorService` is not changed.
- **Security**: `/api/fna/**` is already `hasRole("admin")` in `SecurityConfig`, so the new endpoint inherits the gate.

## 10. Amendment: "maximum" means ten years of daily history

The first version of the spec defined "maximum" as all history the price source holds. The check in section 1 showed that Yahoo returns monthly bars for `range=max`, about 31 days apart, while `range=10y` returns daily bars. Storing both in one series would put monthly points beside daily ones, so the chart and the change and high/low figures would mix two granularities.

**Decision**: "maximum" means the daily history recorded for the ticker, at most ten years. The backfill requests `range=10y`. Monthly bars are never requested or stored. This amends FR-003 and one assumption in the spec, recorded there on 2026-11-07.

**Confirm before tasks**: a longer daily history would need another source, or accepting monthly bars for the older years with the granularity shown on the chart.

## Resolved

| Unknown | Resolution |
|---|---|
| Can the price source supply historical euro prices? | Yes, same endpoint, `range=10y` for daily bars (section 1). |
| How are non-euro listings priced in euro historically? | Per-day FX close, carried forward (section 2). |
| Where is history read from? | Recorded rows, not a Yahoo call per view (section 3). |
| How does history stay current? | Existing 30-minute refresh records today's price (section 4). |
| What does "maximum" cover? | Ten years of daily history; monthly bars are excluded (section 10). |

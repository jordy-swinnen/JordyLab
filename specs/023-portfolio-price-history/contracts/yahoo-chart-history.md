# Contract: Outbound Price Source (Yahoo Finance chart endpoint, history)

**Consumer**: `fna/rest/client/YahooFinanceChartClient` (new), used by `PriceHistoryService`.
**Provider**: Yahoo Finance, the unofficial `query1.finance.yahoo.com` chart endpoint that `StockPriceService` already calls for quotes.

## Request

```http
GET {fna.yahoo-finance.base-url}/v8/finance/chart/{symbol}?interval=1d&range=10y
User-Agent: Mozilla/5.0 (compatible; JordyLab/1.0)
```

| Part | Rule |
|---|---|
| `{symbol}` | A Yahoo symbol: a position's `priceSymbol` (for example `ASML.AS`), or an FX pair `<CCY>EUR=X` (for example `USDEUR=X`). Path-encoded, as in the quote call. |
| `interval` | `1d`. |
| `range` | `10y`, from `fetchDailyCloses`. With `interval=1d`, longer ranges return monthly bars (verified), so they are never requested. |
| User-Agent | The same browser-compatible constant the quote call uses. Other agents get HTTP 429. |

## Fields read

| JSON path | Used for |
|---|---|
| `chart.result[0].meta.currency` | The listing currency (`EUR`, `USD`, `GBp`, …). |
| `chart.result[0].timestamp[]` | Epoch seconds, one per bar. Converted to the bar's date. |
| `chart.result[0].indicators.quote[0].close[]` | Daily closes. Same length as `timestamp[]`. A `null` close is skipped. |

Any other field is ignored.

## Verified responses (2026-11-07)

| Request | Currency | Points | Spacing and notes |
|---|---|---|---|
| `ASML.AS`, `range=1mo` | `EUR` | 24 | Daily. The latest close is `null`. |
| `ASML.AS`, `range=1y` | `EUR` | 256 | Daily, from 2025-11-06. |
| `ASML.AS`, `range=5y` | `EUR` | 1,281 | Daily, from 2021-11-08. |
| `ASML.AS`, `range=10y` | `EUR` | 2,560 | Daily, from 2016-11-07. Used for the backfill. |
| `ASML.AS`, `range=max` | `EUR` | 341 | Monthly, about 31 days apart, from 1998-07-31. Not used. |
| `AAPL`, `range=1mo` | `USD` | 23 | Daily. Needs daily FX. |
| `USDEUR=X`, `range=1mo` | `EUR` | 23 | Daily. An FX pair's closes are the rate. |

## Conversion

- Currency `EUR`: close as returned.
- Currency `GBp` (pence): close ÷ 100, then × the `GBPEUR=X` close for the same date.
- Any other currency `XXX`: close × the `XXXEUR=X` close for the same date.
- Date matching: the rate on the bar's date, or the most recent earlier rate when that date has no bar (weekends, holidays).
- If the rate series cannot be fetched, nothing is stored for that symbol, and the failure is logged at warn with the symbol and currency. Nothing is estimated.

## Errors and timeouts

| Outcome | Behaviour |
|---|---|
| 200 with usable closes | Stored (see the data model). |
| 404 | No history for the symbol. Logged at debug. The symbol is retried on the next gap repair. |
| 429, 5xx, timeout, malformed payload | Logged at warn with the symbol. The backfill cycle stops at the first 429 and resumes next cycle. |

Timeouts: the history client uses its own request factory. Connect 3 s, read 10 s, because a ten-year daily response (about 2,560 bars) is larger than a quote. The quote client keeps its 5 s read timeout.

## Limits

- No API key. Yahoo may change or block the endpoint at any time; the page then shows what is already recorded (research section 8).
- At most one backfill per symbol per cycle, and one cycle every 30 minutes (the existing refresh interval).

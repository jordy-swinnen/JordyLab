# Contract: Price History API

**Consumer**: the FNA Portfolio page (`libs/fna/api` `PriceHistoryStore`).
**Owner**: `fna` module, `FnaController`.

## Endpoint

```http
GET /api/fna/portfolio/{id}/price-history?period=ONE_YEAR
```

| Part | Rule |
|---|---|
| `{id}` | The held position's UUID (the `id` field of `GET /api/fna/portfolio`). |
| `period` | Optional. One of `ONE_MONTH`, `THREE_MONTHS`, `ONE_YEAR`, `MAX`. Defaults to `ONE_YEAR`. |
| Auth | Admin session. Covered by the existing `/api/fna/**` → `hasRole("admin")` rule. Sends no CSRF token: it is a GET. |

## Responses

### 200 OK, recorded history

```json
{
  "positionId": "7d2c1f0e-3b8a-4c52-9e61-2f4a8b9c0d11",
  "ticker": "ASML",
  "period": "ONE_YEAR",
  "status": "AVAILABLE",
  "windowStartDate": "2025-11-07",
  "dataStartDate": "2025-11-07",
  "points": [
    { "date": "2025-11-07", "priceInEuro": 1012.3000 },
    { "date": "2025-11-10", "priceInEuro": 1019.8000 },
    { "date": "2026-11-06", "priceInEuro": 1250.0000 }
  ],
  "summary": {
    "startDate": "2025-11-07",
    "startPriceInEuro": 1012.3000,
    "endDate": "2026-11-06",
    "endPriceInEuro": 1250.0000,
    "changeInEuro": 237.7000,
    "changePercent": 23.48,
    "highestPriceInEuro": 1300.0000,
    "lowestPriceInEuro": 990.0000
  },
  "lastRecordedAt": "2026-11-07T00:40:00Z"
}
```

The values above are illustrative, not measured. The shape is the contract.

Rules:
- `points` is ascending by `date` and contains no `null` prices.
- `MAX` returns the recorded daily values, at most ten years back. Monthly bars are never stored, so every point is one day's value (its closing price, or the latest recorded price for today).
- `summary.changeInEuro` equals `endPriceInEuro − startPriceInEuro`, and `changePercent` equals that change divided by `startPriceInEuro`, times 100, to two decimals. A test checks this.
- `endPriceInEuro` equals the position's `lastPrice` once the refresh has run (FR-007).
- When `dataStartDate` is later than `windowStartDate`, the history starts there (FR-006).

### 200 OK, nothing recorded yet

```json
{
  "positionId": "7d2c1f0e-3b8a-4c52-9e61-2f4a8b9c0d11",
  "ticker": "ASML",
  "period": "ONE_YEAR",
  "status": "NO_HISTORY_YET",
  "windowStartDate": "2025-11-07",
  "dataStartDate": null,
  "points": [],
  "summary": null,
  "lastRecordedAt": null
}
```

This covers a position whose price symbol is not resolved yet, a symbol Yahoo has no history for, and a currency whose FX series is missing. The page shows the no-history message (FR-010).

### 200 OK, one point

`summary` is present with `startDate` = `endDate`, `startPriceInEuro` = `endPriceInEuro`, and `changeInEuro` and `changePercent` both `null`. The page says a change cannot be calculated yet, and never shows zero (spec edge case).

### 400 Bad Request

An unknown `period` value. Spring's default binding error; no custom body.

### 404 Not Found

```json
{ "code": "POSITION_NOT_HELD" }
```

The id is unknown, or it belongs to a wishlist item (no share count). The page treats this as an error state and does not show a chart.

## Not part of this contract

- No write operations. The history is recorded by the refresh job, not by callers.
- No wishlist history. A wishlist id returns `404 POSITION_NOT_HELD`.
- Not exposed through the MCP connector in this feature.

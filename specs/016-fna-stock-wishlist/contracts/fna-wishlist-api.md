# Contract: FNA Portfolio & Wishlist REST API

**Feature**: [spec.md](../spec.md) · **Data model**: [data-model.md](../data-model.md)

All endpoints under `/api/fna/**` require the `admin` realm role (`SecurityConfig`).

## 1. Held Portfolio Endpoints (existing, refined validation)

### `GET /api/fna/portfolio`

Returns only held positions (`share_count IS NOT NULL`), ordered by `ticker ASC`. Wishlist items are never returned here.

**Response `200 OK`**:
```json
[
  {
    "id": "4f675aad-fa21-4b3b-9555-1b698b4e0c0a",
    "ticker": "KBC",
    "shareCount": 10.0,
    "lastPrice": 68.50,
    "lastPriceFetchedAt": "2026-11-02T08:00:00Z"
  }
]
```

### `PUT /api/fna/portfolio/{ticker}?shares={shares}`

Creates or updates a held position for `{ticker}` (trimmed and uppercased), or **promotes** an existing wishlist item with that ticker into a held position by setting its `shareCount` and retaining/refreshing its euro price.

- **Query parameter**: `shares` (`BigDecimal`, required, must be `> 0`).
- **Response `200 OK`**: `PortfolioPositionDto`
- **Response `400 Bad Request`** (when `shares <= 0` or `ticker` is blank):
```json
{
  "reason": "INVALID_POSITION"
}
```

### `DELETE /api/fna/portfolio/{id}`

Removes a held position by UUID.

- **Response `204 No Content`**

---

## 2. Wishlist Endpoints (new)

### `GET /api/fna/wishlist`

Returns all wishlist items (`share_count IS NULL`), ordered by `ticker ASC`.

**Response `200 OK`** (`List<WishlistItemDto>`):
```json
[
  {
    "id": "8a12b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
    "ticker": "ASML",
    "lastPrice": 642.80,
    "lastPriceFetchedAt": "2026-11-02T08:00:00Z"
  }
]
```

### `PUT /api/fna/wishlist/{ticker}`

Adds `{ticker}` (trimmed and uppercased) to the wishlist and immediately attempts to resolve and fetch its euro price via `StockPriceService.refresh(...)`.

- If `{ticker}` is already on the wishlist (`share_count IS NULL`), refreshes its price and returns the existing wishlist item (`200 OK`, idempotent).
- If `{ticker}` is already held in the portfolio (`share_count IS NOT NULL`), rejects the request with `409 Conflict`.
- If `{ticker}` is blank after trimming, rejects the request with `400 Bad Request`.

**Response `200 OK`** (`WishlistItemDto`):
```json
{
  "id": "8a12b3c4-d5e6-4f7a-8b9c-0d1e2f3a4b5c",
  "ticker": "ASML",
  "lastPrice": 642.80,
  "lastPriceFetchedAt": "2026-11-02T08:00:00Z"
}
```

**Response `409 Conflict`** (when ticker is already held in portfolio):
```json
{
  "reason": "TICKER_ALREADY_HELD"
}
```

**Response `400 Bad Request`** (when ticker is blank):
```json
{
  "reason": "INVALID_POSITION"
}
```

### `DELETE /api/fna/wishlist/{id}`

Removes a wishlist item by UUID.

- **Response `204 No Content`**

---

## 3. Briefing Prompt Contract (internal AI boundary)

When `BriefingGeneratorService.generateBriefing()` calls `ResilientAiService.call(AiFeature.FNA_BRIEFING, systemPrompt, userPrompt)`:

- **System prompt (`briefing-system.st`)**:
  Preserves the three required Markdown sections:
  - `## Portfolio Impact`
  - `## European Market Summary`
  - `## Watchlist Suggestion`
  Instructs the model that under `## Watchlist Suggestion`, when wishlist stocks are listed, it must evaluate how recent news affects those wishlist stocks and whether they look interesting, and also suggest one extra discovery ticker not in the portfolio or wishlist; when no wishlist stocks are listed, it suggests one ticker not currently held.

- **User prompt structure**:
  ```text
  Recent financial news:
  <articleContext>

  My portfolio:
  <portfolioContext>

  My wishlist (stocks I do not own yet, considering):
  <wishlistContext>

  <taskInstruction>
  ```
  where each wishlist item in `<wishlistContext>` is formatted as:
  `<TICKER>: last price €<lastPrice> (as of <fetchedAt>)` (or `"No wishlist stocks configured."` when the wishlist is empty).

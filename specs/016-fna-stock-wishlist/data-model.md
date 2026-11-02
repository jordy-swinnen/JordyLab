# Data Model: Stock Wishlist and Briefing Advice in FNA

**Feature**: [spec.md](spec.md) · **Research**: [research.md](research.md)

## `finance.portfolio_position` (existing aggregate root `PortfolioPosition`)

Both held portfolio positions and wishlist items live in `finance.portfolio_position`, distinguished by whether `share_count` is present (`NOT NULL` and `> 0`) or absent (`NULL`).

| Column | Type | Null | Meaning |
|--------|------|------|---------|
| `id` | `UUID` | no | Primary key (`DEFAULT gen_random_uuid()`). |
| `ticker` | `VARCHAR(20)` | no | Normalised uppercase ticker symbol (`UNIQUE`). Ensures a ticker appears at most once across held positions and the wishlist combined. |
| `share_count` | `NUMERIC(20, 10)` | **yes** (was `NOT NULL`) | Number of shares held. Non-null and `> 0` for a held position; `NULL` for a wishlist item. |
| `price_symbol` | `VARCHAR(30)` | yes | Unchanged. Resolved Yahoo Finance symbol quoting the asset in euro (e.g. `BTC-EUR`, `MEUD.PA`). |
| `last_price` | `NUMERIC(12, 4)` | yes | Unchanged. Latest unit price in euro. |
| `last_price_fetched_at` | `TIMESTAMPTZ` | yes | Unchanged. Timestamp when `last_price` was last updated. |
| `created_at` | `TIMESTAMPTZ` | no | Unchanged (`BaseEntity.createdDate`). |
| `updated_at` | `TIMESTAMPTZ` | no | Unchanged (`BaseEntity.lastModifiedDate`). |

### Database constraints

1. `portfolio_position_ticker_key`: `UNIQUE (ticker)` (existing) — guarantees a ticker is never both held and wishlisted at the same time (FR-003).
2. `chk_portfolio_position_share_count_positive`: `CHECK (share_count IS NULL OR share_count > 0)` (new) — guarantees held positions always have a strictly positive share count (FR-004).

### Migration `V20261102002__finance_wishlist_nullable_share_count.sql`

```sql
CREATE SCHEMA IF NOT EXISTS finance;
SET search_path TO finance;

-- Allow tracking wishlist stocks without owning shares (share_count IS NULL), while requiring held positions to have a positive share count.
ALTER TABLE portfolio_position ALTER COLUMN share_count DROP NOT NULL;
ALTER TABLE portfolio_position ADD CONSTRAINT chk_portfolio_position_share_count_positive
    CHECK (share_count IS NULL OR share_count > 0);
```

### Entity rules (`PortfolioPosition`)

- `PortfolioPositionBuilder.build()`:
  - `Preconditions.checkArgument(StringUtils.hasText(ticker), "ticker is required");`
  - `Preconditions.checkArgument(shareCount == null || shareCount.compareTo(BigDecimal.ZERO) > 0, "shareCount must be positive when present");`
  - Assigns `if (id == null) id = UUID.randomUUID();`
- Domain query methods on `PortfolioPosition`:
  - `public boolean isHeld()` → `return shareCount != null;`
  - `public boolean isWishlisted()` → `return shareCount == null;`
- Mutation method `updateShareCount(BigDecimal newShareCount)`:
  - `Preconditions.checkArgument(newShareCount != null && newShareCount.compareTo(BigDecimal.ZERO) > 0, "shareCount must be positive");`
  - Sets `this.shareCount = newShareCount;` (used both when updating a held position's share count and when promoting a wishlist item to a held position).

### Repository (`PortfolioPositionRepository`)

- `List<PortfolioPosition> findAllByOrderByTickerAsc()` — all tracked tickers (held + wishlist), used by `StockPriceService.refreshAllPrices()`.
- `List<PortfolioPosition> findAllByShareCountIsNotNullOrderByTickerAsc()` — held positions only, used by `FnaService.getPortfolioPositions()` and `BriefingGeneratorService`.
- `List<PortfolioPosition> findAllByShareCountIsNullOrderByTickerAsc()` — wishlist items only, used by `FnaService.getWishlistItems()` and `BriefingGeneratorService`.
- `Optional<PortfolioPosition> findByTicker(String ticker)` — lookup by normalised uppercase ticker.

## State Transitions

```text
                       PUT /api/fna/wishlist/{ticker}
  (not tracked) ──────────────────────────────────────────▶ Wishlist Item (share_count = NULL)
        │                                                         │  │
        │ PUT /api/fna/portfolio/{ticker}?shares=N (N > 0)        │  │ DELETE /api/fna/wishlist/{id}
        │                                                         │  ▼
        │       PUT /api/fna/portfolio/{ticker}?shares=N (N > 0)  │ (removed)
        │       (via Promote action or Add position form)         │
        ▼                                                         │
  Held Position (share_count = N > 0) ◀───────────────────────────┘
        │  │
        │  └── PUT /api/fna/wishlist/{ticker} ──▶ 409 Conflict (TICKER_ALREADY_HELD)
        │
        └── DELETE /api/fna/portfolio/{id} ──▶ (removed)
```

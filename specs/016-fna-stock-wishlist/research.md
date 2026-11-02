# Research: Stock Wishlist and Briefing Advice in FNA

**Feature**: [spec.md](spec.md) · **Date**: 2026-11-02

All findings below are grounded in the existing `fna` module in `jordylab-be` (`domain/PortfolioPosition.java`, `service/FnaService.java`, `service/StockPriceService.java`, `service/BriefingGeneratorService.java`, `rest/controller/FnaController.java`, `src/main/resources/prompts/fna/briefing-system.st`) and `libs/fna/{api,ui}` in `jordylab-fe`.

## R1. Persistence model: nullable `share_count` on `finance.portfolio_position` vs a separate table

- **Decision**: Store wishlist items in `finance.portfolio_position` by making `share_count` nullable (`ALTER TABLE portfolio_position ALTER COLUMN share_count DROP NOT NULL`) and adding a check constraint `chk_portfolio_position_share_count_positive CHECK (share_count IS NULL OR share_count > 0)`. A row with `share_count IS NOT NULL` is a held portfolio position; a row with `share_count IS NULL` is a wishlist item.
- **Rationale**:
  1. **Database-enforced mutual exclusivity (FR-003)**: The existing `ticker VARCHAR(20) NOT NULL UNIQUE` constraint on `finance.portfolio_position` guarantees at the database level that a ticker can never exist as both a held position and a wishlist item simultaneously.
  2. **Zero-copy promotion (FR-003, SC-005)**: When the user promotes a wishlist item to a held position (or adds shares for a wishlisted ticker via `PUT /api/fna/portfolio/{ticker}?shares=...`), `PortfolioPosition.updateShareCount(shareCount)` updates the existing row in place. Its resolved Yahoo symbol (`price_symbol`), cached euro price (`last_price`), and fetch timestamp (`last_price_fetched_at`) are preserved automatically without deleting and re-creating a row or re-probing Yahoo Finance.
  3. **Unified price resolution and refresh (FR-008)**: `StockPriceService.refresh(PortfolioPosition)` and `refreshAllPrices()` already iterate `positionRepository.findAllByOrderByTickerAsc()` and resolve/refresh euro quotes on `PortfolioPosition`. Keeping wishlist items in `PortfolioPosition` gives them immediate euro price resolution on creation and 30-minute background price refreshes with zero duplicate pricing code.
- **Alternatives considered**:
  - Separate `finance.wishlist_item` table: duplicates `id`, `ticker`, `price_symbol`, `last_price`, `last_price_fetched_at`, `created_at`, `updated_at`; requires duplicating `StockPriceService.refresh(...)` or introducing a shared interface across entities; requires a transactional delete-plus-insert on promotion; and cannot enforce cross-table ticker uniqueness with a single unique index.

## R2. API contract: dedicated `/api/fna/wishlist` endpoints alongside `/api/fna/portfolio`

- **Decision**: Keep `/api/fna/portfolio` scoped strictly to **held positions** (`share_count IS NOT NULL`) and expose dedicated wishlist endpoints on `FnaController`:
  - `GET /api/fna/wishlist` → `200 OK` with `List<WishlistItemDto>` (`id`, `ticker`, `lastPrice`, `lastPriceFetchedAt`).
  - `PUT /api/fna/wishlist/{ticker}` → `200 OK` with `WishlistItemDto` when created or already on the wishlist (idempotent); `409 Conflict` with `{"reason": "TICKER_ALREADY_HELD"}` when the normalised ticker is already a held position (`shareCount != null`).
  - `DELETE /api/fna/wishlist/{id}` → `204 No Content`.
  - Promoting a wishlist item uses the existing `PUT /api/fna/portfolio/{ticker}?shares={shares}` endpoint: when `findByTicker(ticker)` finds a wishlist row (`shareCount == null`), `updateShareCount(shares)` transitions it to a held position in place.
- **Rationale**:
  - Preserves backward compatibility for `GET /api/fna/portfolio` and `PortfolioPositionDto` (where `shareCount` is always non-null, so frontend `totalWorth` and `allocation` calculations cannot accidentally include wishlist items — FR-006, SC-002).
  - Separates the wire representation of a wishlist item (`WishlistItemDto`, no `shareCount`) from a held position (`PortfolioPositionDto`, required `shareCount`).
  - Maps the domain conflict (`TickerAlreadyHeldException`) cleanly in `FnaExceptionHandler` using the same `ErrorBody(String reason)` pattern already used for `ArticleAlreadyQueuedException`.
- **Alternatives considered**:
  - Returning both held and wishlisted rows from `GET /api/fna/portfolio` with `shareCount: number | null`: forces every consumer of `positions()` in the frontend to filter by `shareCount !== null` before counting positions or computing allocations, increasing the risk of a regression in total worth or allocation display.

## R3. Positive share count validation on held positions

- **Decision**:
  - `PortfolioPosition.PortfolioPositionBuilder.build()` enforces `Preconditions.checkArgument(shareCount == null || shareCount.compareTo(BigDecimal.ZERO) > 0, "shareCount must be positive when present")`.
  - `PortfolioPosition.updateShareCount(BigDecimal newShareCount)` enforces `Preconditions.checkArgument(newShareCount != null && newShareCount.compareTo(BigDecimal.ZERO) > 0, "shareCount must be positive")`.
  - `FnaService.upsertPosition(String typedTicker, BigDecimal shareCount)` validates that `shareCount` is non-null and strictly positive (`> 0`), throwing `IllegalArgumentException` (mapped to `400 Bad Request` in `FnaExceptionHandler`) if null, zero, or negative.
- **Rationale**: FR-004 requires held portfolio positions to have a strictly positive share count; tracking an unowned stock is done through the wishlist (`shareCount == null`), never via a `0`-share held position.
- **Alternatives considered**: Treating `shares = 0` on `PUT /api/fna/portfolio/{ticker}` as a wishlist addition — rejected because the UI has separate forms and the user explicitly requested a dedicated Wishlist panel and promote flow.

## R4. Briefing prompt and context structure

- **Decision**:
  - `BriefingGeneratorService` queries held positions (`findAllByShareCountIsNotNullOrderByTickerAsc()`) and wishlist items (`findAllByShareCountIsNullOrderByTickerAsc()`) separately.
  - `buildPortfolioContext(List<PortfolioPosition> heldPositions)` formats held positions as today (`<TICKER>: <shareCount> shares, last price €<lastPrice> (as of <fetchedAt>)`, or `"No portfolio positions configured. Provide a general European market briefing."` when empty).
  - `buildWishlistContext(List<PortfolioPosition> wishlistItems)` formats wishlist items as `<TICKER>: last price €<lastPrice> (as of <fetchedAt>)` (using `unknown` when `lastPriceFetchedAt` is null), or `"No wishlist stocks configured."` when empty.
  - The user prompt always includes `My portfolio:` and `My wishlist (stocks I do not own yet, considering):` followed by instructions that require:
    - When wishlist items are present: in `## Watchlist Suggestion`, evaluate how recent news affects each wishlisted stock and whether it looks interesting to consider, and also suggest one additional discovery ticker not in the portfolio or wishlist.
    - When the wishlist is empty: suggest one ticker not currently held that looks interesting based on the recent news.
  - `src/main/resources/prompts/fna/briefing-system.st` keeps the exact three headings (`## Portfolio Impact`, `## European Market Summary`, `## Watchlist Suggestion`) and states that `## Watchlist Suggestion` covers the investor's wishlist stocks (when provided) plus one discovery ticker not already in the portfolio or wishlist.
- **Rationale**: Directly implements the user's clarification (Option B for Q1 in `spec.md`, FR-009, FR-010) while keeping the 3-section Markdown structure expected by the briefing reader UI.
- **Alternatives considered**: Adding a fourth Markdown heading `## Wishlist Impact` — rejected by the user during `/speckit-clarify` in favour of Option B.

## R5. Frontend store and Portfolio page UX

- **Decision**:
  - Extend `PortfolioStore` (`libs/fna/api/src/lib/portfolio.store.ts`) with `#wishlist = signal<WishlistItem[]>([])` and `readonly wishlist = this.#wishlist.asReadonly()`, plus `addWishlistItem(ticker: string)` and `removeWishlistItem(id: string)`.
  - `load()` fetches `/api/fna/portfolio` and `/api/fna/wishlist` independently (so held positions still render even if one request fails, or both update on every mutation).
  - When `addWishlistItem` receives `409 Conflict` (`TICKER_ALREADY_HELD`), `PortfolioStore` sets `error` to `'This ticker is already in your portfolio.'`.
  - In `PortfolioManagerViewComponent` (`libs/fna/ui/src/lib/portfolio-manager/`):
    - Below the held positions panel, render a second `.panel` for **Wishlist** with its own header (`Wishlist` + item count), table (`Ticker`, `Last price`, `Updated`, actions), and footer `Add to wishlist` form (`Wishlist ticker` input + `Add to wishlist` button).
    - Each wishlist row has a `Promote` button and a `Remove` button. Clicking `Promote` reveals an inline share-count input (`Promote shares`) and `Confirm` / `Cancel` controls on that row; confirming with a positive share count emits `addPosition.emit({ ticker: item.ticker, shares })`, which calls `PortfolioStore.upsertPosition(ticker, shares)` and moves the item from the wishlist into held positions.
- **Rationale**: Matches the existing Container–Presentation pattern in `libs/fna/ui` and the custom signal store pattern in `libs/fna/api` without introducing a second store for the same page.
- **Alternatives considered**: A separate `WishlistStore` injectable — unnecessary since wishlist and held positions live on the same Portfolio page and promoting a wishlist item mutates both lists together.

# Quickstart: Validating Stock Wishlist and Briefing Advice in FNA

How to verify the feature end-to-end. Contracts: [fna-wishlist-api.md](contracts/fna-wishlist-api.md). Data model: [data-model.md](data-model.md).

## Prerequisites

- On a Podman host, export Testcontainers variables before running backend tests:
  ```bash
  export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
  export TESTCONTAINERS_RYUK_DISABLED=true
  ```
- Never hand-seed rows into the running dev database; use the UI or API endpoints against the running app.

## 1. Automated Backend Tests (changed scope)

```bash
cd jordylab-be && ./gradlew test \
  --tests "*PortfolioPositionTest" \
  --tests "*FnaRepositoryTest" \
  --tests "*FnaServiceTest" \
  --tests "*StockPriceServiceTest" \
  --tests "*BriefingGeneratorServiceTest" \
  --tests "*FnaControllerTest" \
  --tests "*ModularityTests*"
```

Expected:
- `PortfolioPositionTest` verifies building a wishlist item (`shareCount == null`), building a held position (`shareCount > 0`), rejecting `shareCount <= 0`, promoting a wishlist item via `updateShareCount`, and `EqualsVerifier`.
- `FnaRepositoryTest` verifies the Flyway migration (`V20261102002__finance_wishlist_nullable_share_count.sql`) and queries separating held positions (`shareCount IS NOT NULL`) from wishlist items (`shareCount IS NULL`).
- `FnaServiceTest` and `FnaControllerTest` verify wishlist creation, idempotency, `409 TICKER_ALREADY_HELD` when adding an already-held ticker, and promotion from wishlist to held position via `PUT /api/fna/portfolio/{ticker}?shares=...`.
- `BriefingGeneratorServiceTest` verifies that wishlist items are passed in `My wishlist` context and the prompt asks for wishlist evaluation plus one extra discovery suggestion.

## 2. Automated Frontend Tests & Lint (changed scope)

```bash
cd jordylab-fe && bunx nx test fna-api && bunx nx test fna-ui
```

```bash
cd jordylab-fe && bunx nx lint fna-api && bunx nx lint fna-ui
```

Expected:
- `portfolio.store.spec.ts` covers loading wishlist items, adding/removing wishlist items, handling `409 Conflict` when a ticker is already held, and refreshing both lists on promotion.
- `portfolio-manager.component.spec.ts` covers rendering the Wishlist panel, adding a ticker to the wishlist, promoting a wishlist item with a share count, and verifying that wishlist items do not alter `totalWorth` or `allocation`.

## 3. Local End-to-End Verification

1. Start the backend and frontend (`cd jordylab-be && ./gradlew bootRun` and `cd jordylab-fe && bunx nx serve jordylab`), then sign in as admin and open **FNA → Portfolio**.
2. **Add to wishlist**: In the **Wishlist** panel, type `ASML` and click **Add to wishlist**. Verify `ASML` appears in the Wishlist table with its resolved euro price and timestamp, while **Total worth**, **Allocation**, and the held positions count do not change.
3. **Reject already-held ticker**: Try adding a ticker that is already in the held Positions table to the Wishlist. Verify the error banner states that the ticker is already in your portfolio and no duplicate row appears.
4. **Promote to held position**: Click **Promote** on the `ASML` wishlist row, enter `2` shares, and confirm. Verify `ASML` disappears from the Wishlist table and appears in the held Positions table with `2` shares and its euro value included in **Total worth**.
5. **Briefing advice**: Add another ticker (e.g. `SAP`) to the Wishlist, go to **FNA → Briefing**, and trigger a briefing (when AI credentials are configured). Verify the `Watchlist Suggestion` section evaluates `SAP` and also suggests one extra discovery ticker.

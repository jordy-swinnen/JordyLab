# Feature Specification: Stock Wishlist and Briefing Advice in FNA

**Feature Branch**: `016-fna-stock-wishlist`

**Created**: 2026-11-02

**Status**: Draft

**Module**: `fna` (existing)

**Builds on**: 001 (FNA MVP1 completion, scheduled briefing generation, euro ticker resolution and pricing)

**Reference**: Linear `JOR-8` / GitHub `#158`

**Input**: User description: "Add stocks to a wishlist and get advice on them in FNA — As a user, I can add a stock to a wishlist in FNA, so that I get advice on stocks I don't own yet in the existing briefing."

---

## Clarifications

### Session 2026-11-02

- Q: How should the briefing handle the existing AI-chosen `Watchlist Suggestion` section once you have your own wishlist tickers? → A: Keep the 3 sections (`Portfolio Impact`, `European Market Summary`, `Watchlist Suggestion`); in `Watchlist Suggestion`, evaluate the user's wishlist tickers and still ask the AI to suggest one extra discovery ticker not in the portfolio or wishlist.
- Q: How should wishlist items be displayed and added on the Portfolio page? → A: Separate "Wishlist" panel below the held positions panel, showing `Ticker`, `Last price`, `Updated`, and `Remove`, with its own ticker-only `Add to wishlist` form.
- Q: What should happen when the same ticker is added to the wishlist while already held in the portfolio, or added as a held position while on the wishlist? → A: Mutually exclusive (reject adding an already-held ticker to the wishlist with a clear message; adding a held position for a wishlisted ticker automatically removes it from the wishlist), plus each wishlist row has a promote action where the user enters their share count to move the ticker into held positions.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Manage a stock wishlist alongside my portfolio (Priority: P1)

As the investor using FNA, I can add a stock ticker I am considering to a wishlist without needing a share count, see its current euro price in a dedicated wishlist section on the Portfolio page, and remove it when I am no longer interested, so that I can track prospective investments separately from my owned holdings.

**Why this priority**: Before the briefing can comment on prospective stocks, the investor needs a way to maintain the list of tickers they are considering without distorting their actual portfolio worth or asset allocation.

**Independent Test**: Open the Portfolio page in FNA, add a ticker to the wishlist without entering a share count, verify it appears in the wishlist section with its resolved euro price while total portfolio worth and allocation remain unchanged, and remove it from the wishlist.

**Acceptance Scenarios**:

1. **Given** the investor is on the Portfolio page, **When** they submit a ticker symbol to the wishlist, **Then** the ticker is saved and appears in the wishlist section with its latest euro price and last-updated timestamp, without a share count or holding value.
2. **Given** one or more items are on the wishlist, **When** the Portfolio page displays total worth and asset allocation, **Then** wishlist items are excluded from total worth, allocation percentages, and the held-positions count.
3. **Given** a ticker is already on the wishlist, **When** the investor submits the same ticker again (regardless of letter case or surrounding spaces), **Then** no duplicate wishlist entry is created.
4. **Given** a ticker is already held as a portfolio position, **When** the investor tries to add that same ticker to the wishlist, **Then** the system rejects the addition with a clear message explaining that the ticker is already in the portfolio.
5. **Given** a ticker is on the wishlist, **When** the investor clicks the promote action on that wishlist row, enters a positive share count, and confirms (or adds that same ticker via the held-position form), **Then** the ticker is removed from the wishlist and appears as a held portfolio position with that share count.
6. **Given** a ticker on the wishlist, **When** the investor removes it, **Then** it disappears from the wishlist immediately.

---

### User Story 2 - Receive briefing advice on wishlist stocks (Priority: P1)

As the investor, when the scheduled investment briefing runs (or when I trigger a briefing manually), the briefing evaluates the stocks on my wishlist in light of recent financial news and their latest prices, explaining how recent developments affect them and whether they look interesting to consider.

**Why this priority**: Getting actionable commentary on prospective stocks in the regular briefing is the primary reason for tracking a wishlist in FNA.

**Independent Test**: With at least one stock on the wishlist and recent financial articles present, generate a briefing and verify that the prompt context includes the wishlist tickers separately from held positions and that the resulting briefing's `Watchlist Suggestion` section comments on the wishlisted stocks.

**Acceptance Scenarios**:

1. **Given** one or more tickers are on the wishlist, **When** a briefing is generated, **Then** the briefing generator supplies the wishlist tickers (with their latest euro prices and price timestamps) as a distinct group alongside held portfolio positions, and instructs the analyst assistant to assess how recent news affects the wishlisted tickers and whether they look interesting.
2. **Given** one or more tickers are on the wishlist, **When** the briefing is rendered, **Then** it preserves the three standard top-level sections (`Portfolio Impact`, `European Market Summary`, `Watchlist Suggestion`), with `Watchlist Suggestion` evaluating the investor's wishlist tickers and also suggesting one extra discovery ticker from recent news that is neither held nor wishlisted.
3. **Given** the wishlist is empty, **When** a briefing is generated, **Then** the briefing behaves identically to today, suggesting one ticker not currently held based on recent news under `Watchlist Suggestion`.
4. **Given** any generated briefing, **When** wishlist advice is presented, **Then** it is strictly informational commentary with no trade order creation, broker action, or execution capability.

---

### User Story 3 - Keep wishlist prices fresh in euro (Priority: P2)

As the investor, I want wishlist tickers to have their euro market prices resolved immediately when added and updated on the same schedule as held portfolio positions, so that both the Portfolio page and the next briefing always reflect current prices.

**Why this priority**: Automatic euro price resolution and periodic updates make the wishlist informative at a glance and give the briefing current price context without manual input.

**Independent Test**: Add a plain ticker name (such as `ASML` or `BTC`) to the wishlist, verify that its euro price and timestamp appear immediately, and verify that the scheduled price refresh updates wishlist items alongside held positions.

**Acceptance Scenarios**:

1. **Given** the investor adds a plain ticker name to the wishlist, **When** it is saved, **Then** the system immediately resolves its euro-denominated market listing and fetches its current euro price, using the same resolution rules as held positions.
2. **Given** wishlist items exist, **When** the scheduled price refresh runs, **Then** every wishlist item's price and timestamp are refreshed alongside held portfolio positions.
3. **Given** a wishlist ticker whose price cannot be resolved or fetched at that moment, **When** it is saved or refreshed, **Then** the wishlist entry is kept and displayed with an unavailable-price placeholder (`—` in the UI, and noted as price unavailable in the briefing context) rather than failing the operation.

---

### Edge Cases

- **Ticker already held in portfolio**: Attempting to add a ticker to the wishlist when it is already a held position is rejected with a clear user-facing message; the held position is untouched.
- **Wishlist ticker promoted to held position**: Adding a held position (with a positive share count) for a ticker currently on the wishlist automatically removes it from the wishlist so the ticker is never listed as both held and wishlisted at the same time.
- **Blank or whitespace-only ticker**: Submitting an empty or whitespace-only ticker to the wishlist is rejected before saving.
- **Case and whitespace normalisation**: Submitting ` asml ` or `Asml` normalises the ticker to `ASML` before checking for duplicates or saving.
- **Portfolio empty, wishlist populated**: Total worth stays hidden (or zero) and the allocation bar stays hidden; the briefing states that no held positions are configured, summarises European market themes, and evaluates the wishlist tickers under `Watchlist Suggestion`.
- **Both portfolio and wishlist empty**: The briefing states that neither held positions nor wishlist tickers are configured and provides a general European market briefing with an open watchlist suggestion.
- **Price provider unreachable when adding a wishlist item**: The wishlist item is still persisted and returned to the user with no price yet; the next scheduled price refresh retries it automatically.
- **Zero or negative share count on held positions**: Held portfolio positions require a strictly positive share count; tracking a stock without owning shares is done exclusively through the wishlist.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to add a ticker symbol to a wishlist in FNA without providing a share count.
- **FR-002**: The system MUST normalise every submitted ticker (trimming whitespace and converting to uppercase) before checking uniqueness or saving, for both held positions and wishlist items.
- **FR-003**: Each ticker MUST appear at most once across the user's held portfolio and wishlist combined:
  - Re-adding an existing wishlist ticker to the wishlist MUST be idempotent (updating/returning the existing wishlist entry without creating a duplicate).
  - Adding a ticker to the wishlist when it is already a held portfolio position MUST be rejected with a clear error message.
  - Adding a held portfolio position (with a positive share count) for a ticker that is currently on the wishlist — either via the wishlist row's promote action or via the held-positions form — MUST automatically remove that ticker from the wishlist.
- **FR-004**: Held portfolio positions MUST require a strictly positive share count (greater than zero).
- **FR-005**: Users MUST be able to view all wishlist items in a separate "Wishlist" panel below the held positions panel on the Portfolio page, showing columns for `Ticker`, `Last price` (in euro, or `—` when unavailable), `Updated` (last-fetched timestamp), a promote action (prompting for a positive share count to move the ticker into held positions), and a `Remove` action, along with its own ticker-only `Add to wishlist` form.
- **FR-006**: Wishlist items MUST NOT be counted in the held-positions count, total portfolio worth, or portfolio allocation bar/percentages.
- **FR-007**: Users MUST be able to remove any item from the wishlist at any time.
- **FR-008**: When a wishlist item is created, and during every scheduled portfolio price refresh cycle, the system MUST resolve the ticker's euro-quoted symbol and update its latest euro price and fetch timestamp using the same pricing mechanism as held positions. A pricing failure MUST NOT prevent saving or listing the wishlist item.
- **FR-009**: When generating a briefing (scheduled or manually triggered), the system MUST include the user's wishlist items (ticker, latest euro price, and fetch timestamp) in the briefing context as a distinct group from held portfolio positions.
- **FR-010**: The briefing instructions MUST keep the three existing top-level sections (`Portfolio Impact`, `European Market Summary`, `Watchlist Suggestion`) and require `Watchlist Suggestion` to:
  - When wishlist items are present: evaluate how recent news affects the wishlisted stocks and whether they look interesting to buy or watch closely, and also suggest one additional discovery ticker not currently held or wishlisted based on recent news.
  - When the wishlist is empty: suggest one ticker not currently held that looks interesting based on recent news (preserving existing behaviour).
- **FR-011**: The feature MUST provide informational investment commentary only; it MUST NOT create, propose, or execute trade orders.

### Key Entities

- **Portfolio Position** (existing): A held investment owned by the user, defined by a unique uppercase ticker, a positive share count (up to 10 decimal places), a resolved market price symbol, the latest euro unit price, and the timestamp when that price was fetched.
- **Wishlist Item**: A prospective investment the user is considering but does not yet hold, defined by a unique uppercase ticker, a resolved market price symbol, the latest euro unit price, and the timestamp when that price was fetched (with no share count or holding value).
- **Briefing** (existing): A periodic AI-generated market and portfolio report structured into `Portfolio Impact`, `European Market Summary`, and `Watchlist Suggestion`, now informed by both held portfolio positions and wishlist items.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user can add a stock ticker to the wishlist from the Portfolio page in under 15 seconds, entering only the ticker name.
- **SC-002**: 100% of wishlist additions for valid, traded tickers resolve and display a euro price on creation (when the price source is reachable) without changing the displayed total portfolio worth or asset allocation percentages by even €0.01 or 1%.
- **SC-003**: 100% of briefings generated while the wishlist contains at least one ticker include the wishlisted tickers in the briefing context and produce commentary addressing the watchlist in the `Watchlist Suggestion` section.
- **SC-004**: Attempting to create duplicate tickers within the wishlist, or adding a ticker that is already held in the portfolio to the wishlist, results in zero duplicate records across 100% of test cases.
- **SC-005**: Promoting a wishlist ticker (by entering a share count on its row) or adding a held position for a wishlisted ticker transitions the ticker out of the wishlist and into held positions in a single step, leaving zero tickers present in both lists simultaneously.

## Assumptions

- FNA continues to operate as a single-portfolio personal platform for the owner, matching the existing portfolio positions model (no per-user multi-tenant partitioning is needed in FNA).
- The existing scheduled briefing cadence (configurable cron, defaulting to 06:30 on the 1st of the month as amended in spec 001, plus the manual trigger button in the UI) is used for wishlist advice; on-demand per-stock AI chat or instant per-ticker analysis is out of scope.
- Keeping the three existing Markdown section headings (`## Portfolio Impact`, `## European Market Summary`, `## Watchlist Suggestion`) preserves compatibility with the briefing reader view while upgrading `## Watchlist Suggestion` from a purely random AI pick to an evaluation of the user's wishlist (plus a discovery pick).
- Target entry prices, personal notes per wishlist stock, price alerts, and trade order creation are out of scope for this feature.

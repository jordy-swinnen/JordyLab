# Feature Specification: Stock Price History on the FNA Portfolio Page

**Feature Branch**: `023-portfolio-price-history`

**Created**: 2026-11-07

**Status**: Draft

**Module**: `fna` (existing)

**Builds on**: 001 (FNA MVP1 completion: euro prices for held positions), 016 (Portfolio page and its wishlist panel)

**Reference**: Linear `JOR-98`

**Input**: User description: "As a user i want to be able to track stock history in the portfolio page so i can make a more informed decision on when to buy sell or hold."

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - See how a stock's price has moved (Priority: P1)

As the investor using FNA, I can open the price history of any stock I hold or have on my wishlist from the Portfolio page, see its euro price over time for a period I choose, and read how far it has risen or fallen over that period, so that I can judge its recent trend before deciding whether to buy more, sell or hold.

**Why this priority**: This is the whole request. Today the page shows only the current price, which does not show whether a stock has been rising, falling or moving sideways.

**Independent Test**: Open the Portfolio page with at least one position that has price history, open its history, switch between the periods, and check that the chart and the stated figures agree with each other and with the period selected.

**Acceptance Scenarios**:

1. **Given** the investor is on the Portfolio page with a stock that has price history, **When** they open that stock's history, **Then** the page shows a chart of its euro price for the default period of one year, together with the start price, end price, change in euro and change in percent for that period.
2. **Given** a stock's history is open, **When** the investor selects another period (one month, three months, one year or maximum), **Then** the chart and the figures update to that period without leaving the Portfolio page.
3. **Given** a stock's history is open for a period, **When** the investor reads the figures, **Then** they also show the highest and lowest euro price in that period.
4. **Given** a stock has less history than the selected period (for example, a stock bought six weeks ago when three months is selected), **When** the investor selects that period, **Then** the chart shows all the available history and the figures state the date from which the data starts.
5. **Given** a stock's history is open, **When** the investor moves the pointer over the chart or drags a finger across it, **Then** the chart shows the date and the exact euro price of the recorded point under the pointer, and the readout disappears when the pointer leaves the chart.

---

### User Story 2 - Use the history on a phone and without a mouse (Priority: P2)

As the investor, I can read and change the price history on a phone-width screen, operate it with the keyboard alone, and get the figures as text, so that the history is usable on every device I use and with assistive technology.

**Why this priority**: The app is used on phones, and the Portfolio page already had a keyboard-access bug (JOR-48). A history view that does not fit a phone or cannot be operated by keyboard would be unusable for part of the audience.

**Independent Test**: Open the history on a 375-pixel-wide screen and again using the keyboard only; check that the chart, the period choices and the figures are all reachable and readable without horizontal scrolling.

**Acceptance Scenarios**:

1. **Given** the investor opens a stock's history on a 375-pixel-wide screen, **When** the view is shown, **Then** the period choices, chart and figures fit the screen width, and nothing needs horizontal scrolling to read or change them.
2. **Given** the investor uses only the keyboard, **When** they open the history, change the period and close the history, **Then** every one of those actions can be reached and activated without a mouse.
3. **Given** a screen reader is in use, **When** the investor reaches the history, **Then** the start, end, change, highest and lowest figures are available as text, not only as part of the chart.

---

### Edge Cases

- **No history yet** (a newly added position, or a ticker the price source has no history for): the view says that no price history is available for this stock yet. The holding and the rest of the Portfolio page keep working.
- **Price source unavailable** when the history is requested: the view says that the history could not be loaded and lets the investor try again. It does not show an older or estimated price in its place.
- **Missing days** (weekends, market holidays or days without a recorded price): the chart connects the recorded prices only. No price is invented for the missing days.
- **Single recorded price in the period**: the figures say that a change cannot be calculated yet, instead of showing a change of zero.
- **Price in another currency**: history is always shown in euro, the same unit as the position's current price. If the source can only provide prices in another currency for a ticker, the view shows the no-history message rather than mixing currencies.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Users MUST be able to open the price history of any position from the Portfolio page, held or on the wishlist, without leaving that page.
- **FR-002**: The history MUST show the stock's recorded euro prices over time as a chart.
- **FR-003**: Users MUST be able to choose the period shown: one month, three months, one year or maximum (the daily history recorded, at most ten years). The default period is one year.
- **FR-004**: For the selected period, the history MUST state the start price and date, the end price and date, the change in euro and in percent, and the highest and lowest price.
- **FR-005**: The change figures and the chart MUST be calculated from the same recorded prices, so that they always agree.
- **FR-006**: When a stock has less history than the selected period, the history MUST show all available history and state the date from which it starts.
- **FR-007**: The most recent point on the chart MUST be the same price the Portfolio page shows for that position.
- **FR-008**: Price history MUST be refreshed on the same schedule as the positions' prices, with no separate schedule.
- **FR-009**: Price history MUST be informational only. It MUST NOT state, suggest or imply a buy, sell or hold recommendation, and it MUST NOT create, propose or execute a trade order.
- **FR-010**: When history is missing or cannot be loaded, the history MUST say so in plain words. It MUST NOT show zero, estimated or invented prices in place of recorded ones, and the rest of the Portfolio page MUST keep working.
- **FR-011**: Every period choice and the action that closes the history MUST be operable by keyboard alone.
- **FR-012**: The figures in FR-004 MUST be available as text, so they can be read without seeing the chart.
- **FR-013**: The history view MUST fit a phone-width screen of 375 pixels without horizontal scrolling.
- **FR-014**: Prices in the history MUST be in euro, the same unit as the current price of the position.
- **FR-015**: On the chart, the point under the pointer or finger MUST show its date and its exact recorded euro price, not an estimate. The readout MUST disappear when the pointer leaves the chart.

### Key Entities *(include if feature involves data)*

- **Price History Point**: One recorded euro price for one ticker on one date. A series of these is used to draw the chart and calculate the period figures.
- **Position** (existing): The investor's holding in one stock, or a wishlist item for a stock not yet owned. Price history is shown for every position and never changes its share count, value, total worth or allocation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An investor can open a stock's price history and switch to another period in no more than two taps or clicks from the Portfolio page.
- **SC-002**: For every checked stock with history, the change shown for a period equals the difference between the first and last prices of the chart for that period, in 100% of checks.
- **SC-003**: For 95% of views where the price source is reachable, the default-period chart is visible within 2 seconds of opening the history.
- **SC-004**: 100% of history views checked at 375 pixels wide can be read and used without horizontal scrolling.
- **SC-005**: No screen or message the feature adds states, suggests or implies a buy, sell or hold action, as confirmed in review of every screen and message.

## Assumptions

- FNA stays a single-portfolio personal platform for the owner, as in 001 and 016, so no per-user partitioning is needed.
- The existing price source can provide recorded euro prices for held and wishlisted tickers over time. This is the main dependency. Planning must confirm it before work starts; if the source cannot provide history, the feature needs another source first.
- The chart uses prices as recorded, at the granularity the source provides (at least once per trading day).
- The "maximum" period means the daily history recorded for the ticker, at most ten years. The price source returns monthly bars for longer ranges; mixing them with daily bars would make the chart and the change figures misleading, so they are not stored.
- Wishlist items (016) get the same price history as holdings, with the same History button and panel. This was added as a follow-up to the first release of this feature (see tasks.md, Follow-up).
- Technical indicators (moving averages, RSI), price alerts, comparisons between positions, and entering the investor's own purchase prices are out of scope. They are separate ideas.
- One year is the default period because it shows enough trend to judge a holding without crowding the view.

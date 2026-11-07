# Quickstart: Validate Stock Price History

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Contract**: [contracts/price-history-api.md](contracts/price-history-api.md)

This is the run-and-check guide for the finished feature. Run it locally first; release only when everything passes (feature_local_first_then_prod).

## Rules for this check

- **No hand-written rows.** The history must come from the real refresh job against the real price source, never from `INSERT` statements (AGENTS.md → Validation Data). Reading the table with `SELECT` is fine.
- **Dev database.** Use the dev Postgres the owner already runs. Do not stop, remove or recreate `jordylab-be-*` containers or volumes (AGENTS.md → Local containers).

## Prerequisites

- Dev Postgres running (`jordylab-be-pgvector-1`).
- Backend on the `local` profile.
- Frontend dev server.
- Signed in as the admin.

## 1. Start the stack

```bash
cd jordylab-be && ./gradlew bootRun
```

```bash
cd jordylab-fe && bunx nx serve jordylab
```

Open `http://localhost:4200/fna/portfolio`.

## 2. Add a held position

On the Portfolio page, add `ASML` with 2 shares. The position is resolved to `ASML.AS` (euro, Amsterdam) on the next refresh.

**Expected**: the position appears with a price. The new **History** button is visible on the row without hovering.

## 3. Let the refresh backfill and record

The refresh runs at start-up and then every 30 minutes. Restart the backend once so the first cycle runs now.

**Expected** in the backend log: no `Could not fetch a quote` warning for `ASML.AS`, and no `No euro price found`.

Check the rows (read-only):

```bash
podman exec jordylab-be-pgvector-1 sh -c 'psql -U "$POSTGRES_USER" -d jordylab -c "SELECT price_symbol, COUNT(*) AS days, MIN(price_date) AS first_day, MAX(price_date) AS last_day FROM finance.position_price_history GROUP BY price_symbol;"'
```

**Expected**: a row for `ASML.AS` with about 2,560 daily rows, reaching back about ten years (the backfill requests `range=10y`), and a `last_day` equal to today's Brussels date after the cycle.

## 4. Check the endpoint

Open the Portfolio page's network tab and open the history for ASML, or request the endpoint directly with the admin session cookie:

```http
GET /api/fna/portfolio/{id}/price-history?period=ONE_YEAR
```

Check each of these against [the contract](contracts/price-history-api.md):

| Check | Expected |
|---|---|
| `status` | `AVAILABLE` |
| `points` | Ascending by date, no `null` prices |
| `summary.endPriceInEuro` | Equals the position's price on the Portfolio page (FR-007) |
| `summary.changeInEuro` | Equals `endPriceInEuro − startPriceInEuro` |
| `summary.changePercent` | Equals the change ÷ start × 100, to two decimals |
| `summary.highestPriceInEuro` / `lowestPriceInEuro` | Bound every point in the window |
| Other periods | `THREE_MONTHS`, `ONE_MONTH` and `MAX` return the same end price and narrower or wider windows |

Also check the error cases:

| Request | Expected |
|---|---|
| `period=FOREVER` | `400` |
| The id of a wishlist item | `404` with `{"code": "POSITION_NOT_HELD"}` |
| A position that has no price symbol yet | `200` with `status: NO_HISTORY_YET` |

## 5. Check the page

Use a 375-pixel-wide viewport.

| Check | Expected |
|---|---|
| Open history on a row | The panel appears under the table with the chart, the period buttons and the figures as text. |
| Change the period | The chart and figures update without leaving the page. |
| Keyboard only | Tab reaches the History button, the period buttons and Close. Enter or Space activates them. Focus returns to the row's button on Close. |
| Screen reader | The figures are readable as text, not only inside the chart. |
| Phone width | No horizontal scrolling. |
| Theme | Colours come from theme tokens; switching themes changes the chart colours. |

## 6. Automated checks for the touched scope

Backend (module boundaries first, then the fna tests):

```bash
cd jordylab-be && ./gradlew :test --tests "*ModularityTests*"
```

```bash
cd jordylab-be && ./gradlew test --tests "dev.jordy.jordylab.fna.*"
```

Frontend:

```bash
cd jordylab-fe && bunx nx test fna-api
```

```bash
cd jordylab-fe && bunx nx test fna-ui
```

Lint each changed TypeScript file:

```bash
cd jordylab-fe && tools/lint-changed.sh <file>
```

Theme tokens (no hex colours in the new templates):

```bash
cd jordylab-fe && bun tools/check-theme-tokens.mjs
```

**Expected**: all green, with no failures reported.

## 7. Before shipping only

The browser journeys and accessibility gate run on a throwaway stack. Run them once the local checks pass:

```bash
cd jordylab-fe && e2e/run.sh web
```

The accessibility spec needs one new `PAGES` entry for the Portfolio page with the panel open. The layout spec checks the 360, 390 and 430 px widths.

Documentation to update in the same change at ship time (AGENTS.md → Workflow): the `docs/status.md` row and end-goal entry, and the architecture drawing in `README.md` if the new Yahoo client and table change it.

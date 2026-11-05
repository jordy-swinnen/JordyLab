# Contract: MCP tools (read-only)

Nine tools, one set per data area. Every tool carries the MCP read-only hint and a description that says what it
returns and that it only reads (FR-017). Each description is written so Claude can choose without trial and error.

Common rules: lists return `McpToolPage` (at most 50 items, `totalMatching`, `hasMore`); unknown values are `null`,
never guessed (FR-010); records are the tool-specific shapes from `data-model.md`.

## Game catalog (`GameCatalogMcpTools`, module `gamecatalog`)

### `search_games`

Searches the game catalog with the same filters as the web app's grid.

| Argument | Type | Default | Notes |
|---|---|---|---|
| `search` | string? | none | Matches the title, as the grid's search box does |
| `platforms` | string[]? | none | Platform names |
| `installStatus` | `INSTALLED` \| `NOT_INSTALLED` \| `ALL` | `INSTALLED` | Same default as the grid |
| `sources` | enum[]? | none | `STEAM_OWNED`, `STEAM_FAMILY`, `EMULATED`, `CONSOLE` |
| `minLocalPlayers` | int? | none | 1–`MAX_LOCAL_PLAYERS`; only confirmed counts match (FR-010) |
| `mark` | enum[]? | none | `WANT_TO_PLAY`, `PLAYED_LIKED`, `PLAYED_DISLIKED`, from the admin's own marks |
| `romStatus` | enum[]? | none | `UNKNOWN`, `VALIDATED`, `BROKEN` |
| `sort` | enum? | server default | The grid's sort values |
| `page` | int | 0 | Zero-based page index (the web app's page model) |
| `limit` | int | 50 | Clamped to 1–50 |

Returns `McpToolPage<GameSearchItem>`. Calls the same `GameQueryService.getGames` path as the web app. The local multiplayer narrowing is `minLocalPlayers` only; the result still reports each game's `localMultiplayer` (null when unknown).

FR: 001 (catalog questions), 007 (same access), 009 (bounds), 010 (unknown facts), 015 (explicit fields).

### `get_game`

Reads one game in full.

| Argument | Type | Notes |
|---|---|---|
| `gameId` | UUID | Required |

Returns `GameDetail`, or a "not found" tool error. Includes the places where the game lives, the local-multiplayer
facts with their nulls, the description and its source, and the mark totals (never voters).

FR: 001, 010, 014, 015.

### `list_places`

Lists the hosts, consoles and platforms the catalog knows about. No arguments. Returns `PlaceSummary`. Used to answer
"which consoles do I have?" and to resolve a place name before a `search_games` call.

### `list_sources`

Lists the scan sources and their last outcome. No arguments. Returns `McpToolPage<SourceSummary>`. Excludes filesystem
paths and machine identifiers.

### `get_library_status`

Reports the Steam library syncs: last run, outcome, and whether a refresh run is currently running. No arguments.
Returns `LibraryStatus`. Matches the edge case "a refresh is running" (the answer comes from the last committed state).

## Finance (`FnaMcpTools`, module `fna`)

### `search_articles`

Finds recent news articles whose **title** contains the text (for example a ticker or company name). The catalog stores
no ticker tags, so this is a title match (research R9, spec change S2).

| Argument | Type | Default | Notes |
|---|---|---|---|
| `text` | string? | none | Case-insensitive title match. Omitted returns the newest articles |
| `page` | int | 0 | Zero-based page index |
| `limit` | int | 50 | Clamped to 1–50 |

Returns `McpToolPage<ArticleItem>`, newest first. FR: 001, 009.

### `get_portfolio`

Lists the portfolio with the last known prices, their fetch times and the total of the priced positions. No arguments.
Returns `PortfolioSummary`. Prices are reported with their fetch time, never called current (edge case "stale prices").

### `get_wishlist`

Lists the wishlist with the last known prices and fetch times. No arguments. Returns `McpToolPage<WishlistItem>`.

### `get_latest_briefing`

Returns the most recent briefing, with its generation time and model. No arguments. Returns `BriefingView`. Never
triggers a new briefing: the trigger endpoint is not exposed (FR-012). When none exists yet, returns
`reason = "NO_BRIEFING_YET"`, and the answer says so (US2 scenario 4).

## Not tools (by design)

Create, update, delete, approve, reject, scan, refresh, sync, generate, notify, reset password, end sessions, post a
Linear issue, ask LibBot, change any AI model setting, and any feedback-inbox or user-administration read (deferred by
Q1, spec v1 scope). A question that needs one of these is answered as "the connector is read-only" or "not available
through the connector yet" (FR-013).

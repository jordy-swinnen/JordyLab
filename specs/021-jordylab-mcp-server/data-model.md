# Data Model: JordyLab MCP Server (read-only)

This feature adds **no database tables and no Flyway migration**. It owns no schema, so `ModularityTests` has no schema
rule to satisfy. The shapes below are the in-memory state and the response records that the tools and the discovery
endpoint return.

## Persistent data

None. Connector access is the existing admin role on the existing identity, and the connector client is realm
configuration in Keycloak (see `contracts/keycloak-connector-client.md`).

## Response envelope (`mcp` root, public API)

`McpToolPage<T>` is the one shared shape. Every list-returning tool uses it (FR-009).

| Field | Type | Meaning |
|---|---|---|
| `items` | `List<T>` | At most 50 items |
| `returned` | `int` | `items.size()` |
| `totalMatching` | `long` | How many items match in total, so the answer can say "50 of 412" |
| `hasMore` | `boolean` | `totalMatching > offset + returned` |

Constraints: `limit` is clamped to 1–50 by the tool, `offset` is at least 0, and the envelope is built in one place so
no tool can skip the clamp.

## Tool result records (per domain, `rest/controller/model/`)

Each record is tool-specific. Fields are listed explicitly, so hidden data never reaches a response (R9).

### Catalog (`gamecatalog`)

- `GameSearchItem`: `id` (UUID), `title`, `platform`, `sources` (labels: `Steam (Owned)`, `Steam (Family)`, `Emulated`,
  `Console`), `installed` (boolean), `localMultiplayer` (true, false, or **null when unknown**), `maxLocalPlayers`
  (int or **null when unknown**), `markTotals` (`wantToPlay`, `playedLiked`, `playedDisliked`; totals only, never
  voters).
- `GameDetail`: the `GameSearchItem` fields plus `developer`, `publisher`, `releaseYear`, `genres`, `description` with
  its source label (`steam` or `ai`), `splitScreen` (nullable), `onlineOnly` (nullable, derived as the web app derives
  it), `playerCountSource` (nullable), `places` (`PlaceRef`: `name`, `kind` = `host` | `console` | `steamLibrary`,
  `installed` boolean), `romStatuses` (per emulated copy: `UNKNOWN` | `VALIDATED` | `BROKEN`).
  Excluded by design: artwork, voter identities, source file paths, machine identifiers.
- `PlaceSummary`: `hosts` (`label`, the display name or hostname), `consoles` (`name`, `platform`), `platforms` (names).
- `SourceSummary`: `id`, `name`, `type`, `enabled`, `lastOutcome`, `lastScanAt`, `installedGames`. Excluded: paths.
- `LibraryStatus`: `steamOwnedSync` and `steamFamilySync` (each `lastRunAt`, `outcome`, `refreshRunning` boolean).

### Finance (`fna`)

- `ArticleItem`: `title`, `url`, `publishedAt`, `feedName`.
- `PortfolioItem`: `ticker`, `shareCount` (nullable), `lastPrice` (nullable), `lastPriceFetchedAt` (nullable), `value`
  (`shareCount × lastPrice`, null if either is unknown).
- `PortfolioSummary`: `items` (as `McpToolPage<PortfolioItem>`), `totalValue` (sum of known values), `allPriced` boolean.
  Positions are few, so the page is normally complete.
- `WishlistItem`: `ticker`, `lastPrice`, `lastPriceFetchedAt`.
- `BriefingView`: `generatedAt`, `modelUsed`, `content`, or `null` with `reason = "NO_BRIEFING_YET"` (FR-010).

### Discovery (`mcp`)

- `ProtectedResourceMetadata` (RFC 9728): `resource`, `authorizationServers` (a list with one entry), `scopesSupported`
  (`["openid"]`), `bearerMethodsSupported` (`["header"]`), `resourceName` (`"JordyLab"`). Serialized with the RFC's
  snake_case names.

## In-memory state (`mcp/service`)

`McpRateLimiter` keeps one window per admin:

| Field | Type | Meaning |
|---|---|---|
| key | `String` | The admin's JWT `sub` |
| `windowStart` | `Instant` | When the current one-minute window began (from the injected `Clock`) |
| `count` | `int` | Calls accepted in this window |

Rules: a call is accepted while `count < 60`; a call after that is refused and does **not** increment the count past
the limit. When `now - windowStart >= 1 minute` the window resets. The map lives in process memory, so a restart resets
it (R7, accepted). Entries for idle admins are removed when their window expires. There is one admin today, so the
map stays tiny.

## Audit record (log line only, not stored)

One line per tool call on `dev.jordy.jordylab.mcp.audit`:

`mcp tool=<name> admin=<sub> outcome=<ok|rate_limited|error> durationMs=<n>`

No arguments, no result, no e-mail address, no token (FR-014, FR-016). The `sub` is Keycloak's user identifier, not a
name or e-mail.

## Validation rules

- `limit`: integer 1–50, default 50. Out of range is clamped, not rejected, so the model is never stuck (FR-009).
- `offset`: integer 0 or more.
- `gameId`: a UUID. A malformed or unknown ID returns a clear "not found" tool error, never a stack trace (FR-018).
- Text arguments: trimmed, at most 200 characters, and matched as data (never as a query fragment).
- Enumerations (`installStatus`, `source`, `mark`, `romStatus`, `sort`) reuse the existing enums, so the tool schema
  lists exactly the values the web app accepts.

## State transitions

None. The connector reads committed state and changes nothing. The only changing value is the rate-limit window, which
is internal and not part of any response.

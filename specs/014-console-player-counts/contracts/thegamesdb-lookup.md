# Contract: TheGamesDB lookup (outbound)

What the backend sends to TheGamesDB and how it reads the answer. Measured against the live API on 2026-11-01
([research.md](../research.md) R1–R5, R9).

## Configuration

| Name | Where | Default | Meaning |
|------|-------|---------|---------|
| `THEGAMESDB_API_KEY` | environment (`jordylab-be/.env` locally, `jordylab-secrets` in prod) | empty | The key. Empty → the client is unconfigured, every lookup answers "not configured", one INFO line at start-up. |
| `jordylab.gamecatalog.thegamesdb.base-url` | `application.yaml` | `https://api.thegamesdb.net/v1` | Overridden by WireMock in tests. |
| `jordylab.gamecatalog.thegamesdb.timeout-ms` | `application.yaml` | `5000` | Connect and read timeout. |
| `jordylab.gamecatalog.thegamesdb.min-interval-ms` | `application.yaml` | `1000` | Minimum gap between two calls, for every caller. |
| `jordylab.gamecatalog.thegamesdb.allowance-reserve` | `application.yaml` | `20` | Pause when `remaining_monthly_allowance` is at or below this. |

## Request

```http
GET {base-url}/Games/ByGameName?name={title}&fields=players&filter[platform]={platformId}&apikey={key}[&page={n}]
User-Agent: JordyLab/1.0 (+https://jordylab.be)
Accept: application/json
```

- `name`: the game's catalog title as stored (not the title key).
- `filter[platform]`: the TheGamesDB id of one of the game's consoles (map in research R4). A console without an id is not
  called; the result is `CONSOLE_NOT_LISTED`.
- `fields=players` is mandatory: without it the response carries no `players`.
- `page`: added only when the previous response's `pages.next` was non-null; at most 3 pages per lookup.
- One call per (title key, console) per batch; calls are at least `min-interval-ms` apart.

## Response read (200)

```json
{
  "code": 200,
  "status": "Success",
  "remaining_monthly_allowance": 953,
  "extra_allowance": 0,
  "allowance_refresh_timer": 2606609,
  "data": {
    "count": 3,
    "games": [
      { "id": 2318, "game_title": "Mario Party 7", "platform": 2, "region_id": 2, "players": 7 },
      { "id": 116617, "game_title": "Mario Party 7", "platform": 2, "region_id": 2, "players": 7 },
      { "id": 117730, "game_title": "Mario Party 7 [Nintendo GameCube Mic Included]", "platform": 2, "players": 4 }
    ]
  },
  "pages": { "previous": null, "current": "<request URL, contains the key>", "next": null }
}
```

(Values from the 2026-11-01 probe, trimmed. Never log, store or return any part of this body; `pages.*` contains the key.)

Reading rules, in order:

1. Keep `games[]` entries whose `TitleKeys.keyFor(game_title)` equals the game's title key **and** whose `platform` is the
   requested id.
2. Drop entries without a `players` value.
3. None left → `GAP(NO_MATCH)`.
4. Two different `players` values → `GAP(ENTRIES_DISAGREE)` (the example above: 7, 7, 4).
5. One value below 2 → `GAP(COUNT_BELOW_TWO)`.
6. Otherwise → `FOUND(value)`.
7. Record `remaining_monthly_allowance`; at or below the reserve → pause until `allowance_refresh_timer` seconds from now
   (`ALLOWANCE_LOW`). The current answer is still used.

## Failures

| Situation | Seen as | Result for the game | Source state | Log (WARN, once per pause) |
|-----------|---------|---------------------|--------------|----------------------------|
| HTTP 403 | `{"code":403,"status":"Invalid API key was provided.","remaining_monthly_allowance":0,"allowance_refresh_timer":0}` for a bad key; the same code for a used-up allowance | `DEFERRED` | paused until the timer runs out, at least 1 hour (`KEY_REFUSED_OR_ALLOWANCE_USED`) | `TheGamesDB refused the request (HTTP 403); pausing until <instant>` |
| Timeout, connection error, HTTP 5xx | exception | `DEFERRED` | paused 15 minutes (`UNREACHABLE`) | `TheGamesDB unreachable (<ExceptionClass>); pausing until <instant>` |
| Other 4xx | status | `DEFERRED` | paused 15 minutes (`UNREACHABLE`) | `TheGamesDB answered HTTP <code>; pausing until <instant>` |
| Body not readable | exception | `DEFERRED` | not paused | `TheGamesDB response unreadable (<ExceptionClass>)` |
| Paused | — | `DEFERRED`, no call made | unchanged | none |
| Unconfigured | — | `DEFERRED`, no call made | `configured = false` | INFO once at start-up: `TheGamesDB not configured (THEGAMESDB_API_KEY unset); player counts come from IGDB only` |

No log line, exception message rethrown, or response from this backend may contain the request URI, the key or a response
body. The client test asserts this for every row above.

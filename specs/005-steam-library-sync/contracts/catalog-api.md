# Contract: Game Catalog REST API (005 changes)

Base: `/api/gamecatalog`. Amends `specs/002-game-catalog/contracts` and 004's host additions.

## `GET /games` (changed)

| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `search` | string | — | unchanged |
| `platform` | string | — | unchanged |
| `host` | string | — | unchanged; ignored when `installStatus=NOT_INSTALLED` |
| `installStatus` | `INSTALLED` \| `NOT_INSTALLED` \| `ALL` | `INSTALLED` | FR-020 |
| `librarySource` | CSV of `OWNED` \| `FAMILY` \| `LOCAL` | — | omitted ⇒ no source filter |
| `page` | int | 0 | unchanged |
| `size` | int | 60 | unchanged, clamped 1..200 |

Response `GamesPageResponse` items gain:

```json
{
  "id": "…", "title": "…", "platform": "Steam",
  "coverStatus": "EXTERNAL_URL", "coverUrl": "…", "coverEndpoint": null,
  "installStatus": "INSTALLED",
  "librarySource": "OWNED"
}
```

`installStatus`/`librarySource` are always present. `LOCAL` is returned for scan-only games.

## `GET /games/{id}` (changed)

`GameDetailResponse` gains `installStatus`, `librarySource`, and `familyOwners: string[]` (empty unless
`librarySource = FAMILY`). All existing fields unchanged.

## `GET /platforms`, `GET /hosts` (unchanged shape)

Their distinct-value lists now derive from the same visibility predicate (installed or in library).
`GET /hosts` keeps meaning "hosts with an installed, enabled source"; not-installed library games do not
add hosts.

## `POST /library/steam/sync` (new)

- Auth: `jordylab-user` (same as other gamecatalog admin endpoints).
- Body (optional): `{ "force": false }` — bypass the suspicious-shrink guard.
- 200 → `LibrarySyncRunResponse`:

```json
{
  "librarySource": "OWNED",
  "outcome": "APPLIED",
  "startedAt": "2026-09-27T10:00:00Z",
  "finishedAt": "2026-09-27T10:00:04Z",
  "entriesSubmitted": 412,
  "entriesAdded": 87,
  "entriesRemoved": 0,
  "metadataCalls": 25,
  "aiCalls": 6,
  "errorCode": null
}
```

No Steam key material in the response.

## `POST /library/steam-family/sync` (new)

- Body: `{ "accessToken": "<user token>", "force": false }`.
- The token is used in memory for the sync only; it is never persisted, logged or echoed.
- 200 → `LibrarySyncRunResponse` with `librarySource: "FAMILY"`.
- 400 `FAMILY_TOKEN_REQUIRED` when blank; 502 `FAMILY_SYNC_FAILED` (with `errorCode: TOKEN_EXPIRED`
  or `UNKNOWN_RESPONSE`) when Steam rejects/returns an unexpected shape — catalog unchanged.

## `GET /library/status` (new)

```json
{
  "owned": { "lastSuccessAt": "2026-09-27T10:00:04Z", "lastOutcome": "APPLIED",
             "entriesActive": 412, "metadataCalls": 25, "aiCalls": 6 },
  "family": { "lastSuccessAt": null, "lastOutcome": null,
              "entriesActive": 0, "metadataCalls": 0, "aiCalls": 0,
              "familyTokenPresent": false, "stale": true }
}
```

`stale` = last successful family sync older than `jordylab.gamecatalog.library.stale-after-days`
(or never). `familyTokenPresent` is always `false` because the token is not retained (FR-011).

## `POST /chat` (changed)

`ChatFilter` schema advertised to the model gains:

| Field | Type | Notes |
|-------|------|-------|
| `installStatus` | `"INSTALLED"` \| `"NOT_INSTALLED"` \| null | null ⇒ no constraint |
| `librarySources` | array of `OWNED` \| `FAMILY` \| `LOCAL`, or null | validated against the visible members list |

Both are added to `ALLOWED_FILTER_FIELDS`, `parseFilter` (with visible-member validation) and the
`findForChatFilter` JPQL predicate. `ChatService` injects the visible status/source values into the
translation prompt like it does for hosts.

## Errors (new)

| Code | HTTP | When |
|------|------|------|
| `FAMILY_TOKEN_REQUIRED` | 400 | family sync body missing the token |
| `FAMILY_SYNC_FAILED` | 502 | family endpoint failed or returned an unexpected shape |
| `STEAM_SYNC_FAILED` | 502 | owned endpoint failed or returned an unexpected shape |

All three leave catalog rows untouched (FR-016, SC-008).

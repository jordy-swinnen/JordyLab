# Contract: Catalog API Deltas for Switch Games

Switch games are exposed through the existing Game Catalog read endpoints; no new public read endpoints are needed.
This document describes the deltas.

## Existing endpoints (unchanged path semantics)

### `GET /api/gamecatalog/games`

Already supports `search`, `platform`, `host`, `installStatus`, `librarySource`, `localMultiplayer`, page/size.

**Delta**: `host` and `platform` now include `"Nintendo Switch"` for games with a Switch `GameInstallation`. `installStatus`
defaults to `INSTALLED`; Switch games appear under `INSTALLED` because their `GameInstallation.presence` is `INSTALLED`.

### `GET /api/gamecatalog/games/{id}`

**Delta**: `GameDetailResponse` gains a per-host format map for manual hosts.

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "title": "Mario Kart 8 Deluxe",
  "platform": "Nintendo Switch",
  "hosts": [
    { "hostname": "Nintendo Switch", "sourceType": "SWITCH" }
  ],
  "hostFormats": {
    "Nintendo Switch": "PHYSICAL"
  },
  "installStatus": "INSTALLED",
  "librarySource": "OWNED",
  ...
}
```

`hostFormats` only contains entries for manual hosts that carry a format. Steam/EmuDeck hosts are omitted.

### `GET /api/gamecatalog/platforms`

**Delta**: `"Nintendo Switch"` appears if any Switch game exists.

### `GET /api/gamecatalog/hosts`

**Delta**: `"Nintendo Switch"` appears as a host if the virtual Switch source is enabled and has at least one installed
game.

### `POST /api/gamecatalog/chat`

**Delta**: Switch games are included in the catalog corpus used for grounded chat answers, exactly like other games.
No request/response shape change.

## Security

Read endpoints remain `hasAnyRole("admin","guest")`. Write endpoints (refresh metadata/enrichment/multiplayer) remain
`admin` only and do not need Switch-specific changes.

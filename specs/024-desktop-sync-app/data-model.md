# Phase 1 Data Model: Desktop Sync App

**Date**: 2026-11-11 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

No new database tables. This feature adds one enum value, one per-account preference, and app-side state files.

## Backend (existing schemas)

### `gamecatalog` — `SourceType.SUNSHINE` (new enum value)

| Aspect | Detail |
|--------|--------|
| Entity | `SourceType` (`jordylab-be/.../gamecatalog/domain/SourceType.java`) |
| Change | Add `SUNSHINE { platform() → "Sunshine" }` alongside `STEAM`, `EMUDECK` |
| Migration | None needed for the enum itself (source types are code, not rows); `scan_source` rows are created at first scan by the existing resolve-or-adopt flow keyed on `(machine_id, sourceType)` |
| Parser | `SunshineLibraryParser` registered as `@Component("SUNSHINE")` — reads the client's `games[]` payload (client-grouped, like EmuDeck); falls back to path inference |
| Reconciliation | Adopt onto the existing catalog entry: Steam by `(platform, steam_app_id)`, others by `(platform, LOWER(title))` — the existing adoption rules, unchanged |
| Validation | A `SUNSHINE` source's games carry platform `Sunshine` or the detected platform; entries are ordinary `GameInstallation` rows per source |

### `identity` — theme preference (per account)

| Aspect | Detail |
|--------|--------|
| Entity | User attribute on the existing account aggregate (identity schema) |
| Values | One of the six theme ids (`content-led`, `oxblood`, `flare-orange`, `paper`, `glasshouse`, `heather`) or `follow-device` |
| Default | `follow-device` (matches today's web behaviour) |
| Migration | Additive column/table via Flyway (`CREATE SCHEMA IF NOT EXISTS identity` header per convention). No rows to migrate: the web app falls back to its localStorage value on first read, then persists the account preference |
| API | Read/update through the existing user endpoints (admin self); both web and desktop read the same value |
| Retention | Lives with the account; removed with it |

### OAuth public client for the desktop connect (pattern of spec 021)

| Aspect | Detail |
|--------|--------|
| Entity | Second public client on the existing authorization server (spec 021 `McpAuthorizationServerConfiguration` pattern) |
| Redirect | Loopback `http://127.0.0.1:<port>/callback` (port registered per platform convention) |
| Grant | Authorization code + PKCE S256, admin-only machine-token issuance |
| Token | The app receives the `jlmt_` machine token and stores it in the existing token cache path |

## App-side state (no database)

### Token cache — `~/.config/jordylab/scan/token.json` (existing, reused)

Machine token, mode 0600. Revocable in Settings → Machines (existing behaviour).

### App config — `~/.config/jordylab/desktop/config.json` (new)

| Field | Type | Meaning |
|-------|------|---------|
| `schedule` | string | cron-like or interval description (default = today's cadence) |
| `extra_roots` | map | per-library-type additional roots (carried over from script-era config) |
| `window` | object | last window position, theme override cache |

## Catalog entities touched (unchanged)

- `Game`, `GameInstallation`, `ScanSource` — no structural change; the app is another scan client speaking the existing ingest contract.
- `game_library_entry`, marks, enrichment — untouched; identity continuity is preserved by the shared scan core's identical fingerprints.

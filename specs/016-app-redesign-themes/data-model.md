# Data Model: App Redesign with Six Themes

This feature adds no server data, no database change and no API. Everything below lives in the browser (or the native shell) on one device.

## Theme

A named set of colour values selected by the root element's `data-theme` attribute.

| Field | Meaning |
|---|---|
| `id` | one of `content`, `oxblood`, `flare`, `paper`, `glasshouse`, `heather` |
| `name` | display name: Content Led, Oxblood, Flare Orange, Paper, Glasshouse, Heather |
| `kind` | `dark` or `light`; sets the browser's colour-scheme hint |
| tokens | the 39 colour values in [contracts/theme-tokens.md](contracts/theme-tokens.md) |

Relationships and rules:

- Exactly three themes per kind; the picker lists the dark group, then the light group.
- `content` is the default dark theme and the fallback when anything is wrong; `paper` is the default light theme.
- Platform and ROM-validated colours are the same in every theme.
- A theme is valid only if its contrast contract holds (checked by a test; see below).

## Theme preference

What one device has chosen.

| Field | Values |
|---|---|
| `preference` | `system` or a Theme `id` |

Rules: stored under `jordylab.theme`; any other stored value is treated as `system`; never synced and never sent to the server.

State: `system` ⇄ any theme id, changed only by the person in Settings → App. While `system`, the applied theme follows the device setting.

## Screen state

For every screen that loads data.

| State | Entered when | Left when |
|---|---|---|
| `loading` | the first request starts | data arrives, the list is empty, or the request fails |
| `empty` | the request succeeded with nothing to show | data appears (a scan, an add) or a filter is cleared |
| `error` | the request failed | the person retries |
| `loaded` | data arrived | the screen reloads (back to `loading`) |

LibBot adds `firstRun` (no conversation yet), `answering` (a question is in flight), and `limit` (no questions left today); `limit` clears at the next day.

## Cover plate pairing

For a game with no artwork: a stable index from 0 to 5 computed from the title (the same hash as today), selecting `plate-N` and `plate-N-ink` of the active theme. The same title always gets the same index in every theme.

## Validation rules (from the spec)

| Rule | Where it is enforced |
|---|---|
| Text and meaningful marks meet the contrast contract in all six themes | unit test over the token table; end-to-end accessibility audit per theme |
| First paint uses the stored theme | boot script; end-to-end test that reads the root attribute before the app bundle runs |
| No raw colour values outside the token files and the brand assets | a repository check (`tools/check-theme-tokens`) plus lint |
| The sign-in page palettes equal the Content Led and Paper values | the same check |

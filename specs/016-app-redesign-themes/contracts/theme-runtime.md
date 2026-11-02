# Contract: theme runtime

How the active theme is chosen, stored, applied and observed. Nothing here touches the server.

## Values

- Theme ids: `content`, `oxblood`, `flare`, `paper`, `glasshouse`, `heather`.
- Preference values: `system` or one theme id.
- Resolution of `system`: device dark → `content`, device light → `paper`.

## Storage

| Item | Value |
|---|---|
| Place | the browser's local storage for the app's origin; the native app keeps its own copy because its web view has its own origin |
| Key | `jordylab.theme` |
| Stored value | `system` or a theme id, as a plain string |
| Missing or unknown value | treated as `system` and not rewritten |
| Scope | one device; never sent to the backend |

A failed read or write (private mode, storage full) never throws to the user: the preference then lasts for the session only.

## Boot script (no flash)

A small blocking script, `theme-boot.js`, is served from the app's own origin and loaded in the document head **before** any stylesheet. It is an external file, not inline, so the production Content-Security-Policy (`script-src 'self'` plus one inline-script hash) needs no change.

It must, in this order and in a few lines of plain code with no imports and no colour values:

1. read `jordylab.theme`; accept it only if it is `system` or one of the six theme ids it lists;
2. resolve `system` with `matchMedia('(prefers-color-scheme: dark)')`;
3. set `data-theme` on the root element.

If it fails, the page renders the default block (`:root`), which is Content Led.

The browser's address-bar colour has two static defaults in `index.html`: `<meta name="theme-color" content="#131314" media="(prefers-color-scheme: dark)">` and one with the Paper ground for light. `ThemeStore` replaces them with the active theme's ground (read from the computed `--background`) as soon as it starts, so a stored non-default theme corrects the colour a moment after first paint; the page itself is already right.

The id list in the script and the list in the store must equal the list in `themes.js`; a unit test enforces it.

## Runtime store

A signal store in `libs/shared/theme/api`, `ThemeStore`:

| Member | Type | Meaning |
|---|---|---|
| `preference` | read-only signal of `'system' \| ThemeId` | what the person chose |
| `activeTheme` | read-only signal of `ThemeId` | what is applied now (resolves `system`) |
| `themes` | constant list of `{ id, name, kind }` (no colours) | for the picker, in display order: dark group then light group |
| `choose(preference)` | method | stores, applies, updates `theme-color`, and on the native app tells the status bar |
| (internal) | listener | re-resolves `system` when the device setting changes |

Applying a theme sets `data-theme` and updates the `theme-color` meta tag from the computed `--background`; it does not reload and does not re-render components, because every colour is a CSS variable.

## Native shell

On the Android app the store also sets the status-bar colour and icon style from the active theme. The splash screen cannot follow a runtime choice; it uses the default dark `ground`.

## Sign-in page

The identity provider's login page cannot read the app's storage (different origin). It follows `prefers-color-scheme` only: dark → the Content Led palette, light → the Paper palette. Its two palettes are written by hand in its own stylesheet and checked against this contract by `tools/check-theme-tokens`.

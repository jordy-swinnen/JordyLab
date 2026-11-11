# Contract: Theme Preference (account-level)

New/changed. Backend: `identity` module endpoints. Web + desktop read the same value. Serves US3 / FR-009, ticket D6.

## API (existing user endpoints, additive)

| Method | Path | Auth | Body / Result |
|--------|------|------|---------------|
| `GET` | `/api/identity/me/preferences` | signed-in user | `{ "theme": "content-led \| oxblood \| flare-orange \| paper \| glasshouse \| heather \| follow-device" }` |
| `PUT` | `/api/identity/me/preferences` | signed-in user | same body; 200 with the stored value |

- Default when unset: `follow-device` (matches today's behaviour).
- Both the web app and the desktop app read this value after sign-in and apply it (web: CSS themes from spec 016; desktop: Qt stylesheets).

## Migration (web app)

1. On first load after the feature ships, the web app reads the account preference.
2. If the account has no stored value, it reads the device's `localStorage jordylab.theme`, writes it to the account preference once, and keeps rendering it.
3. Subsequent theme changes go to the account preference (web picker re-labelled as account-wide, per the design brief).

## Rendering

- Web: existing six-theme CSS (spec 016), unchanged; `follow-device` resolves to the device light/dark default as today.
- Desktop: Qt stylesheets per theme, verified against `docs/design/JOR-78/` (theme fidelity is a listed risk in the plan).

## Edge cases

- Offline start (desktop): render the last known theme, then reconcile on next successful read (US3.3).
- Unknown theme value (old client): fall back to `follow-device`.

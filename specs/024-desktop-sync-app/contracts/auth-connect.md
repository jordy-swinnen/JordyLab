# Contract: Machine Connect (browser sign-in + loopback callback)

Serves US1.1 / FR-002, ticket D4. Implemented as a small admin web page plus the app's loopback listener — no changes to the authorization server.

## Flow

1. App shows **Connect this PC** (design `D_Connect.png`) with a **Sign in** action.
2. App starts a loopback listener on `http://127.0.0.1:19878/callback` (fixed port, following the spec 021 fixed-port pattern; 19877/19876 belong to the MCP clients) with a random `state`, and opens
   `GET {backend}/connect/desktop?state=...&port=19878&machine=<hostname>` in the system browser.
3. The connect page is guarded by `authGuard` + `roleGuard('admin')`: a signed-out person signs in first; a guest is refused.
4. The page calls the existing `POST /api/identity/machines` with `{"name": "<hostname>"}` and receives the plaintext `jlmt_` token (shown once, as today).
5. The page redirects the browser to `http://127.0.0.1:19878/callback?state=<state>&token=<token>`.
6. The app validates `state`, stores the token in `~/.config/jordylab/scan/token.json` (mode 0600), answers with a success page and closes the listener. The machine appears in Settings → Machines.

## Rules

- Non-admin accounts: the route guard refuses before any token is created; the app shows the permission-denied state.
- `state` mismatch, missing token, or callback timeout: the app shows the failure and offers retry; nothing is stored.
- Machine identity resolution stays server-side: `(machine_id, sourceType)` with `(hostname, sourceType)` fallback — unchanged.
- Token revocation in Settings → Machines is detected on the next check (`401`/`403`) → the app shows "This machine must be reconnected." (US1 edge case).

## Error surface (app window)

| Condition | Message |
|-----------|---------|
| Non-admin sign-in | "Only the admin account can connect a machine." |
| Callback timeout | "Sign-in did not complete. Try again." |
| Token rejected later | "This machine must be reconnected." |

# Contract: Share Target Route

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02 · Spec: FR-007, FR-008, US2, Edge Cases

## Route

```ts
{ path: 'share', canActivate: [authGuard], component: ShareLandingComponent }
```

- Replaces `mobile/share`. Reached either from the OS share sheet (installed PWA → manifest `share_target` → `GET /share?title=…&text=…&url=…`) or by direct navigation.
- `authGuard` (not `roleGuard`): a signed-out share runs login first and returns with params intact — `authGuard` puts the full URL (query params included) into `returnUrl`, and `AuthService.login()` forwards it as the Keycloak redirect target (research D5).
- Legacy redirects (research D7): `/mobile/open?screen=settings-users` → `/settings/users`; `/mobile/open?screen=fna-briefing` → `/fna/briefing`; unknown/missing screen → `/`; `/mobile/callback` → `/login`. Implemented as componentless routes whose `canActivate` returns a `UrlTree`.

## Param resolution

| Param | Use |
|---|---|
| `title` | display only |
| `text` | display + "Ask LibBot" prefill (full text, never truncated) |
| `url` | preferred target URL |

Derived `targetUrl` = `url` param if present, else the **first** `http(s)://` substring in `text` (Android apps frequently embed the URL in `text`). Extraction uses a single anchored scan; no other heuristics.

## Role-filtered actions

| Action | Who | Effect |
|---|---|---|
| Ask LibBot | every approved user | navigates `/games/libbot?prefill=<full shared text>` |
| Save to FNA | admin only (existing `isAdmin` signal) | `POST /api/fna/articles/manual` with `{ url: targetUrl }` → navigates `/fna/articles` |

- "Save to FNA" is disabled (not hidden) when `targetUrl` is null and shows a one-line explanation; a submit failure shows a short error with a retry, and the payload stays on screen (never silently dropped).
- Empty state: all params absent/blank → calm empty state ("Nothing was shared") with one action (go to Library). No blank page.

## Statelessness

The share payload lives only in the URL. No service singleton, no in-memory signal, no storage — a killed browser tab drops the share exactly like the OS would (this *replaces* `ShareTargetService`'s in-memory signal, which existed only because the Capacitor event had no URL form).

## e2e coverage (`apps/jordylab-e2e/src/pwa.spec.ts`)

1. Guest `/share?text=…&url=…`: sees shared text, only "Ask LibBot" → lands in LibBot with prefill.
2. Admin same URL: also "Save to FNA" → article submit lands and navigates to Articles.
3. Signed-out `/share?text=…`: login round-trip → returns to `/share` with the text intact → Ask LibBot works.
4. Bare `/share`: calm empty state, no console errors.
5. URL inside `text` only: "Save to FNA" extracts it (admin case).

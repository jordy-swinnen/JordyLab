# Quickstart: Game Catalog Refinements — Validation Guide

**Date**: 2026-09-27
**Spec**: [spec.md](./spec.md) · **Plan](./plan.md) · [Data model](./data-model.md) · [Contract](./contracts/catalog-api.md)

Runnable scenarios that prove the 004 refinements end-to-end. Each maps to a spec story; run them in order after the migration lands. Local dev: `podman compose up -d` (Postgres + Keycloak), backend on `:8080`, host app `bunx nx serve jordylab` (`:4200`).

---

## Prerequisites

- Flyway migration `V20260927__gamecatalog_multihost_refinements.sql` applied (`game_installation` exists; `game` shows `cover_*`/`banner_*`/metadata columns; backfill row counts equal pre-migration `game` counts).
- At least one source synced with games (Steam source with a few known appids + one EmuDeck source), per the 002/003 quickstarts.
- A second host for the multi-host scenarios: run the downloaded scan client on another machine (or the same machine with an edited `machineId`/hostname, e.g. via `hostname` override in a sandbox) against the same backend.

---

## Story 1 — Card-fitted covers + wide detail banner

1. `GET /api/gamecatalog/games?search=<a Steam title>` → the summary shows `coverStatus: EXTERNAL_URL` and `coverUrl` matching `…/steam/apps/{appid}/library_600x900*.jpg` (portrait) — never `header.jpg`.
2. Open the grid in the app → Steam and ROM cards both show portrait art filling the card (no cropped-wide-header slivers; ROM box art unchanged).
3. `GET /api/gamecatalog/games/{id}` (Steam) → `bannerUrl` is a `library_hero.jpg` URL; (ROM) → a `Named_Snaps` URL.
4. Open detail pages → a wide banner renders at the top; it differs from the cover where both exist; games without banner art show the styled plate, never a stretched cover.
5. Re-apply a no-op sync (`POST /ingest/check` → `scanNeeded: false` path) → cover/banner statuses unchanged (no re-lookup churn).

## Story 2 — One game, many hosts (adoption, no full refresh)

1. Before the second host scans: note a shared Steam game's `id`, `enrichmentStatus`, `description`, `coverStatus`, `releaseYear`, and its `hosts[]` (one entry).
2. Run the scan client on the second host for the same library type.
3. `GET /api/gamecatalog/games?search=<that title>` → exactly **one** card; detail shows **two** `hosts[]` entries; `enrichmentStatus`, description, cover/metadata state are **byte-identical** to step 1 (no re-enrichment, no artwork reset, no full refresh).
4. Backend-level proof: `GET /api/gamecatalog/sources` → two sources (the two hosts); the game has two rows in `game_installation` — one per source — and one row in `game`.
5. Uninstall the game on host B only (delete + next scan): the game stays visible via host A; its `hosts[]` shrinks to one. Reinstall on B within 30 days → restored with all retained data, no regeneration.
6. Uninstall on **both** hosts → game disappears from the grid immediately; after the grace period + purge job (`purgeUninstalledGames`), the `game` row and any local artwork file are deleted (orphan cleanup).
7. ROM variant: the same ROM title on both hosts (different file paths) → one game, two installations (platform + normalized-title adoption).

## Story 3 — Host filter on the overview

1. `GET /api/gamecatalog/hosts` → the distinct hostnames (e.g. `["jordybox", "ryzen-desktop"]`).
2. Grid UI: a host filter chip row appears; selecting one host narrows the grid to that host's games; combining host + platform + search applies all three (AND).
3. `GET /api/gamecatalog/games?host=jordybox` vs `?host=other` → disjoint-installed sets; a game installed on both hosts appears in both lists (once per response).

## Story 4 — Richer deterministic detail data

1. Steam game detail (spec sheet): `genres`, `developer`, `publisher`, `releaseYear` present and matching Steam's store page for that appid (`metadataSource: "STEAM"`); values identical across repeat loads (persisted, not regenerated).
2. ROM game detail: the same fields present with `metadataSource: "AI"` once enrichment completes; bounds hold (genres ≤ 200 chars, year in range).
3. Kill network to `store.steampowered.com` (or WireMock a 500 in the module test): a Steam game's metadata fetch fails → detail page still loads, fields omitted, no error state, `metadataStatus` retries later — browsing never breaks.
4. Chat over the new fields: "which 90s platformers do I own?" / "what Nintendo-developed games do I have?" / "which games on jordybox support 2-player co-op?" → answers name only catalog games matching the structured fields; cited refs open the right detail pages.

## Story 5 — "Ask the catalog" with the game attached

1. On any detail page, the "Ask the catalog" button shows the spark icon (the four-pointed AI star, same shape as the briefing nav icon).
2. Click it → chat opens with the game attached: a removable attachment chip above the input naming the game.
3. Ask "is this good for 4 players on the couch?" → the answer draws on the attached game's multiplayer facts (and metadata), cites the attached game, and names no game outside the catalog.
4. Inspect the request (devtools): `POST /api/gamecatalog/chat` body carries `gameIds: ["<the game id>"]`.
5. Remove the chip → the next ask is a plain catalog-wide question (no `gameIds`), behaving exactly as before 004.
6. Negative cases: attach a game, then uninstall it elsewhere and ask → `400 GAME_IDS_INVALID` (or the unavailable state in UI), never a general-knowledge answer; attach 6 ids via API → `400`.

---

## Regression sweeps (touched scope only)

- Backend: `./gradlew :test --tests "*GameCatalog*"` + `ModularityTests` — reconciliation/adoption, purge, artwork slots, metadata service, chat filter + attachment, host-filtered queries, controller tests.
- Frontend: `bunx nx run-many -t test --projects=gamecatalog-api,gamecatalog-ui` + lint.
- Scan client: untouched — its existing suite stays green (`cd gamecatalog-scanner && python -m pytest`).
- Full end-to-end: one fresh scan from one host (`POST /ingest/check` → scan → grid) to confirm the 003 flow still works against the reshaped backend.

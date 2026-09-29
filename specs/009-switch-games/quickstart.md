# Quickstart: Nintendo Switch Games in the Catalog

End-to-end validation for feature 009. No database hand-seeding — use real data, Testcontainers fixtures, or the real
scan client.

## Prerequisites

- Backend builds: `./gradlew build -x test` (tests run separately)
- Frontend builds: `cd jordylab-fe && bun install` (already done if node_modules present)
- Local runtime: Podman (or Docker) + Compose, Bun, Java 25
- Copy `.env.example` → `.env` and set `POSTGRES_PASSWORD` and `KEYCLOAK_ADMIN_PASSWORD`
- IGDB credentials (`IGDB_CLIENT_ID`, `IGDB_CLIENT_SECRET`) in `.env` — needed for search scenarios; manual-add path
  works without them

## Start the local stack

```bash
# 1. Postgres + Keycloak
podman compose -f compose.yml up -d

# 2. Backend
./gradlew bootRun

# 3. Frontend (in a second terminal)
cd jordylab-fe
bunx nx serve jordylab   # http://localhost:4200
```

If you use Podman for Testcontainers tests, export:

```bash
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true
```

## Scenario 1: Add a Switch game by searching

1. Open `http://localhost:4200` and log in as the admin (`jordy`).
2. Navigate to **Game Catalog** → click **Add Switch game**.
3. Type `mario kart`.
4. Pick **Mario Kart 8 Deluxe**, choose **Physical**, save.
5. Verify:
   - Grid shows a new card with a portrait cover (or placeholder if IGDB has no cover), platform badge **Nintendo Switch**.
   - Detail page shows the banner, genres, developer, release year, local-multiplayer facts, AI description, and
     **Format: Physical**.
   - Host filter includes **Nintendo Switch**; selecting it shows the new game.
   - Platform filter **Nintendo Switch** shows the new game.

## Scenario 2: Add a game manually

1. Open **Add Switch game**.
2. Type a fictional or regional title that IGDB does not match (e.g. `Fictional RPG 99`).
3. Click **Add manually**, enter the exact title, choose **Digital**, save.
4. Verify the game appears with the styled placeholder cover.
5. Search again for the title, pick a real IGDB match, and click **Link to match**.
6. Verify metadata and artwork fill in without creating a second game.

## Scenario 3: Bulk paste

1. Open **Add Switch games** (bulk).
2. Paste a list of official titles, one per line, e.g.:
   ```text
   Mario Kart 8 Deluxe
   The Legend of Zelda: Breath of the Wild
   Not A Real Game Title
   ```
3. Submit and review:
   - First two lines show `MATCH` with cover and year.
   - Third line shows `NO_MATCH`.
4. Untick the third line, set the first two to **Physical**, confirm.
5. Verify the summary says **added: 2, skipped: 1**, and the two matched games appear in the grid.

## Scenario 4: Guest access

1. Open an incognito window and log in as a guest user.
2. Navigate to the Game Catalog.
3. Verify the Switch games are visible and filterable.
4. Verify there is **no** Add Switch game button and no edit/remove controls on the detail page.
5. (Optional) Attempt a direct `POST /api/gamecatalog/switch/games` — expect `403 Forbidden`.

## Scenario 5: Edit and remove

1. As admin, open a Switch game's detail page.
2. Change **Physical** → **Digital** and save. Verify the AI description did **not** regenerate.
3. Relink the game to a different IGDB match. Verify metadata/artwork refresh but the AI description still does not
   regenerate.
4. Click **Remove**, confirm. Verify the game disappears from the grid.

## Scenario 6: Scans/syncs/purges never touch manual Switch games

1. Ensure at least one Switch game is in the catalog.
2. Run a real scan from a configured source (e.g. download the scan client from Sources and run it against an existing
   library), or use the Testcontainers integration test that exercises `ReconciliationService.purgeUninstalledGames`.
3. Verify the Switch game still appears in the grid with the same `id`, title, format and artwork.

## Automated tests to run

```bash
# Backend: gamecatalog module tests
./gradlew :jordylab-be:test --tests 'dev.jordy.jordylab.gamecatalog.*'

# Frontend: gamecatalog libs
bunx nx test gamecatalog-api
bunx nx test gamecatalog-ui

# Spring Modulith boundary test
./gradlew :jordylab-be:test --tests '*ModularityTests*'
```

## Agent-browser UI smoke

Use `agent-browser` to drive the UI end-to-end:

```bash
bunx nx serve jordylab   # if not already running
agent-browser open http://localhost:4200
# log in through Keycloak (use fill + eval for form submit)
agent-browser open http://localhost:4200/games
# exercise Add Switch game dialog, search, save, filters, detail, bulk dialog
# capture screenshots under /tmp/jordylab-e2e/
```

Expected result: admin can add/search/bulk/remove Switch games; guests see games but cannot write; Switch games survive
scans.

## Cleanup

```bash
podman compose -f compose.yml down
```

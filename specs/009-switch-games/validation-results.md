# Validation Results: 009 Nintendo Switch Games

## Environment
- Backend: `SPRING_PROFILES_ACTIVE=local` + `POSTGRES_URL=jdbc:postgresql://localhost:5432/jordylab`
- Frontend: `bunx nx serve jordylab` on `http://localhost:4200`
- Identity: local Keycloak dev realm, user `jordy` / `nightlab-dev-2026`
- Date: 2026-09-29

## Automated Tests

| Suite | Command | Result |
|-------|---------|--------|
| Backend full test suite | `./gradlew test --no-daemon` | 549 tests, 0 failed |
| Backend gamecatalog tests | `./gradlew test --tests 'dev.jordy.jordylab.gamecatalog.*'` | Pass |
| Backend controller tests | `./gradlew test --tests 'dev.jordy.jordylab.*.rest.controller.*'` | Pass |
| Spring Modulith boundaries | `./gradlew test --tests '*ModularityTests*'` | Pass |
| Frontend gamecatalog-api | `bunx nx test gamecatalog-api` | Pass |
| Frontend gamecatalog-ui | `bunx nx test gamecatalog-ui` | Pass |

## End-to-End Validation (agent-browser)

| Scenario | Steps | Result |
|----------|-------|--------|
| Manual add | Navigate to `/games/switch` → fill title, select physical format, save | Game appears in grid as "Nintendo Switch / Installed / Owned" |
| IGDB search add | Open add dialog → search IGDB title → select result → save | Game appears in grid with IGDB-linked metadata |
| 007 regression | `GET /.well-known/assetlinks.json` returns 200; `GET /api/mobile/download/not-a-real-token` returns 403; `GET /api/mobile/releases/latest` requires auth | Pass |

## Known Blockers

- `./gradlew build` fails the JaCoCo coverage verification for `dev.jordy.jordylab.mobile.service` (0.4 vs required 0.8). This is unrelated to the Switch feature; it is a pre-existing coverage gap in the mobile module.

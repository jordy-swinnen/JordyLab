# JordyLab

Personal platform for financial intelligence, health/fitness tracking, game cataloging, and
recipe management — a modular monolith with a separate frontend and Python sidecar. See
`AGENTS.md` for the full architecture and module overview.

## Running locally

Three pieces run together: Postgres + Keycloak (via Docker Compose), the Spring Boot backend,
and the Angular frontend.

### Prerequisites

- Java 25, Gradle Wrapper (bundled — `./gradlew`)
- [Bun](https://bun.sh) (`bunx nx ...` runs everything frontend-side — never `npm`/`npx`/`yarn`)
- Docker + Docker Compose

### 1. Configure environment variables

Create `jordylab-be/.env` (git-ignored, never committed):

```bash
POSTGRES_USER=jordylab
POSTGRES_PASSWORD=<any local dev password>
KEYCLOAK_ADMIN=admin
KEYCLOAK_ADMIN_PASSWORD=<any local dev password>
GAMECATALOG_ARTWORK_DIR=/tmp/jordylab-artwork   # a real path — doesn't need to exist yet
ANTHROPIC_API_KEY=<your Anthropic API key>       # required for AI-backed features (FNA
                                                  # briefings, gamecatalog chat/enrichment)
```

These are local bootstrap credentials for the containers below — not production secrets.

### 2. Start Postgres + Keycloak

```bash
cd jordylab-be
docker compose up -d
```

This starts `pgvector/pgvector:pg16` on `localhost:5432` and Keycloak
(`quay.io/keycloak/keycloak`) on `localhost:8180`, with the `jordylab` realm auto-imported from
`compose/keycloak-realm-export.json`. A `jordy` dev user is seeded with the `jordylab-user` and
`gamecatalog-scanner` roles.

### 3. Start the backend

```bash
cd jordylab-be
./gradlew bootRun
```

Runs on `http://localhost:8080`, validating requests against the Keycloak realm from step 2.

### 4. Start the frontend

```bash
cd jordylab-fe
bun install
bunx nx serve jordylab            # host app, http://localhost:4200 — lazy-loads fna + gamecatalog
```

Or run a single domain standalone, without the host shell (still authenticates against the same
Keycloak realm — see `jordylab-fe/AGENTS.md`'s "Auth via Keycloak" section):

```bash
bunx nx serve fna                 # http://localhost:4300
bunx nx serve gamecatalog         # http://localhost:4400
```

### Ports at a glance

| Service | Port |
|---|---|
| Backend API | 8080 |
| Keycloak | 8180 |
| Postgres | 5432 |
| Frontend host (`jordylab`) | 4200 |
| `fna` standalone harness | 4300 |
| `gamecatalog` standalone harness | 4400 |

## Repository structure

See `AGENTS.md` for the module breakdown, AI routing, and infrastructure notes. Sub-project
conventions live in each subdirectory's own `AGENTS.md` (`jordylab-be`, `jordylab-fe`,
`garmin-sync-service`).

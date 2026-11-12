# JordyLab

Personal platform for financial intelligence, health/fitness tracking, game cataloging, and
recipe management — a modular monolith with a separate frontend and Python sidecar. See
`AGENTS.md` for the full architecture and module overview.

## Architecture

Production is one Docker Compose stack on a single OVH VPS, served at **https://jordylab.be**. Caddy terminates
TLS and is the only service that publishes ports. Local development runs the backend and Postgres with Podman
Compose (see below).

Operations: the procedures in [`docs/runbook.md`](docs/runbook.md) and the `jordylab-devops` agent
(`.claude/agents/`, `.opencode/agents/`). The stack is in [`deploy/compose`](deploy/compose/); the move from
Kubernetes is recorded in [`specs/020-compose-deployment`](specs/020-compose-deployment/).

What is built, what is being replaced and the end goal: [`docs/status.md`](docs/status.md). The AI development
workflow: [`docs/ai-workflow.md`](docs/ai-workflow.md).

<!-- Keep this drawing current. When a feature ships, is replaced or dropped, update it in the same change
     (rule: AGENTS.md, "Keeping the drawing and status current"). Edit the Mermaid text directly. -->


```mermaid
flowchart LR
    user(["Browser / installed PWA"])
    desktop(["Desktop sync app<br/>jordylab-desktop · Qt + Python<br/>on the owner's machines"])

    subgraph gh["GitHub Actions"]
        build["build.yml<br/>tests → images"] --> ghcr[("GHCR<br/>backend · frontend<br/>tags sha-&lt;commit&gt; and releases")]
        build -- "green main, AUTO_DEPLOY=true" --> deploy["deploy-prod-compose.yml<br/>(also run by hand)"]
    end

    subgraph vps["OVH VPS · Docker Compose"]
        caddy["Caddy<br/>TLS · only public ports 80, 443"]
        fe["frontend<br/>nginx · Angular"]
        be["backend<br/>Spring Boot 4 · Java 25"]
        ntfy["ntfy<br/>push notifications"]
        pg[("Postgres 16 + pgvector<br/>app data, identity, price history")]
        art[("artwork volume")]
        host["deploy-release.sh<br/>on the host"]
    end

    s3[("S3-compatible object store<br/>pgBackRest WAL + backups")]

    subgraph ext["External services"]
        ai["OpenRouter, Anthropic fallback<br/>AI calls"]
        games["Steam · IGDB · TheGamesDB"]
        yahoo["Yahoo Finance chart API<br/>quotes, FX, daily closes"]
        linear["Linear<br/>feedback → issue"]
        lf["Langfuse<br/>AI traces"]
    end

    user -- "HTTPS jordylab.be" --> caddy
    desktop -- "POST /api/gamecatalog/ingest" --> caddy
    desktop -- "connect page · downloads" --> caddy
    caddy -- "/" --> fe
    caddy -- "/api, /webauthn, /mcp, /oauth2" --> be
    caddy -- "/ntfy" --> ntfy
    be --> pg
    be --> art
    be -- "publish" --> ntfy
    be --> ai
    be --> games
    be -- "prices and history" --> yahoo
    be --> linear
    be --> lf
    pg -- "WAL + base backups" --> s3
    deploy -- "SSH as tag:ci over Tailscale" --> host
    ghcr -. "image pull" .-> fe
    ghcr -. "image pull" .-> be
```

## Running locally

Two local services run together with the apps: Postgres + pgvector (via Podman Compose) and the
Spring Boot backend; the Angular frontend talks to it with a normal session cookie. Authentication is
built into the backend (passwords + passkeys) — there is no identity container.

### Prerequisites

- Java 25, Gradle Wrapper (bundled — `./gradlew`)
- [Bun](https://bun.sh) (`bunx nx ...` runs everything frontend-side — never `npm`/`npx`/`yarn`)
- [Podman](https://podman.io) + Podman Compose (the compose files are runtime-agnostic, so
  Docker + Docker Compose work the same way if you prefer them)

### 1. Configure environment variables

Create `jordylab-be/.env` (git-ignored, never committed):

```bash
POSTGRES_USER=jordylab
POSTGRES_PASSWORD=<any local dev password>
GAMECATALOG_ARTWORK_DIR=/tmp/jordylab-artwork   # a real path — doesn't need to exist yet
ANTHROPIC_API_KEY=<your Anthropic API key>       # required for AI-backed features (FNA
                                                  # briefings, gamecatalog chat/enrichment)
```

These are local bootstrap credentials for the containers below — not production secrets.

### 2. Start Postgres

```bash
cd jordylab-be
podman compose up -d
```

This starts `pgvector/pgvector:pg16` on `localhost:5432`. The `identity` tables (accounts, passkeys,
sessions, machine tokens) are created by Flyway on the backend's first start; there is no identity
container to run.

### 3. Start the backend

```bash
cd jordylab-be
set -a && source .env && set +a
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

Runs on `http://localhost:8080` and authenticates against its own `identity` tables. On a fresh database
the `local` profile creates the admin **jordy** with the dev-only password
`local-dev-admin-password-not-for-prod` (change it in Account settings; it is never used in production).
The `local` profile is required: without it the backend stops immediately with
*"No valid Spring profile is active"*.

### 3b. Run the backend tests

Tests boot Postgres via Testcontainers, which needs to find the Podman machine socket:

```bash
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true   # ryuk needs privileges the Podman socket may not grant
cd jordylab-be && ./gradlew build
```

Give the Podman machine at least 6 GiB of memory (`podman machine stop && podman machine set --memory 6144 &&
podman machine start`): with the default 2 GiB, the later test classes that start their own Postgres
containers fail with `localhost:2375 failed to respond`.

### 4. Start the frontend

```bash
cd jordylab-fe
bun install
bunx nx serve jordylab            # host app, http://localhost:4200 — lazy-loads fna + gamecatalog
```

Or run a single domain standalone, without the host shell (it uses the same session authentication):

```bash
bunx nx serve fna                 # http://localhost:4300
bunx nx serve gamecatalog         # http://localhost:4400
```

### 5. Your own local account

Register at http://localhost:4200. New accounts are `PENDING` and land on "awaiting approval". Sign in as
**jordy** and approve the new account in Settings → Users (approval grants the `guest` role and Game Catalog
access). There is only one admin, so roles are not edited from the panel.

Forgot the local password? Use the admin-approved reset on `/forgot-password`, or the break-glass procedure in
`docs/runbook.md` §24. The scan client uses a per-machine token from Settings → Machines, not a password.

### Ports at a glance

| Service | Port |
|---|---|
| Backend API | 8080 |
| Postgres | 5432 |
| Frontend host (`jordylab`) | 4200 |
| `fna` standalone harness | 4300 |
| `gamecatalog` standalone harness | 4400 |

## Repository structure

See `AGENTS.md` for the module breakdown, AI routing, and infrastructure notes. Sub-project
conventions live in each subdirectory's own `AGENTS.md` (`jordylab-be`, `jordylab-fe`,
`garmin-sync-service`).

## Licence

This repository is public so the code can be read, but it isn't open source: all rights are
reserved. You're welcome to look around, but copying, modifying, or reusing any part of it
requires my permission first. See [`LICENSE`](LICENSE) for the exact terms, and get in touch with
me (Jordy Swinnen) if you'd like to use something here.

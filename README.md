# JordyLab

Personal platform for financial intelligence, health/fitness tracking, game cataloging, and
recipe management — a modular monolith with a separate frontend and Python sidecar. See
`AGENTS.md` for the full architecture and module overview.

## Architecture

Production runs on a single OVH VPS with k3s at **https://jordylab.be**. Local development runs the same
pieces with Podman Compose (see below). How production was built: [
`docs/runbooks/vps-k3s-deployment`](docs/runbooks/vps-k3s-deployment/README.md);
day-to-day operations: the `jordylab-devops` agent (`.claude/agents/`, `.opencode/agents/`).

```mermaid
flowchart TB
    user(["Browser / Android app"])
    dev(["Jordy's Mac<br/>kubectl · helm · sops"])

    subgraph gh["GitHub"]
        repo["Repo + PR"] --> build["Build workflow<br/>tests → images"]
        build --> ghcr[("GHCR<br/>backend · frontend · keycloak<br/>tag sha-&lt;commit&gt;")]
        build --> deploy["Deploy workflow<br/>env: production<br/>(manual approval)"]
    end

    subgraph ts["Tailscale (private admin network)"]
        tsnote["only port 6443 for CI"]
    end

    subgraph vps["OVH VPS · Ubuntu LTS · ufw: 22/80/443 only"]
        subgraph k3s["k3s (single-node Kubernetes)"]
            api["k3s API :6443"]
            subgraph ks["kube-system / add-ons"]
                traefik["Traefik<br/>Gateway API"]
                certm["cert-manager<br/>Let's Encrypt"]
                cnpgop["CloudNativePG operator<br/>+ Barman Cloud plugin"]
            end
            subgraph ns["namespace jordylab"]
                fe["frontend<br/>nginx · Angular"]
                be["backend<br/>Spring Boot"]
                kc["Keycloak<br/>/auth"]
                ntfy["ntfy<br/>/ntfy"]
                pg[("PostgreSQL 16<br/>app DB + keycloak schema")]
                pvc[("PVCs<br/>artwork · APKs")]
            end
        end
    end

    s3[("OVH Object Storage<br/>Gravelines · backups")]

    user -- "HTTPS jordylab.be" --> traefik
    traefik -- "/" --> fe
    traefik -- "/api" --> be
    traefik -- "/auth/realms …" --> kc
    traefik -- "/ntfy" --> ntfy
    certm -. "TLS cert" .-> traefik
    be --> pg
    be --> pvc
    be -- "token validation" --> kc
    be -- "publish" --> ntfy
    kc --> pg
    cnpgop -. "manages" .-> pg
    pg -- "WAL + daily/weekly base backups" --> s3

    deploy -- "joins as tag:ci" --> ts
    dev --> ts
    ts --> api
    ghcr -. "image pull" .-> k3s
```

## Running locally

Three pieces run together: Postgres + Keycloak (via Podman Compose), the Spring Boot backend,
and the Angular frontend.

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
podman compose up -d
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

### 3b. Run the backend tests

Tests boot Postgres via Testcontainers, which needs to find the Podman machine socket:

```bash
export DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}')
export TESTCONTAINERS_RYUK_DISABLED=true   # ryuk needs privileges the Podman socket may not grant
cd jordylab-be && ./gradlew build
```

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

## Licence

This repository is public so the code can be read, but it isn't open source: all rights are
reserved. You're welcome to look around, but copying, modifying, or reusing any part of it
requires my permission first. See [`LICENSE`](LICENSE) for the exact terms, and get in touch with
me (Jordy Swinnen) if you'd like to use something here.

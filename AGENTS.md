# JordyLab

Personal platform for financial intelligence, health/fitness tracking, game cataloging, and recipe management — built as a modular monolith with separate frontend and Python sidecar.

## Repository Structure

```
AGENTS.md                       ← you are here (root)
jordylab-be/                    ← Spring Boot 4 monolith (Java 25, Gradle Kotlin DSL) — Keycloak JWT auth, gamecatalog ingest endpoint
jordylab-fe/                    ← Nx Angular 21 monorepo (Bun, spartan/ui) — host shell + per-domain deployable remote apps (fna, gamecatalog) wired via @softarc/native-federation; future remotes (recipe, garmin, trading) follow the same pattern
garmin-sync-service/            ← Python 3.12 sidecar (Garmin Connect sync, direct DB writes)
```

Sub-project conventions live in `<subdir>/AGENTS.md` (jordylab-be, jordylab-fe, garmin-sync-service) — loaded automatically when working in each subdirectory.

## Modules

| Module | Schema | Purpose |
|--------|--------|---------|
| `fna` | `finance` | Financial news aggregation, RSS ingestion, AI investment briefings |
| `gamecatalog` | `gamecatalog` | ROM/Steam game catalog — ingestion, grid/detail, AI enrichment, grounded chat, source management (**built**) |
| `garmin` | `garmin` | Health/fitness data from Garmin Connect (written by Python sidecar) |
| `recipe` | `recipe` | Self-hosted recipe management (planned) |
| `trading` | — | AI trade signals with mandatory human-in-the-loop approval |
| `shared` | — | Cross-module AI layer, config, utilities |

## Architecture Principles

- **Monolith-first** — do not extract a microservice unless there is a concrete, demonstrated need
- Module root package = public API (facade + DTOs); sub-packages = internal — never import internals across modules
- All AI calls route through the shared `ResilientAiService` — never instantiate `ChatClient` directly
- Human-in-the-loop for trading: every `TradeOrder` requires explicit approval before execution
- Each module owns its own Flyway schema with `CREATE SCHEMA IF NOT EXISTS`
- `garmin-sync-service` writes to the `garmin` schema but does NOT own DDL — Flyway in `jordylab-be` owns all migrations
- Game library scans come from a downloaded shell script run on the host, not from a sidecar. The script authenticates to Keycloak via Device Authorization Grant, reads the system hostname, walks the chosen library, and POSTs a directory listing to `/api/gamecatalog/ingest/scan`. Sources are auto-registered as `(hostname, libraryType)`. The `jordy-scan-<library>.sh` template is committed under `jordylab-be/src/main/resources/scripts/` and is generated on demand by `ScriptService`

## Workflow

- After implementing a feature or fix, immediately run relevant tests to verify only the changed code works — no full test suite runs unless explicitly requested.
- After completing a plan or task, always test the end-to-end flow of the features built or changed. Test only the scope that was touched — avoid full-suite integration tests unless the change warrants it.

## AI Routing

Per-module provider selection via `ResilientAiService` with health-check-and-cache pattern. MVP1 wires one provider (Anthropic) across the modules that need AI; local inference (Ollama) is deferred to a separate feature. The table below describes the target architecture — `fna` and `gamecatalog` are wired today.

| Module | Provider | Model | Rationale | MVP1 Status |
|--------|----------|-------|-----------|-------------|
| `fna` | Anthropic | Claude Sonnet | Financial analysis needs quality | **Wired** |
| `gamecatalog` | Anthropic | Claude Sonnet | Structured JSON + grounded chat share provider for prompt consistency | **Wired** |
| `recipe` | Ollama | Llama 3.1 8B | Cost-effective for structured tasks | Deferred |

## Infrastructure

- **Hetzner VPS**: Docker Compose stack (Spring Boot, PostgreSQL 16 + pgvector, Traefik, Watchtower, Keycloak)
- **Main desktop**: Ryzen 9 7950X, RX 7900 XTX — Ollama inference host, `0.0.0.0:11434` (LAN only)
- **JordyBox**: i7-9700K, RTX 2070 Super — HTPC/gaming, NFS server for ROMs (where downloaded scan scripts walk the libraries)
- WireGuard connects VPS to home LAN for Ollama access
- **Keycloak** lives in compose (port 8180 in dev). Stores its tables in the shared pgvector container under a dedicated `keycloak` schema. Single `jordylab` realm with two public clients (`jordylab-host` for the web UI, `gamecatalog-script` for device-code login) and two roles (`jordylab-user`, `gamecatalog-scanner`). The dev realm is auto-imported from `jordylab-be/compose/keycloak-realm-export.json` on first boot. `KC_HOSTNAME` and the issuer-uri in `application.yaml` must match the public hostname the browser sees (matters behind Traefik in prod)

## Reference Docs

Read these on-demand when working on related tasks — do not load all at once.

| Doc | Read when... |
|-----|-------------|
| `jordylab-infrastructure-guide.md` | Working on NFS mounts, Ollama config, Docker networking, or AI fallback |
| `jordylab-project-setup.md` | Scaffolding new modules, adding dependencies, or configuring build tools |
| `jordylab-project-overview.md` | Needing full context on project goals, monetization angles, or tech decisions |

Other reference docs (infrastructure guide, project setup, project overview) are planned but not
yet written — do not cite them as if they exist. Add a row here only once the file is actually
present in the repo.

## Shared Gotchas

- Spring Boot 4 Flyway: need `spring-boot-starter-flyway` explicitly, not just `flyway-core`
- Ollama on main desktop uses ROCm (AMD GPU), not CUDA — applies when local inference is wired
- `ResilientAiService` health check only verifies Ollama is running, not that a model is loaded in VRAM — applies when local inference is wired
- Docker containers need the desktop's LAN IP for Ollama — verify with `docker exec jordylab curl http://<desktop-ip>:11434/api/tags` (applies when local inference is wired)
- NFS mount to JordyBox uses `soft,timeo=50,retrans=3` — operations fail after ~15s when JordyBox is off

## Secrets

- **Never echo, log, or print secret values** — `ANTHROPIC_API_KEY`, `KEYCLOAK_ADMIN_PASSWORD`, `KEYCLOAK_BOOTSTRAP_*`, `POSTGRES_PASSWORD`, refresh tokens from `~/.config/jordylab/scan/token.json`, and any other key/token/password. This applies to chat output, file contents, diffs, screenshots, and code. When asked to "show" a `.env` or a key, redact with `<redacted>` or show only the variable name. Verify secrets work by behavior (does the call return 200?), not by reading the value.
- The old `GAMECATALOG_INGEST_TOKEN` static bearer token is deprecated. Backend auth is now Keycloak JWT validated by Spring Security's OAuth2 resource server. Scan scripts cache the access+refresh token in `~/.config/jordylab/scan/token.json` (mode 0600) and refresh on subsequent runs.

# JordyLab

Personal platform for financial intelligence, health/fitness tracking, game cataloging, and recipe management — built as a modular monolith with separate frontend and Python sidecar.

## Repository Structure

```
AGENTS.md                  ← you are here (root)
jordylab-be/               ← Spring Boot 4 monolith (Java 25, Gradle Kotlin DSL)
jordylab-fe/               ← Nx Angular 21 monorepo (Bun, spartan/ui)
garmin-sync-service/       ← Python 3.12 sidecar (Garmin Connect sync)
```

Sub-project conventions live in `<subdir>/AGENTS.md` (jordylab-be, jordylab-fe, garmin-sync-service) — loaded automatically when working in each subdirectory.

## Modules

| Module | Schema | Purpose |
|--------|--------|---------|
| `fna` | `finance` | Financial news aggregation, RSS ingestion, AI investment briefings |
| `gamecatalog` | `gamecatalog` | ROM/Steam game catalog with pgvector semantic search |
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

## AI Routing

Per-module provider selection via `ResilientAiService` with health-check-and-cache pattern. MVP1 wires one provider (Anthropic) for the `fna` module; local inference (Ollama) is deferred to a separate feature. The table below describes the target architecture — only `fna` is in scope for MVP1.

| Module | Provider | Model | Rationale | MVP1 Status |
|--------|----------|-------|-----------|-------------|
| `fna` | Anthropic | Claude Sonnet | Financial analysis needs quality | **Wired** |
| `gamecatalog` | Ollama | Mistral 7B | Descriptions are fine locally | Deferred |
| `recipe` | Ollama | Llama 3.1 8B | Cost-effective for structured tasks | Deferred |

## Infrastructure

- **Hetzner VPS**: Docker Compose stack (Spring Boot, PostgreSQL 16 + pgvector, Traefik, Watchtower)
- **Main desktop**: Ryzen 9 7950X, RX 7900 XTX — Ollama inference host, `0.0.0.0:11434` (LAN only)
- **JordyBox**: i7-9700K, RTX 2070 Super — HTPC/gaming, NFS server for ROMs
- WireGuard connects VPS to home LAN for Ollama access

## Reference Docs

Read these on-demand when working on related tasks — do not load all at once.

| Doc | Read when... |
|-----|-------------|
| `coding-master-prompt.md` | Writing or reviewing any code (Java or Angular conventions) |

Other reference docs (infrastructure guide, project setup, project overview) are planned but not
yet written — do not cite them as if they exist. Add a row here only once the file is actually
present in the repo.

## Shared Gotchas

- Spring Boot 4 Flyway: need `spring-boot-starter-flyway` explicitly, not just `flyway-core`
- Ollama on main desktop uses ROCm (AMD GPU), not CUDA — applies when local inference is wired
- `ResilientAiService` health check only verifies Ollama is running, not that a model is loaded in VRAM — applies when local inference is wired
- Docker containers need the desktop's LAN IP for Ollama — verify with `docker exec jordylab curl http://<desktop-ip>:11434/api/tags` (applies when local inference is wired)
- NFS mount to JordyBox uses `soft,timeo=50,retrans=3` — operations fail after ~15s when JordyBox is off

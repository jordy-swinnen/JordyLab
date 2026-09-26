# Phase 0 Research: Game Catalog

**Date**: 2026-08-02
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

---

## R1: Agent implementation language and repo placement

**Decision**: New top-level Python 3.12 project `gamecatalog-sync-service/`, a sibling of `garmin-sync-service`, adopting its conventions wholesale (src/ layout, pydantic models, `pathlib`, `logging`, ruff, pytest + pytest-mock).

**Rationale**: The agent must read local filesystem state on JordyBox (Steam VDF manifests, ROM folders) and make outbound HTTPS calls. Python is the established sidecar language in this repo, and `garmin-sync-service/AGENTS.md` is an explicit conventions template for outbound data pumps even though it contains no code. One deliberate deviation from the Garmin model: this agent **pushes snapshots over HTTPS to `jordylab-be`** instead of writing to PostgreSQL directly — spec FR-003 (server-side validation before any write) forbids direct DB writes, and the server owns all `gamecatalog` schema writes.

**Alternatives considered**:
- *Direct-to-Postgres pump (garmin model)*: Rejected — violates FR-003; no server-side validation/sanitization possible on that path.
- *Shell script + curl*: Rejected — Steam VDF parsing, multi-disc grouping, and title normalization need a real language and tests.
- *Extend `garmin-sync-service`*: Rejected — different machine (JordyBox vs wherever Garmin runs), different domain, different write path; monorepo-sibling keeps services single-purpose.

---

## R2: Steam library detection

**Decision**: Parse Valve's VDF files read-only: `<steam>/steamapps/libraryfolders.vdf` enumerates all library folders; each library's `steamapps/appmanifest_<appid>.acf` yields `appid`, `name`, and install state. Use the `vdf` PyPI package for parsing.

**Rationale**: This is how Steam itself records installed games — no Steam API key, no network, no inbound anything; it reads files the agent already has access to. The `appmanifest_*.acf` files exist only for actually-installed games, which maps directly onto the spec's "reflect reality" constraint. The `vdf` package is the mature, de-facto parser for this format (both VDF text variants used by these files).

**Alternatives considered**:
- *Steam Web API (GetOwnedGames)*: Rejected — reports owned, not installed, games; needs an API key and leaks the catalog to a third party; breaks the local-truth constraint.
- *Hand-rolled VDF parser*: Rejected — format is simple but has escaping edge cases; `vdf` is a small, stable, test-covered dependency.
- *SteamCMD / Steam client query*: Rejected — requires the client running and IPC; fragile and over-scoped.

---

## R3: Agent run model — one-shot CLI + OS scheduler

**Decision**: The agent is a one-shot CLI (`python -m gamecatalog_sync sync`) invoked hourly by cron/systemd timer on JordyBox. Per run: load local config → pull enabled-state config from the server (authenticated GET) → scan enabled sources → submit one snapshot per source → upload any requested fallback artwork → persist sequence state. No daemon, no listener, no inbound ports.

**Rationale**: A one-shot process is the simplest thing that satisfies FR-001 (outbound-only, scheduled). There is nothing to keep alive, no scheduler library to operate, no long-lived credential process, and the OS timer already provides retries-by-next-tick — mirroring the backend's "fail and wait for next cron tick" philosophy from spec 001. State that must survive between runs (per-source sequence numbers) lives in a JSON state file.

**Alternatives considered**:
- *Long-running daemon with internal scheduler (APScheduler)*: Rejected — adds a process to supervise, a crash/restart story, and Windows/Linux service differences for zero benefit at hourly cadence.
- *Server-triggered sync*: Rejected — requires inbound connectivity to JordyBox, violating the hard constraint.

---

## R4: Ingestion authentication — scoped bearer filter, no Spring Security starter

**Decision**: A custom `OncePerRequestFilter` (`IngestAuthFilter`) registered to intercept only `/api/gamecatalog/ingest/**`. It extracts the `Authorization: Bearer <token>` header and compares it against `jordylab.gamecatalog.ingest.token` (env-provided) using constant-time comparison (`MessageDigest.isEqual`). Missing/invalid → `401` with empty body; no filter effect on any other endpoint. Unconfigured token (blank) → filter rejects all ingest traffic with `503` (fail-closed).

**Rationale**: The application today has zero authentication and a single user; the only new attack surface is the ingestion write path. The smallest correct control is a filter scoped exactly to that path. Introducing `spring-boot-starter-security` pulls in a full filter chain that by default changes behavior of *every* existing endpoint — real regression risk on a working app for no additional protection of the ingest path. Constant-time comparison avoids timing oracles; the token is provisioned once manually (spec Assumptions).

**Alternatives considered**:
- *Full Spring Security with SecurityFilterChain*: Rejected for v1 — heavier, default-deny side effects on all existing open endpoints, and no other endpoint needs authz yet. Revisit when the platform gains real auth (multi-user is explicitly out of scope).
- *mTLS client certificates*: Rejected — certificate issuance/renewal machinery on an HTPC for a single agent is disproportionate.
- *HMAC-signed payloads*: Rejected — defends against tampering TLS already prevents; adds clock-sync requirements. Bearer + TLS + server-side validation is sufficient for the threat model (FR-002/FR-003 treat the payload as untrusted regardless).

---

## R5: Chat grounding — two-call structured query, pgvector deferred

**Decision**: Chat answers are produced by a two-call pattern over structured data:
1. **Translate**: `ResilientAiService.call("gamecatalog", ...)` with a strict-JSON system prompt converts the question into a bounded filter object `{ titleSearch?, platforms[]?, genre?, minLocalPlayers?, onlineMultiplayer?, singlePlayer? }`. The JSON is parsed and validated (allow-listed fields, value bounds, platform names validated against known platforms) — invalid output becomes a named `ChatTranslationFailed` error, never executed blindly.
2. **Answer**: the filter runs as a parameterized JPA query (indexes on platform/genre/local players); the result rows (capped, e.g. 50) are sent to a second AI call to compose the natural-language answer. The API response carries the **actual DB rows** as `games[]` citations — the model's prose never determines which games the user sees.

pgvector semantic search is **not built in v1**: the pgvector columns/migration, `VectorStore` wiring, and an embedding provider are all absent, and no embedding provider is currently wired (Anthropic has no embeddings in Spring AI; Ollama embeddings need the desktop host, which the routing table defers).

**Rationale**: FR-020 mandates that player-count/multiplayer questions be answered from structured facts, not prose — a structured query *is* the grounding mechanism, deterministic and testable (SC-005). The catalog's answerable question space (membership, genre, platform, player counts) maps completely onto the structured fields from clarification Q1. The two-call pattern keeps the model away from the database: it can only produce a filter, and only allow-listed filters can execute.

**Alternatives considered**:
- *pgvector RAG over descriptions*: Deferred — needs an embedding provider and pipeline that don't exist yet; prose embeddings are weaker than structured fields for exact player-count questions; root `AGENTS.md`'s "pgvector semantic search" remains the stated long-term direction and can be added as an enhancement without changing the chat contract.
- *Stuff the whole catalog into one prompt*: Rejected — 5,000 games exceed sensible prompt size, cost per question is high, and citations would come from model prose (unverifiable).
- *Single call returning answer text*: Rejected — hallucinated game names would violate FR-020/SC-005 with no structural defense.

---

## R6: AI provider for the gamecatalog module — Anthropic (existing wiring)

**Decision**: `gamecatalog` AI calls (enrichment + chat) route through the existing `ResilientAiService` with a new config entry `jordylab.ai.modules.gamecatalog.{provider: anthropic, model: claude-sonnet-*}`. No provider-dispatch refactor; Ollama remains deferred per the root routing table.

**Rationale**: `ResilientAiService` currently invokes `AnthropicChatModel` regardless of the configured provider string (provider is used for health-cache keys/attribution only). Wiring Ollama would require (a) a provider-dispatch refactor and (b) LAN/WireGuard access from the VPS to the desktop's Ollama with the ROCm/model-loaded caveats documented in root `AGENTS.md` — both out of scope for this feature. The per-module config design means switching later is config-only once dispatch exists. This matches the precedent set by spec 001 (MVP1 wires Anthropic only).

**Alternatives considered**:
- *Refactor `ResilientAiService` for Ollama dispatch now*: Rejected — doubles the feature's backend scope on infrastructure (ROCm desktop, WireGuard) that the routing table explicitly defers.
- *Separate gamecatalog-specific AI client*: Rejected — violates the "all AI through ResilientAiService" architecture principle.

---

## R7: Artwork sourcing — Steam CDN + libretro-thumbnails, agent upload as fallback

**Decision**: Hybrid, server-first (clarification Q2=C):
- **Steam**: deterministic CDN URL `https://cdn.cloudflare.steamstatic.com/steam/apps/{appid}/header.jpg` — stored as `EXTERNAL_URL`, no fetch or storage needed; the browser loads it directly.
- **ROMs**: server probes `libretro-thumbnails` per-system GitHub repos (`Named_Boxarts/<Title>.png`, title = normalized No-Intro name with `&`→`_` and filesystem-unsafe chars mapped per libretro convention) via `ArtworkLookupClient` (HEAD request, bounded timeout, system→repo mapping table). Hit → `EXTERNAL_URL`.
- **Fallback**: if the server finds nothing, the game's artwork status becomes `LOCAL_FALLBACK_REQUESTED`; the next sync response lists those games and the agent uploads its local EmuDeck/ES-DE `downloaded_media` image (if present) via authenticated POST; stored on the server filesystem (Docker volume) and served from `/api/gamecatalog/games/{id}/artwork` with `X-Content-Type-Options: nosniff`.
- **Nothing found**: `PLACEHOLDER`, frontend renders a neutral placeholder (FR-013).

Upload validation: magic-byte sniffing (JPEG/PNG only), 2 MB size cap, content-length enforcement. No re-encoding in v1 (single-user, server-rendered with nosniff).

**Rationale**: libretro-thumbnails is free, keyless, and its `Named_Boxarts` naming follows the same No-Intro convention EmuDeck ROMs typically use — so normalized titles match with a documented character-mapping rule. Steam CDN needs no key and no storage. The agent-upload fallback captures the already-curated ES-DE scraped media for the long tail (hacks, translations) that libretro misses, exactly as decided in clarify Q2.

**Alternatives considered**:
- *ScreenScraper.fr / TheGamesDB / IGDB*: Rejected for v1 — all need credentials and rate-limit management; libretro covers the EmuDeck naming convention without accounts.
- *Agent uploads all artwork in every snapshot*: Rejected — bloats payloads (thousands of images), duplicates what CDNs serve better; fallback-only keeps submissions small (clarify Q2 rationale).
- *Store external artwork bytes server-side*: Rejected for v1 — URL references are sufficient; CDN rot is acceptable at this scale (status can be re-resolved).

---

## R8: Snapshot identity, idempotency, and out-of-order protection

**Decision**: Per-source monotonic **sequence number**, generated and persisted agent-side (JSON state file), incremented per scan run. Each snapshot payload also carries a SHA-256 **payload hash** over the canonical game list. Server stores `lastSequence` + `lastPayloadHash` per source and applies:
- `sequence < lastSequence` → `OUT_OF_ORDER`: ignore entirely, respond 200 with outcome (FR-006 — never regress).
- `sequence == lastSequence && hash == lastPayloadHash` → `NO_CHANGE`: idempotent no-op (FR-006).
- otherwise → apply reconciliation, record `APPLIED`.
- `scanFailed: true` payloads → `SCAN_FAILED` report recorded, **zero reconciliation** (FR-007).

Reconciliation within an applied snapshot: match by `(source, externalRef)` — present in payload → upsert + `lastSeenAt` + `INSTALLED` (restoring grace-hidden entries with enrichment intact); in DB but missing from payload → `UNINSTALLED` + `uninstalledAt`; daily purge job deletes `UNINSTALLED` rows older than 30 days (clarify Q1=B).

**Rationale**: Agent-side sequences survive server clock skew and agent retries (the agent owns ordering of its own scans; a retry of run N is detectably stale after run N+1 lands). The hash distinguishes "same sequence re-sent" from "genuinely new run", giving cheap idempotency. Matching by externalRef keeps the model simple — ROM renames are explicitly accepted as remove+add (spec Edge Cases).

**Alternatives considered**:
- *Timestamp-based ordering*: Rejected — agent clock skew (HTPC suspend/resume, dual boot) makes wall-clock ordering unreliable; sequence numbers are monotone by construction.
- *Server-generated snapshot IDs with client echo*: Rejected — couples runs together across failures; the agent can submit out of band after offline periods without a prior handshake.
- *Content-hash game identity (survives renames)*: Rejected per spec Assumptions — file-location identity is explicit v1 scope; hashing multi-GB ROMs is also I/O-expensive on an HTPC.

---

## R9: Submission shape — one snapshot per source, per-entry validation

**Decision**: The agent submits **one HTTP request per source per run** (not one monolithic multi-source payload). Payload: source metadata + sequence + games array (max 10,000 entries, 1 MB JSON body cap). Validation is two-layer: (1) structural — bean validation on the request records (required fields, types, length caps: title ≤ 200 chars, externalRef ≤ 500, platform ≤ 50, enum membership); (2) sanitization — `TextSanitizer` strips control characters and markup from titles before persistence. An entry failing either layer is rejected with a per-entry reason in the response; valid entries proceed (FR-004). An unparseable/oversized body → whole request `REJECTED`, nothing written.

**Rationale**: Per-source submissions align exactly with the reconciliation unit (a snapshot is authoritative *per source*), isolate scan failures to their source, and keep payloads comfortably within bounds even for large ROM collections. Per-entry rejection with reasons satisfies "invalid entries rejected with explicit, countable errors while valid entries are processed" (FR-004, SC-003).

**Alternatives considered**:
- *Single multi-source payload per run*: Rejected — one source's scan failure would complicate the atomicity story of all others; larger bodies; reconciliation ordering across sources for no benefit.
- *All-or-nothing per payload*: Rejected — one corrupt ROM filename shouldn't block a 4,999-game sync (SC-003 explicitly wants mixed-validity handling).
- *Reject entries containing markup instead of sanitizing*: Partially adopted — structural violations (wrong type/length) reject; markup/control-chars in otherwise valid titles are sanitized (FR-003 requires neutralization, and rejecting legitimately weird ROM names would lose real games).

---

## R10: Backend module shape — no facade, module root for properties only

**Decision**: `gamecatalog` module root contains only `GameCatalogProperties` (`@ConfigurationProperties`, prefix `jordylab.gamecatalog`: ingest token, artwork storage dir, external-artwork toggles/timeouts, purge grace days, enrichment batch size). No `GameCatalogFacade` — no other module consumes gamecatalog in v1, matching the de-facto `fna` pattern (which also has no facade). Internal packages follow the fixed layout (`domain/`, `rest/`, `service/`, `util/`); `ModularityTests` verifies boundaries automatically.

**Rationale**: `jordylab-be/AGENTS.md` prescribes a facade for cross-module APIs and says "if a new sub-package feels necessary, stop and ask" — the layout above uses only the sanctioned sub-packages. A facade with zero consumers is dead code (YAGNI); introducing it later, when a second module needs catalog data, is a small additive change.

**Alternatives considered**:
- *Introduce the first real facade now*: Rejected — no consumer exists; spec 001 already accepted the fna no-facade reality.
- *Module config in `shared/config`*: Rejected — module-specific properties belong to the module; `shared/config` holds platform-wide beans only.

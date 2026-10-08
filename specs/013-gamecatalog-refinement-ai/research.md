# Research: Game Catalog Refinement and LibBot (AI) Rebuild

**Spec**: [spec.md](./spec.md) | **Date**: 2026-10-07

Every decision below is checked against the code in this repository (paths given) and, where marked, against external
documentation. Anything not verifiable from here is listed as **open** with the step that settles it. No unresolved clarification marker remains.

Inputs read: `docs/research/spring-ai-architecture.md`, `docs/research/spring-ai-gap-analysis.md`,
`jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`, `jordylab-be/AGENTS.md`, `jordylab-fe/AGENTS.md`, the
`gamecatalog`, `shared/ai`, `settings` code, the migrations, `deploy/k8s/cluster/cnpg-cluster.yaml`, and the Night Lab tokens in
`jordylab-fe/tailwind.theme.js`.

---

## Part A: LibBot (AI)

### A1. Pipeline shape: fixed workflow, not an agent

**Decision**: LibBot is a **four-step fixed workflow** with a deterministic seam between model and code:

1. **Interpret** (model call 1, typed output `QuestionInterpretation`): intent, language, extracted *facts* (party size,
   playing alone / online, named hosts/consoles/platforms, genres, years, install status, mark scope, a semantic query),
   follow-up resolution against the conversation, an optional ready-made reply (decline / clarifying question).
2. **Derive** (plain Java): business rules turn facts into constraints. Party size N ≥ 2 → `minLocalPlayers = N` and
   `localMultiplayer = true`; "alone" → `singlePlayer = true`; "online with friends" → `onlineMultiplayer = true`. The
   "applied constraints" sentence is produced here, from a template per language (en/nl). The model never writes it.
3. **Retrieve** (SQL + pgvector, no model): hybrid candidate search (A5), tri-state handling of unknown data, vote ranking.
4. **Compose** (model call 2, typed output `LibBotAnswer`): answer text + the ids of the games it recommends, from the
   candidate list only. References are validated in code (A6).

**Rationale**: the gap analysis ranks "workflows before agents" and "typed outputs with validation" first
(`spring-ai-architecture.md` §3.1, §7.4). Putting the rules ("six people means six local players, local only") in Java makes the
headline behaviour unit-testable without a model and removes the main source of the old failures. Decline and clarify paths cost
one call instead of two.

**Alternatives rejected**: (a) a tool-calling agent: the research defers tools until a feature needs them, adds a loop to
bound and an authorisation surface, and the problem is a fixed pipeline; (b) one giant prompt over the whole catalog: ~230 games
today, thousands tomorrow, and it makes "no invented games" unverifiable; (c) keeping translate-then-answer: that is the design
that failed (empty filter → 50 cited rows).

### A2. Extend `ResilientAiService`, do not bypass it

Today `ResilientAiService.call(AiFeature, String system, String user)` is a single system + user string pair returning text
(`shared/ai/ResilientAiService.java`). The rules forbid a second path (`shared/ai/AGENTS.md`), so the single path grows:

- `call(AiFeature, List<Message>)`: system, history, user. The existing overload delegates to it.
- `callStructured(AiFeature, List<Message>, Class<T>)`: appends the schema instructions from Spring AI's `BeanOutputConverter`,
  parses, validates with Jakarta Validation, makes **one repair attempt** that quotes the validation error, then returns a failed
  result (reason `INVALID_OUTPUT`) which triggers the existing provider fallback like any other failure. Provider-native
  structured output stays deferred (gap analysis #5): the repair loop is enough and works on both providers.
- `embed(List<String>)`: gateway only (A3). Publishes the same `AiCallCompleted` event and counts `jordylab.ai.calls`.
- `AiCallResult` and `AiCallCompleted` gain `inputTokens` / `outputTokens` read from the model response usage metadata
  (FR-017 "token usage recorded"). The budget/alert feature stays in its own draft.
- Temperature is set explicitly where determinism matters (0.0 for interpret, 0.3 for compose), because Spring AI 2.0 removed the 0.7 default. It is an **optional per-feature setting**, left unset for a model that is a router (A13): routers may pick reasoning models that ignore or reject it, and correctness never depends on it (the output is validated).

**Rationale**: one place for retries, fallback, health, timeouts, metrics. `ResilientAiServiceTest`, `AiGatewayWiringTest` and
`AiPropertiesTest` already guard it and get new cases.

### A3. Embeddings: OpenRouter `text-embedding-3-small`, configured, not user-selectable

**Verified (OpenRouter docs)**: OpenRouter exposes an OpenAI-compatible `POST /embeddings` with `model`, `input`,
`dimensions`, `encoding_format`, and lists `openai/text-embedding-3-small`. **Verified (Spring AI docs)**:
`spring.ai.openai.embedding.options.dimensions` and a separate embedding base URL/key are supported, so the existing gateway
properties apply.

**Decision**: `text-embedding-3-small` at **1536 dimensions** (matches the project rule `vector(1536)` and the cosine operator class
in `jordylab-be/AGENTS.md`). The model id lives in `jordylab.ai.embedding.model`, **not** on the AI Models page: switching an
embedding model silently invalidates every stored vector, so it is configuration plus a stored `model` column that triggers
re-embedding when it changes. `spring.ai.model.embedding` flips from `none` to `openai`; chat stays unset (documented gotcha).

**Anthropic has no embeddings**, so the embedding call has **no fallback provider**. That is acceptable only because retrieval
degrades (A5): with no embedding, LibBot still answers from structured and lexical search and says nothing broken.

**Verified (2026-10-07, test in the repo)**: `AiEmbeddingGatewayWiringTest` (WireMock, same style as `AiGatewayWiringTest`) boots the
real `OpenAiEmbeddingAutoConfiguration` of `spring-ai-openai` 2.0.1 and asserts that `EmbeddingModel.embed(...)` posts to
`<base-url>/embeddings` (`/api/v1/embeddings`, the `/v1` kept), with the bearer key, the configured model
`openai/text-embedding-3-small` and `dimensions = 1536`, and that the response's token usage is readable (needed for the token
counters). Both tests pass. No `RestClient` fallback is needed.

### A4. Vector store: pgvector with a Flyway-owned table, queried with SQL, not the generic `VectorStore` bean

The user asked for the data to reach "the vector store". It does: the vectors live in PostgreSQL pgvector. What changes is the
access path.

**Decision**: table `gamecatalog.game_embedding(game_id, model, content_hash, embedding vector(1536), embedded_at)` with an HNSW
`vector_cosine_ops` index, created by Flyway (the repo rule: Flyway owns all DDL). Retrieval is **one SQL statement** that applies
visibility, structured constraints and `ORDER BY embedding <=> :query`. Spring AI's `VectorStore` bean stays excluded
(`application.yaml`).

**Why not `PgVectorStore`** (three reasons; the first was verified in the 2.0.1 bytecode on 2026-10-07):

1. `PgVectorStore` holds its own `EmbeddingModel` and calls `embed(...)` itself when documents are added and on every similarity search.
   Using it would send embedding calls **around** `ResilientAiService`, bypassing the health cache, the events and metrics, and the token
   counters, which the AI rules forbid ("one path"). (A `ResilientAiService`-backed `EmbeddingModel` adapter could close that gap, but the next
   two reasons remain.)
2. Correctness: it owns its table DDL, stores filterable fields as JSON metadata, and filters by metadata
expressions. Everything LibBot filters on changes constantly and lives in other tables: visibility (a source toggled off must
hide games within a minute, FR-016), host display names, ROM status, per-user marks, vote totals. Mirroring those into vector
metadata means rewriting vectors' metadata on every vote and toggle, and a stale mirror would show a hidden game. With SQL the
relational tables are always the truth, and the "metadata" the AI sees (marks, votes, ROM status) is joined at query time, so a
vote is visible to the next question immediately.

**What is embedded**: a text document per game: title, platforms in words, genres, developer, year, multiplayer facts in words
("supports up to 4 players locally; split-screen"), description. Not votes or marks (they are relational and ranked in SQL).
`content_hash` makes re-embedding idempotent; only changed games are re-embedded.

**Performance (both ways are equivalent at this size, so the choice rests on correctness and the one-path rule)**: with a few thousand games a
cosine search is an exact scan of at most a few thousand 1536-float vectors, a few milliseconds. The HNSW index (kept, per the repo rule, for headroom)
is a *post-filter* structure: with very selective `WHERE` clauses pgvector may return fewer rows than asked, so the query is written to filter first
(candidates by SQL, then order by distance) and the planner is free to choose the exact scan. Raising `hnsw.iterative_scan` is the documented
remedy if the library ever grows by an order of magnitude. `PgVectorStore` has the same limitation, so nothing is lost by not using it.

**Alternatives rejected**: `PgVectorStore` with `initialize-schema` (breaks the Flyway ownership rule); a separate vector
database (monolith-first rule); embedding votes into the text (re-embeds on every vote, and "5 people want this" is a count, not
a meaning).

**Flagged for review** (Complexity Tracking): this differs from the literal "pass marks to the vector store"; the outcome (LibBot
knows marks immediately) is the same and more reliable.

### A5. Retrieval: hybrid, tri-state, votes after requirements (clarification Q4)

1. **Visibility predicate** (existing, reused verbatim, extended for console entries): a game is visible when it has an installed
   copy on an *enabled* source, an active library entry, or a console entry. This is also what hides disabled-source games from
   LibBot (FR-016, FR-029).
2. **Hard requirements** from the derived constraints as SQL predicates, **tri-state** for nullable facts:
   `confirmed` = fact present and satisfies; `unknown` = fact null; `excluded` = fact present and fails. `minLocalPlayers` uses
   `max_local_players >= N` for confirmed, and `max_local_players IS NULL AND local_multiplayer IS DISTINCT FROM false` for
   unknown. Unknown games are returned as a separate bucket with a **count**, never silently dropped and never mixed into
   "confirmed" (FR-004).
3. **Semantic ranking** of the surviving candidates by cosine distance to the embedded `semanticQuery`. Only when the question
   has taste/mood content; otherwise rank by votes then title.
4. **Votes**: among games that pass, order by `(wantVotes + likedVotes) - dislikeVotes`, then similarity. Requirements always come first (Q4). The asker's own dislikes are excluded outright; their liked games are excluded when the question is
   "like what I liked".
5. **ROM status**: a Broken emulated copy does not make the game *playable on that machine*. The query computes per-game
   "playable places": non-broken installations on enabled sources, library entries, console entries. A game whose only
   playable place is Broken is excluded from "what can we play" results (FR-054).
6. **Degrade**: if the embedding call fails or the vector index is empty, skip step 3 and use trigram/`ILIKE` over title,
   genres and description. The answer is still produced; the applied-constraints line is unchanged.
7. **Cap**: at most 15 candidates (confirmed first) go into the compose prompt; the unknown bucket contributes a count and at most 5
   titles. The old `max-result-games: 50` cap and "cite everything" behaviour are removed.

### A6. Answer composition and reference validation

`LibBotAnswer { String answer; List<UUID> recommendedGameIds; }`, validated: ids must be members of the candidate set, at most
10, and **each recommended title must appear in the answer text** (case-insensitive, normalised). Failing ids are dropped (not
the answer). This is what makes SC-004 ("every reference is named in the answer") true by construction. Out-of-scope and clarifying
replies come from the interpret step (`outcome`), carry `references = []`, and never reach the second call.

The compose prompt receives a delimited block of catalog rows ("data, not instructions") and the rule "answer only from these rows;
if the rows do not contain the answer, say so" (FR-002, FR-015). The user's marks and vote totals are inputs per row.

### A7. Conversation memory: server-side, bounded, session-only

**Decision**: an in-process store keyed by `userSubject + ":" + conversationId`. The server derives the key from the JWT subject; the
client only supplies the opaque `conversationId` (a UUID it generates), so one user can never address another's history
(`AGENTS.md` rule: "derived on the server from user and session"). Each conversation keeps the **last 10 exchanges**
(confirmed) as `ConversationTurn(userText, answerText, citedGameIds, outcome)`; a Caffeine cache with `expireAfterAccess = 2 h`
and a max size drops idle conversations. Nothing is written to the database.

**Why not Spring AI `ChatMemory`/`MessageWindowChatMemory`**: it stores plain `Message`s; LibBot also needs the cited game ids so
"those" and "the first one" resolve exactly, and the research warns against its in-memory repository in multi-replica setups. The
backend runs one replica (k3s single node); a pod restart ends a visit's conversation, which the spec accepts ("kept only for the
visit"). If replicas are ever added, the store becomes a table behind the same interface.

**New dependency**: `com.github.ben-manes.caffeine:caffeine` (version managed by the Spring Boot BOM).

### A8. Transport: Server-Sent Events with stage events

**Decision**: `POST /api/gamecatalog/libbot/ask` returns `text/event-stream` with events `stage`, then one `answer` (or `error`).
The browser reads it with `fetch` + a stream reader (the existing auth interceptor cannot wrap `EventSource`, which cannot send a
bearer header). Stages are real: `UNDERSTANDING` (sent immediately), `SEARCHING`, `WRITING`. This delivers FR-014 (progress within 1 s)
without token streaming; token streaming would complicate the provider fallback (a stream cannot be retried on another provider
after the first token) for a 2 to 4 s wait. Deferred, noted.

**Guest quota with SSE**: today `GuestChatLimitFilter` (settings module) counts after a 2xx response. An SSE response is `200` before the
answer exists, so a failed answer would be counted. **Decision**: the filter keeps only the **pre-check** (limit reached → `429` JSON
before the stream opens, new path); the **increment** moves to a Modulith application event `LibBotMessageAnswered(userSubject,
admin)` published by gamecatalog on a successful answer and consumed in `settings` (events-only cross-module wiring, the pattern in
`jordylab-be/AGENTS.md` "Mobile module"). Failed or cancelled messages never publish it (FR-013). The remaining count is returned in
the `answer` event and by `GET /libbot/quota`.

### A9. Language: English and Dutch (clarification Q3)

The interpret step returns `language ∈ {en, nl, other}`. The compose prompt instructs "answer in `<language>`". Applied-constraint
sentences, the unknown-data note, decline and error texts are template resources per language
(`src/main/resources/libbot/messages_en.properties`, `messages_nl.properties`). `other` → one fixed English reply asking to
rephrase (FR-056). The golden set includes Dutch cases.

### A10. Prompts as resources, and AI outside transactions

- Prompts move to `src/main/resources/prompts/gamecatalog/`: `libbot-interpret.st`, `libbot-answer.st`, `enrichment.st` (the
  enrichment and chat prompts are text blocks today, gap analysis #6). Templates containing JSON use `<>` delimiters
  (`jordylab-be/AGENTS.md`). A test asserts the loaded enrichment prompt equals the previous text.
- **No model call inside a transaction** (gap analysis #7). LibBot has no transaction around AI calls. The auto-fill worker (B7)
  makes each AI call outside a transaction and persists each game's result in its own short transaction. `ScanService.submitScan`
  stops running enrichment inline: it commits, then publishes `CatalogChanged`; the worker picks up. This also fixes the scan
  upload waiting on the model.

### A11. Golden evaluation set (FR-018)

A JSON fixture of questions with expected *behaviour* (intent, derived constraints, outcome, reference count ≤ N, language), not
exact wording. Two tiers: (1) **replay tier** in CI with recorded model outputs: tests the Java seam (derive, retrieve, validate)
deterministically; (2) **live tier**, a tagged Gradle task run on demand locally and before release, against the real models;
reports pass rate and token cost. Also holds a description-quality set (games with known facts; the Java `DescriptionQualityValidator` checks plus a few sampled live generations). Contains every owner-reported failure (cat question, "four people", "6 people game night",
"how many other games like this"), each US2 and US3 phrase, ROM-status and vote-ranking cases, each in English and Dutch.
Threshold: ≥ 90 % overall, 100 % for out-of-scope-with-no-references and for requirement-failing games never offered.

### A13. Authorship and routers: record the model that really answered (FR-059 to FR-061)

**Today** the game page prints "About · written by Claude" as literal text (`game-detail-view.component.html`, About heading), and the model that answered is
not stored at all (`Game.applyEnrichment` keeps only the text). Since the AI Models page lets the owner pick any OpenRouter model, and the owner's default
for all features is a **router** (`jev-router`: it picks the model and reasoning effort per request), the label is wrong even when it says Claude.

**Verified (test in the repo, `AiGatewayWiringTest.aRouterRequestExposesTheModelTheProviderActuallyUsed`)**: with a request for model `jev-router` and a gateway
response reporting `anthropic/claude-sonnet-5`, the Spring AI client returns `ChatResponse.getMetadata().getModel() == "anthropic/claude-sonnet-5"`. The request still carries `jev-router`. So the answering model is available to us for any provider that reports it in the response's `model` field, which
OpenRouter does for routed requests.

**Not verifiable from here**: what `jev-router` itself returns in that field (it is the owner's own router id, not in the repo or in any doc). The code handles
both outcomes: a reported model is shown; if the field is empty or equals the router id, the page shows the selected id and "(model not reported)". Reasoning effort is
not shown (no reliable way to read it from a router), recorded in the spec's assumptions.

**Decision**:
- `AiCallResult` gains `answeredModel` (from the response metadata; falls back to the selected model when absent) next to the existing `model` (selected) and `provider`,
  so the provider fallback case (Anthropic answers) also reports truthfully. `AiCallCompleted` carries both.
- `Game` stores `descriptionSource`, `descriptionModel`, `descriptionRequestedModel`, `descriptionWrittenAt` (data-model). `EnrichmentService` passes the authorship in.
- The detail response exposes a `description` object and `factSources`; the frontend builds the heading from them and holds no vendor name. "Facts from Steam/AI"
  moves out of the heading into the Spec sheet.
- Older descriptions (no model recorded) show "written by AI"; regenerating (the admin's AI refresh, or a single regenerate) fills in the model.

### A12. Settings → AI Models

The two existing features keep their keys (`gamecatalog.chat.query`, `gamecatalog.chat.answer`) so saved picks survive; their display
names and descriptions are updated to "LibBot: understanding the question" / "LibBot: writing the answer". A third constant
`GAMECATALOG_EMBEDDING` exists for metrics and events but has `selectable = false` and is hidden from the page (A3).

---

## Part B: Catalog model and data

### B1. A game exists once, with many places

**Today** (`gamecatalog/domain`): `Game.platform` is a single string; per-copy state lives in `GameInstallation` (scan copies and
the manual Switch rows), `GameLibraryEntry` (Steam owned/family). Identity: Steam by `steam_app_id` (unique partial index);
ROMs by `(platform, lower(title))`; Switch by `(platform, igdb_game_id)`.

**Decision**: three kinds of **place**, three tables, following the existing `GameLibraryEntry` pattern:

| Place | Table | Notes |
|---|---|---|
| Installed copy from a scan | `game_installation` (existing) | gains `platform`, `rom_status`; loses `manual`, `format` |
| Steam library membership | `game_library_entry` (existing) | unchanged; platform is implicitly Steam |
| Console copy | `console_game_entry` (new) | `(game_id, console_id)` unique |

`Game.platform` is **removed**. A game's platforms are the distinct platforms of its places. This is what makes "one entry,
several platforms" true and what lets filters, chips and LibBot read one source of truth.

**Alternatives rejected**: one polymorphic `place` table with a type column (clean on paper, but a rewrite of every existing
query and entity for no new capability); keeping `Game.platform` as "primary" (a lie once a game has two).

### B2. Same-game identity (clarification Q1)

Rule, in code in one place (`GameIdentityService`) and one SQL-free helper (`TitleKeys.normalise`):

1. If an external id matches (`steam_app_id`, or `igdb_game_id` which becomes unique **across** platforms, because an IGDB id
   identifies the game, not a platform release): same game.
2. If both sides have known ids and they differ: **different games**, never merged.
3. Otherwise (at least one side has no id): same game when `title_key` (lower-case, NFKD accents stripped, punctuation removed,
   a leading "the" dropped, edition/region suffixes like "(USA)", "[!]", "GOTY Edition" stripped, whitespace collapsed) is equal.

Resolve-or-create runs under `pg_advisory_xact_lock(hashtext(title_key))` so two sources scanning the same new title at once cannot
create duplicates (the existing Steam race is solved with an `ON CONFLICT` insert; titles have no unique key, so a lock is the
equivalent). A wrongly merged game is repaired by re-linking to the right IGDB match (existing relink flow, generalised).

### B3. Merge of existing duplicates at delivery

A **Flyway Java migration** (not SQL) backfills `title_key` and merges, so the normalisation code is the *same* class the app uses
(SQL `unaccent` is not guaranteed on the CNPG image). For each `title_key` group with more than one game and no conflicting ids: survivor =
the richest row (enriched, then has artwork, then oldest); repoint installations, library entries and (later) marks to the survivor,
handling `uq_game_library_entry_game_source` collisions by keeping the active row; copy any fact the survivor lacks; delete the rest.
The migration logs every merge (survivor id, merged ids, title). Ships with a **dry-run mode** (a system property that rolls the
transaction back and prints the plan) used locally on a copy of the dev data before the real run, and a recommended on-demand CNPG
backup before the prod release.

### B4. Platform catalog (one place for names, colours, ids)

`gamecatalog/domain/PlatformCatalog`, an immutable registry: canonical name, aliases, brand family, generation, IGDB platform name,
libretro thumbnail repo, chip colours, handheld flag. Replaces the scattered maps in `EmuDeckLibraryParser`
(`EMUDECK_PLATFORM_LOOKUPS`), `ArtworkLookupClient` (`LIBRETRO_REPOS_BY_PLATFORM`), `IgdbClient` (platform 130) and the frontend
`platformTagClass`. It also drives the console autocomplete (generation 5 up to PlayStation 5, Xbox Series X|S, Switch 2, handhelds
included) and the colours served to the frontend (FR-040: defined once). Parser output, console add and migration all pass platform
names through `PlatformCatalog.canonical(...)` so "N64" and "Nintendo 64" are one platform.

**IGDB platform ids: verified against live IGDB on 2026-10-07** (dev credentials, `POST /v4/platforms`, 220 platforms returned; no secret printed).
Every console in the planned list resolves by **exact name**, and IGDB's own `generation` field agrees with the "generation 5 and up" cut:

| Platform (IGDB name) | id | gen | Platform (IGDB name) | id | gen |
|---|---|---|---|---|---|
| PlayStation | 7 | 5 | Xbox | 11 | 6 |
| PlayStation 2 | 8 | 6 | Xbox 360 | 12 | 7 |
| PlayStation 3 | 9 | 7 | Xbox One | 49 | 8 |
| PlayStation 4 | 48 | 8 | Xbox Series X\|S | 169 | 9 |
| PlayStation 5 | 167 | 9 | Nintendo 64 | 4 | 5 |
| PlayStation Portable | 38 | 7 | Nintendo GameCube | 21 | 6 |
| PlayStation Vita | 46 | 8 | Wii | 5 | 7 |
| Sega Saturn | 32 | 5 | Wii U | 41 | 8 |
| Dreamcast | 23 | 6 | Nintendo Switch | 130 | 8 |
| Game Boy Color | 22 | 5 | Nintendo Switch 2 | 508 | 9 |
| Game Boy Advance | 24 | 6 | Nintendo DS | 20 | 7 |
| Nintendo 3DS | 37 | 8 | | | |

Also present for emulated ROMs (below generation 5, so not offered as consoles): Game Boy 33, SNES 19, NES 18, Mega Drive/Genesis 29, Game Gear 35,
Master System 64, Sega 32X 30, Sega CD 78, Atari 2600 59. Optional extras IGDB marks as generation 5 if you want them offered later (a data edit):
Atari Jaguar 62, 3DO 50, Virtual Boy 87, Neo Geo Pocket Color 120, WonderSwan 57. **Steam is not an IGDB platform** ("PC (Microsoft Windows)" is 6);
Steam games keep matching by `steam_app_id`, and the first catalog entry for them has no IGDB platform.

**Decision (changed from the first draft, which planned a runtime resolver)**: store the verified **id and the exact IGDB name** in `PlatformCatalog`.
A tagged integration check (`IgdbPlatformCatalogCheck`, run on demand with the dev credentials) re-fetches `/platforms` and fails if any stored id no longer
carries its stored name, which guards against IGDB changing. No runtime resolver, no extra cache.

**Colours, verified by computation** (WCAG 2 contrast, see [ui-design.md](./ui-design.md) §3): PlayStation `#0070D1`/white 4.95,
Xbox `#107C10`/white 5.37, Nintendo `#E60012`/white 4.80, Sega `#0089CF`/ink 4.89 (official blue with white text is 3.83 and fails, so
dark text), Steam `#1B2838` with `#66C0F4` 7.40, neutral `#3A3552`/bone 9.94. A unit test recomputes the ratio for every entry.

### B5. Hosts

`Host` becomes an aggregate (`host` table): `hostname` (from the machine, unique ignoring case), optional `displayName` (unique ignoring
case, ≤ 40 chars, trimmed, blank means cleared). `Host.label()` is the **only** place that decides which name is shown (FR-030, the
owner's "keep this logic on the entity"). `ScanSource` points at a `Host` instead of carrying a hostname string; scan adoption
(`(machineId, type)` then `(hostname, type)`) resolves or creates the `Host` first, never touching `displayName`. Everything that printed
`source.getHostname()` (`ScanSourceService`, `GameQueryService`, `ChatService`, `GameRepository` host filter and host list,
`HostRef`, the frontend host chips and detail page) switches to `host.label()`; the host filter is keyed by host id, not by the display
string. Cross-check with console names happens in a service under an advisory lock (two tables cannot share a DB unique index).

### B6. Consoles replace the Switch virtual source

`Console` aggregate (`console` table: `platform` canonical, `name` unique ignoring case). Console games are `console_game_entry` rows.
Migration moves every existing manual Switch installation into a console named "Nintendo Switch" (platform Nintendo Switch), drops
`format`, `manual` and the check constraint, deletes the `SWITCH` scan source and the `SourceType.SWITCH` constant. `SwitchGameService`,
`SwitchBulkService` and the controller generalise to `ConsoleGameService` etc. with the console id in the path; IGDB search is filtered
to the console's platform. `ReconciliationService`'s "skip manual installations" branches disappear, because manual copies are no longer
installations. The Sources page lists only `scan_source` rows, so the Nintendo Switch entry disappears for the right reason.

### B7. Auto-fill worker: data fills itself in (US4, FR-019 to FR-022)

**What it is, in plain words**: a background job inside the backend that looks for games that still lack something (cover, facts, player counts, an AI
description, a search-index entry) and fetches it, a few games at a time, without anyone pressing a button. It replaces the "Refresh pending data" button
and the half-measure of filling in a few games during each scan upload. (The first draft called it the "completion worker"; "completion" meant "completes
the missing data" and is a confusing word next to AI "completions", so it is now the **auto-fill worker**.)

**Today** (`jordylab-be/AGENTS.md`: "No scheduler in gamecatalog"): enrichment runs inline per scan in capped batches (8 AI, 25 metadata),
so leftovers wait for the next scan or a button, and ROMs only get covers from exact-title libretro hits. The owner's Q5 answer (daily
retry) is a deliberate change to that rule; `shared/config/SchedulingConfiguration` already exists (the FNA briefing uses `@Scheduled`).

**Decision (daily sweep approved by the owner, 2026-10-07)**: a single-flight `CatalogAutoFillService`, started by (a) `CatalogChanged` events after commit (scan applied, library
sync, console game added), (b) a daily `@Scheduled` sweep, (c) application start. It processes small batches; **per game, in order**:

1. **Facts** (free): Steam appdetails for Steam games (existing); **IGDB** for everything else: match by `igdb_game_id` or by title +
   platform, falling back to title with the title-key tolerance; fills genres, developer, publisher, year, multiplayer facts
   (IGDB is already the source for multiplayer, `IgdbClient.resolveMultiplayerMode`), and `igdb_game_id`.
2. **Artwork** (free), ordered: Steam CDN (Steam games) → **IGDB cover and artwork** (any platform, keyless CDN
   `images.igdb.com`, already allowed by the CSP) → libretro thumbnails tried with the normalised-title variants → placeholder.
   IGDB is the main addition: it is why the ≥ 90 % target is reachable, since libretro only matches exact No-Intro names.
3. **Description**: **Steam games keep Steam's own description and facts** (`Game.applyDeterministicDescription`, free), installed or not (owner decision, 2026-10-07).
   **AI writes a description only for games with no store description**: ROMs and console games, as today, limited to 3 attempts per game. The admin's "Regenerate AI data"
   covers exactly those games and always asks first. **Quality (FR-063)**: the prompt `enrichment.st` is rewritten to produce a store-blurb: 2 to 4 sentences, plain third
   person, what the game is and what you do, nothing beyond the facts supplied. The facts supplied now include IGDB's `summary` text when the facts step found one (fetched with
   the same IGDB call, used **as grounding input only**, never shown verbatim, which also sidesteps any licensing question about re-publishing it). The output is parsed into a
   typed record and checked in Java (`DescriptionQualityValidator`: sentence and word range, no first person, no phrases like "as an AI", no digits that look like scores or years
   that contradict the known release year); a failure counts as an attempt and is retried, never stored.
4. **Embedding** (cheap): when the document text or model changed.

Each step is idempotent, records attempts and `*_checked_at`, and is saved in its own short transaction (A10). **Retry policy (Q5)**:
free steps retry daily in small batches for games still missing data; the AI step retries within its 3-attempt limit, then waits for the
admin's "Regenerate AI data" (surfaced as a count on the Sources page). A scan upload never waits on this.

IGDB is rate-limited (4 requests/s; `IgdbClient.min-interval-ms` already paces calls). The worker is a background consumer of that same
pacing, so a large first fill takes minutes, not seconds; that is why SC-007 says "ten minutes".

### B8. Marks, ROM status, refresh runs

- **`game_mark`**: `(game_id, user_subject, mark)` a unique index on `(game_id, user_subject)` makes "one of three per user per game"
  a database fact, not a convention. Vote totals are aggregate queries (cheap at this scale). Account removal/disable: `settings`
  publishes `UserAccessRemoved(userSubject)` from the existing revoke and delete paths in `KeycloakUserAdministrationService`;
  gamecatalog's listener deletes that user's marks (FR-046).
- **ROM status**: a column on `game_installation` (per machine, clarification Q2) with enum `UNKNOWN | VALIDATED | BROKEN`, set only through
  `GameInstallation.changeRomStatus(...)`, which `Preconditions`-fails unless the source is an emulation source (FR-051). Installations
  already persist across rescans and within the 30-day grace, so FR-055 holds without extra code.
- **`refresh_run`**: durable, so a run survives leaving the page and a restart marks it `INTERRUPTED`; a partial unique index allows one
  `RUNNING` row per kind (cannot start twice). Executes on a virtual thread, commits per game, honours `stop_requested`, stops early
  after N consecutive identical failure causes (e.g. 402 insufficient credits, FR-048). The UI polls `GET /refresh-runs/current` every 2 s.
  Replaces `CatalogRefreshService.refreshPending` and `POST /games/refresh`.

### B9. Sources and labels

Source labels are *derived* per game, never stored: Steam (Owned) = active OWNED library entry; Steam (Family) = active FAMILY entry
and no OWNED; Emulated = an installed copy on an emulation source; Console = a console entry. A Steam copy not in any synced library counts as
Steam (Owned) until the next sync (spec Assumptions). The enum `LibrarySource.LOCAL` is retired.

### B10. Disabled source (confirmed by the owner)

Current behaviour already matches the confirmed rule (visibility requires an enabled source or a library/console place), so no semantic
change; additions are `GET /sources/{id}/hide-impact` (count of games that would be hidden, computed with the same visibility predicate)
for the confirmation dialog (FR-029, US12), and tests that pin hide, partial-hide and restore.

---

## Part C: Frontend

### C1. Library filters

Per [ui-design.md](./ui-design.md): search + one `Filters` button; active chips row with Clear all and live count; popover on desktop, bottom sheet
at ≤ 640 px. State lives in the existing `GameLibraryStore` (signal store), extended to arrays and mirrored to the query string (reload and Back
restore it). The default `Status: Installed` is an honest, removable chip.

### C2. Chips and colours

One `PlatformChipComponent` family driven by data from `GET /platforms` (colours come from the backend catalog, so the frontend hard-codes
none). Four chip families with different shapes and icons (FR-040, FR-041, FR-053). `cover.ts#platformTagClass` is removed.

### C3. Routes and navigation

`/games/chat` → `/games/libbot` (old path redirects), `switch` and `switch/bulk` → `consoles` and `consoles/:id/games/bulk`. Nav labels "LibBot"
(sparkle icon) and "Consoles"; guests see Library and LibBot.

### C4. LibBot UI

New `LibBotStore` (signal store) holds the conversation, stage, quota and applied constraints; reads the SSE stream with `fetch`. The UI follows
the design doc §5. `conversationId` is generated per page visit and sent as a header-free body field; "New conversation" generates a new one and
calls `DELETE /libbot/conversations/{id}`.

### C5. Mobile fingerprint flash (FR-057)

**Root cause (read in code)**: on native start the app auto-prompts fingerprint unlock when not authenticated
(`apps/jordylab/src/app/app.ts` ~L132) while the login page is already routed. An early request passes through `authInterceptor` →
`AuthService.getToken()`; with a Keycloak instance but no session, `updateToken(30)` rejects and the native branch sets
`nativeFailure = "Your session could not be refreshed. Sign in again."` (`auth.service.ts` ~L275) and navigates to `/login`. The login page shows
`biometric.failure() ?? auth.nativeFailure()`. The message stays up while the biometric prompt and token exchange run and is only cleared by a
successful `unlockWithRefreshToken` (`nativeFailure.set(null)`). Hence "a red message for a split second, and it works".

**Fix**: (1) `getToken()` returns `null` quietly when there was never an authenticated session (no failure, no redirect), and only reports a
failure for a session that really existed and then died; (2) `BiometricUnlockService.unlock()` clears `nativeFailure` and `failure` when it starts;
(3) the offline case sets the connection message only. A failing unit test (`auth.service.spec.ts`) is written first. On-device check: 20 sign-ins
(SC-018); biometric stays a manual checklist (`jordylab-fe/AGENTS.md`).

### C7. Game page: same composition on every width (FR-062)

The cover is `static mt-4 w-24` under the banner below `sm` and `absolute -bottom-10` from `sm` up (`game-detail-view.component.html`), which is why the phone shows a floating
cover with empty space beside it. **Fix**: one composition for all widths. Banner `aspect-[16/9]` on phones and `sm:aspect-[16/5]` from `sm` up; the cover overlaps the
bottom-left corner at every width (`absolute -bottom-10 left-4 w-24`, then `sm:left-6 sm:w-32`, `md:left-8 md:w-40`); the content block below gets a top margin that clears the
overhang (`mt-16`). The proportions match desktop (the cover occupies roughly the lower half of the banner height). Checked in the browser pane at 360, 390 and desktop against the
screenshot the owner sent; the sideways-scroll check covers it.

### C6. No horizontal scroll (FR-058)

A new Playwright check in the existing axe journey (`apps/jordylab-e2e/src/accessibility.spec.ts`) asserts `scrollWidth <= clientWidth` at 360, 390
and 430 px on every signed-in page, so new pages are covered the moment they exist. Known offenders are found by running it first (US14 lands as
"red then green"). Design rules: `min-width: 0` on flex/grid children, `overflow-wrap: anywhere` for titles and answers, tables scroll inside their own container.

---

## Part D: Security, observability, operations

- **Security** (`SecurityConfig`): guests may `PUT /games/*/mark`, `PUT /games/*/installations/*/rom-status`, `POST /libbot/**`, `GET` reads; everything else
  under `/api/gamecatalog/**` stays admin-only (consoles, host naming, refresh runs, sources). New explicit matchers go before the catch-all;
  `RoleMatrixTest` gets the new rows.
- **Observability**: `jordylab.ai.calls` gains token counters; LibBot logs outcome, intent, language, candidate counts and timings per message, **never** prompt
  or answer text (rule in `shared/ai/AGENTS.md`).
- **pgvector extension in production (blocking, found by reading the manifests)**: the CNPG image ships pgvector, but nothing creates the extension
  (`deploy/k8s/cluster/cnpg-cluster.yaml` has no `CREATE EXTENSION`), and pgvector's control file is **not** `trusted` (checked upstream), so the app's
  DB-owner role cannot create it. Plan: add a CNPG declarative `Database` resource with `extensions: [{name: vector, ensure: present}]` to
  `deploy/k8s/cluster`, applied by the deploy pipeline **before** the release that contains the migration; the migration itself uses
  `CREATE EXTENSION IF NOT EXISTS vector`, which is a no-op when present. Rehearse on `deploy/k8s/drills/restore-drill-cluster.yaml`. This is a
  cluster mutation: it needs the owner's yes at deploy time (HANDOFF).
- **Local and e2e**: dev and e2e compose use `pgvector/pgvector:pg16` with a superuser, so the extension works there.
- **Backups**: take an on-demand CNPG backup before the prod release (the title-key merge rewrites rows). The runbook (§15) documents restores only; adding the on-demand backup step is a task.

---

## Decisions and their status

| # | Decision | Status |
|---|---|---|
| 1 | Vector search through SQL over a Flyway-owned pgvector table, not Spring's `VectorStore` (A4) | Owner: "only if it is more optimal, best functional and non-functional result". Verified: `VectorStore` embeds through its own `EmbeddingModel`, around `ResilientAiService`; and mirroring votes/visibility as metadata goes stale. SQL is the better result on both counts, equal on speed. Kept. |
| 2 | Daily scheduler in `gamecatalog` (B7) | **Approved** 2026-10-07. `AGENTS.md` ("No scheduler in gamecatalog") is updated as a task. |
| 3 | Paid AI descriptions for Steam games you own but have not installed | **Resolved 2026-10-07: off.** Steam text and facts are used; AI writes only for games with no store description, to a Steam-comparable standard (FR-063). |
| 4 | Sub-packages `service/libbot` and `service/autofill` | Flagged; trivially reversible. |
| 5 | pgvector extension needs a cluster change before release (Part D) | **Approved by the owner 2026-10-07** (the CNPG `Database` resource enabling `vector`, applied just before the release). |

**Why production works today without the extension**: no migration, entity or query mentions `vector`, and Spring's pgvector auto-configuration has been
switched off (`application.yaml`, since 2026-10-01 when Ollama was removed). The Postgres image merely *contains* pgvector; it was never turned on in the
`jordylab` database because nothing needed it. LibBot is the first feature that does.

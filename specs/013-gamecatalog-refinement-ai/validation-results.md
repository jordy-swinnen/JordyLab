# Validation results: 013 Game Catalog Refinement and LibBot

Running log of what was verified, where, and how. Local first, production last (spec "Delivery and Validation"). Newest entries at the bottom of each section.

## Baseline before any change (T001, 2026-10-07)

Backend: `cd jordylab-be && DOCKER_HOST=<podman socket> TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --offline`

Result: **660 tests, 9 failed, all environmental, none in code touched by this feature**:

| Test | Failure | Verdict |
|---|---|---|
| `FnaRepositoryTest` (initializationError) | container startup under load | **flaky**: passes alone (7/7) |
| `JordylabApplicationTests.contextLoads` | context could not start under load | **flaky**: passes alone |
| `RoleMatrixTest` (6) and `GuestChatLimitIntegrationTest` (1) | `NoHttpResponseException: localhost:2375 failed to respond` while starting the Keycloak 26.7.4 container | **known local-only**, same signature as `docs/testing/bug-log.md` BUG-018; fail also when run alone on this Mac; green in CI. Cannot restart the Podman machine here (it would stop the owner's dev Keycloak). Treat the role matrix as verified by CI; extend `RoleMatrixTest` anyway and let CI prove it |

Environment notes:
- The dev stack on this Mac is partly down: only `jordylab-be-keycloak-1` runs; the dev Postgres container no longer exists and two anonymous volumes remain. Agents must not recreate it (root `AGENTS.md`, Local containers). Consequences: Phase 19 manual acceptance on localhost needs the owner to bring the dev stack up (HANDOFF), and the migration rehearsal on a copy of dev data (T027) can only use `podman volume export` of an orphaned volume if one holds the data.
- Automated tests use Testcontainers (`pgvector/pgvector:pg16`) and the throwaway `jordylab-fe/e2e/run.sh` stack, which are allowed.
- Frontend baseline (`bunx nx run-many -t test`): **14 of 14 projects pass**.

## Spikes kept as regression tests (done 2026-10-07)

- `AiEmbeddingGatewayWiringTest` (2 tests) green: embeddings call `<gateway>/embeddings` with model, dimensions and usage.
- `AiGatewayWiringTest.aRouterRequestExposesTheModelTheProviderActuallyUsed` green.
- IGDB platform ids verified against live IGDB (research B4).

## Switch pages (knowingly red until US6)

Not yet applicable.

## Implementation log

(entries are added per phase below)

### Phase 2 model refactor gate (2026-10-07)

`gamecatalog`, `shared`, `settings` packages and `ModularityTests`: **611 tests, 7 failed, all the known local Keycloak/Testcontainers start-up failure** (`RoleMatrixTest` 6, `GuestChatLimitIntegrationTest` 1: `localhost:2375 failed to respond`, same signature as the baseline). Everything else is green, including:

- `GameIdentityBackfillTest` (18): the three migrations against real PostgreSQL (merge by IGDB id / Steam id / title key, place moves, library-entry collision rules, Switch virtual source to console, host creation, ROM status backfill, dry run rolls back, vector column + cosine order, constraints).
- `GameRepositoryTest` (11): visibility predicate, source/platform/host filters, disabled-source hiding.
- `GameCatalogModuleTest`, `ModularityTests`.

Found and fixed while running them: the Java migration did not set its own `search_path` (Flyway resets it between migrations), so it could not see the `gamecatalog` tables.

### Phase 2 complete except T027 (2026-10-07)

- AI layer (`ResilientAiService`): message-list call, `callStructured` (schema appended, Jakarta validation, one repair, `INVALID_OUTPUT` then fallback), `embed` (`NOT_CONFIGURED` without a gateway key), optional per-feature temperature, `answeredModel` and token counts on `AiCallResult`/`AiCallCompleted`, `jordylab.ai.tokens` counter. `ResilientAiServiceTest` 26 green (incl. router model reported, repair quoting the problem without echoing model text), `AiGatewayWiringTest`, `AiEmbeddingGatewayWiringTest`, `AiPropertiesTest`, `AiModelSettingsServiceTest` green. `gamecatalog.embedding` is a non-selectable feature (hidden from Settings → AI Models).
- Enrichment prompt moved to `prompts/gamecatalog/enrichment.st`; a test pins it to the previous literal.
- Gate over `gamecatalog`, `shared`, `settings`, `fna`, `mobile`, `ModularityTests`: 786 tests, only `RoleMatrixTest` (6) and `GuestChatLimitIntegrationTest` (1) red = the known local Keycloak container start-up failure (`localhost:2375 failed to respond`). `JordylabApplicationTests` (full context incl. the embedding bean and validator) green. `FnaRepositoryTest` red once under load, green alone (known flake).
- T027 (rehearsal on a copy of dev data) is not done: the dev Postgres container no longer exists; needs the owner (HANDOFF in Phase 19). The migration itself is covered by `GameIdentityBackfillTest` on synthetic data.

### LibBot backend (US1 to US3), 2026-10-07

- Pipeline built: `QuestionInterpreter` → `ConstraintDeriver` (+`FactMatcher`, tri-state confirmed/unknown/excluded) → `CandidateRetriever` (SQL via the shared visibility predicate, pgvector cosine, tsvector word fallback, votes after requirements) → `AnswerComposer` → `LibBotAnswerValidator` (references named in text, ≤ 10). SSE `POST /api/gamecatalog/libbot/ask`, `DELETE …/conversations/{id}`; allowance pre-check in the settings filter, count by `LibBotMessageAnswered` listener (`GuestChatUsageListener`), quota served by settings at `GET /api/gamecatalog/libbot/quota` (gamecatalog must not depend on settings; documented deviation from the contract file location, path unchanged).
- Tests green locally: libbot unit tests (deriver 12, matcher 9, validator 9, messages 5, interpreter 4, composer 4, store 5, service 9), `CandidateRetrieverTest` 11 (real PostgreSQL + pgvector), `GameEmbeddingServiceTest` 8, `LibBotControllerTest` 9, guest quota/listener/filter tests, `GoldenReplayTest` 24 (owner-reported cat / four people / vague "how many like this" / long list cases, Dutch twins, follow-ups).
- `RoleMatrixTest` / `GuestChatLimitIntegrationTest` were updated for the new path and cannot run here (Keycloak container start-up, known); CI proves them.
- `./gradlew goldenLive` exists (tag `golden-live`, excluded from `test`). Verified the wiring with a dummy key (requests reach both providers and are rejected). **Not run against real models: no API key is available to the agent (HANDOFF for T072).** Temperature is set in `application.yaml` (query 0, answer 0.3): confirm with the live run that the chosen models accept it.

### US7 to US14 (2026-10-08)

- **Filters (US7)**: `GameFilterQueryTest` 12, controller/service tests updated, store (`game-library.store.spec.ts` 25) and filter components green; the Filters panel, active-filter chips, URL state, bottom sheet and keyboard flow are covered by `gamecatalog-filters.spec.ts`.
- **Host names (US8)**: `HostServiceTest` 5, `HostControllerTest` 5, module test (a scan never touches the name). `Host.label()` is the single rule; the raw hostname appears only on the admin Sources page and in the scan path.
- **Labels/colours (US9)**: chip family (`status`, `source`, `rom`, `mark`) with a WCAG contrast spec that reads the colours from the real rendered classes; `LibrarySource.LOCAL` retired.
- **Marks (US10)**: `MarkServiceTest` 10 and `MarkCleanupListenerTest` 2 against PostgreSQL (upsert, one mark per person); `UserAccessRemoved` lives in `shared.event` (not in `settings` as the task said) because `settings` already depends on `gamecatalog` and the reverse would be a module cycle. LibBot: asker's disliked games excluded, mark filters, "like what I liked", votes rank after requirements (`CandidateRetrieverTest` 20, golden +8 incl. Dutch twins). The golden replay found a real bug (the mark chip key used `:` where the message bundle expects `.`).
- **Bulk refresh (US11)**: `RefreshRunServiceTest` 17, controller 8; the old `POST /games/refresh`, `CatalogRefreshService.refreshPending` and the "Refresh pending data" button are gone.
- **Sources (US12)**: hide-impact endpoint + confirmation dialog (`GameRepositoryTest` proves the hidden/still-visible split on real data).
- **ROM status (US13)**: `RomStatusServiceTest` 6, controller 4, module test (survives rescan and a game that left and came back); LibBot never offers a game whose only copies are broken and prefers a validated machine.
- **Fingerprint flash (US14)**: red first: with the new cases `auth.service.spec.ts` failed 2 (`getToken()` without a session set a native failure and navigated to `/login`; no `clearNativeFailure`) and the biometric spec failed (stale failure kept at the start of `unlock()`); both green after the fix (`getToken()` is silent until a session existed; `unlock()` clears stale failures first). shared-auth 80 tests green. The 20 manual fingerprint sign-ins remain an owner HANDOFF.
- **Overflow (US14)**: `layout.spec.ts` (every signed-in page + filters panel + a game page, at 360/390/430 px). First run: all pages fit with short titles. A game with one very long word in its title **overflowed the game page** (`scrollWidth 1398` at 360 px): the heading had no wrapping; fixed (`overflow-wrap:anywhere`, smaller minimum size). The e2e catalog now contains such a title so the check keeps guarding it.
- **CSP (found by the e2e run)**: the production `img-src` allowed only `images.igdb.com`, but covers also come from `cdn.cloudflare.steamstatic.com` (Steam) and `raw.githubusercontent.com` (libretro). Added both to `deploy/containers/frontend/security-headers.conf` and the runbook text. The e2e backend now runs with the cover lookup off so no journey depends on the public internet.
- **e2e web (`e2e/run.sh web`)**: **88 passed** (journeys, axe incl. the open Filters panel, CSP, layout, marks with two people, filters).

### Local gate, T145 (2026-10-08)

- **Frontend**: `bunx nx run-many -t lint oxlint test`: **17 projects green**.
- **e2e web** (`e2e/run.sh web`): **88 passed** (see above).
- **Backend** `./gradlew build --continue`: 1136 tests, **8 failed, all with the known local container signature** (`localhost:2375 failed to respond`): `RoleMatrixTest` (6), `GuestChatLimitIntegrationTest` (1), `JordylabApplicationTests` (passes when run alone). Everything else is green, including `ModularityTests`, every golden replay case (43), `CandidateRetrieverTest` (20), `GameFilterQueryTest`, the mark/ROM/refresh-run/host/console suites and the gamecatalog packages' 80 % coverage. `jacocoTestCoverageVerification` only reports `fna.util`, `fna.rest.controller` and `fna.rest.controller.model` below 80 %: those packages are covered by the Keycloak-based tests that cannot start here, so CI is the judge. The new rows in `RoleMatrixTest` (places, marks, ROM status, host name, refresh runs, consoles) are therefore proven by CI only.
- Found while running the full gate: two scenario test classes sharing one stopped container (BUG-074, fixed with `@DirtiesContext`).
- Not run here: the Android startup suite (`e2e/run.sh android` needs an emulator), the live golden run and the description comparison (no AI keys for the agent).

### Release, T151 to T153 (2026-10-08, `v0.0.1-rc20`)

- **T151**: PR #134 merged into `main` as `b824cff` (all CI checks green).
- **T152**: on-demand CNPG backup `pre-013-202610081936` **completed** before the deploy (Barman plugin → OVH S3). The pgvector `Database` resource was applied to the `jordylab` namespace before the deploy (the manifest has no namespace; a plain `kubectl apply -f` lands it in `default` and it never reconciles — the pipeline's overlay sets `namespace: jordylab` and re-applies it). `\dx` shows `vector 0.8.6` in the `jordylab` database; Flyway logged `extension "vector" already exists, skipping`, so the `CREATE EXTENSION` no-op path is proven in production.
- **T153**: tagged `v0.0.1-rc20` on `b824cff` (Build green for that commit), release pipeline `verify → retag → release → deploy → publish → apk` all green, production deploy approved and rolled out: backend, frontend and keycloak on `ghcr.io/jordy-swinnen/jordylab-*:v0.0.1-rc20`. Flyway applied the three migrations in order: `20261008001 - gamecatalog places hosts consoles`, `20261008002 - GameIdentityBackfillAndMerge` (Java), `20261008003 - gamecatalog identity marks runs`, now at `v20261008003`. GitHub Release `v0.0.1-rc20` published as a prerelease; the APK job is green.
- **Verified on https://jordylab.be** (no credentials, so only the unauthenticated surface): frontend serves the rc20 bundle (contains the 013 strings `Regenerate AI data`, `Not yet searchable`, LibBot); Keycloak login flow reaches the themed `jordylab` sign-in form; `/library`, `/sources`, `/consoles`, `/chat` all bounce an unauthenticated visitor to the sign-in page; `/auth/admin/` is not the Keycloak admin (SPA fallback, as designed); every checked `/api/**` endpoint answers `401` without a token. In the database: `gamecatalog.game` 509 rows, `gamecatalog.game_embedding` being rebuilt (50 → 56 rows while watching). The startup auto-fill worker runs its designed enrichment sweep (via `OPENROUTER_API_KEY`, model per Settings → AI Models); some outputs are rejected by the 2-to-4-sentence validator and retried (`Enrichment output invalid for '…'`) — product behaviour, not a deploy failure. The agent triggered no paid AI call.
- **Left for the owner**: T150 sign-off (local HANDOFFs), T154 to T157.

### Production signed-in pass (2026-10-08, `v0.0.1-rc20`, admin session in the browser pane)

- **Library grid + filters**: 226 installed titles render with artwork, source/status chips and per-card marks; the Filters panel offers every spec 013 group (Platform, Where with host names, Status, Source, Local players, ROM status, Community marks + whose marks); filtering to *Platform: Nintendo 64* gives 32 games with active-filter chips, "Clear all" and URL state (`?platform=Nintendo%2064`). **PASS**
- **Game page** (Banjo-Kazooie): spec sheet with fact provenance ("facts from AI"), AI description with model attribution ("written by gemini-3.8-flash · picked by jev-router"), per-machine ROM status (Unknown/Validated/Broken ROM), marks with counts, "Regenerate description" and "Ask LibBot". **PASS**
- **Sources (admin)**: library health counts (507 games; 95 without a cover, 220 without a description, 389 not yet searchable), free "Refresh game data", paid "Regenerate AI data" behind its cost-confirmation dialog (never activated), 3 scanned sources with host naming and applied status. **PASS**
- **Consoles**: empty state + "Add a console" (generation picker, optional name) and the gated "Add a game" section render. No console was created (no prod data changes). **PASS**
- **LibBot**: **FAIL** — every question (admin) ends in "LibBot can't answer right now. Nothing was counted against your messages." Logged as **BUG-075**: the configured gateway model (`typesafe/jev-router` on Settings → AI Models) returns invalid JSON for `QuestionInterpretation` (repair included) and the Anthropic fallback `claude-sonnet-5` rejects `temperature` with 400. Fix: temperature now goes to the gateway only.

### Live golden run + the two LibBot blockers (2026-10-08, after `v0.0.1-rc21`)

- **BUG-075 (temperature)**: shipped in `v0.0.1-rc21` — the Anthropic fallback call now succeeds (no more 400). Verified in the prod backend log.
- **BUG-076 (language enum)**: with rc21 live, interpretation still failed on *every* model. Reproduced locally against real models and root-caused with a temporary debug print: the generated schema advertises `Language` as `[EN, NL, OTHER]` while the parser accepted only `[en, nl, other]` (`InvalidFormatException`). The recorded goldens use `"en"`, so `GoldenReplayTest` could never catch it. Fixed by accepting both wire forms (schema = deserialiser); regression test `QuestionInterpretationTest`. **Correction to an earlier claim:** the configured router model (`typesafe/jev-router`) was not producing invalid JSON — it hit this same parse bug; no model change is needed for that reason.
- **`./gradlew goldenLive`** (first live run of the tier, owner-approved credits; ~90k in / 10k out tokens): the pipeline now runs end-to-end — **23/31 (74 %)** against the recorded expectations (tag owner-reported 4/4, us1 9/11, us2 6/8, us3 2/4, dutch 6/8, us10 6/8, us13 2/2). All 8 failures are live-model vs recorded-expectation differences in wording or interpretation taste (e.g. follow-up "those"/"die" resolution, `online` extraction, derived-constraint labels embedding the model's semantic query, one case whose recorded bad-model output shape cannot recur live), not parse or pipeline faults. Below the tier's own 90 % gate: re-recording the goldens with the chosen models, prompt tuning, or a model change on Settings → AI Models are the owner's call (T072). Description-quality tier failed alongside (live blurbs judged by `DescriptionQualityValidator`).
- **Verified on prod** (`v0.0.1-rc22`, 2026-10-08, admin session): LibBot answers. "Which games support local multiplayer?" → 9 cited games with platforms and player counts (interpret 21:40:22 → answer 21:40:27, both calls through the gateway `typesafe/jev-router`, no repair, no fallback). "There are 6 people here tonight, what should we play?" → the derived constraints ("6+ local players", "local multiplayer") are shown as Applied chips, two fitting games named (marks considered), and the honest "for 166 more games the player count isn't known yet" note. "Welke games hebben lokale multiplayer?" → correct Dutch answer with cited games. BUG-075 and BUG-076 are VERIFIED-PROD.

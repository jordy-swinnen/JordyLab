# Research — Settings Module (Users & AI Models)

Date: 2026-09-27. Baseline: branch `006-settings-module` at `5f6feff` (main after PR #16), verified against live vendor
docs and the actual codebase (backend and frontend maps below).

This file **carries forward and supersedes** `specs/_drafts/settings/research.md`: its provider comparison (§1), the
four AI call sites (§2), and the auth gap analysis (§3) remain the foundation; everything below adds what was verified
during planning and records the design decisions.

## TL;DR of new findings

| # | Question from the drafts                                             | Verdict                                                                                                                                                                              |
|---|----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Does Spring AI 2.0.0-M2's OpenAI module work with OpenRouter?        | **Yes, but on M2 you need `completions-path`; on 2.0.x GA (M5+) you don't** — see D1/D2. GA is out and is the recommended target                                                     |
| 2 | Is OpenRouter's `/models` readable without a key?                    | **Yes** — keyless fetch returned 458 models with full pricing metadata (see §2)                                                                                                      |
| 3 | keycloak-admin-client or a plain RestClient?                         | **Plain RestClient** — the admin-client artifact line (26.0.x) is decoupled from the server line (26.3.x) and drags the RESTEasy stack in (D3)                                       |
| 4 | Does keycloak-js support AIA (`UPDATE_PASSWORD` / `UPDATE_PROFILE`)? | **Yes** — `login({ action: 'UPDATE_PASSWORD' })` and `register()` are documented and typed (D7)                                                                                      |
| 5 | Do new registrations auto-get `jordylab-user`?                       | **No** — the realm export has no `defaultRole` block referencing it; it is only granted explicitly to the `jordy` user (verified in `compose/keycloak-realm-export.json`)            |
| 6 | Is `ResilientAiService` ready for a second provider?                 | **No** — it injects the autoconfigured `AnthropicChatModel` only; no fallback, no Micrometer, health cache is failure-driven only. The whole call path is refactored by this feature |
| 7 | Does the realm's user list need bookkeeping for "rejected/revoked"?  | **No** — status can be derived from (enabled, roles); see D6                                                                                                                         |

## 1. Verified external facts

### 1.1 Spring AI ↔ OpenRouter (the critical version matrix)

- **`completions-path` exists only up to 2.0.0-M4**. From **2.0.0-M5** the OpenAI module uses the official `openai-java`
  SDK, which **appends `/chat/completions` itself and expects the base URL to already include `/v1`** (commit
  `75fa84486` removed `completions-path`/`embeddings-path`; spring-projects/spring-ai issues #6036 and PR #6093 document
  the migration; docs.spring.io 2.0.1 property tables confirm the properties are gone).
- **Spring AI 2.0.1 GA exists** (current docs line) and targets Spring Boot 4.0.x/4.1.x. `jordylab-be/AGENTS.md` already
  records the plan to move to GA "once a GA 2.x release exists" — it now does.
- **Working OpenRouter config per line**:
    - 2.0.x GA (M5+): `spring.ai.openai.base-url: https://openrouter.ai/api/v1` (keep `/v1`) — no path property.
      Verified pattern in the wild (`pacphi/spring-ai-openrouter-example`: "append /v1 to OpenRouter base URL for
      openai-java SDK compatibility").
    - 2.0.0-M2 (today's pin): homegrown `OpenAiApi` with `spring.ai.openai.chat.completions-path` (default
      `/v1/chat/completions`) — would need `base-url: https://openrouter.ai/api` +
      `completions-path: /api/v1/chat/completions`.
- **Dual chat starters require a model selector**: with both `spring-ai-starter-model-openai` and
  `spring-ai-starter-model-anthropic` on the classpath, `spring.ai.model.chat` must be set (e.g. `openai`) or startup
  fails with "At least one credential source must be specified" (per the OpenRouter example repo's maintenance notes). *
  *Verify at boot smoke test** that both `OpenAiChatModel` and `AnthropicChatModel` beans still exist with the selector
  set to `openai`.
- The 2.0.1 line also migrated the **Anthropic** module to the official Anthropic Java SDK (upgrade note in the 2.0.1
  docs). `AnthropicChatModel.call(Prompt)` + `AnthropicChatOptions.builder().model(...)` — the surface
  `ResilientAiService` uses — is unchanged, but the bump must re-run `ResilientAiServiceTest` and the module tests per
  the AGENTS.md GA-move plan.

### 1.2 OpenRouter `/models` (keyless, fetched 2026-09-27)

- `GET https://openrouter.ai/api/v1/models` **without an Authorization header** returns
  `{ "data": [...458 models...], "total_count": 458, "links": { "next": null } }` (single page, cursor-paginated field
  present).
- Fields per model: `id` (`vendor/slug`, `:batch`/`:free`/`~alias` variants), `name` (`"Vendor: Model"`), `created` (
  epoch), `context_length` (int), `architecture` (`modality`, `input_modalities`, **`output_modalities`**, `tokenizer`),
  `pricing`, `top_provider`, `supported_parameters`, `default_parameters`, `expiration_date`, `canonical_slug`, optional
  `reasoning`/`benchmarks`.
- **`pricing.prompt`/`pricing.completion` are per-token USD strings** (e.g. `"0.000004"` = $4/MTok; special `"-1"` for
  router/auto models, `"0"` for free tier). Multiply by 1,000,000 for the per-million display; guard `"-1"` (display "
  router-priced"/unknown).
- `expiration_date`: ISO date or null — **28/458 models had dates**; drives the "expiring" flag.
- It is **not** a strict OpenAI `/models` body (no `object`/`owned_by`) — parse it as its own shape; only `data[]` is
  OpenAI-style.
- Chat-callable filter: `architecture.output_modalities` contains `"text"` (and exclude `~`-aliases from the default
  list to avoid duplicate rows; `alias_target` marks them).

### 1.3 Keycloak

- **Server**: compose runs `quay.io/keycloak/keycloak:26.3.2` (`start-dev --import-realm`, `KC_HOSTNAME=localhost`, port
  8180, theme `jordylab`, brute-force on, access-token lifespan 1800s, `registrationAllowed: false` today, no
  `registrationEmailAsUsername` key yet, no `defaultRole` block).
- **keycloak-admin-client**: latest on Central is **26.0.12** — the artifact line stayed on `26.0.x` while the server
  moved to 26.1–26.6; there is no 26.3.x admin client. It drags the RESTEasy client stack (`resteasy-client`,
  `resteasy-multipart`, `resteasy-jackson2`, `resteasy-jaxb`) plus Jakarta RS providers — a second HTTP/Jackson pipeline
  inside a Boot 4 app that otherwise uses Spring `RestClient`.
- **keycloak-js 26.2.4** (installed): `KeycloakLoginOptions.action?: string` is typed; the adapter docs confirm
  `login({ action: 'UPDATE_PASSWORD' })` triggers the required-action page (re-authenticating first), `register()` is a
  shortcut for `login({ action: 'register' })`, and the `onActionUpdate(status, action)` callback exists for AIA
  outcomes.
- **AIA + email**: `UPDATE_EMAIL` as an AIA requires email verification (SMTP) — without SMTP the email must be edited
  as a profile field via `UPDATE_PROFILE`. **Known caveat with email-as-username**: changing the email does not always
  update the username (keycloak #13988, #16679) — must be verified against 26.3 during implementation and the outcome
  documented here.
- **Admin REST endpoints needed** (all stable, documented): search/list users (`GET /admin/realms/{r}/users`), get user,
  realm role assignment (`POST /users/{id}/role-mappings/realm`, `DELETE`), reset password (
  `PUT /users/{id}/reset-password` with `temporary: true`), logout sessions (`POST /users/{id}/logout`), role list (
  `GET /roles/{name}`).
- **Testcontainers**: `com.github.dasniko:testcontainers-keycloak` **v4.3.1** (Testcontainers 2.x line, bundles
  keycloak-admin-client 26.0.11; default image 26.5 but any image can be pinned, e.g.
  `quay.io/keycloak/keycloak:26.3.2`; supports `withRealmImportFile`). The debug-options caveat in its README applies to
  26.4+ only — not our case. Verify Boot 4's managed Testcontainers line matches the 2.x requirement at implementation
  time (if the Boot BOM pins 1.x, use the 3.x dasniko line instead).

## 2. Codebase baseline (as mapped on 2026-09-27)

### 2.1 Backend (`jordylab-be`)

- **AI layer** (`dev.jordy.jordylab.shared.ai`): `ResilientAiService` (injects autoconfigured `AnthropicChatModel`,
  per-call `AnthropicChatOptions.model()` from `jordylab.ai.modules.<module>.model`, executor +
  `Future.get(call-timeout-seconds)`, multi-generation `extractText`, cause-chain → `ProviderFailureReason`),
  `AiModuleConfig` (`jordylab.ai.*` record props), `AiCallResult`, `ProviderHealthCache`, `@NamedInterface("ai")`. *
  *No `ChatClient`, no fallback, no metrics.** Callers: `BriefingGeneratorService` (fna,
  `prompts/fna/briefing-system.st`), `EnrichmentService` + `ChatService` (gamecatalog, inline prompts). Config in
  `application.yaml` under `jordylab.ai.modules.fna|gamecatalog`.
- **Security** (`shared/config/SecurityConfig.java`): actuator health/info + h2-console permitAll; `ingest/client` →
  `hasRole("jordylab-user")`; `ingest/scan|check` → `hasRole("gamecatalog-scanner")`; `/api/**` → `authenticated()`;
  `anyRequest().denyAll()`. Realm roles map from `realm_access.roles` (unit-tested in `SecurityConfigTest`). No method
  security, no filters/interceptors anywhere.
- **Realm export**: roles `jordylab-user`, `gamecatalog-scanner`; clients `jordylab-host` (public, PKCE, std flow) and
  `gamecatalog-script` (public, device grant); user `jordy` (explicitly holds both roles + `offline_access`);
  `registrationAllowed: false`; **no `defaultRole` composite includes `jordylab-user`** (verified — new users would get
  no app role).
- **Build** (`build.gradle.kts`): Boot 4.0.3, Java 25, `springAiVersion = "2.0.0-M2"`, Modulith 2.0.3, starters:
  anthropic + **ollama (unused)** + pgvector + advisors-vector-store; `testcontainers-ollama`; WireMock 3.13.0; JaCoCo ≥
  0.80 per package.
- **Module pattern** (gamecatalog): root `@ConfigurationProperties` record + `domain/repository`, `rest/client`,
  `rest/controller/model`, `service`, `util`; no cross-module facades exist today — only `shared`'s `@NamedInterface`s.
  Flyway `V<yyyyMMdd>__*.sql` with `CREATE SCHEMA IF NOT EXISTS` + `SET search_path`. `ModularityTests.verify()`. *
  *Event infrastructure exists but is unused** (`event_publication` table in `public`,
  `BaseEntity extends AbstractAggregateRoot`).
- **Tests**: `TestcontainersConfiguration` (Ollama + 2 Postgres beans), no Keycloak container; `@WebMvcTest` +
  `@MockitoBean` + `@Language("JSON")`; WireMock at ports 9995–9999; `ResilientAiServiceTest` is pure-Mockito with a
  mocked `AnthropicChatModel`.

### 2.2 Frontend (`jordylab-fe`)

- **App shell** (`apps/jordylab`): hardcoded `NavGroup[]` in `app.ts`; `app.routes.ts` lazy-loads
  `@jordylab-fe/fna/ui` + `@jordylab-fe/gamecatalog/ui` behind `authGuard`; default redirect `'' → 'fna'`; *
  *`app.spec.ts` pins the exact nav labels/hrefs**; sidebar gated only by `@if (username())`; no badge-in-nav pattern (
  only an active dot; `hlmBadge` exists for content).
- **Auth** (`libs/shared/auth`): `AuthService` wraps keycloak-js (init check-sso, PKCE S256, silent check) behind
  signals `isAuthenticated`/`username`/`token`; **reads only `token` and `preferred_username` — no roles**;
  `authGuard` = login-only; `authInterceptor` attaches the bearer; `AUTH_CONFIG` per app (all three apps use realm
  `jordylab`, client `jordylab-host`); pre-bootstrap init in `main.ts`.
- **Dev harness apps** `apps/fna` (4300, baseHref /fna) and `apps/gamecatalog` (4400, /games): thin shells wrapping the
  domain route libs behind `authGuard`.
- **Domain libs**: `libs/gamecatalog/{api,ui}` — hand-rolled signal stores (private `#` signals, `asReadonly()`,
  `computed`, `inject(HttpClient)`), Container–Presentation pairs, barrel `src/index.ts`, `gamecatalogRoutes`.
- **Tooling gotchas for this feature**: `eslint.config.mjs`'s `type:app` dependency constraint **hardcodes the scope
  list** (`scope:fna|gamecatalog|shared`) — `scope:settings` must be added or the host app can't depend on the new libs;
  `tsconfig.base.json` paths for `@jordylab-fe/settings/*`; no spartan menu/dropdown/tabs helm components exist yet (
  only badge, button, card, formfield, input, skeleton) — the user-menu dropdown is new (spartan CLI).
- **keycloak-js 26.2.4** installed (`bun.lock`), against server 26.3.2 — same major, fine.

## 3. Decisions

Each: Decision / Rationale / Alternatives considered.

- **D1 — Bump `springAiVersion` 2.0.0-M2 → 2.0.1 GA as part of this feature.** Rationale: the feature rewrites the whole
  AI wiring anyway (primary + fallback + per-feature models); `jordylab-be/AGENTS.md` explicitly plans the GA move once
  a GA exists; M2's `completions-path` mechanism is a dead end that GA removes, so building the OpenRouter integration
  on M2 would be work to throw away. Alternatives: stay on M2 (rejected: milestone risk the repo already wants gone, and
  the OpenRouter config differs per line — do it once, on the line we keep); jump to 2.0.0-M7 (rejected: still a
  milestone). The bump follows the AGENTS.md procedure: bump, re-run `ResilientAiServiceTest`, module tests, check
  migration notes (Anthropic module → official SDK).
- **D2 — Gateway config lives once, under `jordylab.ai.gateway`.** `jordylab.ai.gateway.base-url` (default
  `https://openrouter.ai/api/v1`) + `OPENROUTER_API_KEY`, mapped into `spring.ai.openai.base-url`/`api-key` via yaml
  placeholders; the settings model-catalog client reads the same properties. `spring.ai.model.chat: openai` selects the
  primary. Rationale: FR-011 demands a swappable gateway — one source of truth; the catalog client and the chat model
  must agree on the gateway. Alternatives: two independent configs (rejected: drift), reading `spring.ai.openai.*` from
  the settings module (rejected: settings reaching into Spring AI's prefix).
- **D3 — Thin typed `KeycloakAdminClient` built on Spring `RestClient`, not `keycloak-admin-client`.** Rationale: ~7
  stable Admin REST endpoints needed; no RESTEasy/Jakarta-RS stack in the app; matches the existing `rest/client`
  pattern (`IgdbClient`, `SteamAppDetailsClient` + `RestClientConfiguration`); the compatibility story is cleaner — the
  admin-client artifact line (26.0.12) no longer tracks the server line (26.3.2), so it buys no version lock, only
  weight. Auth: client-credentials token fetch + refresh against the token endpoint using the new confidential client's
  secret (env var, never in the browser). Alternatives: `keycloak-admin-client` (rejected on weight + drift), calling
  admin endpoints with the user's own token (rejected: users don't hold realm-management).
- **D4 — shared/ai gets a port, settings implements it (dependency inversion).** `shared/ai` exposes: `AiFeature` (enum:
  `fna.briefing`, `gamecatalog.enrichment`, `gamecatalog.chat.query`, `gamecatalog.chat.answer` — key, display name,
  module, description), an `AiModelResolver` port (effective model per feature, with "is the saved model still known"
  info), and a `call(AiFeature, system, user)` API that tries the primary then falls back. The **settings module**
  provides the DB-backed `@Primary` resolver (reads `AiFeatureModelSetting`, cached, defaults from
  `jordylab.ai.features.<key>.model` when no row exists) and listens to a shared-published `AiCallCompleted` Modulith
  event to persist `AiFeatureLastRun`. Rationale: keeps `shared` persistence-free and keeps the module arrow one-way (
  `settings → shared`), the same direction as every other module; uses the existing-but-unused event infrastructure.
  Alternatives: shared reading a settings-owned table (rejected: `shared → settings` cycle); model settings in a
  shared-owned schema (rejected: settings owns its data); settings owning the whole AI call path (rejected: every module
  calls AI — that's shared's job).
- **D5 — Guest chat limit is a settings-owned servlet filter, not a gamecatalog change.** `GuestChatLimitFilter` (
  settings, `OncePerRequestFilter`, registered after security) matches `POST /api/gamecatalog/chat`: guests (no admin
  role) are checked against the persisted per-day count **before** the call and rejected with `429` +
  `{"reason":"CHAT_LIMIT_REACHED","resetsAt":...}`; the count is incremented only when the response is 2xx (checked
  after `chain.doFilter`). Rationale: guest policy stays entirely inside settings (no `gamecatalog → settings` module
  edge); works regardless of caller. Alternatives: gamecatalog calling a settings port (rejected: cross-domain
  dependency for one check); counting before the call regardless of outcome (rejected: failed AI calls shouldn't burn a
  guest's budget — spec: "no AI call is made" on limit, and friends shouldn't lose budget on 503s).
- **D6 — User status is derived, not stored.** `PENDING` = enabled + no `guest`; `APPROVED` = enabled + `guest`;
  `REJECTED` = disabled. Revoke returns the user to `PENDING` (re-approvable, still login-capable → they see the
  awaiting-approval page). Rationale: Keycloak is the single source of truth; zero bookkeeping; the clarify round
  already fixed Reject = disable. The last-admin guard (FR-007) blocks Reject/Revoke on the only enabled `admin`.
  Alternatives: a status attribute on the Keycloak user (rejected: duplicated state that can drift).
- **D7 — "My account" uses keycloak-js AIA.** `login({ action: 'UPDATE_PASSWORD' })` and
  `login({ action: 'UPDATE_PROFILE' })` from a new user dropdown (spartan menu via CLI). Registration needs no frontend
  work — with `registrationAllowed: true`, Keycloak's hosted login page shows the Register link; the `jordylab` theme
  must also style the registration page. `UPDATE_EMAIL` is not used (needs SMTP). **Implementation-time verification:**
  the email-as-username username-sync caveat (keycloak #13988/#16679) on 26.3 — test and document the outcome here.
- **D8 — Sign-up notification (P3) is a scheduled poll + Ntfy push.** Poll the pending list (already required for the
  badge/API) every 5 minutes (`@Scheduled`, fna precedent), diff against the last seen set, push new pending users to
  Ntfy (base URL + topic from config; optional token). Alternatives: Keycloak event listener SPI (rejected: extension
  deployment, overkill); notify only while an admin is logged in (rejected: the point is not having to watch).
- **D9 — Keycloak integration tests use `com.github.dasniko:testcontainers-keycloak` (4.3.1, verify TC alignment)** with
  the same `26.3.2` image as compose and a test realm import (roles `admin`/`guest`, test users). Real JWTs exercise the
  role matrix: guest 403 on `/api/fna/**` + `/api/settings/**`, pending user 403 everywhere, scanner flow unchanged.
  Alternatives: mocked `JwtDecoder` (rejected: wouldn't prove the real realm/role wiring); `@WithMockUser` (rejected:
  same).
- **D10 — Realm export changes** (all inside `compose/keycloak-realm-export.json`; **stop-and-report gate before
  touching it**, per the user's instruction): `registrationAllowed: true`, `registrationEmailAsUsername: true`, password
  policy (e.g. `length(12) and notUsername and notEmail`), new realm roles `admin` + `guest`, `jordy` moves from
  `jordylab-user` to `admin` (keeps `gamecatalog-scanner`, `offline_access`), **`jordylab-user` role removed** (
  stop-and-report gate — the FE's `SecurityConfigTest`, AGENTS.md files and the ingest-client gate reference it), new
  confidential client `jordylab-backend` (service account enabled, secret from env `KEYCLOAK_BACKEND_SECRET`, service
  account granted `realm-management` → `view-users`, `manage-users`, `view-roles`), `jordylab-host` unchanged (Register
  link appears automatically). Existing dev realms won't pick up export changes — drop and re-import the `keycloak`
  schema (quickstart documents this; same class of gotcha as the known `loginTheme` import note).
- **D11 — Ollama + docs cleanup is part of this feature**: remove `spring-ai-starter-model-ollama`,
  `testcontainers-ollama` + the `OllamaContainer` test bean, the commented compose service, root/BE AGENTS.md Ollama &
  WireGuard guidance, the AI-routing-table Ollama rows; **flag the remaining "Hetzner VPS" mentions as outdated →
  OVHcloud** (draft instruction). Keep pgvector + advisors (FNA MVP3 RAG needs them later; only Ollama goes).
- **D12 — Frontend wiring**: `libs/settings/api` (signal stores per `/angular-signal-store`) + `libs/settings/ui` (users
  page, ai-models page, `settingsRoutes` behind a `roleGuard('admin')`); `AuthService` gains a `roles` signal parsed
  from `tokenParsed.realm_access.roles`; nav groups get role-based visibility (guests: Game Catalog only — Sources item
  admin-only); awaiting-approval page for authenticated users with no app role; user dropdown with AIA actions;
  `eslint.config.mjs` scope list + `tsconfig.base.json` paths + `app.spec.ts` nav assertions updated; same guards in the
  two dev harness apps (guards live in the domain route libs so host + harnesses inherit).

## 4. Verify at implementation time (carried into tasks)

1. **Boot smoke test**: with `spring.ai.model.chat: openai` and both starters present, both `OpenAiChatModel` (
   OpenRouter base URL) and `AnthropicChatModel` beans exist and a plain call works (spring-ai #6036 pattern).
2. **OpenRouter real call**: one manual call with `OPENROUTER_API_KEY` through `ResilientAiService` (behavioral check —
   no key values echoed anywhere).
3. **Email-as-username update caveat** on 26.3 (`UPDATE_PROFILE` changing email → username sync; keycloak
   #13988/#16679) — document the verified outcome.
4. **dasniko 4.3.1 ↔ Boot-managed Testcontainers version alignment** (2.x required; if the BOM pins 1.x, drop to the 3.x
   line).
5. **Model-not-found mapping**: OpenRouter returns a distinguishable error for an unknown model id — wire it to a new
   `ProviderFailureReason.MODEL_NOT_FOUND` and the "model unavailable" flag; verify the exact error shape with one call.
6. **AGENTS.md numbering collision**: `jordylab-be/AGENTS.md` already has a "feature 006 — local multiplayer metadata"
   section (from the 005 branch). The settings sections added by this feature should say "feature 006 — settings
   module"; note the historical label so readers aren't confused (do not renumber merged 005 history).
7. **Existing dev realm**: export changes apply on first import only — quickstart documents dropping/re-importing the
   `keycloak` schema in dev.

## 5. Implementation findings (from T001–T009)

Verified against the live dev stack on 2026-09-27 while implementing Setup + Foundational:

- **Realm import filename must match the realm name.** Keycloak refuses `test-realm.json` containing realm
  `jordylab-test` ("File name / realm name mismatch"); the fixture is therefore
  `src/test/resources/keycloak/jordylab-test-realm.json`.
- **The password policy is enforced at import.** The previously committed dev password failed `length(12)` (
  `invalidPasswordMinLengthMessage`); it is now `nightlab-dev-2026` — chosen so it also passes `notUsername` (a password
  containing `jordy` would be rejected). Any future dev/user password must satisfy
  `length(12) and notUsername and notEmail`.
- **`registrationEmailAsUsername: true` normalises imported users**: on import the `jordy` account's username became
  `jordy@jordylab.local` (its email). Dev login is now email-as-username; the FE/BE docs and quickstart must say so (the
  user menu's email change is the US5/§4.3 open item).
- **The `jordylab-backend` client secret is not committed.** Keycloak generates it on first import; dev setup copies it
  from the admin console into `KEYCLOAK_BACKEND_SECRET` (quickstart step). Service account verified holding exactly
  `realm-management`: `view-users`, `manage-users`, `view-roles`.
- **T008 test infrastructure deviates from the planned dasniko library.** Boot 4.0.3 manages Testcontainers **2.0.3** (
  dasniko 4.x is compatible in principle), but on this Podman-machine setup a *second* extension-managed `@Container`
  fails during start (phantom `localhost:2375` client / OOM). Working shape: **one** extension container (Keycloak, as a
  plain `GenericContainer` with `withCopyFileToContainer` + `Wait.forHttp("/realms/jordylab-test").forPort(8080)`) plus
  Postgres as a `@ServiceConnection` **bean**. No dasniko dependency.
- **Podman VM size is the binding constraint**: the machine has 2048 MB while the dev stack already uses ~610 MB. The
  test Keycloak carries an explicit heap cap (`JAVA_OPTS_APPEND=-Xms128m -Xmx512m -XX:MaxMetaspaceSize=256m`) or it is
  OOM-killed. `podman machine set --memory 8192` is recommended for comfortable container-backed test runs.
- **Test runs need `DOCKER_HOST` *and* a fresh daemon**: a Gradle daemon started before `DOCKER_HOST` was exported does
  not pass it to test workers (`./gradlew --stop` first).
- **`keycloak-js` exposes realm roles** on `tokenParsed.realm_access.roles` with no extra client config — the
  `AuthService.roles` signal (T009) reads them directly.

## 6. Sources

- Spring AI OpenAI chat docs (2.0.1): docs.spring.io — property tables (no `completions-path`), `spring.ai.model.chat`,
  official `openai-java` SDK since M5, `base-url` must include `/v1`
- spring-projects/spring-ai #6036 + PR #6093 (SDK migration removed `*-path` properties; DMR base URL fix),
  #2251/#2415 (1.x path mechanics)
- `pacphi/spring-ai-openrouter-example` (working OpenRouter + Spring AI 2.0 config, `spring.ai.model.chat=openai`)
- OpenRouter `/api/v1/models` — keyless fetch 2026-09-27 (458 models, field shapes as documented in §1.2)
- keycloak-admin-client: Maven Central (26.0.12 latest; pom shows RESTEasy stack; line decoupled from server 26.x
  releases)
- keycloak-js adapter docs + `keycloak.d.ts` (26.2.1): `login({ action })`, `register()`, `onActionUpdate`
- Keycloak Server Admin guide: AIA (`kc_action`), Update Email workflow (requires verification), required actions
- Keycloak GitHub issues #13988/#16679 (email-as-username username sync)
- dasniko/testcontainers-keycloak: releases v4.3.1, quickstart (TC 2.x line, image pinning, realm import)
- Carried forward from `specs/_drafts/settings/research.md`: OpenCode Go vs OpenRouter comparison, `claude-sonnet-5`
  verification, Keycloak current-state analysis

# JordyLab E2E Bug Log

Append-only. One entry per defect. Format: `specs/011-prod-e2e-hardening/contracts/bug-log-entry.md`.
Secrets are always redacted as `<redacted>`. Test plan and coverage matrix: [e2e-test-plan.md](e2e-test-plan.md).

**Status**: OPEN · FIXING · FIXED-LOCAL · DEPLOYED · VERIFIED-PROD · BLOCKED · WONTFIX-QUESTION

**Severity**:
- **S1**: prod unusable / data loss / security
- **S2**: core feature broken or missing
- **S3**: degraded, workaround exists
- **S4**: cosmetic

---

### BUG-001: Frontend lint fails — `app.ts` statically imports lazy-loaded `settings-ui`
- Status: DEPLOYED (`v0.0.1-rc2`) — lint + coverage gates enforced in CI since PR #42
- Severity: S3
- Area/spec: frontend shell / 001, 006
- Env found: local (baseline `bunx nx run-many -t test,lint`)
- Coverage rows: 001-FR-011, 001-FR-012
- Steps to reproduce:
  1. `cd jordylab-fe && bunx nx lint jordylab`
- Expected (cite spec/story): 001 FR-011/FR-012 — lint enforces library boundaries and passes on `main`.
- Actual (logs/screenshot, secrets redacted): `apps/jordylab/src/app/app.ts 19:1 error Static imports of lazy-loaded libraries are forbidden. Library "settings-ui" is lazy-loaded in these files: apps/jordylab/src/app/app.routes.ts @nx/enforce-module-boundaries`
- Root cause: `app.ts` imports from `@jordylab-fe/settings/ui` directly while `app.routes.ts` lazy-loads it, which also pulls the settings bundle into the initial chunk.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod:

### BUG-002: Frontend lint fails — 5 accessibility errors in the Switch add dialog
- Status: DEPLOYED (`v0.0.1-rc2`) — lint + coverage gates enforced in CI since PR #42
- Severity: S3
- Area/spec: gamecatalog-ui / 009
- Env found: local (baseline)
- Coverage rows: 009-US1, G accessibility
- Steps to reproduce:
  1. `cd jordylab-fe && bunx nx lint gamecatalog-ui`
- Expected (cite spec/story): 001 FR-013 (lint green) and accessible forms.
- Actual (logs/screenshot, secrets redacted): `libs/gamecatalog/ui/src/lib/switch-game/switch-game.component.html` — `31:7`, `41:9`, `92:9` label-has-associated-control; `61:13` click-events-have-key-events and interactive-supports-focus. Also one warning: unused `Subject` import in `libs/gamecatalog/api/src/lib/switch-game.store(.spec).ts`.
- Root cause: labels not bound to their inputs; a clickable non-button element without keyboard support.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod:

### BUG-003: CI never runs frontend lint, so boundary and a11y violations reach `main`
- Status: DEPLOYED (`v0.0.1-rc2`) — lint + coverage gates enforced in CI since PR #42
- Severity: S3
- Area/spec: CI / 001
- Env found: both (`.github/workflows/build.yml`)
- Coverage rows: 001-FR-013, 001-SC-003, 001-US0-AS5
- Steps to reproduce:
  1. Read `.github/workflows/build.yml` job `test-frontend`: it runs only `bunx nx run-many --target=test --all`.
  2. Note BUG-001/BUG-002 are on `main` with a green Build.
- Expected (cite spec/story): 001 FR-013 — "Boundary enforcement MUST run in CI, not only locally."
- Actual (logs/screenshot, secrets redacted): no lint step; Build for `e167de8` is green while lint fails.
- Root cause: lint target was never added to the Build workflow.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod:

### BUG-004: CI does not enforce coverage gates (backend JaCoCo, frontend thresholds)
- Status: DEPLOYED (`v0.0.1-rc2`) — lint + coverage gates enforced in CI since PR #42
- Severity: S3
- Area/spec: CI / 001
- Env found: both
- Coverage rows: 001-FR-016a, 001-FR-016b, 001-FR-016c
- Steps to reproduce:
  1. `build.yml` runs `./gradlew test` (not `build`/`check`), so `jacocoTestCoverageVerification` never runs in CI.
  2. Frontend thresholds (`thresholds: { lines: 80 }` in each `vite.config.mts`) apply only when coverage runs; CI runs `nx test` without coverage.
  3. Locally: `./gradlew jacocoTestReport jacocoTestCoverageVerification -x test` after a full test run.
- Expected (cite spec/story): 001 FR-016a/FR-016b — "coverage MUST meet a minimum 80% threshold enforced in CI; the build MUST fail…".
- Actual (logs/screenshot, secrets redacted): first local runs failed the gate (`fna.util`, `fna.rest.controller`, `fna.rest.controller.model` at 0.6) only because 7 tests could not start (BUG-018). With the suite green (Podman 6 GiB) `./gradlew build` incl. `jacocoTestCoverageVerification` passes locally. The defect is only that CI never runs these gates.
- Root cause: CI invokes the test task only.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod:

### BUG-005: Manual `deploy-prod.yml` run without `sha` deploys image tag `sha-` (empty)
- Status: FIXED-LOCAL (PR #39, `6f1e1f9`) — deploy input is a required, validated release tag
- Severity: S3
- Area/spec: CI/CD / 008
- Env found: prod pipeline (code reading)
- Coverage rows: 008-US4, 008-FR-006
- Steps to reproduce:
  1. Actions → Deploy to Production → Run workflow, leave `sha` empty.
  2. `SHA: ${{ github.event.inputs.sha || github.event.workflow_run.head_sha }}` → empty on `workflow_dispatch`; checkout of ref `''` and images `…:sha-`.
- Expected (cite spec/story): input description says "defaults to the latest successful Build run"; 008 US4 one-click redeploy/rollback.
- Actual (logs/screenshot, secrets redacted): no default is computed; not executed (would hit prod) — found by reading `.github/workflows/deploy-prod.yml`.
- Root cause: missing lookup of the latest successful Build SHA (or `required: true`).
- Fix (PR / commit / tag): likely superseded by BUG-008 (input becomes `version`); fix together.
- Regression test added:
- Verified on prod:

### BUG-006: Production runbook points at moved files and wrong CNPG names
- Status: VERIFIED — PR #69 merged; the corrected paths and log commands were run against the cluster
- Severity: S4
- Area/spec: docs / 008
- Env found: both
- Coverage rows: 008-FR-018, 008-US8
- Steps to reproduce:
  1. `docs/runbook.md` §5 line 144 `deploy/k8s/cluster/cert-manager-clusterissuer.yaml` and §9 lines 198–200 `deploy/k8s/cluster/ci-deploy-rbac.yaml` — both now live in `deploy/k8s/bootstrap/`.
  2. §12 line 235 `kubectl -n cnpg-system logs -l cnpg.io/cluster=jordylab-db` — the cluster is `cnpg-cluster` in namespace `jordylab` (verified with `kubectl get pods -A -l cnpg.io/cluster`).
  3. §20 "Fix while you are in deploy-prod.yml" lists kubectl/kustomize pinning that is already done (v1.36.4, v5.8.1).
- Expected (cite spec/story): 008 FR-018 — runbook covers deploy, rollback, logs accurately.
- Actual (logs/screenshot, secrets redacted): commands fail or target nothing when copied.
- Root cause: `deploy/k8s/bootstrap/` split and CNPG rename after the runbook was written.
- Fix (PR / commit / tag): `docs/runbook.md`: ClusterIssuer and CI RBAC paths point at `deploy/k8s/bootstrap/`; the log commands name the real CNPG pod (`cnpg-cluster-1` in `jordylab`) and the operator (`cnpg-cloudnative-pg` in `cnpg-system`). The §20 "fix while you are in deploy-prod.yml" list was already gone.
- Regression test added: none because docs-only
- Verified on prod: verified: `deploy/k8s/bootstrap/cert-manager-clusterissuer.yaml` and `ci-deploy-rbac.yaml` exist; `kubectl -n jordylab logs cnpg-cluster-1 -c postgres` works (2026-10-01)

### BUG-007: Claude PR review prompt references a file that does not exist
- Status: VERIFIED — PR #69 merged; the review prompt lists only files that exist
- Severity: S4
- Area/spec: CI / —
- Env found: both
- Coverage rows: G cross-cutting
- Steps to reproduce:
  1. `.github/workflows/claude-pr-review.yml` prompt tells the reviewer to read `coding-master-prompt.md`; `ls coding-master-prompt.md` → not found. It also lists `garmin-sync-service/AGENTS.md` for Python conventions, while Python code lives in `gamecatalog-scanner/`.
- Expected (cite spec/story): review reads the real convention sources (`.specify/memory/constitution.md`, `.claude/rules/*`, `gamecatalog-scanner/AGENTS.md`).
- Actual (logs/screenshot, secrets redacted): stale reference.
- Root cause: file removed/renamed after the workflow was written.
- Fix (PR / commit / tag): `.github/workflows/claude-pr-review.yml` now lists `gamecatalog-scanner/AGENTS.md`, `garmin-sync-service/AGENTS.md` and `.claude/rules/*.md` instead of the missing `coding-master-prompt.md`.
- Regression test added: none because workflow prompt only
- Verified on prod: verified on the PR that carries it: the review reads the listed files (all exist)

### BUG-008: One-tag release flow (runbook §20) is not implemented
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: CI/CD / 008 (runbook §20, agreed 2026-09-29)
- Env found: both
- Coverage rows: 008-US4, 008-FR-006
- Steps to reproduce:
  1. `ls .github/workflows/release.yml` → missing; `deploy-prod.yml` still deploys on every Build of `main`; input is `sha`, not `version`; `android-release.yml` still on `mobile-v*`.
- Expected (cite spec/story): runbook §20 — releasing is one tag on GitHub; production changes only on a release; rollback by version via *Run workflow*.
- Actual (logs/screenshot, secrets redacted): §20 says "Status: planned. The workflows below are not implemented yet."
- Root cause: planned work never done (Jordy classes it as a bug — spec 011 FR-012b).
- Fix (PR / commit / tag): planned batch `fix/e2e-release-flow` (tasks T040/T041); deploy pauses for Jordy; rulesets + first tag are handoffs.
- Regression test added:
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: the tag-driven flow ran end to end for rc6, rc7 and rc8 (verify → retag → release → approved deploy → publish → apk, all green)

### BUG-009: Ollama is still wired in build, tests, compose and AGENTS.md
- Status: VERIFIED-PROD
- Severity: S4
- Area/spec: shared / 006, 008, 001
- Env found: both
- Coverage rows: 006-FR-017, 006-SC-005, 008-FR-021, 001-SC-005
- Steps to reproduce:
  1. `grep -rn -i ollama jordylab-be/build.gradle.kts jordylab-be/src/test jordylab-be/compose.yaml AGENTS.md`
- Expected (cite spec/story): 006 FR-017 — "All local-LLM (Ollama) support MUST be removed…"; Jordy's decision 2026-09-30 (011 FR-012c).
- Actual (logs/screenshot, secrets redacted): `spring-ai-starter-model-ollama` (build.gradle.kts:48), `testcontainers-ollama` (:73), `OllamaContainer` bean in `TestcontainersConfiguration.java`, commented service in `compose.yaml`, AGENTS.md routing table / infrastructure / gotchas rows.
- Root cause: 006 T025/T043/T045 never executed. Removing the starter also exposed that it silently supplied the `EmbeddingModel` the (unused) pgvector `VectorStore` auto-configuration needs.
- Fix (PR / commit / tag): branch `fix/e2e-remove-ollama` — both Ollama dependencies, the `OllamaContainer` test bean and the compose remnant removed; `PgVectorStoreAutoConfiguration` excluded until a feature uses a VectorStore; AGENTS.md (routing table, infrastructure, reference docs, gotchas), `.claude/README.md`, the `ai-endpoint` skill and the architect memory note updated. `opencode.json`'s local Qwen model (developer tooling, not product) is left alone.
- Regression test added: `JordylabApplicationTests` + `GameCatalogModuleTest` start the full context without Ollama; `grep -ri ollama` outside specs/history/opencode.json returns only "removed" notes
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: backend, frontend, keycloak start and serve without Ollama or an embedding model (the pgvector VectorStore auto-configuration is excluded); `grep -ri ollama jordylab-be jordylab-fe` is empty

### BUG-010: 006 US4 missing — AI calls are not routed per feature (OpenRouter primary, Anthropic fallback)
- Status: VERIFIED-PROD
- Severity: S2
- Area/spec: shared/ai / 006
- Env found: both
- Coverage rows: 006-US4 (+AS1–AS3), 006-FR-011–FR-014, FR-016, SC-004
- Steps to reproduce:
  1. `grep -rn -E "AiFeature\b|OpenRouter" jordylab-be/src/main` → nothing; 006 tasks T024, T026–T030 open.
- Expected (cite spec/story): 006 US4 / FR-011–FR-013 — OpenRouter primary, Anthropic fallback, configuration per AI feature.
- Actual (logs/screenshot, secrets redacted): only the Anthropic path from 001 exists. `application.yaml` already carries `jordylab.ai.gateway.base-url` (OpenRouter) and `jordylab.ai.features.*` defaults, but no Java code reads them.
- Root cause: 006 T024, T026–T030 never implemented. Also found while fixing: the `jordylab.ai.features` keys contain dots and never bound (now bracketed), and 006 research D2's `spring.ai.model.chat: openai` would have switched the Anthropic fallback bean off (each starter matches only its own value).
- Fix (PR / commit / tag): branch `fix/e2e-settings-ai-routing` — Spring AI 2.0.1 GA (+ OpenAI starter; `spring-ai-advisors-vector-store` → `spring-ai-vector-store-advisor`), `AiFeature` registry, `AiModelResolver` port (config default), `ResilientAiService.call(AiFeature, …)`: OpenRouter first, one Anthropic retry on any failure (`MODEL_NOT_FOUND` mapped from OpenRouter's 400 "not a valid model ID", verified), `AiCallCompleted` event + `jordylab.ai.calls` counter, `metrics` actuator endpoint (admin).
- Local E2E 2026-10-01: "Regenerate description" → gateway answered **402 "Insufficient credits. This account never purchased credits"** → one Anthropic retry succeeded (`provider=anthropic`). 402 is now `INSUFFICIENT_CREDITS`. Until the OpenRouter account has credits every call takes the fallback (HANDOFF-08).
- Regression test added: `ResilientAiServiceTest` (11: each failure reason → one fallback retry, timeout, unhealthy/unconfigured gateway, both fail → explicit failure, event + metrics), `AiPropertiesTest` (real yaml binding), `AiGatewayWiringTest` (both chat beans without a selector; real HTTP call to `/api/v1/chat/completions`), call-site tests updated
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: prod backend log `AI call succeeded: feature=gamecatalog.chat.query/answer, provider=openrouter, model=anthropic/claude-haiku-4.5` after the owner added OpenRouter credits (before: 402 → one Anthropic retry)

### BUG-011: 006 US5 missing — no user menu to manage one's own login details
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: shared/auth, shell / 006
- Env found: both
- Coverage rows: 006-US5 (+AS1–AS2), 006-FR-008
- Steps to reproduce:
  1. 006 tasks T031–T033 open; no `UserMenuComponent` in `jordylab-fe/libs/shared/auth`.
- Expected (cite spec/story): 006 US5 / FR-008 — every user can change password, name and email without admin help.
- Actual (logs/screenshot, secrets redacted): only a sign-out button (confirmed in the UI); no way to change password, name or email.
- Root cause: 006 T031–T033 never implemented. Also found: the email field is not on Keycloak's profile page because email is the username; Keycloak (26.4+) changes it only through the `UPDATE_EMAIL` action, which is disabled on prod.
- Fix (PR / commit / tag): `UserMenuComponent` (Angular CDK menu) replaces both sign-out buttons: Change password (`UPDATE_PASSWORD`), Edit name (`UPDATE_PROFILE`), Change email (`UPDATE_EMAIL`), Sign out; `AuthService.requestAction` (web: `keycloak.login({action})`, Android: `kc_action` on the system-browser authorize URL). Prod realm: enable `UPDATE_EMAIL` (logged in `deploy/keycloak/README.md`).
- Regression test added: `user-menu.component.spec.ts` (6), `auth.service.spec.ts` (+2), `app.spec.ts` (menu for every signed-in user, signs out); local 2026-10-01: menu opens, Edit name → Keycloak "Update Account Information"
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: menu shows Change password / Edit name / Change email / Sign out; Change email reaches Keycloak with `kc_action=UPDATE_EMAIL` and asks to re-authenticate (the form itself was not submitted)

### BUG-012: 006 US6 missing — no per-feature AI model selection (AI Models page)
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: settings / 006
- Env found: both
- Coverage rows: 006-US6 (+AS1–AS4), 006-FR-015, SC-003, SC-006
- Steps to reproduce:
  1. `libs/settings/ui/src/lib/settings.routes.ts` has only `users`; 006 tasks T034–T040 open.
- Expected (cite spec/story): 006 US6 — choose a model per AI feature; applies on the next call.
- Actual (logs/screenshot, secrets redacted): feature absent.
- Root cause: story not implemented; depends on BUG-010.
- Fix (PR / commit / tag): branch `fix/e2e-settings-ai-models` — `AiFeatureModelSetting`/`AiFeatureLastRun` entities on the existing `settings` tables (**no new migration** — `V20260928003` already created them, so no deploy pause), `AiModelSettingsService` (`@Primary AiModelResolver`, cache dropped on `AiFeatureModelSettingUpdated`, `AiCallCompleted` → last run in its own transaction), keyless `OpenRouterModelCatalogClient` (TTL cache, stale-on-outage, loud when empty), `/api/settings/ai-models` (list, catalog, PUT), Settings → AI Models page.
- Regression test added: entity tests (11), `OpenRouterModelCatalogClientTest` (5), `AiModelSettingsServiceTest` (12), `SettingsAiModelsControllerTest` (5), `AiModelSelectionIntegrationTest` (SC-003 on Postgres: the next call uses the saved model, last run recorded), `ai-models.store.spec` (7), `ai-models-page.component.spec` (5); local E2E 2026-10-01 against the real catalog: list → picker → save → revert
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: Settings → AI Models lists all features with model, fallback and last-run line (OpenRouter calls today)

### BUG-013: 006 US7 missing — no Ntfy push when someone signs up
- Status: VERIFIED-PROD
- Severity: S3 (lowered from S2 on 2026-09-30 — Jordy unsure, spec priority P3; see test plan Q-04)
- Area/spec: settings / 006, 007
- Env found: both
- Coverage rows: 006-US7, 006-FR-018, 007-FR-016
- Steps to reproduce:
  1. (Corrected 2026-10-01) US7 *is* built — by spec 007: `PendingSignupWatcherService` publishes `UserSignUpPending`, `mobile`'s `MobileNotificationListener` sends it through `NtfyClient` (006 T041/T042 superseded).
  2. Prod: `kubectl -n jordylab logs deploy/ntfy` → `messages_published=0` since ntfy started (2026-09-30 13:34), despite the HANDOFF-02 sign-ups, a still-pending test account and several backend restarts (each re-announces pending users).
- Expected (cite spec/story): 006 FR-018 — admin notified by Ntfy with the pending user's name and email.
- Actual (logs/screenshot, secrets redacted): no push ever sent, and no log line says why — `NtfyClient` skips at DEBUG when base-url or topic is blank.
- Correction (2026-09-30 20:45) — likely invalid, re-verify after BUG-026/BUG-031: the behaviour *is* implemented via spec 007 — `settings/service/PendingSignupWatcherService` polls Keycloak every 5 min and publishes `UserSignUpPending`, and `mobile` sends the Ntfy push (007 FR-016, research D9). Only 006 T041/T042 were never ticked. On prod it currently fails because of BUG-026. Close as invalid once a sign-up push is observed after PR #30.
- Root cause: `NTFY_TOPIC` was never part of the prod secrets (`specs/008…/contracts/secrets-schema.md` has no such key; ntfy runs without auth, `NTFY_BASE_URL` is set) — and the client skipped silently.
- Fix (PR / commit / tag): branch `fix/e2e-ntfy-visible` — startup log names the missing key (never a value), every sent push logged at INFO (title only, no PII); `NTFY_TOPIC` added to the secrets contract. Jordy sets the topic (HANDOFF-07).
- Regression test added: `NtfyClientTest` (+2: missing-key warning without values; enabled + sent log without the body)
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: backend log `Ntfy notifications enabled`, then `Ntfy notification sent: 'New JordyLab sign-up'` at startup for the pending test account; the ntfy server's `messages_published` rose from 0 to 2

### BUG-014: 009 US4 incomplete — Switch detail format and cross-view tests missing
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US4 (+AS1–AS4)
- Steps to reproduce:
  1. 009 tasks T027–T029, T032–T034 open (visibility/purge/chat tests, `hostFormats` in `GameDetailResponse`, format on detail page, filter chip check).
- Expected (cite spec/story): 009 US4 — Switch games behave like any other game, format shown on detail.
- Actual (logs/screenshot, secrets redacted): partial; exact UI gaps to be confirmed in US3 testing.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): branch `fix/e2e-switch-detail`: `hostFormats` on the detail response, "Format · Physical/Digital" on the detail page; T034 (filter chips) checked in the browser after deploy.
- Regression test added: `GameRepositoryTest` (grid, hosts, platforms, chat filter on PostgreSQL), `ReconciliationServiceTest` (manual installations never hidden or purged), `GameQueryServiceTest.detailMapsManualHostsToTheirFormatAndOmitsScannedHosts`, `game-detail.component.spec.ts`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: detail page of a Digital game shows `Format · Digital` and the admin section

### BUG-015: 009 US2 incomplete — no manual-fallback add form or relink UI
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US2 (+AS1–AS2), 009-FR-004
- Steps to reproduce:
  1. 009 tasks T039–T040 open.
- Expected (cite spec/story): 009 FR-004 — add a game manually when search finds nothing; link it later.
- Actual (logs/screenshot, secrets redacted): feature absent in the UI.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): branch `fix/e2e-switch-detail`: "No IGDB match → Add manually" prompt on the add page; admin relink search on the detail page.
- Regression test added: `switch-game.component.spec.ts`, `switch-game.store.spec.ts`, `game-detail.store.spec.ts`, `game-detail.component.spec.ts`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: Switch page offers Search IGDB / Add Manually tabs and the detail page the relink-to-IGDB control

### BUG-016: 009 US3 missing — bulk add by pasting a list
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US3 (+AS1–AS2), 009-FR-005, 009-SC-002
- Steps to reproduce:
  1. `SwitchGameController` has no `/bulk/*` endpoints; 009 tasks T041–T047 open.
- Expected (cite spec/story): 009 FR-005 — paste a list, review matches, add the confirmed ones.
- Actual (logs/screenshot, secrets redacted): feature absent.
- Root cause: story not implemented.
- Fix (PR / commit / tag): branch `fix/e2e-switch-bulk-add` — `POST /switch/bulk/preview` + `/bulk/confirm` (`SwitchBulkService`, one transaction per line), page `/games/switch/bulk`, IGDB calls paced to 4/s.
- Regression test added: `SwitchBulkServiceTest`, `SwitchGameControllerTest` (bulk), `SwitchGameControllerSecurityTest` (guest 403), `switch-bulk.store.spec.ts`, `switch-bulk.component.spec.ts`; local E2E 2026-10-01 (5 lines → 2 added, duplicate collapsed, DLC flagged, re-paste → already in catalog)
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: `Paste a list` → `Find matches` for `Mario Kart 8 Deluxe` returned `Match … (2017)` and `Add 1 games` (not submitted)

### BUG-017: 009 US5 incomplete — no edit/remove on the detail page; guest-403 tests missing
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US5 (+AS1–AS3)
- Steps to reproduce:
  1. 009 tasks T052–T053 open (`PATCH/DELETE /switch/games/{id}` exist in the backend).
- Expected (cite spec/story): 009 US5 — admin edits/removes from the detail page; guests get 403 on writes.
- Actual (logs/screenshot, secrets redacted): UI controls absent; guest-403 not covered by tests.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): branch `fix/e2e-switch-detail`: admin-only format change and two-step remove on the detail page.
- Regression test added: `SwitchGameControllerSecurityTest` (guest 403 on search/add/edit/remove against the real `SecurityConfig`), `game-detail.component.spec.ts`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: `Remove from catalog` → two-step confirm → the game left the catalog (213 → 212 titles)

### BUG-018: Full backend suite fails 7 tests on this Mac (Podman) — `RoleMatrixTest`, `GuestChatLimitIntegrationTest`
- Status: FIXED-LOCAL (environment; README documents Podman ≥ 6 GiB, PR #43)
- Severity: S4
- Area/spec: backend test infra / 006
- Env found: local only (CI Build for `e167de8` is green)
- Coverage rows: 006-SC-001 (role matrix evidence)
- Steps to reproduce:
  1. With `DOCKER_HOST` set to the Podman socket and `TESTCONTAINERS_RYUK_DISABLED=true`: `./gradlew build` → `561 tests completed, 7 failed` (reproduced twice).
  2. `./gradlew test --tests '*RoleMatrixTest' --tests '*GuestChatLimitIntegrationTest'` → BUILD SUCCESSFUL.
- Expected (cite spec/story): deterministic, isolated tests (constitution IV).
- Actual (logs/screenshot, secrets redacted): `ContainerLaunchException: Could not create/start container … NoHttpResponseException: localhost:2375 failed to respond` when these classes start their own application context late in the run.
- Root cause (confirmed): the Podman VM (2 GiB RAM) runs out of resources when these classes start extra Postgres/Keycloak containers after many cached contexts; not a product defect. To confirm by raising VM memory (Jordy's machine setting) or sharing containers across contexts.
- Fix (PR / commit / tag): Podman VM memory raised 2048 → 6144 MiB with Jordy's OK (2026-09-30); third full `./gradlew build` → BUILD SUCCESSFUL, 561/561, JaCoCo gate green. Follow-up: note the memory requirement in `jordylab-be/AGENTS.md` (Testcontainers on Podman section).
- Regression test added:
- Verified on prod: n/a (local)

### BUG-019: Keycloak test container version drifts from production
- Status: VERIFIED
- Severity: S4
- Area/spec: backend tests / 006, 008
- Env found: local
- Coverage rows: 006-SC-001
- Steps to reproduce:
  1. `KeycloakIntegrationTest.java:51` uses `quay.io/keycloak/keycloak:26.3.2`; production runs Keycloak 26.7.4 (devops agent, first deploy 2026-09-30).
- Expected (cite spec/story): integration tests run against the same major/minor Keycloak as prod.
- Actual (logs/screenshot, secrets redacted): 4 minor versions apart.
- Root cause: image pin not updated with the prod upgrade.
- Fix (PR / commit / tag): `KeycloakIntegrationTest` and local `compose.yaml` on `quay.io/keycloak/keycloak:26.7.4`, the version prod builds from (`deploy/containers/keycloak/Containerfile`) and `KeycloakAdminClientRealmExportIntegrationTest` already uses.
- Regression test added: none because version alignment; `RoleMatrixTest` (6) + `GuestChatLimitIntegrationTest` (1) pass on 26.7.4
- Verified on prod: 2026-10-01: CI `test-backend` (RoleMatrixTest, GuestChatLimitIntegrationTest) green on Keycloak 26.7.4 — test-only change

### BUG-020: Scanner client downloaded from production embeds `http://localhost:8180` as its Keycloak URL
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog ingest / 003, 008
- Env found: prod (config)
- Coverage rows: 003-US1, 003-FR-002, 008-US1, 002-US1
- Steps to reproduce:
  1. As admin on https://jordylab.be → Games → Sources → download the Steam client.
  2. `grep -E '^(KEYCLOAK_URL|BACKEND_URL)' jordylab-scan-steam.py`
- Expected (cite spec/story): 003 FR-002 — one-time browser login against the real identity provider, then unattended runs.
- Actual (logs/screenshot, secrets redacted): `ClientService` renders `KEYCLOAK_URL` from `@Value("${jordylab.script.keycloak-url:http://localhost:8180}")`; no config sets `jordylab.script.keycloak-url` (not in `application*.yaml`, not in the `backend-config` ConfigMap, which only sets `KEYCLOAK_URL`). Rendered constants take precedence over the scanner's env vars (`gamecatalog-scanner/src/jordylab_scan/config.py` `pick`), so there is no workaround besides editing the file.
- Confirmation (HANDOFF-03): client downloaded from https://jordylab.be at 20:37 has `BACKEND_URL = "https://jordylab.be"` and `KEYCLOAK_URL = "http://localhost:8180"`; `login` printed a device URL on `http://localhost:8180/realms/jordylab/device` (Jordy's local Keycloak), so the prod client can never obtain a prod token.
- Root cause: the scanner's Keycloak URL property was never bound to `${KEYCLOAK_URL}` for prod.
- Fix (PR / commit / tag): candidate — `jordylab.script.keycloak-url: ${KEYCLOAK_URL}` in shared config (or prod profile) + test that the rendered client carries the configured URL. Config change → deploy pauses for Jordy.
- Regression test added:
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: the downloaded client has the prod Keycloak URL; the owner's device-code login and EmuDeck/Steam scans against prod worked (HANDOFF-10/11)

### BUG-021: Frontend nginx — no compression, no HSTS, version leak, `index.html` cacheable, headers lost on assets, wrong manifest MIME
- Status: VERIFIED-PROD (CSP still open as follow-up)
- Severity: S3
- Area/spec: frontend container / 008, 007
- Env found: prod
- Coverage rows: 008-US1, 008-FR-008, 007-US7, 007-FR-008, smoke A4/A6/A7
- Steps to reproduce:
  1. `curl -sI https://jordylab.be/` → no `Strict-Transport-Security`, no `Content-Security-Policy`, `server: nginx/1.30.5`, no `Cache-Control`.
  2. `curl -sI -H 'Accept-Encoding: gzip, br' https://jordylab.be/main-W5ZXHQR3.js` → no `Content-Encoding`; also no `X-Content-Type-Options`/`X-Frame-Options` on assets.
  3. `curl -sI https://jordylab.be/manifest.webmanifest` → `application/octet-stream`.
- Expected (cite spec/story): smoke A4/A6 (HSTS, nosniff on all responses, compressed hashed assets, `index.html` not cached); 007 FR-008 installable home-screen web app (manifest served as `application/manifest+json`).
- Actual (logs/screenshot, secrets redacted): as above; `favicon.svg`/`icons/*` (not content-hashed) get `max-age=31536000, immutable`.
- Root cause: `deploy/containers/frontend/nginx.conf` has no `gzip`, no `server_tokens off`, no HSTS/CSP, no `Cache-Control: no-cache` for `index.html`, and the asset `location` sets its own `add_header`, which drops the server-level headers (nginx inheritance rule); default `mime.types` lacks `webmanifest`.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod: 2026-10-01 on `v0.0.1-rc2`: `/` → HSTS, nosniff, `Cache-Control: no-cache`, `server: nginx` (no version); `main-*.js` → gzip + `immutable` + HSTS; manifest → `application/manifest+json`

### BUG-022: Keycloak `master` realm is publicly reachable (login, account console, token endpoint)
- Status: VERIFIED-PROD
- Severity: S3
- Area/spec: gateway / 008
- Env found: prod
- Coverage rows: 008-FR-009, smoke A8
- Steps to reproduce:
  1. `curl -s https://jordylab.be/auth/realms/master/.well-known/openid-configuration` → 200 JSON.
  2. `https://jordylab.be/auth/realms/master/account` → 200 Keycloak account console.
- Expected (cite spec/story): 008 FR-009 — Keycloak admin surfaces MUST NOT be publicly reachable; only the `jordylab` realm is needed publicly.
- Actual (logs/screenshot, secrets redacted): the admin console UI and admin REST API are not routed (`/auth/admin/*` falls back to the SPA — good), but the master realm's login and token endpoints accept password attempts for the Keycloak admin account from the internet.
- Root cause: Gateway route matches `/auth/realms` for every realm.
- Fix (PR / commit / tag): restrict the route to `/auth/realms/jordylab` (+ `/auth/resources`); verify the admin still manages Keycloak over Tailscale/port-forward (runbook).
- Regression test added:
- Verified on prod: 2026-10-01 on `v0.0.1-rc2`: `/auth/realms/master/{.well-known/openid-configuration,account}` → SPA fallback (HTML); jordylab realm issuer still served

### BUG-023: No base backup exists yet and the restore drill was never performed
- Status: VERIFIED — base backups running (manual + daily 03:00 UTC); restore drill passed 2026-10-01
- Severity: S2
- Area/spec: database / 008
- Env found: prod
- Coverage rows: 008-US5, 008-FR-014, 008-FR-015, 008-SC-004
- Steps to reproduce:
  1. `kubectl -n jordylab get scheduledbackup,backup` → `cnpg-daily-backup` (`0 0 3 * * *`) and `cnpg-weekly-backup` with `lastSchedule=never`, `immediate=false`; no `Backup` objects.
  2. `kubectl -n jordylab get cluster cnpg-cluster` → `ContinuousArchiving=True` (WAL is shipped).
- Expected (cite spec/story): 008 FR-015 — "A restore drill MUST be performed and documented before go-live"; FR-014 continuous backups.
- Actual (logs/screenshot, secrets redacted): WAL archiving works but there is no base backup to restore from until 03:00 on 2026-10-01; no drill recorded (008 T073 open).
- Root cause: `immediate: false` on the schedule and the drill deferred at go-live.
- Fix (PR / commit / tag): on-demand `manual-backup-20261001` (approved by Jordy); the scheduled daily backup ran at 03:00 UTC; restore-drill manifest + runbook §15 in PR #51; drill run 2026-10-01 (approved by Jordy).
- Regression test added: none because infrastructure procedure
- Verified on prod: 2026-10-01: `cnpg-restore-drill` recovered from the daily base backup + WAL, Ready in 1 min 55 s (SC-004: < 30 min); row counts identical to production (128 games, 6 Keycloak users, 20 Flyway migrations); drill cluster and volume deleted afterwards. Logged in runbook §15.

### BUG-024: `EnvironmentProfileGuard` does not fail first — missing profile surfaces as a datasource error
- Status: FIXED-LOCAL (PR #43, `b7bc402`) — verified: bootRun without profile stops with the guard's message
- Severity: S4
- Area/spec: shared config / 008
- Env found: local
- Coverage rows: 008-FR-002, 008-US2
- Steps to reproduce:
  1. Start the backend without `SPRING_PROFILES_ACTIVE` (e.g. the old `.claude/launch.json` command).
- Expected (cite spec/story): 008 FR-002 — "Starting with neither `local` nor `prod` active fails fast" with the guard's clear message.
- Actual (logs/screenshot, secrets redacted): `BeanCreationException … 'url' must start with "jdbc"` from Flyway/Hikari; the guard's message never appears.
- Root cause: the guard is a `@Component` with `@PostConstruct`, created after auto-configured infrastructure beans. It must run earlier (e.g. an `EnvironmentPostProcessor` or `ApplicationEnvironmentPreparedEvent` listener).
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod: n/a

### BUG-025: `.claude/launch.json` starts the backend without the `local` profile
- Status: FIXED-LOCAL (working tree; ships with the next dev-tooling batch)
- Severity: S4
- Area/spec: dev tooling / 008
- Env found: local
- Coverage rows: 008-US2
- Steps to reproduce:
  1. `preview_start jordylab-be` → exits in 5 s with BUG-024's datasource error.
- Expected (cite spec/story): `docs/environments.md` — local runs use profile `local`.
- Actual (logs/screenshot, secrets redacted): `.env` defines no `SPRING_PROFILES_ACTIVE` and the command didn't set it. (Separately, `.env` line 13 was `NTFY_TOKEN` without `=`, which broke `source .env`; the agent changed it to `NTFY_TOKEN=` — local file, not in git.)
- Root cause: launch command missing the profile.
- Fix (PR / commit / tag): `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` in `.claude/launch.json`; local backend then starts and `/actuator/health` → UP.
- Regression test added: none because dev-tooling config
- Verified on prod: n/a

### BUG-026: Settings → Users fails on prod (503) — Keycloak Admin REST called through the public URL
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: settings / 006, 008
- Env found: prod (reported by Jordy during HANDOFF-02)
- Coverage rows: 006-US2 (+AS1–AS5), 006-FR-006, 006-SC-002, 007-FR-016
- Steps to reproduce:
  1. As admin on https://jordylab.be → Settings → Users.
- Expected (cite spec/story): 006 US2 — admin lists pending/approved/rejected users and approves them.
- Actual (logs/screenshot, secrets redacted): page shows "Failed to load users."; `GET /api/settings/users?status=ALL` and `/pending-count` → 503. Backend: `KeycloakUnavailableException: Keycloak admin response unreadable` at `KeycloakAdminClient.readTree` (also every 5 min from `PendingSignupWatcherService`). `curl https://jordylab.be/auth/admin/realms/jordylab/users` → `200 text/html` (SPA fallback).
- Root cause: `jordylab.settings.keycloak.server-url: ${KEYCLOAK_URL}` = public `https://jordylab.be/auth`; the Gateway does not route `/auth/admin/**` (by design, 008 FR-009), so the admin client parsed `index.html`. Local works because `KEYCLOAK_URL` there points straight at Keycloak.
- Fix (PR / commit / tag): PR #30 (`1dc4375`) — prod uses `${KEYCLOAK_INTERNAL_URL}` = `http://keycloak:8080/auth` (backend-config).
- Regression test added: `jordylab-be/src/test/java/dev/jordy/jordylab/shared/config/ProdKeycloakUrlConfigurationTest.java`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: signed in as admin, Settings → Users loads the user lists (no 503/403)

### BUG-027: No documented way to give a local user the admin role
- Status: FIXED-LOCAL (PR #43) — README "Your own local admin account"
- Severity: S4
- Area/spec: docs / 006
- Env found: local (reported by Jordy during HANDOFF-03)
- Coverage rows: 006-US2
- Steps to reproduce:
  1. Register a new user on http://localhost:4200 → it is pending with no role; nothing in README/AGENTS says how to make it admin.
- Expected (cite spec/story): a local-dev instruction (the first admin can't be approved from the UI).
- Actual (logs/screenshot, secrets redacted): agent granted it with `kcadm.sh add-roles -r jordylab --uusername <email> --rolename admin` inside `jordylab-be-keycloak-1`, using the container's own `KC_BOOTSTRAP_ADMIN_*` env.
- Root cause: missing docs.
- Fix (PR / commit / tag): add a "Make a local user admin" snippet to `README.md` / `jordylab-be/AGENTS.md`.
- Regression test added: none because docs-only
- Verified on prod: n/a

### BUG-028: Nothing grants the `gamecatalog-scanner` role — only the seeded dev user can scan
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2 (provisional)
- Area/spec: auth / 003, 006
- Env found: local (HANDOFF-03)
- Coverage rows: 003-US1, 003-FR-003, 002-US1
- Steps to reproduce:
  1. Register a new local user, give it `admin` (BUG-027 procedure), download the Steam client from localhost, `login`, `scan`.
- Expected (cite spec/story): 003 FR-002/FR-003 — the owner logs in once and scans; the token carries only the scanner role.
- Actual (logs/screenshot, secrets redacted): `[jordylab] forbidden (HTTP 403); the account lacks the gamecatalog-scanner role`.
- Root cause: `gamecatalog-script` has `fullScopeAllowed=false` with a scope mapping for `gamecatalog-scanner`, so the token only carries that role **if the user holds it**. Only the dev export's seeded `jordy` user has it; `realm-prod.json` has no users, the 006 approval flow grants only `guest`, and no doc says to assign it. Agent assigned it locally via kcadm to unblock HANDOFF-03.
- Follow-up (21:11): after granting the role and `reauth`, the first `scan` still got 403; the next run passed — backend debug log shows `Granted Authorities=[ROLE_offline_access, …, ROLE_gamecatalog-scanner]` and `Secured POST /api/gamecatalog/ingest/check`. The first 403 was most likely an access token minted before the role change took effect; not reproducible.
- Fix (PR / commit / tag): Jordy (Q-07): admin includes all JordyLab roles → `admin` composite of `guest` + `gamecatalog-scanner` in both realm files + kcadm on the live prod realm (realm change → Jordy confirms deploy).
- Regression test added:
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: the owner's device-code login on prod yields the scanner role: Steam and EmuDeck scans were accepted (148 + 5 installations)

### BUG-029: Local Settings → Users can't work — the dev realm has no fixed `jordylab-backend` secret
- Status: FIXED-LOCAL (PR #43, guard test #44) — fixed dev secret; Jordy's local Keycloak aligned with the README command
- Severity: S3
- Area/spec: settings / 006, 008
- Env found: local
- Coverage rows: 006-US2, 008-US2
- Steps to reproduce:
  1. Fresh local stack; `.env` without `KEYCLOAK_BACKEND_SECRET`; start the backend.
- Expected (cite spec/story): 008 US2 / `docs/environments.md` — the local environment runs every feature.
- Actual (logs/screenshot, secrets redacted): `KeycloakAdminClient.refreshToken … 401 Unauthorized` every 5 min (pending-sign-up watcher) and on Settings → Users.
- Root cause: `jordylab-be/compose/keycloak-realm-export.json` has no `secret` on the confidential `jordylab-backend` client, so Keycloak generates a random one on import; `application.yaml` reads `${KEYCLOAK_BACKEND_SECRET:}` (empty default) and nothing documents how to obtain it.
- Workaround (2026-09-30): agent read the generated secret from local Keycloak via kcadm and appended `KEYCLOAK_BACKEND_SECRET` to `jordylab-be/.env` without printing it.
- Fix (PR / commit / tag): candidate — a fixed, clearly dev-only secret in the dev export + `application-local.yaml` (gitleaks allowlist), or a documented one-liner in the README (with BUG-027).
- Regression test added:
- Verified on prod: n/a

### BUG-030: Prod has no download-link signing secret — APK download links can't be issued
- Status: VERIFIED-PROD (2026-10-04)
- Severity: S3
- Area/spec: mobile / 007
- Env found: prod
- Coverage rows: 007-US1, 007-FR-002, 007-SC-005
- Steps to reproduce:
  1. Key names in `deploy/k8s/overlays/prod/secrets.sops.yaml` (plaintext in git): no `MOBILE_DOWNLOAD_LINK_SECRET`; `application.yaml` defaults `jordylab.mobile.download-link.secret` to empty.
- Expected (cite spec/story): 007 FR-002 — approved users get a short-lived signed download link.
- Actual (logs/screenshot, secrets redacted): `DownloadLinkService.sign` throws `IllegalStateException("jordylab.mobile.download-link.secret must be configured")` → link issuance fails (safe: it never signs with an empty key). Not yet user-visible because no APK has been published.
- Root cause: secret never added at go-live.
- Fix (PR / commit / tag): Jordy adds `MOBILE_DOWNLOAD_LINK_SECRET` to `secrets.sops.yaml` (manual step in the Android setup instructions).
- Regression test added: none because secret provisioning
- Verified on prod: 2026-10-04: from the owner's Android phone the signed download link was issued and the APK downloaded and installed (MRB-04)

### BUG-031: Backend service-account token carries no realm-management roles — Admin REST API 403
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: settings / 006
- Env found: both (surfaced on prod after DEPLOY-01; reproduced locally)
- Coverage rows: 006-US2 (+AS1–AS5), 006-FR-006, 006-SC-002, 007-FR-016
- Steps to reproduce:
  1. After PR #30, prod backend log: `KeycloakUnavailableException: Keycloak admin GET /users?max=1000 failed … Caused by: HttpClientErrorException$Forbidden: 403 Forbidden: "{"error":"HTTP 403 Forbidden"}"`.
  2. Locally: client-credentials token for `jordylab-backend` → claims `realm_access: null, resource_access: null`; `GET /admin/realms/jordylab/users` → 403.
- Expected (cite spec/story): 006 US2 — Settings → Users lists and manages users.
- Actual (logs/screenshot, secrets redacted): as above. Live prod and local realm: service account holds `view-users`, `manage-users`, `view-roles`; client `fullScopeAllowed=false`; no scope mappings.
- Root cause: with `fullScopeAllowed=false` roles only reach the token when scope-mapped; the realm files never mapped them. `KeycloakIntegrationTest` uses `src/test/resources/keycloak/jordylab-test-realm.json`, which has `fullScopeAllowed=true`, so tests never saw it. Settings → Users has therefore never worked outside tests.
- Fix (PR / commit / tag): PR #31 — `clientScopeMappings.realm-management` for `jordylab-backend` (least privilege) + kcadm for the live realm. Local Keycloak already patched via kcadm. (Agent's local token re-check after the patch was blocked by the permission classifier; verification will come from the prod Users page after deploy.)
- Regression test added: `jordylab-be/src/test/java/dev/jordy/jordylab/settings/RealmConfigurationTest.java`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: signed in as admin, Settings → Users lists Pending (1) and Approved (3) — the Admin REST API answers

### BUG-032: OpenCode agents point at a model that doesn't exist — none of them can start
- Status: VERIFIED — all four OpenCode agents start (PR `fix/e2e-opencode-agent-models`)
- Severity: S4
- Area/spec: dev tooling / —
- Env found: local (OpenCode 1.18.32)
- Coverage rows: G cross-cutting
- Steps to reproduce:
  1. `opencode run "Delegate to the jordylab-devops subagent …"`.
- Expected (cite spec/story): `.opencode/agents/*` mirror `.claude/agents/*` and work (dual-agent-config).
- Actual (logs/screenshot, secrets redacted): `Error: Model not found: anthropic/claude-sonnet-4-6`; all 4 files in `.opencode/agents/` use that model. `opencode/claude-sonnet-5-5` exists but returns "Insufficient account funds" (OpenCode Zen); the configured OpenCode Go models work.
- Root cause: model id copied from Claude naming without checking `opencode models`.
- Fix (PR / commit / tag): devops → `opencode-go/kimi-k2.7-code` (PR #33); architect and code-reviewer → `opencode-go/glm-5.2` (the `plan` model in `opencode.json`), test-writer → `opencode-go/deepseek-v4.1-flash` (the `build` model). The three still named the non-existent `anthropic/claude-sonnet-4-6` on `main`. The repo-wide default `model` in `opencode.json` (`anthropic/claude-sonnet-5`, also unresolvable) now follows the `build` model, `opencode-go/deepseek-v4.1-flash`; `opencode run` answers with it (2026-10-02).
- Regression test added: none because agent config (verified with `opencode run` / `claude -p`)
- Verified on prod: n/a (developer tooling). 2026-10-01: `opencode run "Use the <agent> subagent … reply ok"` → `ok` for architect, code-reviewer and test-writer (before: `Model not found: anthropic/claude-sonnet-4-6`)

### BUG-033: Approving a user fails on prod (503) — backend service account lacks `view-realm`
- Status: VERIFIED-PROD
- Severity: S2
- Area/spec: settings / 006
- Env found: prod (Jordy, HANDOFF-02: "Keycloak is unavailable right now — try again shortly")
- Coverage rows: 006-US2 (+AS1–AS5), 006-FR-006, 006-SC-002
- Steps to reproduce:
  1. As admin on https://jordylab.be → Settings → Users → Approve on a pending user.
- Expected (cite spec/story): 006 US2 — admin approves a sign-up into `guest`.
- Actual (logs/screenshot, secrets redacted): `POST /api/settings/users/{id}/approve → 503`, nothing in the backend log. Reproduced with `KeycloakAdminClientRealmExportIntegrationTest` (Keycloak 26.7.4 + real dev export): `KeycloakUnavailableException: Keycloak admin GET /roles/guest failed … 403 Forbidden`.
- Root cause: the realm files granted `realm-management` → `view-roles`, which is not a built-in role (on prod it has no `${role_…}` description — the import created an empty custom role). Reading a realm role needs `view-realm`. The existing `KeycloakIntegrationTest` uses a separate test realm with roles granted in code, so it never exercised the real permissions. The 503 handler also logged nothing.
- Fix (PR / commit / tag): PR #35 — `view-realm` in both realm files + scope mapping; `SettingsUsersController` logs the failing Admin REST call. Live realm: `view-realm` granted to `service-account-jordylab-backend` and added to its scope mapping via kcadm; backend restarted by DEPLOY-03 → 0 Keycloak admin errors since.
- Regression test added: `jordylab-be/src/test/java/dev/jordy/jordylab/settings/rest/client/KeycloakAdminClientRealmExportIntegrationTest.java`
- Verified on prod: 2026-10-01 — Jordy approved a test account in Settings → Users on https://jordylab.be; kcadm (read-only) shows `jordylab.frown533@…` enabled with `guest`

### BUG-034: The admin's own account is listed as a pending sign-up
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S3
- Area/spec: settings / 006
- Env found: prod (Jordy, screenshot of Settings → Users)
- Coverage rows: 006-US2, 006-FR-006
- Steps to reproduce:
  1. As admin → Settings → Users: `jordy.swinnen@pm.me` appears under Pending with Approve/Reject.
- Expected (cite spec/story): 006 FR-006 — users listed by status; an admin is not a pending sign-up.
- Actual (logs/screenshot, secrets redacted): Pending (3) includes the admin account.
- Root cause: `deriveStatus` returned APPROVED only for a direct `guest` mapping; admins hold `admin` (composite incl. guest, Q-07) but not `guest` directly.
- Fix (PR / commit / tag): PR #35 (`7decf8d`) — `guest` or `admin` → APPROVED.
- Regression test added: `KeycloakUserAdministrationServiceTest.anAdminWithoutADirectGuestRoleIsApprovedNotPending`
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`: signed in as admin, the admin's own account is under Approved, not Pending

### BUG-035: `src/test/resources/application.yaml` replaced the main `application.yaml` in every test
- Status: FIXED-LOCAL (PR #47) — test infra, no prod component
- Severity: S3
- Area/spec: backend test infra / 001
- Env found: local (while writing the BUG-036 regression test)
- Coverage rows: 001-FR-016b, G cross-cutting
- Steps to reproduce:
  1. Add any property to `src/main/resources/application.yaml` and assert it in a `@SpringBootTest` → it is absent.
- Expected (cite spec/story): tests exercise the real shared configuration.
- Actual (logs/screenshot, secrets redacted): the test file (only an Anthropic test key + model) has the same classpath name, so Spring loads it *instead of* the main file; every context test ran without the shared config.
- Root cause: same-named resource on the test classpath shadows the main one.
- Fix (PR / commit / tag): delete the test file; set the two test-only properties as Gradle test system properties.
- Regression test added: `MobileReleaseUploadIntegrationTest` depends on the main file's multipart limit, so it fails if the shadowing returns.
- Verified on prod: n/a

### BUG-036: APK upload to `POST /api/mobile/releases` answers 403 — 1 MB multipart limit, errors masked by `/error` denyAll
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: mobile / 007
- Env found: prod (release `v0.0.1-rc1`, apk job: `curl: (22) … 403` after the token request succeeded)
- Coverage rows: 007-FR-001, 007-US3, 007-SC-004
- Steps to reproduce:
  1. As `mobile-release-publisher`, upload a multi-MB file to `POST /api/mobile/releases`.
- Expected (cite spec/story): 007 FR-001 — CI publishes signed releases; invalid uploads get a clear error.
- Actual (logs/screenshot, secrets redacted): 403, empty body, nothing logged. Reproduced by `MobileReleaseUploadIntegrationTest` (real Tomcat): 2 MB upload by a publisher → 403.
- Root cause: no `spring.servlet.multipart` limits → Spring's 1 MB default rejects the APK; the exception is forwarded to `/error`, which `SecurityConfig` denies (`anyRequest().denyAll()`), so every server-side error became a bare 403. A non-APK file also escaped as an unmapped `IllegalArgumentException`.
- Fix (PR / commit / tag): multipart limits 200 MB / 210 MB; `DispatcherType.ERROR` permitted; `InvalidApkException` → 400 `INVALID_APK`.
- Regression test added: `jordylab-be/src/test/java/dev/jordy/jordylab/MobileReleaseUploadIntegrationTest.java`
- Verified on prod: 2026-10-02: the release workflow's `apk` job uploaded the signed APK to `POST /api/mobile/releases` on prod for rc6, rc7 and rc8 (all green, versionCodes 106–108)

### BUG-037: APK signing-certificate check only reads v1 (JAR) signatures; release builds are v2/v3-only
- Status: VERIFIED-PROD
- Severity: S2
- Area/spec: mobile / 007
- Env found: code reading (while fixing BUG-036)
- Coverage rows: 007-FR-003, 007-FR-001
- Steps to reproduce:
  1. `ApkSigningCertificateReader` opens the APK as a verified `JarFile` (v1 scheme). AGP 8.13 with `minSdkVersion = 29` signs release builds with v2/v3 only by default.
- Expected (cite spec/story): 007 FR-003 — every published APK's certificate is checked against the release certificate.
- Actual (logs/screenshot, secrets redacted): a real release APK would be rejected as "APK is not signed" (the class's own javadoc notes it was never tested on a real APK).
- Root cause: v1-only reader vs v2/v3-only signing. AGP ignores `enableV1Signing = true` for minSdk ≥ 24, so signing v1 as well (PR #48/#49) was not possible; rc3's CI check proved the APK had no v1 signature.
- Fix (PR / commit / tag): PR #52 — `ApkSigningCertificateReader` uses apksig `ApkVerifier` (v1/v2/v3, checked from API 29), requires a verified APK with exactly one signer; `release.yml` verifies with `apksigner verify` + certificate digest compare. (PR #48/#49 tried v1+v2 signing and were superseded.)
- Regression test added: `ApkSigningCertificateReaderTest` — v1-signed, v2/v3-only, two signers (rejected), tampered after signing (rejected), not an APK (rejected)
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: the `apk` job built, signed and verified the release APK and published it: `mobile.mobile_release` row `0.0.1-rc6` (versionCode 106, 12.8 MB); the backend read the v2 signing certificate with apksig and accepted it against the pin

### BUG-038: Guests see the admin-only "Refresh" / "Regenerate" buttons on the game detail page
- Status: VERIFIED-PROD (2026-10-04)
- Severity: S4
- Area/spec: gamecatalog / 004, 006
- Env found: code reading (while adding the Switch admin controls, 009 T052)
- Coverage rows: 006-FR-guest-readonly
- Steps to reproduce:
  1. Log in as a guest, open any game's detail page.
- Expected (cite spec/story): guests have read-only access to the Game Catalog; write actions are not offered.
- Actual (logs/screenshot, secrets redacted): the refresh/regenerate buttons render; clicking one gets a 403 from `/api/gamecatalog/**` (admin-only), so nothing breaks but the UI offers an action the guest can't take.
- Root cause: the buttons were never gated on the admin role.
- Fix (PR / commit / tag): branch `fix/e2e-switch-detail` — buttons gated on `AuthService.isAdmin`.
- Regression test added: `game-detail.component.spec.ts` (guest sees no refresh/regenerate)
- Verified on prod: 2026-10-04 on `v0.0.1-rc9`, signed in as the guest account in the browser pane: the game detail page shows no Refresh/Regenerate/Remove/Relink controls; admin-only routes (`/settings/users`, `/fna/**`, `/games/switch`) redirect a guest to the library; the sidebar offers only Library and Chat; 0 console errors

### BUG-039: CORS allow-list has no PATCH — the Switch edit fails from the Android app
- Status: VERIFIED-PROD
- Severity: S3
- Area/spec: shared / 007, 009
- Env found: code reading (`SecurityConfig.corsConfigurationSource`)
- Coverage rows: 007-FR-mobile-api, 009-US5
- Steps to reproduce:
  1. In the Android app (WebView origin `https://localhost`), change a Switch game's format: the request is `PATCH /api/gamecatalog/switch/games/{id}`, cross-origin there.
- Expected (cite spec/story): 007 — the app is the same web build and every feature works in it; 009 US5 — admin edits a Switch game.
- Actual (logs/screenshot, secrets redacted): the preflight answer lists only GET, POST, PUT, DELETE, OPTIONS, so the browser blocks the PATCH. The web app on jordylab.be is same-origin and unaffected.
- Root cause: PATCH missing from `allowedMethods`.
- Fix (PR / commit / tag): branch `fix/e2e-switch-detail` — PATCH added.
- Regression test added: `SwitchGameControllerSecurityTest.corsPreflightAllowsPatchForTheSwitchEdit`
- Verified on prod: 2026-10-01 on `v0.0.1-rc4`: preflight `OPTIONS /api/gamecatalog/switch/games/x` with `Origin: https://localhost` answers `Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE,OPTIONS`

### BUG-040: No navigation leads to the Switch add page
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S3
- Area/spec: gamecatalog / 009
- Env found: code reading + local (while adding 009 US3)
- Coverage rows: 009-US1, 009-US3
- Steps to reproduce:
  1. Log in as admin; look for a way to add a Switch game in the sidebar, library or sources page.
- Expected (cite spec/story): 009 US1 — the admin adds a Switch game from the app.
- Actual (logs/screenshot, secrets redacted): `/games/switch` exists but nothing links to it; only typing the URL reaches it.
- Root cause: route added without a nav entry.
- Fix (PR / commit / tag): admin-only "Switch games" item in the sidebar; "Paste a list" ↔ "Add one game" links between the two Switch pages.
- Regression test added: `app.spec.ts` (admin nav lists Switch games; guest doesn't)
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: `Switch games` is in the sidebar and leads to the add page

### BUG-041: Switch endpoints answer 500 for duplicates, bad input and unknown games
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S3
- Area/spec: gamecatalog / 009
- Env found: code reading
- Coverage rows: 009-FR-009, 009 switch-api contract
- Steps to reproduce:
  1. `POST /api/gamecatalog/switch/games` for a game already on the Switch, or `DELETE /switch/games/{unknown id}`.
- Expected (cite spec/story): 009 switch-api — `409` duplicate, `400` bad input, `404` unknown game.
- Actual (logs/screenshot, secrets redacted): `IllegalStateException` / `IllegalArgumentException` are unhandled for `SwitchGameController` → `500`; the UI shows a generic error.
- Root cause: no exception handler for the Switch controller.
- Fix (PR / commit / tag): `SwitchGameExceptionHandler` (400/404/409 ProblemDetail), `SwitchGameNotFoundException`.
- Regression test added: `SwitchGameControllerTest` (409, 404, 400)
- Verified on prod: 2026-10-05 on `v0.0.1-rc16`, admin session in the browser pane: `GET /api/gamecatalog/games/<unknown id>` → 404 ("This game is not in your catalog"); adding Pikmin 4 → 201, adding it again → 409 (no 500s). The 400 for an empty title is stopped by the form before a request is sent; the 400/404/409 mapping is covered by `SwitchGameControllerTest` in CI. The test game was removed again (DELETE → 204)

### BUG-042: Switch IGDB search misses ports and expanded games — "Mario Kart 8 Deluxe" finds nothing
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: local (2026-10-01, IGDB live)
- Coverage rows: 009-US1, 009-US3, 009-SC-002
- Steps to reproduce:
  1. `/games/switch` → search "Mario Kart 8" → "No IGDB match".
- Expected (cite spec/story): 009 SC-002 — ≥ 90% correct first matches for official titles.
- Actual (logs/screenshot, secrets redacted): IGDB returns nothing: the query filters `game_type = 0` (main game) and IGDB files MK8 Deluxe as type 10 (expanded game); every port/remaster is excluded too.
- Root cause: too narrow `game_type` filter.
- Fix (PR / commit / tag): `game_type = (0,4,8,9,10,11)` — main, standalone expansion, remake, remaster, expanded game, port; DLC/bundles/mods/packs stay out.
- Regression test added: `IgdbClientTest.searchSwitchGamesKeepsPortsAndExpandedGamesButNotDlcOrBundles`; verified locally (MK8 Deluxe → Match)
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: IGDB search for `Mario Kart 8 Deluxe` finds it (2017, Nintendo)

### BUG-043: Detail page's admin format select always shows "Physical"
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S3
- Area/spec: gamecatalog / 009
- Env found: local (2026-10-01; shipped in v0.0.1-rc4 via PR #53)
- Coverage rows: 009-US5
- Steps to reproduce:
  1. Admin opens a Switch game whose format is Digital.
- Expected (cite spec/story): 009 US5 — the edit control shows the current format.
- Actual (logs/screenshot, secrets redacted): "Format · Digital" is shown, but the select reads PHYSICAL.
- Root cause: `[value]` on the `<select>` is applied before `@for` renders the options.
- Fix (PR / commit / tag): `[selected]` per option.
- Regression test added: `game-detail.component.spec.ts` (preselects the current format); verified locally
- Verified on prod: 2026-10-02 on `v0.0.1-rc8`, signed in as admin in the browser pane: the detail page select shows `Digital` for a Digital game (not `Physical`)

### BUG-044: Release APK check reads an empty certificate digest — apksigner's signer label isn't "Signer #1"
- Status: VERIFIED-PROD
- Severity: S2
- Area/spec: CI / 007, 011
- Env found: CI (release v0.0.1-rc4, run 36848803968)
- Coverage rows: 007-FR-003, 011-FR-012b
- Steps to reproduce:
  1. Push a `v*` tag; the `apk` job builds and signs the APK, then *Verify the APK signature* runs `apksigner verify --verbose --print-certs`.
- Expected (cite spec/story): the step compares the APK's certificate with `MOBILE_RELEASE_SIGNING_CERT_SHA256` and publishes on a match (007 FR-003).
- Actual (logs/screenshot, secrets redacted): `apksigner verify` passed, then `##[error]APK signed with , expected 1B0213…77E8` — the parsed digest was empty, so nothing was published.
- Root cause: the step only matched `Signer #1 certificate SHA-256 digest:`; apksigner labels v3 signers per SDK range (`Signer (minSdkVersion=…, maxSdkVersion=…) …`).
- Fix (PR / commit / tag): accept any `Signer…` label, require exactly one distinct certificate, and print apksigner's signer lines when that fails.
- Regression test added: none because CI shell step; parse checked locally against both label styles
- Update 2026-10-01 (rc5, run 36884797915): `apksigner verify --verbose --print-certs` printed only `Verifies` and the scheme lines (v2 true; v1, v3, v3.1, v3.2, v4 false) — no `Signer` line in any format, so no digest to parse. Fix: the CI step now relies on apksigner's exit code (`verify --min-sdk-version 29`); the pinned certificate is compared by the backend on upload (`SIGNING_CERT_MISMATCH`, apksig).
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: the release's `apk` job passes *Verify the APK signature* (apksigner exit code) and the APK is published; a wrong-key APK is refused by the backend (`SIGNING_CERT_MISMATCH`, unit-tested)

### BUG-045: The FNA price refresh asks Yahoo for `MEUD`, which isn't a Yahoo symbol — 404 every 30 minutes
- Status: VERIFIED-PROD
- Severity: S4
- Area/spec: fna / 001
- Env found: prod (backend log, 2026-10-01)
- Coverage rows: 001-FR-portfolio-prices
- Steps to reproduce:
  1. `kubectl -n jordylab logs deploy/backend | grep "Could not fetch price"` → `MEUD: 404 Not Found … symbol may be delisted`, every 30 min. The portfolio has `BTC` (price present) and `MEUD` (no price).
- Expected (cite spec/story): every position shows its last price.
- Actual (logs/screenshot, secrets redacted): `MEUD` never gets a price. Yahoo knows the exchange-qualified symbol `MEUD.PA` (Amundi Core Stoxx Europe 600 UCITS ETF Acc, 309.7 on 2026-10-01), not `MEUD`.
- Root cause: the position's ticker was entered without its exchange suffix. It's the owner's own portfolio row, so the agent doesn't edit it.
- Fix (PR / commit / tag): owner edits the ticker to `MEUD.PA` on the Portfolio page (HANDOFF-09). Optional later: the portfolio form could hint that tickers need a Yahoo suffix.
- Regression test added: none because data fix
- Verified on prod: 2026-10-01: after the owner set the ticker to `MEUD.PA` (HANDOFF-09) prod shows `MEUD.PA|309.3500` and the `Could not fetch price` warning is gone

### BUG-046: SpringDoc API docs and Swagger UI are enabled in prod
- Status: VERIFIED-PROD
- Severity: S4
- Area/spec: shared / 008
- Env found: prod (backend startup warnings, 2026-10-01)
- Coverage rows: 008-FR-019
- Steps to reproduce:
  1. Backend start log: "SpringDoc /v3/api-docs endpoint is enabled by default. To disable it in production, set `springdoc.api-docs.enabled=false`" (same for `/swagger-ui.html`).
- Expected (cite spec/story): nothing in prod advertises the API surface.
- Actual (logs/screenshot, secrets redacted): both endpoints are on. The public gateway only routes `/api` to the backend, so they are not reachable from the internet today (the SPA answers instead), but a routing change would expose them.
- Root cause: SpringDoc defaults, never switched off for prod.
- Fix (PR / commit / tag): `SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false` in the prod `backend-config` ConfigMap.
- Regression test added: none because deploy configuration; verify the startup warnings are gone after the next release
- Verified on prod: 2026-10-01 on `v0.0.1-rc6`: the SpringDoc startup warnings are gone from the backend log; `/v3/api-docs` and `/swagger-ui.html` answer the SPA shell publicly, as before

### BUG-047: EmuDeck scan of a large ROM library fails with "scan payload exceeds 1048576 bytes"
- Status: VERIFIED-PROD (2026-10-02)
- Severity: S2
- Area/spec: gamecatalog / 003
- Env found: prod, JordyBox (reported by the owner, 2026-10-01; Steam scan on the same machine succeeded)
- Coverage rows: 003-FR-scan, 002-FR-ingest-limits
- Steps to reproduce:
  1. On JordyBox: `python3 jordylab-scan-… scan` for the EmuDeck library with the downloaded client.
- Expected (cite spec/story): the owner's real ROM library is scanned and catalogued.
- Actual (logs/screenshot, secrets redacted): the client refuses before sending: `scan payload exceeds 1048576 bytes`.
- Root cause: the 1 MiB cap (client `MAX_PAYLOAD_BYTES` and server `max-payload-bytes`) counts ~100 bytes per path, so it holds ~11,000 files; the server's `max-games-per-source` was 10,000. A big EmuDeck library (multi-file games) exceeds both.
- Fix (PR / commit / tag): payload cap 8 MiB (client + server default + yaml), games per source 50,000; frozen client regenerated; ingest contract updated. A request is still bounded.
- Regression test added: `GameCatalogPropertiesTest` (new defaults); client checked with 20,000 entries (accepted) and an oversize payload (still rejected). Verified on prod: after rc6 the owner reruns the EmuDeck scan (HANDOFF-11).
- Verified on prod: 2026-10-02 on `v0.0.1-rc7`: the owner's EmuDeck rescan on JordyBox applied (148 installations on `cachyos-htpc`, 68 Steam); a rerun answered `NO_CHANGE` (exit 0).

### BUG-048: Two overlapping scans of the same source fail one of them with a duplicate-key 500
- Status: VERIFIED (CI)
- Severity: S3
- Area/spec: gamecatalog / 003
- Env found: prod (backend log, 2026-10-01 22:50:55Z; the owner's EmuDeck rescan was started twice in a row)
- Coverage rows: 003-FR-idempotency
- Steps to reproduce:
  1. Start a scan of a source; while it is still enriching (one long transaction, ~40 s with 8 AI calls) submit the same scan again.
- Expected (cite spec/story): the second scan waits, then answers `NO_CHANGE`; no scan ends in an error.
- Actual (logs/screenshot, secrets redacted): the second scan inserts the same installations and dies with `duplicate key … uq_game_installation_source_ref` (HTTP 500); for a brand-new source the same race hits `scan_source_source_key_key`.
- Root cause: `ScanService.submitScan` is one long `@Transactional`; the idempotency hash is only visible after the first transaction commits, so a concurrent scan sees nothing and re-creates the rows.
- Fix (PR / commit / tag): PR #74, `v0.0.1-rc8` — `ScanLock` takes a Postgres transaction-scoped advisory lock per host + library type at the start of `submitScan`; the second scan blocks until the first commits, then sees the stored hash and answers `NO_CHANGE`.
- Regression test added: `GameCatalogModuleTest.aSecondScanOfTheSameSourceWaitsForTheFirstInsteadOfFailingOnDuplicateKeys` (fails with `DataIntegrityViolationException` without the lock, passes with it); `ScanServiceTest` constructor updated.
- Verified on prod: 2026-10-05: the race cannot be produced with the real client — it holds a per-machine file lock, so two scans from one machine are serialized before they reach the backend; the service-level race is reproduced by `GameCatalogModuleTest.aSecondScanOfTheSameSourceWaitsForTheFirstInsteadOfFailingOnDuplicateKeys` (fails with a duplicate-key error without the lock, passes with it) and the lock is live since rc8; since then the owner's rescans and reruns of the EmuDeck scan (rc10–rc15) all answered APPLIED/NO_CHANGE with no 500

### BUG-049: Android install dialog is unstyled and gives no feedback — tapping Download "does nothing"
- Status: VERIFIED-PROD (2026-10-04)
- Severity: S3
- Area/spec: mobile / 007 US1 (web install dialog, FR-005/FR-006)
- Env found: prod, the owner's Android phone (Brave, reported 2026-10-02, MRB-04)
- Coverage rows: 007-US1, 007-FR-005
- Steps to reproduce:
  1. Open `https://jordylab.be` signed in, in a mobile Android browser → the install dialog shows.
  2. Tap Download.
- Expected (cite spec/story): a styled dialog like the rest of the app; a tap on Download visibly starts the APK download.
- Actual (logs/screenshot, secrets redacted): the dialog is plain unstyled text at the top of the page (`Download` and `Not now` run together as "DownloadNot now"); after the tap the dialog disappears and nothing visible happens. The APKs are present on the server (rc6–rc8) and the backend logged no error.
- Root cause: `lib-install-prompt`, `lib-update-available-banner` and `lib-update-required` were written with bare elements and CSS class names that no stylesheet defines; the download handler reports nothing while it requests the signed link, and swallows failures.
- Fix (PR / commit / tag): PR #79, `v0.0.1-rc9` — the three components use the app's Tailwind tokens (bottom sheet, 48 px buttons); the install dialog shows "Preparing the download…", "Download requested — open the file from your notifications or Downloads" or an error with "Try again".
- Regression test added: `install-prompt.component.spec.ts` (preparing / requested / failed states) and `app.spec.ts` (the three handler outcomes)
- Verified on prod: 2026-10-04 on `v0.0.1-rc9`: styled bottom sheet seen in a phone-sized pane (guest session) and on the owner's phone ("looks much better"); "Not now" dismisses it; Download fetched and installed the APK

### BUG-050: Native app shows the login page again after a successful login
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S2
- Area/spec: mobile / 007 US2
- Env found: prod, the owner's Android app (MRB-04, 2026-10-04)
- Coverage rows: 007-US2, 007-US2-AS1
- Steps to reproduce:
  1. In the installed app tap Sign in, log in at Keycloak, return to the app.
- Expected (cite spec/story): the app lands on the library.
- Actual (logs/screenshot, secrets redacted): header and navigation show (the session is valid) but the page body is still the login card; tapping Library works.
- Root cause: the native login completes in an App Link callback outside the router, so nothing navigates away from `/login`.
- Fix (PR / commit / tag): `LoginComponent` navigates to `/` as soon as `AuthService.isAuthenticated()` turns true.
- Regression test added: `login.component.spec.ts` (leaves the page once authenticated)
- Verified on prod: 2026-10-05 on `v0.0.1-rc15`, owner's phone: with the current APK the app starts, sign-in lands on the library, the fingerprint unlock and Settings → App work (owner: "it works now")

### BUG-051: "Unlock with fingerprint" never unlocks — nothing asks for the fingerprint, and a failed enable looks enabled
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S3
- Area/spec: mobile / 007 US4
- Env found: prod, the owner's Android app (MRB-04, 2026-10-04)
- Coverage rows: 007-US4, 007-US4-AS1, 007-FR-012
- Steps to reproduce:
  1. Log in, tick "Unlock with fingerprint", close and reopen the app.
- Expected (cite spec/story): 007 US4-1 — reopening asks for the fingerprint and signs in without a password.
- Actual (logs/screenshot, secrets redacted): the box shows ticked but the app opens on the login page and never asks for a fingerprint.
- Root cause: `BiometricUnlockService.unlock()` is never called anywhere (US4 was only half wired: enable and wipe exist, unlock on start does not); and the checkbox keeps the browser-flipped tick when `enable()` fails, so a failure looks like success. The manifest also lacks `USE_BIOMETRIC`.
- Fix (PR / commit / tag): on native start the app calls `unlock()` when unlock is enabled and there is no session; the login page offers an "Unlock with fingerprint" button as retry; a failed enable un-ticks the box and says so; `USE_BIOMETRIC` added to the manifest. The prompt itself and the Keystore storage still need the phone to confirm (MRB-04).
- Regression test added: `app-native-start.spec.ts` (asks on a native cold start only when enabled and signed out; a failing unlock is contained), `login.component.spec.ts`, `biometric-unlock-toggle.component.spec.ts` (failed enable)
- Verified on prod: 2026-10-05 on `v0.0.1-rc15`, owner's phone: with the current APK the app starts, sign-in lands on the library, the fingerprint unlock and Settings → App work (owner: "it works now")

### BUG-052: The Android app uses the placeholder launcher icon and splash screen
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S4
- Area/spec: mobile / 007 US1
- Env found: prod, the owner's phone (MRB-04, 2026-10-04)
- Coverage rows: 007-FR-001
- Steps to reproduce:
  1. Install the APK and look at the home screen.
- Expected (cite spec/story): the JordyLab "J" mark.
- Actual (logs/screenshot, secrets redacted): the default Capacitor blue-cross icon (and splash).
- Root cause: the generated Capacitor launcher/splash resources were never replaced.
- Fix (PR / commit / tag): adaptive icon = orange background + vector "J" (same glyph as the favicon); splash = dark app background with the mark; old PNG/grid resources removed.
- Regression test added: none because Android resources; verified by the release `apk` job building and by the owner's phone
- Verified on prod: 2026-10-05: the orange J launcher icon is on the owner's phone (screenshots of the install dialog and home screen)

### BUG-053: The app only learns about a new release after being backgrounded — the update check runs before login
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S3
- Area/spec: mobile / 007 US3
- Env found: code review while answering "how do I update the app?" (2026-10-04)
- Coverage rows: 007-US3, 007-FR-011
- Steps to reproduce:
  1. Release a newer APK; open the installed app from scratch and sign in.
- Expected (cite spec/story): 007 FR-011 — the app checks for a newer release on start and resume and offers the update.
- Actual (logs/screenshot, secrets redacted): the check fires in the app constructor, before any session exists; `/api/mobile/releases/latest` needs an admin/guest token, so it answers 401, the rejection is unhandled, and no banner appears until the app is sent to the background and brought back (the resume listener).
- Root cause: `checkForUpdate()` was called unconditionally at construction instead of once the user holds an application role; failures were not contained.
- Fix (PR / commit / tag): the shell runs the check as soon as the user has an application role (login, fingerprint unlock or restored session); a failed check is logged and retried on the next resume.
- Regression test added: `app-native-start.spec.ts` (checks once signed in), `update-check.store.spec.ts` (a failed check is swallowed)
- Verified on prod: 2026-10-05: the owner's phone (rc15) showed the "Update available" banner right after sign-in once the rc16 APK was published ("works perfectly")

### BUG-054: Extracted PS3 games show up as dozens of "EBOOT"/data-file games on platform "Usrdir"; PS3 is labelled "Ps3"
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S3
- Area/spec: gamecatalog / 003
- Env found: prod catalog (platform chips `Ps3` and `Usrdir`, 2026-10-02); layout confirmed by the owner's `ls ~/Emulation/roms/ps3` (2026-10-04)
- Coverage rows: 003-FR-scan
- Steps to reproduce:
  1. EmuDeck library with RPCS3 extracted discs: `ps3/<Game>/PS3_GAME/USRDIR/EBOOT.BIN` (+ many data files).
  2. Scan.
- Expected (cite spec/story): one game per disc, on a readable platform label.
- Actual (logs/screenshot, secrets redacted): every file with a ROM-like extension (`EBOOT.BIN`, data `.bin`, …) inside the extracted tree becomes its own game whose platform is its immediate parent folder, `USRDIR` → "Usrdir"; the `ps3` folder itself is labelled "Ps3" (capitalise fallback).
- Root cause: the platform is "the folder directly above the file" and nothing knows an extracted PS3 disc is a folder-shaped game; `ps3` has no label mapping.
- Fix (PR / commit / tag): a folder that contains `PS3_GAME` is one game named after that folder (everything inside it is ignored); `ps3` → "PlayStation 3" in the client and the server parser; frozen client regenerated. A `.iso` next to an extracted folder is still its own game (the server adopts same-titled games on the same platform).
- Regression test added: `test_grouping.py` (extracted disc → one game; iso + folder; dots in the folder name; folder directly under the root skipped); frozen client selftest
- Verified on prod: 2026-10-05: after the owner's EmuDeck rescan (161 submitted, 18 added, 5 removed) the `Usrdir` chip is gone and PS3 reads "PlayStation 3" (owner: "yep")

### BUG-055: After a successful fingerprint check the app still asks for the password; the fingerprint switch sits awkwardly in the sidebar
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S3
- Area/spec: mobile / 007 US4
- Env found: prod, the owner's Android app on rc10 (2026-10-04, after "signing in and the update work great")
- Coverage rows: 007-US4, 007-US4-AS1, 007-FR-012
- Steps to reproduce:
  1. Sign in, switch fingerprint unlock on, close the app, reopen it, pass the fingerprint check.
- Expected (cite spec/story): 007 US4-1 — the app opens signed in, no password.
- Actual (logs/screenshot, secrets redacted): after the fingerprint the login (credentials) page is shown again, so unlock is useless.
- Root cause: not reproducible without the device. Facts established: the realm grants `offline_access` (client optional scope, default role, offline idle 30 days), so the stored token is an offline token; the plugin stores without a prompt and prompts on read (so ticking never prompts). Code defects found: (1) a native token refresh failure called `login()`, which silently opens the Keycloak credentials page — the most likely source of the symptom; (2) keycloak-js treats the token as expired whenever `timeSkew` is unset, so natively every request forced a refresh with the stored token (more chances to fail, and token churn); (3) every unlock failure was silent.
- Fix (PR / commit / tag): a failed native refresh now drops the dead session and shows the login page with the reason instead of opening Keycloak; `timeSkew` is set when native tokens are applied (no more forced refresh per request); every unlock failure (cancelled check / server rejected the stored session with its HTTP status / offline) is shown on the login page; the fingerprint switch moved off the sidebar to a native-only **Settings → App** page (a proper switch with explanation; reachable by admins and guests).
- Regression test added: `auth.service.spec.ts` (no credentials page on refresh failure, status recorded, timeSkew set), `biometric-unlock.service.spec.ts` (failure reasons), `login.component.spec.ts` (reason shown), `biometric-unlock-toggle.component.spec.ts` (switch), `app.spec.ts` / `app-native-start.spec.ts` (nav)
- Verified on prod: 2026-10-05 on `v0.0.1-rc15`, owner's phone: with the current APK the app starts, sign-in lands on the library, the fingerprint unlock and Settings → App work (owner: "it works now")

### BUG-056: rc12 shows a blank page on web and in the app — `AuthService` needed a router the pre-bootstrap injector does not have
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S1
- Area/spec: mobile + auth / 007 US2, 006
- Env found: prod, the owner's phone and desktop browser, minutes after rc12 (2026-10-04 ~22:25)
- Coverage rows: 007-US2, A12
- Steps to reproduce:
  1. Open `https://jordylab.be` or the app on `v0.0.1-rc12`.
- Expected (cite spec/story): the login page or the library.
- Actual (logs/screenshot, secrets redacted): a blank (dark) page; the console shows `NG0201: No provider found for Router. Path: AuthService -> Router`.
- Root cause: BUG-055's change gave `AuthService` a constructor-time `inject(Router)`. `apps/jordylab/src/main.ts` builds `AuthService` in a throwaway injector (only `AUTH_CONFIG`) for the early Keycloak check before `bootstrapApplication`, so construction threw and the app never started. Unit tests could not see it: they construct the service in a TestBed that has a router, and nothing booted the real start-up path.
- Fix (PR / commit / tag): `AuthService` looks the router up lazily (only when a native refresh fails); the pre-bootstrap injector moved to `createPreBootstrapAuth()` so a spec exercises exactly what `main.ts` does.
- Regression test added: `app.config.spec.ts` (the throwaway injector can build `AuthService`; the real app providers can build `AuthService` and `BiometricUnlockService`) — it fails with NG0201 on the rc12 code
- Verified on prod: 2026-10-05 on `v0.0.1-rc15`, owner's phone: with the current APK the app starts, sign-in lands on the library, the fingerprint unlock and Settings → App work (owner: "it works now")



### BUG-057: rc13 `apk` job: `POST /api/mobile/releases` answers 401 although the CI token is valid — no phone can get the fixed app
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S2
- Area/spec: mobile / 007 FR-001, release flow
- Env found: prod release pipeline, rc13 run (2026-10-04 ~21:03Z), both the first attempt and a rerun
- Coverage rows: 007-FR-001
- Steps to reproduce:
  1. Push a `v*` tag; the `apk` job logs in as `mobile-release-ci` and uploads the signed APK.
- Expected (cite spec/story): `201 Created`; the release is published.
- Actual (logs/screenshot, secrets redacted): `curl: (22) 401` and exit 1 — rc13's log looked like an upload failure only because I filtered the log with `grep -v secret`, which hid the line "Keycloak refused the mobile-release-ci credentials"; rc14's log (no filter) shows it was the Keycloak login that returned 401 (rc12's identical step passed before the rotation). Probes by the owner (HANDOFF-20/20b): token endpoint 200, `iss`/`azp` correct, role `mobile-release-publisher` present, the backend accepts the token on `/latest` (403 = authenticated) and answers 400 (not 401/403) to a file-less POST of the publish endpoint. Backend logs show no error; disk has 58 GB free; the controller has no 401 path.
- Root cause: the rotated secret was set on the GitHub `production` *environment* (HANDOFF-19, my command used `--env production`), but the `apk` job has no `environment:` and reads the *repository* secret, which still held the old (exposed) value — Keycloak, now on the new value, refused it. The runbook already said `--repo`; my handoff was wrong. The probes that "proved" the token path used the cluster value, not the GitHub one.
- Fix (PR / commit / tag): the owner sets the **repository** secret from the cluster value (HANDOFF-21, runbook text now says repository-level in bold) and the failed `apk` job is rerun; separately the publish step prints the HTTP status, `WWW-Authenticate`/`Content-Type` and the first 600 bytes of the body, and retries up to 3 times (30 s apart); a final failure names the status.
- Regression test added: none because CI workflow; verified by the next release's `apk` job
- Verified on prod: 2026-10-05: with the repository secret replaced (HANDOFF-21) the rc14 `apk` job answered `publish attempt 1: HTTP 201` and the rc15 run was green end to end, including `apk`

### BUG-058: A crypto position cannot be entered — shares are cut to 4 decimals; its value looks wrong
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S2
- Area/spec: fna / 001 (portfolio)
- Env found: prod, the owner adding 0.002106 BTC (2026-10-04)
- Coverage rows: 001-US (portfolio manager)
- Steps to reproduce:
  1. Portfolio → ticker `BTC`, shares `0.002106` → Add position.
- Expected (cite spec/story): the position keeps 0.002106 and is worth about €160.7 at €76,318 per BTC.
- Actual (logs/screenshot, secrets redacted): the row shows `0.0021` shares and a value of €0.08.
- Root cause: two separate things. (1) `finance.portfolio_position.share_count` was `NUMERIC(12,4)` and the table displayed `1.0-4` digits, so 0.002106 became 0.0021 (the number input also had no `step`, so browsers flagged fractions). (2) The €0.08 is correct arithmetic on the wrong instrument: Yahoo's symbol `BTC` is the Grayscale Bitcoin Mini Trust ETF (~38 USD), not Bitcoin; 0.0021 × 38.25 = 0.08. Bitcoin in euro is `BTC-EUR` (76,318.08 EUR at the time; 0.0021 → 160.27, matching the owner's Google check).
- Fix (PR / commit / tag): migration `V20261004001` widens `share_count` to `NUMERIC(20,10)` (existing values unchanged); the table shows up to 10 decimals and the shares input takes `step="any"`; the add-position form now says to use euro-priced Yahoo symbols (`BTC-EUR`, not `BTC`). The owner replaces the `BTC` row by `BTC-EUR` with 0.002106 (no hand-editing of data).
- Regression test added: `FnaRepositoryTest.shareCountKeepsTheDigitsOfASmallCryptoPosition` (fails with `0.0021` without the migration), `portfolio-manager.component.spec.ts` (digits shown, value €160.73, `step=any`)
- Verified on prod: 2026-10-05 on `v0.0.1-rc14`: the owner re-entered 0.002106 and the position keeps the digits ("the shares is correct now"); its value is now handled by BUG-059

### BUG-059: A position needs the exact Yahoo symbol (BTC-EUR, MEUD.PA) to get a value
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S2
- Area/spec: fna / 001 (portfolio)
- Env found: prod, the owner replacing the `BTC` row (2026-10-05)
- Coverage rows: 001-US (portfolio manager)
- Steps to reproduce:
  1. Add `BTC` (or `MEUD`) with its shares; or add `BTC-EUR` and wait.
- Expected (cite spec/story): typing the plain name is enough; the position is valued in euro.
- Actual (logs/screenshot, secrets redacted): `BTC` is valued with a US-dollar ETF (€0.08); the exact `BTC-EUR` is right but shows no value until the half-hourly refresh.
- Root cause: prices were fetched for the symbol exactly as typed and only by a 30-minute job; nothing knew that the same letters mean different instruments on different markets.
- Fix (PR / commit / tag): `StockPriceService.resolve` tries crypto (`-EUR`) and European listings (`.PA .AS .BR .DE .MI`) for a plain name, takes the first quoted in EUR, falls back to the plain symbol converted to EUR (USD, GBP and pence handled), and the position remembers the resolved symbol (`price_symbol`, migration `V20261005001`); adding a position prices it immediately; typed names are trimmed and upper-cased; exact symbols (with `.`, `-`, `^`, `=`) are used as typed.
- Regression test added: `StockPriceServiceTest` (BTC → BTC-EUR, MEUD → MEUD.PA, exact symbol as typed, USD and pence conversion, no match, resolve-once-then-reuse), `FnaServiceTest` (upsert prices at once, normalises input)
- Verified on prod: 2026-10-05 on `v0.0.1-rc16`: the owner's `BTC` (0.002106) and `MEUD` rows show euro values ("works now")

### BUG-060: No portfolio price is fetched any more — Yahoo answers 429 to the backend's HTTP client
- Status: VERIFIED-PROD (2026-10-05)
- Severity: S2
- Area/spec: fna / 001 (portfolio prices)
- Env found: prod backend log after rc15 (2026-10-05): `No euro price found for BTC-EUR / MEUD.PA / BTC / MEUD`
- Coverage rows: 001 portfolio prices
- Steps to reproduce:
  1. `curl -A curl/8 https://query1.finance.yahoo.com/v8/finance/chart/BTC-EUR` → `429 Edge: Too Many Requests`; the same with `Mozilla/5.0 (compatible; JordyLab/1.0)` → 200 with the quote.
- Expected (cite spec/story): positions are priced in euro (HANDOFF-09 showed MEUD.PA priced on 2026-10-01).
- Actual (logs/screenshot, secrets redacted): every Yahoo call from the backend (Java HTTP client user agent) is refused with 429, so no position gets a price, new or old; the failure was logged at debug level only.
- Root cause: Yahoo now rejects non-browser user agents (it accepted the default one a few days earlier); nothing in the app set a user agent, and fetch failures other than 404 were invisible.
- Fix (PR / commit / tag): quote requests send `Mozilla/5.0 (compatible; JordyLab/1.0)`; 429/5xx/timeouts are logged at WARN (404 while probing candidates stays debug).
- Regression test added: `StockPriceServiceTest.sendsABrowserCompatibleUserAgentBecauseYahooRejectsCurlAndJava` (a stub answers 429 unless the agent is browser-compatible)
- Verified on prod: 2026-10-05 on `v0.0.1-rc16`: the price refresh logs no warning and the owner's BTC and MEUD rows are valued

### BUG-061: If Keycloak cannot be reached or framed, the app stays on a blank page forever
- Status: VERIFIED (CI) — deployed in `v0.0.1-rc17` (PR #105)
- Severity: S2
- Area/spec: auth / 006, 007
- Env found: the new CI app-boot check, first run against the built app (2026-10-05); same failure class as BUG-056
- Coverage rows: A12
- Steps to reproduce:
  1. Serve the built app from an origin Keycloak will not frame (or with Keycloak unreachable) and open it.
- Expected (cite spec/story): the login page, with the user signed out.
- Actual (logs/screenshot, secrets redacted): `app-root` stays empty for more than 45 s: start-up waits on `keycloak.init`, whose silent check-sso iframe never reports back (`Framing … violates frame-ancestors 'self'`), and nothing times out.
- Root cause: `AuthService.init` had no upper bound; `main.ts` bootstraps only after it settles and every route guard awaits the same promise.
- Fix (PR / commit / tag): start-up waits at most 8 s for Keycloak and then continues signed out (logged); a unit test covers it. The CI app-boot check (below) now guards the whole start-up path.
- Regression test added: `auth.service.spec.ts` (carries on signed out when Keycloak never answers); `jordylab-fe/tools/app-boot-check.mjs` as a CI job `app-boot`: builds the production bundle, boots it in headless Chrome and requires the login page — it fails with `NG0201` when the rc12 bug is put back (verified locally) and passes on the fixed code
- Verified on prod: 2026-10-05: rc17 starts on prod (login page / app render, 0 backend errors); the failure path itself (Keycloak not answering) is exercised on every PR by the `app-boot` CI job, where the login page appears via the 8 s timeout


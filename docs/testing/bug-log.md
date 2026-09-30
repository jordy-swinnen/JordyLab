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
- Status: FIXED-LOCAL (PR #42, `06c645c`) — badge moved to new `settings-nav` lib; lint green; ships with the next release
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
- Status: FIXED-LOCAL (PR #42, `06c645c`) — lint green; ships with the next release
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
- Status: FIXED-LOCAL (PR #42, `06c645c`) — Build runs lint for all projects; first enforced on #42's own CI (green)
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
- Status: FIXED-LOCAL (PR #42, `06c645c`) — Build runs `./gradlew check` (JaCoCo) + `nx test --coverage`; enforced from #42 on
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
- Status: OPEN
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
- Fix (PR / commit / tag):
- Regression test added: none because docs-only
- Verified on prod:

### BUG-007: Claude PR review prompt references a file that does not exist
- Status: OPEN
- Severity: S4
- Area/spec: CI / —
- Env found: both
- Coverage rows: G cross-cutting
- Steps to reproduce:
  1. `.github/workflows/claude-pr-review.yml` prompt tells the reviewer to read `coding-master-prompt.md`; `ls coding-master-prompt.md` → not found. It also lists `garmin-sync-service/AGENTS.md` for Python conventions, while Python code lives in `gamecatalog-scanner/`.
- Expected (cite spec/story): review reads the real convention sources (`.specify/memory/constitution.md`, `.claude/rules/*`, `gamecatalog-scanner/AGENTS.md`).
- Actual (logs/screenshot, secrets redacted): stale reference.
- Root cause: file removed/renamed after the workflow was written.
- Fix (PR / commit / tag):
- Regression test added: none because workflow prompt only
- Verified on prod:

### BUG-008: One-tag release flow (runbook §20) is not implemented
- Status: FIXING — release flow merged (PR #39); first release `v0.0.1-rc1` blocked: the `production` environment only allows branch `main`, so tag runs are refused (Jordy adds a `v*` tag rule)
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
- Verified on prod:

### BUG-009: Ollama is still wired in build, tests, compose and AGENTS.md
- Status: OPEN
- Severity: S4
- Area/spec: shared / 006, 008, 001
- Env found: both
- Coverage rows: 006-FR-017, 006-SC-005, 008-FR-021, 001-SC-005
- Steps to reproduce:
  1. `grep -rn -i ollama jordylab-be/build.gradle.kts jordylab-be/src/test jordylab-be/compose.yaml AGENTS.md`
- Expected (cite spec/story): 006 FR-017 — "All local-LLM (Ollama) support MUST be removed…"; Jordy's decision 2026-09-30 (011 FR-012c).
- Actual (logs/screenshot, secrets redacted): `spring-ai-starter-model-ollama` (build.gradle.kts:48), `testcontainers-ollama` (:73), `OllamaContainer` bean in `TestcontainersConfiguration.java`, commented service in `compose.yaml`, AGENTS.md routing table / infrastructure / gotchas rows.
- Root cause: 006 T025/T043/T045 never executed.
- Fix (PR / commit / tag): planned batch `fix/e2e-remove-ollama` (T042).
- Regression test added:
- Verified on prod:

### BUG-010: 006 US4 missing — AI calls are not routed per feature (OpenRouter primary, Anthropic fallback)
- Status: OPEN
- Severity: S2
- Area/spec: shared/ai / 006
- Env found: both
- Coverage rows: 006-US4 (+AS1–AS3), 006-FR-011–FR-014, FR-016, SC-004
- Steps to reproduce:
  1. `grep -rn -E "AiFeature\b|OpenRouter" jordylab-be/src/main` → nothing; 006 tasks T024, T026–T030 open.
- Expected (cite spec/story): 006 US4 / FR-011–FR-013 — OpenRouter primary, Anthropic fallback, configuration per AI feature.
- Actual (logs/screenshot, secrets redacted): only the Anthropic path from 001 exists. `application.yaml` already carries `jordylab.ai.gateway.base-url` (OpenRouter) and `jordylab.ai.features.*` defaults, but no Java code reads them.
- Root cause: story not implemented (011 FR-012a).
- Fix (PR / commit / tag): planned batch `fix/e2e-settings-ai-routing` (T043).
- Regression test added:
- Verified on prod:

### BUG-011: 006 US5 missing — no user menu to manage one's own login details
- Status: OPEN
- Severity: S2
- Area/spec: shared/auth, shell / 006
- Env found: both
- Coverage rows: 006-US5 (+AS1–AS2), 006-FR-008
- Steps to reproduce:
  1. 006 tasks T031–T033 open; no `UserMenuComponent` in `jordylab-fe/libs/shared/auth`.
- Expected (cite spec/story): 006 US5 / FR-008 — every user can change password, name and email without admin help.
- Actual (logs/screenshot, secrets redacted): static sign-out button only (to be confirmed in the UI during US2 testing).
- Root cause: story not implemented.
- Fix (PR / commit / tag): planned batch `fix/e2e-settings-user-menu` (T044).
- Regression test added:
- Verified on prod:

### BUG-012: 006 US6 missing — no per-feature AI model selection (AI Models page)
- Status: OPEN
- Severity: S2
- Area/spec: settings / 006
- Env found: both
- Coverage rows: 006-US6 (+AS1–AS4), 006-FR-015, SC-003, SC-006
- Steps to reproduce:
  1. `libs/settings/ui/src/lib/settings.routes.ts` has only `users`; 006 tasks T034–T040 open.
- Expected (cite spec/story): 006 US6 — choose a model per AI feature; applies on the next call.
- Actual (logs/screenshot, secrets redacted): feature absent.
- Root cause: story not implemented; depends on BUG-010.
- Fix (PR / commit / tag): planned batch `fix/e2e-settings-ai-models` (T045) — includes a Flyway migration → deploy pauses for Jordy.
- Regression test added:
- Verified on prod:

### BUG-013: 006 US7 missing — no Ntfy push when someone signs up
- Status: OPEN
- Severity: S3 (lowered from S2 on 2026-09-30 — Jordy unsure, spec priority P3; see test plan Q-04)
- Area/spec: settings / 006, 007
- Env found: both
- Coverage rows: 006-US7, 006-FR-018, 007-FR-016
- Steps to reproduce:
  1. 006 tasks T041–T042 open; no `NtfyClient` / `PendingSignupNotifierService` in `jordylab-be/src/main`.
- Expected (cite spec/story): 006 FR-018 — admin notified by Ntfy with the pending user's name and email.
- Actual (logs/screenshot, secrets redacted): feature absent.
- Correction (2026-09-30 20:45) — likely invalid, re-verify after BUG-026/BUG-031: the behaviour *is* implemented via spec 007 — `settings/service/PendingSignupWatcherService` polls Keycloak every 5 min and publishes `UserSignUpPending`, and `mobile` sends the Ntfy push (007 FR-016, research D9). Only 006 T041/T042 were never ticked. On prod it currently fails because of BUG-026. Close as invalid once a sign-up push is observed after PR #30.
- Root cause: story not implemented (superseded — see correction).
- Fix (PR / commit / tag): planned batch `fix/e2e-settings-signup-notify` (T046).
- Regression test added:
- Verified on prod:

### BUG-014: 009 US4 incomplete — Switch detail format and cross-view tests missing
- Status: OPEN
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US4 (+AS1–AS4)
- Steps to reproduce:
  1. 009 tasks T027–T029, T032–T034 open (visibility/purge/chat tests, `hostFormats` in `GameDetailResponse`, format on detail page, filter chip check).
- Expected (cite spec/story): 009 US4 — Switch games behave like any other game, format shown on detail.
- Actual (logs/screenshot, secrets redacted): partial; exact UI gaps to be confirmed in US3 testing.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): planned batch `fix/e2e-switch-detail` (T047, with BUG-015).
- Regression test added:
- Verified on prod:

### BUG-015: 009 US2 incomplete — no manual-fallback add form or relink UI
- Status: OPEN
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US2 (+AS1–AS2), 009-FR-004
- Steps to reproduce:
  1. 009 tasks T039–T040 open.
- Expected (cite spec/story): 009 FR-004 — add a game manually when search finds nothing; link it later.
- Actual (logs/screenshot, secrets redacted): feature absent in the UI.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): planned batch `fix/e2e-switch-detail` (T047).
- Regression test added:
- Verified on prod:

### BUG-016: 009 US3 missing — bulk add by pasting a list
- Status: OPEN
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US3 (+AS1–AS2), 009-FR-005, 009-SC-002
- Steps to reproduce:
  1. `SwitchGameController` has no `/bulk/*` endpoints; 009 tasks T041–T047 open.
- Expected (cite spec/story): 009 FR-005 — paste a list, review matches, add the confirmed ones.
- Actual (logs/screenshot, secrets redacted): feature absent.
- Root cause: story not implemented.
- Fix (PR / commit / tag): planned batch `fix/e2e-switch-bulk-add` (T048).
- Regression test added:
- Verified on prod:

### BUG-017: 009 US5 incomplete — no edit/remove on the detail page; guest-403 tests missing
- Status: OPEN
- Severity: S2
- Area/spec: gamecatalog / 009
- Env found: both
- Coverage rows: 009-US5 (+AS1–AS3)
- Steps to reproduce:
  1. 009 tasks T052–T053 open (`PATCH/DELETE /switch/games/{id}` exist in the backend).
- Expected (cite spec/story): 009 US5 — admin edits/removes from the detail page; guests get 403 on writes.
- Actual (logs/screenshot, secrets redacted): UI controls absent; guest-403 not covered by tests.
- Root cause: story partially implemented.
- Fix (PR / commit / tag): planned batch `fix/e2e-switch-edit-remove` (T049).
- Regression test added:
- Verified on prod:

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
- Status: OPEN
- Severity: S4
- Area/spec: backend tests / 006, 008
- Env found: local
- Coverage rows: 006-SC-001
- Steps to reproduce:
  1. `KeycloakIntegrationTest.java:51` uses `quay.io/keycloak/keycloak:26.3.2`; production runs Keycloak 26.7.4 (devops agent, first deploy 2026-09-30).
- Expected (cite spec/story): integration tests run against the same major/minor Keycloak as prod.
- Actual (logs/screenshot, secrets redacted): 4 minor versions apart.
- Root cause: image pin not updated with the prod upgrade.
- Fix (PR / commit / tag):
- Regression test added:
- Verified on prod: n/a

### BUG-020: Scanner client downloaded from production embeds `http://localhost:8180` as its Keycloak URL
- Status: DEPLOYED (`14fb86b`, DEPLOY-01) — prod re-download pending admin login (PR https://github.com/jordy-swinnen/JordyLab/pull/30) — **confirmed on prod 2026-09-30 20:37**
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
- Verified on prod:

### BUG-021: Frontend nginx — no compression, no HSTS, version leak, `index.html` cacheable, headers lost on assets, wrong manifest MIME
- Status: FIXED-LOCAL (PR #40, `e5989e1`) — verified on the runtime image locally; prod check after the next release; CSP still open
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
- Verified on prod:

### BUG-022: Keycloak `master` realm is publicly reachable (login, account console, token endpoint)
- Status: FIXED-LOCAL (PR #41, `7d68ff8`) — prod check after the next release
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
- Verified on prod:

### BUG-023: No base backup exists yet and the restore drill was never performed
- Status: OPEN
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
- Fix (PR / commit / tag): needs Jordy's yes for a cluster mutation: an on-demand `Backup` now (or `immediate: true` via PR), then the restore drill (runbook §15) into a scratch cluster — candidate for the manual runbook if not done in-campaign.
- Regression test added: none because infrastructure procedure
- Verified on prod:

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
- Status: DEPLOYED (`14fb86b`, DEPLOY-01) — admin calls now reach Keycloak's JSON API (next blocker: BUG-031) (PR https://github.com/jordy-swinnen/JordyLab/pull/30)
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
- Verified on prod:

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
- Status: DEPLOYED (DEPLOY-02, `1b907f2`) + live realm patched — prod UI check pending admin login
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
- Verified on prod:

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
- Status: DEPLOYED (DEPLOY-03, `19b6e2d`) — `MOBILE_DOWNLOAD_LINK_SECRET` in `jordylab-secrets`; download-link issuance to be exercised with the first APK
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
- Verified on prod:

### BUG-031: Backend service-account token carries no realm-management roles — Admin REST API 403
- Status: DEPLOYED (DEPLOY-02, `1b907f2`) + live realm patched — prod UI check pending admin login
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
- Verified on prod:

### BUG-032: OpenCode agents point at a model that doesn't exist — none of them can start
- Status: FIXING — devops agent fixed (PR #33, merged); Jordy re-routed the remaining OpenCode agents himself (Q-08); verify they start
- Severity: S4
- Area/spec: dev tooling / —
- Env found: local (OpenCode 1.18.32)
- Coverage rows: G cross-cutting
- Steps to reproduce:
  1. `opencode run "Delegate to the jordylab-devops subagent …"`.
- Expected (cite spec/story): `.opencode/agents/*` mirror `.claude/agents/*` and work (dual-agent-config).
- Actual (logs/screenshot, secrets redacted): `Error: Model not found: anthropic/claude-sonnet-4-6`; all 4 files in `.opencode/agents/` use that model. `opencode/claude-sonnet-5-5` exists but returns "Insufficient account funds" (OpenCode Zen); the configured OpenCode Go models work.
- Root cause: model id copied from Claude naming without checking `opencode models`.
- Fix (PR / commit / tag): devops → `opencode-go/kimi-k2.7-code` (PR #33, verified: subagent runs and loads the `jordylab-ops` skill). Others pending Jordy's routing choice.
- Regression test added: none because agent config (verified with `opencode run` / `claude -p`)
- Verified on prod: n/a

### BUG-033: Approving a user fails on prod (503) — backend service account lacks `view-realm`
- Status: DEPLOYED (DEPLOY-04, `c4e4788`, PR #35) — prod UI check (Approve) pending Jordy's login
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
- Verified on prod:

### BUG-034: The admin's own account is listed as a pending sign-up
- Status: DEPLOYED (DEPLOY-04, `c4e4788`, PR #35) — prod UI check pending Jordy's login
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
- Verified on prod:

# Tasks: Production End-to-End Test, Bug Log and Fix Loop

**Input**: Design documents from `/specs/011-prod-e2e-hardening/`
**Prerequisites**: plan.md, spec.md, research.md (R1–R11), data-model.md, contracts/, quickstart.md

**Tests**: Regression tests are required per fixed bug (FR-015, SC-003); they are listed inside each fix task
rather than as separate test tasks, because the bugs beyond the known ones are discovered during the campaign.

**Organization**: Grouped by the spec's user stories. US1 (recon + approval gate) blocks everything that changes
code or deploys. US5 (handoffs) is not a separate phase of work; it runs alongside US2/US3.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files/targets, no dependency on unfinished tasks)
- **[Story]**: US1–US5 from spec.md
- ⚠️ **GATE**: stop and wait for Jordy before continuing past this task

## Standing rules (apply to every task)

- Never seed any database; no real data → row `BLOCKED` (FR-008).
- Never print secrets; redact as `<redacted>`; log output from the cluster through
  `grep -v -i -E 'password|secret|token'` (FR-023).
- Cluster access is read-only: `get`, `describe`, `logs` only (research R2).
- Every AI-backed call gets a row in the AI tally; stop at 30 per full pass (FR-010).
- Handoffs use `contracts/handoff-block.md`, Fish-compatible, batched (FR-025).
- Bug entries use `contracts/bug-log-entry.md`; plan layout follows `contracts/coverage-matrix.md`.

---

## Phase 1: Setup

**Purpose**: Tool pre-flight and campaign document skeletons.

- [X] T001 Run the read-only pre-flight from `specs/011-prod-e2e-hardening/quickstart.md` ("Prerequisites"):
  `gh auth status`, `tailscale status`, `KUBECONFIG=~/.kube/jordylab.yaml kubectl -n jordylab get pods`,
  `gitleaks version`; record which channels work (cluster yes/no, GitHub MCP down → `gh`) for the plan Summary
- [X] T002 Create `docs/testing/e2e-test-plan.md` with the 11 fixed sections from
  `specs/011-prod-e2e-hardening/contracts/coverage-matrix.md` (headings + empty tables), Summary stating main SHA
  `e167de8`-or-newer tested and approval status `awaiting Jordy`
- [X] T003 [P] Create `docs/testing/bug-log.md` with a header, the severity/status legend from
  `specs/011-prod-e2e-hardening/contracts/bug-log-entry.md`, and no entries yet

---

## Phase 2: Foundational

**Purpose**: Read the governing sources the matrix is built from. Blocks US1.

- [X] T004 Read `AGENTS.md`, `CLAUDE.md`, `docs/environments.md`, `docs/runbook.md` §9–§13 and §20,
  `.github/workflows/build.yml`, `.github/workflows/deploy-prod.yml`, `.github/workflows/android-release.yml`,
  `.claude/agents/jordylab-devops.md`, `jordylab-be/AGENTS.md`, `jordylab-fe/AGENTS.md`,
  `gamecatalog-scanner/AGENTS.md`; note every fact that differs from the brief
  (`Claude outputs/jordylab-e2e-test-and-fix-prompt.md`) or from `specs/011-prod-e2e-hardening/research.md`
- [X] T005 [P] Read `specs/001-fna-mvp1-completion/` … `specs/005-steam-library-sync/` (spec, plan, tasks,
  quickstart, contracts, checklists, validation-results where present); list every user story, acceptance
  scenario and FR with its location (route/endpoint)
- [X] T006 [P] Read `specs/006-settings-module/` … `specs/010-eufy-presence/` the same way; for 006 and 009 list
  each open task per user story (they become missing-story bugs, FR-012a)
- [X] T007 [P] Grep for `TODO|FIXME|XXX` in `jordylab-be/src`, `jordylab-fe/apps`, `jordylab-fe/libs`,
  `gamecatalog-scanner/src` and keep the hits as matrix evidence

**Checkpoint**: all sources read; contradictions noted.

---

## Phase 3: User Story 1 — Test plan and coverage matrix before any fix (P1) 🎯 MVP

**Goal**: Evidence-based risk map + baseline + approval, with no code changed.

**Independent Test**: `docs/testing/e2e-test-plan.md` has a row for every spec story/scenario/FR of 001–010,
baseline results, contradictions, high-risk list; `bug-log.md` holds baseline failures and known bugs; `git diff`
outside `docs/testing/` and `specs/011-*` is empty; Jordy has been asked for approval.

- [X] T008 [US1] Fill the Coverage Matrix in `docs/testing/e2e-test-plan.md` from T005–T007: one table per spec,
  columns per `contracts/coverage-matrix.md`, `Risk=high` per research R5 / data-model rules, status `TODO`
- [X] T009 [US1] Mark every 010 row and the garmin-sync-service rows `NOT TESTABLE — NOT BUILT` in
  `docs/testing/e2e-test-plan.md` (research R1)
- [X] T010 [P] [US1] Baseline backend: `cd jordylab-be && ./gradlew build` (tests, `ModularityTests`, JaCoCo ≥ 0.80);
  record result in plan section 3; each failure → a BUG entry in `docs/testing/bug-log.md`
- [X] T011 [P] [US1] Baseline frontend: `cd jordylab-fe && bunx nx run-many -t test,lint`; record + log failures
- [X] T012 [P] [US1] Baseline scanner: `cd gamecatalog-scanner && python3 -m venv .venv && .venv/bin/pip install -e ".[dev]" && .venv/bin/python -m pytest --cov=src`
  (target ≥ 80 %); then `python3 tools/build_client.py` and `git diff --exit-code jordylab-be/src/main/resources/scripts/jordylab-scan-template.py`
  (template drift = bug); record + log failures; revert any generated diff
- [X] T013 [P] [US1] Baseline secret scan: `gitleaks detect --redact --no-banner`; record; any finding → S1 bug
  (never paste the secret)
- [X] T014 [US1] Log the known bugs in `docs/testing/bug-log.md` (status OPEN, Env both, expected = cite spec):
  one per missing 006 story (US4 resilient AI per-feature routing T024–T030; US5 user menu T031–T033; US6 AI model
  selection T034–T040; US7 sign-up notification T041–T042), one per missing 009 story (US4 remainder T027–T029,
  T032–T034; US2 remainder T039–T040; US3 bulk add T041–T047; US5 remainder T052–T053), the release-flow bug
  (runbook §20, FR-012b, S2), and the Ollama-removal bug (FR-012c, S4; 006 T025/T043/T045); link each to its
  matrix rows and set those rows to `FAIL`
- [X] T015 [US1] Fill plan sections 1, 2, 4, 10, 11 in `docs/testing/e2e-test-plan.md`: Summary, Contradictions
  (research R1, R2, R3, R9 + anything new from T004), high-risk order, NOT TESTABLE list, QUESTIONS FOR JORDY
  (include: re-confirm delegated deploy approval vs devops-agent rule; who pushes `v*` tags after the release flow
  ships)
- [X] T016 [US1] Write HANDOFF batch 1 in `docs/testing/e2e-test-plan.md` §7 per `contracts/handoff-block.md` and research R10, to be sent with T017: (01) sign in to
  prod as admin in the browser pane; (02) register the guest test account from a second profile and approve it;
  (03) scanner on the MacBook against local then prod — download URL, Fish/zsh commands, expected source names;
  (04) scanner on JordyBox/CachyOS against prod (Fish), incl. 003 T048 timer/linger checks; (05) yes/no: are
  `STEAM_WEB_API_KEY` and OpenRouter keys set in prod secrets (no values)
- [X] T017 [US1] ⚠️ **GATE**: present the plan summary (counts per status, bug list by severity, contradictions,
  questions, handoff batch 1) to Jordy and stop. No fix, branch or deploy until he approves; record
  `approved <date>` in plan section 1

**Checkpoint**: approved plan. Everything below may now run autonomously within the standing rules.

---

## Phase 4: User Story 2 — Production infrastructure, smoke and auth are proven healthy (P1)

**Goal**: Section A + B results with evidence; defects logged.

**Independent Test**: plan section 6 has PASS/FAIL for every check in `contracts/smoke-suite.md` plus the B role
matrix; every FAIL has a BUG.

- [X] T018 [P] [US2] Smoke A1–A4, A6–A10 with `dig`, `openssl s_client`, `curl -sI`, CORS preflight via
  `curl -X OPTIONS -H 'Origin: …'` against `https://jordylab.be`; record in `docs/testing/e2e-test-plan.md` §6-A
- [X] T019 [P] [US2] Smoke A11 + A13 in-cluster (read-only): pod readiness/restarts, readiness/liveness status,
  running image tags vs latest green `Build` SHA; also CNPG cluster + backup status
  (`kubectl -n jordylab get cluster,backup,scheduledbackup`)
- [ ] T020 [US2] Smoke A5 + A12 in the built-in browser pane: load `/`, reload a nested route for each lazy domain
  (fna, gamecatalog, settings), read console + network for errors/mixed content; screenshot evidence (redacted)
- [X] T021 [US2] Keycloak prod config check: issuer, redirect URIs, web origins and client list in
  `deploy/keycloak/realm-prod.json` vs `jordylab-be/compose/keycloak-realm-export.json` vs the live
  `.well-known/openid-configuration`; `KEYCLOAK_URL` under `/auth`; each app's `environment.prod.ts` in
  `jordylab-fe/apps/*/src/environments/`; log mismatches
- [ ] T022 [US2] API-level role matrix: for every matcher in
  `jordylab-be/src/main/java/dev/jordy/jordylab/shared/config/SecurityConfig.java`, call a representative
  endpoint as unauthenticated, pending, guest and admin (tokens obtained through the browser session, never typed
  passwords; read-only verbs on prod, mutating verbs on local); expected per specs 006/007; check error bodies carry
  no stack traces
- [ ] T023 [US2] B1–B3 flows in the browser: login → navigate → logout → login, silent token refresh (leave the tab
  past access-token lifetime), unauthenticated deep link → Keycloak, no redirect loop
- [ ] T024 [US2] Self-registration → admin approval into `guest` → guest sees only Game Catalog; pending and rejected
  users get nothing (depends on HANDOFF for approval and a second browser profile)
- [ ] T025 [US2] Settings user-management UI on prod (list/approve/reject/revoke) against the guest test account only;
  per-AI-feature model selection rows stay `FAIL` → linked to the 006 US6 bug from T014

**Checkpoint**: site, TLS, routing, identity and authz verified or logged.

---

## Phase 5: User Story 3 — Feature areas tested end to end with real data (P2)

**Goal**: Areas C–G tested; every matrix row closed with a final status.

**Independent Test**: no `TODO` rows remain for specs 001–010 in `docs/testing/e2e-test-plan.md`.

- [ ] T026 [US3] Start the local stack: `podman compose -f jordylab-be/compose.yaml up -d`, then `preview_start`
  `jordylab-be` and `jordylab-fe` (`.claude/launch.json`); confirm Flyway applied cleanly on the local DB
- [ ] T027 [US3] C-scanner: after HANDOFF batch 1 (T016) items 03–04 run, verify via API/UI/logs on local then prod: device-code login,
  `/ingest/check` before `/ingest/scan`, source auto-register/adopt on `(machineId, libraryType)`, re-scan
  idempotency (no duplicates), scanner token gets 403 on `/api/gamecatalog/games` and `/api/fna/**`
- [ ] T028 [P] [US3] C-steam (005): owned + family games, install status filter, host filter; if `STEAM_WEB_API_KEY`
  is absent in prod (handoff answer) → `BLOCKED — credentials`
- [ ] T029 [P] [US3] C-switch (009) built parts: add by search, validation errors, duplicate prevention, delete; local
  first, then prod as admin with a game Jordy wants kept (or deleted after, with his OK)
- [ ] T030 [US3] C-catalog UI: grid, filters, sort, search, virtual scroll with the real library, detail page, AI
  description + multiplayer metadata, artwork loads from PVC storage on prod, source management; empty and error states
- [ ] T031 [US3] C-chat (≤ 10 AI calls): SSE streaming, abort mid-stream, error/fallback display, grounding probe
  (ask about a game not in the catalog), history persists across reload; in-cluster check that pgvector is enabled,
  the ivfflat index exists and embeddings are populated (read-only `psql` query through `kubectl exec` is a
  mutation-free read — ask Jordy once before the first `exec`)
- [ ] T032 [P] [US3] D-FNA (001, ≤ 3 briefing calls): RSS ingestion schedule + dedup (observe two cycles), scraping
  edge cases from logs, `.BR`/`.AS` pricing, the three views, empty/error states; compare actual provider/fallback
  in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/` with the AGENTS.md routing table → mismatches logged
- [ ] T033 [P] [US3] E-mobile (007 web side): `/api/mobile/releases/latest` authz, download-link issue as approved
  user, expiry and signature tampering on `/api/mobile/download/**`, Ntfy dispatch (observe on the ntfy topic),
  review `.github/workflows/android-release.yml`; native behavior → `NOT TESTABLE — hardware`
- [ ] T034 [P] [US3] F-eufy: confirm no presence endpoints are exposed (`/api/**` denyAll catches them); rows already
  NOT BUILT (T009)
- [ ] T035 [P] [US3] G-migrations: start a separate, throwaway Postgres + pgvector container (own name, port and
  volume — never touch the `jordylab-be/compose.yaml` volume, which holds real scanned data, and never prod), point
  Flyway at it and migrate from scratch, then remove that container;
  compare `flyway_schema_history` versions local vs prod (read-only) for drift; schema-per-module ownership
- [ ] T036 [P] [US3] G-UX: accessibility basics (labels, focus order, contrast on main pages), phone width 375 px and
  desktop, back/forward, double-submit on Switch add and Settings approve, throttled network, large lists
- [ ] T037 [P] [US3] G-security: re-run `gitleaks`, verify no admin endpoint reachable as guest (from T022), CORS not
  `*`, dependency alerts via `gh api repos/jordy-swinnen/JordyLab/dependabot/alerts` (if permitted)
- [x] T038 [US3] Close every remaining row: PASS / FAIL (+BUG) / BLOCKED / NOT TESTABLE with reason; update the
  AI tally total in `docs/testing/e2e-test-plan.md` §8

**Checkpoint**: full matrix status; bug log complete for the first pass.

---

## Phase 6: User Story 4 — Defects fixed, deployed and verified on production (P2)

**Goal**: Each bug taken from OPEN to VERIFIED-PROD (or deferred with Jordy's agreement).

**Independent Test**: one bug goes reproduce → red test → fix → PR → deploy → prod evidence, and its bug entry and
deployment record are complete.

**Batch procedure (applies to T040–T049 and every discovered bug)**: fresh branch `fix/e2e-<topic>` from updated
`main` → quote the real error → failing test first (backend: JUnit/AssertJ, TestBuilders; frontend: Vitest +
Spectator per `/angular-test`) → fix → relevant tests only (+ `/modularity-check` on structural change) → local
real-flow check → `gitleaks detect --redact` → conventional commit → PR → wait for `Build` + `claude-pr-review` →
address comments → merge → T050 deploy + verify. One batch in flight at a time.

- [ ] T039 [US4] Triage `docs/testing/bug-log.md`: order S1 → S4; S1s first, then T040, then S2s in already-built
  flows, then T041–T048, then S3/S4 (research R11, plan Phase 2)
- [x] T040 [US4] Release-flow bug (branch `fix/e2e-release-flow`, research R11): add
  `.github/workflows/release.yml` (on `v*`: verify commit on `main` + green Build, retag `sha-<sha>` → `vX.Y.Z`,
  `gh release create --generate-notes`, deploy via `production` environment); change
  `.github/workflows/deploy-prod.yml` (drop `workflow_run`, input `sha` → `version`, tags from `${VERSION}`, pin
  `kubectl` to the k3s minor and `kustomize` to a release URL); retire the `mobile-v*` trigger in
  `.github/workflows/android-release.yml` and move its steps into `release.yml` behind an APK job gated on 007 T052
  (gate cleared 2026-09-30 — keystore secrets + realm clients live, PR #34); update `docs/runbook.md` §10/§11/§20, `.claude/agents/jordylab-devops.md`,
  `.opencode/agents/jordylab-devops.md`, and research R8 in this spec. Validate with `actionlint` if available.
  ⚠️ **GATE**: CI/config change — deploy pauses for Jordy; the first `v0.0.1-rc1` tag push and the GitHub rulesets
  (tag + `main` branch) are HANDOFF items, never changed by the agent
- [x] T041 [US4] After T040 is live: prove it once — Jordy (or the agent, if he agreed in T015's question) pushes
  `v0.0.1-rc1`; verify retag (no rebuild), release notes, approved deploy, running tags `v0.0.1-rc1`; then roll back
  once via *Run workflow* with the previous version and forward again; record in plan §9
- [x] T042 [US4] Ollama-removal bug (branch `fix/e2e-remove-ollama`, FR-012c, 006 T025 pre-approved): remove
  `spring-ai-starter-model-ollama` and `testcontainers-ollama` from `jordylab-be/build.gradle.kts`, the
  `OllamaContainer` bean from `jordylab-be/src/test/java/dev/jordy/jordylab/TestcontainersConfiguration.java`, the
  commented service from `jordylab-be/compose.yaml`; rewrite `AGENTS.md` AI Routing (Anthropic only), Infrastructure
  (drop Ollama host + WireGuard lines), Reference Docs and Shared Gotchas Ollama rows; check `.claude/rules/` and
  `.opencode/` copies; `./gradlew build` green; `grep -ri ollama` outside `specs/` returns nothing; tick 006 T025,
  T043 (Ollama parts), T045 in `specs/006-settings-module/tasks.md`
- [x] T043 [US4] 006 US4 bug (branch `fix/e2e-settings-ai-routing`): implement `specs/006-settings-module/tasks.md`
  T024, T026–T030 exactly as written there (Spring AI 2.0.1 GA, `AiFeature`, `ResilientAiService` rewrite with red
  test first, call sites, `AiModelResolver`); tick them in that file
- [ ] T044 [US4] 006 US5 bug (branch `fix/e2e-settings-user-menu`): implement 006 T031–T033 (`UserMenuComponent`,
  shell header, AIA password update verified against local Keycloak)
- [ ] T045 [US4] 006 US6 bug (branch `fix/e2e-settings-ai-models`, depends on T043): implement 006 T034–T040
  (entities + repositories via `/entity`, Flyway migration via `/flyway-migration` in the `settings` schema,
  `AiModelSettingsService`, `OpenRouterModelCatalogClient`, controller, frontend store via `/angular-signal-store`,
  AI Models page + route). ⚠️ **GATE**: contains a Flyway migration → deploy pauses for Jordy; needs OpenRouter
  secret present in prod (handoff answer)
- [ ] T046 [US4] 006 US7 bug (branch `fix/e2e-settings-signup-notify`): implement 006 T041–T042 (`NtfyClient`,
  `PendingSignupNotifierService`)
- [ ] T047 [US4] 009 US4 + US2 remainder bug (branch `fix/e2e-switch-detail`): implement 009 T027–T029, T032–T034,
  T039–T040 as written in `specs/009-switch-games/tasks.md`
- [ ] T048 [US4] 009 US3 bulk add bug (branch `fix/e2e-switch-bulk-add`): implement 009 T041–T047
- [ ] T049 [US4] 009 US5 remainder bug (branch `fix/e2e-switch-edit-remove`): implement 009 T052–T053
- [ ] T050 [US4] Deploy + verify each merged batch per `contracts/deployment-record.md`: state the record; if any of
  migration/realm/secret-config is "yes" → ⚠️ **GATE**; else find the pending run (`gh run list --workflow deploy-prod.yml`,
  or `release.yml` after T041) and approve via `gh api …/pending_deployments` with a comment naming the BUG IDs; if
  approval is refused, report the exact error and stop; watch rollout of backend/frontend/keycloak; confirm running
  tags; re-run the bug's repro and `contracts/smoke-suite.md` on prod; set VERIFIED-PROD with evidence
- [ ] T051 [US4] Regression handling: on login break, 5xx, or failed rollout after a deploy, immediately redeploy the
  previous good SHA/version (`gh workflow run deploy-prod.yml -f sha=…` or `-f version=…` after T041), log an S1
  incident BUG with the `Incident:` line, and ⚠️ **GATE** for Jordy
- [ ] T052 [US4] Repeat T039–T051 for every bug discovered in US2/US3 until all S1/S2 are VERIFIED-PROD and each
  S3/S4 is fixed or deferred with Jordy's recorded agreement

**Checkpoint**: bug log reflects final states; each deployment has a record in plan §9.

---

## Phase 7: User Story 5 — Human-in-the-loop handoffs (P3)

**Goal**: Jordy's hands-on steps batched, short, and independently verified.

**Independent Test**: every HANDOFF in plan §7 is `verified` or `failed` with a reason, and each took ≈ ≤ 10 min.

- [ ] T053 [US5] Track HANDOFF batch 1 (sent in T016) while continuing other areas: keep plan §7 statuses
  current (`sent → reported`), record how long each handoff took Jordy, and adjust/resend a block if a step fails
- [ ] T054 [US5] Write HANDOFF batch 2 when needed: GitHub rulesets for `v*` tags and `main` (T040), first release
  tag (T041), APK install on the Android phone after any mobile fix, any pod-restart persistence test Jordy approves
- [ ] T055 [US5] For each reported handoff, verify independently (API/UI/logs/cluster) and update plan §7 status;
  never mark PASS on report alone when a check is possible

---

## Phase 8: Polish & Close-out

- [ ] T056 Full final smoke on `https://jordylab.be` after the last deploy: `contracts/smoke-suite.md` A + B, one full
  pass through C and D; zero console errors / failed requests (SC-004)
- [ ] T057 [P] Verify every success criterion in `docs/testing/`: SC-001 (no `TODO` rows), SC-002 (all S1/S2
  `VERIFIED-PROD`; each S3/S4 fixed or deferred with Jordy's recorded agreement), SC-003 (regression test or reason
  per fixed bug), SC-005 (`gitleaks` clean, no secrets in `docs/testing/*`), SC-006 (statement that no database was
  seeded, with any BLOCKED-no-real-data rows listed), SC-007 (AI tally ≤ 30 per pass), SC-008 (every rollback has an
  S1 incident entry and happened before the next batch started), SC-009 (handoff durations from plan §7); record the
  result table in plan §1
- [ ] T058 Commit `docs/testing/e2e-test-plan.md`, `docs/testing/bug-log.md` and `specs/011-prod-e2e-hardening/` on
  `011-prod-e2e-hardening`; open a PR to `main` (body ends with the Claude Code attribution line); T060 adds the
  manual runbook to this same PR
- [x] T059 Final report to Jordy (FR-026): tested scope, bugs found/fixed/remaining by severity, AI calls used, NOT
  TESTABLE with reasons, recommended next steps (scheduled smoke-suite GitHub Action, Eufy 010 implementation,
  garmin sidecar)
- [x] T060 Final task (FR-027): write `docs/testing/manual-test-runbook.md` per
  `specs/011-prod-e2e-hardening/contracts/manual-runbook-entry.md` — one entry for every coverage row still
  `NOT TESTABLE` or `BLOCKED` after all handoffs (excluding `NOT BUILT` rows, which get a one-line pointer section);
  expected entries include native Android behavior (APK install/update, deep links, push notifications), anything
  from 003 T048 JordyBox timer/linger not covered by handoff, Steam family sync if credentials were missing, restore
  drill (runbook §15) and pod-restart persistence if not approved; link it from `docs/runbook.md` and
  `docs/testing/e2e-test-plan.md` §10; `gitleaks detect --redact`; commit and push to the T058 PR (SC-010)

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (T001–T003) → Foundational (T004–T007) → **US1 (T008–T017, ends at GATE)**.
- After approval: US2 (T018–T025) and handoff tracking/verification (T053, T055) run first; US3 (T026–T038) needs US2's auth results
  (T022/T024) and scanner handoffs (T016, tracked in T053).
- US4 fix loop (T039–T052) starts as soon as the first bug is triaged after approval; it does not wait for US3 to finish,
  but deploys are serialized (T050).
- Close-out (T056–T060) after the last verified deploy; T060 (manual runbook) is always last.

### Fix batch dependencies

- T040 (release flow) before other non-S1 batches; T041 proves it.
- T043 → T045 (AI models depend on `AiFeature` + resolver).
- T042 (Ollama) is independent; can merge before or after T043 but must not conflict — rebase on `main` each time.
- T047/T048/T049 independent of each other; serialize deploys.

### Within each fix

Reproduce → red test → fix → relevant tests → local real flow → PR → merge → deploy → prod verification.

## Parallel Opportunities

- T003 with T002; T005/T006/T007 together; T010–T013 baselines together.
- T018/T019 together; T028/T029 together; T032–T037 together (different areas, read-mostly).
- Fix branches can be *developed* in parallel (different files), but PR merges and deploys are one at a time.

## Parallel Example: User Story 1

```text
T010 backend baseline   | T011 frontend baseline | T012 scanner baseline | T013 gitleaks
```

## Implementation Strategy

### MVP (User Story 1 only)

T001–T017: Jordy gets the full coverage matrix, baseline, known-bug list and contradictions, and nothing in the product
has changed. Stop and validate at the T017 gate.

### Incremental delivery

1. US1 → approval.
2. US2 → S1 fixes deployed via today's SHA path.
3. T040/T041 release flow → every later deploy uses version tags.
4. US3 + remaining fix batches, one deploy at a time.
5. Close-out, ending with the manual-test runbook (T060).

## Notes

- Tick tasks in the *source* spec (`specs/006-*/tasks.md`, `specs/009-*/tasks.md`) when a fix completes them.

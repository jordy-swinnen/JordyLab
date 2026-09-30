# Feature Specification: Production End-to-End Test, Bug Log and Fix Loop

**Feature Branch**: `011-prod-e2e-hardening`

**Created**: 2026-09-30

**Status**: Draft

**Input**: User description: "Production end-to-end test, bug log and fix loop for https://jordylab.be. Source brief: jordylab-e2e-test-and-fix-prompt.md — mission, ground rules, human-in-the-loop HANDOFF blocks, Phase 0 recon/coverage matrix across specs 001-010, Phase 1 test areas A-G, the bug-log format, Phase 2 fix loop, Phase 3 delegated deploy approval + rollback, and the definition of done."

## Context

JordyLab went live on https://jordylab.be for the first time on 2026-09-30. To get a first version online,
several features (specs 001–010) shipped with light testing. This feature is a structured quality campaign,
not a new product capability: test everything that can be tested end to end, record every defect in a
single log, fix defects in small reviewable batches, ship each batch to production, and re-verify on
production. The production domain is `jordylab.be`; "JordyBox" is the home HTPC that runs the game scanner.

**Actors**

- **QA/fix agent** — does the bulk of the work: recon, testing, logging, fixing, deploying, verifying.
- **Jordy (owner/admin)** — approves the test plan, performs hands-on steps that need his devices or
  accounts, answers product questions, and approves anything outside the delegated scope.
- **Guest test user** — a dedicated, approved guest account on production used to exercise guest-level access
  without touching Jordy's real admin data.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Test plan and coverage matrix before any fix (Priority: P1)

Jordy wants a complete, evidence-based picture of what JordyLab is supposed to do and how well each part is
currently proven, before anyone changes code. The agent reads the governing docs and every feature spec
(001–010), builds a coverage matrix that maps each user story, acceptance scenario and functional requirement
to where it lives, which environment can test it, and the current evidence, runs the existing automated test
suites once as a baseline, and presents a plan summary for Jordy's approval.

**Why this priority**: Every later step depends on knowing what "correct" means. Without the matrix there is
no definition of done and no way to tell a bug from an unspecified behavior.

**Independent Test**: Delivering only the test plan, the empty-but-structured bug log (with baseline test
failures entered), and the approval pause is useful on its own: Jordy gets a risk map of the live platform.

**Acceptance Scenarios**:

1. **Given** specs 001–010 exist, **When** recon completes, **Then** the test plan lists every user story and
   functional requirement of every spec with expected behavior, location (page and endpoint), testable
   environment (local / prod / both / neither) and current evidence.
2. **Given** the brief and the repository disagree on a fact (e.g. a referenced agent, doc, workflow or
   domain), **When** recon finds it, **Then** the plan summary lists the contradiction and follows the
   repository.
3. **Given** baseline automated suites are run once, **When** any suite, boundary check or coverage gate
   fails, **Then** each failure is logged as a bug.
4. **Given** the plan summary is ready, **When** it is presented, **Then** no fix is made and nothing is
   deployed until Jordy approves.
5. **Given** high-risk areas exist (unchecked tasks/checklist items, missing validation results, specs 005–010,
   local-vs-prod configuration differences), **When** the plan is written, **Then** those areas are flagged and
   scheduled first.

---

### User Story 2 - Production infrastructure, smoke and auth are proven healthy (Priority: P1)

Jordy wants confidence that https://jordylab.be is reachable, secure and correctly routed, and that login,
logout, session refresh and role-based access (admin, guest, pending/rejected, unauthenticated) behave as
specified — at the API level, not just by hidden UI.

**Why this priority**: If the site, TLS, routing or login is broken, nothing else is usable; authorization gaps
are security defects.

**Independent Test**: A smoke pass (Section A) and an auth/roles pass (Section B) can be run against production
alone and produce a pass/fail list with evidence.

**Acceptance Scenarios**:

1. **Given** the public site, **When** the smoke pass runs, **Then** DNS resolves, TLS is valid with its expiry
   recorded, plain HTTP redirects to HTTPS, the app, API and identity routes answer, deep links survive a page
   reload, and no page shows console errors, failing requests or mixed content.
2. **Given** each route and each API endpoint, **When** called as admin, guest, pending user and unauthenticated,
   **Then** the result matches the spec (guest limited to Game Catalog; unauthenticated gets 401/403) and error
   responses expose no stack traces or secrets.
3. **Given** a newly self-registered user, **When** an admin approves them into the guest role, **Then** the user
   gains exactly guest access; a rejected or pending user gets none.
4. **Given** a per-AI-feature model selection in Settings, **When** it is changed, **Then** it persists and the
   selected model is observably used by the next AI call for that feature.
5. **Given** cross-origin requests, **When** made from an origin other than the production site or the mobile
   app origin, **Then** they are refused (no wildcard origin).

---

### User Story 3 - Feature areas tested end to end with real data (Priority: P2)

Jordy wants each product area exercised end to end — happy path, validation/error paths, empty states,
authorization, and refresh/deep-link behavior — using only real data produced by real flows.

Areas: Game Catalog (scanner ingest, Steam library sync, manual Switch games, catalog grid/filters/detail,
artwork, source management, grounded chat with streaming and history), Financial News Aggregation (feed
ingestion and dedup, pricing for Brussels/Amsterdam tickers, AI briefings, its three views, provider fallback),
Mobile web-side (release hosting, signed download links, notification dispatch), Eufy presence (only what is
testable without hardware), and cross-cutting checks (module boundaries, schema migrations on fresh and prod
history, garmin sidecar without credentials, accessibility, responsive layout, back/forward, double-submit,
slow network, large lists, secret scanning).

**Why this priority**: This is where most latent defects are expected, but it depends on US1 (plan) and US2
(working site and login).

**Independent Test**: Each area can be run and reported on its own; every coverage-matrix row for that area ends
as PASS, FAIL (logged), BLOCKED or NOT TESTABLE with a reason.

**Acceptance Scenarios**:

1. **Given** a data-mutating or destructive test, **When** it is planned, **Then** it runs on local first and
   only read-mostly or deployment-specific checks run directly on production.
2. **Given** a test needs data, **When** no real flow can produce it, **Then** the area is logged BLOCKED — the
   database is never hand-seeded.
3. **Given** the scanner runs on Jordy's real machines against local then production, **When** it finishes,
   **Then** the agent independently verifies device login, check-then-scan, source auto-register/adopt,
   re-scan idempotency, and that the scanner's credential cannot reach any other API.
4. **Given** the grounded chat, **When** questions are asked, **Then** answers stream, can be aborted, show an
   error/fallback state on failure, stay grounded in the catalog, and history persists; semantic search works
   on production.
5. **Given** a feature needs physical hardware or third-party credentials not available (native Android, Eufy
   HomeBase/geofence, Garmin), **When** it is reached, **Then** it is logged NOT TESTABLE with the exact reason.
6. **Given** the AI provider routing documented in AGENTS.md, **When** the actual primary/fallback behavior is
   observed, **Then** any mismatch is logged.

---

### User Story 4 - Defects fixed, deployed and verified on production (Priority: P2)

Jordy wants each confirmed defect fixed at its root cause, covered by a regression test where practical,
shipped in a small reviewable batch, deployed to production, and verified there with evidence — with an
immediate rollback if production regresses.

**Why this priority**: The goal is a fully functional site, not just a list of bugs; but fixes are only safe
once testing has established the baseline.

**Independent Test**: A single S1/S2 bug can be taken through reproduce → failing test → fix → reviewed change →
deploy → production re-verification, and its log entry reaches VERIFIED-PROD with evidence.

**Acceptance Scenarios**:

1. **Given** open bugs, **When** fixing starts, **Then** they are handled in severity order (S1 first), with the
   real error quoted before a fix is proposed.
2. **Given** a fix, **When** it is submitted, **Then** it lives on its own topic branch, touches only one area,
   is reviewed via pull request, and passes the build and automated review before merge; nothing is pushed
   directly to `main`.
3. **Given** a merged fix with a green build, **When** the production deployment awaits approval, **Then** the
   agent states what is being deployed (change, bug IDs, migrations yes/no) and may approve it itself — unless
   it contains a schema migration, an identity-realm change, or a secret/config change, in which case it pauses
   for Jordy.
4. **Given** self-approval is refused by the platform, **When** approval fails, **Then** the agent reports the
   exact failure and setting involved and waits; it never changes protection rules, reviewers or secrets.
5. **Given** a deployment finishes, **When** it is verified, **Then** the running version matches the merged
   commit, the original reproduction steps pass on production, and the smoke pass is green.
6. **Given** production regresses after a deploy (login broken, server errors, failed rollout), **When** detected,
   **Then** the previous good version is redeployed immediately, an S1 incident is logged, and work stops for
   Jordy's input.
7. **Given** a fix would need a product decision, a schema-breaking migration or a secret rotation, **When** it
   is identified, **Then** the agent stops and asks instead of proceeding.

---

### User Story 5 - Human-in-the-loop handoffs that take Jordy minutes (Priority: P3)

Jordy wants to be asked only for what the agent genuinely can't do (running the scanner on his machines,
device-code login in his browser, phone/APK install, Eufy/Garmin/Steam accounts, a second browser profile),
batched, with copy-paste-ready commands compatible with his Fish shell, and verified afterwards by the agent.

**Why this priority**: Improves throughput and Jordy's time, but testing and fixing can proceed around it.

**Independent Test**: One HANDOFF block for the scanner can be issued, executed by Jordy in a few minutes, and
independently verified by the agent.

**Acceptance Scenarios**:

1. **Given** several steps need Jordy, **When** they are ready, **Then** they are sent together as numbered
   HANDOFF-<nn> blocks with goal, machine, exact commands, expected result and what to send back.
2. **Given** Jordy reports a handoff done, **When** the result can be checked via the site, API or logs, **Then**
   the agent verifies it before marking PASS; his word alone is never the evidence when a check is possible.
3. **Given** a handoff is outstanding, **When** the agent waits, **Then** it continues other areas instead of
   idling, and every handoff and outcome is recorded in the test plan.

---

### Edge Cases

- A spec is silent on expected behavior → logged as a QUESTION for Jordy, work continues elsewhere.
- Cluster access (kubeconfig + private network) is unavailable on this machine → rely on browser, HTTP checks
  and CI logs; log reduced visibility as a limitation; pod-restart persistence test is skipped unless access
  exists and Jordy approves it.
- A paid AI call fails repeatedly → stop calling that path and report; no retry loops.
- The AI call budget is reached before all AI features are proven → remaining AI checks are logged BLOCKED
  (budget) and raised with Jordy.
- The scanner finds no library or its login fails → area BLOCKED, no substitute data.
- A deployment pending approval is not one the agent just merged → never approved.
- A second fix batch is ready while the first is still being verified → it waits; one batch in flight at a time.
- A test would modify Jordy's real admin data → use the guest test account or run it locally instead.
- A screenshot, log or error body contains a secret or token → redacted before it enters any artifact.

## Requirements *(mandatory)*

### Functional Requirements

**Recon and planning**

- **FR-001**: The campaign MUST begin by reading the governing project docs and every feature spec (001–010)
  including plans, tasks, quickstarts, contracts, checklists and validation results where present, treating
  them as the source of truth for expected behavior, and verifying facts against the live repository.
- **FR-002**: The campaign MUST produce a coverage matrix in which each spec user story, acceptance scenario and
  functional requirement has: spec ID, expected behavior, location (page and endpoint), testable environment,
  and current evidence.
- **FR-003**: The campaign MUST flag high-risk areas (unchecked tasks/checklist items, missing validation
  results, specs 005–010, local-vs-prod configuration differences) and test them first.
- **FR-004**: The campaign MUST run each existing automated suite once as a baseline (backend incl. module
  boundaries and coverage gates, frontend tests and lint, scanner and sidecar tests) and log failures as bugs.
- **FR-005**: The test plan and bug log MUST be written to `docs/testing/e2e-test-plan.md` and
  `docs/testing/bug-log.md`, and a plan summary with any brief-vs-repo contradictions MUST be presented to
  Jordy; no fix or deploy happens before his approval. After approval, work continues autonomously within the
  limits below.

**Test execution**

- **FR-006**: Every area in scope (A infrastructure/smoke, B auth/roles/settings, C game catalog, D financial
  news, E mobile web-side, F Eufy presence, G cross-cutting) MUST be tested for happy path, validation/error
  paths, empty states, authorization, and refresh/deep-link behavior where applicable.
- **FR-007**: Data-mutating or destructive tests MUST run locally first; production is used for read-mostly and
  deployment-specific checks, and for data-mutating checks only via the guest test account or Jordy's explicit
  handoff.
- **FR-008**: The campaign MUST NOT insert, copy or seed data directly into any local or production database;
  data comes only from real flows (scanner, feed ingestion, Steam sync, manual entry through the app).
  Synthetic data is allowed only inside automated tests.
- **FR-009**: Authorization MUST be verified at the API level for admin, guest, pending/rejected and
  unauthenticated callers, and the scanner credential MUST be shown unable to reach non-scanner APIs.
- **FR-010**: Real AI calls MUST be capped at 30 per full test pass, use the cheapest path that proves the
  feature, stop on repeated failure, and the count MUST be recorded.
- **FR-011**: Anything that cannot be tested MUST be logged NOT TESTABLE with the concrete reason (missing
  hardware, credentials, or cluster access).
- **FR-012**: Ambiguous expected behavior MUST be logged as a QUESTION FOR JORDY rather than decided by the agent.
- **FR-012a**: A user story from a shipped spec that is missing or only partly built (known at spec time: 006 Settings
  US4–US7, open 009 Switch stories) MUST be logged as a bug and fixed by completing that spec's open tasks. Only spec
  010 (Eufy presence), which was never started, is recorded as NOT BUILT instead of a bug.
- **FR-012b**: Agreed-but-unimplemented operational processes also count as bugs. Known at spec time: the one-tag
  release flow of runbook §20 (release workflow, version-based deploy and rollback) — fixed early so later deploys use
  it; changing GitHub rulesets for it stays with Jordy.
- **FR-012c**: Ollama is removed from the project (Jordy's decision 2026-09-30): build dependencies, test containers,
  compose remnants and the live agent docs (`AGENTS.md` and its rule/agent copies). Historical specs are left as they are.

**Bug log**

- **FR-013**: The bug log MUST be append-only with one entry per defect, each having: ID (BUG-nnn), status
  (OPEN, FIXING, FIXED-LOCAL, DEPLOYED, VERIFIED-PROD, BLOCKED, WONTFIX-QUESTION), severity (S1 prod
  unusable/data loss/security, S2 core feature broken, S3 degraded with workaround, S4 cosmetic), area/spec,
  environment found, reproduction steps, expected behavior with spec citation, actual behavior (redacted),
  root cause, fix reference, regression test (or reason none), and production verification (date + how).
- **FR-014**: The test plan MUST keep NOT TESTABLE and QUESTIONS FOR JORDY sections and a handoff log.

**Fix loop**

- **FR-015**: Bugs MUST be fixed in severity order, root cause first, with a failing test written before the fix
  where practical and following project testing conventions.
- **FR-016**: Each fix batch MUST be on its own `fix/e2e-<topic>` branch with conventional commits, grouped by a
  single area, merged only via pull request after the build and automated review pass; unrelated fixes and
  refactoring beyond the fix are not allowed.
- **FR-017**: Only relevant tests are run per fix (plus the module-boundary check when structure changes), and the
  fix is verified locally with a real flow before merge.

**Deploy and verify**

- **FR-018**: Production deployment approval is delegated to the agent only for commits it just merged for this
  campaign with a green build, after stating what is deployed; changes containing a schema migration, an
  identity-realm change or a secret/config change MUST pause for Jordy.
- **FR-019**: The agent MUST NOT change repository or environment protection rules, reviewers or secrets; if
  self-approval is refused it reports the exact failure and waits.
- **FR-020**: At most one fix batch may be deployed at a time, and it MUST be verified (rollout complete, running
  version equals merged commit, original reproduction passes on production, smoke pass green) before the next.
- **FR-021**: On a production regression the previous good version MUST be redeployed immediately, an S1 incident
  logged, and work paused for Jordy.
- **FR-022**: A bug MAY be marked VERIFIED-PROD only with evidence captured from production.

**Safety and handoffs**

- **FR-023**: Secret values (keys, tokens, passwords, kubeconfig, encrypted secret contents, env files) MUST never
  appear in output, logs, commits, screenshots or bug reports; a secret scan MUST run before every push when the
  scanner is available.
- **FR-024**: Only a dedicated guest test account is used for production user-level testing; Jordy's real admin
  data is never modified or deleted, and no third-party account is acted on beyond the app.
- **FR-025**: Steps needing Jordy MUST be batched as HANDOFF-<nn> blocks (goal, machine, target environment, exact
  Fish-compatible commands, expected result, what to send back), prepared in advance, independently verified
  afterwards, and logged.

**Close-out**

- **FR-026**: The test plan and bug log MUST be committed via pull request, and a final report MUST cover what was
  tested, bugs found/fixed/remaining, AI calls used, what could not be tested and why, and recommended next steps.
- **FR-027**: As its final step, the campaign MUST produce a manual test runbook (`docs/testing/manual-test-runbook.md`)
  with one step-by-step procedure for every check that is still NOT TESTABLE or BLOCKED after all handoffs (NOT BUILT
  features are only listed, not given procedures). Each procedure states what it covers, why the agent could not run
  it, what is needed (device, account, access), Fish-compatible steps, the expected result per step, and how to record
  the outcome in the bug log or test plan. It is linked from the production runbook and included in the close-out PR.

### Key Entities

- **Coverage matrix row**: one spec story/scenario/requirement → expected behavior, location, testable
  environment, evidence, final status (PASS, FAIL-FIXED, BLOCKED, NOT TESTABLE + reason).
- **Bug entry**: one defect with the fields in FR-013; linked to coverage rows and to a fix batch.
- **Fix batch**: a group of related bugs in one area → branch, pull request, merged commit, deployment, verification
  evidence.
- **Handoff**: a request to Jordy → ID, goal, machine, commands, expected result, outcome, agent verification.
- **Question**: an open product decision → context, options, Jordy's answer.
- **Manual runbook entry**: one procedure for an untestable check → coverage row, reason, prerequisites, steps,
  expected results, how to record the outcome.
- **Deployment record**: merged commit (and release version once the release flow ships), bug IDs, contains migration/realm/secret change (y/n), approval, verification
  or rollback outcome.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of coverage-matrix rows across specs 001–010 end with a final status (PASS, FAIL-FIXED, BLOCKED
  or NOT TESTABLE with reason); zero blank rows.
- **SC-002**: 100% of S1 and S2 bugs reach VERIFIED-PROD; every S3/S4 bug is either fixed or explicitly deferred
  with Jordy's agreement.
- **SC-003**: Every fixed bug has a regression test or a written reason why none was added.
- **SC-004**: After the last deploy, a full smoke pass (infrastructure + auth/roles, plus one full pass through Game
  Catalog and Financial News) completes on the live site with zero failures, zero console errors and zero failing
  requests.
- **SC-005**: Zero secrets appear in any committed file, pull request, bug entry or screenshot produced by the
  campaign.
- **SC-006**: Zero rows are hand-seeded into any database during the campaign.
- **SC-007**: AI-backed calls per full test pass stay at or below 30, and the actual count is reported.
- **SC-008**: Every production regression, if any, is rolled back within one deploy cycle and logged as an S1 incident.
- **SC-009**: Each handoff to Jordy takes him no more than about 10 minutes of hands-on time.
- **SC-010**: 100% of checks left NOT TESTABLE or BLOCKED at close-out have a manual runbook procedure that Jordy can
  follow without asking the agent for missing steps.

## Assumptions

- The brief is the authoritative scope; where it and the repository disagree, the repository wins and the
  contradiction is reported. Known at spec time: the brief mentions a `browser-test` agent that does not exist in
  `.claude/agents/` (available agents: architect, code-reviewer, jordylab-devops, test-writer); the built-in browser
  or Claude in Chrome will be used instead. The deploy pipeline in `deploy-prod.yml` matches the brief (triggered only by a
  successful Build of a push to `main`, `production` environment gate, `workflow_dispatch` with `sha` for
  redeploy/rollback). Once the FR-012b release flow ships, deploys and rollbacks switch to version tags. A copy of the brief is committed at `Claude outputs/jordylab-e2e-test-and-fix-prompt.md`
  (same content as the downloaded version).
- `docs/testing/` does not yet exist and will be created by this campaign.
- The GitHub MCP server currently fails to connect in this session; the `gh` CLI is the fallback for branches, PRs,
  workflow runs and deployment approvals.
- Only two environments exist: `local` and `prod`.
- Cluster access is read-only and only if the kubeconfig and private network link are already present on this machine.
- The recommended next step of automating the smoke suite as a scheduled workflow is out of scope for this campaign
  and appears only as a recommendation in the final report.
- Spec 010 (Eufy presence) is not implemented; its rows are NOT BUILT. The `garmin-sync-service` directory contains no
  code, so it is NOT BUILT too. Missing parts of 006 and 009 count as bugs (FR-012a, decided by Jordy 2026-09-30).
- Native Android behavior, Eufy HomeBase/geofence behavior and Garmin data sync are expected to be NOT TESTABLE from
  this machine unless Jordy provides the device/account via a handoff.
- "Full test pass" for the AI cap means one complete run through areas A–G; re-verification after fixes counts
  against a fresh pass only if the whole matrix is re-run.

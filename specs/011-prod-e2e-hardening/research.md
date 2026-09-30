# Phase 0 Research: Production End-to-End Test, Bug Log and Fix Loop

All facts below were verified against the repository at `main` @ `e167de8` (2026-09-30) and this machine's
tooling. Where the brief and the repository disagree, the repository wins and the item is marked
**CONTRADICTION** — each one goes into the Phase 0 plan summary for Jordy.

## R1. What is actually built (scope of the coverage matrix)

**Decision** (confirmed by Jordy, 2026-09-30): The coverage matrix covers every story in specs 001–010.
- **010 Eufy** is out of scope as a feature: its rows are **NOT BUILT** (sub-reason of NOT TESTABLE), not bugs.
- **006 Settings US4–US7** and the **open 009 Switch stories** (US3 bulk add, manual-fallback/relink UI, detail
  format display, detail edit/remove, guest-403 tests) were supposed to ship → each missing story is logged as a
  **bug** (default S2: core feature missing) and goes through the fix loop by completing the open tasks in that
  spec's `tasks.md`.
- Other open tasks (001 housekeeping, 005 deferred Steam capture, 007/008 validation passes) are covered by the
  matching test rows; a failing behavior becomes a bug as usual.

Evidence (open tasks / total in each `tasks.md`):

| Spec | Open | Validation results | State |
|------|------|--------------------|-------|
| 001 fna | 2/47 | no | Built; 2 housekeeping tasks open |
| 002 game-catalog | 1/46 | yes | Built; T046 "production smoke" deferred until deploy → this campaign closes it |
| 003 scanner | 1/53 | yes | Built; T048 JordyBox/CachyOS E2E open → HANDOFF |
| 004 refinements | 0/46 | no | Built, never validated |
| 005 steam sync | 2/35 | no | Built; real Steam sync never run (T035) → needs `STEAM_WEB_API_KEY` in prod |
| 006 settings | **24/47** | no | **US4–US7 unbuilt**: per-AI-feature model selection, OpenRouter catalog, AI Models page, user menu, Ntfy pending-signup notifier |
| 007 mobile | 4/55 | no | Built; 2 stop-and-report gates (realm, signing key) + full validation open |
| 008 k8s deploy | 3/74 | no | Deployed; US1 checks, quickstart pass and port-scan review open → Section A |
| 009 switch | **17/58** | yes | Partly built: bulk add (US3), manual-fallback/relink UI, detail format display, edit/remove on detail open |
| 010 eufy | **68/68** | no | **Not implemented at all** (only spec artifacts exist; `Presence` hits in code are gamecatalog's own concept) |

**CONTRADICTIONS**:
- Brief §B "per-AI-feature model selection persists and is actually used by ResilientAiService" — not built
  (006 T026–T040) → logged as a bug per Jordy.
- Brief §C/§D mention OpenRouter — no OpenRouter code exists yet; arrives with the 006 US4/US6 fix.
- Brief §F Eufy presence — nothing to test; the whole area is NOT BUILT.
- Brief §G `garmin-sync-service` — the directory holds only `AGENTS.md`, `CLAUDE.md`, `.gitignore`; no code or tests.
  There is also no `garmin`, `recipe` or `trading` backend module (modules present: `fna`, `gamecatalog`, `mobile`,
  `settings`, `shared`).
- AGENTS.md AI-routing/Ollama guidance still describes Ollama. Correction to the first recon pass: no Ollama *Java*
  code remains in `jordylab-be/src/main`, but Ollama is still wired in `build.gradle.kts`
  (`spring-ai-starter-model-ollama`, `testcontainers-ollama`), `TestcontainersConfiguration.java` (`OllamaContainer`)
  and a commented service in `compose.yaml`. **Decision (Jordy, 2026-09-30): remove Ollama entirely** → the 006 T025
  stop-and-report gate is answered "yes"; T025 + T043 + T045 run as part of the 006 missing-story fix (or as their own
  `fix/e2e-remove-ollama` batch). Scope of the doc cleanup: root `AGENTS.md` (AI-routing table, Infrastructure,
  Reference Docs, Shared Gotchas) and any `.claude/rules/` or `.opencode/` copies; historical specs stay untouched.

**Rationale**: Jordy treats shipped-but-incomplete specs (006, 009) as defects; only 010 was never started.
**Consequences for the fix loop**:
- "Fix" for a missing-story bug = implement that story's open tasks as written in its own `tasks.md`/`plan.md`,
  one story per `fix/e2e-<topic>` branch (e.g. `fix/e2e-settings-ai-models`, `fix/e2e-switch-bulk-add`). This is
  the only case where a fix adds feature code; the "no refactoring beyond the fix" rule still applies.
- Stop-and-report gates inside those tasks stay in force (006 T025 Ollama-removal gate).
- 006 US6 adds entities (`AiFeatureModelSetting`, `AiFeatureLastRun`) → a Flyway migration → deploy **pauses** for
  Jordy (FR-018). 006 T024 bumps Spring AI to 2.0.1 GA — a dependency change inside the story, allowed as part of it.
- 006 US4/US6 depend on OpenRouter (model catalog). Whether OpenRouter secrets exist in prod is asked in HANDOFF
  batch 1 (yes/no only).
- These are large relative to other fixes; they are scheduled after all S1s and after the S2s that break
  already-built flows.
**Alternatives**: mark 006/009 gaps NOT BUILT — rejected by Jordy.

## R2. Cluster and CI visibility

**Decision**: Read-only cluster access is available and will be used.
Evidence: `kubectl`, `tailscale` present; `~/.kube/jordylab.yaml` exists; `tailscale status` shows `jordylab-vps` online.
`gh` is authenticated as `jordy-swinnen` with `repo`, `workflow` scopes. `gitleaks` is installed.
The GitHub MCP server failed to connect this session (bad Authorization header) → `gh` CLI is the channel.

Rules adopted from `.claude/agents/jordylab-devops.md`: only `get/describe/logs`, `kustomize build`; log output piped
through `grep -v -i -E 'password|secret|token'`; never show `~/.kube/jordylab.yaml` content.

**CONTRADICTION**: the devops agent says approving a deploy needs Jordy's explicit yes; the brief delegates approval
to the campaign agent for its own merged, green commits. The brief is Jordy's newer, explicit instruction for this
task → delegation applies within FR-018's limits, and the plan summary asks Jordy to re-confirm it once.

**Alternatives**: no cluster access (browser + HTTP + CI logs only) — kept as the fallback if Tailscale drops.

## R3. Production routing and health checks

**Decision**: Public smoke checks use `/`, a nested SPA route, `/api/**` (expect 401 unauthenticated),
`/auth/realms/jordylab/.well-known/openid-configuration`, and `/.well-known/assetlinks.json`. Backend health
(`/actuator/health/{liveness,readiness}`) is checked in-cluster (probes / `kubectl` port-forward is a mutation-free
read), because the Gateway routes only `/api` and `/.well-known/assetlinks.json` to the backend.
Evidence: Gateway table in the devops agent; `SecurityConfig` permits `/actuator/health/**`; prod enables probes.

**Rationale**: Don't log "health endpoint not public" as a bug — it is by design.
**Alternatives**: expose actuator publicly — rejected (security surface).

## R4. Baseline automated suites

**Decision**: one baseline run each, results recorded in the plan:

| Suite | Command | Gate |
|-------|---------|------|
| Backend | `cd jordylab-be && ./gradlew build` | tests incl. `ModularityTests`, JaCoCo ≥ 0.80 |
| Frontend | `cd jordylab-fe && bunx nx run-many -t test,lint` | all green |
| Scanner | `cd gamecatalog-scanner && python3 -m venv .venv && .venv/bin/pip install -e ".[dev]" && .venv/bin/python -m pytest --cov=src` | ≥ 80 % on `src` |
| Frozen client | `python tools/build_client.py` then `git diff --exit-code` on `jordylab-scan-template.py` | template in sync |
| garmin sidecar | — | NOT BUILT (R1) |
| Secret scan | `gitleaks detect --redact --no-banner` | clean |

`uv` is not installed; plain venv + pip is used (matches `gamecatalog-scanner/AGENTS.md`).

## R5. Local vs prod configuration risk list

**Decision**: these differences are high-risk and tested first on prod (source `docs/environments.md`):
Keycloak URL under `/auth` + `KC_HTTP_RELATIVE_PATH` + `KC_HOSTNAME`; issuer URI match; CORS
`https://jordylab.be,https://localhost`; realm `realm-prod.json` (no dev user, real redirects, `${env.VAR}` secrets);
`environment.prod.ts` in three apps (changed in the last 15 commits); PVC-backed artwork/APK/ntfy storage;
frontend served by nginx (deep-link fallback, gzip, cache headers).

## R6. Test identities

**Decision**: Four identities — admin (Jordy's, read-mostly on prod), one dedicated guest test account (created by
self-registration and approved by Jordy via HANDOFF), one pending account (registered, never approved) and
unauthenticated. The scanner identity (`gamecatalog-scanner` role) is exercised only through the real client.
The agent never types Jordy's password: admin-authenticated browser checks run in a browser session Jordy signs into.

**Rationale**: satisfies FR-024 and the prohibition on entering real credentials.
**Alternatives**: a second admin test account — rejected, requires a realm change (FR-018 pause).

## R7. AI budget accounting

**Decision**: a tally table in the test plan, one row per AI-backed call (feature, env, purpose, result).
Allocation for one full pass (≤ 30): FNA briefing 3, gamecatalog enrichment 5, grounded chat 10 (incl. abort +
grounding probes), fallback/error-path 4, reserve 8. Enrichment is triggered by real scans, so its count is
observed from backend logs rather than chosen.

## R8. Deploy approval mechanics

**Decision**: after merge and green `Build`, find the `Deploy to Production` run
(`gh run list --workflow deploy-prod.yml`), list pending deployments
(`gh api repos/jordy-swinnen/JordyLab/actions/runs/<id>/pending_deployments`), state the deploy record, then approve
with `gh api -X POST .../pending_deployments -f "environment_ids[]=<id>" -f state=approved -f comment="BUG-…"`.
If GitHub refuses (e.g. "prevent self-review" or token not a reviewer): stop and report. Rollback =
`gh workflow run deploy-prod.yml -f sha=<previous-good-sha>`. Runbook §20 tag-based release flow is **not implemented**
("Changes to make when implementing") → not relied upon **until the R11 fix ships**.

Version verification: running image tag via `kubectl -n jordylab get deploy -o jsonpath` (images are
`...:sha-<full-sha>`).

## R9. Browser tooling

**Decision**: the built-in browser pane for localhost and prod UI checks; Claude in Chrome only if Jordy asks
(his logged-in sessions). **CONTRADICTION**: no `browser-test` agent / `agent-browser` exists in `.claude/agents/`.

## R10. Handoff format

**Decision**: HANDOFF blocks follow `contracts/handoff-block.md`; commands are single-line Fish-compatible
(no heredocs, `set -x VAR value` instead of `export`), with the target env stated in the first line.
Expected first batch: (1) sign in to prod in the browser pane as admin; (2) approve the guest test account;
(3) run the scanner on the MacBook against local then prod; (4) run it on JordyBox/CachyOS against prod;
(5) confirm `STEAM_WEB_API_KEY` is set in prod secrets (yes/no only, no value).

## R11. Version-tag release flow (runbook §20) is a bug

**Decision (Jordy, 2026-09-30)**: the missing tag-based release flow is logged as a bug (S2 — the agreed release
process doesn't exist) and fixed in its own batch `fix/e2e-release-flow`, implementing runbook §20 "Changes to make
when implementing":
- new `.github/workflows/release.yml` on `v*` tags: verify the tag's commit is on `main` with a green Build, **retag**
  `sha-<sha>` images to `vX.Y.Z` (no rebuild), create the GitHub Release with generated notes, then deploy through the
  `production` environment;
- `deploy-prod.yml`: drop the `workflow_run` trigger, `workflow_dispatch` input `sha` → `version`, image tags from
  `${VERSION}`, pin `kubectl` to the k3s server minor version and `kustomize` to a release URL;
- `android-release.yml`: retire the `mobile-v*` trigger, move its steps into `release.yml`; the APK job stays
  **disabled** while the 007 T052 keystore/realm gate is open — **cleared 2026-09-30** (keystore secrets set by Jordy,
  realm clients live, PR #34), so the APK job runs on every release;
- runbook §10/§11/§20 and the devops agent (both `.claude/` and `.opencode/` copies) updated to the new flow.

**Consequences for the campaign**:
- This batch changes how every later deploy happens, so it is scheduled **early** — right after S1 fixes — and its
  own rollout is proven with a `v0.0.1-rc1` tag and one rollback via *Run workflow* (runbook §20 "Order of work").
- It is a CI/config change → the deploy pauses for Jordy (FR-018), and the first tag push is Jordy's call.
- The GitHub rulesets in §20 (only Jordy may create `v*` tags; `main` requires PRs + Build) are settings the agent must
  not change (FR-019) → HANDOFF to Jordy.
- After it ships, research R8 changes: a merge no longer deploys; the agent deploys a fix by pushing a `v*` tag
  (with Jordy's agreement on who creates tags, see ruleset) and approving the release run; rollback = *Run workflow*
  with the previous good `version`. Deployment records then carry a version as well as the SHA
  (`contracts/deployment-record.md`, `quickstart.md` Phase 3 show both paths).
- Until then, every deploy uses today's SHA-based path.

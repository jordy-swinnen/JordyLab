# Implementation Plan: Production End-to-End Test, Bug Log and Fix Loop

**Branch**: `011-prod-e2e-hardening` | **Date**: 2026-09-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/011-prod-e2e-hardening/spec.md`

## Summary

A four-phase quality campaign over the live JordyLab platform: (0) recon → coverage matrix, baseline suites, test
plan and bug log, then pause for Jordy's approval; (1) execute test areas A–G on local and prod with real data only;
(2) fix bugs in severity order on small `fix/e2e-<topic>` branches; (3) deploy one batch at a time via the existing
`Build` → `Deploy to Production` pipeline with delegated approval, verify on prod, roll back on regression.

The campaign's own deliverables are documents (`docs/testing/e2e-test-plan.md`, `docs/testing/bug-log.md`) plus
whatever code fixes the bugs require. Research found that 010 Eufy and the garmin sidecar are **not built** (NOT
BUILT rows, not bugs), while 006 Settings US4–US7 and several 009 Switch stories were meant to ship but are
missing — per Jordy these are **bugs** (default S2), fixed by completing their specs' open tasks, one story per
fix branch; 006 US6 includes a Flyway migration, so its deploy pauses for Jordy (research R1).

## Technical Context

**Language/Version**: Existing stack — Java 25 / Spring Boot 4 (backend), Angular 21 / Nx 22 on Bun (frontend),
Python 3.12 stdlib client (scanner). Campaign tooling: `gh`, `kubectl` (read-only), `tailscale`, `gitleaks`, `curl`,
`openssl`, `dig`, `jq`, built-in browser pane.

**Primary Dependencies**: GitHub Actions (`build.yml`, `claude-pr-review.yml`, `deploy-prod.yml`), k3s + Traefik
Gateway, Keycloak 26.7 (`/auth`), CloudNativePG, Anthropic via `ResilientAiService`.

**Storage**: PostgreSQL 16 + pgvector (schema per module); campaign artefacts are Markdown in `docs/testing/`.

**Testing**: JUnit 5 / AssertJ / Mockito / Testcontainers / MockMvc (backend, JaCoCo ≥ 0.80); Vitest + Spectator
(frontend); pytest (scanner). Regression tests follow the `angular-test`, `test-builder`, `entity` skills.

**Target Platform**: https://jordylab.be (single-node k3s on OVH VPS-2) and the local Podman Compose stack.

**Project Type**: QA/fix campaign over an existing web application (monorepo: backend, frontend, Python client).

**Performance Goals**: None new. Smoke observes page load and SSE streaming but sets no new targets.

**Constraints**: ≤ 30 AI calls per full pass; no DB seeding; no secrets in any output; one deploy batch in flight;
pause for migrations / realm / secret changes; read-only cluster access; Fish-compatible handoff commands.

**Scale/Scope**: 10 specs, ~534 tasks total, roughly 60 user stories; 7 test areas; single admin + a handful of guests.

All former unknowns were resolved in [research.md](research.md) (R1–R10); none remain.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | How the campaign complies | Status |
|-----------|---------------------------|--------|
| I. Clean Code | Fixes are minimal, root-cause, no drive-by refactors (FR-016) | PASS |
| II. Fail fast, no silent failures | Every defect is logged; untestable items carry explicit reasons; repeated AI failures stop, never retry-loop | PASS |
| III. Immutable, builder-first | Any Java fix/test uses Lombok `@Builder` + TestBuilders | PASS |
| IV. Testing discipline | Failing test first where practical; `assertSoftly`, no `any()`, Spectator `useValue` mocks; synthetic data only inside tests | PASS |
| V. Tooling currency | No version bumps unless a bug requires it (006 T024 Spring AI GA bump stays out of scope) | PASS |
| AGENTS.md: never hand-seed DB | FR-008; BLOCKED instead | PASS |
| AGENTS.md: secrets | FR-023; `gitleaks --redact` before each push | PASS |
| AGENTS.md: module boundaries | `/modularity-check` on structural fixes | PASS |

Post-design re-check (after Phase 1 artefacts): unchanged — PASS. No complexity-tracking entries needed.

## Phase Structure (execution order)

1. **Phase 0 – Recon** (no code changes): read governing docs → build coverage matrix from all specs → baseline suites
   (research R4) → create `docs/testing/e2e-test-plan.md` + `bug-log.md` → issue HANDOFF batch 1 → **STOP for Jordy's
   approval** with plan summary + contradictions list (research R1, R2, R9).
2. **Phase 1 – Test** in this order: A (prod smoke) → B (auth/roles/settings) → C (game catalog, scanner handoffs in
   parallel) → D (FNA) → E (mobile web-side) → F (Eufy: NOT BUILT, verify only that no presence endpoints are exposed)
   → G (cross-cutting). Local first for mutations.
3. **Phase 2 – Fix loop**: S1 → S4; one area per branch; red test → fix → relevant tests → local real-flow check → PR.
   Missing-story bugs (006 US4–US7, 009 open stories) come after S1s and after S2s in already-built flows; each is
   one branch that completes that story's open tasks from its own `tasks.md`, honouring its stop-and-report gates
   (006 T025 Ollama removal is pre-approved by Jordy). The runbook §20 release-flow bug is fixed **right after S1s**,
   since it changes how every later batch is deployed (research R11); its deploy pauses for Jordy.
4. **Phase 3 – Deploy & verify**: deploy record → (pause if migration/realm/secret) → approve → rollout watch →
   image-tag check → re-run repro + smoke on prod → VERIFIED-PROD or rollback + S1 incident.
5. **Close-out**: PR with test plan + bug log; final report (FR-026); **last**, the manual test runbook for every
   check still NOT TESTABLE/BLOCKED (FR-027), added to the same PR.

## Project Structure

### Documentation (this feature)

```text
specs/011-prod-e2e-hardening/
├── spec.md
├── plan.md              # this file
├── research.md          # Phase 0 findings, contradictions, tooling decisions
├── data-model.md        # campaign records: coverage row, bug, fix batch, handoff, question, deployment
├── quickstart.md        # how to run/validate each campaign phase
├── contracts/
│   ├── bug-log-entry.md
│   ├── coverage-matrix.md
│   ├── handoff-block.md
│   ├── deployment-record.md
│   ├── manual-runbook-entry.md
│   └── smoke-suite.md
├── checklists/requirements.md
└── tasks.md             # /speckit-tasks (not created here)
```

### Source Code (repository root)

```text
docs/testing/
├── e2e-test-plan.md     # coverage matrix, handoff log, AI tally, NOT TESTABLE, QUESTIONS FOR JORDY
├── bug-log.md           # append-only BUG-nnn entries
└── manual-test-runbook.md  # final task: procedures for checks the agent could not run (FR-027)

# Fix targets (touched only as bugs require):
jordylab-be/src/main/java/dev/jordy/jordylab/{fna,gamecatalog,mobile,settings,shared}/
jordylab-be/src/test/java/dev/jordy/jordylab/...           # regression tests
jordylab-fe/apps/jordylab/  jordylab-fe/libs/{fna,gamecatalog,settings,shared,ui}/
gamecatalog-scanner/src/ (+ tools/build_client.py → jordylab-be/src/main/resources/scripts/jordylab-scan-template.py)
deploy/k8s/  deploy/keycloak/realm-prod.json               # realm/config changes → pause for Jordy
```

**Structure Decision**: No new modules or projects. Campaign records live in `docs/testing/`; fixes land in the
existing module that owns the defect.

## Complexity Tracking

None — no constitution violations.

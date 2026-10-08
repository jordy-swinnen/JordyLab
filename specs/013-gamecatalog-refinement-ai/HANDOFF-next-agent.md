# Handoff: finish spec 013 (written 2026-10-08 for a fresh agent, e.g. OpenCode)

Read this first, then `tasks.md` (ticked `[X]` = done) and `validation-results.md` (what was run and what it showed).

## State

- Everything in `tasks.md` up to **Phase 18 (Polish)** is implemented, tested and ticked. Frontend lint/Oxlint/tests: 17 projects green. `e2e/run.sh web`: 88 journeys passed. Backend: green except the known local container-start failures (see below).
- Work is on branch `claude/game-catalog-refinement-461e9c`, **PR #134** (https://github.com/jordy-swinnen/JordyLab/pull/134). Check its state first: `gh pr view 134 --json state,mergedAt,statusCheckRollup`. If it is merged, continue from "Release". If not, get CI green (the previous session fixed the review comments and the CI failures: a removed endpoint still in `RoleMatrixTest`, a phone-width assertion, a Mockito rule) and merge it.
- Dev stack was rebuilt on 2026-10-08: `jordylab-be-pgvector-1` (named volume `jordylab-be_pgdata`, empty) and `jordylab-be-keycloak-1` (port 8180). Never `compose down -v` or remove that volume. Compose is run from the worktree with `podman compose -p jordylab-be --env-file /Users/jordy/Projects/JordyLab/jordylab-be/.env up -d`.

## Still open

| Tasks | What | Who |
|---|---|---|
| T027, T072, T086, T100, T146-T149 | Real scans (Mac Steam, Linux box), `./gradlew goldenLive`, migration rehearsal, 20 fingerprint sign-ins, description comparison | **Owner**: `HANDOFF-30` and `HANDOFF-31` in `docs/testing/e2e-test-plan.md` |
| T150 | Decision gate: every story passed locally | Owner after the HANDOFFs; record in `validation-results.md` |
| T151-T153 | PR merged, pre-deploy backup + `vector` Database, tag rc and approve deploy | Agent (see Release) |
| T154-T157 | Real scan against jordylab.be, quickstart B2, fingerprint, close-out | Agent + owner |

The owner explicitly asked for a release so he can test on https://jordylab.be before finishing the local HANDOFFs. Release is therefore allowed; keep saying in the final report that T150 was not signed off.

## Release (runbook `docs/runbook.md` §20, §22)

1. PR #134 merged into `main` (CI required: backend, frontend, lint, `e2e-web`, refs). Merge only when all review comments are resolved.
2. Before the deploy (cluster mutations need the owner's yes unless already given; the owner approved the `Database` resource on 2026-10-07 and asked for the release): on-demand CNPG backup (§22 step 1), confirm `Completed`. `deploy/k8s/cluster/cnpg-database.yaml` (pgvector) must exist before the new backend starts; the normal deploy applies it, verify with §22 step 2.
3. Tag the next release candidate (look at existing tags, e.g. `git tag --sort=-v:refname | head`; last was `v0.0.1-rc19`), approve the production deploy per §20, watch the rollout and the backend log for Flyway (three new migrations: `V20261008001`, `V20261008002` Java backfill, `V20261008003`).
4. Verify on prod: login, library grid and filters, a game page, Sources (admin), LibBot (needs AI keys in the cluster secret, `OPENROUTER_API_KEY`), Consoles. Roll back per §11 if the migration fails.
5. Log findings in `docs/testing/bug-log.md` first, then fix (root `AGENTS.md`, Commits). Every commit needs a `Refs:` trailer, e.g. `Refs: 013 T152`; enable the hook with `git config core.hooksPath .githooks`; never `--no-verify`.

## Things to know

- **Known local test failures** (they pass in CI): `RoleMatrixTest`, `GuestChatLimitIntegrationTest` and sometimes `JordylabApplicationTests` fail on this Mac with `localhost:2375 failed to respond` (Testcontainers/Podman). Run backend tests with `DOCKER_HOST=unix://$(podman machine inspect --format '{{.ConnectionInfo.PodmanSocket.Path}}') TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --tests '<pattern>'`.
- **Never seed the database by hand**; real data comes from the real scanner (root `AGENTS.md`, Validation Data). Never print secrets; `jordylab-be/.env` holds real keys (the live golden run and IGDB cost money: ask before running `goldenLive`).
- Rules the reviewer enforces: no `var`, no Mockito `any*()` matchers, blank line before `return`, explicit types, TestBuilder pattern, `assertSoftly`.
- Frontend: Bun/Nx (`bunx nx run-many -t lint oxlint test`), signal stores, Vitest + Spectator, no `fakeAsync`.
- Deviations from the contracts are written at the end of each file in `contracts/`.
- Module rule: `gamecatalog` must not depend on `settings` (events only; `UserAccessRemoved` lives in `shared.event`).

## Prompt to give the next agent

> Read `specs/013-gamecatalog-refinement-ai/HANDOFF-next-agent.md`, then `CLAUDE.md`/`AGENTS.md`. Check PR #134 (`gh pr view 134`). If it is not merged, make CI green and merge it once review comments are resolved. Then do the release steps in the handoff (backup, pgvector `Database`, tag the next rc, approve the prod deploy, verify on jordylab.be) and tell me the result. Do not run `goldenLive` or any paid AI call, never print secrets, and never remove the `jordylab-be_pgdata` volume. Report what you did and what is left for me.

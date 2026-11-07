# Implementation Plan: Self-Hosted Kaneo as the Backlog

**Branch**: `022-kaneo-backlog` | **Date**: 2026-11-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/022-kaneo-backlog/spec.md`

**Note**: Produced by `/speckit-plan`. Decisions are in [research.md](research.md) (R1-R18). The spec had no open
clarifications; the points the documentation leaves open are settled by the local trial (User Story 1), not guessed here.

## Summary

Kaneo (MIT, one container) joins the production Compose stack as `kaneo.jordylab.be`, using a second database in the
existing PostgreSQL so the existing backups cover it. The Linear backlog is imported **once** with its numbers kept
(the importer makes the `JOR` project itself), a small fix-up script restores the Triage, Drafted and Specified columns
that the importer flattens, and App/Type stay labels. The idea box gets a `KaneoClient` beside the `LinearClient`, chosen
by one operator-set GitHub repository variable (not a code literal or SOPS key), so cutover and rollback need no code change; old
stored links are relinked to the Kaneo tickets before Linear is cancelled. Agents move to Kaneo's remote MCP; the Linear skills are renamed
and rewritten; the commit/PR rules are rewritten from what a scratch-repository trial shows. A follow-up pull request at
least 30 days later removes the Linear client and key.

Technical approach, in one line each:
- **Proof first**: a labelled throwaway trial settles six undocumented points and ends in go/no-go (R17, quickstart 1-3).
- **Hosting**: a pinned `kaneo` service, 768 MB, no host port, second database via an idempotent script, Caddy site, a
  best-effort deploy step that can never roll back the application (R6-R9).
- **Data**: import once, then `scripts/kaneo/import-fixup.py` (dry run first, paced to the rate limit) (R2, R3).
- **Classification**: App and Type as labels with the Linear names (R4).
- **Idea box**: `BacklogClient` interface, `KaneoClient` and `LinearClient`, `BACKLOG_PROVIDER`; one Flyway rename (R11, R12, R16).
- **Agents**: remote MCP, renamed skills, a per-run write budget instead of the plan-size stop (R14, R15).
- **Rules and docs**: rewritten from trial results; `docs/kaneo.md` and a runbook section (R13).

## Technical Context

**Language/Version**: Java 25 (backend); Bash (deploy scripts, trial harness); Python 3.12 (fix-up script, tests); YAML
(Compose, Caddy, workflows); SQL (one Flyway migration); Markdown (skills, docs).

**Primary Dependencies**: Kaneo (pinned 2.x, MIT) and its image; Spring Boot 4.1.1 `RestClient` (existing, no new library);
Jackson (existing); WireMock (existing test dependency); `pytest` for the Python tool; the Kaneo MCP server and API. No new
backend dependency.

**Storage**: PostgreSQL 16 (existing server): new database `kaneo` and role `kaneo`; the application's `feedback` schema
gets a column rename. Object Storage (existing bucket, new prefix) for the one-off Linear export.

**Testing**: JUnit 5, AssertJ (`assertSoftly`), Mockito without `any()`, WireMock for the client, MockMvc with
`@Language("JSON")`, TestBuilders, `ModularityTests`, Playwright journey (existing feedback journey updated); shell fixture
tests (existing) and `pytest` for the fix-up; Compose `config -q`; the live checks in `quickstart.md`.

**Target Platform**: single OVH VPS-2 (4 vCPU, 7.7 GB, no swap) behind Caddy; checked 2026-11-07: about 1 GB used by
containers, 6 GB available, 32 GB disk free.

**Project Type**: infrastructure and tooling change plus a small backend change (the `feedback` module). No frontend
change beyond an e2e test text (the web app does not read the renamed field).

**Performance Goals**: an accepted idea reaches the board within a few seconds (SC-003); agents' board operations finish
in under a minute (SC-002). Nothing user-visible on the application's pages changes.

**Constraints**:
- Application deploy and rollback must not depend on Kaneo (FR-008).
- Kaneo rate limit: 100 requests per 60 s per key; agents and scripts keep under 40 writes a minute.
- No host port for Kaneo; Caddy remains the only publisher. No secret in a repo file, log, ticket or chat.
- Production config changes reach production only with the owner's yes; secrets are edited in IntelliJ by the owner.
- Images build only on `main`, so Kaneo reaches production only after the merge (R16).
- Registration closed after the owner exists; checked after each relevant deploy.

**Scale/Scope**: one user, about 100 existing tickets, a few new tickets a day; 8 columns, 12 labels.

## Constitution Check

*Checked against `.specify/memory/constitution.md` v1.1.2, before research and again after Phase 1.*

| Principle | Applies to | Result |
|-----------|-----------|--------|
| I. Clean Code | `KaneoClient`, interface, script | Pass. Full names (`BacklogClient`, `BacklogUnavailableException`, `ensureLabels`); each method one job; early returns for the cached lookups as in `LinearClient`. |
| II. Fail Fast, No Silent Failures | client, deploy step, fix-up | Pass. Unconfigured or drifted board = `BacklogUnavailableException`, never an unlabelled ticket. The best-effort Kaneo deploy step is **not** silent: it prints a warning in the job summary. The fix-up stops on the first 401/403. |
| III. Immutable, Builder-First | records | Pass. `BacklogTicket` and `KaneoProperties` are records; no hand-written constructors in new Java. |
| IV. Testing Discipline | tests | Pass. WireMock + AssertJ `assertSoftly`, captors for request bodies (no `any()`), TestBuilders, descriptive names; pytest for the Python tool; no live call in unit tests. |
| V. Language & Tooling Currency | Java, Python | Pass. Java 25, Boot 4.1.1; the Python tool follows the 3.12 used by the sidecar. No `var`. |
| Architecture constraints | module boundaries | Pass. Everything stays inside the `feedback` module; `BacklogClient` is internal to it. No cross-module import added. |
| AI routing (AGENTS) | any model call | Not applicable. No model call. |
| Flyway (AGENTS) | schema | Pass. One dated migration targeting the `feedback` schema (rename only). The `kaneo` database is created outside Flyway by the deploy script, because Kaneo owns its own schema. |
| Secrets (AGENTS) | keys, logs | Pass. New names listed in the environment contract; values only in the encrypted file; the DB script passes the password on stdin; client never logs bodies. |
| Validation data (AGENTS) | live data | Pass. No hand-seeded rows in any dev database; the trial uses its own throwaway stack; the real board data comes from the real import. |
| Container hygiene (AGENTS) | local containers | Pass. Trial stack is labelled and self-cleaning with a `verify` step; the `jordylab-be-*` dev stack is never touched. |
| Commits/PRs (AGENTS) | commit rules | Pass. The hook and `JOR-n:` rule are kept; one deliberate follow-up pull request is declared in R16 for the owner's approval. |

Post-design re-check: still passing. The one addition that earns scrutiny is the second client behind an interface; it is
justified by the rollback requirement (FR-032) and the 30-day window, and it is deleted in the follow-up (see Complexity
Tracking).

## Project Structure

### Documentation (this feature)

```text
specs/022-kaneo-backlog/
├── plan.md              # this file
├── research.md          # Phase 0: decisions R1-R18
├── data-model.md        # Phase 1: feedback rename, config, board, mappings, cutover states
├── quickstart.md        # Phase 1: trial + production validation
├── contracts/
│   ├── kaneo-client.md      # backend <-> Kaneo REST
│   ├── deployment.md        # compose service, Caddy, database, secrets, deploy step, upgrade
│   ├── board-conventions.md # import runbook, fix-up contract, GitHub matrix, ticket text rules
│   └── agent-tooling.md     # MCP entries, tool mapping, skills, write budget
├── checklists/requirements.md
├── trial-results.md     # written by the trial (go/no-go) - created during implementation
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
deploy/compose/
├── compose.yaml                      # + kaneo service
├── Caddyfile                         # + kaneo.jordylab.be site
├── env/prod.env.example              # + KANEO_* and BACKLOG_PROVIDER names
└── scripts/
    ├── render-env.sh                 # + KANEO_* sources
    └── ensure-kaneo-database.sh      # new, idempotent, SQL on stdin
deploy/scripts/smoke-check.sh         # + optional Kaneo health invocation (warn only)
.github/workflows/
├── deploy-prod-compose.yml           # + best-effort Kaneo step after the app deploy
├── commit-refs.yml                   # message text only
└── hook-tests.yml                    # renamed skill paths; + pytest for scripts/kaneo

jordylab-be/src/main/
├── java/dev/jordy/jordylab/feedback/
│   ├── FeedbackProperties.java       # + kaneo, backlog.provider
│   ├── rest/client/{BacklogClient,KaneoClient,LinearClient}.java
│   ├── service/{AcceptTicketService,BacklogUnavailableException}.java   # (Linear exception renamed)
│   ├── domain/{FeedbackTicket,FeedbackApp,FeedbackType}.java            # backlog* names
│   └── rest/controller/{FeedbackExceptionHandler}.java + model/InboxTicketView.java
└── resources/{application.yaml, db/migration/V2026110x__feedback_rename_linear_columns.sql}
jordylab-be/src/test/java/.../feedback/   # KaneoClientTest (new), renamed/updated Linear and service tests
jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts   # message text only

scripts/kaneo/
├── import-fixup.py               # new, dry run default
├── relink-feedback.py            # new, prints reviewed UPDATE statements for old idea-box links
├── tests/                        # pytest + fake server
└── trial/run.sh                  # labelled, self-cleaning trial stack

.claude/skills/{backlog-capture,backlog-triage,backlog-sync-drafts}/   # renamed from linear-*
.claude/skills/{speckit-taskstoissues,agent-handover,refresh-agent-models}/   # updated
.mcp.json, opencode.json, AGENTS.md, CLAUDE.md, .github/**, .githooks/commit-msg   # text/config
docs/{kaneo.md (from linear.md), runbook.md}, specs/_drafts/README.md, jordylab-be/AGENTS.md
```

**Structure Decision**: tooling, deployment and the `feedback` module only; no new module, no new Gradle or Nx project.
New code lives next to what it replaces (`feedback/rest/client`, `deploy/compose/scripts`, `scripts/`), following the
existing layout.

## Delivery order (one pull request, staged by commits and by owner steps)

1. **Trial and go/no-go** (no production effect). Commit `trial-results.md` and the harness.
2. **Infrastructure and code, inert**: compose service, Caddy, scripts, secrets names, `BacklogClient` + `KaneoClient`,
   migration, skills and docs. `BACKLOG_PROVIDER=linear` is set as a repository variable before the merge, so merging
   changes nothing for the idea box. `KANEO_DISABLE_REGISTRATION` and `KANEO_MEM_LIMIT` are repository variables too, and
   `KANEO_API_KEY` is a production environment secret, so no later step needs another pull request. The secrets that exist
   before the merge (auth secret, database password, GitHub App values) are added to the SOPS file on the branch, and the
   DNS record and the GitHub App are created before the merge, so the merge's own deploy brings Kaneo up complete.
3. **Owner steps before the merge, then merge and deploy**: DNS record, GitHub App, the secrets on the branch, the repository
   variables; then Kaneo comes up empty with the merge's own deploy; owner account; closed registration; health and
   capacity recorded; restore drill.
4. **Freeze, import, fix-up, verify**; then the backend's API key is set as a production environment secret.
5. **Cutover**: relink the stored idea-box links, then the owner sets the repository variable `BACKLOG_PROVIDER=kaneo` and
   deploys; the tooling and rules (already merged) become the working ones; Linear GitHub integration and commit webhook
   off; nothing writes to Linear.
6. **Follow-up pull request (>= 30 days)**: remove `LinearClient`, its tests, the `linear` properties, `LINEAR_API_KEY`;
   cancel Linear; final search (User Story 7, SC-008).

Between merge (step 3) and cutover (step 5) the agent skills in the repository already name Kaneo while the board is empty;
the freeze covers that window, so nobody logs tickets then, and every piece of work in flight already has its key.

## Complexity Tracking

| Violation / addition | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Two backlog clients behind one interface until the follow-up pull request | rollback by a config flip within 30 days (FR-032) and no idea-box outage between merge and import | Replacing the client outright makes accept fail from the merge until the import ends, and makes rollback a code revert |
| A second pull request (the Linear removal) | decommission is defined as >= 30 days after cutover (FR-033). **Approved by the owner, 2026-11-07** | Holding the pull request open for 30 days blocks every other change on this branch |
| A fix-up script after the import | the importer drops Triage, Drafted and Specified into one Backlog | Doing ~60 moves by hand is slower and error-prone; losing the distinction breaks the board |

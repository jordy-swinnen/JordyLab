# Tasks: Self-Hosted Kaneo as the Backlog

**Input**: Design documents from `specs/022-kaneo-backlog/` (plan.md, spec.md, research.md, data-model.md, contracts/,
quickstart.md)

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: included. The constitution's Testing Discipline (IV), AGENTS.md ("run relevant tests after each change") and
the plan's Testing section make them part of this feature. Within a story, tests come before the implementation they cover.

**Organization**: grouped by user story (spec.md: US1 trial, US2 hosting, US3 import, US4 agents, US5 idea box, US6 commits
and pull requests, US7 Linear off, US8 docs). Marks: **OWNER** = only Jordy can do it (DNS, secrets in IntelliJ, GitHub App,
clicks in the Kaneo or Linear UI); **OPERATIONS** = touches the live host or live board, needs Jordy's yes first (see the
`jordylab-ops` rules: read first, show the exact command, wait).

**Ticket**: every commit starts with its Linear ticket (`JOR-<n>: ...`) and ends with `Refs: 022/T###`. T001 creates the
ticket key for this spec is **JOR-85** (the existing Linear ticket "Migrate Linear to self-hosted Kaneo (Docker)", chosen by
the owner on 2026-11-07 over creating a duplicate; see T001).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different files, no dependency on an unfinished task
- **[Story]**: US1 to US8; set only in user story phases
- Paths are relative to the repository root. The backend root is `jordylab-be/`.

## Delivery stages (from plan.md)

A. Trial and go/no-go (T004-T012). B. Build, inert, and the owner steps that must precede the merge (DNS T023, secrets and
repository variables T024, GitHub App T069). C. Merge; the merge's deploy brings Kaneo up empty. D. Freeze, import, fix-up,
verify. E. Cutover (the `BACKLOG_PROVIDER` repository variable, no code change) after the stored links are relinked.
F. Follow-up pull request at least 30 days later (T077-T079).
Tasks marked `[B]`, `[C]`, `[D]` or `[E]` belong to that stage; unmarked tasks belong to the stage of their phase.

---

## Phase 1: Setup

**Purpose**: ticket and scaffolding for the scripts.

- [X] T001 Use the existing Linear ticket **JOR-85** ("Migrate Linear to self-hosted Kaneo (Docker)", Platform + Chore, Backlog)
      for this spec. The duplicate search on 2026-11-07 found it; the owner chose it over creating a new ticket. Recorded in the
      Ticket line above. No new ticket was created and JOR-85 itself was not changed.
- [X] T002 [P] Create `scripts/kaneo/README.md` describing the folder (`trial/`, `import-fixup.py`, `tests/`), that nothing
      in it ever prints or stores a key, and how to run each part.
- [X] T003 [P] Create `scripts/kaneo/requirements-dev.txt` with `pytest` pinned (Python 3.12) for the fix-up tests.

---

## Phase 2: User Story 1 - Prove Kaneo can replace Linear (Priority: P1) - GATE

**Goal**: a recorded go/no-go from a throwaway local trial.

**Independent Test**: `specs/022-kaneo-backlog/trial-results.md` exists, answers every point in `research.md` "Open points"
and `quickstart.md` steps 1-3, ends in "go" or "no-go", and `run.sh verify` shows nothing of the trial left.

**Nothing after this phase starts until the result is "go".**

- [X] T004 [US1] Write the failing test first: `scripts/kaneo/trial/tests/verify-cases.sh` checks that after `run.sh up` then
      `run.sh down`, `run.sh verify` finds no container, network or volume with the trial label, and that `run.sh` refuses
      to act when run from a path outside `/Users` or `/private/tmp`. Written and seen to fail (17 failures) before
      `run.sh` existed; 22 cases pass now (fake Podman and curl, no container engine needed).
- [X] T005 [US1] Implement `scripts/kaneo/trial/run.sh` (`up | down | verify`) and `scripts/kaneo/trial/compose.yaml`: its own
      Compose project name and label, its own Postgres, Kaneo image pinned by tag and digest (the newest 2.x release,
      verified against `ghcr.io/usekaneo/kaneo` and recorded in `research.md` R1), the app bound to `127.0.0.1` on a free
      port, `DISABLE_REGISTRATION=true` from the start, modelled on `jordylab-fe/e2e/run.sh`. It never touches the
      `jordylab-be-*` dev stack and never writes a key to disk.
- [X] T006 [US1] OWNER: put a read-only Linear personal key in `LINEAR_API_KEY_READONLY` in the shell. Run `run.sh up`, create
      the owner account and record whether the first user could register with registration closed (research R8).
- [X] T007 [US1] Import from Linear in the trial UI (Settings -> Workspace -> Import). Record in `trial-results.md`: project key
      (exactly `JOR`?), numbers kept, number of the next new ticket, the importer's summary, everything that did not
      arrive, and what a second import does (on a second throwaway workspace).
- [X] T008 [US1] Exercise the client contract by hand against the trial with a throwaway script kept outside the repo: create a
      task in `triage` with `priority: no-priority`, attach two labels, attach the same label twice, search by title, call
      with a wrong key and read the error `code`, read the 429 headers if reachable. Also record: the endpoint that changes
      a task's column and how a task is moved into and out of the Backlog view; whether API keys can be scoped (least
      privilege); whether a safe unauthenticated check shows that registration is closed; the task page URL shape; the
      `/api/health` response; label-name uniqueness; whether the board filters by label; whether saved views exist.
- [ ] T009 [US1] Connect Claude Code (`/mcp`), OpenCode (`opencode mcp auth`) and a Claude app custom connector (via a tunnel) — partly done (2026-11-07): OpenCode connected and checked; Claude Code and the Claude app are verified after the merge against https://kaneo.jordylab.be (see trial-results.md, T009).
      to the trial's `/api/mcp`. Per tool record how it signed in and that list, create, update, comment and
      `get_task_by_ticket_id` work. If the Claude apps cannot authorise it, record "no-go".
- [ ] T010 [US1] On a scratch GitHub repository install a GitHub App for the trial and run cases A-G of the matrix in — moved after the merge (owner decision, 2026-11-07): run on production against a scratch repository, see trial-results.md.
      `contracts/board-conventions.md` (branch push, PR open, merge). Record the webhook path, whether commit messages
      link, the branch regex that accepts `jordyswinnen/jor-23-x` and `jor-23-x`, and which cases finish a ticket.
- [ ] T011 [US1] Write `specs/022-kaneo-backlog/trial-results.md` (observed answers, importer losses, go/no-go). Update the
      "trial" items in `contracts/kaneo-client.md` (URL template, attach behaviour), `contracts/board-conventions.md` (matrix,
      status-change endpoint, Backlog moves), `contracts/deployment.md` (health body, registration plan, registration probe,
      key scopes) and `research.md` "Open points" (all ten). Also record whether Linear can lock a Free-plan workspace.
      OWNER reviews and confirms "go" here. On "no-go" stop the feature and say so in `spec.md` Status.
- [X] T012 [US1] `run.sh down` then `run.sh verify` (nothing of the trial remains); OWNER revokes the trial's Linear key.

**Checkpoint**: go/no-go recorded; undocumented points settled.

---

## Phase 3: User Story 2 - Kaneo runs on the VPS, private, backed up (Priority: P1)

**Goal**: Kaneo reachable at `https://kaneo.jordylab.be`, registration closed, secrets handled like the others, covered by the
existing backups, and unable to break an application deploy.

**Independent Test**: after a deploy, the Kaneo health address answers 200 over HTTPS, another person cannot register, a
restore drill brings the `kaneo` database back, and the application services are unchanged.

### Tests and build [B]

- [X] T013 [P] [US2] Write `deploy/scripts/tests/ensure-kaneo-database-cases.sh` first: with a fake `docker` on `PATH`, assert
      that the script sends its SQL on stdin, that the password never appears in the arguments, that a second run does not
      fail, and that a missing variable stops it with a clear message.
- [X] T014 [P] [US2] Write `deploy/scripts/tests/smoke-check-kaneo-cases.sh` first: `smoke-check.sh --kaneo-url <url>` prints
      a warning and exits 0 when Kaneo (or, if the trial found a safe probe, the closed-registration probe) fails, and still
      exits 1 when an application check fails.
- [X] T015 [US2] Implement `deploy/compose/scripts/ensure-kaneo-database.sh` per `contracts/deployment.md` (role and database if
      missing, password reset, `REVOKE CONNECT ... FROM PUBLIC`, SQL on stdin, nothing printed).
- [X] T016 [US2] Extend `deploy/scripts/smoke-check.sh` with the optional Kaneo health check (and the closed-registration probe
      if T011 found one) that warns only (T014 passes). If no safe probe exists, T022 documents the manual check instead.
- [X] T017 [US2] Add the `kaneo` service to `deploy/compose/compose.yaml` exactly as in `contracts/deployment.md`: tag and digest TRUSTED_PROXIES is not set yet (open point in the runbook).
      taken from the registry (the same pin as the trial, recorded in `research.md` R1), `mem_limit: ${KANEO_MEM_LIMIT:-768m}`,
      log rotation, health check, `depends_on: postgres`, no `ports`, Caddy's `depends_on` unchanged. First read the Compose
      network range on the host with `docker network inspect` (OPERATIONS, read-only) for `TRUSTED_PROXIES`; add the `kaneo`
      volume only if the trial showed one is needed.
- [X] T018 [P] [US2] Add the site block `kaneo.jordylab.be -> kaneo:5173` to `deploy/compose/Caddyfile`; validate it with the
      Caddy image (`caddy validate`).
- [X] T019 [US2] Add the Kaneo names to `deploy/compose/env/prod.env.example` and their sources to
      `deploy/compose/scripts/render-env.sh`: SOPS secrets `KANEO_AUTH_SECRET`, `KANEO_DB_PASSWORD` and the three GitHub App
      values; literals `KANEO_DB_NAME`, `KANEO_DB_USER` and the backend-side names; and values read from the environment the
      workflow provides, with defaults when unset: the repository variables `KANEO_DISABLE_REGISTRATION` (default `true`),
      `KANEO_MEM_LIMIT` (default `768m`), `BACKLOG_PROVIDER`, and the production environment secret `KANEO_API_KEY` (may be
      empty). Add the same rows to `specs/020-compose-deployment/contracts/env-contract.md`. Extend the render script's tests if
      any exist; it must never print a value.
- [X] T020 [US2] In `.github/workflows/deploy-prod-compose.yml` (1) pass `vars.BACKLOG_PROVIDER`, `vars.KANEO_DISABLE_REGISTRATION`,
      `vars.KANEO_MEM_LIMIT` and `secrets.KANEO_API_KEY` to the `render-env.sh` step as environment variables, and (2) add the
      best-effort Kaneo step after the application deploy and smoke check: `ensure-kaneo-database.sh`, `docker compose up -d
      kaneo`, health wait, Kaneo smoke check; every failure is a `::warning::` plus a job-summary line and never changes the job
      result or touches the release state files.
- [ ] T021 [US2] Validate locally: `docker compose --env-file deploy/compose/env/prod.env.example -f deploy/compose/compose.yaml — partly done (2026-11-07): every related suite passes and the compose file and Caddyfile validate; shellcheck is not installed here, so it was not run.
      config -q`, `shellcheck` on the new scripts, `bash deploy/scripts/tests/*-cases.sh`, `bash scripts/mcp/tests/*` unaffected.
- [X] T022 [US2] Add a "Kaneo" section to `docs/runbook.md` (and its Contents): deploy, first-user bootstrap per the trial, closing
      registration with `gh variable set KANEO_DISABLE_REGISTRATION --body true`, the manual closed-registration check after
      every deploy that touches Kaneo's configuration (if there is no automatic probe), upgrade procedure
      (`contracts/deployment.md`), restore drill, rotating `KANEO_AUTH_SECRET` and the API key, resizing with
      `KANEO_MEM_LIMIT`, the capacity record, and the "Kaneo down never blocks a deploy" rule.

### Operate [C] (after the merge; OWNER and OPERATIONS)

- [ ] T023 [US2] OWNER [B] before the merge: create the DNS A record `kaneo.jordylab.be` -> the VPS IPv4 at OVH (so the merge's deploy
      can issue the certificate at once).
- [ ] T024 [US2] OWNER [B] before the merge: add `KANEO_AUTH_SECRET` (`openssl rand -hex 32`) and `KANEO_DB_PASSWORD` (generated
      without `/`, `+`, `=`) to the SOPS file on the branch with `SOPS_EDITOR="idea --wait" sops
      deploy/k8s/overlays/prod/secrets.sops.yaml`; the agent never opens the file. Set the repository variables:
      `gh variable set BACKLOG_PROVIDER --body linear` (so merging does not move the idea box) and, per the trial result,
      `gh variable set KANEO_DISABLE_REGISTRATION --body true` (or `false` for the bootstrap window).
- [ ] T025 [US2] OPERATIONS [C] (Jordy's yes): merge, then the merge's deploy (or `Deploy to Production (Compose)` by hand). Check `kaneo`
      healthy, `https://kaneo.jordylab.be/api/health` 200 with a valid certificate, the other five services unchanged, the
      application smoke checks green.
- [ ] T026 [US2] OWNER [C]: create the owner account following the trial's finding (T011); if registration had to be open, run
      `gh variable set KANEO_DISABLE_REGISTRATION --body true`, redeploy, and check the instance status shows the user exists.
- [ ] T027 [US2] OPERATIONS: lock-down check (SC-005): the sign-up page, the register call and workspace creation are refused and an
      unauthenticated request lists no tickets. Record in `docs/runbook.md`.
- [ ] T028 [US2] OPERATIONS: read-only capacity record now and one week later (`free -m`, `docker stats --no-stream`, `df -h /`) plus a
      before/after `curl` timing of `https://jordylab.be/` (SC-007); confirm at least 25% headroom (FR-010); write them in
      `docs/runbook.md`; resize with `gh variable set KANEO_MEM_LIMIT` and a redeploy if needed (no pull request).
- [ ] T029 [US2] OPERATIONS: restore drill (SC-006): `pgbackrest --stanza=jordylab --type=diff backup`, follow `docs/runbook.md` §15 into
      a throwaway volume, confirm the `kaneo` database and its tickets; record the date and result.

**Checkpoint**: Kaneo live, empty, locked down, backed up.

---

## Phase 4: User Story 3 - The backlog is in Kaneo with the same numbers (Priority: P1)

**Goal**: every Linear ticket in Kaneo under its `JOR-n`, in the right column, with its labels, comments and relations.

**Independent Test**: counts per state equal Linear's, every key exists, ten spot checks pass, a Linear export is stored off
the VPS (`import-report.md`).

- [ ] T030 [P] [US3] Write `scripts/kaneo/tests/test_import_fixup.py` first, with a fake Kaneo and fake Linear HTTP server: state
      -> column mapping (Triage, Drafted, Specified; Duplicate -> Canceled), moves into and out of Backlog, the `Spec:` first
      line written once for spec tickets (and not duplicated on a second run), dry run writes nothing, a run is idempotent,
      pacing stays at or below 40 writes a minute (inject a fake clock), a 429 waits `Retry-After`, a 401 stops, tickets
      lacking an App or Type label are listed.
- [ ] T031 [US3] Implement `scripts/kaneo/import-fixup.py` per `contracts/board-conventions.md` "Fix-up script contract" (reads Linear's JSON export, not a Linear key: decided 2026-11-07)
      (`--dry-run` default, keys only from the environment, nothing printed but counts and ticket keys; spec link mapping
      from `repo_inventory.sh`); T030 passes.
- [ ] T032 [US3] Add `scripts/kaneo/tests` to `.github/workflows/hook-tests.yml` (path trigger and a `python -I -m pytest` step).
- [ ] T033 [US3] OWNER [D]: announce the freeze (Claude project instructions and the Linear welcome note), make sure every piece
      of work in flight already has its `JOR-n`, the fix-up reads the Linear export from T034, so it needs no Linear key (decided 2026-11-07).
- [ ] T034 [US3] OPERATIONS [D]: take a full Linear export (JSON) and store it in the Object Storage bucket under a separate prefix;
      verify it opens and its issue count equals Linear's (FR-018).
- [ ] T035 [US3] OWNER [D]: run the Linear import once in production Kaneo (Settings -> Workspace -> Import). Do not pre-create a
      project. Save the importer's summary into `specs/022-kaneo-backlog/import-report.md`.
- [ ] T036 [US3] OPERATIONS [D]: `import-fixup.py --dry-run` (the owner's short-lived Kaneo key in the shell, revoked afterwards),
      review the table with the owner, run it for real, append the output counts to `import-report.md`.
- [ ] T037 [US3] OPERATIONS [D]: run the verification checklist in `contracts/board-conventions.md` (counts per state, every key and the
      highest number, a new test ticket's number, ten spot checks, tickets missing an App or Type label fixed); write the results
      and every difference into `import-report.md`.
- [ ] T038 [US3] OWNER [D]: create the four saved views (or the filter recipes if Kaneo has no saved views); record in
      `import-report.md`.

**Checkpoint**: board verified; Linear still intact and frozen.

---

## Phase 5: User Story 5 - Accepted ideas become Kaneo tickets (Priority: P1)

(Placed before US4 because US4's cutover check and US7 depend on it; it is built before the merge and switched on at cutover.)

**Goal**: accept creates exactly one Triage ticket in Kaneo, retry-safe, with `LinearClient` kept behind the same interface.

**Independent Test**: unit and controller tests pass; a live check creates one throwaway ticket on the real board; accept with
no key answers "backlog unavailable".

### Tests and build [B]

- [X] T039 [US5] Rename `LinearUnavailableException` to `BacklogUnavailableException` (all uses, including
      `FeedbackExceptionHandler`), change the response code to `BACKLOG_UNAVAILABLE` and the message to "The backlog is unreachable
      right now - the ticket stays as it was, so you can retry."; update `FeedbackExceptionHandler` tests and
      `FeedbackTicketControllerTest`/`FeedbackInboxControllerTest` first, then the code.
- [X] T040 [US5] Extract `jordylab-be/src/main/java/dev/jordy/jordylab/feedback/rest/client/BacklogClient.java` (interface,
      `BacklogTicket(String id, String url)` record) and make `LinearClient` implement it (its inner `LinearIssue` becomes
      `BacklogTicket`); `AcceptTicketService` depends on the interface; update `AcceptTicketServiceTest` and `LinearClientTest`.
- [X] T041 [US5] Extend `FeedbackProperties` with the `kaneo` record and `backlog.provider` per `data-model.md` section 2, defaults
      as listed, and `application.yaml` (env names `BACKLOG_PROVIDER`, `KANEO_*`); add a properties test for defaults and
      blank handling.
- [X] T042 [US5] Select the client with `@ConditionalOnProperty("jordylab.feedback.backlog.provider")` (`kaneo` matches by default,
      `linear` selects the old one); test both selections and that exactly one `BacklogClient` bean exists (in
      `FeedbackModuleTest` or a slice test).
- [X] T043 [US5] Write `KaneoClientTest` first under `jordylab-be/src/test/java/dev/jordy/jordylab/feedback/rest/client/` with WireMock
      and request-body assertions (`@Language("JSON")`, no `any()`): create + two label attaches, missing key, 401/403,
      429, 5xx, timeout, missing project / Triage column / Type label, App fallback to Platform, search finds one / none,
      adopt path completes labels, response bodies never logged. Reuse the existing `FeedbackTicketTestBuilder`
      (`@UtilityClass`, minimal object + pre-filled builder); add a response fixture only if it adds something.
- [X] T044 [US5] Implement `KaneoClient` in `feedback/rest/client/KaneoClient.java` per `contracts/kaneo-client.md` (records for
      responses, `RestClient` with timeouts, `volatile` caches, description template, label mapping, one
      `BacklogUnavailableException` for every failure); T043 passes.
- [X] T045 [US5] Add Flyway migration `jordylab-be/src/main/resources/db/migration/V<next date>__feedback_rename_linear_columns.sql`
      (schema `feedback`, rename `linear_issue_id` -> `backlog_ticket_id`, `linear_issue_url` -> `backlog_ticket_url`; check
      the highest existing version first and use the `flyway-migration` skill), and rename the Java side:
      `FeedbackTicket`, `FeedbackApp`/`FeedbackType` (`backlogLabelName`), `InboxTicketView.backlogTicketUrl`, the repository
      comments; update `FeedbackTicketTest`, `FeedbackTicketTestBuilder` and the repository test (Testcontainers) so the rename
      is exercised.
- [X] T046 [US5] Update the e2e journey text in `jordylab-fe/apps/jordylab-e2e/src/feedback.spec.ts` for the new message and the
      comment about the missing key (`KANEO_API_KEY`/provider), and confirm the web app has no reference to the renamed
      field (`grep -ri linearIssue jordylab-fe` empty).
- [ ] T047 [US5] Add the backend-side names to the `backend` service `environment:` block in `deploy/compose/compose.yaml`
      (`BACKLOG_PROVIDER`, `KANEO_BASE_URL`, `KANEO_API_KEY`, `KANEO_PROJECT_KEY`, `KANEO_TICKET_URL_TEMPLATE`); the values
      arrive through `prod.env` (T019/T020), with `BACKLOG_PROVIDER` coming from the repository variable (`linear` until
      cutover); keep `LINEAR_API_KEY`. (Same files as T017/T019; do after them.)
- [X] T048 [US5] Update `jordylab-be/AGENTS.md` (feedback module: the backlog is Kaneo, the provider switch, the error code) and the
      Linear mentions in `specs/017-guest-feedback/contracts/linear-client.md` header (a note that spec 022 replaces it).
- [X] T049 [US5] Run `./gradlew test --tests '*feedback*'` and `./gradlew test --tests '*ModularityTests*'` in `jordylab-be`; fix
      until green.
- [ ] T050 [US5] Write `scripts/kaneo/tests/test_relink_feedback.py` first (fake Kaneo server): the `JOR-n` is taken from a Linear issue
      URL, resolved with the by-ticket-id call, and an `UPDATE` statement is printed (never executed); unresolved keys are listed;
      already-relinked rows are skipped; a 401 stops; nothing but ids and URLs is read or printed. [B]
- [ ] T051 [US5] Implement `scripts/kaneo/relink-feedback.py` (input: rows of id and Linear URL from a read-only `select` on stdin;
      output: reviewed `UPDATE feedback.feedback_ticket SET backlog_ticket_id=..., backlog_ticket_url=...` statements per
      `contracts/board-conventions.md` step 6, spec FR-027); the previous task's test passes. [B]

### Operate [C]/[D]

- [ ] T052 [US5] OWNER [D]: create a Kaneo API key (Settings -> Account -> API Keys), with the narrowest scope the trial found
      (tasks and labels only, if scoping exists), and store it with `gh secret set KANEO_API_KEY --env production` (value
      piped, never printed); redeploy (the provider variable is still `linear`).
- [ ] T053 [US5] OPERATIONS [D]: live check against the real instance (FR-028): run the backend locally with `BACKLOG_PROVIDER=kaneo`
      and the production base URL and key from the shell, create one throwaway ticket through `KaneoClient`, confirm it is in
      Triage with the two labels and the template, adopt-search finds it, then delete the ticket by hand. Record the result and
      any shape surprises in `import-report.md` and fix the client if needed.
- [ ] T054 [US5] OPERATIONS [E] (Jordy's yes): run the read-only `select id, backlog_ticket_url from feedback.feedback_ticket where
      backlog_ticket_id is not null` on the production database, pipe it to `relink-feedback.py`, review the statements with the
      owner, run them once, and confirm every stored link now opens the Kaneo ticket with the same number (spec FR-027).

**Checkpoint**: idea box ready, off until cutover.

---

## Phase 6: User Story 4 - Agents capture, triage and sync in Kaneo (Priority: P1)

**Goal**: the same daily workflow from Claude Code, OpenCode and the Claude apps, on Kaneo, with the same safeguards.

**Independent Test**: `quickstart.md` step 9 recorded in `specs/022-kaneo-backlog/agent-validation.md`.

### Build [B]

- [ ] T055 [P] [US4] Replace the `linear` entry with `kaneo` in `.mcp.json` (`type: http`, URL `https://kaneo.jordylab.be/api/mcp`, no
      secret); keep `github`, `nx-mcp`, `jordylab`.
- [ ] T056 [P] [US4] Replace the `linear` entry with `kaneo` in `opencode.json` (remote, same URL, OAuth settings as the trial found).
- [ ] T057 [US4] `git mv .claude/skills/linear-capture .claude/skills/backlog-capture` and rewrite its `SKILL.md` for Kaneo using the
      tool mapping in `contracts/agent-tooling.md` (Triage column, `search` duplicate check, two label attaches, exactly-one
      App/Type check, preview and approval, `Source:` line, outage message); keep the injection rule.
- [ ] T058 [US4] `git mv .claude/skills/linear-triage .claude/skills/backlog-triage` and rewrite for Kaneo (list the Triage column,
      one proposal table, apply only confirmed rows by moving to Backlog, injected text reported not obeyed).
- [ ] T059 [US4] `git mv .claude/skills/linear-sync-drafts .claude/skills/backlog-sync-drafts` (scripts, `scripts/tests`,
      `app-map.tsv` move with it), fix the paths inside `repo_inventory.sh`, `open_bugs.sh` and the two fixture test scripts,
      and rewrite `SKILL.md` for Kaneo with the write budget (40 writes a minute, 150 per run) instead of the 230-issue stop.
      Run both fixture scripts green.
- [ ] T060 [P] [US4] Rewrite `.claude/skills/speckit-taskstoissues/SKILL.md` for Kaneo (one ticket per user story and per phase without a
      story, a `Phase:` line instead of milestones, relations for ordering, spec link on the first line, preview and approval).
- [ ] T061 [P] [US4] Update the Linear wording in `.claude/skills/agent-handover/SKILL.md` (section 5b: comment on the Kaneo ticket with
      `create_task_comment`) and in `.opencode/commands/agent-handover.md`.
- [ ] T062 [P] [US4] Update `.claude/skills/refresh-agent-models/SKILL.md` (steps 1-2 name the Kaneo ticket and `backlog-capture`).
- [ ] T063 [US4] Update the renamed skill paths in `.github/workflows/hook-tests.yml` (after T032) and in any other reference found
      by `grep -rIl "linear-sync-drafts\|linear-capture\|linear-triage"` (CLAUDE.md skill list is done in T075).

### Operate [D]/[E]

- [ ] T064 [US4] OWNER [D]: add the Kaneo custom connector (`https://kaneo.jordylab.be/api/mcp`) to the Claude apps, paste the conventions
      block from `docs/kaneo.md` (T080) into the Claude project instructions, remove the Linear connector only at cutover
      (T073); record the date in `docs/kaneo.md` handoffs log.
- [ ] T065 [US4] OPERATIONS: validate per `quickstart.md` step 9 from Claude Code, OpenCode and a Claude app (at least three
      attempts per tool; the app's pasted conventions block comes from `docs/kaneo.md`, T080): log an idea, a three-ticket
      table with partial approval, triage with a planted "ignore your rules" ticket, the draft sync twice (second run no
      change), and the signed-out outage message. Record in `specs/022-kaneo-backlog/agent-validation.md`; delete the throwaway
      tickets.

**Checkpoint**: agents work on Kaneo.

---

## Phase 7: User Story 6 - Commits and pull requests keep tracking tickets (Priority: P2)

**Goal**: pull requests link and move tickets through the columns, and the written rules say exactly what finishes a ticket.

**Independent Test**: `quickstart.md` step 10 with the production App and a throwaway ticket.

- [ ] T066 [US6] Rewrite `.github/instructions/commits-and-pull-requests.instructions.md` from the trial's matrix (T010/T011): which
      branch names, title patterns and words link, which finish a ticket, and the commit-linking statement; keep the
      `JOR-n:` subject rule and the `Refs:` table unchanged. [B]
- [ ] T067 [P] [US6] Update `.github/pull_request_template.md` and `.github/copilot-instructions.md` to match T066. [B]
- [ ] T068 [P] [US6] Update message text only in `.githooks/commit-msg` and `.github/workflows/commit-refs.yml` (ticket creation points to
      `backlog-capture`, no Linear wording); run the hook's own tests and `bash .githooks` cases if present. [B]
- [ ] T069 [US6] OWNER [B] before the merge: create the GitHub App for Kaneo with the permissions, name and webhook URL
      (`https://kaneo.jordylab.be` + the trial's path) the trial recorded, and add `KANEO_GITHUB_APP_ID`,
      `KANEO_GITHUB_WEBHOOK_SECRET`, `KANEO_GITHUB_PRIVATE_KEY_BASE64` to the SOPS file on the branch with the IntelliJ `sops`
      command; set `GITHUB_APP_NAME` in `compose.yaml` (T017). After the merge's deploy [C], install the App on
      `jordy-swinnen/JordyLab`.
- [ ] T070 [US6] OWNER [D]: in Kaneo connect the repository to project `JOR`, set the branch regex from the trial, map push ->
      In Progress, PR opened -> In Review, merged -> Done.
- [ ] T071 [US6] OPERATIONS [D]: on a throwaway ticket and two throwaway pull requests (one `Part of`, one finishing word) run
      the matrix cases the written rules rely on; confirm the ticket moves and the open/finish behaviour matches T066; a
      subject without a key is refused by the hook. Record in `import-report.md`; fix T066 if reality differs.

---

## Phase 8: User Story 7 - Linear is switched off cleanly (Priority: P2)

**Goal**: cutover by a config flip, Linear read-only for 30 days, then removal.

**Independent Test**: after the follow-up, `grep -ri linear` over code, config, workflows and secret names finds only history.

- [ ] T072 [US7] OPERATIONS [E] (Jordy's yes): relink first (T054), then `gh variable set
      BACKLOG_PROVIDER --body kaneo` and redeploy (no code change), and run `quickstart.md` step 8: ten accepted ideas (exactly
      one Triage ticket each), a forced failure and retry (no duplicate), blank key (503 "backlog unavailable", guest ticket
      stays open). Roll back with `gh variable set BACKLOG_PROVIDER --body linear` and a redeploy if anything fails.
- [ ] T073 [US7] OWNER [E]: switch off the Linear GitHub integration and commit webhook, disconnect every Linear connector except
      the owner's own browser login, lock the workspace read-only if Linear's plan allows it (T011 recorded whether), otherwise
      nothing writes to it by agreement; announce the end of the freeze and note the 30-day date in `docs/kaneo.md`.
- [ ] T074 [US7] [E] Record the cutover (date, result of T072, rollback not needed) in `specs/022-kaneo-backlog/import-report.md` and in
      `spec.md` Status.
- [ ] T075 [US7] Update `AGENTS.md` (Backlog and Commits sections, repository references to Linear) and `CLAUDE.md` (skill list with the
      renamed skills, MCP list) and `specs/_drafts/README.md` (tracking in Kaneo, `backlog-sync-drafts`). [B]
- [ ] T076 [US7] [E] At the 30-day mark, with no rollback needed: OWNER confirms; the Linear export from T034 is re-checked as stored.

### Follow-up pull request [F] (at least 30 days after cutover; the one approved exception to one PR per spec)

- [ ] T077 [US7] Delete `LinearClient` and `LinearClientTest`, the `jordylab.feedback.linear.*` properties, `jordylab.feedback.backlog.provider`
      and the `@ConditionalOnProperty` selection (keep `BacklogClient` only if a second implementation is still wanted,
      otherwise inline), `LINEAR_API_KEY`/`BACKLOG_PROVIDER` from `deploy/compose/compose.yaml`, `env/prod.env.example`,
      `render-env.sh`, the workflow's pass-through and `specs/020-compose-deployment/contracts/env-contract.md`; OWNER removes
      `LINEAR_API_KEY` from the secrets file with the IntelliJ `sops` command and runs `gh variable delete BACKLOG_PROVIDER`;
      tests green; deploy.
- [ ] T078 [US7] OWNER: cancel the Linear workspace, revoke the personal API key, remove the Linear connector from the Claude apps and
      the Linear block from the Claude project instructions.
- [ ] T079 [US7] Final search (SC-008): `grep -rIi linear` over the repository excluding `specs/015-*`, `specs/017-*`, `specs/022-*`, `docs/testing/` history
      and `.git`; list any hit that is not history and fix it; record the result.

---

## Phase 9: User Story 8 - Operate and rebuild from the docs (Priority: P3)

- [ ] T080 [US8] `git mv docs/linear.md docs/kaneo.md` and rewrite: workspace facts, terms (a ticket is what Kaneo calls a task; the
      Triage inbox is the Triage column), columns, labels, which steps are manual and which an agent does, connector sign-in for
      each tool, GitHub App setup, saved views or filter recipes, secret and variable rotation, upgrade, backup and restore
      pointers, owner handoffs log (H-01 ...), the Claude apps' conventions block (`contracts/board-conventions.md` "Ticket
      text conventions"), the Linear history pointer (`specs/015-linear-backlog/`), and the rebuild checklist. [B]
- [ ] T081 [P] [US8] Update any other doc that names Linear: `docs/learn/` and `docs/runbooks/` if `grep -il linear` finds one; add a
      Kaneo row to the Reference Docs table in `AGENTS.md` only if useful.
- [ ] T082 [US8] Rebuild test (SC-010): a reviewer follows the rebuild checklist on a clean `scripts/kaneo/trial/run.sh up` and ends with
      columns, labels and one ticket in under two hours; record time and gaps, fix the doc.

---

## Phase 10: Polish and cross-cutting

- [ ] T083 Run every affected gate: `./gradlew test --tests '*feedback*'`, `*ModularityTests*`, the shell fixture suites,
      `python -I -m pytest scripts/kaneo/tests`, `docker compose ... config -q`, `gitleaks git --no-banner --redact .`; all green.
- [ ] T084 Review the whole diff with the `code-reviewer` agent against the JordyLab conventions; fix findings by severity.
- [ ] T085 Mark the Kaneo draft as history (a one-line note at the top of `specs/_drafts/kaneo-backlog/plan-draft.md` pointing to spec 022) and
      leave `specs/_drafts/langfuse-self-hosting/` untouched.
- [ ] T086 Update `tasks.md` counts, `spec.md` Status, and open the pull request only when the owner asks, using
      `.github/pull_request_template.md` (`Refs JOR-<n>` until the work is finished, `Part of` for the staged parts).

---

## Dependencies and order

- Phase 1 -> Phase 2 (gate) -> everything else. A "no-go" in T011 stops the feature.
- Build-before-merge tasks (marked `[B]` or in a "Tests and build" block): T013-T022, T039-T051, T055-T063, T066-T068, T075,
  T080. They can run in parallel by story once Phase 2 says "go"; they touch different files except where noted
  (`compose.yaml`: T017 then T047; `render-env.sh`: T019 then T047; `deploy-prod-compose.yml`: T020; `hook-tests.yml`: T032
  then T063).
- OWNER steps before the merge (after "go"): T023 (DNS), T024 (secrets on the branch and the repository variables), T069
  (GitHub App and its secrets; needs the webhook path from T011 and `GITHUB_APP_NAME` in T017). The merge's deploy then
  brings Kaneo up complete.
- Merge, then Stage C: T025 -> T026 -> T027; T028 and T029 follow T025; the App is installed after T025 (T069, second half).
- Stage D (import) needs Stage C: T033 -> T034 -> T035 -> T036 -> T037 -> T038. T052 and T053 need T037; T070 and T071 need
  T025 and T037; T064 needs T080 (docs); T065 needs T037 and T064.
- Stage E: T054 (relink) needs T037, T051, T053; T072 (cutover) needs T054, T053, T065 and T071; T073-T074 follow T072.
- Stage F (T077-T079) needs T076 and at least 30 days.
- Post-deploy MCP check (no task number; decided 2026-11-07): after T025, the owner signs in to the production Kaneo MCP
  (`https://kaneo.jordylab.be/api/mcp`) in Claude Code through `/mcp`, and in the Claude app as a custom connector. Claude
  Code refuses sign-in over plain HTTP, so the trial could not test it (trial-results.md, T009). The agent then runs a
  read-only lookup (JOR-85) through each. Remove the trial's local Claude Code entry at teardown.
- US8's T082 needs a working trial script (T005) and the finished docs (T080).

## Parallel opportunities

- After "go": T013 and T014 (tests) in parallel; then T015, T016, T017 and T018 are mostly independent files.
- T030 and T050 (fix-up and relink tests) can start the same day as the hosting build, on a fake server.
- US5 (T039-T051, backend and relink script) and US4 (T055-T063, skills) are independent of each other and of hosting.
- T055, T056, T060, T061, T062, T067 and T068 are all different files.

## Implementation strategy

- **MVP**: Phase 1-2 (trial, go/no-go) then Phase 3 (Kaneo live and safe). That alone delivers a running, backed-up, private
  Kaneo without touching how tickets are made today.
- **Increment 2**: Phase 4 + 5 (import and the idea-box client, still switched off). The board is complete and verified.
- **Increment 3**: Phase 6 + 7, then Phase 8 cutover (T054, T072-T076): the daily workflow moves.
- **Closing**: Stage F and Phase 9-10 after 30 days.
- Stop and ask the owner at every OWNER/OPERATIONS task; never print or read secret values; a failing Kaneo never blocks an
  application deploy; production switches are repository variables and environment secrets, never code changes.

## Counts

86 tasks: setup 3, US1 9, US2 17, US3 9, US5 16, US4 11, US6 6, US7 8, US8 3, polish 4.

# Research: Self-Hosted Kaneo as the Backlog

Date: 2026-11-07. Sources: Kaneo docs (kaneo.app/docs: installation, environment variables, API reference, MCP, Linear
migration, GitHub integration, upgrades), the repository on `main`, and a read-only look at the live VPS on 2026-11-07
(about 1 GB of containers in use, 6 GB available, no swap). Where the docs say nothing, this file says **not documented**
and the local trial (`quickstart.md`) settles it. Nothing here was run against Kaneo itself.

## R1. Version and image

**Decision**: pin the newest 2.x release that the trial passes, by tag **and** digest. At the time of writing the latest
GitHub release is v2.49.0 (2026-11-02); the docs' Compose example still shows 2.28.3, so the docs lag the releases. Verify
the tag and digest against the registry (`ghcr.io/usekaneo/kaneo`) when implementing, as the ops rules require.

**Pinned for the trial (checked 2026-11-07 against the registries):** Kaneo `2.49.0`, digest
`sha256:cd1826b1aba3ce736015cadf6f6c975d923aac00f4b6aaa7aedb29bc722dae49` (the tag `v2.49.0` does not exist on GHCR; the
bare version does). Postgres `16-alpine`, digest `sha256:aa6cb33cf4c19c765cefcf48d96176258800870a9a1e930ad126200f9d825139`
(Docker Hub). Both are in `scripts/kaneo/trial/compose.yaml`; the production pin is chosen again at the production step.
**Rationale**: weekly releases, one security release in October (v2.47.1). A floating tag would upgrade by surprise.
**Alternatives**: the `2` major tag (follows the line, but then "no automatic upgrade" is not true); building from source
(more to maintain).

## R2. The importer creates its own project and cannot be re-run

**Facts**: the Linear importer (Settings -> Workspace -> Import) takes a read-only Linear personal key, creates **new**
projects (never merges into an existing one), uses the Linear team key as the project key (`JOR`, with a suffix like
`JOR-2` if taken), keeps issue numbers, and "running the same import twice gives you two copies".
**Decision**: do **not** pre-create the `JOR` project. Import once into a fresh workspace that has no `JOR` project; close
the gap for tickets created during the freeze by hand. The trial confirms that the project key comes out as exactly `JOR`
and that the next new ticket continues after the highest number (`lastTaskNumber`; setting it over REST is not
documented).
**Consequence for the spec**: the "catch-up import" in the edge cases is ruled out; the freeze must be short.

## R3. The importer flattens Triage and Backlog states: a fix-up step is needed

**Facts**: workflow states become columns in Linear's order; Completed, Canceled and Duplicate become final columns;
issues in **Backlog** and **Triage**-type states go to the project's Backlog (a virtual view), not to a column. In our
workspace, *Drafted* and *Specified* are Backlog-type states, and *Triage* is the Triage state, so they would all land in
one undifferentiated Backlog and the draft/specified distinction would be lost.
**Decision**: after the import, a small script (`scripts/kaneo/import-fixup.py`, dry run by default) reads each issue's
original Linear state from Linear's JSON export (decided 2026-11-07; no Linear key), and, through Kaneo's REST API, creates the columns Triage, Drafted and
Specified (and Todo/In Progress/In Review if the importer did not), moves each ticket to the column named after its
Linear state, and writes the `Spec: specs/<nnn-name>/` first line into the description of each spec ticket (the mapping from
the repository inventory, `backlog-sync-drafts`' `repo_inventory.sh`, FR-016). Rate limit: 100 requests/minute per key, so the script paces itself (about 40 writes a minute). About 100
tickets means a few minutes.
**Alternatives**: keep Backlog-type states as labels (loses the board view); change the Linear state types before the
import (Linear does not allow it for custom states in the Free plan reliably, and it touches the system being left);
do it by hand (error-prone, 60 tickets).

## R4. App and Type are labels, not custom fields

**Facts**: Kaneo labels are workspace-level and flat (no groups). The importer carries labels over by name. Custom fields
(dropdown, `required` flag) exist, and tasks accept them on create, but filtering **by custom field is not documented**,
while filtering by label is.
**Decision**: App and Type stay labels with the exact Linear names (App: Game Catalog, Finance News, Garmin, Mobile,
Recipe, Trading, Platform; Type: Bug, Feature, Improvement, Idea, Chore). "Exactly one of each" is enforced by the clients
(skills, idea box), as it is today for Linear, not by the tool. The trial checks that the board can filter by label and
that every name is unique in the workspace. Custom fields stay the fallback if label filtering is too weak.
**Rationale**: zero conversion at import, filterable, one extra API call per label at create time.

## R5. Saved views

**Not documented** whether Kaneo stores named, shareable filters. The trial finds out. Fallback: a table of filter recipes
in `docs/kaneo.md` (open work by app, open ideas, open bugs, specified and todo) that the owner applies in two clicks.

## R6. Database: a second database in the existing PostgreSQL

**Facts**: Kaneo reads `DATABASE_URL` or `POSTGRES_HOST/PORT/DB/USER/PASSWORD`; the stock Compose file ships its own
`postgres:16-alpine`. The production Postgres (16, pgvector, pgBackRest, WAL archiving) already backs up the whole cluster
to Object Storage.
**Decision**: a database `kaneo` and a role `kaneo` in the existing server, created by an idempotent host script
(`deploy/compose/scripts/ensure-kaneo-database.sh`) that sends its SQL on stdin so the password never appears in a process
list; Kaneo gets the `POSTGRES_*` variables (no URL encoding of the password). Flyway belongs to the backend and is not
involved. A restore drill restores the cluster and checks the `kaneo` database.
**Alternatives**: the stock Postgres container (a second database server to patch, size and back up; rejected unless the
trial shows a conflict, such as a required extension).

## R7. Exposure and routing

**Decision**: new Caddy site `kaneo.jordylab.be` -> `kaneo:5173` (Kaneo serves web and API on one port). No host port.
Public, because GitHub's webhook, the Claude apps and the phone must reach it. Caddy's `depends_on` is **not** extended to
Kaneo, so Kaneo being down never stops Caddy (FR-008); Caddy resolves the name per request and answers 502 for that site
only. `TRUSTED_PROXIES` is set to the Compose network's address range (read from the live network at implementation time;
a fixed subnet is better than a guessed one). DNS record at OVH by the owner.

## R8. Closing registration without locking myself out

**Facts**: a fresh Kaneo has no users; `DISABLE_REGISTRATION` closes sign-up; the instance-status endpoint reports
`hasUsers`/`hasAdmin`, which hints at a first-user bootstrap. Whether the first user can still register with registration
closed is **not documented**.
**Decision**: the trial answers it. If the first user can register with registration closed, production is deployed closed
from the start. If not, the owner registers during a window in which the site route exists but registration is open,
immediately followed by a deploy that closes it, and `hasUsers` is checked right after. The risk (a stranger registers
first) is small, short and detectable; it is recorded in the runbook.

## R9. Deploy integration that cannot break the application deploy

**Facts**: `deploy-release.sh` starts only `backend` and `frontend`, checks the public site, and restores on failure.
**Decision**: Kaneo is **not** part of that rollback loop. A separate best-effort step after a successful application deploy
runs `ensure-kaneo-database.sh`, then `docker compose up -d kaneo` (recreates only if its config or pin changed) and a
Kaneo health check; failures print a warning in the job summary and never roll back or fail the release. The smoke check
for Kaneo is a second invocation with `continue-on-error`. A Kaneo config change still needs the owner's yes (it reaches
production through a merge), as for every config change.
**Alternatives**: put `kaneo` in the `up --wait` list (couples the two; rejected by FR-008).

## R10. Secrets

New keys in the existing encrypted secrets file: `KANEO_AUTH_SECRET`, `KANEO_DB_PASSWORD`, `KANEO_GITHUB_APP_ID`,
`KANEO_GITHUB_WEBHOOK_SECRET`, `KANEO_GITHUB_PRIVATE_KEY_BASE64`, and `KANEO_API_KEY` (the backend's key). The `_FILE`
variants need a release newer than 2.28.3 and are not used (the stack already renders an environment file). The API key
can only be created in the UI after Kaneo is up, so it is a second owner step; until it is set the backend answers
"backlog unavailable", which is the designed fail-fast behaviour. The owner edits secrets in IntelliJ; the agent never
opens decrypted content.

## R11. Backend client against Kaneo's REST API

**Facts** (all from the API reference): bearer API key; `POST /task/{projectId}` needs `title`, `description`, `priority`
(`no-priority|low|medium|high|urgent`) and `status` (the **column slug**); it takes no labels and returns `number`, no
ticket id or URL (the key is `<slug>-<number>`); labels are attached afterwards with `PUT /label/{id}/task` by id; search is
`GET /search?q&workspaceId&type=tasks&projectId&limit` (relevance, no label filter, no paging); columns
`GET /column/{projectId}`; labels `GET /label/workspace/{workspaceId}`; errors are `{message, code}` with stable codes;
429 carries `Retry-After`; default limit 100 requests/60 s per key. The task's web URL pattern and `/api/health`'s body are
**not documented**.
**Decision**: a `KaneoClient` in `feedback/rest/client` mirroring the Linear one: `RestClient` with short timeouts,
cached lookups (workspace, project by key, the Triage column slug, labels by name; refreshed on a miss), and three
operations: `createTicket`, `findTicket` (search by title, then exact match on title and the `Source: idea box` marker
client-side), and `ensureLabels` (idempotent attach). Create = create task + attach App and Type labels; the adopt path also
calls `ensureLabels`, so a half-finished earlier attempt is completed rather than left unlabeled. The ticket URL is built
from a configurable template whose real shape the trial confirms; if no stable task URL exists, the project board URL is
stored. Any transport error, timeout, 401/403/429/5xx or a missing project, column or Type label becomes one
"backlog unavailable" exception; response bodies are never logged. Calls per accept: about 3 plus 3 cached lookups on a
cold start, far below 100/minute.

## R12. Persistence of accepted tickets

**Decision**: one Flyway migration renames `linear_issue_id` -> `backlog_ticket_id` and `linear_issue_url` ->
`backlog_ticket_url` (no schema data change). Old rows keep their Linear id and URL while Linear lives; before Linear is
cancelled a one-off step rewrites each old link to the Kaneo ticket: the `JOR-n` is the identifier inside the Linear URL
(`.../issue/JOR-123/title`), Kaneo resolves it with `GET /task/by-ticket-id/JOR-123`, and the step prints reviewed `UPDATE`
statements (`scripts/kaneo/relink-feedback.py`, spec FR-027). No link dies when Linear is cancelled. The admin API field `linearIssueUrl` becomes `backlogTicketUrl`; the
web app does not read it (checked: no frontend code references it). The error code and message change from
`LINEAR_UNAVAILABLE` to `BACKLOG_UNAVAILABLE`; the e2e journey is updated. A column for the ticket **key** is not added:
the key is derivable from the URL/stored id for new rows only if the trial shows it is needed (YAGNI).

## R13. GitHub integration and the written rules

**Facts**: a GitHub **App** (not an OAuth app) connected per project; branch pattern default `{slug}-{number}` with presets
and a custom regex; PR linking by key in title/body, by `task: n`, or by task URL; automations: push -> in-progress, PR open
-> in-review, merge -> done, each remappable. Not documented: whether commit messages are scanned, whether `Part of JOR-n`
stops the completion on merge, whether a key alone in a title completes the ticket, the webhook path.
**Decision**: the branch pattern is a custom regex that accepts the `jordyswinnen/jor-23-...` shape Linear's "copy branch
name" produced (and the plain `jor-23-...`); the trial runs the matrix in `contracts/board-conventions.md` on a scratch
repository, and the commit/PR instruction file, template, hook text and CI messages are then rewritten to what was observed.
If merge always completes a linked ticket, the rule becomes "link partial work by branch only, not in the title".

## R14. Agent connections

**Facts**: Kaneo exposes a remote MCP at `https://<host>/api/mcp` (Streamable HTTP, OAuth 2.1 with PKCE and consent) and a
stdio package `@kaneo/mcp` (Node 24+, device flow, credentials in `~/.config/kaneo-mcp/`). 42 tools, including
`search`, `list_tasks`, `get_task_by_ticket_id`, `create_task`, `update_task`, `move_task`, `update_task_status`,
`list_workspace_labels`, `create_label`, `attach_label_to_task`, `create_task_comment`, `create_task_relation`. Whether a
plain API key works against the remote MCP is **not documented**.
**Decision**: Claude Code and OpenCode use the remote MCP with OAuth if the trial can authorise them; otherwise the stdio
package. The Claude apps use a custom connector pointing at the same URL; if the apps cannot authorise it, that is a no-go
for the spec (User Story 1). No secret goes into `.mcp.json` or `opencode.json`.

## R15. Skills and the safety stop

**Confirmed by the owner (2026-11-07):** the renamed skills keep the `backlog-*` names (`backlog-capture`, `backlog-triage`, `backlog-sync-drafts`).

**Decision**: rename `linear-capture`, `linear-triage`, `linear-sync-drafts` to `backlog-capture`, `backlog-triage`,
`backlog-sync-drafts`; keep `speckit-taskstoissues` (customised); update `agent-handover` and `refresh-agent-models`.
Linear tool calls map to Kaneo tools (table in `contracts/agent-tooling.md`). Kaneo has no 250-issue cap, so the "stop at
230" rule becomes a **per-run write budget** (at most 40 writes a minute, 150 per run, then stop and say so) driven by the
rate limit. The two fixture scripts (`repo_inventory.sh`, `open_bugs.sh`) do not touch Linear and are only moved and
renamed; their CI paths follow.

## R16. One pull request, a later tail

The owner's rule is one pull request per spec. Two facts bend it: images are only built on `main`, so Kaneo can only reach
production after the merge, and the Linear client must stay as the way back for 30 days.
**Decision**: one pull request carries everything, with the backend holding **both** clients behind one interface and a
setting `BACKLOG_PROVIDER` (`kaneo` by default in the code). In production the value is a **GitHub repository variable**
(like `AUTO_DEPLOY`; `BACKLOG_PROVIDER=linear` until the cutover), passed by the deploy workflow to `render-env.sh`. It is
not a literal in `render-env.sh` and not a SOPS key, because the encrypted secrets file is committed to git and either
would make every flip a code change and another pull request (analysis finding I1). The same applies to
`KANEO_DISABLE_REGISTRATION` and the memory limit (`KANEO_MEM_LIMIT`, default 768m). A secret that can only exist after
Kaneo runs, the backend's `KANEO_API_KEY`, is a **production environment secret** set with `gh secret set` (the pattern the
deploy already uses for its SSH key), also passed to `render-env.sh`; secrets that exist before the merge (auth secret,
database password, GitHub App values) go into the SOPS file on the branch. Cutover is `gh variable set BACKLOG_PROVIDER
--body kaneo` plus a redeploy; rollback is setting it back; no code changes either way. The Linear client, its
tests and `LINEAR_API_KEY` are deleted by a small **follow-up pull request at least 30 days after cutover** (User Story 7).
That follow-up is the single deviation from "one pull request per spec"; **approved by the owner on 2026-11-07**.

## R17. Trial harness

A labelled, self-cleaning script (`scripts/kaneo/trial/run.sh`), modelled on `jordylab-fe/e2e/run.sh`: its own Compose
project name and label, its own Postgres, a verify-everything-is-gone step, never touching the `jordylab-be-*` dev stack.
The fix-up reads Linear's JSON export from a file outside the repository (decided 2026-11-07); no Linear key is needed, so none is stored or read.
On macOS it runs from a path under `/Users` or `/private/tmp`.

## R18. Capacity

Kaneo's docs say to plan for at least 2 GB; real use for one user is far lower, and memory is limited in the stack to
768 MB at first, adjusted from `docker stats` after a week. The host has 6 GB available today (no swap), so the 25%
headroom rule (FR-010) holds with a wide margin; it is re-measured after deploy and written in the runbook. Disk grows with
tickets and uploads only (S3 storage is optional and left off; images stay in the database or are not used).

## Open points (resolved by the trial, not by more reading)

1. First user with registration closed (R8).
2. Task URL shape and `/api/health` body (R11).
3. Label filtering, saved views (R4, R5).
4. Linking words, commit scanning, webhook path (R13).
5. Remote MCP authorisation from Claude Code, OpenCode and the Claude apps (R14).
6. Project key comes out exactly `JOR` and numbering continues (R2).
7. How a ticket is moved into and out of the Backlog view (a virtual status) and the exact endpoint that changes a task's
   column; used by the fix-up and by the triage skill (R3, R15).
8. Whether Kaneo API keys can be scoped, so the backend's key and the fix-up's key can have least privilege (R10).
9. A safe unauthenticated check that registration is closed, for the Kaneo smoke check (R8).
10. Whether Linear can lock a Free-plan workspace read-only, or "read-only" means agreement plus integrations off (US7).

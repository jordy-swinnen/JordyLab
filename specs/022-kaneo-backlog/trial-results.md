# Trial results: Kaneo go/no-go (spec 022, User Story 1)

**Status: in progress. No go/no-go decision yet.** This file is the trial record (tasks T006-T011). It is written as the
trial runs and is only marked "go" or "no-go" at T011, after the owner has reviewed it.

## Run 1: the stack starts (2026-11-07)

- Command: `scripts/kaneo/trial/run.sh up` from the repository, on macOS with Podman (machine `podman-machine-default`,
  running, arm64).
- Images, pinned by digest in `scripts/kaneo/trial/compose.yaml`:
  - Kaneo `2.49.0`, `sha256:cd1826b1aba3ce736015cadf6f6c975d923aac00f4b6aaa7aedb29bc722dae49`
  - Postgres `16-alpine`, `sha256:aa6cb33cf4c19c765cefcf48d96176258800870a9a1e930ad126200f9d825139`
- Both containers report healthy. Address: `http://127.0.0.1:<port>`, with a random port per run (the run that produced
  these notes printed `52405`; the port changes on every `up`).
- No volumes, no networks beyond the Compose default, no key or password written to disk (the harness passes them through the
  environment only).
- The development stack (`jordylab-be-pgvector-1`) was running before and after the trial; nothing in it was touched.

### Observed API answers (read-only GET requests, no account created)

| Request | Answer | Feeds |
|---|---|---|
| `GET /api/health` | `200`, body `{"status":"ok"}` | research R11 (health body: now known) |
| `GET /api/instance/status` | `{"hasUsers":false,"hasAdmin":false}` | research R8 (first user with registration closed) |
| `GET /` | `200` | the web board is served |

## Sign-up with registration closed (T006, owner in the browser, 2026-11-07)

- **First account: created.** With `DISABLE_REGISTRATION=true` and no users yet, the first sign-up succeeded. This answers
  research R8: the first user can register while registration is closed, so production can be closed from the start.
- **Second sign-up: refused, as a login page.** The sign-up form was not offered; the same address showed the login page.
  The account was not created a second time.
- **Server state, read-only, afterwards:** `GET /api/instance/status` returns `{"hasUsers":true,"hasAdmin":true}`. The
  first account is the admin.
- The sign-up result is "closed for everyone after the first user", which is what User Story 2 needs. Whether the
  registration probe can be automated (research R8, task T011) is still open: the status endpoint reports users, not
  whether registration is closed.

## Linear import (T007, owner, 2026-11-07)

- The owner ran Settings -> Workspace -> Import -> Linear once on the trial instance, using a personal Linear key. The key
  was revoked afterwards; the owner confirmed.
- Spot checks by the owner: **JOR-85** and **JOR-94** are found in Kaneo, with titles matching Linear.
- The importer reported (screenshot, 2026-11-07): 1 team (JordyLab), **85 tasks, 44 comments, 9 labels**, 5 columns; under a
  minute; imported tasks sent no notifications.
- Checked read-only against the live instance with the trial's API key (no data changed):
  - **Numbers: kept.** The project key is **JOR**. The 85 tasks are exactly the 85 open Linear issues, with the same numbers
    and no extras or gaps. The highest is 99, the same as Linear's. `lastTaskNumber` is 99, so the next new ticket should be
    JOR-100 (not tested yet: that needs a write).
  - **Columns and the Backlog view.** Todo 2, In Progress 2, In Review 0, Done 53, Canceled 4 (61 tasks in columns). The
    Backlog view ("planned") holds 24: Linear's Backlog 17 plus Drafted 7. Backlog and Drafted look the same on the board,
    so the fix-up has to restore Drafted (research R3). **No Triage column exists**; the fix-up must create Triage, Drafted
    and Specified.
  - **States agree with Linear:** Done 53 = 53, Canceled 4 = 4, Todo 2 = 2, In Progress 2 = 2, Backlog + Drafted 24 = 24.
  - **Labels: all kept (corrected 2026-11-07).** The first read showed labels missing on 56 tasks. That was my error: the
    board returns task labels one page at a time (100 label pairs per page, `relatedPage`), and I had read page 1 only.
    Reading page 2 as well gives 170 label pairs, and every one of the 85 tasks matches Linear exactly (170 pairs in
    Linear, 170 in Kaneo, no differences). The importer's "9 labels" is the number of distinct names. The workspace label
    endpoint returns 179 rows: 170 attached to tasks (one row per task and label) and 9 without a task (one per name). No
    labels were added or changed on the trial instance.
  - **Did not transfer:** 14 archived Linear issues (JOR-1 to JOR-7, JOR-53, JOR-58, JOR-59, JOR-60, JOR-71, JOR-74,
    JOR-77: 7 Canceled, 4 Todo, 2 Backlog, 1 Triage). Per the importer's documentation, projects, milestones, cycles,
    estimates and attachments do not transfer either. The Linear export (T034) is the archive for all of it.
  - **Comments:** the importer reported 44. Not checked per task.
- **Owner spot checks:** JOR-85 and JOR-94 found, with titles matching Linear.
- **Search** for "Kaneo" returns the expected tickets (`totalCount` 4).
- **By ticket id:** JOR-85 returns number 85, the same title, status `planned` (the Backlog view).

## Kaneo API key (T008, owner, 2026-11-07)

- The owner copied the key to the clipboard; the agent saved it to `~/.config/jordylab/kaneo-trial-api-key` (file mode 0600,
  folder 0700, outside the repository). It was never printed and never written into the repository.
- It works: `GET /api/workspace` returns 200.
- The owner should clear the clipboard, and revoke the key when the trial ends.

## Client rule found in the trial (for KaneoClient, T044)

- The board endpoint paginates its related data (labels, links, column metadata) separately: `relatedPage` and
  `relatedPageSize`, 100 per page here (`pagination.relatedTotalPages` was 2). A client that reads only the first page sees
  100 label pairs and silently misses the rest. Every read of the board must follow `relatedPage` until it is exhausted.
- The single-task lookup (`GET /task/by-ticket-id/...`) returned no `labels` field for JOR-85, while the board shows its
  labels. Reads of labels should go through the board, following `relatedPage`, until the single-task path is confirmed.

## Decision for the owner: none needed for labels

The label restore I proposed is not needed, and I did not run it. Restoring would have added about 70 duplicate label rows
to the trial. The go/no-go is not affected by labels.

## Still open for the go/no-go

- **Web board, checked in the built-in browser (2026-11-07, already signed in as the owner):**
  - Open tickets show their labels on the card: JOR-36 (Idea, Platform), JOR-19 (Feature, Platform), JOR-83 (Bug, Game
    Catalog). The Done tickets JOR-20 and JOR-41 show no labels on their cards, although they have labels in the data.
    Done cards appear to hide labels; this is a display choice, not data loss.
  - The Backlog view lists its 24 tasks with their labels.
  - **Label filter counts are per view.** The Tasks view counts its 61 column tasks: Platform 37, Feature 21, Bug 24,
    Improvement 11, Game Catalog 17, Chore 4, Finance News 4, Mobile 3, Idea 1. These equal the API counts for the column
    tasks exactly. The Backlog view counts its 24 tasks: Platform 22, Feature 12, Improvement 8, Chore 4, Game Catalog 2,
    which also equal the API counts for those tasks.
  - So the data is complete and the label filters are correct within each view. My expectation of 59 Platform tasks across
    both views was wrong: the two views count separately. A filter in this version cannot combine them, so a saved view
    for "open work by app" needs to cover both views (research: the saved-views step, T038).
  - The earlier pagination blocker and the earlier claim that cards never show labels are both withdrawn.
- Related Kaneo issues (public, checked 2026-11-07): #1731 "canonical label identity" (open: labels are per task, as the
  data shows), #2051 "saved board filter on a label silently stops matching" (closed; check it against 2.49.0),
  #1630 "board fetch ships labels unpaginated" (closed; the paginated behaviour seen here is the result).
- Columns: the fix-up still has to create Triage, Drafted and Specified (research R3). Backlog and Drafted are both in
  the Backlog view.

## Still to check (T008-T011)

Client contract by hand, Claude Code / OpenCode / Claude app connections, the GitHub App matrix, and the go/no-go itself.

## Client contract checks (T008, 2026-11-07)

Run by hand against the trial instance with the trial key. Writes were limited to two probe tasks, both deleted, and one
column, `Triage`, that the trial keeps for the fix-up (research R3). Label rows returned to the baseline (179).

| Check | Result |
|---|---|
| Create column `Triage` (`POST /column/{projectId}`) | 200, slug `triage`. Columns are now todo, in-progress, in-review, done, canceled, triage |
| Create a task in status `triage`, priority `no-priority` (`POST /task/{projectId}`) | 200. The response has `number` but no label list and no URL. Ticket key = project slug + number |
| Numbering | Continues from the project's `lastTaskNumber` (99): the first probe is JOR-100, the next JOR-101. Numbers are not reused after a delete |
| Label on create (`POST /label` with `taskId`) | 200. Creates a label row attached to the task |
| Same label on the same task, again | 200 and the **same row id**: repeated creates are idempotent, so adopt-on-retry is safe |
| Attach an existing workspace label row by id (`PUT /label/{id}/task`) | 200. Returns a **new** row attached to the task; the template row stays unattached |
| Detach (`DELETE /label/{id}/task`) | 200, the row is removed from the task |
| Delete the task (`DELETE /task/{id}`) | 200; later reads return 404 `NOT_FOUND`; no label rows are left pointing at it |
| Search (`GET /search?q=...&type=tasks&projectId=...`) | 200; the probe's title found (`totalCount` 1) |
| By ticket id (`GET /task/by-ticket-id/JOR-n`) | 200 with `number`, `status`, `title`. This response has no `labels` field; labels come from the board or the label endpoints |
| Move between columns (`PUT /task/status/{id}`, body `status`) | 200 for `todo`, `planned` (the Backlog view) and `triage`; read back correctly each time |
| Wrong key | 401, code `UNAUTHORIZED` |
| Rate-limit headers on a normal reply | `x-ratelimit-limit`, `x-ratelimit-remaining`, `x-ratelimit-reset` are sent on success. The docs say they come only with a 429; the instance disagrees. A 429 was not provoked |
| Health (`GET /api/health`) | 200, `{"status":"ok"}` (checked earlier) |

**What this means for the client (T044) and the fix-up (T031):**
- Label attach is safe to repeat, so the adopt path can call `ensureLabels` without creating duplicates.
- Label rows are per task. The client caches label **colours by name**, never label ids; ids differ per task.
- The fix-up can restore a missing label with `POST /label` and `taskId`, or with `PUT` on a template row (which copies it).
- Read the board with `relatedPage` (above). A client that reads only page 1 misses labels.
- Not checked yet: the API key's scopes (not visible through the API), and a 429 response.

## MCP connection (T009, 2026-11-07)

**Protocol and sign-in, checked on the trial instance:**
- `POST /api/mcp` without a token: `401` with `WWW-Authenticate: Bearer resource_metadata=...`, so discovery starts from the
  protected-resource metadata, as the spec expects.
- Protected-resource metadata: `resource` = the MCP URL; `authorization_servers` = `/api`.
- Authorization-server metadata, found at `/.well-known/oauth-authorization-server/api` (RFC 8414 path insertion; the
  `/api/.well-known/...` path returns 404): issuer `/api`; authorize `/api/mcp/authorize`; token `/api/mcp/token`;
  **dynamic client registration** `/api/mcp/register`; PKCE `S256` only; public clients (`none`).
- A client registered without credentials (`200`). The owner signs in to the trial in the browser and clicks **Allow
  access** on an "Authorize an MCP client" page, which shows the client name, the redirect address and the account.
- The token endpoint returned a bearer token valid for 30 days, with **no refresh token**. A client must sign in again after
  30 days.

**Tools, through the MCP endpoint with the token:**
- `tools/list`: **42 tools**, the same count the docs give. Required arguments are as the REST API.
- Passed: `whoami`, `list_workspaces`, `list_tasks`, `get_task_by_ticket_id` (JOR-85), `create_task` (status `triage`,
  priority `no-priority`; the probe became **JOR-102**), `update_task`, `update_task_status` (`todo` and back to `triage`),
  `create_task_comment`, `list_task_comments`, `create_label`, `attach_label_to_task`, `delete_label`, `search`,
  `delete_task`; after the delete, `get_task_by_ticket_id` returns an error "Task not found".
- **One failure:** `detach_label_from_task` on the label created with `create_label` returned "Label is not assigned to a
  task". `attach_label_to_task` makes a copy on the task (as REST's `PUT /label/{id}/task` does), so detach needs the copy's
  id, not the workspace label's. The probe's labels were removed with the probe; label rows stayed at the baseline (170
  task rows over MCP).
- Tool errors come back as HTTP 200 with the error text in the result, so a client must check the `isError` flag, not
  only the HTTP status.

**Clients (what each one needs):**
- **Claude Code and OpenCode:** the flow above is the one they use (dynamic registration, PKCE, a loopback redirect). The
  protocol check used a plain client with the same flow; it did **not** configure Claude Code or OpenCode. Doing that needs
  a change to each tool's configuration (`claude mcp add` or the OpenCode config), which is a persistent change to the
  owner's machine and needs the owner's yes.
- **Claude app (custom connector):** Anthropic's cloud must reach the MCP URL over public HTTPS. The trial is on
  `127.0.0.1`, so a tunnel is required, and a tunnel makes the trial instance public for as long as it runs. Not done; needs
  the owner's yes and a decision on the tunnel.
- The probe OAuth client stays registered on the trial until the trial is torn down. The token and the PKCE material were
  deleted from the scratch folder.

## Live clients (T009, 2026-11-07, after the owner's yes)

**Configuration changes on this Mac, all for the trial and all to be removed when it ends:**
- Claude Code: `claude mcp add --transport http kaneo-trial http://127.0.0.1:52405/api/mcp` at **local** scope (private
  to this project, stored in `~/.claude.json`). The repository's `.mcp.json` was not changed.
- OpenCode: `opencode mcp add --global --url http://127.0.0.1:52405/api/mcp kaneo-trial`, which wrote to
  `~/.config/opencode/opencode.jsonc`. The repository's `opencode.json` was not changed (checked with git: unchanged).

**OpenCode, connected:**
- `opencode mcp auth kaneo-trial` started the sign-in; the authorization page appeared in the built-in browser, where the
  owner is signed in. It showed the client `opencode`, the redirect to OpenCode's local callback, and the account. The
  owner's yes was given for this (Allow access).
- `opencode mcp list`: `kaneo-trial` **connected**.
- A read-only tool call through OpenCode: `get_task_by_ticket_id` for JOR-85 returned "Migrate Linear to self-hosted Kaneo
  (Docker) — status: planned".

**Claude Code, blocked by the transport (2026-11-07):**
- `claude mcp list`: `kaneo-trial` **needs authentication**. The sign-in is the interactive `/mcp` screen. Typing
  `/mcp kaneo-trial` is not valid; `/mcp` with no argument, then choose the server and Authenticate, is the path.
- When the owner tried it, Claude Code **refused to open the sign-in page**: "must be https". The trial serves
  `http://127.0.0.1:52405`, so Claude Code cannot complete sign-in against it. OpenCode accepts the same address, so this
  is a Claude Code rule, not a fault in the trial.
- Production is `https://kaneo.jordylab.be`, so the production check does not hit this rule. Claude Code's sign-in can be
  verified only against an HTTPS address: either the production host after the merge, or a local HTTPS setup for the trial.
  The local setup needs a certificate the Mac trusts and an extra CA setting for Claude Code, and changes the Mac's trust
  store; a public tunnel exposes the trial. Neither has been done.
- **Decision (owner, 2026-11-07):** verify Claude Code, and the Claude app, **after the merge, against
  `https://kaneo.jordylab.be`**. No local HTTPS and no tunnel for the trial. The production check is part of the Operate
  stage (see tasks.md, Dependencies). At teardown, remove the trial's local Claude Code entry with
  `claude mcp remove kaneo-trial -s local` as well as the OpenCode entry listed above.

**Claude app (custom connector):** still not done. It needs a public HTTPS address for the trial (a tunnel), and the owner
has not yet decided on one.

**Removal when the trial ends:**
- `claude mcp remove kaneo-trial -s local`
- Remove the `kaneo-trial` entry from `~/.config/opencode/opencode.jsonc`, then `opencode mcp logout kaneo-trial`.
- `scripts/kaneo/trial/run.sh down` and `verify`, then revoke the Kaneo API key.

## Teardown record (2026-11-07)

- **API key revoked.** The stored key still worked when checked, so it had not been revoked. The owner's key list is only
  available in a browser session, so the owner's built-in browser session deleted the key ("jordylab", prefix `viltOB…`,
  Settings → Account → API keys). The stored key then returned 401. The local key file was removed.
- **Claude Code entry removed** (`claude mcp remove kaneo-trial -s local`). **OpenCode entry removed** from
  `~/.config/opencode/opencode.jsonc` (the `servers` block that held only `kaneo-trial`; `linear` kept), and the OpenCode
  sign-in credentials for `kaneo-trial` removed. The repository's `opencode.json` and `.mcp.json` were not changed.
- **Stack torn down.** `run.sh down` left one trial container (the Postgres). It was removed by ID, and `run.sh verify`
  then reported "verified clean". The cause is not confirmed; the removal loop in `run.sh` now redirects stdin for each
  removal (`</dev/null`), which is a plausible cause because the loop reads the resource list from stdin. The 22 harness
  cases still pass.
- The development stack (`jordylab-be-pgvector-1`) was running before and after, and was not touched.

## Teardown

When the trial is finished: `scripts/kaneo/trial/run.sh down`, then `scripts/kaneo/trial/run.sh verify` (must report
"verified clean"). The trial is not finished until that has run (T012).

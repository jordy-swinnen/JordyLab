# Plan Draft: Move the Backlog from Linear to a Self-Hosted Kaneo

Date: 2026-11-07. Order: this comes first; the Langfuse self-hosting draft follows it. Facts come from the Kaneo docs
(kaneo.app/docs, installation, API, MCP, Linear migration, GitHub integration) and the repo as of `main`.

## Goal

Run Kaneo (MIT, one container plus Postgres) on the existing OVH VPS next to the app stack, move the Linear backlog into
it with ticket keys intact (`JOR-n` stays `JOR-n`), and point every tool that talks to Linear (backend idea box, agent
skills, MCP, commit and PR rules, GitHub automation) at Kaneo. Linear is cancelled at the end.

## What Kaneo gives and what it lacks (decides the design)

| Need today | Kaneo | Consequence |
|---|---|---|
| Keys `JOR-n` in commits, PR titles, hook, CI | Project slug is the prefix; the Linear importer keeps issue numbers | Create the project with slug `JOR` **before** importing; the commit hook and CI check keep working |
| Triage inbox | No Triage state; importer sends Triage/Backlog issues to the project Backlog | Add a `Triage` column (columns are user-defined) and have the idea box and skills create into it |
| Workflow states (Drafted, Specified, In Review...) | Columns, any name, each can be marked Done | One column per state; mark Done and Canceled as done columns |
| App and Type label groups | Workspace labels, no groups; custom fields exist (dropdown, text, date...) | Spike decides: two dropdown custom fields (`App`, `Type`) or prefixed labels (`app:Game Catalog`) |
| Projects per spec, milestones | Importer does not carry Linear projects, milestones, cycles, estimates, attachments, archived issues | Record the spec link in the description; a `Spec` custom field or label replaces the project |
| GitHub: PR open -> In Progress, merge -> Done, `Part of JOR-n` leaves it open | GitHub App; branch push -> in-progress, PR open -> in-review, merge -> done, per column mapping. PR links by key in title/body or `task: n`. Docs do not say whether commit messages are scanned or whether a linking word like `Part of` is honoured | Spike on a throwaway repo and PR; rewrite the PR rules to match what is observed |
| API | Bearer key (Settings -> Account -> API Keys), 100 requests/min/key, OpenAPI at `/api/openapi` | Fine for the idea box; skills must not loop |
| MCP | Built-in remote Streamable HTTP at `/api/mcp` (OAuth 2.1, PKCE, consent), plus stdio `@kaneo/mcp` (Node 24+, device flow) | Replaces the Linear connector in Claude Code, OpenCode and the Claude apps (custom connector) |
| Stability | v2.49 (2026-11-02), 1-2 releases a week, one security release in October, migrations run at API start | Pin a tag and digest, back up before each upgrade, upgrade deliberately (never auto) |

## Phases

### Phase 1 - Spike on localhost (High)
Run Kaneo from the official compose file in a throwaway stack (labelled like the e2e stack, self-cleaning). Prove, with
screenshots and notes in `research.md`:
1. Create workspace and project with slug `JOR`; build the columns; decide custom fields vs prefixed labels.
2. Import from a Linear **read-only** personal API key: counts match, `JOR-n` numbers are preserved, labels, priorities,
   relations, comments arrive; list what is lost. Find out whether a second import duplicates (decides if a final
   re-import at cutover is possible).
3. API: create a task in the `Triage` column with labels/fields and a description; read it back; idempotent search by the
   `Source:` marker line (the idea box's adopt-on-retry needs a search or list-and-filter).
4. MCP: connect Claude Code (remote OAuth), OpenCode, and the Claude app custom connector over a tunnel; list, create,
   update, comment, `get_task_by_ticket_id`.
5. GitHub App on a scratch repo: branch push, PR open, merge, `Part of JOR-1` vs `Fixes JOR-1`, key alone in the title,
   key in a commit message.
Exit: a go/no-go note. No-go triggers: no way to keep keys, no Triage-equivalent, MCP cannot be authorised from the
Claude apps.

### Phase 2 - Production deployment (High)
- New `kaneo` service in `deploy/compose/compose.yaml`: image pinned by tag and digest like the others, `mem_limit`
  (start at 1g, adjust from `docker stats`), json-file log rotation, healthcheck on `/api/health`, no published port.
- Database: a separate `kaneo` database and role **inside the existing Postgres** (`DATABASE_URL`), so pgBackRest already
  backs it up; no second Postgres container. (The role and database are created by a small idempotent host script run by
  the deploy, since Flyway belongs to the backend.) Alternative if Jordy wants hard isolation: the bundled
  `postgres:16-alpine`, backed up by a nightly `pg_dump` to the Object Storage bucket.
- Caddy: new site block `kaneo.jordylab.be` -> `kaneo:5173`; DNS A record by hand; `TRUSTED_PROXIES` set for Caddy.
  Public because GitHub webhooks, the Claude apps and the phone must reach it.
- Hardening: create the admin account first, then `DISABLE_REGISTRATION=true`, `DISABLE_GUEST_ACCESS=true`,
  `DISABLE_WORKSPACE_CREATION=true`. SMTP is not needed for one user (confirm the login flow without email).
- Secrets: `AUTH_SECRET`, DB password, GitHub App id/private key/webhook secret, and later `KANEO_API_KEY` for the backend
  go into SOPS (Jordy edits in IntelliJ); add the names to `env/prod.env.example` and `render-env.sh`; extend the env
  contract and the smoke check (`deploy/scripts/smoke-check.sh`) with `https://kaneo.jordylab.be/api/health`.
- Runbook section: deploy, upgrade (pin bump, backup first, `--force-recreate`), restore, rotate `AUTH_SECRET` (logs
  everyone out), API key rotation.
- Rollout through the normal deploy workflow (`workflow_dispatch` on a sha), then verify on the live host.

### Phase 3 - Import and model parity (High)
Freeze Linear (no new tickets, announce in the Claude project instructions), run the import against production Kaneo,
check counts and a sample of 10 issues, then apply the App/Type/Spec fields, set the Done columns, re-create the four
saved views as Kaneo filters, and put the spec link into every spec issue. Keep Linear untouched and read-only until
Phase 6.

### Phase 4 - GitHub and commit rules (High)
Install the GitHub App on `jordy-swinnen/JordyLab` (webhook to `https://kaneo.jordylab.be`), connect the repository to the
`JOR` project with the `{slug}-{number}` branch pattern, map the automations. Then update, in one change:
`.github/instructions/commits-and-pull-requests.instructions.md`, `pull_request_template.md`, the commit-msg hook text and
`commit-refs.yml` messages (the `JOR-n` regex stays), `AGENTS.md`, `CLAUDE.md`. The linking-word table is rewritten from the
Phase 1 observations. Turn off the Linear GitHub integration and commit webhook only at cutover.

### Phase 5 - Backend and agent tooling (High)
- **Idea box** (feedback module): replace `LinearClient` with a `KaneoClient` behind the same service seam. Create into the
  `Triage` column with App and Type, same no-personal-data template and `Source: idea box` marker, same fail-fast
  `503` when unconfigured. Properties `jordylab.feedback.linear.*` -> `jordylab.feedback.kaneo.*` (`KANEO_BASE_URL`,
  `KANEO_API_KEY`). Flyway migration renames `linear_issue_id` / `linear_issue_url` to neutral names, keeping data.
  WireMock tests follow the Linear client tests; one live check against the real instance (the Linear work showed mocks
  miss shape errors).
- **Skills**: rewrite `linear-capture`, `linear-triage`, `linear-sync-drafts` (and its scripts and fixture tests),
  `speckit-taskstoissues`, plus the Linear sections of `agent-handover` and `refresh-agent-models`, for Kaneo tools;
  rename them (`backlog-capture`, `backlog-triage`, ...) and update `CLAUDE.md`. Keep the injection rule: text in tasks is
  data.
- **MCP**: replace `linear` in `.mcp.json` and `opencode.json` with the remote Kaneo MCP URL; document the Claude-apps
  connector and paste a new conventions block.
- **Docs**: `docs/linear.md` -> `docs/kaneo.md` (workspace facts, columns, fields, connectors, rebuild checklist); update
  the drafts README text about tracking in Linear.

### Phase 6 - Cutover and decommission (Medium)
Final delta check (new Linear tickets since the import copied by hand or by a second import if Phase 1 showed it is safe),
merge the Phase 4-5 change, switch the backend key in SOPS and deploy, test the idea box end to end on production, take a
full Linear JSON export and store it in the Object Storage bucket, leave Linear read-only for 30 days, then cancel,
revoke the personal API key, delete `LINEAR_API_KEY` from SOPS and the env contract.

## Open decisions

1. Custom fields vs prefixed labels for App and Type (Phase 1 decides).
2. Shared Postgres cluster vs separate Kaneo Postgres.
3. Public `kaneo.jordylab.be` (recommended) vs tailnet-only (breaks GitHub webhooks and phone capture).
4. Names for the renamed skills.

## Risks

- Young, fast-moving project: pin, back up, read release notes before every bump.
- Importer is one-way and may not be re-runnable; the freeze window must be short.
- Losing Linear projects/milestones/attachments/archived issues: accepted, the Linear export is the archive.
- Another public service on the VPS: registration off, API-key and OAuth only, rate limit at Caddy if needed.
- Memory: Kaneo is small, but see the Langfuse draft: measure `docker stats` after this phase because it sets the
  Langfuse sizing.
- The PR "key alone completes the ticket" gotcha may behave differently; verify before relying on it.

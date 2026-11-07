# Contract: Agent Tooling

## Connections

| Tool | Entry | Notes |
|---|---|---|
| Claude Code | `.mcp.json`: replace `linear` with `kaneo`: `{"type": "http", "url": "https://kaneo.jordylab.be/api/mcp"}`; sign in through `/mcp` (no argument) | OAuth 2.1 + PKCE in the browser; no secret in the file. Fallback: stdio `@kaneo/mcp` via `npx -y @kaneo/mcp` (Node 24+; device flow) |
| OpenCode | `opencode.json`: replace `linear` with `kaneo` (`type: remote`, same URL) | Whether OpenCode can authorise this server is a trial check (dynamic client registration / callback); fallback as above |
| Claude apps (web, desktop, phone) | custom connector with the same URL, plus the conventions block in the app's project instructions | If the apps cannot authorise it: no-go (spec User Story 1) |

The `github` and `jordylab` entries are untouched. `docs/kaneo.md` replaces the Linear connector sections.

## Tool mapping (what the skills call)

| Need | Linear tool (today) | Kaneo MCP tool |
|---|---|---|
| Who am I / workspace | `whoami`, `get_team` | `whoami`, `list_workspaces`, `list_projects` |
| Duplicate search by words | `list_issues` (query) | `search` (type tasks, project `JOR`) |
| Read one ticket | `get_issue` | `get_task_by_ticket_id` (`JOR-n`) / `get_task` |
| List a column or filter | `list_issues` (state, label) | `list_tasks` (status, priority) and filter labels client-side |
| Create | `save_issue` (no id) | `create_task` (status `triage`, priority none), then `attach_label_to_task` x2 |
| Update state / title / body | `save_issue` (id) | `update_task`, `move_task` / `update_task_status` (moving into and out of the Backlog view is confirmed by the trial before the skills rely on it) |
| Labels | `list_issue_labels`, labels param | `list_workspace_labels`, `create_label`, `attach_label_to_task`, `detach_label_from_task` |
| Comment | `save_comment` | `create_task_comment` |
| Relations | relations in `save_issue` | `create_task_relation`, `get_task_relations` |
| Project per spec | `save_project` | none (spec link in the description's first line) |
| Milestones | `save_milestone` | none (the tasks-to-tickets skill uses one ticket per story plus a `Phase:` line) |

Kaneo has no label groups, so the skills check "exactly one App label and one Type label" themselves before and after every
write, and say what is missing.

## Skills

| Today | After | Change |
|---|---|---|
| `linear-capture` | `backlog-capture` | Kaneo tools; create in Triage; duplicate check with `search`; same preview/approval/outage-message rules |
| `linear-triage` | `backlog-triage` | list Triage column; table of proposed labels/duplicates; apply only confirmed rows by moving to Backlog; injected-text rule kept |
| `linear-sync-drafts` | `backlog-sync-drafts` | scripts `repo_inventory.sh`, `open_bugs.sh` and their fixture tests move with it; creates/updates one ticket per spec/draft/open bug in the mapped column; **write budget** instead of the 230-issue stop |
| `speckit-taskstoissues` | same name | one ticket per user story and per phase without a story, a `Phase:` line instead of milestones, links as relations, spec link on the first line |
| `agent-handover` (section 5b) | same | the handover comment is posted on the Kaneo ticket via `create_task_comment`; key found from the branch name / commits as before |
| `refresh-agent-models` | same | replace the Linear wording; branch/commit rules unchanged |

### Write budget (replaces the plan-size stop)

Any run that writes to the board: at most 40 writes per minute (pace with sleeps), at most 150 writes per run; when the
budget is hit the skill stops, reports what it did and what remains, and waits. A 429 is never retried in a loop. Reads are
batched (one `list_tasks` per column, not one call per ticket).

## Files touched (inventory; tasks refine it)

`.mcp.json`, `opencode.json`, `AGENTS.md`, `CLAUDE.md`, `.github/instructions/commits-and-pull-requests.instructions.md`,
`.github/copilot-instructions.md` (if it names Linear), `.github/pull_request_template.md`, `.githooks/commit-msg` (message
text only), `.github/workflows/commit-refs.yml` (message text), `.github/workflows/hook-tests.yml` (skill paths),
`.claude/skills/{linear-capture,linear-triage,linear-sync-drafts}` (renamed), `speckit-taskstoissues`, `agent-handover`,
`refresh-agent-models`, `.claude/skills/linear-sync-drafts/app-map.tsv` (moves), `specs/_drafts/README.md`,
`docs/linear.md` -> `docs/kaneo.md`, `docs/runbook.md` (new Kaneo section), `jordylab-be/AGENTS.md`.
OpenCode mirrors under `.opencode/` follow the same renames.

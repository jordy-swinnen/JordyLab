@AGENTS.md
@.github/instructions/commits-and-pull-requests.instructions.md

## Claude Automations

Skills (invoke with `/skill-name`):
- `/entity` — scaffold a new DDD entity with builder + test fixtures
- `/test-builder` — generate a TestBuilder fixture class
- `/flyway-migration` — create a dated Flyway migration targeting the correct schema
- `/modularity-check` — run Spring Modulith boundary tests and fix violations
- `/new-module` — scaffold a full module (backend DDD structure + frontend Nx libs)
- `/ai-endpoint` — wire up a ResilientAiService integration for a module
- `/angular-signal-store` — generate an NgRx-free signal-based state service for an Angular domain
- `/angular-test` — Angular/Vitest testing conventions (mocking, spec structure, mock fixtures)
- `/kaneo-idea` — capture a rough idea, bug or chore as a thin Kaneo ticket in Ideas, with a duplicate check; then hand over to `/kaneo-refinement`
- `/kaneo-refinement` — turn a rough ticket into a Ready ticket (functional analyst and customer proxy) and move it to Todo
- `/kaneo-design` — optional design step for tickets flagged `needs-design` or `needs-ui-design`, then move to Todo
- `/kaneo-board` — shared Kaneo conventions (columns, labels, Ready template, Definition of Ready); read by the three skills above
- `/kaneo-start-ticket` — runs before `/speckit-specify JOR-<n>` (SpecKit hook in `.specify/extensions.yml`): moves the ticket to In progress and comments the branch
- `/backlog-capture`, `/backlog-triage`, `/backlog-sync-drafts` — older Kaneo skills for the Triage inbox and drafts; they still assume the Triage and Drafted columns (see `docs/ai-workflow.md`)
- `/speckit-taskstoissues` — customised: turns a spec's tasks into Kaneo tickets in project JOR, one per user story and one per phase without a story
- `/dual-agent-config` — set up and audit shared config so Claude Code and OpenCode read the same rules/skills/MCP servers
- `/agent-handover` — hand work over between Claude Code and OpenCode (wrap up, update tasks/bug log, write
  `HANDOFF-next-agent.md`, print the prompt for the other tool, stop); also used by the agent taking over
- `/refresh-agent-models` — re-curate the Planner, Reviewer and Worker agent models and effort levels for OpenCode (OpenCode Go) and Claude Code using the Artificial Analysis leaderboards
- `/model-harness-recommendations` — recommend a model per harness and cost level for SpecKit Plan and Build steps (method only; reads current prices and scores each time)
- `trading-guard` — background rules for the trading module (human-in-the-loop enforcement, not user-invocable)
- `jordylab-ops` — production operations knowledge (VPS + k3s, deploys/approvals/rollbacks, SOPS secrets edited in
  IntelliJ, identity/user administration, CNPG backups); the single source of truth, shared natively with OpenCode

Agents (run automatically by Claude):
- `code-reviewer` — reviews against all JordyLab conventions; reports issues by severity
- `test-writer` — generates tests matching existing patterns
- `architect` — read-only codebase exploration for architecture questions
- `jordylab-devops` — delegated production investigations/changes in their own context; preloads the
  `jordylab-ops` skill and holds no operational knowledge itself (mirrored in `.opencode/agents/`)

MCP servers:
- `nx-mcp` — configured in `.mcp.json` (repo root; also defines the `github` MCP server)
- `kaneo` — the Kaneo board (project JOR) at `https://kaneo.jordylab.be/api/mcp`, configured in `.mcp.json` and `opencode.json`; no token in the repo, sign in with `/mcp`
- `jordylab` — the read-only connector at `https://jordylab.be/mcp`, public client `jordylab-mcp-connector`, local callback
  port 19877; run `/mcp` and authenticate with the admin account

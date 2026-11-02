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
- `/dual-agent-config` — set up and audit shared config so Claude Code and OpenCode read the same rules/skills/MCP servers
- `/agent-handover` — hand work over between Claude Code and OpenCode (wrap up, update tasks/bug log, write
  `HANDOFF-next-agent.md`, print the prompt for the other tool, stop); also used by the agent taking over
- `/linear-capture` — log an idea, bug, improvement, feature or chore in Linear (team `JOR`, `Triage` state, duplicate check first), or turn a conversation/plan into user-story-sized tickets
- `/linear-sync-drafts` — mirror repository specs, drafts and open bugs into Linear and keep states in sync
- `/refresh-agent-models` — re-curate the Planner, Reviewer and Worker agent models and effort levels for OpenCode (OpenCode Go) and Claude Code using the Artificial Analysis leaderboards
- `trading-guard` — background rules for the trading module (human-in-the-loop enforcement, not user-invocable)
- `jordylab-ops` — production operations knowledge (VPS + k3s, deploys/approvals/rollbacks, SOPS secrets edited in
  IntelliJ, Keycloak kcadm, CNPG backups); the single source of truth, shared natively with OpenCode

Agents (run automatically by Claude):
- `code-reviewer` — reviews against all JordyLab conventions; reports issues by severity
- `test-writer` — generates tests matching existing patterns
- `architect` — read-only codebase exploration for architecture questions
- `jordylab-devops` — delegated production investigations/changes in their own context; preloads the
  `jordylab-ops` skill and holds no operational knowledge itself (mirrored in `.opencode/agents/`)

MCP servers:
- `nx-mcp` — configured in `.mcp.json` (repo root; also defines the `github` MCP server)

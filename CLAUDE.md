@AGENTS.md

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
- `trading-guard` — background rules for the trading module (human-in-the-loop enforcement, not user-invocable)

Agents (run automatically by Claude):
- `code-reviewer` — reviews against all JordyLab conventions; reports issues by severity
- `test-writer` — generates tests matching existing patterns
- `architect` — read-only codebase exploration for architecture questions

MCP servers:
- `nx-mcp` — configured in `.mcp.json` (repo root; also defines the `github` MCP server)

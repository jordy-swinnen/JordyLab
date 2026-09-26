# Full compatibility matrix

Per-concern detail behind the summary table in SKILL.md. Verified against official docs; see the `docs-verified` date in the SKILL.md frontmatter.

## Contents

- [Project instructions](#project-instructions)
- [Global / user instructions](#global--user-instructions)
- [Skills](#skills)
- [Path/language-scoped rules](#pathlanguage-scoped-rules)
- [Subagents](#subagents)
- [Slash commands](#slash-commands)
- [MCP servers](#mcp-servers)
- [Permissions](#permissions)
- [Hooks and plugins](#hooks-and-plugins)
- [Model selection](#model-selection)
- [Formatter / LSP](#formatter--lsp)

---

## Project instructions

**Claude Code:** `./CLAUDE.md` *or* `./.claude/CLAUDE.md`. Nested `CLAUDE.md` in subdirectories load on demand when Claude works in that directory. `/init` generates one. Delivered as a user message after the system prompt — it is context, not enforcement; there is no compliance guarantee. For hard enforcement use `PreToolUse` hooks or permission `deny` rules.

**OpenCode:** `AGENTS.md` at project root. `/init` generates or updates in place.

**Sharing:** `CLAUDE.md` containing `@AGENTS.md`, or a symlink. Claude Code never reads `AGENTS.md` on its own.

## Global / user instructions

**Claude Code:** `~/.claude/CLAUDE.md`. Managed-policy CLAUDE.md also exists at OS-specific system paths and cannot be excluded via `claudeMdExcludes`.

**OpenCode:** `~/.config/opencode/AGENTS.md`, **falling back to `~/.claude/CLAUDE.md`** when absent.

**Sharing:** the fallback means a single `~/.claude/CLAUDE.md` can serve both — until someone creates the OpenCode-native file, which then wins for OpenCode.

## Skills

**Claude Code discovers:** `.claude/skills/<name>/SKILL.md`, `~/.claude/skills/<name>/SKILL.md`, plugin `skills/`, and enterprise/managed locations.

**OpenCode discovers:** `.opencode/skills/<name>/SKILL.md`, `~/.config/opencode/skills/<name>/SKILL.md`, `.claude/skills/`, `~/.claude/skills/`, `.agents/skills/`, `~/.agents/skills/`.

**Portable frontmatter (Agent Skills spec — the only six fields both tools honor):**

| Field | Required | Constraint |
|---|---|---|
| `name` | yes | 1–64 chars, lowercase alphanumerics + hyphens, no leading/trailing/consecutive hyphens, must match parent directory name |
| `description` | yes | 1–1024 chars, states what it does *and* when to use it |
| `license` | no | license name or bundled file reference |
| `compatibility` | no | ≤500 chars, environment requirements |
| `metadata` | no | string→string map |
| `allowed-tools` | no | space-separated tool list (experimental) |

**OpenCode ignores any other key silently.** Claude Code accepts many extensions (`when_to_use`, `argument-hint`, `arguments`, `disable-model-invocation`, `user-invocable`, `disallowed-tools`, `model`, `effort`, `context`, `agent`, `background`, `hooks`, `paths`, `shell`) — but any of those cause `Unexpected key(s) in SKILL.md frontmatter` when packaging for claude.ai or the Skills API.

**Other constraints:** `SKILL.md` must be uppercase; skill names must be unique across all discovery locations. In Claude Code personal/project skills the *directory name* is the slash command; the `name` field is the display label. Claude Code truncates combined `description` + `when_to_use` at 1,536 characters in the skill listing, and caps the whole listing at roughly 1% of the context window (tunable via `skillListingBudgetFraction` / `SLASH_COMMAND_TOOL_CHAR_BUDGET`) — with many skills installed, low-use ones silently lose their descriptions and stop auto-triggering.

**Progressive disclosure targets:** metadata ~100 tokens, `SKILL.md` body under 5,000 tokens / 500 lines, everything else in `references/`, `scripts/`, `assets/` loaded on demand. Keep file references one level deep.

## Path/language-scoped rules

See `scoped-rules.md` — this concern has enough detail to warrant its own file.

## Subagents

**Claude Code:** `.claude/agents/<n>.md` (project) or `~/.claude/agents/<n>.md` (user). **Identity comes from the `name` frontmatter field**, which is required; the filename need not match.

Frontmatter fields: `name`, `description` (both required), `tools`, `disallowedTools`, `model`, `permissionMode`, `maxTurns`, `skills`, `mcpServers`, `hooks`, `memory`, `background`, `effort`, `isolation`, `color`, `initialPrompt`. `model` accepts `sonnet` / `opus` / `haiku` / `fable`, a full model ID, or `inherit`.

**OpenCode:** `.opencode/agents/<n>.md` or `~/.config/opencode/agents/<n>.md`, or inline under `agent` in `opencode.json`. **Identity comes from the filename** (`review.md` → `review`).

Frontmatter fields: `description` (required), `mode` (`primary` / `subagent` / `all`, default `all`), `model`, `temperature`, `top_p`, `prompt`, `tools` (deprecated in favor of `permission`), `permission`, `steps` (`maxSteps` deprecated), `disable`, `hidden`, `color`.

**Note:** a markdown agent file that fails to parse simply doesn't appear in OpenCode's agent list, with no explicit error.

## Slash commands

**Claude Code:** `.claude/commands/<n>.md`. As of 2026 commands and skills are merged — `.claude/commands/deploy.md` and `.claude/skills/deploy/SKILL.md` both produce `/deploy`, and **the skill wins if both exist.** Frontmatter: `description`, `allowed-tools`, `argument-hint`, `model`, `disable-model-invocation`, `arguments`. Placeholders: `$ARGUMENTS`, `$0`/`$1`, `` !`cmd` `` for bash, `@` for files.

**OpenCode:** `.opencode/commands/<n>.md` or `~/.config/opencode/commands/<n>.md`, or a `command` block in `opencode.json`. Filename = command name. Frontmatter: `description`, `agent`, `model`, `subtask` (boolean — forces subagent invocation). Placeholders: `$ARGUMENTS`, `!` bash injection, `@` file references.

The template bodies and placeholder conventions are close enough to copy; the frontmatter is not.

## MCP servers

See `mcp-translation.md`.

## Permissions

**Claude Code:** `permissions` block in `.claude/settings.json`, `.claude/settings.local.json`, or `~/.claude/settings.json`. Arrays `allow` / `deny` / `ask`, plus `defaultMode` and `additionalDirectories`. Rule syntax is `Tool(specifier)`: `Bash(npm run test:*)`, `Read(./.env)`, `Edit(src/**)`.

**Precedence: `deny` > `ask` > `allow`. Deny always wins**, regardless of order or specificity. Rules merge across settings scopes.

**OpenCode:** `permission` block in `opencode.json` or in agent frontmatter. Keys are tool names: `read`, `edit` (covers write/edit/patch), `bash`, `glob`, `grep`, `list`, `task`, `webfetch`, `websearch`, `skill`, `lsp`, `external_directory`, `doom_loop`, `question`, `todowrite`. Values are `"allow"` / `"ask"` / `"deny"`, or an object mapping pattern → action.

**Precedence: last matching rule wins.** Put `"*"` first and specifics after, or the catch-all overrides everything.

These two models are inverted. Never translate one to the other mechanically.

## Hooks and plugins

**Claude Code:** `hooks` block in `settings.json`. Events include `PreToolUse`, `PostToolUse`, `UserPromptSubmit`, `SessionStart`, `SessionEnd`, `Stop`, `StopFailure`, `SubagentStart`, `SubagentStop`, `PreCompact`, `PostCompact`, `Notification`, `InstructionsLoaded`, `ConfigChange`. Declarative — shell commands, HTTP calls, or prompts, with matcher syntax. Also a full plugin/marketplace system (`.claude-plugin/plugin.json`, with `commands/`, `agents/`, `skills/`, `hooks/hooks.json`, `.mcp.json` at plugin root).

**OpenCode:** JS/TS plugins in `.opencode/plugins/`, `~/.config/opencode/plugins/`, or npm packages listed under `plugin` in config. Plugins hook into events programmatically.

No translation path. Author separately.

## Model selection

**Claude Code:** the `model` setting is read once at session start; `/model` switches mid-session. Subagents accept aliases (`sonnet`, `opus`, `haiku`, `fable`), full model IDs, or `inherit`.

**OpenCode:** `model: "provider/model-id"` (e.g. `anthropic/claude-sonnet-4-5`), plus `small_model` for cheap background work, plus per-agent `model`.

## Formatter / LSP

**OpenCode:** `formatter` and `lsp` keys in `opencode.json`; each accepts `true` or an object with per-tool overrides and custom entries.

**Claude Code:** no equivalent config surface. Formatting is handled via a `PostToolUse` hook or IDE integration. (This is inferred from the absence of such keys, not an explicit statement that it's unsupported.)

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
- [OpenCode 1 → 2](#opencode-1--2)

---

## Project instructions

**Claude Code:** `./CLAUDE.md` *or* `./.claude/CLAUDE.md`. Nested `CLAUDE.md` in subdirectories load on demand when Claude works in that directory. `/init` generates one. Delivered as a user message after the system prompt — it is context, not enforcement; there is no compliance guarantee. For hard enforcement use `PreToolUse` hooks or permission `deny` rules.

**OpenCode 2:** `AGENTS.md` only. It loads the files from the workspace directory up to the project root at start, and nested ones below the workspace when the agent first reads or lists something there. Edits to a loaded file are picked up before the next request. `CLAUDE.md` is never read (V1 fell back to it).

**Sharing:** `CLAUDE.md` containing `@AGENTS.md`, or a symlink. Neither tool reads the other's file on its own.

## Global / user instructions

**Claude Code:** `~/.claude/CLAUDE.md`. Managed-policy CLAUDE.md also exists at OS-specific system paths and cannot be excluded via `claudeMdExcludes`.

**OpenCode 2:** `~/.config/opencode/AGENTS.md` only. The V1 fallback to `~/.claude/CLAUDE.md` is gone.

**Sharing:** content in `~/.config/opencode/AGENTS.md`, and `~/.claude/CLAUDE.md` containing `@~/.config/opencode/AGENTS.md`.

## Skills

**Claude Code discovers:** `.claude/skills/<name>/SKILL.md`, `~/.claude/skills/<name>/SKILL.md`, plugin `skills/`, and enterprise/managed locations.

**OpenCode 2 discovers:** `.opencode/skills/` (and V1 `.opencode/skill/`), `~/.config/opencode/skills/`, and for compatibility `.claude/skills/`, `~/.claude/skills/`, `.agents/skills/`, `~/.agents/skills/`, plus any `skills` paths or URLs in config. Project sources are searched from the current directory up to the project root. The skill ID is the folder name; `name` is only a display label. When an ID exists twice, the source registered last wins: `.claude/skills`, then `.agents/skills`, then `~/.config/opencode/skills`, then `.opencode/skills`. `metadata.opencode/autoinvoke: false` or `disable-model-invocation: true` hides a skill from the model's list.

**Portable frontmatter (Agent Skills spec — the only six fields both tools honor):**

| Field | Required | Constraint |
|---|---|---|
| `name` | yes | 1–64 chars, lowercase alphanumerics + hyphens, no leading/trailing/consecutive hyphens, must match parent directory name |
| `description` | yes | 1–1024 chars, states what it does *and* when to use it |
| `license` | no | license name or bundled file reference |
| `compatibility` | no | ≤500 chars, environment requirements |
| `metadata` | no | string→string map |
| `allowed-tools` | no | space-separated tool list (experimental) |

**OpenCode 2 accepts `license` and `compatibility` without interpreting them, and ignores unknown keys silently.** Claude Code accepts many extensions (`when_to_use`, `argument-hint`, `arguments`, `disable-model-invocation`, `user-invocable`, `disallowed-tools`, `model`, `effort`, `context`, `agent`, `background`, `hooks`, `paths`, `shell`) — but any of those cause `Unexpected key(s) in SKILL.md frontmatter` when packaging for claude.ai or the Skills API.

**Other constraints:** `SKILL.md` must be uppercase; skill names must be unique across all discovery locations. In Claude Code personal/project skills the *directory name* is the slash command; the `name` field is the display label. Claude Code truncates combined `description` + `when_to_use` at 1,536 characters in the skill listing, and caps the whole listing at roughly 1% of the context window (tunable via `skillListingBudgetFraction` / `SLASH_COMMAND_TOOL_CHAR_BUDGET`) — with many skills installed, low-use ones silently lose their descriptions and stop auto-triggering.

**Progressive disclosure targets:** metadata ~100 tokens, `SKILL.md` body under 5,000 tokens / 500 lines, everything else in `references/`, `scripts/`, `assets/` loaded on demand. Keep file references one level deep.

## Path/language-scoped rules

See `scoped-rules.md` — this concern has enough detail to warrant its own file.

## Subagents

**Claude Code:** `.claude/agents/<n>.md` (project) or `~/.claude/agents/<n>.md` (user). **Identity comes from the `name` frontmatter field**, which is required; the filename need not match.

Frontmatter fields: `name`, `description` (both required), `tools`, `disallowedTools`, `model`, `permissionMode`, `maxTurns`, `skills`, `mcpServers`, `hooks`, `memory`, `background`, `effort`, `isolation`, `color`, `initialPrompt`. `model` accepts `sonnet` / `opus` / `haiku` / `fable`, a full model ID, or `inherit`.

**OpenCode 2:** `.opencode/agents/<n>.md` or `~/.config/opencode/agents/<n>.md`, or inline under `agents` in `opencode.json`. **Identity comes from the path** (`review.md` → `review`, `team/review.md` → `team/review`).

V2 frontmatter fields (the same as an `agents` config entry): `description`, `mode` (`primary` / `subagent` / `all`, **default `primary`** for a new custom agent), `model` (`provider/model-id#variant`), `permissions` (ordered list), `steps`, `hidden`, `disabled`, `color`; `system` in JSON only, since the file body is the prompt. Sampling (`temperature`, `top_p`) now lives on a model variant, not the agent.

**Notes:** a file with any of `permissions`, `request`, `system` or `disabled` is read as V2 and its V1 fields (`name`, `tools`, `permission`, `temperature`) are ignored with only a log warning; other files are read as V1 and still translated. A file that fails to parse doesn't appear in the agent list. `opencode debug agents` shows what was loaded.

## Slash commands

**Claude Code:** `.claude/commands/<n>.md`. As of 2026 commands and skills are merged — `.claude/commands/deploy.md` and `.claude/skills/deploy/SKILL.md` both produce `/deploy`, and **the skill wins if both exist.** Frontmatter: `description`, `allowed-tools`, `argument-hint`, `model`, `disable-model-invocation`, `arguments`. Placeholders: `$ARGUMENTS`, `$0`/`$1`, `` !`cmd` `` for bash, `@` for files.

**OpenCode 2:** `.opencode/commands/<n>.md` or `~/.config/opencode/commands/<n>.md`, or a `commands` block in `opencode.json`. The path is the command name (`team/review.md` → `/team/review`). Frontmatter: `description`, `agent`, `model` (`provider/model#variant`), `subagent` (boolean; runs it in a background child session — `subtask` is the deprecated V1 alias). Placeholders: `$ARGUMENTS`, `!` bash injection, `@` file references.

The template bodies and placeholder conventions are close enough to copy; the frontmatter is not.

## MCP servers

See `mcp-translation.md`.

## Permissions

**Claude Code:** `permissions` block in `.claude/settings.json`, `.claude/settings.local.json`, or `~/.claude/settings.json`. Arrays `allow` / `deny` / `ask`, plus `defaultMode` and `additionalDirectories`. Rule syntax is `Tool(specifier)`: `Bash(npm run test:*)`, `Read(./.env)`, `Edit(src/**)`.

**Precedence: `deny` > `ask` > `allow`. Deny always wins**, regardless of order or specificity. Rules merge across settings scopes.

**OpenCode 2:** a `permissions` array in `opencode.json` or agent frontmatter, each rule `{ "action", "resource", "effect" }`. Actions: `read`, `edit` (covers write and patch), `shell` (V1 `bash`), `glob`, `grep`, `subagent` (V1 `task`), `skill`, `webfetch`, `websearch`, `question`, `external_directory`, and `<server>_<tool>` for MCP tools. Resources are whole-value wildcards: `*` matches any characters including `/`, `?` one character; edit paths are relative to the project. A shell pattern ending in ` *` also matches the bare command.

**Precedence: last matching rule wins**, with defaults first, then global, then project, then the agent's own rules appended. Put the broad rule first and exceptions after. An operation touching several resources is denied if any is denied. The base policy allows everything except asking for `.env` reads, and **`external_directory` is allowed by default in V2** (V1 asked). V1's `permission` object is still read and normalized.

These two models are inverted. Never translate one to the other mechanically.

## Hooks and plugins

**Claude Code:** `hooks` block in `settings.json`. Events include `PreToolUse`, `PostToolUse`, `UserPromptSubmit`, `SessionStart`, `SessionEnd`, `Stop`, `StopFailure`, `SubagentStart`, `SubagentStop`, `PreCompact`, `PostCompact`, `Notification`, `InstructionsLoaded`, `ConfigChange`. Declarative — shell commands, HTTP calls, or prompts, with matcher syntax. Also a full plugin/marketplace system (`.claude-plugin/plugin.json`, with `commands/`, `agents/`, `skills/`, `hooks/hooks.json`, `.mcp.json` at plugin root).

**OpenCode 2:** JS/TS plugins in `.opencode/plugins/` and `~/.config/opencode/plugins/` (discovered automatically), or npm packages and directories listed under `plugins` in config (`"name@1.2.3"` pins a version; a local path must be a directory). **V2 has a new plugin API: V1 plugins do not run.** The desktop app loads plugins from the service it runs, so put plugins meant for every window in the global config.

No translation path. Author separately.

## Model selection

**Claude Code:** the `model` setting is read once at session start; `/model` switches mid-session. Subagents accept aliases (`sonnet`, `opus`, `haiku`, `fable`), full model IDs, or `inherit`.

**OpenCode 2:** `model: "provider/model-id"`, with an optional variant after `#` (`anthropic/claude-sonnet-5-5#high`), plus per-agent `model`. V1's `small_model` maps to the built-in `title` agent (`agents.title.model`).

## Formatter / LSP

**OpenCode 2:** `formatter` in `opencode.json` works as before. `lsp` is accepted and kept but **not run**: V2 starts no language servers and gives no LSP diagnostics, so use the project's lint, typecheck or compile commands instead.

**Claude Code:** no equivalent config surface. Formatting is handled via a `PostToolUse` hook or IDE integration. (This is inferred from the absence of such keys, not an explicit statement that it's unsupported.)

## OpenCode 1 → 2

OpenCode 2 reads the same config locations and normalizes most V1 syntax in memory, so a V1 setup keeps loading. Write new config in the native V2 form; mixing is allowed at the top level, and a valid V2 value wins over a V1 one for the same setting.

| V1 | V2 |
|---|---|
| `permission: { bash: {...}, edit: "allow" }` | `permissions: [{ action, resource, effect }, ...]`; `bash` → `shell`, `task` → `subagent`, `write`/`patch` → `edit` |
| `agent` / `mode` maps | `agents`; `prompt` → `system`, `disable` → `disabled`, `maxSteps` → `steps`, `variant` joins the model as `#variant` |
| `command`, `subtask` | `commands`, `subagent` |
| `plugin` | `plugins` — and the plugin code itself must use the V2 API |
| `mcp.<name>` with `enabled`, numeric `timeout`, camelCase OAuth | `mcp.servers.<name>` with `disabled`, `timeout: { catalog, execution }`, snake_case OAuth |
| `skills: { paths, urls }` | `skills: [ ... ]` |
| `provider`, `reference`, `snapshot`, `attachment` | `providers`, `references`, `snapshots`, `media` |
| `CLAUDE.md` fallback (project and global) | removed — `AGENTS.md` only |
| `external_directory` asks | allowed by default |
| new agent defaults to `mode: all` | defaults to `primary` |
| `lsp` runs language servers | accepted, not run |
| todos on | off unless `experimental.todo` |

Ignored with a warning: `logLevel` (use `OPENCODE_LOG_LEVEL`), `server`, top-level `subagent_depth` (use `experimental.subagent_depth`), `compaction.tail_turns` / `prune`, and an MCP entry without a `type`. Source: <https://opencode.ai/docs/migrate-v1/>.

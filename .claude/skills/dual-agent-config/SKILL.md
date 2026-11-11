---
name: dual-agent-config
description: Configure a repo so Claude Code and OpenCode 2 (CLI and desktop app) read the same rules, skills, subagents, and MCP servers without duplicated or drifting files. Use whenever work touches AGENTS.md, CLAUDE.md, .claude/, .opencode/, opencode.json, .mcp.json, or settings.json; when adding per-language or per-framework rules (the Copilot .instructions.md equivalent); when porting a skill, subagent, command, or MCP server between the two tools; when bootstrapping a repo for dual-agent use; or when debugging why one agent follows conventions the other ignores. Applies even when only one of the two tools is named.
metadata:
  docs-verified: "2026-11-11"
---

# Claude Code + OpenCode unified config

The two tools overlap enough that most configuration can live in one place, but they diverge in ways that fail **silently** — no error, just an agent quietly running on a different rulebook. This skill covers what to genuinely share, what must be maintained twice, and how to translate between formats.

It targets **OpenCode 2** (the CLI and the desktop app, which read the same config). OpenCode 2 still reads most OpenCode 1 syntax and normalizes it in memory, so an old config keeps working, but write the native V2 form: `permissions`, `agents`, `commands`, `plugins`, `mcp.servers`. The differences that bite are in `references/compatibility-matrix.md` under "OpenCode 1 → 2".

Both tools ship weekly. Every claim here was verified against official docs on the date in the frontmatter. When the user reports behavior that contradicts this skill, trust the user and check `https://opencode.ai/docs/` and `https://code.claude.com/docs/en/` before debugging further.

## The compatibility map

Read this before touching anything — it determines whether a task is "point both tools at one file" or "author two files deliberately."

| Concern | Claude Code | OpenCode | Share? |
|---|---|---|---|
| Project instructions | `CLAUDE.md` or `.claude/CLAUDE.md` | `AGENTS.md` | **Yes** — import shim |
| Global instructions | `~/.claude/CLAUDE.md` | `~/.config/opencode/AGENTS.md` (no `CLAUDE.md` fallback in V2) | **Yes** — import shim |
| Skills | `.claude/skills/<name>/SKILL.md` | reads `.claude/skills/` natively | **Yes** — no work |
| Path/language-scoped rules | `.claude/rules/*.md` with `paths:` (lazy) | no glob rules; nested `AGENTS.md` per directory (lazy) or `instructions` (eager) | **Partly** — see below |
| Subagents | `.claude/agents/<n>.md` | `.opencode/agents/<n>.md` | Body yes, frontmatter no |
| Slash commands | `.claude/commands/` (or a skill) | `.opencode/commands/` | Body yes, frontmatter no |
| MCP servers | `.mcp.json` → `mcpServers` | `opencode.json(c)` → `mcp.servers` | **No** — translate |
| Permissions | `.claude/settings.json` → `permissions` | `opencode.json` → `permissions` (ordered array) | **No** — opposite precedence |
| Hooks | `.claude/settings.json` → `hooks` | plugins (`plugins` key, `.opencode/plugins/`; V2 API) | **No** — different models |
| Model selection | aliases (`sonnet`, `opus`, `inherit`) | `provider/model-id` | **No** |
| Formatter / LSP | none (use hooks) | `formatter` in config; `lsp` accepted but not run in V2 | **No** |

Full per-row detail — exact paths, complete frontmatter field lists, precedence rules — is in `references/compatibility-matrix.md`. Read it before making claims about a field name.

### The four differences that cause most breakage

1. **Neither tool reads the other's rules file.** Claude Code **never** reads `AGENTS.md`, and OpenCode 2 **never** reads `CLAUDE.md` (OpenCode 1 fell back to it; V2 dropped that, globally too). A repo with only one of the two files leaves one tool running blind.
2. **Glob-scoped rules exist in exactly one tool.** Claude Code has `.claude/rules/` with `paths:`. OpenCode 2 scopes only by directory (nested `AGENTS.md`, loaded when the agent reads files there). This is the single biggest gap.
3. **Permission precedence is inverted.** Claude Code: deny always wins regardless of order. OpenCode: an ordered `permissions` array where the last matching rule wins, so the broad rule must come first. Copying one to the other inverts the intent.
4. **An OpenCode agent file without `mode: subagent` is a primary agent in V2** (V1 defaulted to `all`). It shows up in the agent switcher and is never offered for delegation, with no warning.

## Task: bootstrap a repo for both tools

Make `AGENTS.md` the source of truth; `CLAUDE.md` imports it.

1. If `CLAUDE.md` holds the real content: `git mv CLAUDE.md AGENTS.md`
2. Write a new `CLAUDE.md` containing exactly one line: `@AGENTS.md`
3. Verify both sides loaded it (see "Verifying it worked")

Same pattern globally: put content in `~/.config/opencode/AGENTS.md`, and `~/.claude/CLAUDE.md` contains `@~/.config/opencode/AGENTS.md`. OpenCode 2 has no fallback to `~/.claude/CLAUDE.md`, so a setup that relied on it in V1 must move its content into `~/.config/opencode/AGENTS.md`.

Two things to flag, because both surprise people later:

- **Imports expand inline.** They remove the maintenance burden, not the token cost. To shrink context, move instructions into skills or path-scoped rules instead.
- **Symlinking works too** but needs Administrator privileges or Developer Mode on Windows. Prefer the import unless the user wants a symlink specifically.
- **`@import` has a depth limit of 4 hops**, skips paths inside code blocks and backticks, and prompts once for approval on paths resolving outside the working directory.

## Task: add language- or framework-scoped rules

This is the dual-agent answer to Copilot's `.instructions.md` + `applyTo`. It is the request most likely to be answered wrongly, so be careful here.

**These are rules, not skills.** A skill is an on-demand procedure Claude invokes for a described task. Per-language conventions are passive context that should be present whenever a matching file is touched. If someone asks for "rules per language," the deliverable is rule files — reach for a skill only if they also want a runnable procedure.

### Claude Code — native support

`.claude/rules/*.md`, discovered recursively. The frontmatter key is **`paths`** (a YAML list of globs):

```markdown
---
paths:
  - "**/*.java"
  - "src/**/*.{ts,tsx}"
---

# Java / Spring Boot conventions
- Never use `var` — explicit types always
```

- No `paths` field → loads unconditionally at launch, same priority as `CLAUDE.md`.
- With `paths` → enters context only when Claude reads a matching file.
- **`match:`, `applyTo:`, and `glob:` are not recognized.** A rule using one of those silently never triggers. Check this first when auditing a setup that "isn't working."
- Brace expansion is supported; the whole `paths` list shares a budget of 1,000 expanded patterns / 4 MiB.
- User-level `~/.claude/rules/` loads *before* project rules, so project rules win on conflict. **Known bug: `paths:` in user-level rules may be ignored entirely** — put anything that must work at project level.

Full glob semantics, version-dependent behavior, and a worked three-language example are in `references/scoped-rules.md`.

### OpenCode 2 — scoping by directory only

Verify this before repeating it, then state it plainly. As of the frontmatter date, OpenCode has **no** glob- or extension-triggered rules: the "Smart Rules" proposal (issue #10096 / PR #10090) was closed without merging, and a follow-up request (#29405) was closed as not planned. Blog posts that describe `.opencode/rules/` with `paths:`/`alwaysApply:` and an `OPENCODE_EXPERIMENTAL_SMART_RULES` flag are copying that proposal. It does not work; do not put it in a user's config.

Two real options:

1. **Nested `AGENTS.md` per directory, loaded lazily.** OpenCode 2 loads the `AGENTS.md` files from the workspace up to the project root at start, and adds a nested one the first time the agent reads or lists something below it. That is real lazy scoping, by directory subtree rather than file extension. Best when languages already map to directories (a Java backend, a TS frontend). Claude Code loads nested `CLAUDE.md` files the same way, so `dir/AGENTS.md` plus a one-line `dir/CLAUDE.md` (`@AGENTS.md`) scopes one file for both tools. Not inside a Unity `Assets/` folder or similar, where the engine imports every file.
2. **The `instructions` array in `opencode.json`** — paths, globs and URLs, loaded after `AGENTS.md`. It can point at the same `.claude/rules/*.md` files (OpenCode reads them as plain Markdown and ignores the frontmatter). But it is **eager**: everything loads every session. Say so rather than implying parity with Claude Code's lazy loading.

A third-party plugin (`frap129/opencode-rules`) added Cursor-style conditional rules that read `.claude/rules/`. It was written for the OpenCode 1 plugin API, which OpenCode 2 does not run, so check that it has a V2 release before suggesting it, flag that it is unaffiliated, and let the user decide.

**Do not try to unify these into one mechanism.** The loading models are genuinely different. Author each side deliberately, the same way you would MCP servers.

## Task: share a skill between both tools

Nothing to migrate — OpenCode 2 still discovers `.claude/skills/` and `~/.claude/skills/` alongside its own locations. Moving files to `.opencode/skills/` would only break Claude Code. When the same skill ID is in several places, OpenCode loads the one registered last, and `.opencode/skills/` is registered after `.claude/skills/` and `.agents/skills/`, so a stray copy there silently wins.

The one real constraint is frontmatter. Both tools honor the six portable Agent Skills fields — `name`, `description`, `license`, `compatibility`, `metadata`, `allowed-tools`. OpenCode **silently ignores** everything else, and Claude Code's own extensions (`paths`, `context`, `argument-hint`, `disable-model-invocation`, …) cause hard failures when packaging a skill for claude.ai or the Skills API. For a skill meant to work in both places, stay inside the six fields.

Also: `SKILL.md` must be uppercase, the `name` must match the parent directory name, and skill names must be unique across every discovery location or one silently wins. OpenCode 2 takes the skill ID from the folder and treats `name` only as a display label, without enforcing either rule, so a mismatch shows up in Claude Code first.

### Package managers are the exception that bites

A package manager that installs "for both tools" will usually deploy a second copy. APM (Agent Package Manager) is the common case: every target except `claude` converges on `.agents/skills/<name>/SKILL.md`, so `targets: [claude, opencode]` leaves the same skill in `.claude/skills/` and `.agents/skills/` — two discovery locations, one winner, silently. OpenPackage does the same thing under `.opencode/skills/` when it redetects platforms.

- **APM:** declare `targets: [claude]` in `apm.yml`. Manifest targets outrank filesystem auto-detection, so a repository that also has `.opencode/` still gets one copy, and `apm install` / `apm update` stay safe without flags. Do not run `apm compile` for a skills-only package.
- **OpenPackage:** install each skill by subpath with `--platforms claude --force`; never run bare `opkg install`.
- **Either way:** `check_config.py` fails on `.opencode/skills/`, on `.agents/skills/`, on a skill nested below `.claude/skills/<name>/`, and on an `apm.yml` that names any target other than `claude`.

## Task: port a subagent

**The system-prompt body is fully portable — copy it unchanged.** Only frontmatter differs, and the field names are not analogous:

| | Claude Code | OpenCode |
|---|---|---|
| Identity | `name:` frontmatter (required) | **the filename** |
| Required | `name`, `description` | `description` |
| Restrict tools | `tools` / `disallowedTools` lists | `permissions` (ordered list of `action`/`resource`/`effect`) |
| Kind | always a subagent | `mode: subagent` — **required**; V2 defaults a new agent to `primary` |
| Turn cap | `maxTurns` | `steps` |
| Model | `sonnet` / `opus` / `haiku` / `fable` / `inherit` | `provider/model-id#variant` |

Port from Claude Code by dropping `name:` and naming the file after it; port the other way by adding `name:` back. Write OpenCode frontmatter entirely in V2 fields: a file with any of `permissions`, `system`, `request` or `disabled` is read as V2, and V1 fields in it (`tools`, `permission`, `temperature`, `name`) are ignored. Set `model:` for the target rather than copying it — running two tools usually means routing to different models, so ask which if it isn't obvious. Field-by-field mapping and both worked examples: `references/subagent-translation.md`.

## Task: add or port an MCP server

Mechanical translation, but with four traps: OpenCode 2 puts servers under **`mcp.servers`** (V1 put them directly under `mcp`); it uses a single `command` **array** where Claude Code splits `command`/`args`; its env key is **`environment`**, not `env`; and the type vocabularies differ (`local`/`remote` vs `stdio`/`http`/`sse`/`ws`). OAuth keys are snake_case in V2 (`client_id`, `callback_port`) and camelCase in Claude Code.

Use the script rather than hand-editing anything non-trivial — it writes the V2 shape (and moves existing V1 entries under `mcp.servers`), handles the array split and OAuth key names, reads `opencode.jsonc`, and warns about env-var expansion syntax, which is incompatible between the tools. A `.jsonc` target is preview-only, since rewriting it would drop its comments:

```bash
# preview only (default — nothing is written)
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json

# apply, merging into existing config (backs up to <target>.bak)
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json --write
```

`to-claude` runs the reverse. Full field mapping and verified schema examples: `references/mcp-translation.md`.

Two gotchas worth stating up front: a Claude Code entry with a `url` but no `type` is misread as stdio and skipped with a warning, and `mcpServers` is **not** valid inside `settings.json` — it belongs in `.mcp.json` or `~/.claude.json`.

## What not to unify

Resist making permissions and hooks shareable. The syntaxes differ, the precedence models are *inverted* (see difference 3), and more to the point the setups *should* differ: people run two tools precisely because they route to different-cost models, and a cheap model doing routine work warrants different gates than a frontier model touching production-adjacent code. Conversion layers here are fragile and solve nothing.

If the user pushes to unify anyway, explain the reasoning rather than declining — then do it if they still want it. It's their setup.

## Task: audit an existing setup

Run the bundled checker first:

```bash
python3 .claude/skills/dual-agent-config/scripts/check_config.py
```

Add `--strict-portable` when every skill in the repository should package for claude.ai and OpenCode too. The checker verifies one rulebook, one skill copy, skill names and references, scoped-rule keys, MCP parity (V1 and V2 shapes, `.json` and `.jsonc`), OpenCode 1 keys that have a native V2 name, and OpenCode agent files that are missing `mode` or mix V1 fields into V2 frontmatter. If a harness-specific skill fails only the portable check, leave it strict-off and fix the other failures.

When someone reports "one agent isn't following my rules," walk this in order and stop at the first hit:

1. Does `AGENTS.md` exist without a `CLAUDE.md` importing it? → Claude Code is blind.
2. Does `CLAUDE.md` hold real content (project or `~/.claude/CLAUDE.md`)? → OpenCode 2 never reads it.
3. Do any `.claude/rules/` files use `match:`/`applyTo:` instead of `paths:`? → silently never fire.
4. Is a path-scoped rule sitting in `~/.claude/rules/` rather than the project? → may be ignored (known bug).
5. Is `OPENCODE_DISABLE_PROJECT_CONFIG` set? → OpenCode skips project `AGENTS.md` and config. (V1 also had `OPENCODE_DISABLE_CLAUDE_CODE` and its `_PROMPT` / `_SKILLS` variants; the V2 docs no longer mention them, so check the environment for both.)
6. Do skills carry non-spec frontmatter that OpenCode drops?
7. Is the same skill installed twice — `.opencode/skills/`, `.agents/skills/`, or a namespaced folder below `.claude/skills/`? → one copy silently wins.
8. Do subagents exist in only one of `.claude/agents/` and `.opencode/agents/`, or does an OpenCode agent lack `mode: subagent`? → it is a primary agent in V2 and never delegated to.
9. Are MCP servers in `.mcp.json` but missing from OpenCode's `mcp.servers`, or vice versa?
10. Does an OpenCode plugin still use the V1 API, or sit under the V1 `plugin` key? → V1 plugin code does not run in OpenCode 2.

The full catalogue of silent failure modes in both tools is in `references/gotchas.md`. Consult it whenever something "should work but doesn't."

## Verifying it worked

Never declare a config change done without checking both sides — this is exactly the class of change that fails quietly.

- **Claude Code**: `/context` shows `CLAUDE.md` under memory files with the imported content present. `/memory` lists loaded rules.
- **OpenCode** (CLI and desktop app share the config): `opencode debug config` lists every config source it loaded with its parsed content, and `opencode debug agents` lists the agents with their resolved models and permissions. Both run without a model call. Then start a session and ask for the first heading of `AGENTS.md`.
- **OpenCode desktop app**: it runs the same service, so the checks above cover it. Plugins it should load in every window belong in the global `~/.config/opencode/opencode.json(c)`.
- **Rules**: edit a file matching a rule's glob and confirm the response reflects that rule. For a nested `AGENTS.md`, have OpenCode read a file in that directory and confirm the guidance appears.
- **MCP**: list servers in each tool and confirm the same names appear in both.
- **Skills**: run with `--debug` in Claude Code to surface frontmatter parse errors, which otherwise load as empty metadata and silently stop auto-invocation.

If the user can't run these right now, say plainly which checks are outstanding rather than implying the setup is verified.

## Reference layout

```
repo/
├── AGENTS.md            ← source of truth
├── CLAUDE.md            ← "@AGENTS.md"
├── opencode.json        ← OpenCode models, agents, permissions, plugins, mcp.servers, instructions
├── .mcp.json            ← Claude Code MCP servers
├── .claude/
│   ├── settings.json    ← Claude Code permissions + hooks
│   ├── agents/          ← Claude Code subagents
│   ├── commands/        ← Claude Code slash commands
│   ├── skills/          ← shared — both tools read this
│   └── rules/           ← Claude Code only — path-scoped (paths: frontmatter)
└── .opencode/
    ├── agents/          ← OpenCode subagents (same bodies, different frontmatter)
    ├── commands/        ← OpenCode slash commands
    └── plugins/         ← OpenCode hooks equivalent (V2 plugin API)
```

Plural directory names are canonical for OpenCode; singular (`agent/`, `command/`, `mode/`) still works for backward compatibility, so don't "fix" an existing singular setup unless asked. A file under `mode/` is a primary agent.

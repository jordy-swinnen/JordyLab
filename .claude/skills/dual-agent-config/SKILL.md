---
name: dual-agent-config
description: Configure a repo so Claude Code and OpenCode read the same rules, skills, subagents, and MCP servers without duplicated or drifting files. Use whenever work touches AGENTS.md, CLAUDE.md, .claude/, .opencode/, opencode.json, .mcp.json, or settings.json; when adding per-language or per-framework rules (the Copilot .instructions.md equivalent); when porting a skill, subagent, command, or MCP server between the two tools; when bootstrapping a repo for dual-agent use; or when debugging why one agent follows conventions the other ignores. Applies even when only one of the two tools is named.
metadata:
  docs-verified: "2026-08-05"
---

# Claude Code + OpenCode unified config

The two tools overlap enough that most configuration can live in one place, but they diverge in ways that fail **silently** — no error, just an agent quietly running on a different rulebook. This skill covers what to genuinely share, what must be maintained twice, and how to translate between formats.

Both tools ship weekly. Every claim here was verified against official docs on the date in the frontmatter. When the user reports behavior that contradicts this skill, trust the user and check `https://opencode.ai/docs/` and `https://code.claude.com/docs/en/` before debugging further.

## The compatibility map

Read this before touching anything — it determines whether a task is "point both tools at one file" or "author two files deliberately."

| Concern | Claude Code | OpenCode | Share? |
|---|---|---|---|
| Project instructions | `CLAUDE.md` or `.claude/CLAUDE.md` | `AGENTS.md` | **Yes** — import shim |
| Global instructions | `~/.claude/CLAUDE.md` | `~/.config/opencode/AGENTS.md` (falls back to `~/.claude/CLAUDE.md`) | **Yes** — fallback |
| Skills | `.claude/skills/<name>/SKILL.md` | reads `.claude/skills/` natively | **Yes** — no work |
| Path/language-scoped rules | `.claude/rules/*.md` with `paths:` (lazy) | **no native equivalent** | **No** — see below |
| Subagents | `.claude/agents/<n>.md` | `.opencode/agents/<n>.md` | Body yes, frontmatter no |
| Slash commands | `.claude/commands/` (or a skill) | `.opencode/commands/` | Body yes, frontmatter no |
| MCP servers | `.mcp.json` → `mcpServers` | `opencode.json` → `mcp` | **No** — translate |
| Permissions | `.claude/settings.json` → `permissions` | `opencode.json` → `permission` | **No** — opposite precedence |
| Hooks | `.claude/settings.json` → `hooks` | plugin system (JS/TS) | **No** — different models |
| Model selection | aliases (`sonnet`, `opus`, `inherit`) | `provider/model-id` | **No** |
| Formatter / LSP | none (use hooks) | `formatter` / `lsp` in config | **No** |

Full per-row detail — exact paths, complete frontmatter field lists, precedence rules — is in `references/compatibility-matrix.md`. Read it before making claims about a field name.

### The four asymmetries that cause most breakage

1. **The fallback runs one way only.** OpenCode reads `CLAUDE.md` when no `AGENTS.md` exists. Claude Code **never** reads `AGENTS.md`. A repo with only `AGENTS.md` means Claude Code is running blind.
2. **When both files exist, OpenCode uses `AGENTS.md` and ignores `CLAUDE.md` entirely.** So "keep both in sync by hand" degrades the moment they drift.
3. **Path-scoped rules exist in exactly one tool.** Claude Code has `.claude/rules/`. OpenCode has nothing equivalent. This is the single biggest gap.
4. **Permission precedence is inverted.** Claude Code: deny always wins regardless of order. OpenCode: last matching rule wins, so `"*"` must come first. Copying one syntax to the other inverts the intent.

## Task: bootstrap a repo for both tools

Make `AGENTS.md` the source of truth; `CLAUDE.md` imports it.

1. If `CLAUDE.md` holds the real content: `git mv CLAUDE.md AGENTS.md`
2. Write a new `CLAUDE.md` containing exactly one line: `@AGENTS.md`
3. Verify both sides loaded it (see "Verifying it worked")

Same pattern globally: put content in `~/.config/opencode/AGENTS.md`, and `~/.claude/CLAUDE.md` contains `@~/.config/opencode/AGENTS.md`. (Or exploit the fallback: keep only `~/.claude/CLAUDE.md` and OpenCode reads it directly — simpler, but breaks the moment someone creates the OpenCode-native file.)

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

### OpenCode — no native equivalent

Verify this claim is still true before repeating it, then state it plainly. As of the frontmatter date, OpenCode has **not** shipped path-scoped or glob-triggered rules: the "Smart Rules" proposal (issue #10096 / PR #10090) is unmerged, and a follow-up request (#29405) was closed as not planned. Several blog posts describe `.opencode/rules/` with `paths:`/`alwaysApply:` and an `OPENCODE_EXPERIMENTAL_SMART_RULES` flag — that is copied from the unmerged proposal and does not work. Do not put it in a user's config.

Two real options:

1. **Nested `AGENTS.md` per directory** — OpenCode combines project and global rules additively. Best when languages already map to directories (a Java backend, a TS frontend, a Python sidecar). Scopes by directory subtree, not by file extension.
2. **The `instructions` array in `opencode.json`** — accepts paths, globs over instruction-file locations, and remote URLs. It can even point at the same `.claude/rules/*.md` files (OpenCode reads them as plain Markdown and ignores the frontmatter). But it is **eager**: everything loads every session. Say so rather than implying parity with Claude Code's lazy loading.

A third-party plugin (`frap129/opencode-rules`) does add Cursor-style conditional rules and reads `.claude/rules/`. Mention it as an option, flag that it is unaffiliated with the official project, and let the user decide.

**Do not try to unify these into one mechanism.** The loading models are genuinely different. Author each side deliberately, the same way you would MCP servers.

## Task: share a skill between both tools

Nothing to migrate — OpenCode natively discovers `.claude/skills/` and `~/.claude/skills/` alongside its own locations. Moving files to `.opencode/skills/` would only break Claude Code.

The one real constraint is frontmatter. Both tools honor the six portable Agent Skills fields — `name`, `description`, `license`, `compatibility`, `metadata`, `allowed-tools`. OpenCode **silently ignores** everything else, and Claude Code's own extensions (`paths`, `context`, `argument-hint`, `disable-model-invocation`, …) cause hard failures when packaging a skill for claude.ai or the Skills API. For a skill meant to work in both places, stay inside the six fields.

Also: `SKILL.md` must be uppercase, the `name` must match the parent directory name, and skill names must be unique across every discovery location or one silently wins.

## Task: port a subagent

**The system-prompt body is fully portable — copy it unchanged.** Only frontmatter differs, and the field names are not analogous:

| | Claude Code | OpenCode |
|---|---|---|
| Identity | `name:` frontmatter (required) | **the filename** |
| Required | `name`, `description` | `description` |
| Restrict tools | `tools` / `disallowedTools` lists | `permission` object |
| Autonomy | `permissionMode` | `mode` (`primary`/`subagent`/`all`) |
| Turn cap | `maxTurns` | `steps` (`maxSteps` is deprecated) |

Port from Claude Code by dropping `name:` and naming the file after it; port the other way by adding `name:` back. Set `model:` for the target rather than copying it — running two tools usually means routing to different models, so ask which if it isn't obvious. Field-by-field mapping and both worked examples: `references/subagent-translation.md`.

## Task: add or port an MCP server

Mechanical translation, but with three traps: OpenCode uses a single `command` **array** where Claude Code splits `command`/`args`; OpenCode's env key is **`environment`**, not `env`; and the type vocabularies differ (`local`/`remote` vs `stdio`/`http`/`sse`/`ws`).

Use the script rather than hand-editing anything non-trivial — it handles the array split and warns about env-var expansion syntax, which is incompatible between the tools:

```bash
# preview only (default — nothing is written)
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json

# apply, merging into existing config (backs up to <target>.bak)
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json --write
```

`to-claude` runs the reverse. Full field mapping and verified schema examples: `references/mcp-translation.md`.

Two gotchas worth stating up front: a Claude Code entry with a `url` but no `type` is misread as stdio and skipped with a warning, and `mcpServers` is **not** valid inside `settings.json` — it belongs in `.mcp.json` or `~/.claude.json`.

## What not to unify

Resist making permissions and hooks shareable. The syntaxes differ, the precedence models are *inverted* (see asymmetry 4), and more to the point the setups *should* differ: people run two tools precisely because they route to different-cost models, and a cheap model doing routine work warrants different gates than a frontier model touching production-adjacent code. Conversion layers here are fragile and solve nothing.

If the user pushes to unify anyway, explain the reasoning rather than declining — then do it if they still want it. It's their setup.

## Task: audit an existing setup

When someone reports "one agent isn't following my rules," walk this in order and stop at the first hit:

1. Does `AGENTS.md` exist without a `CLAUDE.md` importing it? → Claude Code is blind.
2. Does `CLAUDE.md` hold real content while `AGENTS.md` also exists? → OpenCode is ignoring it.
3. Do any `.claude/rules/` files use `match:`/`applyTo:` instead of `paths:`? → silently never fire.
4. Is a path-scoped rule sitting in `~/.claude/rules/` rather than the project? → may be ignored (known bug).
5. Is `OPENCODE_DISABLE_CLAUDE_CODE` (or the `_PROMPT` / `_SKILLS` variants) set in the environment? → shared `.claude` files silently stop loading in OpenCode.
6. Do skills carry non-spec frontmatter that OpenCode drops?
7. Do subagents exist in only one of `.claude/agents/` and `.opencode/agents/`?
8. Are MCP servers in `.mcp.json` but missing from `opencode.json`, or vice versa?

The full catalogue of silent failure modes in both tools is in `references/gotchas.md`. Consult it whenever something "should work but doesn't."

## Verifying it worked

Never declare a config change done without checking both sides — this is exactly the class of change that fails quietly.

- **Claude Code**: `/context` shows `CLAUDE.md` under memory files with the imported content present. `/memory` lists loaded rules.
- **OpenCode**: start a session and confirm it reports reading `AGENTS.md`.
- **Rules**: edit a file matching a rule's glob and confirm the response reflects that rule. OpenCode has no lazy load to verify — instead confirm the right `AGENTS.md` content is present in a session started from that directory.
- **MCP**: list servers in each tool and confirm the same names appear in both.
- **Skills**: run with `--debug` in Claude Code to surface frontmatter parse errors, which otherwise load as empty metadata and silently stop auto-invocation.

If the user can't run these right now, say plainly which checks are outstanding rather than implying the setup is verified.

## Reference layout

```
repo/
├── AGENTS.md            ← source of truth
├── CLAUDE.md            ← "@AGENTS.md"
├── opencode.json        ← OpenCode models, permission, mcp, instructions
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
    └── plugins/         ← OpenCode hooks equivalent
```

Plural directory names are canonical for OpenCode; singular (`agent/`, `command/`) still works for backward compatibility, so don't "fix" an existing singular setup unless asked.

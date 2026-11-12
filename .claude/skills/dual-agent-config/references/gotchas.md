# Silent failure modes

Config problems in both tools overwhelmingly fail without an error message. This is the catalogue to consult when something "should work but doesn't."

## OpenCode

**Instruction loading**
- OpenCode 2 reads `AGENTS.md` only. `CLAUDE.md`, project or global, is never read; V1's fallback is gone. Rules that only live in `CLAUDE.md` are invisible to OpenCode.
- Nested `AGENTS.md` files load when the agent first reads or lists something in their directory, not at start. After compaction drops that entry, they reload on the next read there.
- `OPENCODE_DISABLE_PROJECT_CONFIG=1` skips project `AGENTS.md` and config. V1's `OPENCODE_DISABLE_CLAUDE_CODE` (and `_PROMPT` / `_SKILLS`) switched off `.claude` support; the V2 docs no longer mention them, so check the environment for both before debugging further.

**Skills**
- The folder is the skill ID; `name` is only a display label, and V2 enforces neither the name pattern nor a match with the folder. Unknown frontmatter keys are dropped without warning.
- Missing `description` means the skill is never advertised to the model. `metadata.opencode/autoinvoke: false` or `disable-model-invocation: true` hides it on purpose.
- The same ID in two places: the source registered last wins, and `.opencode/skills/` comes after `.claude/skills/`, so a stray copy there overrides the shared one.

**Agents**
- A new custom agent defaults to `mode: primary` in V2 (V1: `all`). Without `mode: subagent` it is never offered for delegation.
- A file with any of `permissions`, `request`, `system`, `disabled` is read as V2, and V1 fields in it (`name`, `tools`, `permission`, `temperature`) are ignored with only a log warning. Convert a file's frontmatter all at once.
- A markdown agent file that fails to parse simply doesn't appear in the agent list — no error. `opencode debug agents` shows what loaded.

**Permissions**
- The `permissions` array is ordered and the last match wins, so an exception placed *before* the broad rule is silently overridden. Broad rule first, exceptions after.
- `*` matches across `/`, so `Assets/*.unity` covers every depth. Edit resources are project-relative paths; `external_directory` uses absolute ones.
- V2 allows `external_directory` by default (V1 asked). Add `{ "action": "external_directory", "resource": "*", "effect": "ask" }` to get the old prompt back.
- V1 names (`bash`, `task`) are not V2 actions. In a native `permissions` array, write `shell` and `subagent`.

**Plugins**
- V1 plugin code does not run in V2, which has a new plugin API. A V1-only plugin loads nothing, and its hooks never fire.
- A local plugin entry must name a directory; a path to a single file is skipped.

**MCP**
- V2 servers sit under `mcp.servers`; a name directly under `mcp` is V1 syntax. An entry without a `type` is ignored with a warning.
- Servers add to every request's context. Large ones (GitHub's, for instance) can push a session past the context limit. Be deliberate about which are enabled.

**LSP**
- V2 accepts `lsp` config but runs no language servers. Workflows that relied on LSP diagnostics need the project's lint or compile command instead.

**Directory naming**
- Plural (`agents/`, `commands/`, `skills/`, `plugins/`) is canonical; singular is accepted for backward compatibility. Both work — don't "fix" a working singular setup. Files under `mode/` are primary agents.

## Claude Code

**Instruction loading**
- Claude Code never reads `AGENTS.md`. There is no fallback in this direction.
- `CLAUDE.md` is delivered as context, not enforcement — no compliance guarantee. Use `PreToolUse` hooks or `deny` permission rules for anything that must hold.
- `@import` has a max depth of 4 hops; parsing skips code spans and fenced blocks (backtick a path to keep it literal); external imports prompt once for approval, and declining disables them silently thereafter.
- `MEMORY.md` (auto memory) loads only the first 200 lines / 25 KB; the rest is dropped silently. `CLAUDE.md` itself loads in full — but longer files measurably reduce adherence.
- After `/compact`, project-root `CLAUDE.md` is re-injected; nested `CLAUDE.md` files and `paths:`-scoped rules are not, until a matching file is read again.

**Rules**
- `match:` / `applyTo:` / `glob:` are not recognized in place of `paths:` — the rule silently never fires.
- `paths:` in user-level `~/.claude/rules/` may be ignored entirely (known bug); project-level works.
- Rules do not support `@import`.
- Version-dependent: brace-heavy patterns could crash startup before v2.1.217; one invalid glob broke Read for all evaluated files before v2.1.207.

**Skills and commands**
- Malformed frontmatter loads with empty metadata: `/skill-name` still works manually, but with no description to match on, auto-invocation silently stops. Run with `--debug` to see the parse error.
- Non-spec frontmatter fields work inside Claude Code but hard-fail packaging for claude.ai or the Skills API with `Unexpected key(s) in SKILL.md frontmatter`.
- Combined `description` + `when_to_use` truncates at 1,536 characters in the listing; the whole listing is capped near 1% of the context window. With many skills installed, low-use ones lose their descriptions and stop triggering. Tune with `skillListingBudgetFraction` or `SLASH_COMMAND_TOOL_CHAR_BUDGET`.
- If both `.claude/commands/deploy.md` and `.claude/skills/deploy/SKILL.md` exist, the skill wins.

**MCP**
- An entry with a `url` but no `type` is read as stdio and skipped: `MCP server "<name>" has a "url" but no "type"`. Always set `type` for remote servers.
- Reserved server names (`workspace`, `claude-in-chrome`, `computer-use`, `Claude Preview`, `Claude Browser`) are skipped with a warning.
- `mcpServers` is **not** valid in `settings.json`. It belongs in `.mcp.json` (project) or `~/.claude.json` (user/local).

**Permissions**
- `deny` always wins over `ask` and `allow`, regardless of order or specificity. Rules merge across settings scopes, so a deny in `~/.claude/settings.json` overrides a project allow.

**Subagents**
- The default spawn depth for nested subagents changed repeatedly across 2026 releases (5 → 1 → 3). If a setup depends on nesting, pin a minimum version.

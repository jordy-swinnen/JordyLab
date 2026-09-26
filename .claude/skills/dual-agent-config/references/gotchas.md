# Silent failure modes

Config problems in both tools overwhelmingly fail without an error message. This is the catalogue to consult when something "should work but doesn't."

## OpenCode

**Instruction loading**
- If both `AGENTS.md` and `CLAUDE.md` exist, only `AGENTS.md` is used — first match wins per category. Same for `~/.config/opencode/AGENTS.md` over `~/.claude/CLAUDE.md`.
- Claude Code compatibility can be switched off by environment variable, at which point shared `.claude` files silently stop loading: `OPENCODE_DISABLE_CLAUDE_CODE=1` (all `.claude` support), `OPENCODE_DISABLE_CLAUDE_CODE_PROMPT=1` (only `~/.claude/CLAUDE.md`), `OPENCODE_DISABLE_CLAUDE_CODE_SKILLS=1` (only `.claude/skills`). Check the environment before debugging further.

**Skills**
- Only `name`, `description`, `license`, `compatibility`, `metadata` are recognized. Every other frontmatter key is dropped without warning.
- Missing `name` or `description` means the skill is never advertised to the model.
- `SKILL.md` must be uppercase; `name` must match the directory name; names must be unique across all discovery locations or one silently wins.

**Agents**
- A markdown agent file that fails to parse simply doesn't appear in the agent list — no error.
- `maxSteps` is deprecated in favor of `steps`; the boolean `tools` config is deprecated in favor of `permission` (still honored for back-compat).

**Permissions**
- Last matching rule wins, so a specific rule placed *before* `"*"` is silently overridden by the catch-all. Always put `"*"` first.
- Known issues: path-scoped `read` permissions not enforced when a catch-all is present (#13646); `edit` uses relative paths while `external_directory` uses absolute, breaking some agent-level path rules (#20045).

**MCP**
- Servers add to every request's context. Large ones (GitHub's, for instance) can push a session past the context limit. Be deliberate about which are enabled.

**Directory naming**
- Plural (`agents/`, `commands/`, `skills/`, `plugins/`) is canonical; singular is accepted for backward compatibility. Both work — don't "fix" a working singular setup.

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

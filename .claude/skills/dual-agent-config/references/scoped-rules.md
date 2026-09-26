# Path- and language-scoped rules

The dual-agent equivalent of Copilot's `.instructions.md` + `applyTo`. Claude Code has a native mechanism; OpenCode does not. This file covers both sides and ends with a worked three-language example.

## Claude Code: `.claude/rules/`

All `.md` files under `.claude/rules/` (project) and `~/.claude/rules/` (user) are discovered recursively, including subdirectories. No import line is needed — presence in the directory is enough.

### The `paths` frontmatter key

```markdown
---
paths:
  - "src/api/**/*.ts"
  - "src/**/*.{ts,tsx}"
---

# API development rules

- All endpoints must include input validation
- Use the standard error response format
```

- **No `paths` field** → loads unconditionally at launch, at the same priority as `.claude/CLAUDE.md`.
- **With `paths`** → enters context only when Claude *reads a file matching the pattern*. It does not trigger on every tool use.

### Correct key name

The key is **`paths`**. `match:`, `applyTo:` (Copilot's spelling), and `glob:` (Cursor-adjacent spellings) are not recognized and produce no error — the rule simply never fires. This is the most common reason a hand-written or LLM-generated rules setup does nothing.

### Glob semantics

- Standard globs: `**/*.ts`, `src/**/*`, `*.md`, `src/components/*.tsx`.
- **Brace expansion** is supported: `src/**/*.{ts,tsx}` expands to two patterns; `{a,b}/{c,d}/*.{ts,tsx}` to eight.
- The whole `paths` list of a single rule shares a budget of **1,000 expanded patterns and 4 MiB**. Patterns without braces don't count against it. A pattern that would exceed the budget is used *unexpanded*, meaning its literal braces match no files.
- `[` starts a bracket expression. A literal `[` in a filename must be escaped: `photos \[2024/**`. An unescapable `[` makes that one pattern match nothing; the rule's other patterns still work.
- Symlinks are supported — a shared rules folder can be linked into several repos (`ln -s ~/shared-rules/security.md .claude/rules/security.md`). Path matching also works when Claude reaches a file through a symlinked path into the project (v2.1.198+).

### Precedence and exclusion

User-level rules load *before* project rules, so project rules win on conflict. `claudeMdExcludes` (a glob list against absolute paths, settable at any settings layer, arrays merging across layers) can skip specific CLAUDE.md or rules files; managed-policy CLAUDE.md cannot be excluded.

### Limitations and version-dependent behavior

- **Rules do not support `@import`.** Each rule file must be self-contained. Shared content goes in `CLAUDE.md` (which does support imports) or via symlink.
- **Known bug: `paths:` in user-level `~/.claude/rules/` may be ignored** while working correctly at project level. Put anything that must work in the project's `.claude/rules/`.
- Before v2.1.217, many brace groups could stall or crash the CLI at startup.
- Before v2.1.207, one invalid glob made the Read tool fail for *every* file the rule was evaluated against, instead of matching nothing.
- After `/compact`, project-root `CLAUDE.md` is re-injected but path-scoped rules are not — they return only when a matching file is read again.

## OpenCode: no native equivalent

**Status as of this skill's `docs-verified` date.** Re-check before repeating, since this is exactly the kind of thing that ships.

The official rules documentation contains no mention of `.opencode/rules/`, a `paths:` field, or any glob-triggered conditional loading. The "Smart Rules" proposal (GitHub issue #10096, implemented in PR #10090, gated behind `OPENCODE_EXPERIMENTAL_SMART_RULES`) is **open and unmerged**; a related PR #18903 is likewise unmerged; and a follow-up request for `.opencode/rules` support (#29405) was **closed as "not planned."**

Numerous blog posts describe this feature as if it works. They are transcribing the unmerged proposal. Do not put it in a user's config.

### Workaround 1 — nested `AGENTS.md`

OpenCode walks up from the working directory to the first matching `AGENTS.md`, and combines project and global rules additively (concatenated, project first). Placing a file per project directory scopes instructions by subtree:

```
jordylab-be/AGENTS.md              ← Java / Spring conventions
jordylab-fe/AGENTS.md              ← Angular / TypeScript conventions
garmin-sync-service/AGENTS.md      ← Python conventions
```

Good when the language boundary *is* the directory boundary. Useless for cross-cutting file types — `.sql` migrations scattered across modules can't be targeted this way.

### Workaround 2 — the `instructions` array

```json
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": [
    "CONTRIBUTING.md",
    "docs/guidelines.md",
    "packages/*/AGENTS.md",
    ".claude/rules/*.md"
  ]
}
```

Accepts file paths, globs *over instruction-file locations*, and remote URLs (fetched with a 5-second timeout). It can point at the same `.claude/rules/*.md` files Claude Code uses — OpenCode reads them as plain Markdown and ignores frontmatter it doesn't understand.

**But it is eager.** Every listed file loads into every session regardless of what the agent is working on. It saves file duplication, not context. Say this explicitly rather than presenting it as parity.

### Workaround 3 — third-party plugin

`frap129/opencode-rules` adds Cursor-style conditional injection: `.md`/`.mdc` rule files from `.opencode/rules/`, `.claude/rules/`, `~/.config/opencode/rules/`, and `~/.claude/rules/`, with conditions on file paths, prompt keywords, and available tools, plus filtering by model, agent, git branch, OS, and CI, and `match: any` / `match: all` logic.

Its README states it is not affiliated with the official OpenCode project. Offer it as an option with that caveat; don't install it on someone's behalf without asking.

## Worked example: three languages, both tools

### `.claude/rules/java-spring.md`

```markdown
---
paths:
  - "**/*.java"
---

# Java / Spring Boot

- Never use `var` — explicit types always
- No hand-written constructors — `@Builder` exclusively, with a `Preconditions` inner class for validation
- `AbstractAggregateRoot<T>` for domain events
- `RestClient` for all HTTP calls — never `WebClient` or `RestTemplate`
- `@ApplicationModuleTest` for module boundary tests; run `ModularityTests` after any structural change
- Blank line before every `return`
```

### `.claude/rules/typescript-angular.md`

```markdown
---
paths:
  - "**/*.ts"
  - "**/*.html"
---

# Angular / TypeScript

- `inject()` over constructor injection
- `#field` over TypeScript `private`
- Signals over NgRx for state
- Container–Presentation split; barrel imports via `index.ts`
- spartan/ui components; Vitest + `@ngneat/spectator` for tests
```

### `.claude/rules/python-django.md`

```markdown
---
paths:
  - "**/*.py"
---

# Python / Django

Guardrails for occasional Django work, not established house conventions:

- Type hints on everything
- Class-based views for anything beyond a trivial endpoint
- `django-environ` or equivalent for settings — never hardcode secrets in `settings.py`
- One logical change per migration; never hand-edit an applied migration
- `pytest-django` over the built-in `TestCase` runner; `factory_boy` over raw `.objects.create()` chains
- Use `select_related` / `prefetch_related` explicitly — N+1 queries are the most common Django review comment
```

Note the scoping collision: a generic `python.md` matching `**/*.py` and a `python-django.md` matching the same glob will both fire on every Python file, including non-Django code. If the repo has a plain-Python component alongside a Django one, narrow the Django rule's glob to that project's directory (`services/web/**/*.py`) rather than the extension.

### OpenCode side of the same setup

Given a repo where each language lives in its own directory, add three small `AGENTS.md` files with the same content — or, if the rules must not be duplicated, point `opencode.json` at the Claude rule files and accept eager loading:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "instructions": [
    ".claude/rules/java-spring.md",
    ".claude/rules/typescript-angular.md",
    ".claude/rules/python-django.md"
  ]
}
```

Tell the user which tradeoff they're taking: one source of truth with all rules always loaded, or duplicated content with proper scoping on both sides.

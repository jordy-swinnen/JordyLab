---
name: dual-agent-config
description: Set up and maintain shared configuration so Claude Code and OpenCode read the same rules, skills, and subagents from one repo without duplicated files. Use this skill whenever the work touches AGENTS.md, CLAUDE.md, .claude/ or .opencode/ directories, opencode.json, .mcp.json, switching or alternating between Claude Code and OpenCode, porting a subagent or MCP server from one agent tool to the other, bootstrapping a repo for dual-agent use, or debugging why one agent picks up instructions the other misses — even when only one of the two tools is named explicitly.
---

# OpenCode + Claude Code unified config

Claude Code and OpenCode overlap enough that most config can live in one place, but they diverge in ways that quietly break assumptions. This skill covers what to unify, what to keep separate, and how to translate between the two formats when they can't share a file.

## The compatibility map

Know this before touching anything — it determines whether a task is "point both tools at one file" or "maintain two files deliberately."

| Concern | Claude Code | OpenCode | Shareable? |
|---|---|---|---|
| Project instructions | `CLAUDE.md` | `AGENTS.md` | Yes, via import |
| Global instructions | `~/.claude/CLAUDE.md` | `~/.config/opencode/AGENTS.md` | Yes, via import |
| Skills | `.claude/skills/<n>/SKILL.md` | reads `.claude/skills/` natively | Yes, no work needed |
| Subagents | `.claude/agents/<n>.md` | `.opencode/agents/<n>.md` | Body yes, frontmatter no |
| MCP servers | `.mcp.json` → `mcpServers` | `opencode.json` → `mcp` | No, translate |
| Permissions | `.claude/settings.json` | `opencode.json` → `permission` | No, keep separate |
| Hooks | `.claude/settings.json` | OpenCode plugin system | No, keep separate |

The single most important asymmetry: **OpenCode falls back to `CLAUDE.md` when no `AGENTS.md` exists, but Claude Code never reads `AGENTS.md` on its own.** People assume the fallback runs both directions and end up with Claude Code silently ignoring half their conventions. It doesn't, and the failure is invisible — no error, just an agent that doesn't follow the rules.

Also worth internalizing: when both files exist, OpenCode uses `AGENTS.md` and ignores `CLAUDE.md` entirely. So "just keep both in sync manually" degrades badly — the moment they drift, the two tools are running on different rulebooks.

## Task: bootstrap a repo for both tools

Make `AGENTS.md` the source of truth and have `CLAUDE.md` import it.

1. If `CLAUDE.md` already holds the real content, move it: `git mv CLAUDE.md AGENTS.md`
2. Write a new `CLAUDE.md` containing exactly one line: `@AGENTS.md`
3. Verify both sides actually loaded it — see "Verifying it worked" below

Apply the same pattern globally if the user keeps personal cross-project preferences:
- `~/.config/opencode/AGENTS.md` holds the content
- `~/.claude/CLAUDE.md` contains `@~/.config/opencode/AGENTS.md`

Two things to flag when setting this up, because both surprise people later:

- **Imports expand inline.** They remove the maintenance burden, not the token cost. If someone is trying to shrink their context window, this is the wrong lever — moving instructions into skills is.
- **Symlinking is the alternative**, and it works, but it needs Administrator privileges or Developer Mode on Windows. Prefer the import unless the user has a specific reason to want a symlink.

Skills need no action at all. OpenCode discovers `.claude/skills/` and `~/.claude/skills/` natively alongside its own `.opencode/skills/`. If someone asks to "migrate" skills to OpenCode, tell them there's nothing to migrate — moving the files would only break Claude Code.

## Task: add or port an MCP server

The two schemas describe the same servers with different vocabulary, so this is mechanical translation. Read `references/mcp-translation.md` for the full field mapping and worked examples in both directions.

For anything beyond a single trivial server, use the script rather than hand-editing — it handles the array/string split on `command` and won't silently drop a field:

```bash
# preview only (default — nothing is written)
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json

# apply, merging into any existing config
python scripts/sync_mcp.py to-opencode --source .mcp.json --target opencode.json --write
```

`to-claude` runs the reverse direction. The script merges into the target's existing content rather than replacing it, and backs up to `<target>.bak` before writing.

One honesty note to carry into this task: OpenCode's field names for *remote* servers and environment variables are the part most likely to have shifted between releases. If a translated server fails to start, check `https://opencode.ai/docs/mcp-servers/` for the current schema before assuming the config is wrong elsewhere.

## Task: port a subagent between tools

Both tools define subagents as Markdown with YAML frontmatter, and **the system-prompt body is fully portable — copy it unchanged.** Only the frontmatter differs. Read `references/subagent-translation.md` for the field-by-field mapping.

The one that trips people up: Claude Code takes the agent's identity from a `name:` frontmatter field, while OpenCode takes it from the *filename*. Port a Claude Code agent by dropping `name:` and making sure the file is named after it. Go the other direction and you have to add `name:` back, matching the filename.

When porting, also set `model:` appropriately for the target rather than copying it across — the whole reason for running two tools is usually that they route to different models. Ask which model the ported agent should use if it isn't obvious from context.

## Task: audit an existing setup

When someone reports "one agent isn't following my rules," walk the compatibility map in order:

1. Does `AGENTS.md` exist? If yes, does `CLAUDE.md` exist and import it? A repo with only `AGENTS.md` means Claude Code is running blind.
2. Does `CLAUDE.md` hold real content while `AGENTS.md` also exists? Then OpenCode is ignoring the `CLAUDE.md` content entirely.
3. Are skills under `.claude/skills/`? If they've been moved to `.opencode/skills/`, Claude Code lost them.
4. Do subagent definitions exist in only one of `.claude/agents/` and `.opencode/agents/`?
5. Are MCP servers present in `.mcp.json` but missing from `opencode.json`, or vice versa?

## What not to unify

Resist the urge to make permissions and hooks shareable. The rule syntaxes genuinely differ, and more to the point, the setups *should* differ: people run two agent tools precisely because they route to different-cost models, and a cheap model doing routine work often warrants different permission gates than a frontier model touching production-adjacent code. Treating the divergence as a bug leads to fragile conversion layers that solve nothing.

If a user pushes to unify these anyway, explain the reasoning rather than just declining — then do it if they still want it. It's their setup.

## Verifying it worked

Never declare a config change done without checking both sides — this is exactly the kind of change that fails silently.

- **Claude Code**: run `/context` and confirm `CLAUDE.md` appears under memory files, with the imported content present.
- **OpenCode**: start a session and confirm it reports reading `AGENTS.md`.
- **MCP**: list servers in each tool and confirm the same names appear in both.

If the user can't run these right now, say plainly which checks are still outstanding rather than implying the setup is verified.

## Reference layout

The end state for a repo configured this way:

```
repo/
├── AGENTS.md            ← source of truth
├── CLAUDE.md            ← "@AGENTS.md"
├── opencode.json        ← OpenCode models, permissions, mcp block
├── .mcp.json            ← Claude Code MCP servers
├── .claude/
│   ├── settings.json    ← Claude Code permissions + hooks
│   ├── agents/          ← Claude Code subagents
│   └── skills/          ← shared — both tools read this
└── .opencode/
    └── agents/          ← OpenCode subagents (same bodies, different frontmatter)
```

Both tools ship frequently. When a documented behavior here doesn't match what the user observes, trust the observation and check `https://opencode.ai/docs/` and `https://code.claude.com/docs/` before debugging further.

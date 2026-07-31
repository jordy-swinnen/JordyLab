# Subagent translation: Claude Code ↔ OpenCode

Both tools define subagents as a Markdown file with YAML frontmatter, where the body is the system prompt. **The body is fully portable — copy it verbatim.** Everything below concerns frontmatter and file placement only.

## Locations

| Scope | Claude Code | OpenCode |
|---|---|---|
| Project | `.claude/agents/<name>.md` | `.opencode/agents/<name>.md` |
| Global | `~/.claude/agents/<name>.md` | `~/.config/opencode/agents/<name>.md` |

Claude Code scans its agents directory recursively, so subfolders like `agents/review/` are fine — the subdirectory doesn't affect identity.

## The identity difference

This is the one that silently breaks a port:

- **Claude Code** takes the agent's name from the required `name:` frontmatter field.
- **OpenCode** takes it from the **filename** — `review.md` defines an agent called `review`.

Porting Claude Code → OpenCode: drop `name:`, and rename the file to match what `name:` said.
Porting OpenCode → Claude Code: add `name:`, matching the filename.

Claude Code additionally requires names to be unique across the whole agents tree. Two files declaring the same name means only one loads, chosen by filesystem read order — so a port that duplicates an existing name fails nondeterministically.

## Field mapping

| Purpose | Claude Code | OpenCode |
|---|---|---|
| Identity | `name:` (required) | filename |
| When to invoke | `description:` (required) | `description:` (required) |
| Agent kind | implicit | `mode: subagent` |
| Model | `model:` — `sonnet`, `opus`, `haiku`, full ID, or `inherit` | `model:` — `provider/model-id` |
| Tool allowlist | `tools:` (comma-separated) | configured via `tools`/permissions |
| Temperature | not a frontmatter field | `temperature:` |

Claude Code supports a number of fields OpenCode has no equivalent for — `skills:`, `memory:`, `effort:`, `hooks:`, `disallowedTools:`. When porting away from Claude Code, call out which fields are being dropped instead of discarding them silently; the user may be relying on one of them.

## Model values do not port

Claude Code's aliases (`sonnet`, `opus`, `inherit`) are meaningless to OpenCode, which expects a `provider/model-id` string. Never copy the `model:` line across.

More importantly, the model *shouldn't* be the same. Running both tools usually means routing to different models at different price points — that's typically the whole reason for the dual setup. Ask which model the ported agent should use if context doesn't make it obvious.

## Worked example

**Claude Code** — `.claude/agents/code-reviewer.md`:
```markdown
---
name: code-reviewer
description: Reviews code for quality and best practices. Use after any significant change.
tools: Read, Grep, Glob, Bash
model: sonnet
---

You are a senior code reviewer.

When invoked:
1. Run git diff to see recent changes
2. Focus on modified files
3. Review for readability, error handling, security, and test coverage

Organize feedback by priority: Critical, Warnings, Suggestions.
```

**OpenCode** — `.opencode/agents/code-reviewer.md`:
```markdown
---
description: Reviews code for quality and best practices. Use after any significant change.
mode: subagent
model: opencode-go/glm-5.2
---

You are a senior code reviewer.

When invoked:
1. Run git diff to see recent changes
2. Focus on modified files
3. Review for readability, error handling, security, and test coverage

Organize feedback by priority: Critical, Warnings, Suggestions.
```

Note what changed: `name:` removed (filename carries it), `mode: subagent` added, `model:` retargeted, `tools:` dropped. The body is byte-identical.

## Keeping bodies in sync over time

For a small number of agents, maintaining two files is fine — the bodies change rarely once written.

If they start drifting, extract each system prompt into a shared fragment (for example `docs/agents/code-reviewer-prompt.md`) and have both frontmatter wrappers reference or include it. Don't build a generator for three files; the maintenance cost of the generator will exceed the duplication it removes.

## Writing effective descriptions

The `description` field drives automatic delegation in both tools — it's a matching signal, not a label. Be concrete about what the agent handles and when it should be selected. `"Reviews code"` routes poorly; `"Reviews code for security vulnerabilities including SQL injection, XSS, and secrets exposure. Use when analyzing changes for security risks."` routes well.

This field ports across unchanged, so improving it improves both tools at once.

# MCP server translation: Claude Code ↔ OpenCode

## Field mapping

| Concept | Claude Code (`.mcp.json`) | OpenCode (`opencode.json`) |
|---|---|---|
| Top-level key | `mcpServers` | `mcp` |
| Local process type | `"type": "stdio"` | `"type": "local"` |
| Remote server type | `"type": "http"` or `"sse"` | `"type": "remote"` |
| Command | `"command"` (string) + `"args"` (array) | `"command"` (single array) |
| Environment vars | `"env"` | `"environment"` |
| Enable/disable | omit the entry, or remove it | `"enabled": true/false` |
| Remote URL | `"url"` | `"url"` |

Claude Code also supports `websocket` transport, which has no clean OpenCode equivalent — flag these rather than guessing at a translation.

## The command split

This is the field that breaks naive copy-paste. Claude Code separates the executable from its arguments; OpenCode puts everything in one array.

Claude Code:
```json
"command": "npx",
"args": ["-y", "@modelcontextprotocol/server-postgres"]
```

OpenCode:
```json
"command": ["npx", "-y", "@modelcontextprotocol/server-postgres"]
```

Going the other direction, the first array element becomes `command` and the remainder becomes `args`.

## Worked example: local stdio server

**Claude Code** (`.mcp.json`):
```json
{
  "mcpServers": {
    "postgres": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": { "POSTGRES_CONNECTION_STRING": "${DATABASE_URL}" }
    }
  }
}
```

**OpenCode** (`opencode.json`):
```json
{
  "$schema": "https://opencode.ai/config.json",
  "mcp": {
    "postgres": {
      "type": "local",
      "command": ["npx", "-y", "@modelcontextprotocol/server-postgres"],
      "environment": { "POSTGRES_CONNECTION_STRING": "${DATABASE_URL}" },
      "enabled": true
    }
  }
}
```

## Worked example: remote server

**Claude Code**:
```json
{
  "mcpServers": {
    "sentry": {
      "type": "http",
      "url": "https://mcp.sentry.dev/mcp"
    }
  }
}
```

**OpenCode**:
```json
{
  "mcp": {
    "sentry": {
      "type": "remote",
      "url": "https://mcp.sentry.dev/mcp",
      "enabled": true
    }
  }
}
```

## Secrets

Both formats support environment variable expansion, and neither should ever contain a literal credential — `.mcp.json` in particular is meant to be committed to the repo.

- Claude Code: `${VAR}` and `${VAR:-default}`
- OpenCode: `{env:VAR}` for env vars, `{file:~/.secrets/key}` to read from a file

These syntaxes are **not** interchangeable. When translating an entry that expands a variable, convert the syntax rather than copying it, or the server will receive a literal string and fail in a confusing way.

If a source config does contain a hardcoded secret, point it out rather than faithfully copying it into a second file — that doubles the exposure.

## Scope differences

Claude Code has three MCP scopes with precedence local > project > user:

- **local** — `~/.claude.json`, keyed by project path, private to the user
- **project** — `.mcp.json` at the repo root, committed and shared
- **user** — `~/.claude.json` top level, applies across all projects

OpenCode merges a global `~/.config/opencode/opencode.json` with a project-level `opencode.json`, project winning.

When translating, map Claude Code's *project* scope to OpenCode's *project* config and *user* scope to OpenCode's global config. Claude Code's *local* scope has no direct equivalent — if an entry lives there, ask whether it should become project-level or global on the OpenCode side rather than picking silently.

## Verification

After translating, list servers in both tools and confirm the same names appear. A server that appears in the list but fails on first tool call is usually an environment-variable syntax problem, not a schema problem — check that first.

Claude Code also requires approving servers from a newly cloned `.mcp.json` before they activate. If a translated server shows up as disabled on the Claude Code side, that approval prompt is the likely cause, not the translation.

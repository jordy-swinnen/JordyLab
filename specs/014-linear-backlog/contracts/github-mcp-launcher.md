# Contract: GitHub MCP launcher (`scripts/mcp/github-mcp.sh`)

Started by both tools as a stdio MCP server entry. It is the only place that touches the GitHub token.

## Behaviour

1. Run `gh auth token`. If `gh` is missing or not signed in, print to stderr
   `github-mcp: not signed in to GitHub. Run: gh auth login` (and, when `gh` is missing, `install the GitHub CLI first`)
   and exit **1**. Nothing else is written to stdout, which carries the MCP protocol.
2. Export `GH_MCP_AUTH="Bearer <token>"`. It lives only in this process's environment.
3. `exec bunx mcp-remote@0.15.1 https://api.githubcopilot.com/mcp/ --header 'Authorization:${GH_MCP_AUTH}'` (single
   quotes: the bridge, not the shell, expands the variable) so the bridge replaces the shell and stdio belongs to it.
   The version is the exact pin reviewed in the spike (research D1); the launcher never runs an unpinned package.
4. The token is never printed, logged, written to disk, put in a file name or in the argument list (the spike showed
   the bridge reads the header value from the environment, so the process list shows only the placeholder); `set +x`
   is enforced.

## Exit codes

| Code | Meaning |
|---|---|
| 0 | bridge exited normally |
| 1 | `gh` missing or not signed in |
| 2 | `bunx` missing |
| other | the bridge's own exit code |

## Config entries

`.mcp.json`:

```json
"github": { "type": "stdio", "command": "sh", "args": ["scripts/mcp/github-mcp.sh"] }
```

`opencode.json`:

```json
"github": { "type": "local", "command": ["sh", "scripts/mcp/github-mcp.sh"], "enabled": true }
```

## Fixture cases (`scripts/mcp/tests/github-mcp-cases.sh`, stub `gh` and `bunx` first on `PATH`)

| Case | Expect |
|---|---|
| stub `gh auth token` fails | exit 1, stderr names `gh auth login`, stub `bunx` never called |
| `gh` not on `PATH` | exit 1, stderr mentions installing |
| `bunx` not on `PATH` | exit 2 |
| success | stub `bunx` called once with the endpoint URL and the header argument `Authorization:${GH_MCP_AUTH}`; stub sees `GH_MCP_AUTH` = `Bearer <token>` in its environment; token appears in no stdout/stderr line and in no argument |
| pinned version | the package argument passed to the stub `bunx` contains `@` followed by an exact version, never `@latest` or no version |

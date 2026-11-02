#!/bin/sh
# Starts the GitHub MCP connection for Claude Code and OpenCode (both run this as a stdio server).
# The token comes from the `gh` login, so no personal access token is created, stored or exported by hand.
# stdout carries the MCP protocol: nothing but the bridge may write to it. Contract:
# specs/015-linear-backlog/contracts/github-mcp-launcher.md
set +x

# Exact pin, reviewed in specs/015-linear-backlog/research.md (D1). Raise it deliberately, never use a tag.
BRIDGE="mcp-remote@0.15.1"
ENDPOINT="https://api.githubcopilot.com/mcp/"

# The desktop app starts outside a login shell and can miss Homebrew's and bun's directories; append them (after the
# existing PATH, so anything already found wins). The fixtures set GITHUB_MCP_EXTRA_PATH to an empty value.
PATH="$PATH:${GITHUB_MCP_EXTRA_PATH-/opt/homebrew/bin:/usr/local/bin:$HOME/.bun/bin}"

if ! command -v gh >/dev/null 2>&1; then
  echo "github-mcp: the GitHub CLI is missing; install the GitHub CLI first, then run: gh auth login" >&2
  exit 1
fi

token="$(gh auth token 2>/dev/null)"
if [ -z "$token" ]; then
  echo "github-mcp: not signed in to GitHub. Run: gh auth login" >&2
  exit 1
fi

if ! command -v bunx >/dev/null 2>&1; then
  echo "github-mcp: bunx is missing. Install bun (https://bun.sh) to start the GitHub connection." >&2
  exit 2
fi

# The bridge expands ${GH_MCP_AUTH} itself (single quotes keep the shell from doing it), so the token lives only in
# this process's environment and never in the argument list.
GH_MCP_AUTH="Bearer $token"
export GH_MCP_AUTH
unset token
exec bunx "$BRIDGE" "$ENDPOINT" --header 'Authorization:${GH_MCP_AUTH}'

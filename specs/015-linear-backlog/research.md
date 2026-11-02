# Research: Linear as Ticketing and Backlog

Date: 2026-11-01. Checked against the repo on this branch and against the vendors' own docs (sources at the end). The
earlier, broader research stays in `specs/_drafts/linear-backlog/research.md`; this file records only the decisions the
plan needed and the facts verified for them. Text read from web pages is treated as data.

## Decisions

### D1. GitHub connection: one launcher script that turns the `gh` login into the bridge's header

- **Decision**: Claude Code and OpenCode both start the GitHub connection through the same small launcher
  (`scripts/mcp/github-mcp.sh`). It asks `gh auth token` for the token, fails with a clear message when `gh` is not
  signed in, and then runs a stdio-to-remote bridge (`bunx mcp-remote`) against `https://api.githubcopilot.com/mcp/`
  with the token in the `Authorization` header. The token only exists in that process; it is never written to a file,
  a config or the repo.
- **Verified**: GitHub's remote MCP endpoint accepts this machine's `gh` token (a `gho_` OAuth token, scopes `repo`,
  `workflow`, `read:org`, `gist`): an `initialize` request answered HTTP 200 with the server's capabilities (status
  checked on 2026-11-01; the token was not printed). GitHub's own docs only document a personal access token, so this
  is a tested behaviour, not a documented one; the quickstart re-checks it, and the plan has a fallback.
- **Why this and not what exists today**: `.mcp.json` and `opencode.json` send `Authorization: Bearer ${GITHUB_PAT}`.
  `GITHUB_PAT` is not set in the shell, and Claude Code documents that an unset variable is passed through as the
  literal text, so the header is malformed, which matches "Authorization header is badly formatted". A shell variable
  also does not reach the desktop app, which starts outside the shell.
- **Rationale for one launcher over per-tool mechanisms**: Claude Code can run a command to build headers
  (`headersHelper`), but OpenCode's remote servers only take `{env:…}` and `{file:…}` values, no command. A stdio entry
  works the same in both, so there is one mechanism to test and nothing that can drift. The `gh` login is refreshed by
  `gh`, not by us.
- **Alternatives considered**:
  - *Keep `GITHUB_PAT`*: another long-lived secret to create, scope and rotate, and it still has to reach the desktop
    app. Rejected by the clarification.
  - *`headersHelper` for Claude Code plus `{file:…}` for OpenCode*: two mechanisms; several open reports say
    `headersHelper` is ignored for some server setups. Kept only as a documented fallback.
  - *GitHub's local server (`github-mcp-server stdio`)*: needs a binary or a container image on every machine;
    the bridge needs only `bunx`, which the repo already uses for `nx mcp`.
  - *Drop the connection and use the `gh` command only*: loses the tools the SpecKit skills call.
- **Spike result (T002, 2026-11-01)**: `bunx mcp-remote@0.15.1 https://api.githubcopilot.com/mcp/ --header
  'Authorization:${GH_MCP_AUTH}'` with `GH_MCP_AUTH="Bearer $(gh auth token)"` exported answered an `initialize`
  request with the server's capabilities (tools, prompts, resources). The bridge expands `${VAR}` in a header value
  from its environment (its log says "Replacing ${GH_MCP_AUTH} with environment value" and prints only the header
  *name*), so the token is **not** in the argument list or the process list; the launcher therefore passes it through
  the environment. No token appeared in the bridge's stdout or stderr, and its state directory (`~/.mcp-auth`) stayed
  empty. Still to be seen by Jordy in the real tools: that the stdio entry connects from the desktop app (T014).
  Fallback if the bridge ever misbehaves: `headersHelper` (Claude Code) and a documented `{file:…}` header for
  OpenCode only.
- **Pinned version and review**: `mcp-remote@0.15.1` (published 2026-10-14, 18 days old; `0.15.2` is 12 days old and
  `latest`, so it is skipped until it is two weeks old). Registry integrity
  `sha512-znJhxnoQZTDx9AieHZrRYs8YqWKpyH1SKyFEFnMTMDUPpx6r9dSc/L4lKf18DdWMQqKpbwWbfIZfyiVg2RSXzg==`. Read of the
  published package: runtime dependencies are `express`, `open`, `strict-url-sanitise`, `undici`; the only hosts in
  the code are loopback (OAuth callback), `github.com` and `raw.githubusercontent.com` (documentation and update
  links) and the server URL the launcher passes; the header goes only to that URL; headers are logged by name only. No
  install scripts. Review result: clean. Raising the pin is a deliberate change with the same review.
- **Supply chain (analysis finding)**: the bridge is a third-party npm package that receives a token with repo and
  workflow scope. The launcher therefore runs an exact pinned version (never `latest`, at least two weeks old), the
  spike reads what the package does with the header, and the version and review result are recorded here before the
  launcher is written. Raising the pin later is a deliberate change with the same review.

### D2. Linear connection: hosted server in both tools, claude.ai connector for the apps

- **Decision**: add `https://mcp.linear.app/mcp` as a remote server named `linear` in `.mcp.json` (HTTP) and
  `opencode.json` (`type: remote`). Both do the browser sign-in themselves (OAuth with dynamic client registration):
  Claude Code through `/mcp`, OpenCode through `opencode mcp auth linear`. Nothing is stored in the repo. For Claude on
  the web, desktop and phone, Jordy enables the Linear connector in Claude's connector settings; the setup document
  lists the clicks.
- **Rationale**: OAuth per person is what Linear documents; no key, no secret in git. The phone and desktop apps cannot
  read `.mcp.json`, so they need the connector.
- **Alternatives considered**: a Linear API key in a header (a secret to store; only worth it for headless runs);
  `mcp-remote` for Linear (not needed, both tools speak HTTP MCP).
- **Note**: if Jordy's Claude account already has the Linear connector, Claude Code may list it next to the project
  entry. Both point at the same server and the same login; the setup document says to keep either one.

### D3. One source for commit and pull-request instructions, three readers

- **Decision**: the full text lives in `.github/instructions/commits-and-pull-requests.instructions.md`.
  - Claude Code reads it through an `@` import in `CLAUDE.md`.
  - OpenCode reads it through the `instructions` list in `opencode.json`.
  - GitHub shows its skeleton to people through `.github/pull_request_template.md` and points Copilot at it through
    `.github/copilot-instructions.md` (one line each, no rules repeated).
  - The root `AGENTS.md` "Commits" section shrinks to a short summary and a link, so the token table exists once.
- **Rationale**: the three readers find the same text without copies. `.github/pull_request_template.md` is the
  documented way to prefill a pull-request description. Whether Copilot's commit-message and PR-description generators
  read repository instruction files could not be confirmed from GitHub's docs (VS Code has separate settings for them);
  the pointer file costs nothing and the plan does not depend on it.
- **Alternatives considered**: keep everything in `AGENTS.md` (long, and GitHub does not read it); a new `docs/` page
  (not a place GitHub or either agent reads on its own); copies in three places (drift).

### D4. Linear linking words

- **Verified** (Linear docs): a branch name containing the issue key links without any word. In a pull-request title or
  description, a closing word (close, fix, resolve, complete, implement, in any tense) links the issue and, on merge,
  moves it to the team's merge status; a non-closing word (ref, refs, references, part of, contributes to, towards)
  links without completing; "relates to" only relates. "Skip/ignore KEY" prevents a link. Words in comments do not
  link. Defaults: In Progress when the pull request opens, Done when it merges, set per team under Workflows &
  automations. Commit linking is a separate toggle plus a webhook, and covers one GitHub organization.
- **Decision**: the instructions teach `Fixes JOR-n` only when the pull request completes the issue, `Refs JOR-n` /
  `Part of JOR-n` otherwise. The `Refs:` commit trailer is a different thing (the project's own reference) and is only
  format-checked; commit linking in Linear was first left off, because pull-request linking is enough and commit
  linking needs a webhook. **Changed 2026-11-02 (owner):** the commit webhook was added, so commit linking is on. That
  is consistent with the new commit rule (every subject starts with `JOR-n:`, so every commit links to its ticket).
- **Rationale**: the project's `Refs:` trailer starts with the word "Refs", which Linear also treats as a magic word;
  it does not complete anything by accident because the trailer carries a spec or task number (`Refs: 015/T036`), never
  an issue key, and completion on merge is decided by the pull request's words and title (see `docs/linear.md`). The
  webhook's behaviour is not yet observed (T035).

### D5. How the migration runs

- **Decision**: a skill, `linear-sync-drafts`, with a read-only inventory script. The script prints, for every folder
  under `specs/_drafts/`, a JSON record: name, title, the draft's own status wording, the matching numbered spec (when
  found), a proposed App and Type, related drafts and whether the spec looks shipped. The agent shows the proposal as a
  table, Jordy confirms (this is where the "looks shipped → Done" and unclear matches are decided), and only then does
  the agent create or update the issues through the Linear connection. Re-runs diff the inventory against what Linear
  already holds, matching on the draft folder link, so nothing is created twice.
- **Why a script for the inventory but the agent for the writes**: matching drafts to specs and reading status lines is
  deterministic and testable with fixtures; creating issues goes through Linear's connection, which only agents have.
- **Draft-to-spec match**: a spec matches a draft when the spec folder's slug equals the draft name (deployment →
  008-ovh-k8s-deployment is *not* an equal slug, so it is matched through the spec's `Input`/title text and a small
  override list kept in the skill). Unclear cases are never guessed; they are listed.
- **Proposed matches found by reading the specs** (to confirm during the run): deployment → 008, mobile-app → 007,
  switch-games → 009, eufy-presence → 010, settings → 006, oxlint-fast-lint, e2e-testing and
  ai-integration-conventions → 012, linear-backlog → 015. The other AI drafts are partly absorbed by 013 and stay
  *Drafted* unless Jordy says otherwise.
- **Proposed App and Type per draft** (first guess, confirmed in the run): every `ai-*` draft, `auth-without-keycloak`,
  `compose-deployment`, `continuous-deploy`, `deployment`, `e2e-testing`, `guest-feedback`, `langfuse-prompt-management`,
  `linear-backlog`, `oxlint-fast-lint`, `angular-onpush-adoption`, `public-private-exposure`, `settings`,
  `user-management`, `presence-ha-prototype`, `eufy-presence` → Platform; `switch-games`, `multiplayer-player-counts`
  → Game Catalog; `mobile-app`, `pwa-replaces-capacitor` → Mobile. Types: Feature for new capability, Improvement for
  changes to something that exists, Chore for tooling. `eufy-presence` has no obvious App among the seven and is the
  likeliest to need a decision.

### D6. Where the one-time workspace setup is done

- **Decision**: labels, projects and issues are created by the agent through the Linear connection. Workflow states
  (*Drafted*, *Specified*, Triage on), saved views, the GitHub integration and the pull-request automations are set by
  Jordy in Linear's settings, following `docs/linear.md`, as a handoff block. The migration refuses to start until the
  *Drafted* and *Specified* states exist.
- **Rationale**: the hosted server's tools cover issues, projects, labels and comments, not workspace settings; the
  GitHub integration needs an owner click-through in the browser. Everything done by hand is a checklist in the
  document, so the setup is reproducible (FR-001).
- **Unverified**: that the hosted tools cannot create workflow states. The first task lists the tools after sign-in; if
  they can, the agent does it.

### D7. Free-plan limit

- **Facts**: Linear's billing page says workspaces with more than about 250 issues cannot create new ones on the free
  tier. The docs do not say whether archived issues count.
- **Decision**: before the migration, the agent counts issues including archived ones and prints "N of 230 usable" (limit about 250, safety stop at 230). Planned
  usage is about 25 draft issues, 15 open bugs (4 FIXING, 11 FIXED-LOCAL; the 9 DEPLOYED and 53 verified ones stay in
  git) and 5–10 issues per newly specified spec, so about 45 now. The tasks-to-issues step re-counts and stops when the
  new issues would pass 230. The answer to the archive question is recorded in `docs/linear.md` once known.

### D8. Commit hook change

- **Decision**: add `JOR-[0-9]+` to the accepted tokens in `.githooks/commit-msg` (format only, no Linear lookup),
  add the same cases to the existing table-driven test and to the workflow's token list comment. `NO-CODE` stays alone.
  A Linear key stays optional on feature commits; the existing rule that every commit has some `Refs:` token is
  unchanged.
- **Rationale**: the CI check runs the same script, so one change covers both.

### D9. Handover comment

- **Decision**: `agent-handover` looks for an active issue key (in the branch name, then in recent `Refs:` trailers)
  and, only if it finds one and the Linear connection is signed in, posts one short comment with the state and the
  handoff file path. It never fails the handover.

### D10. Tasks-to-issues for Linear

- **Decision**: edit the `speckit-taskstoissues` skill in place (it is a generated SpecKit file, so a header note says
  it is customised and `specify` upgrades must be diffed against it). It creates one project per spec, milestones per
  phase or user-story group in `tasks.md`, one issue per user story, finds existing ones by the spec folder link and
  the user-story title, and reuses the draft's *Specified* issue as the project's lead item.

## Sources

- Linear MCP: https://linear.app/docs/mcp
- Linear GitHub integration: https://linear.app/docs/github
- Linear plans and limits: https://linear.app/docs/billing-and-plans and https://linear.app/docs/usage-and-limits
- Claude Code MCP: https://code.claude.com/docs/en/mcp
- OpenCode MCP servers and config variables: https://opencode.ai/docs/mcp-servers/ and https://opencode.ai/docs/config/
- GitHub MCP server: https://github.com/github/github-mcp-server
- GitHub repository custom instructions: https://docs.github.com/en/copilot/how-tos/copilot-cli/customize-copilot/add-custom-instructions

## First real inventory (T020, 2026-11-02)

`repo_inventory.sh` on this repository: 30 items, 14 numbered specs and 16 drafts without a spec (the nine drafts that
belong to specs share their spec's issue: switch-games, eufy-presence and linear-backlog by slug; deployment,
mobile-app, settings, oxlint-fast-lint, e2e-testing and ai-integration-conventions by the override list). States from
`tasks.md`: Done 004 and 012; In Progress 001, 002, 003, 005, 006, 007, 008, 009, 011, 013, 015; Specified 010. Done
candidates (In Progress with at most 3 open tasks): 001, 002, 003, 005, 007, 008, 009, 011. Needs decision: the draft
`guest-feedback` (only mentioned by spec 015). `open_bugs.sh`: 15 open bugs (11 FIXED-LOCAL, 4 FIXING).

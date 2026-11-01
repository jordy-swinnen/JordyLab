# Linear as Ticketing and Backlog: Research

Date: 2026-11-01. Checked against the repo on `main` and against Linear's own docs (sources at the end). Decisions
from Jordy are marked as such.

## Idea in one paragraph

Ideas, bugs and progress are spread over `docs/testing/bug-log.md` (77 bugs), the `tasks.md` checkboxes of each spec,
the drafts in `specs/_drafts/` and chat with agents. Linear becomes the one place to log ideas, see what is in
progress and group work per app and per epic. It is set up "AI-native": Claude Code, OpenCode and Linear's own agent do
most of the logging, triage and status updates, and Jordy mostly confirms. Specs stay in git, where the agents read
them. The workspace exists already: `linear.app/jordylab`, team key `JOR`.

## Decisions (Jordy, 2026-11-01)

- **Plan: Free.** No paid features are assumed (no Triage Intelligence, no Linear Asks, no team-level project labels).
- **Linear for the backlog, git for the specs.** Ideas, bugs and progress live in Linear. `spec.md`, `plan.md` and
  `tasks.md` stay in the repo. New bugs are logged in Linear; the existing bug log stays as history.
- **The idea box creates a Linear issue on accept.** Writing a SpecKit draft becomes a later, separate step started
  from the Linear issue (see the `guest-feedback` draft).

## What the repo has today

- **Bug log:** `docs/testing/bug-log.md`, headings `### BUG-###:`. The commit hook checks that a referenced bug is
  logged there.
- **Commit references:** `.githooks/commit-msg` and `.github/workflows/commit-refs.yml` accept spec numbers, spec
  tasks, `BUG-###`, campaign IDs and `NO-CODE`. Any other token fails the commit.
- **Tasks:** `/speckit-tasks` writes `tasks.md` per spec; `/speckit-taskstoissues` turns tasks into **GitHub** issues
  today (never used in practice).
- **Agents:** Claude Code and OpenCode share skills and MCP servers (`.mcp.json`, `opencode.json`, the
  `dual-agent-config` skill). The `agent-handover` skill writes `HANDOFF-next-agent.md` next to the spec.
- **Drafts:** `specs/_drafts/<short-name>/`, no numbers or codes; SpecKit numbers a draft when it is specified.
- **Idea box:** the `guest-feedback` draft (not built) — guests send feedback, the admin triages it in the app.

## How to structure the workspace

Linear's building blocks, from big to small: workspace → team → initiative → project → milestone → issue → sub-issue,
plus labels (optionally in label groups) and cycles.

| Linear object | Used for | Example |
|---|---|---|
| Team | One team, `JOR`. More teams only add overhead for one person and agents. | `JOR` |
| Initiative | A longer theme spanning several epics. Available on Free. | First public release, AI quality |
| Project | **An epic: one per SpecKit spec or draft.** The description links to the spec folder. | Game catalog AI refinement |
| Milestone | A user story or phase of that spec. | Setup, LibBot answers, Polish |
| Issue | One user story, bug, idea or chore. **Not one per `tasks.md` task** (see Free-plan limit). | LibBot misses console names |
| Label group "App" | **Per-app grouping**, single-select, across all projects. | Game Catalog, Finance News, Garmin, Mobile, Recipe, Trading, Platform |
| Label group "Type" | What kind of work it is. | Bug, Feature, Improvement, Idea, Chore |
| Cycles | Off at first. Useful later if Jordy wants a weekly rhythm. | — |

Why apps are labels and epics are projects: Linear's docs say to use initiatives for an intentional set of projects
tied to one objective, and labels for cross-cutting categories that do not fit one hierarchy. An app is cross-cutting
(a bug, an idea and several epics can all be "Game Catalog"); an epic has a start, an end and a progress bar, which is
exactly a project. Saved views then give "everything for Game Catalog" or "all open ideas" in one click.

Workflow states for team `JOR`: **Triage** (on) → Backlog → Drafted (a draft folder exists) → Specified (numbered
spec exists) → In Progress → In Review → Done / Canceled. Issues created without a state land in Triage, so everything
an agent or the idea box creates is reviewed once.

## AI-native capture: logging an idea in seconds

1. **From the coding agent.** Linear's hosted MCP server (`https://mcp.linear.app/mcp`, OAuth on first use) is added
   to both agents. Jordy says "log an idea: …" or "log this as a bug" mid-session. A shared skill makes the agent
   search Linear for duplicates first, then create the issue in Triage with the App and Type labels and, when the
   session is about a spec, the matching project. The agent already knows the context (file, error, spec), so the
   issue is better than one typed by hand.
2. **From Linear itself.** Linear Agent (Cmd/Ctrl+J or `@Linear`, on by default) creates and updates issues, projects
   and initiatives from a plain-language request, also in the Linear mobile app. Good for ideas on the go.
3. **From the JordyLab app.** Accepting an idea in the idea box creates the issue through Linear's GraphQL API
   (`issueCreate`), labelled with the ticket's app and type (see the `guest-feedback` draft).

## AI-native triage on the Free plan

Linear's Triage Intelligence (auto labels, assignee, duplicate detection) needs Business or Enterprise. On Free only
the search-based quick suggestions in the composer are available. The replacement is a skill both agents share:
"triage Linear" walks the Triage queue, proposes App/Type labels, a project and possible duplicates per issue, and
applies them after Jordy confirms. It runs on the agent's own model, so it costs no Linear credits.

## Tracking progress without extra work

- **GitHub integration (all plans).** A branch name containing `JOR-123` links its pull request automatically, as do
  magic words in the pull request title or description (`Fixes JOR-123`, `Refs JOR-123`, `Part of JOR-123`). By
  default the issue moves to In Progress when the pull request opens and to Done when it merges; the triggers are set
  per team under Workflows & automations.
- **Commit references.** The `Refs:` trailer accepts `JOR-123` next to the existing tokens. The hook checks only the
  format, so it works offline and does not need a Linear key.
- **Spec to Linear.** `/speckit-taskstoissues` targets Linear instead of GitHub issues: one project per spec, one
  milestone per phase or user story, one issue per user story. Task-level detail stays in `tasks.md`.
- **Handover and updates.** `agent-handover` also posts a short comment on the active issue. A project update ("what
  moved this week") can be written by an agent from merged pull requests and the issue history.

## Free-plan limit

Linear's billing page says workspaces with more than 250 issues can no longer create issues when on the free tier.
That rules out one issue per task (a spec has 50–100 tasks). One issue per user story, bug or idea fits: specs give
5–10 issues each. The docs do not say whether archived issues count; to check before importing anything. Only open
bugs move from the bug log.

## Credentials

- **Agents:** the MCP server uses OAuth per person; nothing is stored in the repo.
- **Backend (idea box):** an OAuth app installed with `actor=app` creates issues as "JordyLab" instead of as Jordy
  (needs workspace admin; cannot be combined with the `admin` scope). A personal API key is the simpler fallback
  (`Authorization: <key>`, no `Bearer`). Either is a SOPS secret, never sent to the browser.
- **Rate limits:** 2,500 requests per hour for an API key, 5,000 for an OAuth app. The idea box uses a handful a day.
- **Webhooks (optional):** Linear signs each delivery with `Linear-Signature` (HMAC-SHA256 of the raw body); reject
  deliveries whose `webhookTimestamp` is more than 60 seconds old. Deliveries must answer within 5 seconds.

## Later, not in this draft

- **Delegating issues to coding agents.** Linear lets an issue be assigned to an agent, which can open pull requests;
  Linear Agent coding sessions are billed as usage. Worth a try once the backlog lives in Linear, but not part of
  this feature.
- **Paid features.** Triage Intelligence, Linear Asks and AI-written issues from pull requests, if Jordy upgrades.

## Risks to design for

- **Two sources of truth.** If an agent marks a task done in `tasks.md` but not in Linear (or the reverse), they
  drift. Rule: `tasks.md` is task-level and only in git; Linear is story-level and moved by pull requests.
- **Personal data.** The repository is public but Linear is private. Issues created from guest feedback still carry no
  email address; the guest is "a guest of Game Catalog".
- **Prompt injection.** Issue text written by guests is read by agents later. The skills treat issue text as data.
- **Lock-in.** Specs and the bug-log history stay in git, so leaving Linear costs only the backlog, which exports to CSV.

## Open questions

- Do archived issues count towards the 250-issue limit?
- OAuth app (`actor=app`) or personal API key for the backend?
- Which App labels exactly — is "Platform" one label or split into infra, auth and AI?
- Move all open bugs from the bug log now, or only log new ones in Linear?
- Should the commit hook require a `JOR-###` for feature work once Linear is the backlog, or stay optional?

## Sources

- Triage Intelligence: https://linear.app/docs/triage-intelligence
- Linear Agent: https://linear.app/docs/linear-agent and https://linear.app/docs/agents-in-linear
- Initiatives: https://linear.app/docs/initiatives
- Project labels: https://linear.app/docs/project-labels
- MCP server: https://linear.app/docs/mcp
- GitHub integration: https://linear.app/docs/github
- GraphQL API: https://linear.app/developers/graphql
- Agents and OAuth `actor=app`: https://linear.app/developers/agents
- Rate limits: https://linear.app/developers/rate-limiting
- Webhooks: https://linear.app/developers/webhooks
- Plans and limits: https://linear.app/docs/billing-and-plans and https://linear.app/docs/usage-and-limits

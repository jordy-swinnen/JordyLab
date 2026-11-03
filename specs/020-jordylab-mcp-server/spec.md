# Feature Specification: JordyLab MCP Server (read-only)

**Feature Branch**: `020-jordylab-mcp-server`

**Created**: 2026-11-03

**Status**: Draft

**Linear**: JOR-79

**Input**: User description: "As an admin, I want a JordyLab MCP server so I can connect Claude Desktop and Claude mobile to JordyLab and ask questions about the current state of the data in the app. The server is read-only: it answers questions and does not change data or trigger any actions."

## Overview

JordyLab joins Claude as a remote, read-only data source. Jordy adds one HTTPS address as a custom connector in
Claude Desktop and in Claude mobile, signs in with his existing JordyLab account, and from then on asks Claude things
like "which four-player couch games do I own?", "what did the latest briefing conclude?" or "what is my portfolio worth today?". Claude answers from the live data in
JordyLab instead of guessing.

The connector is a second door into the same data, not a second app: it shows what the admin already sees in
JordyLab, obeys the same access rules, and can only read. It cannot add, change or delete anything, cannot start a
scan or a refresh, cannot spend AI tokens and cannot create a Linear issue. Asking it to do any of those things is
refused, and nothing in JordyLab changes.

## Current state (verified in the repository, 2026-11-03)

- The backend has no MCP endpoint and no MCP dependency; the only "mcp" in the repository is the GitHub MCP launcher
  used by the pull-request review workflow.
- Four modules are built: `gamecatalog`, `fna`, `feedback` and `settings`. `garmin`, `recipe` and `trading` have no
  code, so there is no health, recipe or trading data to expose.
- The backend is an OAuth2 resource server on Keycloak JWTs. Realm roles are `admin`, `guest`, `app-gamecatalog` and
  `gamecatalog-scanner`; authorization is deny-by-default in one `SecurityConfig`. FNA, settings and the feedback
  inbox are admin-only; the game catalog is admin or the per-app grant; anything unlisted is refused.
- Access tokens live 60 seconds, and the backend validates them statelessly. A revoked account or removed role stops
  working within that minute (spec 014).
- Production is a single public origin, `https://jordylab.be`. The gateway sends `/api` to the backend,
  `/auth/realms/jordylab` and `/auth/.well-known` to Keycloak, and everything else to the web app. There is no
  `.well-known` route for a connector to discover an authorization server except Keycloak's own under `/auth`.
- The production realm has the clients `jordylab-host` (public web app), `gamecatalog-script` (device-code scan
  client) and `jordylab-backend` (service account). It has no connector client and no dynamic client registration
  configured.
- Claude mobile only supports remote connectors; a local process cannot be used from the phone.

## Clarifications

### Session 2026-11-03

- Q: Which data may Claude read through the connector in v1? (FR-006) → A: Game catalog and finance only; the feedback
  inbox and user administration move to a later spec, because they hold guests' personal data.
- Q: How should Claude identify itself to JordyLab's Keycloak when Jordy connects? (FR-002) → A: One public client,
  created once in the `jordylab` realm; Jordy enters its client identifier in Claude. No secret, no experimental feature,
  no anonymous registration.
- Q: Where should each connector call be recorded: only in the backend log, or also in a database table? (FR-016) → A:
  Backend log only; no database table for connector calls.
- Q: Should the connector limit how many tool calls one admin can make per minute? (FR-020) → A: Yes, 60 calls per
  minute per admin; calls over the limit are refused, not queued.
- Q: How many items may one tool call return before it says more exist? (FR-009) → A: 50 items per call.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Connect Claude and ask about the game catalog (Priority: P1)

Jordy opens Claude Desktop, adds JordyLab as a custom connector with the JordyLab address, is sent to the JordyLab
sign-in page, signs in with his admin account, and returns to Claude. Claude now lists JordyLab's read-only tools.
Jordy asks which of his games suit four people on one couch, and Claude answers with titles, player counts and where
each copy lives. He adds the same address as a remote connector on his phone and gets the same answers there.

**Why this priority**: the connection plus one real dataset proves the whole chain — public HTTPS, standard sign-in,
admin-only access, tool discovery and a truthful answer from live data.

**Independent Test**: add the connector in Claude Desktop with the admin account, ask a catalog question, and compare
the answer with the Game Catalog screens. Repeat on Claude mobile with the same address.

**Acceptance Scenarios**:

1. **Given** Jordy is signed out of JordyLab, **When** he opens the connector, **Then** Claude shows the JordyLab
   sign-in page and no JordyLab data is returned before he signs in.
2. **Given** Jordy signed in as admin, **When** Claude lists the connector's tools, **Then** only read-only JordyLab
   tools appear, each with a description of the data it returns.
3. **Given** the connector is connected, **When** Jordy asks "which local co-op games do I own for four players?",
   **Then** Claude answers from the catalog and names only games that are visible in the app with at least four
   confirmed local players.
4. **Given** an account without the admin role tries to connect, **When** it opens any tool, **Then** access is
   refused with a clear message and no data is returned.
5. **Given** Jordy adds the same address as a remote connector in Claude mobile, **When** he signs in and asks a
   catalog question, **Then** the answer matches the desktop answer for the same data.

---

### User Story 2 - Ask about finance without opening the app (Priority: P2)

Jordy asks Claude what the latest news is about a ticker he holds, what his portfolio is worth today, which stocks
are on his wishlist, and what the last briefing concluded. Claude answers from the current articles, holdings,
wishlist and latest briefing, and says plainly when the latest briefing does not exist yet.

**Why this priority**: a second, different dataset on the same connection proves the connector generalizes beyond the
catalog, and finance is the data Jordy checks while away from the app.

**Independent Test**: sign in once, then ask a question per finance area and compare each answer with the Finance
News screens (articles, portfolio, wishlist, briefing).

**Acceptance Scenarios**:

1. **Given** articles were ingested today, **When** Jordy asks what is new about a ticker, **Then** Claude names
   recent articles about it with their publication dates and sources.
2. **Given** the portfolio holds positions, **When** Jordy asks what it is worth, **Then** Claude reports each
   position and the total using the same prices and the price timestamp that the portfolio screen shows.
3. **Given** the wishlist has entries, **When** Jordy asks what is on it, **Then** Claude lists the same tickers with
   the same last-known prices as the wishlist screen.
4. **Given** a briefing exists, **When** Jordy asks what it said, **Then** Claude summarizes that briefing and names
   its generation time; **Given** no briefing exists yet, **Then** Claude says so and does not invent one.

---

### User Story 3 - Prove the connector cannot change anything (Priority: P2)

Jordy asks Claude to add a game, mark one as played, refresh a briefing, start a library scan or create a ticket.
Claude finds no tool for it and says the connector is read-only. Jordy checks the app afterwards: nothing changed,
nothing was generated, nobody was notified, and no AI call was paid for.

**Why this priority**: the read-only promise is the condition for connecting a personal assistant to real data; it
must be demonstrated, not just documented.

**Independent Test**: ask for one change per domain, then compare the app's data, the AI call count and the feedback
inbox before and after. All are unchanged, and no new Linear issue exists.

**Acceptance Scenarios**:

1. **Given** a connected session, **When** Claude lists its tools, **Then** no tool creates, updates, deletes,
   approves, rejects, scans, refreshes, syncs, generates, posts or sends anything.
2. **Given** Jordy asks Claude to make a change, **When** Claude looks for a way, **Then** it reports that JordyLab
   offers read-only access and the change is not made.
3. **Given** a connector session ends, **When** Jordy compares the app before and after, **Then** no record has
   changed, no AI-generated content or briefing was created, no scan or refresh ran, no notification was sent and no
   Linear issue was created.
4. **Given** a tool is called with an argument that would select a forbidden action, **When** the server handles it,
   **Then** it refuses rather than guessing an alternative.

---

### Edge Cases

- **Access revoked while connected**: an account revoked or stripped of the admin role in Keycloak stops getting data
  within the 60-second token lifetime; the tool call fails with a clear sign-in or permission error, and Claude does
  not retry with cached data.
- **Wrong account**: signing in with a guest account, or with an account that is still pending approval, is refused
  before any data is read.
- **Empty data**: with no installed games, no portfolio, no wishlist or no briefing, the answer says the
  collection is empty rather than inventing entries.
- **Unknown facts**: a game whose local player count was never confirmed is reported as unknown, never inferred from
  its genre or description.
- **Very large results**: a question that would match the whole catalog or every article returns the first 50 items and
  states how many more match, so a single answer cannot dump the database.
- **Stale prices**: a price that was last fetched hours ago is reported with its fetch time; the answer does not call
  it current.
- **Work in progress in the app**: a scan, refresh run or library sync that is running does not block reads; the
  connector answers from the last committed state and can say a refresh is running.
- **Backend or database unavailable**: the tool returns a clear failure the model can explain, never partial data
  presented as complete and never internal details such as stack traces or connection strings.
- **Untrusted text**: article titles and game descriptions are data. A sentence inside them that looks
  like an instruction does not cause any action, because no action exists to take.
- **Same question, two clients**: desktop and mobile return the same values for the same underlying data.
- **No connector configured**: the web app works exactly as before; the connector is optional.

## Requirements *(mandatory)*

### Functional Requirements

**Connecting and access**

- **FR-001**: An admin MUST be able to add JordyLab to Claude Desktop and to Claude mobile as a remote connector
  using a single public HTTPS address on `https://jordylab.be`.
- **FR-002**: Connecting MUST use the existing JordyLab account through the browser sign-in that Claude uses for remote
  connectors, against one public client for the connector, created once in the `jordylab` realm. Jordy enters that
  client's identifier in Claude. No secret is copied into Claude, and the realm MUST NOT accept anonymous client
  registration for this purpose.
- **FR-003**: Only an account holding the `admin` role MUST be able to connect or call any tool. A guest, a pending
  or revoked account and the scan client's role MUST all be refused, and the refusal MUST reveal no data.
- **FR-004**: Revoking the account or the admin role MUST stop access within the existing 60-second token lifetime,
  without an extra disconnect step in Claude.
- **FR-005**: The server MUST be usable from Claude mobile, which supports remote connectors only; no local process
  on Jordy's machine may be required.

**Reading**

- **FR-006**: The server MUST expose read-only tools covering the game catalog (games, platforms, places, sources,
  marks and library state) and finance (articles, portfolio, wishlist, latest briefing). The feedback inbox and app
  administration are not exposed in v1 (see Out of Scope).
- **FR-007**: Every tool MUST obey the same access rule as the corresponding screen or API. A tool MUST NOT return
  data the admin cannot see in JordyLab.
- **FR-008**: Every answer MUST reflect the data committed at call time; the connector MUST NOT serve a snapshot
  older than the data the app itself would show.
- **FR-009**: A list-returning tool MUST return at most 50 items per call and MUST report whether more results exist. It
  MUST accept
  narrowing arguments (for example platform, source, mark, state or text) and MUST NOT return an entire table in one
  call.
- **FR-010**: A value the app does not know (for example an unconfirmed player count or a missing briefing) MUST be
  reported as unknown or absent, never inferred, guessed or filled from the model's own knowledge.

**Read-only guarantee**

- **FR-011**: No tool may create, update, delete or approve anything, and none may start a scan, library sync,
  refresh run, briefing generation, enrichment, notification, password reset, session termination or Linear issue.
- **FR-012**: No connector call may spend AI tokens or call a paid external service. The existing LibBot question
  endpoint and the briefing trigger MUST NOT be reachable through the connector.
- **FR-013**: A request that has no read-only equivalent MUST fail explicitly rather than be reinterpreted as the
  nearest read.

**Safety and observability**

- **FR-014**: Responses MUST NOT contain secrets or credentials — API keys, client secrets, SMTP or Ntfy tokens,
  database passwords, Keycloak admin credentials, the Linear API key, scan-client tokens or other people's session
  data.
- **FR-015**: Responses MUST NOT contain personal data of any account other than the admin's own, and no hidden fields.
- **FR-016**: Each tool call MUST leave one line in the backend log naming the signed-in admin, the tool name, the time
  and the outcome. Nothing about a call is stored in the database. Arguments and returned data MUST NOT be logged when
  they contain personal or financial values.
- **FR-017**: Tool descriptions MUST state what each tool returns and that the access is read-only, so the model
  chooses correctly without trial and error.
- **FR-018**: A failing tool MUST return a clear, non-sensitive error that the model can explain, and MUST NOT expose
  stack traces, SQL, internal host names or credentials.
- **FR-019**: The connector MUST be optional: if it is not configured or unreachable, the web app and every existing
  API keep working unchanged.
- **FR-020**: Each admin MUST be limited to 60 tool calls per minute. A call over the limit MUST be refused at once with
  a clear, non-sensitive "try again shortly" error, and MUST NOT be run or queued.

### Key Entities

- **Connector access grant**: the admin's permission to use the connector, held by the existing JordyLab identity and
  role; revocable with the account.
- **Read-only tool**: a named capability with a description, the data it may read and its bounds. The complete set is
  the contract with Claude.
- **Tool call record**: one backend log line per call, naming who called which tool, when and with what outcome. No
  payload and no database row.
- **Existing data read through tools**: game entries and their places, marks and player facts; finance articles,
  portfolio positions, wishlist items and briefings.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An admin who has never configured a connector can connect from Claude Desktop and from Claude mobile
  in under 5 minutes each, with no help beyond the address.
- **SC-002**: 95% of catalog and finance questions return an answer within 10 seconds for the data
  volumes JordyLab holds today.
- **SC-003**: Across a recorded test session, zero attempts to change data through the connector succeed, and no
  record in JordyLab differs afterwards.
- **SC-004**: Across every tool, a non-admin account completes zero successful calls and receives no data.
- **SC-005**: In a spot-check of ten questions spanning both v1 domains, every answer matches what the corresponding
  screen shows, or states the same reason for a difference (for example a price fetched earlier).
- **SC-006**: After an account is revoked, its next call within 60 seconds is refused and returns no data.
- **SC-007**: A review of the tool list and of representative responses finds no secret, no credential and no
  personal field the admin cannot already see.
- **SC-008**: A connector session that asks for changes produces zero new AI calls, zero briefings, zero scans,
  zero notifications and zero Linear issues.
- **SC-009**: The connector is reachable over the public HTTPS address from a phone on mobile data, not only from
  the local network or a desktop browser.
- **SC-010**: Every tool call leaves one attributable line in the backend log naming the admin, the tool and the
  outcome, and a review of those lines finds no personal or financial values.
- **SC-011**: With the connector not configured, every existing web-app journey still passes unchanged.
- **SC-012**: A test that sends 61 tool calls from one admin within one minute runs exactly 60 of them and refuses the
  61st with the "try again shortly" error, without running it.
- **SC-013**: A question that matches more than 50 games or articles returns exactly 50 items and states the full number
  of matches.

## Assumptions

- v1 covers the game catalog and finance. The feedback inbox and administration are built but stay out of v1 (see Out
  of Scope). Garmin, recipe and trading have no code and are added only when those modules exist.
- There is exactly one admin today, Jordy. "Admin role" is still the rule, so a second admin would work without a
  code change.
- Claude Desktop and Claude mobile support remote MCP connectors with browser-based OAuth and a user-entered OAuth client
  identifier with no secret. This is assumed, not yet tested: the first connection from each client verifies it.
  Keycloak 26.7.4 also offers dynamic registration and an experimental published-identity mode; neither is used.
- Read-only means: no state change, no AI generation, no external side effect and no cost. A question that can only
  be answered by generating something new is answered with what exists, or refused.
- The connector publishes what the app already knows. It is not a data warehouse, does not add caching that could go
  stale, and does not reshape or pre-aggregate the domain data beyond what a screen shows.
- No guest personal data reaches a tool in v1: guests' names, e-mail addresses and ticket text stay in the web app.
- The public address lives on the existing origin and certificate. No new domain, tunnel or hosted MCP service is
  introduced.
- The web app remains the place for changes. The connector complements it and never becomes the only way to do
  something.
- Tool count stays small enough that the model can choose without confusion (roughly one tool per data area rather
  than one per REST endpoint).

## Out of Scope

- Any write, approval, triage or configuration change through the connector.
- A natural-language "ask JordyLab" tool that runs LibBot or another model; the reasoning stays in Claude.
- The feedback inbox and app administration (users, pending sign-ups, app grants, AI model settings, guest chat limit)
  in v1. They hold guests' personal data; a later spec decides whether and how they join.
- Garmin, recipe, trading and Eufy data (modules not built).
- Guests using the connector, per-user tool scopes or sharing the connector with other people.
- A local (stdio) MCP server for Jordy's desktop, and support for MCP clients other than Claude Desktop and Claude
  mobile.
- Replacing or duplicating the web app's screens, charts or search experience.
- A public, unauthenticated dataset or an API key handed to third parties.

## Dependencies

- `specs/014-console-player-counts/`: the access matrix (admin, guest, per-app grant) and the 60-second token
  lifetime the connector reuses.
- `specs/016-fna-stock-wishlist/` and the finance module: the portfolio, wishlist and briefing data.
- `specs/018-pwa-replaces-capacitor/`: the PWA that remains the web client; the connector is a separate door and
  does not change it.
- `specs/019-compose-deployment/`: the public gateway where the connector's address and discovery routes are added.
- Keycloak: one public connector client in the `jordylab` realm, created once (FR-002), with the existing admin role as
  the only way in.

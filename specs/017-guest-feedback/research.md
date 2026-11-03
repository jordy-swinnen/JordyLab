# Guest Feedback and Admin Triage: Research (Phase 0)

Date: 2026-11-02. Grounded in the repo as of branch `017-guest-feedback` (post-015/016). Decisions marked **[J]** were made by Jordy (JOR-33, 2026-11-02, and the clarify pass); the rest are plan decisions with the alternatives that were rejected.

## How Settings opens to guests without weakening admin tabs

- **Decision**: Backend — `/api/settings/**` stays `hasRole("admin")` exactly as today; the new feedback endpoints get their own matchers in `SecurityConfig` (see the access matrix in [contracts/rest-api.md](contracts/rest-api.md)). Frontend — the host app (`apps/jordylab/src/app/app.routes.ts`) changes the `settings` route from `loadChildren: settingsRoutes` to a composed `children` array guarded `roleGuard('admin', 'guest')`; each tab re-guards itself (`users`, `ai-models` → `roleGuard('admin')`; `feedback` → `roleGuard('guest')`; `inbox` → `roleGuard('admin')`), and the empty path gets a role-aware redirect (admin → `users`, guest → `feedback`). `libs/settings/ui` exports its per-tab routes for that composition.
- **Rationale**: The backend rule "deny by default, gate in one place" is preserved — FR-003 needs no new enforcement, just no change. The host owning path composition is the documented Nx pattern ("the host mounts a domain's routes"); it is the only way to nest the feedback domain under `/settings` without `libs/settings` importing `libs/feedback`, which the Nx tag boundaries forbid.
- **Alternatives rejected**: (a) `libs/settings/ui` mounting the feedback routes — cross-domain import, breaks `@nx/enforce-module-boundaries`; (b) giving the feedback domain its own top-level path (`/feedback`) — the spec puts both tabs *inside* Settings; (c) hiding tabs in the UI only — rejected outright, the spec demands backend enforcement; (d) `@PreAuthorize` method security — nothing in the codebase uses it, URL matchers are the house style.

## Access matrix for `/api/feedback/**`

- **Decision**: `POST|GET /api/feedback/tickets` and `GET /api/feedback/apps` → `hasRole("guest")`; `/api/feedback/inbox/**` → `hasRole("admin")`. `RoleMatrixTest` gains rows for both roles and for the no-role user.
- **Rationale**: Mirrors FR-001/FR-002 exactly (guest never sees the inbox; the admin never sees the guest tab — so the admin is genuinely 403 on the guest endpoints, not just UI-hidden). Keeps the matrix trivially testable in the existing Keycloak-container test.
- **Alternatives rejected**: `hasAnyRole("admin", "guest")` on the guest endpoints with UI hiding — weaker than the spec; the admin has no journey that submits feedback.

## Linear client: hand-built GraphQL over RestClient, no new dependency

- **Decision**: `feedback/rest/client/LinearClient` posts JSON `{query, variables}` to `https://api.linear.app/graphql` with `Authorization: <personal API key>` (Linear's documented scheme — the raw key, no `Bearer` prefix; verified live during implementation with a `viewer` ping). A module-private `RestClient` with short timeouts (built in `@PostConstruct` like `KeycloakAdminClient`/`IgdbClient`) so a hanging Linear fails in seconds instead of the shared client's 120 s read timeout. Team resolved by **key** `JOR` (not name "JordyLab"), the Triage workflow state and the App/Type label ids resolved once and cached in memory, refreshed on miss.
- **Rationale**: One mutation plus three small lookups does not justify a GraphQL client library (KISS/YAGNI); the repo already has the exact pattern for bearer-style auth over RestClient (`NtfyClient`) and for cached-token/lookup clients (`IgdbClient`). Team *key* is stable where the team *name* is cosmetic.
- **Alternatives rejected**: (a) a GraphQL Java library (graphql-java / Apollo) — new dependency and schema tooling for four operations; (b) the shared `RestClient` bean — its 120 s read timeout would hold the accept flow (and the row lock below) far too long; (c) an OAuth app with `actor=app` — **[J]** rejected on 2026-11-02: workspace-admin install, no `admin` scope, and creator "JordyLab" instead of Jordy wasn't worth it.

## Accept idempotency: row lock during the Linear call + adopt-on-retry

- **Decision**: `AcceptTicketService` loads the ticket with a pessimistic write lock (`@Lock(PESSIMISTIC_WRITE)` finder) and holds it across the Linear call. In the lock: if `linearIssueId` is already set → return the stored result, no Linear call; if a previous attempt recorded an `accept_error` → **first search Linear** for an existing issue (team + exact title + description contains `Source: idea box`) and adopt it if found; otherwise create, then `markAccepted(id, url)` in the same transaction. A Linear failure records `accept_error` on the ticket and throws → `503 LINEAR_UNAVAILABLE`; the state never moved.
- **Rationale**: A double click serializes on the row lock: the second request waits (≤ the short client timeout), then sees the stored issue id and returns it — never a second issue. The only remaining duplicate window is "Linear created the issue but our transaction rolled back" (timeout/crash after success); the ticket then still carries its old state plus `accept_error`, so the retry takes the search-first path and *adopts* the existing issue instead of creating a twin. That closes every path in SC-002 with three moving parts and no extra states.
- **Alternatives rejected**: (a) an `ACCEPTING` intermediate state + conditional claim update — more states to model, plus stuck-ticket recovery logic, for no additional correctness at this volume (one admin, a few accepts a week); (b) relying only on the stored issue id — leaves the timeout-after-success hole open; (c) a Linear idempotency key — Linear's `issueCreate` has no client-supplied correlation id.

## Daily limit: count the tickets themselves

- **Decision**: The submission service counts today's tickets for the sender (`WHERE sender_subject = ? AND created_at >= start of day`, same day-window semantics as `GuestChatUsage`) inside the submit transaction; at five it refuses with `429 FEEDBACK_LIMIT_REACHED` (mirroring the existing `429 CHAT_LIMIT_REACHED`). No extra table.
- **Rationale**: Tickets *are* the usage record; a parallel usage table (like `guest_chat_usage`) exists there only because chat messages aren't stored. The index `(sender_subject, created_at)` serves both the count and the guest's own-ticket list.
- **Alternatives rejected**: (a) a `feedback_usage` table — duplication of data one query away; (b) a servlet filter like `GuestChatLimitFilter` — that filter exists because the limited endpoint belongs to another module; here the endpoint is ours, a service check is simpler and transactional. **[J]** Limit value: five per day (clarify pass).

## Notification: reuse the UserSignUpPending pattern exactly

- **Decision**: `FeedbackTicketCreated(senderName, appName, typeName)` — a record at the `feedback` module root, registered as a domain event on save. `mobile`'s `MobileNotificationListener` gains one `@ApplicationModuleListener` that publishes one Ntfy message: title "New feedback ticket", body "{sender} sent a {type} for the {app} app.", click-through `appLinkUrl("feedback-inbox")` (screen mapping added in the platform lib). No title, no description — ever.
- **Rationale**: The cross-module rule is event-only wiring; this is the third listener on the same path (`UserSignUpPending`, `BriefingReady`). Sender + app + type are metadata the admin already sees in the inbox (Ntfy is his private channel, like the sign-up notification that carries name + email), while the ticket *text* stays out per FR-017. A delivery failure is logged and swallowed by the existing client — intentional, spec-mandated.
- **Alternatives rejected**: (a) `feedback` calling `NtfyClient` directly — module boundary violation (`NtfyClient` is `mobile`'s); (b) including the ticket title in the body — violates FR-017; (c) a scheduler polling for new tickets — the domain event already fires on commit.

## Which apps a guest may pick

- **Decision**: `FeedbackAppAccessService` resolves the caller's apps from the authenticated authorities: `ROLE_guest` → `GAME_CATALOG` today. It backs both `GET /api/feedback/apps` (the form's choice list) and the submit validation, so the two can never disagree. When the user-management draft adds per-app grants, only this service changes.
- **Rationale**: Today the role matrix *is* the access source (guests are hard-wired to the Game Catalog — `RoleMatrixTest`). Centralising it in one service is the seam the spec's "reads the same access source" assumption asks for.
- **Alternatives rejected**: (a) a hardcoded list in the frontend — bypasses backend validation and drifts; (b) building a grant store now — that's the user-management feature's job (out of scope).

## Sender identity and anonymisation

- **Decision**: The ticket stores `sender_subject` (the JWT `sub`, UUID) plus a `sender_display_name` snapshot taken from the token's name/preferred-username claim at submission. A listener in `feedback` consumes the existing `shared.event.UserAccessRemoved` and clears/anonymises `sender_display_name` on that person's tickets (they stay, as history, per the spec's edge case).
- **Rationale**: The inbox needs a readable sender without a Keycloak round-trip per ticket; the snapshot also survives later profile changes. `UserAccessRemoved` already exists for exactly this pattern (gamecatalog deletes marks on it) and lives in `shared` precisely so consumer modules don't depend on the publisher.
- **Alternatives rejected**: (a) joining back to Keycloak live — an admin API call per inbox render for data that rarely changes; (b) dropping the tickets on access removal — the spec says they stay, minus personal data.

## Secret threading for the Linear key

- **Decision**: `application.yaml` gains `jordylab.feedback.linear.api-key: ${LINEAR_API_KEY:}` (empty default → the client is unconfigured; Accept then fails fast with `503 LINEAR_UNAVAILABLE`, which is also exactly what the e2e journey asserts). Prod: the value is added by the owner to the SOPS-encrypted `deploy/k8s/overlays/prod/secrets.sops.yaml` under the existing `jordylab-secrets` Secret — the backend container already `envFrom`s it, so no manifest change is needed.
- **Rationale**: Identical to every existing secret (NTFY, ANTHROPIC, STEAM…); zero new deploy machinery. The key is server-side config only — no endpoint ever returns it (FR-014).
- **Alternatives rejected**: (a) a new Kubernetes Secret + manifest — `jordylab-secrets` already exists for exactly this; (b) storing the key in the database — secrets in git-SOPS, not in DB rows, is the house rule.

## Frontend shape

- **Decision**: New domain pair `libs/feedback/{api,ui}` (tags `scope:feedback,type:api|ui`; standard self+shared boundary rules, `scope:feedback` added to the app's allowed scopes). Guest tab `feedback-page` (form + own tickets) and admin tab `inbox-page` (triage list, accept dialog with optional private note, reply/note editing), both Container–Presentation with stores (`feedback.store.ts`, `inbox.store.ts`) in the api lib. `navigation.ts` learns `requiredRole: 'guest'` (symmetric with `'admin'`): each persona sees exactly one "Feedback" entry — guest → `/settings/feedback`, admin → `/settings/inbox`.
- **Rationale**: Same audience split as the backend; the settings lib stays admin-only and untouched except for exporting its per-tab routes. The nav model change is two lines of types plus `visibleNavigationGroups` gaining the guest flag — the smallest symmetric extension of the existing contract ("omit → admin-or-guest").
- **Alternatives rejected**: (a) feedback pages inside `libs/settings/*` — couples a guest-facing flow to an admin-only lib and muddies its scope; (b) a separate top-level nav group — the spec says a tab *in* Settings.

## States, texts and limits (from the clarify pass **[J]**)

- **Decision**: States `NEW → ACCEPTED | BACKLOG | REJECTED`, `BACKLOG → ACCEPTED | REJECTED`, `ACCEPTED` and `REJECTED` terminal (Rejected final, archive view-only; Accepted final because Linear becomes the source of truth and v1 has no sync). Exactly two admin texts: guest-visible `reply` and `privateNote` — the accept dialog's optional note *is* the private note, snapshotted into the Linear issue at accept time. Types `BUG_REPORT | FEATURE_REQUEST | IMPROVEMENT | IDEA` (a "question" files as `IDEA`). Title ≤ 120 characters, description ≤ 5 000 (after trim) — fixing the spec's "on the order of 100 and 5 000".
- **Rationale**: Every item above is a recorded clarify decision; the length limits make the daily limit and abuse guard meaningful and are testable.
- **Alternatives rejected**: reopening Rejected (recoverable misclicks) — rejected for v1 minimalism; a third "accept note" text — rejected as a redundant field.

## Test seams

- **Decision**: `LinearClientTest` on WireMock (next free port in the `999x` series — 9990/9991/9992 are currently unused; confirm before use) stubs the GraphQL route and asserts the exact JSON body (query + variables) and headers, using `@Language("JSON")`. The module test (`@ApplicationModuleTest` + pgvector) covers submission, limit, transitions, own-list isolation and anonymisation with `LinearClient` mocked (Mockito, explicit values). `RoleMatrixTest` grows the feedback rows. The Playwright journey drives guest submit → admin triage → accept against the throwaway stack and asserts the graceful `LINEAR_UNAVAILABLE` state (no key there, exactly like the AI journeys assert "unavailable"); the real-key happy path is the manual quickstart.
- **Rationale**: WireMock is the house tool for outbound HTTP; e2e never spends real quota or needs secrets — the same design 011 used for paid AI calls.
- **Alternatives rejected**: hitting real Linear from automated tests — flaky, quota-consuming, secret-dependent; asserting accept success only at the unit level — the module test + WireMock cover the full accept flow including idempotency.

## Open items deliberately left to `/speckit-tasks` and implementation

- Exact GraphQL field names/fragments are sketched in [contracts/linear-client.md](contracts/linear-client.md) and must be verified once against the live schema (the shapes are the contract, the syntax may need adjusting).
- The role-aware default redirect for `/settings` (a `canMatch`-style guard vs. a tiny redirect component) — either satisfies the plan; tasks pick one.
- The mobile App-Link screen key (`feedback-inbox`) wiring inside `libs/shared/platform` follows the existing `settings-users` pattern.

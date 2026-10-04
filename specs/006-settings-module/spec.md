# Feature Specification: Settings Module (Users & AI Models)

**Feature Branch**: `006-settings-module`

**Created**: 2026-09-27

**Status**: Draft

**Module**: `settings` (new)

**Input**: User description: "Add a new Settings module to JordyLab, structured like FNA and Game Catalog (its own
backend Spring Modulith module, its own frontend domain libs, its own top-level nav tab), with two sub-pages like FNA's
Portfolio or Game Catalog's Chat: 'Users' and 'AI Models'. Context: I want to deploy JordyLab so my friends can use the
Game Catalog. Today there is one user (me) and every logged-in user can reach every API. Once self-sign-up is on, that
stops being safe. […] Out of scope: Kubernetes/OVHcloud deployment manifests, SMTP/email flows, per-guest model choice,
cost dashboards, local/Ollama inference." (full description in `specs/_drafts/settings/speckit-prompts.md` §1)

---

## Context

JordyLab is meant to be used by more than its owner: friends should get the Game Catalog. Today there is exactly one
user, and every logged-in user can reach every API — including the financial data. Opening self-sign-up would make that
unsafe overnight: research verified that today's access checks only confirm *that* someone is logged in, never *who*
they are or what they may do.

This feature adds the gate, and the admin levers for the AI that sits behind it:

1. **Users** — friends sign up through Keycloak, the admin approves them into a `guest` role, and guests can use only
   the Game Catalog (read and chat).
2. **AI Models** — every AI feature is listed with a live model picker. Calls go to OpenRouter first and fall back to
   Anthropic Claude Sonnet 5. Ollama is removed.

---

## Clarifications

### Session 2026-09-27

- Q: When rejecting a pending sign-up, should the account be deleted or disabled? (FR-006) → A: Disabled — the account
  stays listed as rejected and the email can't sign up again.
- Q: What should each guest's daily chat-message limit be? (FR-009) → A: 20 messages per calendar day, resetting at
  midnight; the admin is exempt.
- Q: Should you get a push notification when someone signs up, and if so at what priority? (FR-018 / US7) → A: Yes —
  Ntfy push on new sign-up, priority P3.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Only approved people get in (Priority: P1)

As the admin, I want strangers and unapproved sign-ups to have no access to anything, so that opening sign-up doesn't
expose my finance data.

**Why this priority**: It is the entire motivation for the feature. Today every API only checks that a caller is logged
in; self-sign-up would hand a stranger a valid login and full access to FNA. Nothing else in this spec is safe to build
without this story.

**Independent Test**: Register a new account, log in, and confirm that only the "awaiting approval" page renders and
every backend call is rejected with access denied.

**Acceptance Scenarios**:

1. **Given** a visitor who isn't logged in, **When** they open any app URL, **Then** they are sent to the Keycloak login
   screen, which offers "Register".
2. **Given** the registration form, **When** a visitor submits email, first name, last name and a password that meets
   the policy, **Then** an account is created with status *pending* and no app role.
3. **Given** a pending user who logs in, **When** they open any route, **Then** they see only the "awaiting approval"
   page, and every backend request is denied.
4. **Given** any user, **When** the admin looks at them in Settings → Users or in the Keycloak admin console, **Then**
   no password or password hash is shown — a password can be reset, never read.

---

### User Story 2 — Admin approves, rejects and revokes (Priority: P1)

As the admin, I want to manage sign-ups from inside JordyLab, so I don't have to hand out access by editing the identity
provider directly.

**Why this priority**: The gate from Story 1 is only operable if it can be worked from the app. Keycloak has no built-in
approval workflow — approval is the act of granting the `guest` role, done by JordyLab.

**Independent Test**: With one pending user, approve them and check that their next login shows the Game Catalog. Then
revoke them and check that their access is gone within one token lifetime.

**Acceptance Scenarios**:

1. **Given** pending sign-ups, **When** I open Settings → Users, **Then** I see them listed with name, email and sign-up
   date, and the Settings tab shows a badge with the pending count.
2. **Given** a pending user, **When** I click Approve, **Then** they get the `guest` role.
3. **Given** a pending user, **When** I click Reject, **Then** their account is disabled — it stays listed as rejected,
   can't log in, and the email can't sign up again.
4. **Given** an approved guest, **When** I click Revoke, **Then** the `guest` role is removed and their active sessions
   are ended.
5. **Given** any user, **When** I click Reset password, **Then** a temporary password is set that has to be changed at
   next login. I see it once, to share with them out-of-band.

---

### User Story 3 — Guests see only the Game Catalog (Priority: P1)

As the admin, I want my friends to get the Game Catalog and nothing else, so a shared deployment never exposes my
finances or my administration.

**Why this priority**: Least privilege for the people the gate lets in. This is enforced in the backend (deny by
default), not just hidden in the UI.

**Independent Test**: Log in as a guest and confirm only the Game Catalog tab renders (grid, detail, chat). Then attempt
FNA, Settings and every admin-only Game Catalog action directly; each one is denied.

**Acceptance Scenarios**:

1. **Given** a guest, **When** the app shell renders, **Then** only the Game Catalog tab is visible, with the grid,
   detail and chat sub-pages. Sources is hidden.
2. **Given** a guest, **When** they call FNA, Settings or any Game Catalog write — sources, refresh, artwork upload or
   ingest — directly, **Then** they are denied.
3. **Given** a guest who has reached the daily chat limit, **When** they send a chat message, **Then** they see a
   friendly "limit reached, resets at midnight" message and no AI call is made.

---

### User Story 4 — Resilient AI calls (Priority: P1)

As the admin, I want every AI feature to survive a provider outage, so a broken gateway never breaks the app's AI
features.

**Why this priority**: Research verified there is *no* fallback today — an unhealthy provider just returns a failure.
Every AI feature in the app depends on this story, and the fallback switch to a second vendor is what makes the primary
gateway swappable at all.

**Independent Test**: With the gateway failing for each failure reason in turn (unreachable, timeout, rate-limited, bad
key, unknown model), trigger each AI feature and confirm output still arrives, recorded as answered by the fallback.

**Acceptance Scenarios**:

1. **Given** the gateway is unreachable, times out, rate-limits, rejects the key or doesn't know the model, **When** a
   feature calls AI, **Then** the call is retried once on Anthropic Claude Sonnet 5 and the result records that the
   fallback was used, plus the actual provider and model that answered.
2. **Given** a saved model no longer appears in the gateway's list, **When** the AI Models page loads, **Then** that row
   is flagged "model unavailable", and calls go to the fallback until I pick another model.
3. **Given** both providers fail, **When** a feature calls AI, **Then** it gets an explicit failure, with no silent
   empty result (constitution principle II).

---

### User Story 5 — Everyone manages their own login details (Priority: P2)

As any logged-in user, I want to change my own name, email and password from the user menu, so I never need the admin
for routine account changes.

**Why this priority**: Reduces admin burden, but is not needed for the gate to work: the admin reset from Story 2 is the
fallback for anyone who gets stuck.

**Independent Test**: As a guest, change own password and own profile from the user menu, then log back in with the new
details.

**Acceptance Scenarios**:

1. **Given** a logged-in user (admin or guest), **When** they choose My account → Change password, **Then** they
   re-authenticate, set a new password and come back to the app.
2. **Given** a logged-in user, **When** they choose My account → Edit profile, **Then** they can change their first
   name, last name and email, and then log in with the new email.

---

### User Story 6 — Choose a model per AI feature (Priority: P2)

As the admin, I want to pick a model for each AI feature, to trade off quality against cost and speed — especially the
chat features my guests trigger, which cost me money.

**Why this priority**: Cost and quality control for the admin, built on the per-feature registry from Story 4's call
path; not required for the gate to function.

**Independent Test**: Change the model for the game-description feature, trigger one enrichment, and check that the
result reports the new model. No restart.

**Acceptance Scenarios**:

1. **Given** Settings → AI Models, **When** it loads, **Then** every registered AI feature is listed with its current
   model, the read-only fallback model, and when it last ran and which provider/model answered.
2. **Given** a feature row, **When** I open the model picker, **Then** I see the gateway's current models with vendor,
   price per million input/output tokens and context size, searchable and filterable by vendor.
3. **Given** I pick and save a new model, **When** the feature next runs, **Then** it uses that model.
4. **Given** a new AI feature added in code, **When** the app starts, **Then** it shows up in the list with its
   code-defined default model.

---

### User Story 7 — Know when someone signs up (Priority: P3)

As the admin, I want a push notification when someone registers, so I can approve or reject them promptly instead of
discovering them later.

**Why this priority**: Convenience only — the pending list and badge from Story 2 already surface sign-ups whenever I'm
in the app.

**Independent Test**: Register a new account and confirm the admin receives a push notification showing the pending
user's name and email.

---

### Edge Cases

- **A bot or stranger registers**: they can do nothing while pending, and the admin rejects them. Keycloak's brute-force
  protection stays on.
- **Someone registers again with an email already in use** — including a rejected (disabled) one: Keycloak's standard
  duplicate-email error, and a disabled account can't log in.
- **The admin revokes themselves or removes the last admin**: blocked. There must always be at least one admin.
- **The gateway's model list is unreachable**: the picker shows the cached list with a "stale since …" hint, and saving
  still works for cached model ids.
- **The admin changes their own email**: the next login uses the new email, and the session stays valid until the token
  expires.
- **A revoked guest with a still-valid access token**: their sessions are ended immediately, and any remaining access
  ends when the token expires (short token lifetime).
- **A feature's model is changed while a call is running**: the running call finishes on the old model.

---

## Requirements *(mandatory)*

### Functional Requirements

**Access control**

- **FR-001**: The system MUST have two app roles, `admin` and `guest`. Self-registered users MUST get neither role until
  approved; a new registration is "pending".
- **FR-002**: The backend MUST deny by default. FNA and Settings functionality MUST require `admin`. Game Catalog reads
  and chat MUST require `admin` or `guest`. Game Catalog writes — sources, manual refresh, artwork upload and client
  ingest — MUST require `admin`. The scan client's dedicated device role MUST keep working unchanged.
- **FR-003**: The frontend MUST show tabs and routes based on role, and MUST show an "awaiting approval" page to
  authenticated users with no app role.
- **FR-004**: Registration MUST collect email (used as the login name), first name, last name and password, and MUST
  enforce a password policy.
- **FR-005**: Passwords MUST never be stored, logged or shown by JordyLab. Credentials live only in Keycloak; the admin
  can trigger a reset, never read a password.
- **FR-006**: The admin MUST be able to list users by status (pending / approved / revoked) with name, email and sign-up
  date, and from Settings → Users Approve (grant `guest`), Reject (disable the account — it stays listed as rejected and
  the email can't sign up again), Revoke (remove `guest` and end sessions) and Reset password (temporary, must be
  changed at next login, shown once to the admin to share out-of-band).
- **FR-007**: There MUST always be at least one admin; removing the last one MUST be blocked.
- **FR-008**: Every user MUST be able to change their own password, name and email without admin help.
  *(Implementation note, spec 011 BUG-011: email is the username in this realm, so Keycloak 26.4+ changes it only
  through the `UPDATE_EMAIL` action, which the realm must enable — see `deploy/keycloak/README.md`.)*
- **FR-009**: Each guest MUST have a daily limit of 20 chat messages, resetting each calendar day at midnight. The count
  MUST survive a backend restart. The admin is exempt.
- **FR-010**: Admin-only service credentials used to manage users MUST never reach the browser.
- **FR-018**: On a new sign-up, the admin MUST be notified by Ntfy push, showing the pending user's name and email.

**AI models**

- **FR-011**: The primary AI provider MUST be OpenRouter, reached through its OpenAI-compatible API and configured by
  base URL and API key through configuration/secrets, so another compatible gateway can replace it without code changes.
- **FR-012**: The fallback provider MUST be Anthropic direct with Claude Sonnet 5. When the primary fails (unreachable,
  timeout, rate-limited, auth error, model unavailable), the same call MUST be retried once on the fallback, and the
  result MUST record which provider and model actually answered.
- **FR-013**: AI configuration MUST be per feature, not per module. The current features are: the FNA daily briefing,
  Game Catalog enrichment (game descriptions), Game Catalog chat query understanding, and Game Catalog chat answer
  writing.
- **FR-014**: AI features MUST be registered in code with a default model. Saved choices MUST override the default. A
  change MUST apply from the next AI call, with no restart.
- **FR-015**: The model picker MUST be filled live from the gateway's model list (cached), showing each model's vendor,
  price per million input/output tokens and context size, and MUST support search and filter by vendor.
- **FR-016**: Every AI call MUST record the feature, the provider and model that actually answered, whether the fallback
  was used, and the outcome — for usage and cost visibility and the "last run" display per feature.
- **FR-017**: All local-LLM (Ollama) support MUST be removed from the product — code, configuration, build and
  documentation.

### Key Entities *(include if feature involves data)*

- **AppUser**: a view over Keycloak, not stored by JordyLab — id, email, first name, last name, created at, status (
  pending / approved / revoked).
- **AiFeature** (code registry): key, display name, module, description, default model.
- **AiFeatureModelSetting**: feature key, model id, updated at, updated by.
- **AiFeatureLastRun**: feature key, provider, model, fallback used, outcome, timestamp — stored or derived from
  recorded call outcomes.
- **GuestChatUsage**: user, date, message count.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A pending or guest user is denied on 100% of admin-only functionality, verified by automated tests for
  each access-controlled function group.
- **SC-002**: Approving a user takes no more than 2 clicks from the Settings tab, and they have access on their next
  login.
- **SC-003**: Changing a feature's model applies on the next AI call, with 0 restarts.
- **SC-004**: When the gateway is down, 100% of AI features still produce output through the fallback, verified by
  automated tests for each failure reason.
- **SC-005**: No reference to Ollama is left in code, build or configuration; documentation mentions it only as history.
- **SC-006**: The AI Models page loads in under 2 s using the cached model list.

---

## Assumptions

- The module is named `settings` (settled with the feature's short name; the alternative `config` was considered and set
  aside).
- Keycloak remains the identity provider. There is no SMTP, so there are no verification or forgot-password emails; a
  forgotten password means the admin resets it from Settings → Users.
- OpenRouter is the chosen primary gateway (decision and comparison in `specs/_drafts/settings/research.md` §1); the
  base URL and key stay configurable, so any OpenAI-compatible gateway can be swapped in without code changes.
- The fallback is Anthropic direct — a different vendor from the gateway — so it survives a gateway outage.
- The OpenCode Go subscription stays in use for coding agents only, not for app traffic.
- Guests get read + chat only; the scanner device flow (its dedicated role) is unchanged.
- Kubernetes/OVHcloud deployment manifests, TLS and the production Keycloak hostname are covered by a separate
  deployment spec.

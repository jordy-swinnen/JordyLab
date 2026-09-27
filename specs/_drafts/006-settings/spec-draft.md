# Feature Specification: Settings Module (Users & AI Models)

**Feature Branch**: `006-settings-module`
**Created**: 2026-09-27
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)

---

## Overview

A new admin-only **Settings** module with two sub-pages:
1. **Users**: friends sign up through Keycloak, the admin approves them into a `guest` role, and guests can use only the Game Catalog (read and chat).
2. **AI Models**: every AI feature is listed with a live model picker. Calls go to OpenRouter first and fall back to Anthropic Claude Sonnet 5. Ollama is removed.

---

## User Scenarios & Testing

### User Story 1: Only approved people get in (Priority: P1)

As the admin, I want strangers and unapproved sign-ups to have no access to anything, so that opening sign-up doesn't expose my finance data.

**Independent Test**: Register a new account, log in, and confirm that only the "awaiting approval" page renders and every `/api/**` call returns 403.

**Acceptance Scenarios**:
1. **Given** a visitor who isn't logged in, **When** they open any app URL, **Then** they are sent to the Keycloak login screen, which offers Register.
2. **Given** the registration form, **When** a visitor submits email, first name, last name and a password that meets the policy, **Then** an account is created with status *pending* and no app role.
3. **Given** a pending user who logs in, **When** they open any route, **Then** they see only the "awaiting approval" page, and every API request is rejected with 403.
4. **Given** any user, **When** the admin looks at them in Settings → Users or in the Keycloak admin console, **Then** no password or password hash is shown.

### User Story 2: Admin approves, rejects and revokes (Priority: P1)

As the admin, I want to manage sign-ups from inside JordyLab.

**Independent Test**: With one pending user, approve them and check that their next login shows the Game Catalog. Then revoke them and check that their access is gone within one token lifetime.

**Acceptance Scenarios**:
1. **Given** pending sign-ups, **When** I open Settings → Users, **Then** I see them listed with name, email and sign-up date, and the Settings tab shows a badge with the pending count.
2. **Given** a pending user, **When** I click Approve, **Then** they get the `guest` role.
3. **Given** a pending user, **When** I click Reject, **Then** their account is removed.
4. **Given** an approved guest, **When** I click Revoke, **Then** the `guest` role is removed and their active sessions are ended.
5. **Given** any user, **When** I click Reset password, **Then** a temporary password is set that has to be changed at next login. I see it once, to share with them out-of-band.

### User Story 3: Guests see only the Game Catalog (Priority: P1)

**Acceptance Scenarios**:
1. **Given** a guest, **When** the app shell renders, **Then** only the Game Catalog tab is visible, with the grid, detail and chat sub-pages. Sources is hidden.
2. **Given** a guest, **When** they call FNA, Settings or any Game Catalog write, sources, refresh, artwork or ingest API directly, **Then** they get 403.
3. **Given** a guest who has reached the daily chat limit, **When** they send a chat message, **Then** they see a friendly "limit reached, resets at …" message and no AI call is made.

### User Story 4: Everyone manages their own login details (Priority: P2)

**Acceptance Scenarios**:
1. **Given** a logged-in user (admin or guest), **When** they choose My account → Change password, **Then** they re-authenticate, set a new password and come back to the app.
2. **Given** a logged-in user, **When** they choose My account → Edit profile, **Then** they can change their first name, last name and email, and then log in with the new email.

### User Story 5: Choose a model per AI feature (Priority: P2)

As the admin, I want to pick a model for each AI feature, to trade off quality against cost and speed.

**Independent Test**: Change the model for the game-description feature, trigger one enrichment, and check that the result reports the new model. No restart.

**Acceptance Scenarios**:
1. **Given** Settings → AI Models, **When** it loads, **Then** every registered AI feature is listed with its current model, the read-only fallback model, and when it last ran and which provider/model answered.
2. **Given** a feature row, **When** I open the model picker, **Then** I see the gateway's current models with vendor, price per million input/output tokens and context size, searchable and filterable by vendor.
3. **Given** I pick and save a new model, **When** the feature next runs, **Then** it uses that model.
4. **Given** a new AI feature added in code, **When** the app starts, **Then** it shows up in the list with its code-defined default model.

### User Story 6: Resilient AI calls (Priority: P1)

**Acceptance Scenarios**:
1. **Given** the gateway is unreachable, times out, rate-limits, rejects the key or doesn't know the model, **When** a feature calls AI, **Then** the call is retried once on Anthropic Claude Sonnet 5 and the result records `fallbackUsed=true` plus the actual provider and model.
2. **Given** a saved model no longer appears in the gateway's list, **When** the AI Models page loads, **Then** that row is flagged "model unavailable", and calls go to the fallback until I pick another model.
3. **Given** both providers fail, **When** a feature calls AI, **Then** it gets an explicit failure, with no silent empty result (constitution principle II).

### Edge Cases
- A bot or stranger registers: they can do nothing while pending, and the admin rejects them. Keycloak brute-force protection stays on.
- Someone registers again with an email already in use: Keycloak's standard duplicate-email error.
- The admin revokes themselves or removes the last admin: blocked. There must always be at least one admin.
- The gateway's `/models` is unreachable: the picker shows the cached list with a "stale since …" hint, and saving still works for cached ids.
- The admin changes their own email: the next login uses the new email, and the session stays valid until the token expires.
- A revoked guest with a still-valid access token: access ends when the token expires (short token lifetime), and their sessions are also ended immediately.
- Changing a feature's model while a call is running: the running call finishes on the old model.

---

## Requirements

### Functional: Access control
- **FR-001**: The system MUST have two app roles, `admin` and `guest`. Self-registered users get neither role until approved.
- **FR-002**: The backend MUST deny by default. `/api/fna/**` and `/api/settings/**` require `admin`. Game Catalog reads and chat require `admin` or `guest`. Game Catalog writes, sources, refresh, artwork upload and client ingest require `admin`. Scanner ingest keeps its `gamecatalog-scanner` role.
- **FR-003**: The frontend MUST show tabs and routes based on role, and show an "awaiting approval" page to authenticated users with no app role.
- **FR-004**: Registration MUST collect email (used as the login name), first name, last name and password, and MUST enforce a password policy.
- **FR-005**: Passwords MUST never be stored, logged or shown by JordyLab. Credentials live only in Keycloak.
- **FR-006**: The admin MUST be able to list users by status and approve, reject, revoke (including ending sessions) and reset passwords (temporary) from Settings → Users.
- **FR-007**: There MUST always be at least one admin.
- **FR-008**: Every user MUST be able to change their own password, name and email without admin help.
- **FR-009**: Each guest MUST have a daily limit on chat messages (value to confirm in clarify; draft: 30). The count MUST survive a backend restart. Admin is exempt.
- **FR-010**: Admin-only credentials used to manage users (the service account) MUST never reach the browser.

### Functional: AI models
- **FR-011**: The primary AI provider MUST be OpenRouter, reached through its OpenAI-compatible API and configured by base URL and API key through environment/secret.
- **FR-012**: The fallback provider MUST be Anthropic direct with Claude Sonnet 5, and it MUST be used for any primary failure listed in US6.
- **FR-013**: AI configuration MUST be per feature, not per module. The current features are `fna.briefing`, `gamecatalog.enrichment`, `gamecatalog.chat.query` and `gamecatalog.chat.answer`.
- **FR-014**: Features MUST be registered in code with a default model. Saved choices override the default. A change applies from the next call, with no restart.
- **FR-015**: The model picker MUST be filled from the gateway's live model list (cached), showing vendor, pricing and context size.
- **FR-016**: Every AI call MUST record the feature, the provider and model that actually answered, whether the fallback was used, and the outcome, for both metrics and the "last run" display.
- **FR-017**: All Ollama/local-LLM code, dependencies, compose services and documentation MUST be removed.

### Key Entities
- **AppUser** (a view over Keycloak; not stored in JordyLab): id, email, first name, last name, created at, status (pending / approved / revoked).
- **AiFeature** (code registry): key, display name, module, description, default model.
- **AiFeatureModelSetting** (`settings` schema): feature key, model id, updated at, updated by.
- **AiFeatureLastRun** (`settings` schema, or derived from metrics): feature key, provider, model, fallback used, outcome, at.
- **GuestChatUsage**: user subject, date, count.

---

## Success Criteria
- **SC-001**: A pending or guest user gets 403 on 100% of admin-only endpoints (proven by an automated test for each endpoint group).
- **SC-002**: Approving a user takes no more than 2 clicks from the Settings tab, and they have access on their next login.
- **SC-003**: Changing a feature's model applies on the next call, with 0 restarts.
- **SC-004**: When the gateway is down, 100% of AI features still produce output through the fallback (proven with WireMock for each failure reason).
- **SC-005**: No reference to Ollama is left in code, build or compose. Docs mention it only as history.
- **SC-006**: The AI Models page loads in under 2 s using the cached model list.

---

## Assumptions
- Keycloak 26.3 stays the identity provider. There's no SMTP, so there are no verification or forgot-password emails.
- OpenRouter is the chosen primary gateway (see research.md). The base URL and key stay configurable, so another OpenAI-compatible gateway can be swapped in without code changes.
- The OpenCode Go subscription stays in use for coding agents only, not for app traffic.
- Kubernetes/OVHcloud manifests, TLS and the production Keycloak hostname are covered by a separate deployment spec.

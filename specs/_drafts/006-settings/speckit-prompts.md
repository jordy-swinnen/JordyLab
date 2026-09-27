# 006 Settings Module: SpecKit Prompts

Run these in order. Step 1 is the **what/why** and goes to `/speckit-specify`. Step 3 is the **how** and goes to `/speckit-plan`. Background is in `research.md`, and `spec-draft.md` is a reference for what the output should look like.

---

## 1. `/speckit-specify`

> **Numbering:** spec 005 is in development elsewhere and isn't in this checkout's `specs/`, so SpecKit would auto-number this as 005. Force 006: the prompt below starts with that instruction (the script accepts `--number 6`). Check the created folder/branch is `006-settings-module` before continuing.

```
Use feature number 006 (short name: settings-module). 005 is taken by a feature in development on another branch.

Add a new Settings module to JordyLab, structured like FNA and Game Catalog (its own backend Spring Modulith module, its own frontend domain libs, its own top-level nav tab), with two sub-pages like FNA's Portfolio or Game Catalog's Chat: "Users" and "AI Models".

Context: I want to deploy JordyLab so my friends can use the Game Catalog. Today there is one user (me) and every logged-in user can reach every API. Once self-sign-up is on, that stops being safe.

USERS & ACCESS
- Anyone opening the app sees the Keycloak login screen, which offers "Register". Registering asks for email, first name, last name and password. Email is the login name. Passwords are only ever stored hashed by Keycloak. I (the admin) must never be able to see a password, only trigger a reset.
- Two roles: admin (only me) and guest. A new registration gets no role. It is "pending" and, after logging in, sees only an "awaiting approval" page. It can reach no other page and no API.
- Settings → Users (admin only) lists pending, approved and rejected/revoked users with name, email and sign-up date. I can Approve (grant guest), Reject (remove the pending account), Revoke (remove guest from an approved user and end their sessions) and Reset password (the user must set a new password at next login). The Settings tab shows a badge with the number of pending sign-ups.
- Guests see ONLY the Game Catalog tab, and within it only the grid, the game detail page and "Ask the catalog" chat. Sources, manual refresh, artwork upload and ingest are admin only. FNA and Settings are admin only. This is enforced in the backend (deny by default), not just hidden in the UI.
- Everyone (admin and guests) can change their own name, email and password from a "My account" entry in the user menu. There is no email server for now: no verification mails, no self-service "forgot password". A forgotten password means I reset it from Settings → Users.
- Guest chat usage costs me money, so each guest has a daily chat-message limit. Admin is exempt.

AI MODELS
- Replace the AI provider setup: the primary provider is OpenRouter, a multi-vendor LLM gateway reached through one OpenAI-compatible API (base URL + API key from configuration, so the gateway stays swappable). The fallback is Anthropic direct with the latest Sonnet (Claude Sonnet 5). When the primary fails (unreachable, timeout, rate-limited, auth error, model unavailable), the same call is retried once on the fallback, and the result records which provider and model actually answered. Ollama/local LLMs are removed from the project entirely.
- Settings → AI Models (admin only) lists every AI feature in the app, one row each, with a model selector: FNA daily briefing, Game Catalog enrichment (game descriptions), Game Catalog chat query understanding, and Game Catalog chat answer writing. The selector is filled live from the gateway's model list and shows each model's price per million input/output tokens and context size. Search/filter by vendor (OpenAI, Anthropic, GLM/Zhipu, Kimi/Moonshot, DeepSeek, Qwen, ...).
- A change takes effect on the next AI call, with no restart. Each row also shows the fallback model (read-only), and which provider/model last answered for that feature and when.
- If a saved model disappears from the gateway's list, the row is flagged and calls use the fallback until I pick a new model.
- Adding a new AI feature in code (e.g. the future FNA investment co-pilot) must make it appear in this list automatically, with a sensible default model.

Out of scope: Kubernetes/OVHcloud deployment manifests, SMTP/email flows, per-guest model choice, cost dashboards, local/Ollama inference.
```

---

## 2. `/speckit-clarify`

Let it ask. The expected questions and my suggested answers are in `research.md` §4: chat limit 30/day, reject = delete, Ntfy notification on sign-up as P3, module name `settings`.

---

## 3. `/speckit-plan`

```
Tech context for the Settings module (read AGENTS.md, the constitution, and specs/_drafts/006-settings/research.md first, and carry its findings into this feature's research.md; verify every library claim against live docs before committing to it, and report rather than guess if something doesn't match):

Backend (jordylab-be, Spring Boot 4.0.3, Java 25, Spring Modulith, Spring AI 2.0.0-M2):
- New module `dev.jordy.jordylab.settings` with its own Flyway schema `settings`. Use /new-module, /entity, /flyway-migration, /test-builder. Run /modularity-check at the end.
- Primary provider: Spring AI OpenAiChatModel with base-url pointing at OpenRouter (https://openrouter.ai/api/v1). Verify the base-url / completions-path split in 2.0.0-M2. Key via env OPENROUTER_API_KEY. Keep AnthropicChatModel (claude-sonnet-5) as the fallback. Remove spring-ai-starter-model-ollama, testcontainers-ollama, the commented ollama compose service, and Ollama/WireGuard guidance in AGENTS.md (flag Hetzner mentions as outdated → OVHcloud).
- Refactor shared/ai: replace per-module `jordylab.ai.modules` with an AiFeature registry (enum or registered beans: fna.briefing, gamecatalog.enrichment, gamecatalog.chat.query, gamecatalog.chat.answer), each with a default model in application.yaml. ResilientAiService.call(AiFeature, system, user) resolves the model from the settings store (cached, invalidated on update via a Modulith application event), tries primary, then falls back to Anthropic on UNREACHABLE/TIMEOUT/RATE_LIMITED/AUTH_FAILED/model-not-found. AiCallResult records the actual provider+model+fallbackUsed. Record Micrometer counters per feature/provider/outcome. Keep the multi-generation text extraction.
- Model catalog: fetch the gateway /models, cache ~1h, expose a trimmed DTO (id, name, vendor, pricing in/out per MTok, context_length, expiring flag). Filter to text-output chat models.
- Users: a confidential Keycloak client (service account, realm-management view-users + manage-users) used via keycloak-admin-client or a RestClient against the Admin REST API. Pick one and justify with the Keycloak 26.3 compatibility matrix. Endpoints under /api/settings/users (list by status, approve, reject, revoke + logout sessions, reset password with temporary=true).
- SecurityConfig: realm roles `admin` and `guest` replace `jordylab-user`. Deny by default: /api/fna/** and /api/settings/** → admin; Game Catalog GET + chat → admin|guest; Game Catalog writes/sources/refresh/artwork/ingest-client → admin; ingest scan/check stay gamecatalog-scanner. Guest chat rate limit per JWT subject per day (persisted, so restarts don't reset it).
- Realm export: registrationAllowed=true, registrationEmailAsUsername=true, editUsernameAllowed=false, password policy, no default app role, user jordy → admin, new confidential client. Keep the jordylab login theme and style the registration page too.

Frontend (jordylab-fe, Angular 21 zoneless, Nx, spartan/ui, Night Lab tokens):
- libs/settings/api (signal stores via /angular-signal-store) and libs/settings/ui (users-page, ai-models-page, presentation components). Tests per /angular-test.
- libs/shared/auth: expose roles as a signal from the token; add roleGuard; an "awaiting approval" page for authenticated users with no app role; "My account" menu actions via keycloak-js login({ action: 'UPDATE_PASSWORD' }) / UPDATE_PROFILE (verify the AIA support and the email-as-username behaviour on Keycloak 26.3, and document what you find in research.md).
- App shell nav built from roles; Settings tab with a pending-count badge. Apply the same guards to the standalone fna/gamecatalog dev apps.

Tests: Testcontainers Keycloak for role enforcement (guest gets 403 on /api/fna/**, pending user gets 403 everywhere), WireMock for the gateway (fallback triggered for each failure reason, model-not-found → fallback + flag), ModularityTests green.

Stop and report before: removing the jordylab-user role, changing the realm export, or deleting Ollama config.
```

---

## 4. Then run `/speckit-tasks` → `/speckit-analyze` → `/speckit-implement`

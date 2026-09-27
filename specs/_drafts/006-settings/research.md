# 006 Settings Module: Research & Sanity Check

Date: 2026-09-27. Checked against the repo on branch `004-gamecatalog-refinements` and the live vendor docs.

## TL;DR

| # | Your assumption | Verdict | Impact on spec |
|---|---|---|---|
| 1 | Use the OpenCode Go key as the primary provider for app features | **Risky.** Go is for coding-agent traffic only | **Decided:** OpenRouter as the primary gateway (see §1) |
| 2 | `opencode.ai/zen/go/v1/models` lists every model | **Partly true.** The endpoint exists, but the list doesn't match the plan and has no protocol info | Moot if Go isn't the primary. The model picker should use the gateway's own `/models` |
| 3 | "Latest Anthropic Sonnet" as fallback | **OK.** The latest Sonnet is **Claude Sonnet 5** (`claude-sonnet-5`), already set in `application.yaml` | No change |
| 4 | `ResilientAiService` already does primary → fallback | **No.** Today it calls Anthropic only. There's no fallback; an unhealthy provider just returns a failure | Fallback is new work |
| 5 | "No page is reachable unauthenticated, Keycloak handles it" | **True today, but it breaks once sign-up is on.** Every `/api/**` endpoint only checks that you're logged in, not your role | Role-based authorisation is a must-have (see §3) |
| 6 | Keycloak can do "admin approves the email" | **Not built in.** Keycloak has no approval workflow | Approval = granting the `guest` role, done in-app through the Keycloak Admin API |
| 7 | "Secured password, I can't see it" | **True.** Keycloak stores only a salted hash, and neither the admin console nor the Admin API returns it | You can *reset* a password, never *read* one |
| 8 | AI features: "FNA stock advice", "game descriptions" | **Partly right.** There's no stock-advice feature yet. The FNA AI feature is the **daily briefing**. There are **4** AI call sites, not 2 | The feature list below is taken from the code |
| 9 | Drop Ollama / local LLMs | **Safe.** Nothing calls Ollama at runtime today, and no embeddings are in use | Cleanup is dependencies, compose and docs. Note: the FNA MVP3 pgvector RAG will later need a non-Ollama embedding provider |

## 1. The provider: OpenCode Go vs. alternatives

### OpenCode Go (what you asked for)
- The models endpoint is real: `GET https://opencode.ai/zen/go/v1/models` returns an OpenAI-style list (`id`, `object`, `created`, `owned_by`). I fetched it on 2026-09-27 and got **43 ids**, including `glm-5.3`, `kimi-k3`, `deepseek-v4-pro`, `qwen3.8-max`, `minimax-m3`, `gpt-6-luna` and `grok-4.7`. I couldn't curl it from the sandbox (the domain isn't allowlisted), so **I haven't checked whether it needs your API key**. Run `curl -H "Authorization: Bearer $OPENCODE_API_KEY" https://opencode.ai/zen/go/v1/models` yourself.
- **Three problems:**
  1. **Terms.** The docs say: *"OpenCode Go is designed for OpenCode and other coding agents that produce similar types of requests."* Clients must *"send typical coding agent traffic"*, send their own user agent and send an `x-opencode-session` header. A finance briefing, game descriptions and your friends' chat aren't coding traffic.
  2. **Mixed protocols.** Each model has its own endpoint: `/chat/completions` (GLM, Kimi, DeepSeek), `/messages` (MiniMax), `/responses` (Grok, GPT). The `/models` list doesn't say which, so a picker built on it can offer models the backend can't call without a hand-kept mapping.
  3. **The list doesn't match the plan.** The docs list 35 Go models, but the endpoint returns 43, including older ones (`glm-5`, `kimi-k2.5`) and odd entries (`omen-alpha`).
- Limits: $10/month, with rolling caps of 20% per 5 hours, 50% per week and 100% per month. Friends using chat could drain the quota you need for coding.

**Keep Go for what it's built for: OpenCode as your coding agent.**

### Alternatives that cover OpenAI, Anthropic, GLM, Kimi, DeepSeek, Qwen and more

| | **OpenRouter** (chosen) | **OpenCode Zen** | **LiteLLM (self-hosted)** |
|---|---|---|---|
| Models | Hundreds from all major labs, including Claude, GPT, GLM, Kimi, DeepSeek, Qwen and Gemini | 80+ curated models: Claude, GPT, Gemini, Grok, DeepSeek, Qwen, GLM, Kimi | Whatever you have keys for |
| Protocol | **One OpenAI-compatible `/chat/completions` for every model** (`https://openrouter.ai/api/v1`) | Varies per model: chat/completions, messages, responses, Google | One OpenAI-compatible proxy |
| Models endpoint | `/api/v1/models` returns `id`, `name`, `pricing`, `context_length`, `supported_parameters`, `architecture`, `expiration_date` | `/zen/v1/models` returns ids only, no protocol info | Config-driven |
| Pricing | Provider prices passed through with no markup, plus a **5.5% fee on credit purchases** | Pay-as-you-go at provider prices, plus a card fee of 4.4% + $0.30 per top-up | Free software, but you pay each vendor directly and run one more pod |
| Privacy | Per-request **ZDR** (zero data retention) routing, plus data-residency routing controls | Hosted in the US. Zero retention for most models, 30 days for OpenAI/Anthropic | Direct to each vendor |
| Non-coding use | Allowed (general API) | Nothing in the docs forbids it, but it's positioned for coding | n/a |

**Why OpenRouter (decided 2026-09-27):** its `/models` metadata (price, context size, supported parameters, expiry date) is exactly what a per-feature model picker needs. You can show the price per MTok next to each model and hide models that are expiring or can't do what you need. Because it's a single protocol, the backend needs **one** Spring AI `OpenAiChatModel` with a changed `base-url`: no per-model routing table and no second SDK.

**Fallback stays Anthropic direct** (`claude-sonnet-5` through the existing `spring-ai-starter-model-anthropic`). If the gateway goes down, a Claude model routed *through* the gateway fails with it, so the fallback needs a separate vendor. That's what makes it resilient.

Build it against a **configurable base URL + key**. Switching to Zen later, or back to Go if its terms change, is then a config change for any model on `/chat/completions`.

> To verify in the plan phase: that OpenRouter's `/models` is readable without a key, and that Spring AI 2.0.0-M2's `OpenAiChatModel` works with the OpenRouter base URL (the path is `/api/v1/chat/completions`, so check the `completions-path` / `base-url` split).

## 2. The AI features, taken from the code

The config today is **per module** (`jordylab.ai.modules.fna|gamecatalog`), but there are 4 call sites:

| Feature key (proposed) | Where | What it does | Good fit |
|---|---|---|---|
| `fna.briefing` | `BriefingGeneratorService` | Daily market briefing from articles and portfolio | Heavy reasoning model |
| `gamecatalog.enrichment` | `EnrichmentService` | AI game description and multiplayer metadata | Fast, cheap model (batch job) |
| `gamecatalog.chat.query` | `ChatService` (translation prompt) | Turns the user's question into a structured catalog filter | Fast model with reliable structured output |
| `gamecatalog.chat.answer` | `ChatService` (composition prompt) | Writes the chat answer from the matched games | Mid-tier model. **Guests trigger this one**, so it costs you money |

"FNA stock advice" doesn't exist yet. It's the MVP4 Embabel co-pilot. The feature registry should make adding it a one-line change.

## 3. Auth: what has to change

Current state (`SecurityConfig`, `keycloak-realm-export.json`):
- `registrationAllowed: false`, roles `jordylab-user` and `gamecatalog-scanner`, a single user `jordy`, no SMTP, `bruteForceProtected: true`, PKCE public client `jordylab-host`.
- `/api/**` only requires `.authenticated()`. **Once self-registration is on, an unapproved sign-up gets a valid token and full FNA API access.** The frontend `authGuard` only checks login too, and the nav shows every tab.

What's needed:
- Realm roles `admin` and `guest`. Move `jordy` from `jordylab-user` to `admin`, and re-point `/api/gamecatalog/ingest/client` to `admin`. Keep `gamecatalog-scanner`.
- Backend: `/api/fna/**` and `/api/settings/**` → `admin`. Game Catalog reads and chat → `admin` or `guest`. Sources, refresh, artwork upload and ingest → `admin`. Deny by default.
- Frontend: role-aware route guards, and a nav built from roles (guests see only the Game Catalog tab). Logged-in users without a role get an "awaiting approval" page. The standalone `fna` and `gamecatalog` dev apps need the same guard.
- Registration: `registrationAllowed: true`, `registrationEmailAsUsername: true` (you log in with your email), first and last name, and a password policy (e.g. `length(12) and notUsername and notEmail`). New users get **no** app role by default.
- Approval in-app: a new **confidential** Keycloak client with a service account holding `realm-management` → `view-users` and `manage-users`, called from the backend. The secret is a k8s Secret and is never sent to the browser.
- Self-service without SMTP:
  - Password: Keycloak application-initiated action `kc_action=UPDATE_PASSWORD` (re-authenticates, then shows the change form). Supported and stable.
  - Name and email: Keycloak's Account Console, or `kc_action=UPDATE_PROFILE`. The `update-email` feature and its `UPDATE_EMAIL` action need a verification mail, so without SMTP the email is edited as a profile field. **Known issue:** with email-as-username, changing the email doesn't always update the username ([keycloak#13988](https://github.com/keycloak/keycloak/issues/13988), [#16679](https://github.com/keycloak/keycloak/issues/16679)). Test this against Keycloak 26.3 in the plan phase.
  - Forgot password: no self-service. The admin triggers a reset from Settings → Users, which sets a temporary password that has to be changed at next login.
- Open sign-up on a public URL invites bot accounts. Pending accounts can do nothing, and Keycloak's brute-force protection is already on. The spec adds a pending-list cleanup (reject = delete). Keycloak's reCAPTCHA registration step is optional hardening.

### OVHcloud k8s notes (out of scope for this spec)
- Keycloak runs `start-dev` with `KC_HOSTNAME=localhost`, and the redirect URIs are localhost only. Production needs `start`, a real hostname, TLS at the ingress and prod redirect URIs.
- The compose comments and AGENTS.md still say "Hetzner VPS", and AGENTS.md still documents WireGuard → Ollama. Update these in the Ollama cleanup.

## 4. Open questions for `/speckit-clarify`
1. Guest chat budget: how many chat messages per guest per day? (Draft: 30/day, admin exempt.)
2. Rejecting a sign-up: delete the Keycloak account, or disable it so the email can't sign up again? (Draft: delete.)
3. Should a new sign-up notify you by Ntfy push? You already run Ntfy. (Draft: yes, P3.)
4. Settings-module name: `settings` (draft) or `config`?

## Sources
- [OpenCode Go docs](https://opencode.ai/docs/go/): endpoints, models list, "designed for coding agents" terms, limits
- [OpenCode Go models endpoint](https://opencode.ai/zen/go/v1/models), fetched 2026-09-27
- [OpenCode Zen docs](https://opencode.ai/docs/zen/): models, per-model endpoints, pricing, retention
- [OpenRouter FAQ](https://openrouter.ai/docs/faq): fees, pass-through pricing
- [OpenRouter Models API](https://openrouter.ai/docs/guides/overview/models): endpoint and fields
- [OpenRouter ZDR](https://openrouter.ai/docs/guides/features/zdr) · [OpenRouter data residency](https://openrouter.ai/blog/insights/ai-data-residency/)
- [Claude models overview](https://platform.claude.com/docs/en/about-claude/models/overview): Claude Sonnet 5 = `claude-sonnet-5`
- Keycloak update-email issues: [#13988](https://github.com/keycloak/keycloak/issues/13988), [#16679](https://github.com/keycloak/keycloak/issues/16679), [#27234](https://github.com/keycloak/keycloak/issues/27234)
- [Baeldung: Keycloak user self-registration](https://www.baeldung.com/keycloak-user-registration)

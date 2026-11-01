# Feature Draft: AI ingestion pipeline hardening

**Status**: Draft (unnumbered, SpecKit assigns a number when this is specified)
**Target path**: `specs/_drafts/ai-ingestion-pipeline/spec.md`
**Consolidates**: `ai-calls-outside-transactions`, `ai-token-usage-and-budget`, `ai-prompts-as-resources`, `ai-evals-golden-set`

> Written without access to the four existing drafts or the repo. Before `/speckit-specify`, diff this against those drafts, keep what they say that this misses, then delete them. Everything under "Facts to verify" is an assumption taken from `jordylab-current-state.md` (checked 2026-10-07).

## Problem

1. **LLM calls can run inside DB transactions.** A call takes seconds to minutes. If it runs inside a transaction, it holds a connection from the pool for that time, and a late failure rolls back unrelated work.
2. **Deploys will interrupt AI work.** Once every green merge to `main` deploys (single node, so the pod restarts each time), any job that only lives in memory is lost. AI work must survive restarts.
3. **Cost is invisible.** `ResilientAiService` publishes `AiCallCompleted` and counts `jordylab.ai.calls`, but nothing records tokens or money per feature, model or provider. OpenRouter-first with an Anthropic retry means the real cost depends on which path was taken.
4. **Changes are untested.** Swapping a model in Settings → AI Models or editing a prompt has no check that output quality stayed the same.

## Scope

All AI features routed through `ResilientAiService.call(AiFeature, ...)`:

| Feature | Kind | Parts that apply |
|---|---|---|
| `fna.briefing` | background (scheduled) | all |
| `gamecatalog.enrichment` | background (per game) | all |
| `gamecatalog.chat.query`, `gamecatalog.chat.answer` | interactive | no transaction rule, ledger, budget, prompts, evals (no job queue) |

## User stories

- **(High)** As the admin, a slow or failing AI call never holds a DB connection or rolls back unrelated writes.
- **(High)** As the admin, merging to `main` while enrichment or a briefing is running does not lose or duplicate work.
- **(Medium)** As the admin, I can see what each feature costs per day and set a monthly cap.
- **(Low)** As the admin, when I change a prompt or a feature's model, I can see whether output got better or worse on a fixed set of cases.

## Requirements

### Transaction boundaries and durability

- No code path calls `ResilientAiService` while a DB transaction is open. The step that calls the model is not `@Transactional`. The result is persisted by a separate short transactional method.
- The no-model-call-in-a-transaction rule is enforced by an automated check (an ArchUnit rule next to `ModularityTests`): a class that depends on `ResilientAiService` must not carry `@Transactional`, and neither must its callers' listener methods.
- Background AI work is recorded before it runs and is retried after a crash or restart (mechanism: see the open question on durability).
- Each unit of work has an idempotency key (for example article id + feature + prompt version, or game id + enrichment version). Running it twice produces one result.
- Concurrency per provider is bounded and configurable, so a backlog after a restart cannot flood OpenRouter or Anthropic.
- On graceful shutdown (deploy), running calls get a bounded time to finish. Anything unfinished is picked up again after start.

### Usage ledger and budget

- Every AI call writes one ledger row: feature, requested model, model and provider actually used (including the Anthropic fallback), prompt id and version, input tokens, output tokens, cost, latency, outcome, correlation id. The ledger stores no prompt or response text by default.
- Cost uses the provider-reported figure when available, otherwise a configured price table, and the row records which of the two was used.
- The admin can set a monthly cap globally and per feature in Settings. At 80% the admin is notified (existing Ntfy dispatch).
- At the cap, background jobs pause (not fail) and interactive features return a clear "budget reached" error. The admin can raise the cap to resume.
- The Settings → AI Models area shows cost per feature per day and month to date. Nothing more at this stage.

### Prompts and evals

- Every prompt lives in a versioned resource file (`.st`), none are built from string concatenation in code. The prompt's id and content hash go into the usage ledger.
- Each feature has a golden set of roughly 10 to 30 cases stored as test resources: input, plus expected properties (not exact text).
- Scoring is deterministic first (valid JSON/schema, required fields present, length limits, forbidden content absent). An LLM-as-judge step is optional and clearly marked as non-deterministic.
- Evals run on demand and from a manually triggered or path-filtered workflow that only fires when prompt files or default model config change. They do not run on every PR (cost, flakiness).
- An eval run produces a stored report (feature, model, prompt version, per-case result, aggregate) so two runs can be compared.

## Success criteria

- A test that makes the model call hang for 60 seconds shows the DB connection pool unaffected during that time.
- Killing the backend mid-enrichment and starting it again completes the batch with no duplicate rows and no manual action.
- Over a week of normal use, ledger totals match the OpenRouter and Anthropic dashboards within a tolerance agreed during planning.
- Setting a feature's cap to a tiny value pauses its background work within one scheduling interval.
- Changing one prompt line and re-running evals produces a report that shows the difference.

## Out of scope

- RAG, embeddings or a semantic-search feature (the pgvector `VectorStore` auto-configuration is excluded today and no `EmbeddingModel` exists).
- External tracing or eval platforms (Langfuse, OpenTelemetry GenAI conventions). Revisit once the ledger exists; the ledger fields are chosen so they can be exported.
- Ollama or any local inference. It was removed on 2026-09-30 and must not come back.
- Changing the routing order (OpenRouter first, one retry on Anthropic).
- AI use in the `trading` module (separate human-approval rules apply).

## Open questions

1. **Durability mechanism.** Spring Modulith's event publication registry may already cover recording and retrying background work (persisted events, resubmission of incomplete ones). If it does, a separate job table or scheduler library is unnecessary. Decide in `/speckit-plan` after checking how the registry is configured today and whether it covers listeners that are not `@ApplicationModuleListener`. Note that `@ApplicationModuleListener` itself runs in a new transaction, so an LLM call inside it breaks the no-model-call-in-a-transaction rule.
2. **Who owns the ledger table?** `shared` has no schema, and each module owns exactly one. Options: `settings` (it already owns `ai_feature_model_setting`) or a new `ai` module with its own schema.
3. **Does OpenRouter return cost per call in the response, and is usage reporting enabled for our requests?** If not, cost falls back to the configured price table for all OpenRouter calls.
4. **Interactive chat at the budget cap**: hard error, or fall back to a cheaper model?
5. **Are prompts and evals one spec or two?** Transaction boundaries and the usage ledger are tightly related. Evals could be split off so a stalled eval design does not block cost visibility.

## Facts to verify in the repo before specifying

- `ResilientAiService` publishes `AiCallCompleted` and increments `jordylab.ai.calls`; what the event currently carries (tokens? model used?).
- Which listeners and services call `ResilientAiService` today, and which are `@Transactional` or `@ApplicationModuleListener` (see `ai-calls-outside-transactions` draft).
- Whether all prompts already live as `.st` files (the setup notes say this is the convention) or some are still built in code. If all are, the prompts requirement shrinks to recording the version in the ledger.
- Current Spring Modulith version and event-registry configuration.
- Current default models per feature in `jordylab.ai.features`.

## Phasing

Each part ships on its own. Transaction boundaries and durability make continuous deploy safer (interrupted jobs resume instead of being lost) but is not a hard prerequisite: until it lands, a deploy during a running batch interrupts it (see the restart-impact question in `continuous-deploy`). The usage ledger is useful without prompts and evals. Evals only need the prompt version from the ledger to be meaningful.

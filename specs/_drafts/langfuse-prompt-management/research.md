# Manage AI Prompts in Langfuse: Research

Date: 2026-11-01. Sources: Langfuse docs (prompt management: get started, version control, caching, link to traces; OpenTelemetry property mapping), the `langfuse-java` README and Maven Central metadata, and the code on `main`.

## Where the code stands

- Four system prompts, all classpath resources: `prompts/fna/briefing-system.st`, `prompts/gamecatalog/enrichment.st`, `libbot-interpret.st`, `libbot-answer.st`. Loaded once in `@PostConstruct` by `BriefingGeneratorService`, `EnrichmentService` and `LibBotPrompts`.
- Placeholders are StringTemplate `<name>` (`StTemplateRenderer` with `<`/`>` delimiters in `LibBotPrompts`), chosen so the JSON-like text needs no escaping. `enrichment.st` contains literal `{ }` JSON.
- Langfuse tracing already exists: `AiTracing` makes a `run-<feature>` span per `ResilientAiService` call, Spring AI adds `generate-<provider>` spans, and `AiSpanExporter` exports only `gen_ai.*` / `langfuse.*` spans. Prompts and answers are only sent with `LANGFUSE_CAPTURE_CONTENT=true`. Keys are optional: without them nothing is exported.

## What Langfuse provides

- Prompts have a name, an auto-incremented version, free-form labels (`production`, `staging`, tenant names) and a type (text or chat, fixed at creation). Fetching by label returns the version that label points to; moving a label is the rollback.
- Variables are `{{name}}`, compiled by the SDK. Single braces are left alone, so the enrichment JSON is safe.
- The Python and JS SDKs cache prompts client-side (stale-while-revalidate, optional prefetch and fallback). **The Java client is an auto-generated REST wrapper (`com.langfuse:langfuse-java`, 0.3.0 on Maven Central, repository active, last push October 2026) with none of that.** Cache, refresh and fallback must be written here. Its README says tracing through it is gone and OpenTelemetry is the only path, which matches what the repo already does; the client is for prompts, scores, datasets.
- Linking a prompt to a trace over OpenTelemetry is done with `langfuse.observation.prompt.name` (string) and `langfuse.observation.prompt.version` (integer), documented as generation-only. In our trace that means the Spring AI `generate-<provider>` span, not our `run-<feature>` span, so the plan must decide how the attributes reach it (observation context or a span processor).
- Langfuse Cloud has deprecated several v3-model endpoints (cutoff 16 Nov 2026). Confirm the prompts endpoint is not among them before building.

## Constraints from the repo

- All model calls go through `ResilientAiService`; no `ChatClient` beside it. Rules: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`.
- Module root package is the public API: the prompt source belongs in `shared/ai`, consumed by `fna` and `gamecatalog` without reaching into internals (Spring Modulith tests).
- AI calls stay outside open transactions.
- Secrets are SOPS-encrypted; `LANGFUSE_*` already exist for tracing. Never log them.
- Changing a prompt in Langfuse changes production behaviour with no deploy or review. The `production` label is the control; use another label locally.

## Overlap with other drafts

- The "prompts as resources" draft moves the last Java text blocks into files. In the current code that is already done, so this draft supersedes it.
- The golden-set evals draft benefits from this one: versioned prompts plus Langfuse datasets allow comparing a new prompt version before promoting it.

## Open questions

- Fallback strategy (see `spec-draft.md`, top clarification).
- Prompt names: mirror the file paths (`fna/briefing-system`) or use `AiFeature` keys (`fna.briefing`)? The second ties a prompt to a feature, but LibBot has two prompts per feature pair.

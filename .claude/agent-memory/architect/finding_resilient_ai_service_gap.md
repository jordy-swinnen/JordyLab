---
name: finding-resilient-ai-service-gap
description: ResilientAiService (shared/ai) — OpenRouter-first per AiFeature, one Anthropic fallback retry, never throws (since spec 011 BUG-010)
metadata:
  type: project
---

`jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java` (re-checked 2026-10-01, PR #61):
`call(AiFeature, system, user)` resolves the feature's model through `AiModelResolver` (settings' AI Models page,
else `jordylab.ai.features`), calls the OpenRouter gateway (`OpenAiChatModel`), and on any failure retries once on
Anthropic (`jordylab.ai.fallback`). It returns a typed `AiCallResult` (provider/model that answered, `fallbackUsed`,
`ProviderFailureReason` incl. `MODEL_NOT_FOUND` and `INSUFFICIENT_CREDITS`), publishes `AiCallCompleted` and counts
`jordylab.ai.calls`. Ollama was removed (006 FR-017).

**Why:** this note used to describe an Anthropic-only service with no fallback; that gap is closed.
**How to apply:** for new AI consumers, add an `AiFeature` constant + bracketed default model key; never add a
provider per module. As of 2026-10-01 the OpenRouter account had no credits (402), so prod calls took the fallback
(HANDOFF-08) — re-check before stating which provider answers in practice.

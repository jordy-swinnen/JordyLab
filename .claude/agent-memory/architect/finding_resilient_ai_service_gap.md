---
name: finding-resilient-ai-service-gap
description: ResilientAiService (shared/ai) calls Anthropic only — health check + timeout + typed failures, but no fallback provider yet
metadata:
  type: project
---

`jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java` (re-checked 2026-10-01) resolves
the per-module config (`jordylab.ai.modules.<module>`), skips unhealthy providers, calls `AnthropicChatModel` with
a timeout and returns a typed `AiCallResult` (`TIMEOUT` / `UNREACHABLE` / `UNKNOWN` failures) instead of throwing.
There is **no fallback provider**: every AI call depends on the Anthropic API being reachable and funded.
Ollama was removed from the product on 2026-09-30 (006 FR-017, spec 011 BUG-009) — it is no longer a planned
fallback; OpenRouter-first routing with an Anthropic fallback is planned in spec 006 (AI Models).

**Why:** the older version of this note described an Ollama-primary design that AGENTS.md used to claim; that
design is gone.
**How to apply:** when asked about AI routing or scoping new `ResilientAiService` consumers, say there is one
provider and no fallback until 006's OpenRouter work lands. Re-check the file before repeating this.

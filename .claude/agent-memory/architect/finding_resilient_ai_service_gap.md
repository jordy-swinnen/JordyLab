---
name: finding-resilient-ai-service-gap
description: ResilientAiService (shared/ai) has no actual resilience — no Ollama fallback, no health check, no caching, despite AGENTS.md architecture claims
metadata:
  type: project
---

`jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/ResilientAiService.java` only wraps
`AnthropicChatModel.call()` in a try/catch that rethrows as RuntimeException. It does not reference
`OllamaChatModel` at all, has no health-check-and-cache logic, and no fallback path. This directly contradicts
root AGENTS.md's "AI Routing" section, which states Ollama on the main desktop is primary with Anthropic as
fallback via a "health-check-and-cache pattern." The Spring AI Ollama starter is a build.gradle.kts dependency
and `TestcontainersConfiguration` spins up an `OllamaContainer`, but nothing in main source code uses it yet —
suggesting the Ollama integration was scaffolded (dependency + test infra) but never wired into the service.

**Why:** Found by reading the actual service body and comparing against the documented architecture principle —
this is a real, verifiable violation, not a guess.
**How to apply:** Flag this whenever asked about AI routing, cost/latency of AI calls, or when scoping new
`ResilientAiService` consumers (e.g. via the `/ai-endpoint` skill) — the "resilient" name is currently
misleading; every AI call today is a hard dependency on the Anthropic API being reachable and funded. Re-check
the file before repeating this claim, since this is exactly the kind of gap that gets fixed quickly once flagged.

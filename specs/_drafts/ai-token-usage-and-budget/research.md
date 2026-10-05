# AI Token Usage Metrics and a Spending Budget: Research

Date: 2026-10-05. Source: `docs/research/spring-ai-gap-analysis.md` (checked against the code on `main`) and the report
`docs/research/spring-ai-architecture.md`.

## Where the code stands

`ResilientAiService.record`, `AiCallCompleted`, `AiCallResult`, `jordylab.ai.*` properties; Settings -> AI Models.

## Why it is worth building

Calls are counted but their size is not: `AiCallCompleted` and `jordylab.ai.calls` carry no token usage, and there is no limit on spend. The OpenRouter credit has already run out once without warning during an earlier test campaign.

## Constraints from the repo

- All model calls keep going through `ResilientAiService`; no `ChatClient` beside it.
- No hand-seeded data for validation; tests use Testcontainers and mocks (root `AGENTS.md`).
- Rules for AI features: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`.

## Open questions

- Where does the budget live: `jordylab.ai.*` properties, or the Settings page like the model choice?
- Which features may be stopped at the limit and which never (the monthly briefing)?

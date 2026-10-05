# Golden Examples for AI Output: Research

Date: 2026-10-05. Source: `docs/research/spring-ai-gap-analysis.md` (checked against the code on `main`) and the report
`docs/research/spring-ai-architecture.md`.

## Where the code stands

`EnrichmentServiceTest`, `ChatServiceTest`, `ResilientAiServiceTest` (mocks only); enrichment output parser (`parseAndValidate`) and chat filter parser (`parseFilter`).

## Why it is worth building

Only the plumbing is tested, with a mocked model. Nothing checks that the real prompts still produce valid, sensible output after a model or prompt change, which the research ranks as the first thing to build.

## Constraints from the repo

- All model calls keep going through `ResilientAiService`; no `ChatClient` beside it.
- No hand-seeded data for validation; tests use Testcontainers and mocks (root `AGENTS.md`).
- Rules for AI features: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`.

## Open questions

- Real-model tier: nightly, weekly or manual only? (cost versus drift detection)
- Fixture format: JSON files under `src/test/resources`, or Java builders like the existing TestBuilders?

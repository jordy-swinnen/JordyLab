# Move Remaining AI Prompts to Resources: Research

Date: 2026-10-05. Source: `docs/research/spring-ai-gap-analysis.md` (checked against the code on `main`) and the report
`docs/research/spring-ai-architecture.md`.

## Where the code stands

`EnrichmentService.SYSTEM_PROMPT`, `ChatService.TRANSLATION_SYSTEM_PROMPT`, `ChatService.COMPOSITION_SYSTEM_PROMPT`; the pattern to copy is `prompts/fna/briefing-system.st` loaded by `BriefingGeneratorService`.

## Why it is worth building

Only the FNA briefing prompt is a classpath resource; the enrichment and the two chat prompts are Java text blocks, so prompts are edited in code and cannot be reviewed or loaded the same way.

## Constraints from the repo

- All model calls keep going through `ResilientAiService`; no `ChatClient` beside it.
- No hand-seeded data for validation; tests use Testcontainers and mocks (root `AGENTS.md`).
- Rules for AI features: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`.

## Open questions

- Should prompt files carry a version suffix or header now? (the report calls this a community convention, so the default is no)

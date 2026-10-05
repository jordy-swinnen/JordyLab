# AI Calls Outside Database Transactions: Research

Date: 2026-10-05. Source: `docs/research/spring-ai-gap-analysis.md` (checked against the code on `main`) and the report
`docs/research/spring-ai-architecture.md`.

## Where the code stands

`BriefingGeneratorService.generateBriefing` (`@Transactional`, calls `aiService.call`); `EnrichmentService.enrichPending` and `refresh` (`@Transactional`, call `aiService.call` per game); `ScanService.submitScan` -> `populateCatalogData` -> `enrichPending`; `SteamLibrarySyncService.syncOwned` -> `enrichPending`.

## Why it is worth building

Model calls run inside open database transactions today, so a connection is held for up to 120 s per call (twice with the fallback), a scan upload waits on enrichment, and one late failure rolls back unrelated work.

## Constraints from the repo

- All model calls keep going through `ResilientAiService`; no `ChatClient` beside it.
- No hand-seeded data for validation; tests use Testcontainers and mocks (root `AGENTS.md`).
- Rules for AI features: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`.

## Open questions

- Run enrichment after the scan transaction commits on the same request thread, on an async worker, or as a scheduled drain? (the code comment says 'there is no scheduler' today)
- Is an outbox needed, or are the existing PENDING status and attempt counters enough? (the report recommends outbox plus idempotency keys; a status column may already be the outbox here)

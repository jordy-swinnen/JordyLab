# AI Calls Outside Database Transactions: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: ai-calls-outside-transactions.

AI Calls Outside Database Transactions. Model calls run inside open database transactions today, so a connection is held for up to 120 s per call (twice with the fallback), a scan upload waits on enrichment, and one late failure rolls back unrelated work.

WHERE: `BriefingGeneratorService.generateBriefing` (`@Transactional`, calls `aiService.call`); `EnrichmentService.enrichPending` and `refresh` (`@Transactional`, call `aiService.call` per game); `ScanService.submitScan` -> `populateCatalogData` -> `enrichPending`; `SteamLibrarySyncService.syncOwned` -> `enrichPending`.

Requirements: see specs/_drafts/ai-calls-outside-transactions/spec-draft.md. Keep product behaviour unchanged except where the draft says otherwise.

Out of scope: changing models or routing, new AI features, anything the gap analysis marks defer.
```

## `/speckit-clarify`: expected questions

- Run enrichment after the scan transaction commits on the same request thread, on an async worker, or as a scheduled drain? (the code comment says 'there is no scheduler' today)
- Is an outbox needed, or are the existing PENDING status and attempt counters enough? (the report recommends outbox plus idempotency keys; a status column may already be the outbox here)

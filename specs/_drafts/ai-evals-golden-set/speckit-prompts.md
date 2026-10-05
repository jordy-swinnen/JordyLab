# Golden Examples for AI Output: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: ai-evals-golden-set.

Golden Examples for AI Output. Only the plumbing is tested, with a mocked model. Nothing checks that the real prompts still produce valid, sensible output after a model or prompt change, which the research ranks as the first thing to build.

WHERE: `EnrichmentServiceTest`, `ChatServiceTest`, `ResilientAiServiceTest` (mocks only); enrichment output parser (`parseAndValidate`) and chat filter parser (`parseFilter`).

Requirements: see specs/_drafts/ai-evals-golden-set/spec-draft.md. Keep product behaviour unchanged except where the draft says otherwise.

Out of scope: changing models or routing, new AI features, anything the gap analysis marks defer.
```

## `/speckit-clarify`: expected questions

- Real-model tier: nightly, weekly or manual only? (cost versus drift detection)
- Fixture format: JSON files under `src/test/resources`, or Java builders like the existing TestBuilders?

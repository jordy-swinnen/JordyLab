# Move Remaining AI Prompts to Resources: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: ai-prompts-as-resources.

Move Remaining AI Prompts to Resources. Only the FNA briefing prompt is a classpath resource; the enrichment and the two chat prompts are Java text blocks, so prompts are edited in code and cannot be reviewed or loaded the same way.

WHERE: `EnrichmentService.SYSTEM_PROMPT`, `ChatService.TRANSLATION_SYSTEM_PROMPT`, `ChatService.COMPOSITION_SYSTEM_PROMPT`; the pattern to copy is `prompts/fna/briefing-system.st` loaded by `BriefingGeneratorService`.

Requirements: see specs/_drafts/ai-prompts-as-resources/spec-draft.md. Keep product behaviour unchanged except where the draft says otherwise.

Out of scope: changing models or routing, new AI features, anything the gap analysis marks defer.
```

## `/speckit-clarify`: expected questions

- Should prompt files carry a version suffix or header now? (the report calls this a community convention, so the default is no)

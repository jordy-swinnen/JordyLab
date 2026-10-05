# AI Token Usage Metrics and a Spending Budget: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: ai-token-usage-and-budget.

AI Token Usage Metrics and a Spending Budget. Calls are counted but their size is not: `AiCallCompleted` and `jordylab.ai.calls` carry no token usage, and there is no limit on spend. The OpenRouter credit has already run out once without warning during an earlier test campaign.

WHERE: `ResilientAiService.record`, `AiCallCompleted`, `AiCallResult`, `jordylab.ai.*` properties; Settings -> AI Models.

Requirements: see specs/_drafts/ai-token-usage-and-budget/spec-draft.md. Keep product behaviour unchanged except where the draft says otherwise.

Out of scope: changing models or routing, new AI features, anything the gap analysis marks defer.
```

## `/speckit-clarify`: expected questions

- Where does the budget live: `jordylab.ai.*` properties, or the Settings page like the model choice?
- Which features may be stopped at the limit and which never (the monthly briefing)?

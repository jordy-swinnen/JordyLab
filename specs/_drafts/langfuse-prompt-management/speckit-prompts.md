# Manage AI Prompts in Langfuse: SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: langfuse-prompt-management.

Manage AI Prompts in Langfuse. The four system prompts live as .st files in the repository, so tuning one needs a commit and a deploy. Langfuse already receives the AI traces. Make Langfuse Prompt Management the place where prompts are edited, versioned and promoted: the backend fetches each prompt by name and label at runtime, caches it, falls back safely when Langfuse is unreachable or has no keys, and links every AI call to the prompt version it used.

WHERE: prompts `prompts/fna/briefing-system.st`, `prompts/gamecatalog/enrichment.st`, `libbot-interpret.st`, `libbot-answer.st`, loaded by `BriefingGeneratorService`, `EnrichmentService` and `LibBotPrompts`; tracing in `shared/ai` (`AiTracing`, `LangfuseTracingConfiguration`, `AiSpanExporter`); the REST client is `com.langfuse:langfuse-java` (generated, no cache or fallback of its own).

Requirements: see specs/_drafts/langfuse-prompt-management/spec-draft.md. Keep product behaviour unchanged except where the draft says otherwise: the text sent to the model must be identical.

Out of scope: choosing models or routing from Langfuse, chat-type prompts, experiments and datasets, writing prompts back from the app.
```

## `/speckit-clarify`: expected questions

- Does the repository keep a copy as an offline fallback? (default: yes, labelled fallback only, Langfuse is the source of truth when configured)
- Cache lifetime: 60 seconds, or longer?
- Missing prompt or label in production: fail on startup, or log and fall back? (default: log, count and fall back)
- How are the four prompts first pushed into Langfuse: UI, CLI or a one-off script?
- E2E stack: stub prompt source or the bundled fallback? (default: bundled fallback)
- Prompt names: file-path style or feature keys?

## `/speckit-plan`: points to settle

- How the prompt name and version reach the Spring AI `generate-<provider>` span (generation-only attributes).
- Confirm the Langfuse prompts endpoint is unaffected by the v4 endpoint deprecations.
- One placeholder syntax (`{{name}}`) for Langfuse and the fallback files, and the renderer that replaces `StTemplateRenderer`.

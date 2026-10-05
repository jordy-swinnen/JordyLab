# AI integration rules (shared/ai)

Short rules for anyone adding or changing an AI feature. Only high-confidence findings from the Spring AI research live
here; the full report, its caveats and everything it marks unverified is `docs/research/spring-ai-architecture.md`.
What the repo does not do yet is listed in `docs/research/spring-ai-gap-analysis.md`.

## Path

- Every model call goes through `ResilientAiService.call(AiFeature, …)`. Never create a `ChatClient` or call a
  `ChatModel` directly, and never register a second advisor/tool loop beside it.
- A new feature adds one `AiFeature` constant and its default model under `jordylab.ai.features` (keys with dots need
  `"[feature.name]"`), and stays selectable on Settings → AI Models.
- OpenAI and Anthropic run on their vendor SDKs: the SDKs retry, and `spring.ai.retry.*`, `RestClientCustomizer` and
  `spring.http.client.*` do not apply to them.
- Set the temperature explicitly where determinism matters (Spring AI 2.0 removed the 0.7 default).

## Output and prompts

- Prompts live as classpath resources under `src/main/resources/prompts/`, not as string literals in Java.
- Ask for structured output and parse it into a typed record, then validate it right away (bean validation or
  `Assert`) before any other code uses it. Never act on, execute or store raw model text unchecked.
- OpenAI structured output rejects a top-level array schema: wrap the list in a record.
- Treat retrieved or user-supplied text as data, not instructions: delimit it in the prompt.

## Tools, memory and retrieval (when a feature introduces them)

- Keep `spring.ai.tools.resolution.fallback.enabled=false`: otherwise any resolvable tool, including destructive ones, runs
  when merely named. Pass user/tenant context through `ToolContext`, never in tool arguments the model controls.
- Cap tool loops (`maxCallsPerTool`, `maxTotalToolCalls`). Write or irreversible actions need human approval: Spring AI
  ships none, so persist the pending call and build approve/resume ourselves (trading already requires it).
- Chat memory needs a conversation id derived on the server from user and session, never a client-chosen shared id.
- RAG: filter by metadata *values* only, never by user-controlled keys; every search carries the tenant filter; keep the
  default empty-context refusal; never build document readers from user-supplied URLs.

## Observability and tests

- Prompt, completion and tool-argument content logging stays off (`spring.ai.chat.observations.log-prompt|log-completion`,
  `spring.ai.tools.observations.include-content`, `spring.ai.chat.client.observations.log-prompt`).
- Keep `AiCallCompleted` and the `jordylab.ai.calls` counter on every path; token usage is the cost signal.
- A new feature ships with a unit test around a mocked `ChatModel` (see `ResilientAiServiceTest`) and at least one
  golden example asserting the typed result; run `ResilientAiServiceTest`, `AiGatewayWiringTest` and `AiPropertiesTest`
  after any Spring AI bump.

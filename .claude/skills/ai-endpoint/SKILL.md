---
name: ai-endpoint
description: Scaffold a ResilientAiService integration for a module with model config, analysis class, and system prompt.
---

# AI Endpoint Scaffold

## Gather Input

Ask the user for:
- **Target module** (e.g., `fna`, `gamecatalog`)
- **AI task description** (e.g., "analyze portfolio risk", "generate recipe suggestions")
- **Default model** (an OpenRouter model id, e.g. `anthropic/claude-haiku-4.5`; see `AGENTS.md` AI Routing table)

## Scaffold

1. Create the analysis class in the module's internal package:
   `jordylab-be/src/main/java/dev/jordy/jordylab/<module>/analysis/<Task>AnalysisService.java`

   ```java
   @Service
   @RequiredArgsConstructor
   @Slf4j
   public class <Task>AnalysisService {

       private final ResilientAiService resilientAiService;

       // Use ResilientAiService — never instantiate ChatClient directly
   }
   ```

2. Add an `AiFeature` constant in `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AiFeature.java`
   (key like `<module>.<task>`, display name, module, one-line description — it appears on the AI Models page) and its
   default OpenRouter model in `jordylab-be/src/main/resources/application.yaml` (`.yaml`, not `.yml`). Feature keys
   contain dots, so **bracket them** or they won't bind (`AiPropertiesTest` checks every feature):
   ```yaml
   jordylab:
     ai:
       features:
         "[<module>.<task>]":
           model: <vendor>/<model>   # an id from https://openrouter.ai/api/v1/models
   ```
   Call it with `resilientAiService.call(AiFeature.<CONSTANT>, systemPrompt, userPrompt)` and handle
   `!result.success()` explicitly — the service never throws.

3. Create the system prompt as a `.st` resource (see `jordylab-be/AGENTS.md` "Spring AI / Prompts"):
   `jordylab-be/src/main/resources/prompts/<module>/<task>.st`

   Inject it as a `Resource` (`@Value("classpath:prompts/<module>/<task>.st") Resource`), render with
   `SystemPromptTemplate` if the prompt has `{placeholder}` tokens, or read it as plain fixed text
   otherwise. Use `<>` delimiters instead of the ST default `{}` if the template contains literal JSON.
   Build the user prompt in code from runtime data — it is never filed as a resource.
   If the answer drives behaviour, ask for structured output, parse it into a typed record and validate it right
   away (see the AI checklist below); plain chat-style answers can stay text.

4. If the task involves RAG:
   - Use pgvector embeddings with `vector_cosine_ops`
   - Create a Flyway migration for the embeddings table using the flyway-migration skill

## Rules

- Never instantiate `ChatClient` directly — always use `ResilientAiService`
- Routing is shared: OpenRouter first, one Anthropic fallback retry (`jordylab.ai.fallback`). Don't add providers
  per module. Local inference (Ollama) was removed (006 FR-017); don't wire it.

<!-- BEGIN AI CHECKLIST -->
## AI checklist

Rules behind each item: `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`; research and caveats:
`docs/research/spring-ai-architecture.md`; known gaps: `docs/research/spring-ai-gap-analysis.md`.

- [ ] Model calls go only through `ResilientAiService.call(AiFeature, …)`: no `ChatClient` or `ChatModel` anywhere else
- [ ] A new feature has its own `AiFeature` constant and a bracketed default model key (`"[module.task]"`), and the failure case
      (`!result.success()`) is handled explicitly
- [ ] The system prompt is a resource under `src/main/resources/prompts/<module>/`, not a Java string literal
- [ ] The answer is parsed into a typed record and validated before use; raw model text is never executed, trusted for
      authorization, or stored unchecked
- [ ] Options that matter for determinism (temperature, max tokens) are set explicitly
- [ ] No model call runs while a database transaction is open (repo convention; existing violations are listed in the gap
      analysis and tracked there, flag only new ones)
- [ ] Prompt, completion and tool content logging stays off; no secrets or prompt text in logs
- [ ] If tools are involved: tool fallback resolution off, user/tenant context via `ToolContext`, call caps, authorization
      inside a secured service, human approval for write actions
- [ ] If memory or retrieval is involved: server-derived conversation id; tenant filter on every search, filters on metadata
      values only, empty-context refusal kept
- [ ] A unit test around a mocked `ChatModel` plus a golden example of the typed result exist; `ResilientAiServiceTest`,
      `AiGatewayWiringTest` and `AiPropertiesTest` still pass
<!-- END AI CHECKLIST -->

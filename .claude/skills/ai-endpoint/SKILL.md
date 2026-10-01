---
name: ai-endpoint
description: Scaffold a ResilientAiService integration for a module with model config, analysis class, and system prompt.
---

# AI Endpoint Scaffold

## Gather Input

Ask the user for:
- **Target module** (e.g., `fna`, `gamecatalog`)
- **AI task description** (e.g., "analyze portfolio risk", "generate recipe suggestions")
- **Provider** (`anthropic` — the only provider wired today; see `AGENTS.md` AI Routing table)

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

2. Add per-module provider configuration to `jordylab-be/src/main/resources/application.yaml` under `jordylab.ai.modules.<module>` (note: `.yaml`, not `.yml`; the config is keyed under `modules`, not directly under `ai`):
   ```yaml
   jordylab:
     ai:
       modules:
         <module>:
           provider: anthropic
           model: <model-name>
   ```

3. Create the system prompt as a `.st` resource (see `jordylab-be/AGENTS.md` "Spring AI / Prompts"):
   `jordylab-be/src/main/resources/prompts/<module>/<task>.st`

   Inject it as a `Resource` (`@Value("classpath:prompts/<module>/<task>.st") Resource`), render with
   `SystemPromptTemplate` if the prompt has `{placeholder}` tokens, or read it as plain fixed text
   otherwise. Use `<>` delimiters instead of the ST default `{}` if the template contains literal JSON.
   Build the user prompt in code from runtime data — it is never filed as a resource.

4. If the task involves RAG:
   - Use pgvector embeddings with `vector_cosine_ops`
   - Create a Flyway migration for the embeddings table using the flyway-migration skill

## Rules

- Never instantiate `ChatClient` directly — always use `ResilientAiService`
- One provider (Anthropic) is wired today, for `fna` and `gamecatalog` — there is no fallback provider yet
  (OpenRouter-first routing is planned in spec 006). Local inference (Ollama) was removed (006 FR-017); don't
  wire it.

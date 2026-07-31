---
name: ai-endpoint
description: Scaffold a ResilientAiService integration for a module with model config, analysis class, and system prompt.
---

# AI Endpoint Scaffold

## Gather Input

Ask the user for:
- **Target module** (e.g., `finance`, `gamecatalog`)
- **AI task description** (e.g., "analyze portfolio risk", "generate recipe suggestions")
- **Preferred provider** (ollama or anthropic — ollama is default)

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

2. Add model configuration to `jordylab-be/src/main/resources/application.yml`:
   ```yaml
   jordylab:
     ai:
       <module>:
         model: <model-name>
         provider: <ollama|anthropic>
   ```

3. Create the system prompt file:
   `jordylab-be/src/main/resources/prompts/<module>/<task>.st`

   Use StringTemplate format with placeholders for dynamic content.

4. If the task involves RAG:
   - Use pgvector embeddings with `vector_cosine_ops`
   - Create a Flyway migration for the embeddings table using the flyway-migration skill

## Rules

- Never instantiate `ChatClient` directly — always use `ResilientAiService`
- Ollama is the primary provider; Anthropic Claude is the fallback
- Health check only verifies Ollama is running, not that a model is loaded in VRAM

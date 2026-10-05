# Architecting Qualitative & Scalable AI Integrations with Spring AI (and AI Integration Engineering Principles)

*Research report — architect perspective. Query type: conceptual + technical deep-dive (principles first, then Spring AI mapping).*

---

## 1. Executive Summary

Production-grade AI integration is mostly **ordinary distributed-systems engineering wrapped around a non-deterministic component**. Across Anthropic, OpenAI, Microsoft, Martin Fowler, Chip Huyen, Eugene Yan and Hamel Husain, the same principles recur: start with the simplest workflow, build **evals first**, treat **context as a scarce resource**, layer **guardrails**, centralise **provider access (gateway/fallback/caching/cost)**, and keep **humans in the loop for irreversible actions**[^25][^27][^28][^29][^30][^31]. Spring AI (current GA line **2.0.x**, designed for Spring Boot 4.0 and 4.1 / Java 17+) maps well onto these principles: `ChatClient` + **Advisors** (middleware chain) for cross-cutting concerns, `VectorStore`/modular RAG, `@Tool`/MCP, Micrometer-based observability using OTel GenAI conventions, and an `Evaluator` API[^1][^3][^5][^11][^12]. However, **many "hard" production concerns are not provided by the framework** — circuit breaking, model fallback, token budgeting, PII/injection guardrails, tool authorisation, HITL approval and compaction-based memory must be designed by you or taken from community projects[^21][^23][^24][^41][^42]. The recommended architecture is therefore: a **domain-owned port + anti-corruption adapter** around Spring AI, a **thin advisor pipeline** for cross-cutting concerns, **asynchronous/outbox-driven** long-running work, **externalised versioned prompts**, **eval suites in CI**, and **explicit security layers**.

---

## 2. Version & Landscape Reality Check (important)

| Item | Finding |
|---|---|
| Current Spring AI GA | **2.0.x** (2.0.0 GA announced **12 June 2026**; latest release tag `v2.0.1`); `main` is `2.1.0-SNAPSHOT`[^1][^43] |
| Platform baseline | **Spring Boot 4.0 and 4.1 / Spring Framework 7.0 / Java 17+ / Jackson 3**[^1][^43] |
| Date confidence | GA date is confirmed by the primary spring.io announcement (12 June 2026); the earlier 28 May date in secondary sources was wrong[^43] |
| Many "1.x" tutorials | Outdated: tool loops, memory ordering, retry stack and HTTP clients all changed in 2.0 (see §9) |

> **Corrections made when this report was filed in the repo (2026-10-05):** the GA date is 12 June 2026, not 28 May; the
> baseline is Spring Boot 4.0 **and** 4.1, not only 4. The question whether the repo's combination (Spring Boot 4.0.3 with
> Spring AI 2.0.1) suffers from the starter-dependency problem some reports describe is answered in
> "§14 Repo finding: Boot 4.0.3 + Spring AI 2.0.1" below. Everything else is the research as delivered; items it marks
> unverified are still unverified and are deliberately kept out of `jordylab-be/.../shared/ai/AGENTS.md`.

---

## 3. Core Engineering Principles (vendor-neutral)

### 3.1 Workflows before agents
Anthropic distinguishes **workflows** (LLMs orchestrated through predefined code paths) from **agents** (LLMs dynamically direct their own process and tools) and advises "finding the simplest solution possible, and only increasing complexity when needed"[^25]. OpenAI agrees: build an agent only for complex decision-making, hard-to-maintain rule sets, or heavy unstructured data — "otherwise, a deterministic solution may suffice"[^27]. Start single-agent; split into multi-agent only when instructions/tool sets become unwieldy (~10–15 overlapping tools)[^27].

### 3.2 Evals first
"Building solid evals should be the starting point for any LLM-based system"[^28]. Fowler treats evals as CI/CD gates with thresholds plus production evals for drift, and warns against pure self-evaluation[^29]. Hamel Husain's maturity ladder: **L1** assertion-style unit tests → **L2** logged traces + human/model review with measured judge↔human agreement → continuous review ("you can never stop looking at data")[^30]. LLM-as-judge biases (position, verbosity, self-enhancement) must be controlled[^28].

### 3.3 Context engineering
Context is "a finite resource with diminishing marginal returns"; aim for "the smallest possible set of high-signal tokens". Techniques: **compaction**, **structured note-taking**, **sub-agents returning condensed summaries**, and **just-in-time retrieval**[^26]. Huyen frames context construction as "feature engineering" (hybrid retrieval, query rewriting, reranking)[^31].

### 3.4 Layered guardrails
"A single [guardrail] is unlikely to provide sufficient protection"; combine relevance/safety classifiers, PII filters, moderation, rules-based checks, and **risk-rated tool safeguards**[^27]. Input *and* output guardrails are needed; streaming complicates output checks; guardrails add latency/cost[^31][^29].

### 3.5 Determinism boundary & human-in-the-loop
Keep read-only actions autonomous; gate **write/irreversible/high-value actions** and **repeated failures** behind humans[^27][^31]. Fowler/Thoughtworks: force the model to return an assertable "intent" field alongside free-text "message"[^35].

### 3.6 Gateway, caching, routing, cost
A **model gateway** centralises key custody, access control, fallback, retries and logging[^31]. Caching types: provider **prompt cache**, **exact cache**, and **semantic cache** (both Huyen and Yan call semantic caching risky; cache safely, don't rely on similarity alone)[^31][^28]. Route simple queries to small models; baseline with the best model, then optimise cost[^27][^25].

### 3.7 Reliability, security and governance
Azure Well-Architected: AI replaces deterministic functionality with nondeterministic behaviour → failure-mode analysis, bulkheads/circuit breakers, HA inference endpoints, PII removal at *every* store (indexes, caches, aggregates), RBAC/ABAC, drift management (GenAIOps)[^32]. **OWASP LLM Top 10 (2025)**: LLM01 Prompt Injection, 02 Sensitive Info Disclosure, 03 Supply Chain, 04 Data/Model Poisoning, 05 Improper Output Handling, 06 Excessive Agency, 07 System Prompt Leakage, 08 Vector/Embedding Weaknesses, 09 Misinformation, 10 Unbounded Consumption[^33].

---

## 4. Spring AI Architecture Overview

```mermaid
graph TD
  UI[Client / Frontend] --> GW[API Gateway: authN/Z, rate limit]
  GW --> APP[Application Service]
  APP -->|domain port| ACL[AI Adapter - Anti-Corruption Layer]
  ACL --> CC[ChatClient]
  subgraph Advisor chain - ordered middleware
    A1[Guardrail in: PII / injection / SafeGuard]
    A2[Memory advisor +200]
    A3[ToolCallingAdvisor +300]
    A4[RAG advisor 0]
    A5[Guardrail out / logging / token budget]
  end
  CC --> A1 --> A2 --> A3 --> A4 --> A5 --> CM[ChatModel]
  CM --> RES[Resilience: retry, CB, bulkhead, timeout]
  RES --> PROV[(LLM Providers / Gateway)]
  A4 --> VS[(VectorStore)]
  A3 --> TOOLS[@Tool / MCP servers]
  A2 --> MEM[(ChatMemoryRepository)]
  CC -.-> OBS[Micrometer + OTel gen_ai.*]
```

### 4.1 Key abstractions
- **ChatModel vs ChatClient** — `ChatModel` is the thin portability contract; `ChatClient` is a fluent, `RestClient`-style client built on top[^2]. Auto-configuration provides a prototype-scoped `ChatClient.Builder`; use `ChatClientBuilderConfigurer`/customizers when you build extra clients so observability/defaults survive[^2].
- **Advisors** — Servlet-filter-like chain (`CallAdvisor`/`StreamAdvisor`, `Ordered`, lowest order = first on request/last on response, may short-circuit)[^3]. Built-ins: `SimpleLoggerAdvisor`, `MessageChatMemoryAdvisor`, `VectorStoreChatMemoryAdvisor`, `QuestionAnswerAdvisor`, `RetrievalAugmentationAdvisor`, `SafeGuardAdvisor`, `ToolCallingAdvisor`[^3].
- **Structured output** — `BeanOutputConverter` (prompt-appended schema + response cleaning of `<think>`/markdown fences) by default; opt in to provider-native schema enforcement with `.entity(Class, spec -> spec.useProviderStructuredOutput())` and `validateSchema()` (auto-retry up to 3)[^2].

```java
ActorsFilms films = chatClient.prompt()
    .user("Generate the filmography for a random actor.")
    .call()
    .entity(ActorsFilms.class, spec -> spec.useProviderStructuredOutput());
```
Caveats: OpenAI structured outputs reject top-level array schemas; some Ollama reasoning models ignore it[^2].

### 4.2 Advisor ordering (verified constants, 2.0.x)
`MemoryAdvisor` = `HIGHEST_PRECEDENCE+200` → `ToolCallingAdvisor` = `HIGHEST_PRECEDENCE+300` → RAG advisors = `0`. Memory wraps the tool loop, and `ToolCallingAdvisor` manages intermediate history itself[^18][^4]. Exactly one `ToolAdvisor` per chain; it is auto-registered whenever tools are present[^4][^7].

---

## 5. Building Blocks Deep-Dive

### 5.1 RAG & vector stores
- **ETL**: `DocumentReader → DocumentTransformer (TokenTextSplitter) → DocumentWriter (VectorStore)`. Never construct readers from user-supplied URLs (SSRF risk)[^6].
- **Modular RAG** (`RetrievalAugmentationAdvisor`): query transformers (compression/rewrite/translation; use **temperature ≈ 0** for them) → query expander → parallel retrieval (async executor) → joiner → `DocumentPostProcessor` (reranking/compression — interface only, no shipped reranker) → `ContextualQueryAugmenter`. By default **empty context ⇒ the model is told not to answer** (hallucination guard)[^5].

```java
Advisor rag = RetrievalAugmentationAdvisor.builder()
    .queryTransformers(RewriteQueryTransformer.builder()
        .chatClientBuilder(chatClientBuilder.build().mutate()).build())
    .documentRetriever(VectorStoreDocumentRetriever.builder()
        .similarityThreshold(0.50).vectorStore(vectorStore).build())
    .build();
```
- **Scalability knobs**: pgvector `index-type` HNSW (default; better query perf/more memory) vs IVFFlat vs NONE; Redis HNSW `M`/`efConstruction`/`efRuntime` trade recall vs memory/latency; `BatchingStrategy` (`TokenCountBatchingStrategy`, 8191-token default, 10% reserve) must be overridden by a `@Bean`; `SimpleVectorStore` is test-only[^6].
- **Pitfalls**: filterable metadata fields must be declared up front in some stores (adding later means re-ingesting); tenant isolation = a mandatory metadata filter on every search/delete; a document-versioning pattern deletes old versions by compound filter before adding new[^6].

### 5.2 Tools & MCP
- `@Tool`/`@ToolParam`, `ToolCallback(Provider)`, `ToolContext` (pass tenant/user to tools **without exposing it to the model**), `returnDirect`, and a `ToolExecutionExceptionProcessor` (default: runtime-exception message goes back to the model)[^7].
- Guard-rails on the loop: `maxCallsPerTool`, `maxTotalToolCalls`, `ToolCallLimitBehavior`[^7]. Keep `spring.ai.tools.resolution.fallback.enabled=false` (docs warn it makes every resolvable tool, including destructive ones, executable when merely named)[^7][^24].
- **Large tool catalogs**: `ToolSearchToolCallingAdvisor` (progressive disclosure via regex/Lucene/vector index) replaces the default advisor[^7][^20].
- **MCP**: client/server starters for STDIO, Streamable-HTTP (recommended), Stateless; SSE deprecated. **HTTP transports expose an unauthenticated JSON-RPC endpoint by default** — add Spring Security or community `mcp-security` (OAuth2/API-key; JWT only, no SSE/WebFlux server yet)[^8][^9].

### 5.3 Agentic patterns
Spring documents Anthropic's five workflows (chain, parallelization, routing, orchestrator-workers, evaluator-optimizer) with runnable `agentic-patterns` examples; guidance is "start simple, prefer fixed workflows"[^10]. The deterministic seam is a record/enum returned via `.entity()` (e.g. `Evaluation {PASS, NEEDS_IMPROVEMENT, FAIL}`) that plain Java then branches on[^34][^10]. Community: `spring-ai-agent-utils` (Agent Skills, TaskTool sub-agents, AutoMemory), Embabel (GOAP planning, built on Spring AI), Spring AI Alibaba (graph)[^20].

### 5.4 Memory
- `ChatMemory` (retention policy: only `MessageWindowChatMemory`, default 20 messages, turn-boundary eviction, system messages pinned) vs `ChatMemoryRepository` (storage: In-memory, JDBC, Cassandra, Neo4j, Mongo, Redis; Cosmos externalised in 2.0)[^18].
- **Conversation ID is mandatory** (no default) — derive it server-side from user+session (e.g. `userId:sessionId`), never trust a client-provided shared id[^18].
- JDBC/Cassandra/Mongo **silently drop tool-call messages**; Neo4j persists them[^18]. No token-based or summarising memory in core; `spring-ai-session` (community, event-sourced, token/turn/LLM-summarisation compaction) is stated to be planned to replace `ChatMemory` in 2.1[^19].
- Do not use `InMemoryChatMemoryRepository` in multi-replica deployments.

---

## 6. Scalability & Resilience Blueprint

| Concern | What Spring AI gives | What you must add |
|---|---|---|
| Retry | `spring.ai.retry.*` (default 10 attempts, 2s×5 backoff, 3 min max); 5xx/network retried, **4xx incl. 429 not retried** unless `on-http-codes: [429]`; no `Retry-After` awareness; built on Spring Framework 7 retry in 2.0[^14] | Pick **one** retry layer — stacking Resilience4j `@Retry` on top compounds attempts and trips breakers[^41] |
| Circuit breaker / bulkhead / rate limiter / timeout | **None** | Resilience4j around the port adapter[^41] |
| Provider fallback | **None** (no router/fallback ChatModel) | Multiple qualified `ChatModel` beans + CB fallback, or an OpenAI-compatible gateway (LiteLLM/Portkey/Foundry) via `base-url`[^42][^39] |
| Concurrency | Blocking `call()` (Servlet) and `stream()` (needs WebFlux) are separate stacks; tool calling is blocking[^15] | Virtual threads (`spring.threads.virtual.enabled=true`) for blocking calls; raise HTTP pool limits; avoid ThreadLocal/pinning[^15] |
| HTTP clients | Non-SDK providers: `RestClient`/`WebClient` (+`spring.http.client.*`). **OpenAI & Anthropic in 2.0 use vendor SDKs** — `spring.ai.retry.*`, `RestClientCustomizer`, `spring.http.client.*` **no longer apply**; use `OpenAiHttpClientBuilderCustomizer` (timeout, `maxIdleConnections`, `keepAliveDuration`, dispatcher executor)[^14][^16] | Anthropic pool/timeout hook unverified[^16] |
| Prompt caching | Anthropic: `AnthropicCacheStrategy` (`SYSTEM_ONLY`, `SYSTEM_AND_TOOLS`, `CONVERSATION_HISTORY`, ≤4 breakpoints, TTL 5m/1h). OpenAI: automatic server-side[^17] | Monitor cache-hit ratio via usage metadata |
| Rate-limit telemetry | Anthropic `ChatResponseMetadata#getRateLimit()`[^17] | Client-side throttle/token budget advisor (none built-in) |
| Ingestion | ETL + `TokenCountBatchingStrategy`[^6] | Spring Batch or queue + bounded workers (inferred pattern, not first-party)[^14] |
| Streaming | `Flux<String>`/SSE; Anthropic streaming holds a `boundedElastic` worker per stream[^15] | Size schedulers for concurrent streams |

### Recommended resilience wiring (illustrative)
```yaml
spring.ai.retry:
  max-attempts: 3
  on-http-codes: [429]
resilience4j:
  circuitbreaker.instances.chatModel: { sliding-window-size: 20, failure-rate-threshold: 50, wait-duration-in-open-state: 15s }
  bulkhead.instances.chatModel: { max-concurrent-calls: 25 }
  timelimiter.instances.chatModel: { timeout-duration: 20s }
```
(Resilience4j structure from the research[^41]; the specific retry values above are an architect's suggestion, not from a source.)

---

## 7. Code-Structure Patterns (Maintainability)

### 7.1 Port + anti-corruption adapter
Domain owns the interface and result types; the adapter owns `ChatClient`, prompt templates and parsing. Spring AI's own `RelevancyEvaluator` follows this shape (narrow `Evaluator` interface; adapter owns `ChatClient.Builder` + `PromptTemplate`)[^34].

```java
public interface SummarizationPort { Summary summarize(DocumentToSummarize document); }

@Component
class SpringAiSummarizationAdapter implements SummarizationPort {
    public Summary summarize(DocumentToSummarize document) {
        SummaryResult result = chatClient.prompt()
            .user(u -> u.text(summarizePrompt.getTemplate()).param("document", document.text()))
            .call().entity(SummaryResult.class);
        return new Summary(result.summary(), result.confidence());   // translate -> domain
    }
}
```
Enforce with **ArchUnit** (only the adapter package may depend on `ChatClient`)[^37]. This also makes later extraction to a separate AI service a matter of swapping the adapter; start as a modular monolith[^37].

### 7.2 Prompts as versioned artifacts
Externalise to `classpath:/prompts/*.st` (`.defaultSystem(resource)`, `StTemplateRenderer`) as in `spring-petclinic-ai`[^34]. Versioning (`prompt_v2.st` chosen by property/Spring Cloud Config) is a community convention; Langfuse via OTLP/Micrometer gives "which prompt produced which output" traceability but does not manage the files[^34][^38]. Note: a `templateRenderer` set on `ChatClient` does not affect advisor-internal templates[^2].

### 7.3 Keep LLM calls out of transactions
Write intent to an outbox/event in the transaction, call the model after commit off-thread (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`, or a poller), and make handlers **idempotent** (idempotency key → stored response) so retries don't re-bill[^36].

### 7.4 Determinism seam
LLM output → typed record/enum → bean validation → deterministic code. Use `Assert`/validation immediately after `.entity()`[^34].

---

## 8. Security & Guardrails (what exists vs. what you build)

| Risk (OWASP 2025) | Spring AI provides | You must build/do |
|---|---|---|
| LLM01 Prompt injection | Advisor chain; `SafeGuardAdvisor` | `SafeGuardAdvisor` is a **case-sensitive substring blocklist on input only** (open issue #7058)[^21]; add classifier/LLM-judge advisor; delimiter-separate retrieved content |
| LLM02/07 Disclosure & prompt leakage | — | Input PII-masking and **output** secret-leak advisors (regex = floor; names need real PII tooling, e.g. Presidio via community wrapper)[^21] |
| LLM03 Supply chain | Frequent patch releases | Stay patched: CVE-2026-22738 (SpEL RCE via user-supplied filter **key** in `SimpleVectorStore`, CVSS 9.8), CVE-2026-22742 (SSRF in Bedrock media URL fetch) fixed in 1.0.5/1.1.4[^22] |
| LLM04/08 Poisoning, vector weaknesses | Metadata filters | Sanitise ingestion; filter *values* only, never user-controlled keys; mandatory tenant filter (**no default-deny** — forgetting `FILTER_EXPRESSION` leaks cross-tenant data)[^22][^21] |
| LLM05 Output handling | Advisors | Validate/sanitise before use; never execute model output |
| LLM06 Excessive agency | `ToolContext`, fallback-resolution off by default, call limits | **`@PreAuthorize` on `@Tool` methods currently doesn't work** (issues #2356/#3272) → delegate to a secured `@Service`, and translate `AccessDeniedException` to a safe tool result[^24]; **HITL approval isn't shipped** (proposal #4878 explicitly excludes async/HITL in v1) → persist pending tool calls and build approve/resume yourself[^23] |
| LLM09 Misinformation | `FactCheckingEvaluator`, `RelevancyEvaluator`, empty-context refusal | Wire judges into runtime gating if needed (not shown in docs)[^12][^5] |
| LLM10 Unbounded consumption | Token usage metrics | Token-budget advisor, gateway rate limits, tool-call limits[^11][^7] |

Other: secrets via env/secret manager (standard Spring practice, not a Spring AI feature); `ModerationModel` exists but integration into the chain is DIY[^21]; MCP-security SSRF CVE-2026-45609 is **unverified** against an official advisory[^22]. Enterprise lesson (Tanzu/Broadcom): protect expensive AI endpoints with edge rate limiting and central SSO/identity forwarding[^40].

---

## 9. Migration & Ecosystem Decisions

**1.x → 2.0 traps** (from the official upgrade notes)[^1][^4][^39]:
- Per-model tool loops removed; `ChatClient` **auto-registers** `ToolCallingAdvisor` → manually-added ones are registered twice; `ToolCallAdvisor` renamed `ToolCallingAdvisor`; legacy `FunctionCallback`/`defaultFunctions` removed; `streamToolCallResponses` removed.
- Memory order changed (+1000 → +200); **conversation ID mandatory**; `PromptChatMemoryAdvisor` removed; JDBC schema needs a `sequence_id` migration; Mongo ordering bug fixed (remove manual reversal workarounds).
- Options immutable (`mutate()`), `.options` dropped from property keys, **default temperature 0.7 removed** (behaviour may shift), `N()`→`n()`.
- OpenAI/Anthropic moved to vendor SDKs (retry/HTTP-client config no longer applies); Azure OpenAI/OCI modules removed (use unified `spring-ai-openai`); MCP SDK 2.0 changes; module rename `spring-ai-vector-store-advisor`.

**Framework choice**: Spring AI is the default for Spring Boot shops; LangChain4j for non-Spring/polyglot; Quarkus LangChain4j for native/serverless; Embabel (built on Spring AI, pre-GA at time of research) or Google ADK's Spring AI adapter when you need autonomous planning; Semantic Kernel Java is effectively superseded by Microsoft Agent Framework[^39]. Portability is real only for the common `ChatOptions` subset; provider-specific options and multimodal capabilities break portability[^39].

---

## 10. Testing & Observability Strategy

**Testing pyramid**[^35][^12][^13]
1. **Unit** — mock `ChatModel`, build a real `ChatClient` around it; assert your logic deterministically.
2. **Golden/example tests** — JSON-fixture scenarios asserting only on the structured "intent" field.
3. **LLM-as-judge** — `RelevancyEvaluator` (topical relevance) vs `FactCheckingEvaluator` (groundedness; a relevant-but-wrong answer passes the former, fails the latter), ideally with a *separate* cheap judge model (e.g. `bespoke-minicheck`) configured as its own `ChatClient` bean.
4. **Adversarial/red-team** — prompt-injection suites; run real-model tiers nightly/on merge to control cost.
5. **Integration** — Testcontainers with `@ServiceConnection` (Ollama, Qdrant, Chroma, Milvus, OpenSearch, Weaviate, Typesense; pgvector via the plain Postgres container).
Promptfoo/DeepEval have no first-party Java integration — bridge over HTTP if needed[^12].

**Observability**[^11]: Micrometer observations for `ChatClient`, advisors, `ChatModel` (`gen_ai.client.operation`), tools (`spring.ai.tool`), embeddings, vector stores (`db.vector.client.operation`); metric `gen_ai.client.token.usage` (input/output/total) for cost dashboards. Prompt/completion/tool-argument/vector-response content is **off by default** (`spring.ai.chat.client.observations.log-prompt`, `spring.ai.chat.observations.log-prompt|log-completion`, `spring.ai.tools.observations.include-content`, `spring.ai.vectorstore.observations.log-query-response`) because of privacy risk. Streaming HTTP spans are not parented under the chat span for OpenAI/Anthropic.

---

## 11. Architect's Checklist

1. **Scope**: can a single prompt + retrieval or a fixed workflow solve it? Only then add agents[^25][^27].
2. **Eval set + CI gate** before prompt/model iteration[^28][^30].
3. **Port/adapter** boundary + ArchUnit rule; typed outputs with validation[^37][^34].
4. **Prompts** in versioned resources; log prompt-version with traces[^34][^38].
5. **Advisor pipeline**: input guardrail → memory → tools → RAG → output guardrail/budget; verify ordering explicitly[^3][^4].
6. **Resilience**: one retry layer, CB + bulkhead + timeout, fallback model/gateway, virtual threads, pool sizing[^14][^41][^15].
7. **Async** for long tasks; outbox + idempotency; no LLM calls inside DB transactions[^36].
8. **Memory**: durable shared repository, server-derived conversation IDs, plan for compaction (`spring-ai-session`), beware dropped tool messages[^18][^19].
9. **RAG**: chunking/embedding batching, index tuning, tenant filters, ingestion sanitisation, empty-context refusal[^5][^6].
10. **Tools**: least privilege, `ToolContext`, call limits, fallback-resolution off, HITL for write actions, authorisation inside secured services[^7][^24][^23].
11. **Security**: patch cadence, filter keys never user-controlled, SSRF-safe readers, MCP endpoints authenticated[^22][^8].
12. **Cost**: provider prompt caching, model routing, token metrics/budgets, gateway quotas[^17][^11][^31].
13. **Observability** with content logging off by default; dashboards on token usage/latency/cache hits[^11].

---

## 12. Key Sources Table

| Source | Role |
|---|---|
| [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai) | Framework source, docs (`spring-ai-docs`), upgrade notes |
| [docs.spring.io/spring-ai/reference](https://docs.spring.io/spring-ai/reference) | Official reference (2.0.x) |
| [spring-projects/spring-ai-examples](https://github.com/spring-projects/spring-ai-examples) | `agentic-patterns`, MCP examples |
| [spring-petclinic/spring-petclinic-ai](https://github.com/spring-petclinic/spring-petclinic-ai) | Prompts-as-resources, advisor config |
| [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security) | MCP OAuth2/API-key security |
| [spring-ai-community/spring-ai-session](https://github.com/spring-ai-community/spring-ai-session) | Event-sourced memory + compaction |
| [spring-ai-community/spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) | Skills, sub-agents, auto-memory |
| [anthropic.com/engineering](https://www.anthropic.com/engineering/building-effective-agents) | Workflows vs agents, context engineering |
| [OpenAI agents guide (PDF)](https://cdn.openai.com/business-guides-and-resources/a-practical-guide-to-building-agents.pdf) | Agent design, guardrails, HITL |
| [Chip Huyen GenAI platform](https://huyenchip.com/2024/07/25/genai-platform.html) | Gateway, caching, guardrails |
| [Martin Fowler](https://martinfowler.com/articles/gen-ai-patterns/) | Evals, guardrails, patterns |
| [OWASP GenAI](https://genai.owasp.org/llm-top-10/) | LLM Top 10 |

---

## 13. Confidence Assessment

**High confidence (primary-source verified):** Spring AI 2.0.x architecture (advisor ordering constants, `ChatClient`, structured output, RAG pipeline, vector-store options, tool-calling API, observability properties, `Evaluator` API, retry behaviour and the SDK-based OpenAI/Anthropic HTTP change, memory behaviour, upgrade-note breaking changes); vendor-neutral principles quoted from Anthropic/OpenAI/Fowler/Huyen/Yan/Husain/Azure.

**Medium confidence:** the 2.0 GA date (secondary sources); CVE details (official spring.io pages for 22738/22742, secondary source for 22743; **CVE-2026-45609 unverified**); open GitHub issues/discussions' status (#2356, #3272, #4878, #7058) as of research time; community patterns (Resilience4j double-retry interaction, Spring Batch ingestion, multi-model fallback, Langfuse, outbox/idempotency, ArchUnit rule, prompt versioning) — credible but **not first-party**.

**Unverified / gaps:** OpenAI typed cached-token accessor; Anthropic HTTP-pool customizer; per-provider typed rate-limit metadata beyond Anthropic; `AnthropicCacheProperties` YAML keys; Koog and Semantic Kernel details (search-synthesised); the chat-memory doc warning about tool messages may be stale vs 2.0 behaviour; no Google Cloud reference architecture was retrieved (Azure used instead); several comparison blogs are SEO/aggregator content; JavaOne material unconfirmed.

**Assumptions:** the architect's recommendations in §6 (specific retry numbers) and §11 are synthesis, not quotations; "start as modular monolith" is consensus commentary rather than a Spring-specific mandate[^37].

---

## 14. Repo finding: Boot 4.0.3 + Spring AI 2.0.1 (added 2026-10-05, not part of the original report)

**Question.** Some reports (issue spring-projects/spring-ai#6465, title only seen) say Spring AI 2.0.x starters pull Spring Boot 4.1
dependencies even though 2.0 is documented for Boot 4.0 as well. JordyLab ran Boot 4.0.3 with Spring AI 2.0.1. Does the problem apply?

**Method.** From `jordylab-be/`: `./gradlew dependencies --configuration runtimeClasspath --no-daemon -q`, then list every
`org.springframework.boot:spring-boot*` coordinate with the version it *requested* and the version it *resolved* to.

**Result on Boot 4.0.3 (before spec 012 US16).** The Spring AI 2.0.1 starters (`spring-ai-starter-model-anthropic`,
`-openai`, `-vector-store-pgvector`) *request* `spring-boot-starter`, `spring-boot-starter-jdbc`, `-restclient` and `-webclient`
at **4.1.1**. The Boot 4.0.3 BOM (applied by the dependency-management plugin) resolves all of them to **4.0.3**
(`4.1.1 -> 4.0.3` in the tree). Result: 0 coordinates resolved to a 4.1.x version, so the classpath held a single Boot minor. The mismatch exists as a
*request*, not as a resolved jar, because the BOM wins. It would bite a build that does not apply the Boot BOM (plain Gradle
platform without constraint enforcement) or a Maven build that does not manage these starters.

**Result on Boot 4.1.1 (spec 012 PR 1c, pull request #106).** Every Boot coordinate resolves to 4.1.1 (106 of 106 references), nothing is forced down, and the
`JordylabApplicationTests` context loads with Spring AI 2.0.1 on Boot 4.1.1 (run alone; the full local suite has Podman-related
container failures that also occur on main, see `specs/012-technical-improvements/research.md`, PR 1c record).

**Conclusion.** The combination worked, because the Boot BOM forced the starters down. Moving to Boot 4.1.1 removes the
request/resolution mismatch altogether. Spring AI 2.0.1 is the newest 2.0.x (checked 2026-10-05), so nothing newer is available to pair.

---

## Footnotes

[^1]: spring-projects/spring-ai `spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc` ([raw](https://raw.githubusercontent.com/spring-projects/spring-ai/main/spring-ai-docs/src/main/antora/modules/ROOT/pages/upgrade-notes.adoc)); latest release `v2.0.1` of [spring-projects/spring-ai](https://github.com/spring-projects/spring-ai); `main` at commit `46d05c2fdbab52f6ef04207abe19b12687a47453` (pom `2.1.0-SNAPSHOT`).
[^2]: `spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/ChatClient.java` (blob `d089276c`), `spring-ai-model/.../converter/BeanOutputConverter.java`; docs: [chatclient](https://docs.spring.io/spring-ai/reference/api/chatclient.html), [structured output](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html), [prompt](https://docs.spring.io/spring-ai/reference/api/prompt.html).
[^3]: [Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html); `SimpleLoggerAdvisor.java`, `MessageChatMemoryAdvisor.java`, `QuestionAnswerAdvisor.java` in spring-ai at commit `46d05c2f`.
[^4]: `upgrade-notes.adoc` "Upgrading to 2.0.0 → Tool Calling" (~lines 505–900); `Advisor.java:36`, `ToolCallingAdvisor.java:75` (commit `46d05c2f`).
[^5]: [Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html); `spring-ai-rag/src/main/java/org/springframework/ai/rag/advisor/RetrievalAugmentationAdvisor.java:63-190`.
[^6]: [Vector Databases](https://docs.spring.io/spring-ai/reference/api/vectordbs.html), [ETL pipeline](https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html), [pgvector](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html), [Redis](https://docs.spring.io/spring-ai/reference/api/vectordbs/redis.html), [embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings.html); `TokenCountBatchingStrategy.java:33-60`.
[^7]: `spring-ai-docs/.../pages/api/tools.adoc` (lines ~36–920) — rendered at [Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html).
[^8]: `spring-ai-docs/.../api/mcp/mcp-client-boot-starter-docs.adoc`, `mcp-server-boot-starter-docs.adoc:17-91`.
[^9]: `spring-ai-docs/.../api/mcp/mcp-security.adoc`; [spring-ai-community/mcp-security](https://github.com/spring-ai-community/mcp-security).
[^10]: `spring-ai-docs/.../api/effective-agents.adoc` ([rendered](https://docs.spring.io/spring-ai/reference/api/effective-agents.html)); [spring-projects/spring-ai-examples `agentic-patterns/`](https://github.com/spring-projects/spring-ai-examples).
[^11]: `spring-ai-docs/.../observability/index.adoc:1-420` ([rendered](https://docs.spring.io/spring-ai/reference/observability/index.html)); `DefaultChatModelObservationConvention.java:38-70`; `ChatObservationAutoConfiguration.java:76-100`; `AiObservationMetricNames.java:32-40`.
[^12]: [Evaluation Testing](https://docs.spring.io/spring-ai/reference/api/testing.html) (`api/testing.adoc:1-178`); [LLM-as-judge guide](https://docs.spring.io/spring-ai/reference/guides/llm-as-judge.html); [Baeldung evaluators tutorial](https://www.baeldung.com/spring-ai-testing-ai-evaluators); `RetrievalAugmentationAdvisorIT.java:233-241`.
[^13]: [Testcontainers](https://docs.spring.io/spring-ai/reference/api/testcontainers.html); `OllamaContainerConnectionDetailsFactory.java:17-49`; `spring-ai-integration-tests/.../TestcontainersConfiguration.java`.
[^14]: `spring-ai-retry/src/main/java/org/springframework/ai/retry/RetryUtils.java:106-112`; `SpringAiRetryAutoConfiguration.java:88-125`; `SpringAiRetryProperties` (commit `46d05c2f`); open issue spring-projects/spring-ai#1073.
[^15]: [ChatClient "Implementation Notes"](https://docs.spring.io/spring-ai/reference/api/chatclient.html) (`chatclient.adoc:983-994`); [Anthropic chat](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html); [Spring Boot task execution](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html).
[^16]: [OpenAI chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html); `models/spring-ai-openai/.../http/okhttp/SpringAiOpenAiHttpClient.java`; `anthropic-migration.adoc`.
[^17]: `models/spring-ai-anthropic/.../AnthropicCacheOptions.java`, `AnthropicCacheStrategy.java`; `anthropic-chat.adoc:915-955`; [Dan Vega on prompt caching](https://www.danvega.dev/blog/spring-ai-prompt-caching).
[^18]: `spring-ai-docs/.../api/chat-memory.adoc` ([rendered](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)); `ChatMemory.java:30-57`; `MessageWindowChatMemory.java`; `Neo4jChatMemoryRepository.java:187-220`; `VectorStoreChatMemoryAdvisor.java`.
[^19]: [spring-ai-community/spring-ai-session](https://github.com/spring-ai-community/spring-ai-session) (`TokenCountCompactionStrategy`, `SessionMemoryAdvisor`); `upgrade-notes.adoc` "Upgrading to 2.0.0" TIP stating it is planned to replace `ChatMemory` in 2.1.
[^20]: [spring-ai-community/spring-ai-agent-utils](https://github.com/spring-ai-community/spring-ai-agent-utils) (`TaskTool.java`); [Spring blog: Agent Skills](https://spring.io/blog/2026/01/13/spring-ai-generic-agent-skills/); `advisors/spring-ai-tool-search-advisor`; [embabel/embabel-agent](https://github.com/embabel/embabel-agent); [alibaba/spring-ai-alibaba](https://github.com/alibaba/spring-ai-alibaba).
[^21]: [Dan Vega – SafeGuardAdvisor & guardrails](https://www.danvega.dev/blog/spring-ai-guardrails-safeguard-advisor) and [spring-ai-safeguards](https://github.com/danvega/spring-ai-safeguards); [issue #7058](https://github.com/spring-projects/spring-ai/issues/7058); [Moderation docs](https://docs.spring.io/spring-ai/reference/api/moderation.html); community [spring-ai-privacy-guardrails](https://github.com/ultramancode/spring-ai-privacy-guardrails) (unvetted).
[^22]: [CVE-2026-22738](https://spring.io/security/cve-2026-22738); [CVE-2026-22742](https://spring.io/security/cve-2026-22742); CVE-2026-22743 and CVE-2026-45609 via secondary sources only.
[^23]: [Discussion #4878 – Tool Approval Strategy](https://github.com/spring-projects/spring-ai/discussions/4878); [Discussion #3331](https://github.com/spring-projects/spring-ai/discussions/3331); [spring-ai-playground HITL](https://spring-ai-community.github.io/spring-ai-playground/hitl-architecture/).
[^24]: [Issue #2356](https://github.com/spring-projects/spring-ai/issues/2356); [Issue #3272](https://github.com/spring-projects/spring-ai/issues/3272); `tools.adoc` resolution-fallback warning.
[^25]: Anthropic, [Building effective agents](https://www.anthropic.com/engineering/building-effective-agents).
[^26]: Anthropic, [Effective context engineering for AI agents](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents).
[^27]: OpenAI, [A Practical Guide to Building Agents (PDF)](https://cdn.openai.com/business-guides-and-resources/a-practical-guide-to-building-agents.pdf), pp. 4–7, 11–17, 24–27, 31.
[^28]: Eugene Yan, [Patterns for Building LLM-based Systems](https://eugeneyan.com/writing/llm-patterns/).
[^29]: Martin Fowler, [Emerging Patterns in Building GenAI Products](https://martinfowler.com/articles/gen-ai-patterns/).
[^30]: Hamel Husain, [Your AI Product Needs Evals](https://hamel.dev/blog/posts/evals/).
[^31]: Chip Huyen, [Building A Generative AI Platform](https://huyenchip.com/2024/07/25/genai-platform.html).
[^32]: Microsoft, [Well-Architected AI workloads](https://learn.microsoft.com/en-us/azure/well-architected/ai/get-started), [design principles](https://learn.microsoft.com/en-us/azure/well-architected/ai/design-principles), [GenAIOps](https://learn.microsoft.com/en-us/azure/well-architected/ai/mlops-genaiops), [baseline Foundry chat architecture](https://learn.microsoft.com/en-us/azure/architecture/ai-ml/architecture/baseline-microsoft-foundry-chat).
[^33]: [OWASP GenAI Top 10 for LLM Applications 2025](https://genai.owasp.org/llm-top-10/) (list cross-checked via per-risk pages and secondary sources).
[^34]: [spring-petclinic/spring-petclinic-ai](https://github.com/spring-petclinic/spring-petclinic-ai) `ChatConfiguration.java:29-45`, `prompts/system.st`; spring-ai `Evaluator.java:26-35`, `RelevancyEvaluator.java:33-83`; spring-ai-examples `RoutingWorkflow.java:102-155`, `EvaluatorOptimizer.java:124-236`; [Langfuse–Spring AI integration](https://langfuse.com/integrations/frameworks/spring-ai).
[^35]: Thoughtworks/Fowler, [Engineering Practices for LLM Application Development](https://martinfowler.com/articles/engineering-practices-llm.html) (only partially fetched); [spring-ai discussion #679](https://github.com/spring-projects/spring-ai/discussions/679) on mocking `ChatModel`.
[^36]: Wim Deblauwe, [Transactional outbox pattern with Spring Boot](https://www.wimdeblauwe.com/blog/2024/06/25/transactional-outbox-pattern-with-spring-boot/); [microservices.io outbox](https://microservices.io/patterns/data/transactional-outbox.html); idempotency write-up on [dev.to](https://dev.to/thellu/idempotency-keys-in-spring-boot-make-post-safe-against-retries-3h87).
[^37]: [ArchUnit + AI article](https://www.programinjava.com/2026/05/architecting-java-code-in-age-of-ai.html); [Azure Anti-Corruption Layer pattern](https://learn.microsoft.com/en-us/azure/architecture/patterns/anti-corruption-layer); modular-monolith commentary on [dev.to](https://dev.to/codewithamrendra/microservices-vs-modular-monolith-in-2026-which-architecture-actually-scales-1iie) (secondary).
[^38]: [langfuse-examples spring-ai-demo](https://github.com/langfuse/langfuse-examples/tree/main/applications/spring-ai-demo); prompt-versioning convention: [jsbisht blog](https://blogs.jsbisht.com/blog/spring-ai-prompt-templates/) (community).
[^39]: [google/adk-java](https://github.com/google/adk-java); [Embabel discussion #417](https://github.com/embabel/embabel-agent/discussions/417); [Spring I/O 2026 agentic frameworks session](https://2026.springio.net/sessions/comparing-agentic-ai-frameworks-for-java/); [model comparison matrix](https://docs.spring.io/spring-ai/reference/api/chat/comparison.html); `upgrade-notes.adoc` 2.0.0 sections (lines ~504–2397).
[^40]: [Broadcom Tanzu blog – Modern Spring Workflow](https://blogs.vmware.com/tanzu/the-modern-spring-workflow-is-enterprise-ready-and-ai-boosted/); [Spring I/O 2025 – Tzolov](https://2025.springio.net/sessions/from-single-shot-llms-to-intelligent-agents-building-scalable-ai-systems-with-spring-ai-and-mcp/).
[^41]: Resilience4j + Spring AI double-retry caution from community write-ups (e.g. dev.to "Retry and Circuit Breaker in Spring Boot: When Resilience Backfires") — not official Spring documentation.
[^42]: Baeldung "Configuring Multiple LLMs in Spring AI" and Java Code Geeks multi-LLM article (community patterns); [Adrastopoulos/spring-ai-multi-provider](https://github.com/Adrastopoulos/spring-ai-multi-provider); no fallback/router type exists in `spring-projects/spring-ai`.
[^43]: Spring blog, [Spring AI 2.0.0 GA available now](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/) (12 June 2026): "designed to be used with Spring Boot 4.0 / 4.1", Spring Framework 7.0.

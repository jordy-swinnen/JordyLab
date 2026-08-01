# Contract: ResilientAiService

**Spec**: [spec.md](../spec.md) — FR-001 through FR-010
**Data model**: [data-model.md](../data-model.md)

---

## Public API

`ResilientAiService` is the sole entry point for all AI calls in JordyLab. No module may call a `ChatModel` directly (FR-009).

### Method

```java
public AiCallResult call(String moduleName, String systemPrompt, String userPrompt)
```

**Parameters**:

| Parameter | Type | Validation | Description |
|-----------|------|------------|-------------|
| `moduleName` | `String` | non-blank, must exist in `AiModuleConfig` | Identifies the calling module for provider routing and metrics attribution |
| `systemPrompt` | `String` | non-blank | The system message passed to the chat model |
| `userPrompt` | `String` | non-blank | The user message passed to the chat model |

**Returns**: `AiCallResult` (never `null`; never throws — failures are returned as `AiCallResult.failure(...)`)

### Return contract

| Outcome | `success` | `content` | `failureReason` | Behaviour |
|---------|-----------|----------|-----------------|-----------|
| Provider returned text | `true` | non-null `String` | `null` | Provider attributed, metrics recorded |
| Provider unreachable / down (cache hit) | `false` | `null` | `UNREACHABLE` | Health cache was stale/unhealthy; no inference call attempted |
| Provider call threw exception | `false` | `null` | `UNREACHABLE` / `TIMEOUT` / `RATE_LIMITED` / `AUTH_FAILED` / `UNKNOWN` | Exception mapped to enum; health cache invalidated (FR-004); reason recorded (FR-007) |

### Bounds

- NEVER throws — all failures surface as `AiCallResult.failure(...)`.
- NEVER records raw exception text in the failure reason (FR-007). Raw text MAY be logged at `DEBUG`/`TRACE` level but is not part of the returned contract.
- Health probes are bounded by `health-check-timeout-seconds` (default 2s) and MUST NOT block the caller indefinitely (FR-008).

---

## Provider selection contract

Per-module routing (FR-010):

```
moduleName → AiModuleConfig.modules[moduleName] → { provider, model }
provider → ChatModel bean (currently only "anthropic")
```

MVP1 supports one provider (`anthropic`). An unknown `moduleName` or an unmapped provider returns `AiCallResult.failure(module, provider, model, UNKNOWN)` with a logged warning — never throws.

---

## Health cache contract (`ProviderHealthCache`)

| Operation | Behaviour | Spec ref |
|-----------|-----------|----------|
| `isHealthy(providerName)` | Returns cached status if within TTL; returns `true` if no entry exists yet (optimistic first call) | FR-002 |
| `recordFailure(providerName)` | Invalidates the cached entry immediately | FR-004 |
| `recordSuccess(providerName)` | Refreshes the cached `lastCheckedAt` and marks healthy | FR-002 |

| Configuration key | Default | Description |
|-------------------|---------|-------------|
| `jordylab.ai.health-check-ttl-seconds` | `30` | Seconds before a cached health status is considered stale |
| `jordylab.ai.health-check-timeout-seconds` | `2` | Maximum seconds for a health probe before timeout |

---

## Caller integration contract — `BriefingGeneratorService`

The `BriefingGeneratorService` is the only current caller. It MUST:

1. Call `aiService.call("fna", SYSTEM_PROMPT, userPrompt)`.
2. Inspect `AiCallResult.success()`:
   - On success: build and save a `Briefing` with `content = result.content()` and `modelUsed = result.model()`.
   - On failure: log the normalized `failureReason`, do NOT save a `Briefing`, return `null` (or throw a caught domain exception). Do NOT retry within the same cron tick (clarification Q1: A).

The `@Scheduled(cron = "0 30 6 * * *")` fires once daily. The next attempt is the following 06:30.

---

## Metrics contract

Per-call attribution is recorded (FR-006, FR-007). The contract does not mandate a metrics backend in MVP1 (LLM tracing/observability tooling is out of scope) — recording happens via structured `@Slf4j` logs that are queryable by provider, module, and failure reason. A future feature may replace this with Micrometer meters.

### Recorded per success call

| Field | Source |
|-------|--------|
| `module` | `AiCallResult.module` |
| `provider` | `AiCallResult.provider` |
| `model` | `AiCallResult.model` |

### Recorded per failure

| Field | Source |
|-------|--------|
| `module` | `AiCallResult.module` |
| `provider` | `AiCallResult.provider` |
| `model` | `AiCallResult.model` |
| `failureReason` | `AiCallResult.failureReason` (enum name) |
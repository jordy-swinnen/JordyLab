# Phase 1 Data Model: Complete FNA MVP1

**Date**: 2026-08-01
**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

---

## Scope

This feature introduces **no new JPA entities** and **no database schema changes** (spec assumption: "No database schema changes are required"). The data model here covers the new in-memory value objects, the configuration model, and the failure model that the `ResilientAiService` uses.

---

## 1. Configuration model — `AiModuleConfig`

A Spring `@ConfigurationProperties` record binding `jordylab.ai.modules.*` properties. Each module declares its provider and model.

### Fields

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `moduleName` | `String` | non-blank, stable enum-like string (`"fna"`, `"gamecatalog"`, `"recipe"`) | Identifies the calling module for routing and metrics |
| `provider` | `String` | non-blank, one of supported providers (`"anthropic"` in MVP1) | Which chat model implementation to route to |
| `model` | `String` | non-blank | Model identifier passed to the provider (e.g., `"claude-sonnet-4-20250514"`) |

### Properties file shape

```yaml
jordylab:
  ai:
    health-check-ttl-seconds: 30
    call-timeout-seconds: 120
    modules:
      fna:
        provider: anthropic
        model: claude-sonnet-5
```

### Lifecycle

- Loaded once at application startup by Spring `@ConfigurationProperties`.
- Immutable after binding — provider selection does not change at runtime without a restart (FR-010: "configurable without a code change", not "configurable without a restart").

---

## 2. Failure model — `ProviderFailureReason`

A Java enum representing the bounded set of normalized failure reasons (spec FR-007, clarification Q2: B).

### Values

| Enum value | Trigger condition | Notes |
|------------|-------------------|-------|
| `UNREACHABLE` | DNS resolution failure, connection refused, unknown host | Provider is down or unreachable from the host |
| `TIMEOUT` | `SocketTimeoutException`, `call-timeout-seconds` exceeded, read timeout | Provider accepted connection but did not respond in time |
| `RATE_LIMITED` | HTTP 429 from provider | Provider throttled the request |
| `AUTH_FAILED` | HTTP 401/403 from provider | Credentials invalid or lacking permissions |
| `UNKNOWN` | Any other exception not in the above categories | Catch-all for unrecoverable, unexpected failures |

### Mapping logic

Exception-to-enum mapping happens in `ResilientAiService` at catch time:

| Exception type / status | → Enum value |
|-------------------------|-------------|
| `UnknownHostException`, `ConnectException` | `UNREACHABLE` |
| `SocketTimeoutException` | `TIMEOUT` |
| HTTP 429 | `RATE_LIMITED` |
| HTTP 401, 403 | `AUTH_FAILED` |
| Everything else | `UNKNOWN` |

### Lifecycle

- Stateless enum — created as a value on `AiCallResult`, persisted in metrics/logs, never mutated.

---

## 3. Call result — `AiCallResult`

A Java `record` returned by `ResilientAiService.call(...)` to every caller. Replaces the current `String` return type, which conflated success text with failure by throwing.

### Fields

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `success` | `boolean` | — | True if the provider returned text; false on any failure |
| `module` | `String` | non-blank | The module that originated the call (for attribution, FR-006) |
| `provider` | `String` | non-blank | The provider that served (or attempted) the call |
| `model` | `String` | non-blank | The model identifier used |
| `content` | `String` | nullable (null when `success=false`) | The generated text on success; `null` on failure |
| `failureReason` | `ProviderFailureReason` | nullable (null when `success=true`) | Normalized reason on failure; `null` on success |

### Constructors

- `AiCallResult.success(module, provider, model, content)` — factory for success path.
- `AiCallResult.failure(module, provider, model, failureReason)` — factory for failure path.

### Lifecycle

- Immutable `record`, created per call, passed to the caller for inspection. Callers decide whether to throw, log, save, or skip.

---

## 4. Health status — `ProviderHealth`

An immutable value object cached per provider in `ProviderHealthCache`. There is no separate,
out-of-band health probe — health status is derived passively from the outcome of real AI calls.

### Fields

| Field | Type | Validation | Description |
|-------|------|------------|-------------|
| `provider` | `String` | non-blank | Provider name |
| `healthy` | `boolean` | — | True if the most recent real AI call succeeded |
| `lastCheckedAt` | `Instant` | non-null | When the status was last updated |

TTL is not stored on `ProviderHealth` itself — staleness is evaluated at read time against
`AiModuleConfig.healthCheckTtlSeconds()`, so a single config change affects every cached entry
uniformly.

### Lifecycle

- Created and replaced (never mutated) by `recordSuccess`/`recordFailure` in `ResilientAiService`,
  called after every real AI call — not by a separate probe.
- Cached in `ProviderHealthCache` keyed by provider name.
- A stale entry (older than `health-check-ttl-seconds`) is treated as healthy again — the next
  real call attempt is what determines the actual current status; no active re-probe is issued.
- Invalidated immediately on runtime failure (FR-004) regardless of TTL.

---

## 5. Relationship to existing `Briefing entity`

The existing `Briefing` JPA entity (`dev.jordy.jordylab.fna.domain.Briefing`) is **unchanged** in schema. The only behavioural change is in `BriefingGeneratorService`: it now inspects `AiCallResult` before building/saving a `Briefing`. On failure, no `Briefing` is saved; the normalized reason is logged; the job returns without throwing.

The `Briefing.modelUsed` field continues to record the model that served the successful call (from `AiCallResult.model`).

---

## 6. Entity/dependency summary

| Name | Type | Persistence | Validation | Scope |
|------|------|-------------|------------|-------|
| `AiModuleConfig` | `@ConfigurationProperties record` | Spring config (YAML) | non-blank fields, valid provider name | bean |
| `ProviderFailureReason` | enum | none | bounded set | value |
| `AiCallResult` | `record` | none | factory methods enforce success/failure invariants | value |
| `ProviderHealth` | immutable class / record | none (in-memory cache) | non-null timestamps, positive TTL | value |
| `ProviderHealthCache` | `@Component` | in-memory `ConcurrentHashMap` | thread-safe | bean |
| `ResilientAiService` | `@Service` | none | — | bean |
| `Briefing` | JPA entity | `finance.briefing` table | extended from `AbstractAggregateRoot` | domain |
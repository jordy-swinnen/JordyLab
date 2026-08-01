# Quickstart: Complete FNA MVP1 Validation Guide

**Spec**: [spec.md](./spec.md)
**Plan**: [plan.md](./plan.md)

---

## Prerequisites

- Java 25, Gradle (wrapper included in `jordylab-be/`)
- Bun, Nx CLI (via `bunx nx` in `jordylab-fe/`)
- Anthropic API key configured in `jordylab-be/src/main/resources/application.yml` under `spring.ai.anthropic.api-key`
- PostgreSQL 16 + pgvector via `docker compose up -d` from `jordylab-be/`

---

## Backend — AI provider resilience

### Run resilient service unit tests

```bash
cd jordylab-be
./gradlew test --tests "dev.jordy.jordylab.shared.ai.*"
```

**Expected**: All `ResilientAiServiceTest`, `ProviderHealthCacheTest`, `ProviderFailureReasonTest`, `AiModuleConfigTest` pass. Tests assert per-call provider attribution, normalized failure reasons (enum values, never raw text), health-cache TTL behaviour, and bounded probe timeout.

### Validate briefing job failure handling

```bash
# Simulate provider unavailable — block the API host or set an invalid key
./gradlew test --tests "dev.jordy.jordylab.fna.service.BriefingGeneratorServiceTest"
```

**Expected**: On failure, `BriefingGeneratorService` does not save a `Briefing`, logs the normalized reason, and returns without throwing. The test asserts no `Briefing` entity is persisted and the reason is one of the five enum values.

### Validate single-attempt cron behaviour (acceptance scenario 2)

Run the `@ApplicationModuleTest` that boots the `fna` module with `@MockBean` `AnthropicChatModel` throwing on `call(...)`. Trigger `generateBriefing()` once. Assert: one call attempt, zero retries, no `Briefing` saved, normalized reason logged.

---

## Frontend — boundary enforcement

### Run lint and confirm boundary rules

```bash
cd jordylab-fe
bunx nx run-many -t lint
```

**Expected**: Lint passes. To verify enforcement (FR-011, FR-012), temporarily add an import from `fna-ui` inside `fna-api` and re-run lint — it must fail with the `@nx/enforce-module-boundaries` rule naming the violated constraint. Revert the import after verification.

### Validate CI enforcement

The lint target runs in `targetDefaults` and is cached. The same `bunx nx run-many -t lint` command is the CI gate. No separate CI script is needed.

---

## Frontend — test coverage

### Run tests with coverage

```bash
cd jordylab-fe
bunx nx run-many -t test --coverage
```

**Expected**: Every `fna-ui` view (render, populated, empty, error states) and the `fna-api` service (success + HTTP error paths) are covered. Coverage reports are emitted to `coverage/libs/fna/{ui,api}/`.

### Validate 80% threshold

Each lib's `test` target includes a coverage threshold. If any lib drops below 80% lines, the test target fails. Verify by temporarily removing a spec file and re-running — the build fails.

---

## Backend — test coverage

### Run build with JaCoCo verification

```bash
cd jordylab-be
./gradlew check
```

**Expected**: `jacocoTestCoverageVerification` runs after `test`. If any package drops below 80% instruction coverage, the build fails. Reports are emitted to `build/reports/jacoco/`.

---

## End-to-end — briefing cycle (acceptance scenario 1)

```bash
cd jordylab-be
./gradlew bootRun
# In another terminal:
curl -X POST http://localhost:8080/api/fna/briefing/generate
# Or wait for the 06:30 cron if running overnight
curl http://localhost:8080/api/fna/briefing/latest
```

**Expected**: A `Briefing` is returned with `content` non-empty and `modelUsed` matching the configured `jordylab.ai.modules.fna.model`. Structured logs attribute the call to provider `anthropic`, module `fna`.

---

## Documentation reconciliation (SC-005)

After implementation, verify:
- `AGENTS.md` AI Routing section matches the cloud-primary, per-module-config decision (already updated in the clarify phase).
- No remaining text in `AGENTS.md` or `jordylab-infrastructure-guide.md` references local-primary routing or fallback for `fna`.
- The AI Routing table marks `gamecatalog` and `recipe` as "Deferred".
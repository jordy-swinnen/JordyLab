---
paths:
  - "**/*.java"
---

# Java + Spring (JordyLab backend)

Applies to `jordylab-be/` and any new Spring Boot service in this repo.

## Language & syntax

- Java 25 — use the most up-to-date syntax
- **Never use `var`** — always declare explicit types
- Records for DTOs and value objects; `@Builder` over hand-written constructors
- `@UtilityClass` for stateless helpers
- No `@Data` — use `record` or `@Getter`/`@Setter` when mutation is needed
- Mutability only when JPA requires it
- Space before every `return` statement
- Method order: public first, then package-private, then private

## Lombok cheat sheet

- `@Builder` with a partial inner `XxxBuilder` class that overrides `build()` — the only place `Preconditions.checkArgument` guards live
- `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on every JPA entity (Hibernate needs it)
- `@AllArgsConstructor(access = AccessLevel.PRIVATE)` to support `@Builder`
- `@Getter` at class level; field-level `@Setter` only when the field type is immutable (String, UUID, primitives)
- `@RequiredArgsConstructor` for services
- `@Slf4j` for loggers

## Spring Modulith

- Module root package = public API (facade + DTOs); sub-packages = internal
- Use `@NamedInterface` for shared sub-packages exposed to other modules
- Cross-module communication through public facades only — never import internal packages
- Schema-per-module: each Flyway migration targets its own schema (`finance`, `gamecatalog`, `garmin`, `recipe`)
- The `ModularityTests` test is the build-time boundary check — run it after any structural change

## DDD entities (canonical)

- IDs are always `UUID`, never `Long`
- Extend `AbstractAggregateRoot<T>`
- Builder is the sole creation path
- `Preconditions.checkArgument` lives exclusively in `build()` — nowhere else
- Named mutation methods for non-trivial state changes — no raw setters
- `equals`/`hashCode` inherited from `BaseEntity` (use `EqualsVerifier` in the entity test)
- Audit fields (`createdDate`, `lastModifiedDate`) come from the base class — never redeclare
- `registerEvent(...)` is called inside mutation methods — never expose the events list directly

## Package structure (every module, exact)

```
{module}/
  {Module}Facade.java          ← public API
  {Module}Dto.java             ← only if shared across modules
  domain/
    repository/                ← JPA repository interfaces only
    {Entity}.java              ← one file per aggregate root
  rest/
    client/                    ← outbound HTTP (RestClient-based)
    controller/
      model/                   ← request/response records
      {X}Controller.java
  service/                     ← application services
  util/                        ← stateless helpers, always @UtilityClass
```

- `domain/` contains entities and repository interfaces — no service logic ever
- DTOs used only within a controller live in `rest/controller/model/`
- DTOs shared across modules are declared in the module root package only
- If a new sub-package feels necessary, stop and ask — do not invent structure

## Flyway

- Every migration starts with `CREATE SCHEMA IF NOT EXISTS <schema>;` + `SET search_path TO <schema>;`
- Naming: `V<yyyyMMdd>__<description>.sql`
- Never modify already-applied migrations — create a new migration instead
- Need `spring-boot-starter-flyway` dependency, not just `flyway-core` (Spring Boot 4 gotcha)

## Testing

- JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, MockMvc
- `assertSoftly` for multiple assertions in a single test
- **Never use `any()` in Mockito** — use explicit values or `ArgumentCaptor`
- Annotate inline JSON with `@Language("JSON")`
- `@ApplicationModuleTest` for module integration tests (boots only target + shared)
- TestBuilder fixture pattern: `@UtilityClass` class with `DEFAULT_*` constants and `aDefault{Thing}()` / `a{Thing}()` methods
- Entity test must include `EqualsVerifier` (suppress `SURROGATE_KEY`, `IDENTICAL_COPY_FOR_VERSIONED_ENTITY`, `STRICT_HASHCODE`)

## HTTP

- Use `RestClient`, not `WebClient` with `.block()`
- Use HttpClient interfaces with `RestClient`

## Style

- Full descriptive names — no abbreviations or single-letter identifiers
- Early returns over deep nesting
- Self-explanatory code over comments (KISS / Clean Code)
- Methods: short, single responsibility, pure where possible

## pgvector

- Use `vector(1536)` column type with `vector_cosine_ops` index operator class

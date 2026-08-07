# JordyLab Constitution

## Core Principles

### I. Clean Code Discipline
Apply SOLID, KISS, YAGNI, DRY, and Clean Code practices consistently. Use full,
descriptive names — no abbreviations or single-letter identifiers. Prefer
self-explanatory code over comments, and expressive constructs over clever
tricks. Favor early returns over deep nesting. Methods stay short,
single-responsibility, and pure where possible.

### II. Fail Fast, No Silent Failures
Exceptions are intentional and sparse. Fail fast with clear errors — never
fail silently.

### III. Immutable, Builder-First Design
Use builders for complex objects and prefer immutability throughout. In Java,
this means Lombok `@Builder` exclusively — no manual constructors — and
`@Record` over `@Data` wherever possible.

### IV. Testing Discipline
Tests are isolated and deterministic. Use `assertSoftly` for multiple
assertions, the Test Builder pattern for fixtures, and descriptive test names
that explain the scenario. Java tooling: JUnit 5, AssertJ, Mockito (never
`any()` — use explicit values or `ArgumentCaptor`; an `ArgumentCaptor` created
inline and never assigned to a variable is `any()` in disguise and is
forbidden the same way — a captor must be assigned and its captured value
asserted), Testcontainers, WireMock, MockMvc with `@Language("JSON")` for JSON
assertions. Angular tooling: Vitest with `@ngneat/spectator/vitest` — never
Jest — plus marble testing for observables. Never mock a dependency with a
hand-written mock class + `useClass`; provide a plain object via `useValue`
with `vi.fn()` for methods and real `signal(...)` instances for reactive
state.

### V. Language & Tooling Currency
Use the most up-to-date language and framework features: Java 25 on the
backend, Angular 21 / Nx 22 on the frontend. Never use `var` in Java. Prefer
`inject(Service)` over constructor injection and JavaScript `#field` over
TypeScript `private` in Angular.

## Additional Constraints
<!-- Technology stack requirements -->

- **Architecture patterns**: Container–Presentation component separation;
  Angular signals for state — no NgRx store; API-backed state lives in a
  signal store (`/angular-signal-store`) in the domain's `api` lib, not in
  the container component; barrel imports for clean import paths; clear
  separation of UI, business logic, and data access layers.
- **Java**: Lombok used liberally to reduce boilerplate; `@UtilityClass` for
  utility classes.
- **Test Builders**: suffixed `TestBuilder`, marked `@UtilityClass`, exposing
  two static methods — a minimal object and a pre-filled builder.

## Governance

This constitution supersedes ad-hoc practices where the two conflict.
Amendments require updating this file and noting the change under "Last
Amended" below. For concrete build/test commands, module boundaries, and
per-subproject conventions, defer to the root [AGENTS.md](../../AGENTS.md)
and the three subproject `AGENTS.md` files — this constitution covers
project-wide coding principles, not operational detail.

**Version**: 1.1.0 | **Ratified**: 2026-08-01 | **Last Amended**: 2026-08-02

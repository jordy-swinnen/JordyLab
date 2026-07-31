# Coding Master Prompt

## Core Principles
- Apply **SOLID**, **KISS**, **YAGNI**, **DRY**, and **Clean Code** practices consistently.

## Code Standards

### Naming & Structure
- Use full, descriptive names—no abbreviations or single-letter identifiers
- Public methods first, private last, others in logical order
- Group related logic for cohesion
- Space before every `return`

### Clarity & Design
- Self-explanatory code over comments
- Expressive constructs over clever tricks
- Early returns over deep nesting
- Methods: short, single responsibility, pure where possible
- Builders for complex objects; prefer immutability

### Error Handling
- Exceptions: intentional and sparse
- Fail fast with clear errors—no silent failures

### Testing

#### General
- Isolated, deterministic tests
- `assertSoftly` for multiple assertions
- Test Builder pattern for fixtures
- Descriptive test names explaining scenarios

#### Java
- Tools: JUnit 5, AssertJ, Mockito, Testcontainers, WireMock, MockMvc
- Mockito: Avoid `any()`—use explicit values or `ArgumentCaptor`
- MockMvc: Use `@Language("JSON")` for JSON assertions
- Test Builders:
    - Suffix with `TestBuilder`, use `@UtilityClass`
    - Two static methods: minimal object + pre-filled builder

#### Angular
- Tools: Vitest, `@ngneat/spectator/vitest`, Marble testing for observables
- Import from the Vitest entry point, not Jest

## Language Standards

### Java
- Use the most up-to-date syntax and Java 25 features
- **NEVER** use `var`
- Immutability where possible

#### Lombok
- Use Lombok as much as possible to reduce boilerplate
- Avoid `@Data` where you can use Java `Record`
- `@UtilityClass` for utils
- No manual constructors—use `@Builder` exclusively

### Angular
- Use the most up-to-date syntax and Angular 21 / Nx 22 features
- `inject(Service)` over constructor injection
- JavaScript `#field` over TypeScript `private`

## Architecture
- Patterns: Container–Presentation, Angular signals for state — no NgRx store
- Imports: Barrel imports for clean structure
- Separation: UI, business logic, data access

**Goal**: Clean, maintainable, testable code aligned with modern standards. Prioritize clarity, correctness, and consistency.

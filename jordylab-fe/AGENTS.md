# Commands

```bash
bun install                        # Install dependencies
bunx nx serve <app>                # Dev server (e.g. bunx nx serve fna)
bunx nx test <lib>                 # Test specific lib (e.g. bunx nx test fna-ui)
bunx nx run-many -t test           # Run all tests
bunx nx run-many -t lint           # Lint everything
```

Use `bun` and `bunx` — not `npm`, `npx`, or `yarn`.

# Angular Code Style

- Use `inject(Service)` over constructor injection
- Use JavaScript `#field` over TypeScript `private`
- Use Angular signals for state — no NgRx store
- Apply Container–Presentation pattern for smart/dumb component separation
- Use barrel imports via `index.ts` for clean paths
- Use zoneless mode
- Use spartan/ui (brain + helm) for UI components

# State

- Use the Custom Signal Store pattern (`/angular-signal-store`) for all API-backed state — a plain `@Injectable` service in the domain's `api` lib with private writable signals, public readonly signals, and methods that call the API and mutate state directly
- No NgRx, no `@ngrx/signals` `signalStore()` — hand-rolled services only
- Containers `inject()` the store directly — the store *is* the facade, no separate facade layer
- Prefer `httpResource()` only once verified stable in the installed `@angular/core` version (still `@experimental` as of 21.1.6) — default to manual signals until then

# Nx Structure

- Apps in `apps/`, domain libs in `libs/<domain>/{ui,api}`
- Two-layer lib structure per domain: `ui` (components) + `api` (services, HTTP, signal stores)
- Enforce module boundaries via Nx ESLint tags (`scope:*`, `type:*`)
- Tag new libs: `--tags="scope:<domain>,type:<ui|api>"`
- `type:ui` → can depend on `type:api` and `type:ui`
- `type:api` → can only depend on `type:api`
- `scope:<domain>` → can only depend on `scope:<domain>` and `scope:shared` — cross-domain state belongs in a `scope:shared` lib, not a relaxed boundary rule

# Component Library

- spartan/ui with brain (headless logic) + helm (styled components)
- Add components: `bunx @spartan-ng/cli@latest add <component-name>`

# Testing

- Use Vitest + `@ngneat/spectator/vitest` — import from the Vitest entry point, not Jest
- In each lib's `vite.config.mts`, inline Spectator: `test.server.deps.inline: ['@ngneat/spectator']`
- Descriptive test names explaining the scenario
- Follow existing test patterns in the codebase
- Never mock a dependency with a hand-written `class FooMock { ... }` + `useClass` — provide a plain object via `useValue` and mock individual methods with `vi.fn()`. For a store dependency, back its readonly signal properties with real `signal(...)` instances held at `describe` scope so tests can drive state with `.set(...)` directly, instead of injecting the mock back out and casting it
- Never write `spectator.inject(Token) as unknown as SomeMock` — if a test needs to assert on injected state, hold a reference to the mock's own signals/spies before creating the component, not by casting the DI-resolved instance
- Create the component once in `beforeEach`, not separately inside every `it()`
- Fixture data lives in `libs/<domain>/api/src/lib/mocks/<interface>.model.mock.ts` — one file per interface, named after it, exporting a factory function (`aFooMock(overrides = {}) => Foo`)
- Specs import via the barrel (`@jordylab-fe/<domain>/<layer>`), never deep-relative into another lib (`../../other-lib/src/...`)

# Commands

```bash
bun install                        # Install dependencies
bunx nx serve jordylab             # Main app (port 4200) — lazy-loads fna + gamecatalog routes
bunx nx serve fna                  # fna dev harness, no host/auth (port 4300)
bunx nx serve gamecatalog          # gamecatalog dev harness, no host/auth (port 4400)
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

All real code lives in per-domain libs; the apps are thin shells over them:

```
apps/jordylab          — the deployable app. Lazy-loads each domain's routes
apps/<domain>/         — dev harness per domain: boots that domain's routes alone,
                         without the host shell or Keycloak. Not deployed
libs/<domain>/{ui,api} — domain libs: ui = components + routes, api = services & HTTP
libs/ui/helm           — shared spartan helm overrides reused by multiple apps
```

Tag new libs with the right scope + type: `--tags="scope:<domain>,type:<ui|api>"`. App-level tags: `--tags="scope:<domain|shared>,type:app"`. Boundary rules:

- `type:api` → may depend on `type:api`, `type:shared`
- `type:ui` → may depend on `type:api`, `type:ui`, `type:shared`
- `type:app` → may depend on `scope:fna | scope:gamecatalog | scope:shared`
- `scope:fna` → may depend on `scope:fna`, `scope:shared`
- `scope:gamecatalog` → may depend on `scope:gamecatalog`, `scope:shared`
- `scope:shared` → may depend on `scope:shared` only

# Domain Routing

`apps/jordylab` is the single deployable app. Each domain's routes live in its `ui` lib and
are lazy-loaded by the host with a plain dynamic `import()` — Angular's own code splitting
puts each domain (and each component within it) in its own chunk, fetched on first visit.

- **Route ownership.** `libs/<domain>/ui/src/lib/<domain>.routes.ts` exports `<domain>Routes`,
  re-exported from the lib's barrel. The host mounts it under a path segment it owns
  (`/fna`, `/games`); domain routes are relative within that segment and must NOT repeat it.
- **Dev harnesses.** `apps/fna` and `apps/gamecatalog` exist only so `bunx nx serve <domain>`
  can exercise one domain without the host or Keycloak. Their `app.routes.ts` just re-exports
  the lib's routes, so there is one source of truth. They are not deployed.
- **No cross-domain imports.** No shared stores, no cross-domain service imports, no shared
  mutable state — enforced by the Nx tag boundaries above.
- **Base hrefs:** `apps/jordylab` → `/`, `apps/fna` → `/fna`, `apps/gamecatalog` → `/games`.
  If Angular's `baseHref` builder option normalises a trailing slash in, treat it as framework
  behaviour, not design intent.
- **Dev ports:** host `:4200`, harnesses start at `:4300` and increment.
- **Adding a new domain** (e.g. recipe, garmin, trading):
  1. `bunx nx g @nx/angular:library <name>-ui --directory=libs/<name>/ui --tags="scope:<name>,type:ui"`
  2. Add `libs/<name>/ui/src/lib/<name>.routes.ts` exporting `<name>Routes`; export it from the barrel
  3. Add the host route in `apps/jordylab/src/app/app.routes.ts`:
     `loadChildren: () => import('@jordylab-fe/<name>/ui').then((m) => m.<name>Routes)`
  4. Add a nav link to the host's `app.html`
  5. Optionally scaffold a dev harness app the same way `apps/fna` is set up (next free port)

This replaced a native-federation micro-frontend setup. Micro-frontends solve independent
team/deploy-cadence problems this project doesn't have, and the custom build pipeline they
required broke repeatedly against Angular's AOT output. Don't reintroduce them without a
concrete, demonstrated need for independently deployed bundles — the same monolith-first rule
the backend follows.

# Component Library

- spartan/ui with brain (headless logic) + helm (styled components)
- Helm overrides live in `libs/ui/helm/<component>-helm/` and are tagged `scope:shared, type:ui`. They're ignored by ESLint (vendored styling) and tsconfig-pathed as `@spartan-ng/ui-<component>-helm`.
- Add a new component: `bunx @spartan-ng/cli@latest add <component-name>`

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

# Auth via Keycloak

- The host shell (`apps/jordylab`) integrates with Keycloak via the official `keycloak-js` SDK. Other apps don't know about OAuth.
- Token plumbing: `apps/jordylab/src/app/auth/auth.service.ts` wraps the SDK. `apps/jordylab/src/app/auth/auth.interceptor.ts` adds the bearer header to every outgoing request.
- Route protection: `authGuard` in `apps/jordylab/src/app/auth/auth.guard.ts` redirects unauthenticated users to `/login`.
- Login page: `apps/jordylab/src/app/auth/login.component.ts`. Button calls `authService.login()` which kicks off the standard OIDC Authorization Code flow with PKCE.
- Logout: `authService.logout()` from the header button.
- Configuration: `apps/jordylab/src/environments/environment.ts` (dev) and `environment.prod.ts` (prod). The `project.json` swaps via `fileReplacements` on production builds.
- Realm: single `jordylab` realm. Client: `jordylab-host` (public, OIDC web). Roles: `jordylab-user` (default for any logged-in user), `gamecatalog-scanner` (required for the script's `/scan` access — the script uses a separate `gamecatalog-script` device-code client).
- `apps/gamecatalog` calls `/api/gamecatalog/ingest/script?libraryType=steam` (or `emudeck`) to download the scan script for the user. Both endpoints sit behind the host's auth interceptor.

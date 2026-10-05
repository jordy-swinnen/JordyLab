# Commands

```bash
bun install                        # Install dependencies
bunx nx serve jordylab             # Main app (port 4200) — lazy-loads fna + gamecatalog routes
bunx nx serve fna                  # fna dev harness, no host shell (port 4300)
bunx nx serve gamecatalog          # gamecatalog dev harness, no host shell (port 4400)
bunx nx test <lib>                 # Test specific lib (e.g. bunx nx test fna-ui)
bunx nx run-many -t test           # Run all tests
bunx nx run-many -t lint           # Lint everything
```

Use `bun` and `bunx` — not `npm`, `npx`, or `yarn`.

## Two linters, one owner per rule

ESLint runs through Nx (`bunx nx run-many -t lint`) and owns the Angular rules, the Angular template rules,
`@nx/enforce-module-boundaries`, the core rules and typescript-eslint. Oxlint (`bunx nx run-many -t oxlint`, config
`jordylab-fe/.oxlintrc.json`) is a fast extra pass that owns only the `oxc/*` and `unicorn/*` rules ESLint does not have, so
agents never get conflicting feedback. `tools/check-lint-ownership.sh` fails when a rule is enabled in both. CI runs Oxlint
first, then ESLint; either failing fails the build. Formatting stays with Prettier.

To drop Oxlint again, one commit removes: `oxlint` and `@nx/oxlint` from `package.json`, the `@nx/oxlint` plugin entry in
`nx.json`, `.oxlintrc.json`, `tools/check-lint-ownership.sh`, the "Oxlint" and "Lint rule ownership" steps in
`.github/workflows/build.yml`, the Oxlint block in `tools/lint-changed.sh` and the Oxlint stub and cases in
`.claude/hooks/tests/lint-cases.sh` (the hook then falls back to ESLint only). Rehearsed: ESLint over all 14 projects and the unit tests stay green.

## Lint feedback after every edit

After editing a TypeScript file under `jordylab-fe/`, run `jordylab-fe/tools/lint-changed.sh <file>` and fix what it prints
(empty output means clean). It runs ESLint on that one file in about a second: Angular rules, template rules and
module boundaries stay with ESLint. In Claude Code a post-edit hook runs the same command and hands the result back to the
agent automatically, so this is only an explicit step in OpenCode (an OpenCode plugin calling the same script is the
fallback if this instruction proves unreliable). `--strict` exits 1 when there are findings, for scripts.

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
- `httpResource()` carries no `@experimental` marker in Angular 22 (checked in `@angular/common`), but state stays in the hand-rolled signal stores — do not introduce it ad hoc; adopt it only through a deliberate change

# Nx Structure

All real code lives in per-domain libs; the apps are thin shells over them:

```
apps/jordylab          — the deployable app. Lazy-loads each domain's routes
apps/<domain>/         — dev harness per domain: boots that domain's routes alone,
                         without the host shell. Still authenticates via Keycloak
                         independently (see Auth via Keycloak below). Not deployed
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

## Mobile app (`apps/jordylab-mobile`, feature 007)

`apps/jordylab-mobile` is **not** a separate Angular app — it has no `src/`. Its `capacitor.config.ts`
points `webDir` at `dist/apps/jordylab/browser`, so Capacitor packages the exact same web build the
`mobile` configuration in `apps/jordylab/project.json` produces (`fileReplacements` swap in
`environments/environment.mobile.ts`). The Android native project lives under
`apps/jordylab-mobile/android/`. There is no separate mobile bootstrap: platform-conditional
behavior (native login, install prompts, update checks, biometric unlock, share target) is wired
into the *same* `apps/jordylab/src/app/app.ts` used on web, gated by `PlatformService.isNative()` —
see `specs/007-mobile-app/tasks.md` for the reasoning behind that choice.

`libs/shared/platform/{api,ui}` (tagged `scope:shared,type:api|ui` — **not** a new `scope:platform`
tag) holds everything that only breaks inside a WebView: platform detection, the absolute-API-URL
interceptor + `artworkUrl` pipe (native only prefixes `/api/**`), install-prompt/update-check
signal stores, the App-Link (`appUrlOpen`) and share-target listeners, and the web app manifest.
`libs/shared/auth` gained the native login flow (`AuthService`'s `Capacitor.isNativePlatform()`
branch) and `BiometricUnlockService`.

# Domain Routing

`apps/jordylab` is the single deployable app. Each domain's routes live in its `ui` lib and
are lazy-loaded by the host with a plain dynamic `import()` — Angular's own code splitting
puts each domain (and each component within it) in its own chunk, fetched on first visit.

- **Route ownership.** `libs/<domain>/ui/src/lib/<domain>.routes.ts` exports `<domain>Routes`,
  re-exported from the lib's barrel. The host mounts it under a path segment it owns
  (`/fna`, `/games`); domain routes are relative within that segment and must NOT repeat it.
- **Dev harnesses.** `apps/fna` and `apps/gamecatalog` exist only so `bunx nx serve <domain>`
  can exercise one domain without the host shell. Their `app.routes.ts` re-exports the lib's
  routes wrapped in the shared `authGuard`, so there is one source of truth for the routes and
  each harness still authenticates against the real Keycloak realm independently — see
  "Auth via Keycloak" below. They are not deployed.
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

Mobile is not a new domain in this sense — see "Mobile app" above; it reuses `apps/jordylab`'s own
routes and adds cross-cutting `scope:shared` libs, not a `scope:mobile` domain lib.

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

# End-to-end tests (Playwright)

Automated browser journeys for the web app, run against a freshly built app on a throwaway stack. Unit tests (Vitest) stay the
fast layer; this is the regression gate for whole journeys.

```bash
cd jordylab-fe
e2e/run.sh web                       # build fresh, start Postgres + Keycloak + backend, run the journeys, clean up, verify clean
E2E_SKIP_BACKEND_BUILD=1 E2E_SKIP_WEB_BUILD=1 e2e/run.sh web    # reuse the last builds while iterating
e2e/lib/cleanup.sh verify-all        # lists any E2E container, volume or network still around (should print "verified clean")
e2e/prove-cleanup.sh                 # proves cleanup after a pass, a failure, Ctrl-C, SIGTERM and a hard kill
```

Needs Podman (macOS) or Docker, Java 25, Bun and `bunx playwright install chromium` once. CI runs the same script as the required
`e2e-web` check.

- **Throwaway environment.** Every run starts its own Postgres and Keycloak (unique compose project, free ports, labels, no volumes,
  per-run credentials, a realm derived from the dev export) and a backend against them, and removes everything on pass, failure,
  interrupt or hard kill; a final check fails the run if anything with the run label is left. It never touches the dev stack or the
  dev database: the backend runs under profile `local` with every dev value overridden (see `e2e/run.sh`).
- **Data only through the app.** The catalog is filled through the scan endpoint with a scanner-role token, guests sign up through
  Keycloak's page. No SQL, no hand-seeded rows (same rule as everywhere).
- **Selectors** are roles, labels and `data-testid`; never class names. Keycloak's own pages are third-party DOM, so only there the
  documented element ids are used.
- **Journeys** (`apps/jordylab-e2e/src/*.spec.ts`): sign-in with session reuse and sign-out, library grid/search/detail, catalog chat
  up to the model call, FNA briefing (read-only), admin approving a sign-up. The throwaway backend has no AI keys, so nothing paid
  is ever called and journeys that touch AI assert the graceful "unavailable" state.
- **agent-browser / the browser pane versus these suites.** Use the browser pane for exploring, reproducing a bug, checking a
  deployed environment or anything that needs judgement. Use the suites to prove a journey still works: repeatable, run by CI, no
  agent needed. When a manual finding becomes a regression risk, turn it into a journey here.
- **Android layer (Appium 3 + WebdriverIO, `apps/jordylab-mobile-e2e`)** for what only breaks inside the installed app: native Keycloak login (Custom Tab, App Link), install prompt, update check, share target. Runs on a hosted emulator in the
  `E2E Android` workflow (on demand, after a Release, and when Android files change; not a required check); locally `e2e/run.sh android` needs the Android SDK and an emulator. Emulator and WebView pins: `apps/jordylab-mobile-e2e/PINS.md`.
  Biometric unlock stays a manual checklist (README of that project).

# Auth via Keycloak

- Keycloak integration (the `keycloak-js` SDK, token plumbing, route guard, login page) lives in
  the shared `libs/shared/auth` lib (`@jordylab-fe/shared/auth`), not in any single app. All three
  deployable/dev-harness apps (`jordylab`, `fna`, `gamecatalog`) depend on it and authenticate
  independently — there is no host-only session that other apps borrow from.
- Token plumbing: `AuthService` (`libs/shared/auth/src/lib/auth.service.ts`) wraps the SDK.
  `authInterceptor` (`auth.interceptor.ts`) adds the bearer header to every outgoing request.
- Route protection: `authGuard` (`auth.guard.ts`) redirects unauthenticated users to `/login`.
- Login page: `LoginComponent` (`login.component.ts`). Button calls `authService.login()` which
  kicks off the standard OIDC Authorization Code flow with PKCE.
- Logout: `authService.logout()` from the header button (host app only — the standalone harnesses
  don't render the host chrome).
- Configuration: each app supplies its own `AUTH_CONFIG` (an `InjectionToken` defined in
  `auth-config.ts`) from its own `src/environments/environment.ts` (dev) /
  `environment.prod.ts` (prod), provided in that app's `app.config.ts`. All three apps currently
  provide the same realm/client — only the runtime redirect origin differs, and that's read from
  `window.location.origin`, not from the environment file. Each app's `project.json` swaps via
  `fileReplacements` on production builds.
- Realm: single `jordylab` realm. Client: `jordylab-host` (public, OIDC web) — its `redirectUris`/
  `webOrigins` in `compose/keycloak-realm-export.json` already list the standalone harness ports
  (`:4300`, `:4400`) alongside the host's `:4200`. Roles: `jordylab-user` (default for any
  logged-in user), `gamecatalog-scanner` (required for the script's `/scan` access — the script
  uses a separate `gamecatalog-script` device-code client).
- `apps/gamecatalog` calls `/api/gamecatalog/ingest/client?libraryType=steam` (or `emudeck`) to download the scan client for the user. Both endpoints sit behind the host's auth interceptor.

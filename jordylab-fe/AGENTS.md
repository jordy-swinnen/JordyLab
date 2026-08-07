# Commands

```bash
bun install                        # Install dependencies
bunx nx serve jordylab             # Host shell (port 4200) — mounts fna + gamecatalog
bunx nx serve fna                  # Standalone fna remote (port 4300)
bunx nx serve gamecatalog          # Standalone gamecatalog remote (port 4400)
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

# Nx Structure

Three app tiers live alongside per-domain libs:

```
apps/<shell|domain>/   — deployable Angular apps. One shell (jordylab) + one app per domain remote
libs/<domain>/{ui,api} — domain libs: ui = components, api = services & HTTP
libs/ui/helm           — shared spartan helm overrides reused by multiple apps
apps/jordylab          — host shell, mounts remotes via native-federation
```

Tag new libs with the right scope + type: `--tags="scope:<domain>,type:<ui|api>"`. App-level tags: `--tags="scope:<domain|shared>,type:app"`. Boundary rules:

- `type:api` → may depend on `type:api`, `type:shared`
- `type:ui` → may depend on `type:api`, `type:ui`, `type:shared`
- `type:app` → may depend on `scope:fna | scope:gamecatalog | scope:shared`
- `scope:fna` → may depend on `scope:fna`, `scope:shared`
- `scope:gamecatalog` → may depend on `scope:gamecatalog`, `scope:shared`
- `scope:shared` → may depend on `scope:shared` only

# Microfrontends

- **Tech:** `@softarc/native-federation` 4.x (build) + `@softarc/native-federation-runtime` (runtime).
  - The runtime package is marked `@deprecated` upstream in favor of `@softarc/native-federation-orchestrator`; migrate when the orchestrator's Angular story stabilises.
- **Topology:** One host shell (`apps/jordylab`, port 4200) lazy-mounts N remote apps. Each remote app (`apps/fna`, `apps/gamecatalog`) is independently deployable and runnable.
- **Every app stands alone.** Each remote keeps its own bootstrap path so `bunx nx serve <app>` works without the host. Only the host imports `@softarc/native-federation-runtime`; remotes don't reference the runtime.
- **Exposed surface.** Each remote exposes exactly `./Routes` from its `federation.config.ts`. Routes are imported as `loadChildren` in the host. No shared stores, no cross-remote service imports, no shared mutable state.
- **Route prefix contract.** The host owns the path segment (`/fna`, `/games`). Remote routes are relative within that segment — e.g. remote exposes `{ path: 'grid' }` and the host mounts it at `/games/grid`. Remote route paths must NOT repeat the segment.
- **Base hrefs.** Each app pins its production base href in `project.json`:
  - `apps/jordylab` → `/`
  - `apps/fna` → `/fna`
  - `apps/gamecatalog` → `/games`
  - If Angular's `baseHref` builder option normalises a trailing slash in, treat it as framework behaviour, not design intent.
- **Dev ports:** host `:4200`, remotes start at `:4300` and increment.
- **Adding a new remote** (e.g. recipe, garmin, trading):
  1. `bunx nx g @nx/angular:application <name> --tags="scope:<domain>,type:app" --style=css`
  2. Add `federation.config.ts` exporting `withNativeFederation({ name, exposes: { './Routes': './apps/<name>/src/app/app.routes.ts' }, shared: shareAll({...}) })`
  3. Set the app's `serve.port` (next free, e.g. 4500) and `baseHref` (e.g. `/<name>`)
  4. Add the remote to `apps/jordylab/src/main.ts`'s `initFederation({...})` map pointing at `http://localhost:<port>/remoteEntry.json`
  5. Add the host route in `apps/jordylab/src/app/app.routes.ts` via `loadChildren: () => loadRemoteModule('<name>', './Routes').then(m => m.appRoutes)`
  6. Add a nav link to the host's `app.html`
- **Federation build pipeline** (TODO follow-up): the runtime wiring is in place. The build-time `federationBuilder` integration that produces `remoteEntry.json` per remote is not yet wired into `project.json` — today the host expects `remoteEntry.json` at the remote's dev port. Track this as a follow-up task before production deploy.

# Component Library

- spartan/ui with brain (headless logic) + helm (styled components)
- Helm overrides live in `libs/ui/helm/<component>-helm/` and are tagged `scope:shared, type:ui`. They're ignored by ESLint (vendored styling) and tsconfig-pathed as `@spartan-ng/ui-<component>-helm`.
- Add a new component: `bunx @spartan-ng/cli@latest add <component-name>`

# Testing

- Use Vitest + `@ngneat/spectator/vitest` — import from the Vitest entry point, not Jest
- In each lib's `vite.config.mts`, inline Spectator: `test.server.deps.inline: ['@ngneat/spectator']`
- Descriptive test names explaining the scenario
- Follow existing test patterns in the codebase

# Auth via Keycloak

- The host shell (`apps/jordylab`) integrates with Keycloak via the official `keycloak-js` SDK. Other apps don't know about OAuth.
- Token plumbing: `apps/jordylab/src/app/auth/auth.service.ts` wraps the SDK. `apps/jordylab/src/app/auth/auth.interceptor.ts` adds the bearer header to every outgoing request.
- Route protection: `authGuard` in `apps/jordylab/src/app/auth/auth.guard.ts` redirects unauthenticated users to `/login`.
- Login page: `apps/jordylab/src/app/auth/login.component.ts`. Button calls `authService.login()` which kicks off the standard OIDC Authorization Code flow with PKCE.
- Logout: `authService.logout()` from the header button.
- Configuration: `apps/jordylab/src/environments/environment.ts` (dev) and `environment.prod.ts` (prod). The `project.json` swaps via `fileReplacements` on production builds.
- Realm: single `jordylab` realm. Client: `jordylab-host` (public, OIDC web). Roles: `jordylab-user` (default for any logged-in user), `gamecatalog-scanner` (required for the script's `/scan` access — the script uses a separate `gamecatalog-script` device-code client).
- `apps/gamecatalog` calls `/api/gamecatalog/ingest/script?libraryType=steam` (or `emudeck`) to download the scan script for the user. Both endpoints sit behind the host's auth interceptor.

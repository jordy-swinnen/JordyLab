---
paths:
  - "**/*.ts"
  - "**/*.html"
---

# TypeScript + Angular (JordyLab frontend)

Applies to `jordylab-fe/` and any new Angular workspace in this repo.

## Tooling

- **Use `bun` and `bunx`** — not `npm`, `npx`, or `yarn`
- Nx 22 as the task runner; never call the Angular CLI directly
- Bun lockfile is the source of truth (not `package-lock.json` / `yarn.lock`)

## Angular style

- **Use `inject(Service)` over constructor injection**
- **Use JavaScript `#field` over TypeScript `private`** (real private with runtime semantics, plus tree-shake friendly)
- **Use Angular signals for state — no NgRx store**
- Zoneless mode (no `zone.js`)
- Standalone components (no `NgModule`)
- Apply Container–Presentation pattern for smart/dumb component separation
- Barrel imports via `index.ts` for clean paths
- Full descriptive names — no abbreviations or single-letter identifiers
- Early returns over deep nesting
- Self-explanatory code over comments

## Nx structure

- Apps in `apps/`, domain libs in `libs/<domain>/{ui,api}`
- Two-layer lib structure per domain: `ui` (components) + `api` (services, HTTP)
- Module boundaries via Nx ESLint tags (`scope:*`, `type:*`)
- Tag new libs: `--tags="scope:<domain>,type:<ui|api>"`
- Dependency rules: `type:ui` → can depend on `type:api` and `type:ui`; `type:api` → can only depend on `type:api`

## spartan/ui

- Use spartan/ui with brain (headless logic) + helm (styled components)
- Add components: `bunx @spartan-ng/cli@latest add <component-name>`
- Use the helm variant for styled output, brain for unstyled primitives

## Testing

- Vitest + `@ngneat/spectator/vitest` — **import from the Vitest entry point, not Jest**
- In each lib's `vite.config.mts`, inline Spectator: `test.server.deps.inline: ['@ngneat/spectator']`
- Descriptive test names explaining the scenario
- Follow existing test patterns in the codebase
- 80% line coverage gate per lib

## HTTP services

- Use `HttpClient` via injected `provideHttpClient()` in the app config
- Model records (TypeScript `interface` or `type`) shared via the `api` lib, imported by `ui`
- Api lib should expose typed methods, not raw `Observable<T>` to consumers

## Scripts (in `package.json`)

- `bun run serve` → `bunx nx serve fna`
- `bun run build` → `bunx nx build fna`
- `bun run test` → `bunx nx run-many -t test`
- `bun run lint` → `bunx nx run-many -t lint`

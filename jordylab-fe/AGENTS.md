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

# Nx Structure

- Apps in `apps/`, domain libs in `libs/<domain>/{ui,api}`
- Two-layer lib structure per domain: `ui` (components) + `api` (services, HTTP)
- Enforce module boundaries via Nx ESLint tags (`scope:*`, `type:*`)
- Tag new libs: `--tags="scope:<domain>,type:<ui|api>"`
- `type:ui` → can depend on `type:api` and `type:ui`
- `type:api` → can only depend on `type:api`

# Component Library

- spartan/ui with brain (headless logic) + helm (styled components)
- Add components: `bunx @spartan-ng/cli@latest add <component-name>`

# Testing

- Use Vitest + `@ngneat/spectator/vitest` — import from the Vitest entry point, not Jest
- In each lib's `vite.config.mts`, inline Spectator: `test.server.deps.inline: ['@ngneat/spectator']`
- Descriptive test names explaining the scenario
- Follow existing test patterns in the codebase

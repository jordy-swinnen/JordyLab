# Oxlint as a Fast Lint Pass (with the Nx Upgrade It Needs): Research

Date: 2026-10-05. Checked against `jordylab-fe` on `main` and live vendor docs. Sources are listed at the end.

## Verdict: add Oxlint as an extra fast pass next to ESLint, and upgrade Nx first

Oxlint cannot replace ESLint here, because it cannot lint Angular templates. It can still give agents (and CI) a very
quick first check on TypeScript, which is where agentic development gains most: the lint error arrives right after the
edit, not minutes later in CI. The Nx plugin that wires Oxlint into the workspace may need a newer Nx than the one the
repo runs, so the Nx upgrade is part of this work, done first and on its own.

Not part of this work: Vite+ (Angular is not a listed target, and it would compete with the Angular builder and Nx),
Oxfmt (Prettier stays; Angular support is partial and formatting speed is not a problem at this size).

## What the repo has today

- Nx `22.7.12`, Angular `~21.2.25`, `@angular/build` builders, TypeScript `~5.9.2`, Vite `^7`, Vitest `^4.0.8`, Bun.
- ESLint `^9.8` with flat config: `@nx/eslint-plugin` presets at the root; every lib extends `flat/angular` and
  `flat/angular-template`. `@nx/enforce-module-boundaries` with tag constraints lives in the root config.
- Lint runs through the `@nx/eslint:lint` executor and is cached by Nx.
- The post-edit hook only runs Prettier (through `npx`, which also goes against the bun-only rule), so lint errors
  appear late.
- **Not measured yet:** how long a full and a single-file lint take today. The measurement is the first step of the plan.

## Oxlint: what it can and cannot do

| Topic | Finding |
|---|---|
| Rules | 650+ rules built in; type-aware linting is stable through `oxlint-tsgolint` |
| ESLint plugins | JS plugin support is **alpha**; most existing plugins work, and the plugin API is outside Oxlint's semver promise |
| Angular templates | **Not supported** (no template linting). Angular `.ts` files are fine |
| Auto-fix | More limited than ESLint's |
| Speed | Benchmarks quote several times faster than ESLint (4.8x on one project, "up to 100x" in reports); not measured here |
| Nx integration | `@nx/oxlint`, **experimental**, needs Oxlint 1.70.0 or later, runs alongside ESLint, claims the first free task name (`lint` is taken, so it would use `oxlint`) |
| Module boundaries | Possible through the JS plugin API with the same `depConstraints`, but experimental and breakable; keep ESLint as the authority |

## The Nx upgrade (22.7.12 to current 23.x)

- **Nx 23**: Node 22 or newer, TypeScript 5.4 or newer, Angular 19 or newer. The Vitest executor moved from `@nx/vite`
  to `@nx/vitest` (the repo already uses `@nx/vitest:test`). Some generators were removed (`@nx/angular:ngrx`,
  `@nx/angular:move`), which the repo does not use. Migrations can now be prompt-based, applied by an agent.
- **Nx 23.1**: drops Angular 19, drops ESLint 8 (repo is on 9, fine), adds Angular 22 and a TypeScript 6 migration.
- **Nx 23.2**: adds `@nx/oxlint` and Oxfmt support.
- Upgrade path from the docs: `nx migrate latest`, review, then `nx migrate --run-migrations`.

**Things to verify before touching anything** (the sources do not say):

- Nx 23.x still supports Angular 21.2 (only the minimum, 19, and the Angular 22 addition are stated).
- The TypeScript 6 migration does not break Angular 21.2's compiler; decline that migration if unsupported.
- `@analogjs/*` 2.1.x, `@spartan-ng/nx` (alpha) and `@nx/vitest` still work with the Vite version Nx 23 expects.
- Node 22 or newer everywhere: local machine, GitHub Actions, the container images that build the frontend.
- `nx add @nx/oxlint` works with Bun as the package manager.
- Whether the Capacitor build and the `mobile` configuration of the app survive the migration.

## Open questions

- Should the agent hook block on Oxlint errors, or only report them?
- Does OpenCode get the same feedback through a plugin, or through an instruction in `AGENTS.md`? (Claude Code hooks and
  OpenCode plugins do not share a format, so they are authored separately.)
- Is a type-aware pass worth running in the hook, or only in CI?

## Sources

- Oxlint JS plugins alpha: https://oxc.rs/blog/2026-03-11-oxlint-js-plugins-alpha.html
- Oxlint and Oxfmt compatibility: https://oxc.rs/compatibility.html
- Nx with Oxlint: https://nx.dev/docs/technologies/oxlint/introduction
- Nx 23: https://nx.dev/blog/nx-23-release, Nx 23.1: https://nx.dev/blog/nx-23-1-release, Nx 23.2: https://nx.dev/blog/nx-23-2-release
- Angular quality stack on Vite+ (hybrid, ESLint kept for templates): https://dev.to/nikhilrajnair/modernizing-the-angular-quality-stack-moving-to-vite-2d23

# Plan Draft: How We'll Build It (Nx Upgrade, then Oxlint)

Two separately reviewable pieces, in this order. The first must be merged and green before the second starts.

## Piece one: baseline and Nx upgrade (own branch, own pull request)

- **Measure first.** Time a full `nx run-many -t lint` and a single-file ESLint run on the developer machine and in CI,
  cold and cached. Record the numbers in the spec's research file. They decide the hook's speed target.
- **Check the prerequisites** listed in `research.md` (Angular 21.2 on Nx 23, TypeScript 6 migration, Analog, spartan,
  Node 22 everywhere). Stop and report if one fails.
- **Upgrade in the documented way:** `nx migrate latest`, review the generated changes, `nx migrate --run-migrations`.
  Review every migration an agent applies; decline the TypeScript 6 migration if Angular 21.2 does not support it.
- **Prove nothing changed:** lint, unit tests with the coverage gate, production build, the `mobile` configuration
  build, `nx graph` before and after, and one deliberate module-boundary violation that must still fail lint.
- **Cleanup that belongs here:** the post-edit hook calls `npx prettier`; the repo rule is `bunx`. Fix while the
  tooling is open (separate commit).

## Piece two: Oxlint alongside ESLint

- **Install** Oxlint 1.70.0 or later with Bun, then `nx add @nx/oxlint`. Because a `lint` target exists, the plugin
  should pick the task name `oxlint`; confirm it does not touch the `lint` target.
- **Config:** one root `.oxlintrc.json`. Rules ESLint already owns are switched off in Oxlint (or the other way round),
  so each rule has exactly one owner. ESLint keeps Angular rules, template rules and `@nx/enforce-module-boundaries`.
  Do not register the boundary rule in Oxlint (its JS plugin API is alpha).
- **Shared command:** one script that runs Oxlint on a list of files and prints diagnostics in a readable form.
- **Claude Code:** a PostToolUse hook for TypeScript edits calls the script; diagnostics go back to the agent.
- **OpenCode:** hooks are a different mechanism (plugins). Start with a rule in the frontend `AGENTS.md` telling agents
  to run the shared script after editing TypeScript; add a plugin only if that proves unreliable. Do not try to
  translate the Claude Code hook (see the dual-agent-config notes on why hooks stay separate).
- **CI:** an Oxlint step before the ESLint step in the frontend job, so a plain error fails fast.
- **Docs:** commands in the frontend `AGENTS.md`, the division of labor in one short paragraph, a pointer from the root
  `AGENTS.md`. Commit trailers follow the usual rules.
- **Verify:** introduce one TypeScript error, one template-rule violation and one boundary violation; confirm the hook
  catches the first, ESLint in CI catches the other two, and removing Oxlint leaves everything green.

## Risks

- The Nx 23 upgrade drags in an Angular upgrade: then stop, report, and decide separately.
- The Nx Oxlint plugin is experimental and may change: pin the version, keep the hook script independent of the plugin.
- Two linters double-report: the rule ownership list in the Oxlint config is the single place that settles it.

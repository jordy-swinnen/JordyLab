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
- **Shared command:** `jordylab-fe/tools/oxlint-changed.sh <files...>` runs Oxlint (through `bunx`) on the given files
  and prints diagnostics in a readable form. It knows nothing about any agent tool, so both tools can call it.

## The agent hook

- **Claude Code hook:** a new `.claude/hooks/post-oxlint-check.sh`, registered in `.claude/settings.json` as a
  PostToolUse hook with the same `Write|Edit|MultiEdit` matcher as the existing post-edit hooks. It follows their
  pattern: read the tool input from stdin with `jq`, take `file_path`, exit quietly when there is nothing to do.
- **What it checks:** only TypeScript files under `jordylab-fe/` (specs included), skipping what ESLint also ignores
  (`dist`, `out-tsc`, `libs/ui/helm`, `node_modules`). It calls the shared command on that one file.
- **What the agent sees:** the diagnostics, in a form Claude Code feeds back to the agent after the edit. Verify the
  exact exit-code and output behavior against the current Claude Code hook docs while planning. Advisory first, like
  the test-convention hook; whether it should ever block is a clarify question.
- **Stays out of the way:** no Oxlint installed, no match, or a parse failure means a silent skip with exit code zero.
  A hard time limit keeps a slow run from stalling the agent.
- **Ordering with the formatter:** hooks on the same event may run side by side, so check that linting cannot read a
  file while Prettier is rewriting it; chain them in one script if it can.
- **Tested like the other hooks:** a fixture script `.claude/hooks/tests/oxlint-cases.sh` (an error file, a clean file,
  an ignored path, a non-TypeScript file, a missing binary), run by the existing Hook Tests workflow, which already
  triggers on changes under `.claude/hooks`.
- **Fallback:** if the baseline measurement shows a single-file ESLint run is nearly as fast, the same hook calls
  ESLint instead and the rest of the feature (Nx upgrade, CI order, docs) is unaffected.
- **OpenCode:** hooks are a different mechanism (plugins) and are not translated from the Claude Code one. Start with a
  rule in `jordylab-fe/AGENTS.md` telling agents to run the shared command on each TypeScript file they edit; add a
  plugin that calls the same command only if the instruction proves unreliable.

- **CI:** an Oxlint step before the ESLint step in the frontend job, so a plain error fails fast.
- **Docs:** commands in the frontend `AGENTS.md`, the division of labor in one short paragraph, a pointer from the root
  `AGENTS.md`. Commit trailers follow the usual rules.
- **Verify:** introduce one TypeScript error, one template-rule violation and one boundary violation; confirm the hook
  catches the first, ESLint in CI catches the other two, and removing Oxlint leaves everything green.

## Risks

- The Nx 23 upgrade drags in an Angular upgrade: then stop, report, and decide separately.
- The Nx Oxlint plugin is experimental and may change: pin the version, keep the hook script independent of the plugin.
- Two linters double-report: the rule ownership list in the Oxlint config is the single place that settles it.

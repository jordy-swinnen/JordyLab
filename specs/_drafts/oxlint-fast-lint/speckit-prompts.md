# Oxlint Fast Lint Pass (with the Nx Upgrade): SpecKit Prompts

Background is in `research.md`, the expected spec is in `spec-draft.md`, the build order is in `plan-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.
> **Order:** the Nx upgrade comes first inside this feature. It has no dependency on other drafts.

---

## 1. `/speckit-specify`

```
Short name: oxlint-fast-lint.

Give coding agents (and CI) fast lint feedback on the Angular frontend by adding Oxlint next to ESLint, and upgrade the workspace from Nx 22 to the current Nx 23 first, because the Nx Oxlint plugin may need it.

WHY: lint errors in TypeScript show up late. The edit hook only runs Prettier; ESLint runs through Nx on demand or in CI. I develop mostly with agents (Claude Code and OpenCode), so the useful thing is that an agent sees a lint error right after it edits a file and fixes it in the same turn.

UPGRADE: move jordylab-fe from Nx 22.7.12 to the current Nx 23 release in its own reviewable change, with no behaviour change: lint, unit tests with the coverage gate, the production build, the mobile build and the dependency graph give the same results, and a deliberate module-boundary violation still fails lint. Verify first that Nx 23 supports Angular 21.2, that the TypeScript 6 migration is compatible (decline it if not), and that Analog, spartan and Node 22 (local and CI) are fine. Report instead of silently upgrading Angular.

OXLINT: Oxlint runs next to ESLint, never instead of it. ESLint keeps Angular template rules, Angular rules and @nx/enforce-module-boundaries (Oxlint cannot lint templates, and its JS plugin API is alpha). Each rule has exactly one owner so agents never get conflicting feedback. A TypeScript file an agent just edited is checked within about a second and the result reaches the agent: through a post-edit hook in Claude Code (a new script registered next to the existing post-edit hooks, advisory, silent when there is nothing to check, with a time limit and fixture tests in the existing hook-test workflow), through an instruction (and a plugin only if needed) in OpenCode, both calling one shared command. If measurement shows a single-file ESLint run is nearly as fast, the same hook calls ESLint instead. CI runs Oxlint before ESLint and still fails when either reports an error. Formatting stays with Prettier.

REMOVABLE: dropping Oxlint again is one commit and leaves lint, tests and builds working.

Out of scope: Vite+, Oxfmt, upgrading Angular itself, Oxlint on the Java or Python code, custom Oxlint rules.
```

---

## 2. `/speckit-clarify`: expected questions and suggested answers

1. Should the agent hook block on errors or only report them? → report and let the agent decide, block only in CI.
2. Type-aware pass in the hook? → CI only for now; the hook stays untyped for speed.
3. Which Nx 23 release? → latest at the time of work, unless the Oxlint plugin needs a specific one.
4. What speed target for the single-file check? → under one second, confirmed against the measured ESLint baseline.

---

## 3. `/speckit-plan`

```
Tech context (read AGENTS.md, jordylab-fe/AGENTS.md, the constitution, and specs/_drafts/oxlint-fast-lint/research.md + plan-draft.md first; carry their findings into this feature's research.md; verify every version, plugin name and config field against live docs before committing, and report instead of guessing):

- Frontend: Nx 22.7.12 to Nx 23.x, Angular ~21.2, ESLint 9 flat config, @nx/eslint:lint, root eslint.config.mjs with the boundary constraints, Bun (use bun/bunx, never npm/npx).
- Oxlint 1.70.0 or later with @nx/oxlint (experimental), root .oxlintrc.json, task name oxlint (the lint target stays ESLint).
- Agents: Claude Code PostToolUse hook (.claude/hooks/post-oxlint-check.sh, same Write|Edit|MultiEdit matcher and stdin/jq pattern as the other post-edit hooks, fixtures in .claude/hooks/tests run by hook-tests.yml; verify the hook output/exit-code behaviour against current docs; check for races with the Prettier hook); OpenCode through an AGENTS.md instruction first. One shared script. Follow /dual-agent-config: hooks are authored separately for each tool, never translated.
- CI: build.yml frontend job, Oxlint step before ESLint.
- Do the upgrade as its own pull request first; measure the ESLint baseline before changing anything.
```

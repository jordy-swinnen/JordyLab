# Quickstart: validating feature 012

Run from the repo root. Each part is validated on its own. Commands assume Bun (`bunx`), never `npx`. Replace
`<file>` with a real path. Nothing here prints secrets.

## Part A: Nx upgrade, Oxlint, hook

**Baseline (before any change)**

```bash
cd jordylab-fe && time bunx nx run-many -t lint            # cold, then again for cached
time bunx eslint libs/shared/auth/src/lib/pkce.ts          # single-file ESLint
bunx nx graph --file=/tmp/graph-before.json                # for the before/after comparison
```

**After the upgrade (own PR)** — results must match the baseline (FR-009):

```bash
cd jordylab-fe
bunx nx run-many -t lint
bunx nx run-many -t test --all --coverage
bunx nx build jordylab && bunx nx build jordylab --configuration=mobile
bunx nx graph --file=/tmp/graph-after.json && diff /tmp/graph-before.json /tmp/graph-after.json
```

Then add a deliberate import that violates a boundary tag in a scratch file; `bunx nx lint <project>` must fail.

**Oxlint and hook**

```bash
jordylab-fe/tools/lint-changed.sh <file-with-a-known-error>   # prints diagnostics
bash .claude/hooks/tests/lint-cases.sh                        # fixture cases
jordylab-fe/tools/check-lint-ownership.sh                     # no rule owned twice
```

Expected: diagnostics in under about one second; fixtures all pass; ownership check exits 0. Edit a TypeScript file
in Claude Code with a deliberate error and confirm the agent receives the message in the same turn. In OpenCode,
confirm the instruction makes it run the script. Removal check: on a scratch branch remove Oxlint in one commit and
re-run the three commands above.

## Part B: AI conventions

```bash
test -f docs/research/spring-ai-architecture.md && grep -c "12 June 2026" docs/research/spring-ai-architecture.md
cat jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/CLAUDE.md          # exactly: @AGENTS.md
diff <(sed -n '/BEGIN AI CHECKLIST/,/END AI CHECKLIST/p' .claude/agents/code-reviewer.md) \
     <(sed -n '/BEGIN AI CHECKLIST/,/END AI CHECKLIST/p' .opencode/agents/code-reviewer.md)   # empty
```

Then, interactively: start Claude Code in the package and run `/context` (rules listed); start a fresh OpenCode
session in the package and confirm the rules load. Any check not run is reported as not run.

## Part C: E2E

```bash
jordylab-fe/e2e/run.sh web           # whole web suite on a throwaway stack
podman ps -a --filter label=dev.jordylab.e2e.run ; podman volume ls --filter label=dev.jordylab.e2e.run   # both empty
```

Cleanup proof, three runs, each followed by the two listing commands above (must be empty):
a passing run; a run with one test deliberately failing; a run interrupted with Ctrl-C during the tests. The dev
stack (`jordylab-be/compose.yaml`) stays up and untouched throughout; its row counts are unchanged.

Android (CI, or locally once an Android SDK and emulator exist):

```bash
jordylab-fe/e2e/run.sh android       # preflight WebView check, then the four behaviours
```

Break a covered journey on purpose: the web suite fails locally and the PR check blocks the merge.

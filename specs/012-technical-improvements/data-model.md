# Data Model: Technical Improvements (012)

No application database tables change. These are the working records the tooling reads and writes. All live in files
or in a run's own throwaway services.

## Lint rule ownership list (Part A)

Location: `jordylab-fe/.oxlintrc.json` (Oxlint side) and `jordylab-fe/eslint.config.mjs` (ESLint side).

| Field | Meaning |
|-------|---------|
| rule id | e.g. `no-unused-vars`, `@typescript-eslint/no-unused-vars` |
| owner | `eslint` or `oxlint` (exactly one) |
| state in the other linter | must be `off` / not registered |

Validation: the ownership check (contract `lint-ownership.md`) fails if a rule is enabled in both. ESLint always owns
`@angular-eslint/*`, template rules and `@nx/enforce-module-boundaries`.

## Hook input / output (Part A)

- Input (stdin, JSON): `tool_input.file_path` (or `filePath`) of the edited file.
- Decision: act only if the path is under `jordylab-fe/`, ends in `.ts`, and matches no ESLint ignore
  (`**/dist`, `**/out-tsc`, `libs/ui/helm/**`, `node_modules`).
- Output (stdout, JSON, exit 0): `additionalContext` with the diagnostics, or nothing.

## Gap entry (Part B)

Location: a table in `docs/research/spring-ai-gap-analysis.md` (new, linked from the reference doc).

| Field | Meaning |
|-------|---------|
| topic | research checklist item (retry, fallback, circuit breaking, token budget, typed output, prompts as resources, no model call inside a transaction, tool authorization, human approval for write actions, observability content logging, evals, ...) |
| report says | one line, with the report section |
| repo does | one line, citing the file/class checked |
| verdict | `worth building` / `defer` / `not applicable` |
| reason | one line |
| draft or trigger | `specs/_drafts/<name>/` for worth building; the triggering module for defer |

Rule: worth-building gaps relevant to today's code get a draft; gaps that only matter for modules that do not exist yet
are `defer` with the module named (clarification Q4).

## E2E run (Part C)

| Field | Meaning |
|-------|---------|
| runId | short unique id, e.g. timestamp + random suffix |
| projectName | `jordylab-e2e-<runId>` (compose project, container/volume/network name prefix) |
| label | `dev.jordylab.e2e.run=<runId>` on every container, volume and network |
| ports | host ports chosen at start for Postgres, Keycloak, backend, web server; never fixed |
| realm | throwaway realm import with one admin and one guest test user |
| credentials | generated per run into an untracked env file; never printed |
| state | `starting` → `ready` → `testing` → `cleaning` → `verified-clean` or `leftover-detected` |

Lifecycle rule: every path (pass, fail, interrupt, CI failure) reaches `cleaning`; `verified-clean` is required for a
green run. A run ending in `leftover-detected` fails.

## Web journey (Part C)

Fixed by clarification: sign-in with session reuse; game catalog grid and detail; admin Settings including approving a
user; FNA briefing view (read-only); catalog chat up to the model call. Each journey: id, covered spec user story,
required role, data created through the app (API/UI only), selectors (role/name or test id).

## Version pin record (Part C)

`apps/jordylab-mobile-e2e/PINS.md`: emulator image + API level, its WebView version, Appium version, UiAutomator2
driver version, chromedriver version, WebdriverIO version, and the date checked. Read by the preflight check.

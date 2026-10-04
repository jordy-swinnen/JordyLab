# Feature Specification: Fast Lint Feedback with Oxlint (and the Nx Upgrade)

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `jordylab-fe` tooling, agent configuration

---

## Overview

Lint errors in TypeScript currently show up late: the edit hook only formats, and ESLint runs through Nx on demand or in
CI. This feature adds Oxlint as a very fast extra check that agents get right after they edit a file, and that CI runs
first. ESLint stays in place for Angular templates, the Angular rules and module boundaries. Because the Nx plugin for
Oxlint may need a newer Nx, the workspace is first upgraded from Nx 22 to the current Nx 23 as a separate, behavior-neutral step.

---

## User Scenarios & Testing

### User Story: Upgrade Nx without changing behavior (Priority: High)

As the developer, I want the workspace on current Nx so that I can use the Oxlint integration, with nothing else changing.

**Independent Test**: after the upgrade, lint, unit tests, the production build and the mobile build all pass as before,
the dependency graph is the same, and a deliberate module-boundary violation still fails lint.

### User Story: Agents see lint errors right after an edit (Priority: High)

As the developer working with coding agents, I want a TypeScript file an agent just edited to be checked within a second
so that the agent fixes errors in the same turn.

**Independent Test**: edit a file to introduce a known lint error; the agent receives the diagnostic immediately after
the edit, in Claude Code and in OpenCode.

### User Story: CI runs Oxlint first, ESLint still gates (Priority: Medium)

As the developer, I want CI to fail fast on Oxlint errors while ESLint still checks everything Oxlint cannot, so that
nothing slips through the gap between the two tools.

**Independent Test**: an error only ESLint can see (an Angular template rule, a boundary violation) still fails CI; an
error both can see fails on the Oxlint step first.

### User Story: No conflicting rules between the two linters (Priority: Medium)

As the developer, I want one clear owner for each rule so that agents never get contradictory feedback.

**Independent Test**: running both linters on the whole frontend reports no rule twice with different severity.

### User Story: Easy to back out (Priority: Low)

As the developer, I want to remove Oxlint again without touching ESLint if it turns out not to be worth it.

**Independent Test**: removing the Oxlint dependency, config and hook leaves lint, tests and builds working.

### Edge Cases

- A file Oxlint cannot parse (Angular template inside a decorator, unusual syntax): the hook reports it as a skip, not an error.
- The upgrade's TypeScript 6 migration is not supported by the installed Angular: it is declined and recorded.
- Oxlint finds a rule violation ESLint already reports: the rule is turned off in one of the two.

---

## Requirements

- The workspace MUST run on the current Nx 23 release with no change in what lint, test, build and the dependency graph report.
- The upgrade MUST be reviewable on its own, before any Oxlint change lands.
- Oxlint MUST run next to ESLint, never instead of it. ESLint remains the owner of Angular template rules, Angular
  rules and module-boundary enforcement.
- A TypeScript file edited by an agent MUST be checked by Oxlint right after the edit and the result MUST reach the agent.
- Both Claude Code and OpenCode MUST get this feedback, each through its own mechanism, with one shared command.
- CI MUST run Oxlint before ESLint and MUST still fail when either one reports an error.
- No rule MAY be owned by both linters at once.
- Formatting stays with Prettier. Vite+ and Oxfmt are out of scope.
- The new commands and the division of labor MUST be documented where both agent tools read them.

## Success Criteria

- The single-file Oxlint check finishes in under one second on the developer machine (draft value; confirm against the
  measured ESLint baseline during clarify).
- Zero regressions after the upgrade: the same lint, test and build results as before on the same commit.
- A deliberate boundary violation and a deliberate template-rule violation still fail CI after the change.
- Removing Oxlint takes one commit and leaves everything else green.

## Out of Scope

Vite+ adoption, Oxfmt, upgrading Angular itself (unless Nx 23 turns out to require it, which is then reported, not done
silently), Oxlint on the Python or Java code, custom Oxlint rules.

## Assumptions

- Nx 23 supports Angular 21.2 (to verify, see `research.md`).
- Node 22 or newer is available locally and in CI (to verify).
- The Oxlint Nx plugin works with Bun (to verify).

## Clarifications Needed

- Should the agent hook block on errors, or only report them?
- Is a type-aware pass part of the hook, or CI only?
- Which exact Nx 23 release to land on (latest at the time of work, or the first 23 release that has the Oxlint plugin)?

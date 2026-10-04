# Feature Specification: AI Integration Conventions

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md` and `research-report.md`)
**Module**: `shared` (AI layer), agent configuration, docs

---

## Overview

Every AI feature goes through `ResilientAiService`, and more features are coming. This feature turns an external
research report on production AI integration into short, repo-specific conventions that both Claude Code and OpenCode
follow, files the full report as an on-demand reference, and writes down which recommendations the repo does not follow
yet. It changes no product behaviour.

---

## User Scenarios & Testing

### User Story: Agents follow the same AI rules every time (Priority: High)

As the developer, I want agents working on AI code to load short, repo-specific rules automatically, in both agent tools.

**Independent Test**: start a Claude Code session and an OpenCode session in the shared AI package; both show the rules.

### User Story: The full research is available when needed (Priority: Medium)

As the developer, I want the corrected report in the repo as a reference that agents read on demand, not every session.

**Independent Test**: the root `AGENTS.md` lists it in Reference Docs, and it is not part of any always-loaded file.

### User Story: I know which recommendations we don't follow yet (Priority: High)

As the developer, I want a written gap analysis so that I can decide what to build.

**Independent Test**: every topic in the report's checklist is marked worth building, defer or not applicable, with a
reason; each worth-building gap has its own draft.

### User Story: New AI endpoints and reviews apply the checklist (Priority: Medium)

As the developer, I want `/ai-endpoint` to scaffold with the conventions and `code-reviewer` to check them, in both tools.

**Independent Test**: scaffolding a sample AI feature produces typed output and a prompt resource; a review of code that
breaks a convention reports it.

---

## Requirements

- The full report MUST be saved under `docs/research/` with the corrections listed in `research.md`.
- The root `AGENTS.md` MUST list it in Reference Docs; it MUST NOT be imported into any always-loaded file.
- A short rules file MUST live next to the shared AI code as `AGENTS.md`, with a one-line `CLAUDE.md` importing it.
- The backend `AGENTS.md` MUST point to the rules file and to the reference doc.
- The rules MUST only contain findings the report rates high-confidence and that fit this repo.
- The gap analysis MUST compare the report with `ResilientAiService` and the AI code, and mark each topic.
- Worth-building gaps MUST become drafts under `specs/_drafts`, not code.
- `/ai-endpoint` and both copies of `code-reviewer` MUST carry the checklist.
- Loading MUST be verified in both tools, and any check that could not be run MUST be reported.

## Success Criteria

- Both agent tools load the rules in the shared AI package.
- The gap list covers every topic of the report's checklist.
- No always-loaded instruction file grows by more than a pointer line.

## Out of Scope

Building any gap, changing models or routing, adding AI features.

## Clarifications Needed

- Evals in CI: every merge or nightly?
- Gaps that only matter for future modules (RAG, chat memory, tools): defer or draft now?

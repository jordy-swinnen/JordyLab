# Feature Specification: Golden Examples for AI Output

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `gamecatalog` (enrichment, chat), `shared` (AI layer)
**Origin**: gap analysis `docs/research/spring-ai-gap-analysis.md`

---

## Overview

Only the plumbing is tested, with a mocked model. Nothing checks that the real prompts still produce valid, sensible output after a model or prompt change, which the research ranks as the first thing to build.

---

## User Scenarios & Testing

### User Story: Prompts and parsers are checked against fixed examples (High)

As the owner, I want a small set of example inputs with expected assertions for enrichment and chat translation, so that a prompt or model change that breaks the output is caught.

**Independent Test**: Run the golden set against recorded responses (no model): all pass; alter a prompt so output breaks: it fails.

### User Story: A real-model run exists and is cheap (Medium)

As the owner, I want the same examples runnable against the real models on a schedule or on demand, so that model drift is noticed without paying on every merge.

**Independent Test**: The scheduled/on-demand run reports pass/fail per example and its cost.

### User Story: Failures are readable (Low)

As the owner, I want a failing example to show input, expected assertion and actual output.

**Independent Test**: A failing example prints all three.

---

## Requirements

- Golden examples MUST assert on structured fields (parsed record, filter) and bounds, not on free-text wording.
- The default run MUST need no network and no API key.
- Real-model runs MUST NOT be part of the merge gate and MUST be opt-in or scheduled.
- A separate cheap judge model MAY be added later for free-text answers; it is out of scope here.

## Out of Scope

Changing models or routing, new AI features, anything marked defer in the gap analysis.

## Clarifications Needed

- Real-model tier: nightly, weekly or manual only? (cost versus drift detection)
- Fixture format: JSON files under `src/test/resources`, or Java builders like the existing TestBuilders?

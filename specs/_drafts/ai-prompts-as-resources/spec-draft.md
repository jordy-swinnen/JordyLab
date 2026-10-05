# Feature Specification: Move Remaining AI Prompts to Resources

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `gamecatalog`
**Origin**: gap analysis `docs/research/spring-ai-gap-analysis.md`

---

## Overview

Only the FNA briefing prompt is a classpath resource; the enrichment and the two chat prompts are Java text blocks, so prompts are edited in code and cannot be reviewed or loaded the same way.

---

## User Scenarios & Testing

### User Story: All AI prompts live under prompts/ (High)

As the owner, I want every system prompt in `src/main/resources/prompts/<module>/`, loaded the same way as the briefing prompt, so that prompt changes are plain file diffs.

**Independent Test**: Each prompt file exists and no prompt text block remains in Java.

### User Story: Prompt text is unchanged (High)

As the owner, I want the model to receive exactly the same prompt text as before, so that outputs are not affected by the move.

**Independent Test**: A test compares the loaded resource with the previous text for each prompt.

---

## Requirements

- Every system prompt MUST be a resource under `src/main/resources/prompts/`.
- The rendered text sent to the model MUST be byte-identical to today's.
- Existing service tests MUST pass without changes to their expectations.
- The pattern MUST follow `BriefingGeneratorService` (`@Value` resource, rendered in `@PostConstruct`).

## Out of Scope

Changing models or routing, new AI features, anything marked defer in the gap analysis.

## Clarifications Needed

- Should prompt files carry a version suffix or header now? (the report calls this a community convention, so the default is no)

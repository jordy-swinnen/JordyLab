# Feature Specification: Manage AI Prompts in Langfuse

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-11-01
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `shared` (AI layer), used by `fna` and `gamecatalog`
**Origin**: owner request: "save and manage my prompts from Langfuse instead of in my repository"

---

## Overview

The four system prompts live as `.st` files in the repository, so changing a word means a commit, a build and a deploy. Langfuse already receives the AI traces. This feature makes Langfuse Prompt Management the place where prompts are edited, versioned and promoted: the backend fetches each prompt by name and label at runtime, falls back safely when Langfuse is unreachable or not configured, and links every AI call to the prompt version that produced it.

---

## User Scenarios & Testing

### User Story: Edit a prompt without a deploy (High)

As the owner, I want to edit a prompt in the Langfuse UI and have the running backend use the new text shortly after, so that tuning a prompt needs no commit, build or deploy.

**Independent Test**: Publish a new version under the active label; within the cache lifetime the next AI call sends the new text.

### User Story: Roll back a bad prompt (High)

As the owner, I want to move the active label back to an earlier version in Langfuse, so that a prompt that makes outputs worse is undone in seconds.

**Independent Test**: Move the label to the previous version; the next call after the cache lifetime sends the old text.

### User Story: See which prompt version produced a result (High)

As the owner, I want each AI call in Langfuse to show the prompt name and version it used, so that metrics, cost and scores can be compared per prompt version.

**Independent Test**: After a call, the generation in Langfuse shows the prompt and version badge and appears under the prompt's linked generations.

### User Story: The app keeps working when Langfuse does not (High)

As the owner, I want AI features to keep working when Langfuse is down or has no keys configured (local dev, the e2e stack), so that prompt management is never a new single point of failure.

**Independent Test**: With no keys, and again with Langfuse unreachable, every AI feature still builds its prompt and calls the model.

### User Story: Different prompts per environment (Medium)

As the owner, I want local, staging-style and production to read different labels of the same prompt, so that I can try a change locally before promoting it to production.

**Independent Test**: Two backends configured with different labels receive different versions of the same prompt.

### User Story: Existing prompts are moved over without changing behaviour (High)

As the owner, I want today's four prompts created in Langfuse with identical text, so that moving to Langfuse changes nothing about the outputs.

**Independent Test**: The text compiled from Langfuse equals the text rendered from the repository files for the same inputs, for all four prompts.

---

## Requirements

- Each system prompt (`fna` briefing, `gamecatalog` enrichment, LibBot interpret, LibBot answer) MUST be fetched from Langfuse by name and label through one shared component in `shared/ai`; modules MUST NOT call the Langfuse API themselves.
- The label to read MUST be configurable per environment (default `production`).
- Prompts MUST be cached in memory with a configurable lifetime (default 60 seconds). A stale prompt MUST be served while a refresh runs in the background, and the last good version MUST be served if a refresh fails. The generated Java client has no cache, so this is part of the feature.
- When Langfuse has no keys, is unreachable, or the prompt is missing, and nothing is cached, the bundled fallback text MUST be used and the event logged and counted. An AI call MUST NOT fail because of prompt management.
- Placeholders in Langfuse prompts use `{{name}}`. The bundled fallback files MUST use the same syntax, so one renderer serves both, and the rendered text MUST be identical to today's for the same inputs.
- Each AI call MUST record the prompt name and version it used on the Langfuse generation (`langfuse.observation.prompt.name` / `.version`). When the fallback was used, no version is recorded and the span says so.
- The model for a feature MUST keep coming from Settings → AI Models and `jordylab.ai.features`; Langfuse prompt `config` is not used to choose models or routing.
- The app only reads prompts. Creating and promoting prompts happens in the Langfuse UI or CLI.
- Langfuse API keys use the existing `LANGFUSE_PUBLIC_KEY` / `LANGFUSE_SECRET_KEY` secrets and must never be logged.
- All calls still go through `ResilientAiService`; the fetch happens before the call and outside any open database transaction.
- Tests MUST cover: cache hit, stale-while-refresh, refresh failure keeping the last good prompt, no-keys fallback, variable rendering, and the version attributes on the span. No test calls the real Langfuse API.

## Out of Scope

Choosing models or routing from Langfuse, chat-type prompts (all four are single system prompts), prompt experiments and datasets (see the golden-set draft), writing prompts back from the app, a prompt editor inside JordyLab, prompts for features that do not exist yet.

## Clarifications Needed

- **Does the repository keep a copy?** The request is to manage prompts in Langfuse instead of in the repository, but the app must still work without Langfuse (local dev, e2e, outage). Recommended default: keep the four files as a clearly labelled offline fallback, never edited for tuning, with Langfuse as the source of truth whenever it is configured. The alternative is no copy and AI features that fail while Langfuse is unreachable and nothing is cached.
- Default cache lifetime: 60 seconds (matches the Langfuse SDKs) or longer, since edits are rare?
- Should a missing prompt or label fail loudly on startup in production, or only log and fall back?
- Where does the first push of the four prompts into Langfuse happen: by hand through the UI, with the Langfuse CLI, or a one-off script kept in `tools/`?
- Should the e2e stack get a stub prompt source, or simply run on the bundled fallback?

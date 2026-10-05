# Feature Specification: AI Token Usage Metrics and a Spending Budget

**Feature Branch**: assigned by SpecKit when specified
**Created**: 2026-10-05
**Status**: Draft (reference for `/speckit-specify`; see `research.md`)
**Module**: `shared` (AI layer), `settings`
**Origin**: gap analysis `docs/research/spring-ai-gap-analysis.md`

---

## Overview

Calls are counted but their size is not: `AiCallCompleted` and `jordylab.ai.calls` carry no token usage, and there is no limit on spend. The OpenRouter credit has already run out once without warning during an earlier test campaign.

---

## User Scenarios & Testing

### User Story: See what AI calls cost in tokens (High)

As the owner, I want input and output tokens recorded per feature, provider and model, so that I can see where usage goes.

**Independent Test**: After calls, a metric and the event show token counts per feature.

### User Story: Be warned before the money runs out (Medium)

As the owner, I want a configurable daily token budget per feature that warns at a threshold and stops non-essential calls at the limit, so that credits are not drained unnoticed.

**Independent Test**: With a tiny budget, the next call is refused with a clear reason and a warning notification fires first.

### User Story: Credit exhaustion is explained (Low)

As the owner, I want a 402 'insufficient credits' to surface as such in Settings, so that I know to top up.

**Independent Test**: The existing `INSUFFICIENT_CREDITS` reason shows on the AI Models page.

---

## Requirements

- Token usage from each response (when the provider reports it) MUST be recorded on the metric and on `AiCallCompleted`.
- A call whose provider reports no usage MUST be recorded as 'unknown', never as zero.
- A budget breach MUST fail fast with an explicit reason, never a silent skip.
- The budget MUST be configurable per feature and MUST default to off.

## Out of Scope

Changing models or routing, new AI features, anything marked defer in the gap analysis.

## Clarifications Needed

- Where does the budget live: `jordylab.ai.*` properties, or the Settings page like the model choice?
- Which features may be stopped at the limit and which never (the monthly briefing)?

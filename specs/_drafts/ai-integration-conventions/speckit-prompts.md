# AI Integration Conventions: SpecKit Prompts

Background is in `research.md`, the source report is `research-report.md`, the expected spec is in `spec-draft.md`.

> **Numbering:** none. SpecKit assigns the next free number when this draft is specified, so drafts can be taken in any order.

---

## `/speckit-specify`

```
Short name: ai-integration-conventions.

Turn the Spring AI architecture research report (specs/_drafts/ai-integration-conventions/research-report.md, to be moved to docs/research/spring-ai-architecture.md) into working conventions for how AI features are built in this repo, without changing product behaviour.

WHY: every AI feature goes through ResilientAiService, and more are coming. I want agents to follow the same proven rules each time, and I want the gaps between the research and what the repo already does written down, so I can decide which ones are worth building.

CONVENTIONS: a short, repo-specific rules file next to the shared AI code (in jordylab-be, under shared/ai), read by both Claude Code and OpenCode. Claude Code needs a one-line CLAUDE.md that imports it, because it does not read nested AGENTS.md files. The existing AGENTS.md in jordylab-be points to it and to the full research doc. The rules only carry the research's high-confidence findings; anything the research marks unverified stays in the reference doc.

REFERENCE DOC: the full report saved under docs/research/ with corrections: Spring AI 2.0 went GA on 12 June 2026 (the report says 28 May), the baseline is Spring Boot 4.0 and 4.1, and check whether the Boot 4.0.3 + Spring AI 2.0.1 combination I run has the starter-dependency problem some reports describe.

GAP ANALYSIS: compare the report against ResilientAiService and the AI code in the repo (retry, fallback, token budgets, typed outputs, prompts as resources, no LLM calls inside transactions, tool authorization, observability content logging, evals). Result: a written list of gaps, each marked worth building, defer or not applicable. Each worth-building gap becomes a draft under specs/_drafts, not code.

SKILLS AND AGENTS: update the /ai-endpoint skill (shared by both tools) and the code-reviewer agent (both the Claude Code and the OpenCode copy) with the checklist.

VERIFY: confirm in Claude Code (/context) and in a fresh OpenCode session started in the AI package that the rules load; say plainly which checks could not be run.

Out of scope: building any of the gaps, changing models or routing, adding new AI features.
```

# AI Integration Conventions: Research

Date: 2026-10-05. The source is `research-report.md` in this folder (an architect-perspective report on building
production AI integrations with Spring AI). This file records what was checked, what is corrected, and how the report
should be filed so both agent tools use it without loading it into every session.

## Verdict: file it in three layers, by how it should load

The report is a reference, not a set of instructions. Loading all of it into every session would cost tokens and bury
the rules that matter. Following the dual-agent-config skill:

| Layer | Where | Loads |
|---|---|---|
| Full report (corrected) | `docs/research/spring-ai-architecture.md` | On demand, through a row in the Reference Docs table of the root `AGENTS.md` |
| Short repo-specific rules | `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md` plus a one-line `CLAUDE.md` with `@AGENTS.md` beside it | When an agent works in the shared AI package (OpenCode reads nested `AGENTS.md`; Claude Code only reads nested `CLAUDE.md`, hence the import) |
| Pointer | the Spring AI section of `jordylab-be/AGENTS.md` | With the backend instructions, so AI code in other modules (for example gamecatalog) still finds the rules |
| Checklist | the `/ai-endpoint` skill and the `code-reviewer` agent (both the `.claude/agents` and `.opencode/agents` copy) | When scaffolding or reviewing AI code |

Path-scoped `.claude/rules/` files were considered and rejected: they work in Claude Code only, so the rules would have
to be duplicated for OpenCode.

## Corrections to the report

- **GA date.** The report says Spring AI 2.0 went GA on 28 May 2026 (and flags it as secondary-sourced). The official
  announcement is dated **12 June 2026**: https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/
- **Baseline.** The report says Spring Boot 4. The announcement says Spring AI 2.0 is designed for **Spring Boot 4.0 and
  4.1** with Spring Framework 7.0.
- **To check.** An issue reports that the Spring AI 2.0.0 starters pull Spring Boot 4.1 dependencies despite documented
  4.0 compatibility (https://github.com/spring-projects/spring-ai/issues/6465, title seen only). The repo runs Boot
  4.0.3 with Spring AI 2.0.1; verify whether this applies.
- **Unverified items stay out of the rules.** The report itself marks some items unverified (one MCP-security CVE, the
  Anthropic HTTP-pool customizer, some cache metadata). They stay in the reference doc only.

## What the repo already does (from `AGENTS.md` and `jordylab-be/AGENTS.md`)

- All AI calls go through `ResilientAiService.call(AiFeature, …)`; `ChatClient` is never created directly.
- Each `AiFeature` has its own model (Settings page, else the default in `jordylab.ai.features`).
- Calls go to OpenRouter first and are retried once on Anthropic on any failure.
- Spring AI 2.0.1 GA; the OpenAI and Anthropic modules wrap the vendor SDKs, whose own retries apply
  (`spring-ai-retry` is no longer on the classpath). Errors are mapped in `ResilientAiService.reasonFor`.
- Every call publishes `AiCallCompleted` and counts `jordylab.ai.calls`.
- The pgvector `VectorStore` auto-configuration is excluded; no embedding model is configured.
- Trading requires human approval for every order.

## Candidate gaps to check (not yet analysed)

These are topics from the report to compare against the code. Whether each is a real gap is the job of the gap
analysis, not a claim made here: typed structured output with validation after `.entity()`, prompts kept as versioned
resources, no LLM calls inside database transactions (outbox and idempotency for long work), token budgets and cost
limits, circuit breaker or bulkhead around providers, content logging off by default in observability, tool
authorization and least privilege, human approval for write tools, evals (unit, golden examples, LLM-as-judge) in CI,
tenant and filter safety once RAG arrives, conversation-ID handling once chat memory arrives.

## Open questions

- Which gaps are worth building now, and which wait for the recipe or trading modules?
- Should evals run in CI on every merge or nightly (they cost model calls)?

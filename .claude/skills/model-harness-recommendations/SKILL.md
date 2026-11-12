---
name: model-harness-recommendations
description: Recommend a model for each harness (OpenCode Go Plus or Claude Code) for Plan steps (constitution, specify, clarify, plan, tasks, analyze, converge) and Build steps (implement, checklist, taskstoissues) at budget, balanced and performance cost levels. Each cost level sets its own quality line. Use when the user asks which model or harness to use for a SpecKit step, wants to compare or refresh models, asks for a cheaper or better option, or pastes an OpenCode usage table.
---

# Model and harness recommendations

Give one model per harness, mode and cost level. Read the sources below before answering; do not rely on memory for prices or scores. Say which dates you read.

## Inputs to ask for

1. The OpenCode console "Usage by Model" table (requests, input, cache read, cache write, output, cost per model) and its date range. The console does not show the range.
2. The Claude plan: Pro, Max 5x, Max 20x, Ultra or Team. Ask when unknown. It changes headroom, not the ranking.

## Sources

- https://opencode.ai/docs/go/ and https://opencode.ai/go: plans, monthly dollar caps, Go Plus caps, per-token prices.
- https://platform.claude.com/docs/en/about-claude/models/overview and https://platform.claude.com/docs/en/about-claude/pricing: model IDs and API prices.
- https://claude.com/pricing and the usage-limits help article: plan prices and relative allowances.
- https://artificialanalysis.ai/leaderboards/models and the model pages: Intelligence Index, Cost per Task, First Answer Token.

Artificial Analysis is the only source that scores both the Claude models and the OpenCode Go models on one scale. Its limits: some models are deprecated there with no current index, and some are unscored. Check each model's page, not only the leaderboard. Other benchmark sites (BenchLM SWE-bench Verified and Terminal-Bench, tbench.ai) may not cover these models, so do not swap in a coding leaderboard for them without checking coverage first. Say so when relevant, and offer a short local trial of one SpecKit step with two or three candidates.

## Measure the real cost per request on OpenCode Go

Real $/request = console cost / requests. Use this rather than the docs' estimated requests, which assume about 55k cached tokens; real agent requests carry 100k to 420k. Check: cost = (input x in + cache read x cache_read + cache write x cache_write + output x out) / 1,000,000, at the price tier matching the request's prompt size. The published prices should reproduce the console within about 2%. Capacity = monthly cap / real $/request, with the 5-hour window at 20% of the cap and the week at 50%. A model with fewer than 30 requests is unmeasured.

## Quality lines move with the cost level

| Cost level | Plan line | Build line |
|---|---|---|
| Budget | Artificial Analysis index 40 | index 30 |
| Balanced | index 45 | index 35 |
| Performance | index 50 | index 40 |

A model below the Build line is Light at that level. The labels are relative to the cost level.

## Picking rules

For each harness, mode and cost level, take the candidates at or above the line that have a usable score, then:

- Budget: the cheapest candidate (real $/request on Go when measured, AA Cost per Task otherwise). Among candidates within 1.25 times the cheapest, take the highest index.
- Balanced: the highest index per AA dollar (index / Cost per Task), among candidates at or above the line.
- Performance: the highest index whose First Answer Token is 120 seconds or less.

For Claude, AA Cost per Task is an API-equivalent proxy, because Claude allowances are relative. Mark each pick with its basis: measured or unmeasured cost, current or provisional index, and capacity.

## Output format

One table per harness: Plan and Build as rows, Budget, Balanced and Performance as columns. Each cell names the model, effort, index, cost basis and capacity. Then list changes from the user's current setup with reasons, and the data basis (dates and usage range). Do not change any settings unless the user asks.

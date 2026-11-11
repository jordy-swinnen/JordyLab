---
name: opencode-go-plus-build-plan-recommender
description: Live-fetch the OpenCode Go pricing page and current BenchLM rankings to recommend and compare OpenCode Go, Go Plus, and Go Teams models for Planning and Build, then print side-by-side Planning and Build cost/allowance tables and optionally update opencode.json and .opencode/commands/. Use whenever the user asks which OpenCode Go or Go Plus model to use for Plan or Build, wants a budget or value comparison of Go models, asks to refresh the Build/Plan model tables, or wants to set default models for OpenCode agents and commands.
license: MIT
compatibility: Requires live access to https://opencode.ai/docs/console/go/ (via WebFetch) and the BenchLM MCP server (benchlm_recommend, benchlm_value_board, benchlm_compare_models, benchlm_list_models).
metadata:
  author: jordy-swinnen
  version: "1.1"
allowed-tools: WebFetch Read Write Edit
---

# opencode-go-plus-build-plan-recommender

Recommend OpenCode Go / Go Plus / Go Teams models for **Planning** and **Build** by fetching the **latest live data** every time the skill runs.

Never rely on cached or hardcoded model lists, prices, allowances, or benchmark scores. Models, promotional discounts, monthly limits, and BenchLM scores change frequently.

## 1. Fetch live OpenCode Go models, prices, and allowances

Before calling BenchLM, fetch `https://opencode.ai/docs/console/go/` with `webfetch` (`WebFetch`). Extract from the live page:

1. **Current model roster** and exact OpenCode Go model IDs (`opencode-go/<model-id>`).
2. **Pricing table (`Usage limits`):** `Input`, `Output`, `Cached Read`, `Cached Write` (per 1M tokens), and the **Monthly limit** dollar allowance for each plan tab (`Go`, `Go Plus`, `Go Teams`).
3. **Promotional notes and pricing tiers:**
   - Temporary discounts and their expiration dates (for example, 50%-off windows).
   - Peak vs. off-peak hours and pricing (such as DeepSeek off-peak vs. peak).
   - Context-window price tiers (such as `<= 100K`, `<= 200K`, `<= 256K`, `<= 272K`, `<= 512K`).
4. **Estimated requests table (`Estimated requests`):** Requests per 5 hours, per week, and per month for the user's plan (default to **Go Plus** when the user has Go Plus or does not specify), plus the per-request token assumptions listed below the table.

## 2. Query live BenchLM rankings for Planning and Build

Run live queries through the `benchlm` MCP server (`compact: true`):

1. **Planning (`reasoning` surface):**
   - Call `benchlm_recommend` with `useCase: "everyday"`, `surface: "reasoning"`, `tags: ["reasoning"]`, `excludeTags: ["api-retired", "superseded"]`, `maxInputPrice: 2`, `maxOutputPrice: 6`, `scores: true`, `limit: 10`.
   - Call `benchlm_value_board` with `surface: "reasoning"`, `priceBasis: "blended"`, `maxBlendedPrice: 3`, `tags: ["reasoning"]`, `excludeTags: ["api-retired", "superseded"]`, `limit: 25`.
2. **Build (`coding` surface):**
   - Call `benchlm_recommend` with `useCase: "software-development"`, `surface: "coding"`, `excludeTags: ["api-retired", "superseded"]`, `maxInputPrice: 2`, `maxOutputPrice: 6`, `scores: true`, `limit: 10`.
   - Call `benchlm_value_board` with `surface: "coding"`, `priceBasis: "blended"`, `maxBlendedPrice: 3`, `excludeTags: ["api-retired", "superseded"]`, `limit: 25`.
3. **Cross-reference with the live OpenCode Go roster:**
   - For notable models on the OpenCode Go page that did not appear in the top picks (including any discounted model that might carry the `superseded` tag), resolve their exact catalog keys with `benchlm_list_models` and call `benchlm_compare_models` (2 to 5 keys per call) to get their current `reasoning`, `coding`, `agentic`, and `knowledge` scores, ranks, intervals, `modalities`, `supersededBy`, and `lifecycleNotices`.
   - Record `dataAsOf` from BenchLM and link each model's `url`.

## 3. Compute actual request costs from the live pricing table

Token prices are identical across **Go ($10/mo)**, **Go Plus ($40/mo)**, and **Go Teams ($20/member/mo)**; only the included monthly dollar allowance per model changes (5-hour = 20% of monthly, weekly = 50% of monthly).

Compute a standard comparison request for each candidate model using the live prices fetched in Step 1:

- **Standard comparison request:** `2,000` fresh input tokens + `50,000` cached-input tokens + `1,000` output tokens (excluding cache writes).
- **Formula:**
  ```text
  example_request_cost = (2,000 * input_price + 50,000 * cached_read_price + 1,000 * output_price) / 1,000,000
  ```
- State clearly that these are **allowance deductions** against each model's monthly dollar limit on the subscription, not extra charges unless **Extra Usage** is enabled.

## 4. Output format: both tables + recommendation

Always present the results using this structure, populated entirely from the live fetch and live BenchLM calls:

### Recommendation
- State the top **Build** pick and why (coding score, software-development benchmark coverage, image-input support if needed, and monthly request allowance).
- State the top **Planning** picks by tier:
  - **Best budget / high-volume planner** (best reasoning step-up per dollar and highest request allowance).
  - **Best higher-reasoning upgrade** (especially relevant on **Go Plus**, where 4x higher monthly allowances make mid-tier `$2/$10` reasoning models practical for daily planning).
  - **Best temporary/discounted option** if any model on the live Go page has an active discount, noting its expiration date and whether BenchLM marks it `superseded`.
  - **Agentic alternative** if a candidate scores notably higher on the `agentic` surface than on pure `reasoning`.

### Overview tables (state `dataAsOf` and the target plan: Go / Go Plus / Go Teams)

#### Planning (`reasoning` surface)

| Model | Reasoning score | Go price per 1M: input / cached / output | Example request | Go Plus typical requests/month |
|---|---:|---:|---:|---:|
| **[Model Name](benchlm-url)** | `<score> (#<rank>)` | `$<in> / $<cached> / $<out>` | **`$<cost>`** | **`<requests/mo>`** (`$<limit>` limit) |

#### Build (`coding` surface)

| Model | Coding score | Go price per 1M: input / cached / output | Example request | Go Plus typical requests/month |
|---|---:|---:|---:|---:|
| **[Model Name](benchlm-url)** | `<score> (#<rank>)` | `$<in> / $<cached> / $<out>` | **`$<cost>`** | **`<requests/mo>`** (`$<limit>` limit) |

Include footnotes for any active promotional discounts, peak/off-peak windows, context-length tiers, or `superseded` flags discovered during Step 1 and Step 2.

## 5. Applying the defaults (when requested)

When the user asks to set the recommended models as defaults:

1. Update `opencode.json`: `model`, and `agents.build.model` / `agents.plan.model` (OpenCode 2). Only if the file still uses the OpenCode 1 `agent` map and no `agents`, edit `agent.*.model` instead. Never write both: OpenCode 2 prefers `agents`, so a value added under `agent` next to it is silently ignored.
2. Search `.opencode/commands/` for any command that pins a `model:` in frontmatter. Confirm with the user whether to align those commands with the Plan and Build choices, and verify that any command judging images uses a model with image input.
3. Update the model routing note in `AGENTS.md` if the repository documents its defaults there.
4. Run the repository's config check if it has one. With the shared `dual-agent-config` skill installed, use `python3 .claude/skills/dual-agent-config/scripts/check_config.py`.

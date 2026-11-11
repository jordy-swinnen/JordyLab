# Recommending a model against a subscription budget

BenchLM ranks models; a subscription prices them. Do both, in this order.

## 1. Query BenchLM

Use the MCP server `benchlm` when it is connected, otherwise its REST API at `https://data.benchlm.ai/v1` with `Authorization: Bearer $BENCHLM_DATA_KEY`.

- `benchlm_recommend` — "which model should I use". Give `surface: "coding"` for build work, `useCase: "software-development"`, and:
  - `tags: ["image-input"]` when the task includes judging renders, screenshots or exported PNGs. Without it you will be offered text-only models.
  - `maxInputPrice` / `maxOutputPrice` in USD per 1M tokens when the user names a cap (for example "not more than the current model").
- `benchlm_compare_models` — put 2 to 5 exact keys side by side, including the model being replaced. Read the per-surface ranks (coding, agentic, multimodal), not just the overall score.
- `benchlm_get_model` — check `modalities` before claiming a model reads images; `null` means unrecorded, not text-only.
- `benchlm_list_access_plans` — the vendor's own plans, caps and free tiers.

Record `dataAsOf` and link each row's `url` when you quote a number. One page of results is one read; the free tier is 1,000 reads a month.

## 2. Turn the plan into real cost

A subscription allowance is a dollar budget, not a request count, so:

```
cost per request = (input_tokens  x input_price
                  + cached_tokens x cached_price
                  + output_tokens x output_price) / 1,000,000
requests per month = monthly allowance / cost per request
```

Use the vendor's published per-request token estimates when it gives them; otherwise use a measured mean from the session. Remember peak/off-peak pricing and context-size tiers double or halve the numbers.

### Worked example: OpenCode Go / Go Plus

Pricing and per-request token estimates: <https://opencode.ai/docs/console/go/#usage-limits>.

| Model | In / Out ($ per 1M) | Go allowance | Go Plus allowance | Cost / request | Go Plus requests / month |
|---|---|---|---|---|---|
| DeepSeek V4.1 Flash | 0.15 / 0.60 off-peak | $100 | $200 | ~$0.00046 | ~433,000 |
| GLM-5.3-Flash | 0.15 / 0.50 | $60 | $180 | ~$0.0019 | ~95,000 |
| GLM-5.3 | 1.40 / 4.40 | $15 | $120 | ~$0.015 | ~8,000 |

Method: multiply each token count by its price, sum, divide the allowance by the result. DeepSeek V4.1 Flash reads images and is cheapest per request, so a coding-plus-renders task fits it best; the Go Plus allowance removes the request wall that the $10 Go plan hits.

### Claude Code Pro

Claude Code Pro includes Claude models under usage limits rather than token prices, so it is a flat-cost option rather than a metered one. Pair it with BenchLM's coding ranks: Claude Sonnet 5.5 sits near the top of the coding surface and reads images, so a Pro subscription is a reasonable single-harness choice for a build-and-look task; the metered Go Plus plan is the high-volume option beside it.

## 3. Honest limits of the recommendation

- A benchmark score is benchmark-shaped work, not the user's repository. Say so.
- Intervals overlap; a 2-point gap is directional, not certain.
- Provider curation matters: a model the harness has not tested may behave differently in long tool loops.
- Prices and scores move; give the date and the source, and re-query rather than trusting an old answer.
- You cannot hear audio or judge subjective visual quality and feel; say which judgements are the person's.

# Contract — Settings AI Models API

Admin-only AI feature configuration (spec FR-011–FR-016, research D1/D2/D4). All endpoints require the `admin` realm
role. Reads the `AiFeature` registry (`shared/ai`), the settings store (`settings` schema), the last-run rows, and the
cached gateway model catalog.

## Endpoints

### `GET /api/settings/ai-models`

One row per registered AI feature. A new feature added in code appears here automatically with its default model (
FR-014).

**200** body:

```json
{
  "features": [
    {
      "key": "gamecatalog.chat.answer",
      "displayName": "Game Catalog chat answer writing",
      "moduleName": "gamecatalog",
      "description": "Writes the chat answer from the matched games",
      "currentModel": "anthropic/claude-haiku-4.5",
      "defaultModel": "anthropic/claude-haiku-4.5",
      "fallbackModel": "claude-sonnet-5",
      "modelAvailable": true,
      "lastRun": {
        "provider": "openrouter",
        "model": "anthropic/claude-haiku-4.5",
        "fallbackUsed": false,
        "outcome": "SUCCESS",
        "failureReason": null,
        "ranAt": "2026-09-27T19:02:44Z"
      }
    }
  ]
}
```

- `currentModel` = saved setting if present, else `defaultModel` (effective model, FR-014).
- `fallbackModel` is read-only, from config (`jordylab.ai.fallback.model`, default `claude-sonnet-5` via Anthropic
  direct).
- `modelAvailable` = `currentModel` (when a saved setting exists) is present in the cached gateway catalog; `false`
  drives the "model unavailable" flag (spec US6-2) — and calls use the fallback until a new model is picked.
- `lastRun` is `null` when the feature has never run. `failureReason` is non-null only for `outcome: "FAILURE"`.

### `GET /api/settings/ai-models/catalog?search={text}&vendor={prefix}`

The gateway's model list, trimmed and cached (~1 h TTL, config `jordylab.settings.model-catalog.cache-ttl`). Text-output
chat models only; `~` alias entries excluded.

**200** body:

```json
{
  "fetchedAt": "2026-09-27T18:00:00Z",
  "fresh": true,
  "models": [
    {
      "id": "deepseek/deepseek-v4.1-flash",
      "name": "DeepSeek: V4.1 Flash",
      "vendor": "deepseek",
      "pricingPerMillionTokens": {
        "input": 0.28,
        "output": 0.42
      },
      "contextLength": 128000,
      "expiring": false
    }
  ]
}
```

- `pricingPerMillionTokens.input`/`output`: the gateway's per-token prices × 1,000,000 (research §1.2). Router-priced
  models (`-1`) get `null` values with `id` still listed.
- `vendor` derives from the id prefix; `?vendor=` filters by it; `?search=` matches id + display name. Both optional.
- `fresh: false` + `fetchedAt` = serving a stale cache because the gateway's `/models` is unreachable (spec edge case —
  saving still works for cached ids).
- **503** `{"reason": "GATEWAY_CATALOG_UNAVAILABLE"}` only when there is no cache at all (first boot with the gateway
  down). No silent empty list (constitution II).

### `PUT /api/settings/ai-models/{featureKey}`

Save the model choice for one feature. Applies from the next AI call; no restart (FR-014).

Request body: `{"modelId": "openai/gpt-6-luna-pro"}`

**204** No content.
**400** `{"reason": "UNKNOWN_FEATURE"}` — `{featureKey}` is not in the registry.
**400** `{"reason": "BLANK_MODEL"}` — blank `modelId`.
**400** `{"reason": "MODEL_UNAVAILABLE"}` — model id is not in the catalog **and the catalog is fresh**. When the
catalog is stale, unknown-but-previously-cached ids are accepted (spec edge case).

## Non-functional contract

- The gateway base URL + key are server-side config (`jordylab.ai.gateway.*` → `spring.ai.openai.*`, research D2); never
  exposed by any endpoint.
- The catalog cache is shared with nothing else; the picker endpoint reads it read-only.
- Every AI call (not this endpoint) records feature/provider/model/outcome via the `AiCallCompleted` event — this API
  only reads what was recorded (FR-016).

# Data Model — Settings Module (Users & AI Models)

Derived from [spec.md](spec.md) Key Entities + FR-006/FR-009/FR-014/FR-016 and the decisions
in [research.md](research.md) (D3–D6). Backend conventions: UUID ids, `BaseEntity`/`AbstractAggregateRoot`,
builder-first entities, schema-per-module.

## Entity overview

| Entity                | Kind                      | Store                     | Aggregate root |
|-----------------------|---------------------------|---------------------------|----------------|
| AppUser               | View (not persisted)      | Keycloak (via Admin REST) | —              |
| AiFeature             | Code enum (not persisted) | `shared/ai` registry      | —              |
| AiFeatureModelSetting | JPA entity                | `settings` schema         | yes            |
| AiFeatureLastRun      | JPA entity                | `settings` schema         | yes            |
| GuestChatUsage        | JPA entity                | `settings` schema         | yes            |

All three persisted aggregates are independent — no foreign keys between them, and none references a Keycloak user by
FK (`AppUser` is not a JordyLab row; `GuestChatUsage` stores the JWT `sub` as a plain string).

## AppUser (view over Keycloak)

A projection of a Keycloak user as the Users page needs it. Never stored by JordyLab; built by the settings module from
Admin REST responses.

| Field      | Type          | Notes                                                                                              |
|------------|---------------|----------------------------------------------------------------------------------------------------|
| id         | UUID          | Keycloak user id                                                                                   |
| email      | string        | doubles as the login name (`registrationEmailAsUsername`)                                          |
| firstName  | string        |                                                                                                    |
| lastName   | string        |                                                                                                    |
| createdAt  | instant       | Keycloak `createdTimestamp`                                                                        |
| enabled    | boolean       | `false` after Reject                                                                               |
| realmRoles | set of string | app roles only: `admin` / `guest` (+ `gamecatalog-scanner` for device accounts, ignored by the UI) |
| status     | derived       | see state table below                                                                              |

**Status derivation (D6 — single source of truth is Keycloak, no bookkeeping):**

| enabled | has `guest` | status                                                                       |
|---------|-------------|------------------------------------------------------------------------------|
| true    | no          | `PENDING` (also where a revoked user returns — re-approvable)                |
| true    | yes         | `APPROVED`                                                                   |
| false   | —           | `REJECTED` (cannot log in; the email cannot sign up again — duplicate email) |

Transitions: Approve = grant `guest` (+ ensure enabled); Reject = disable (stays listed, kept for "this email was
already refused"); Revoke = remove `guest` + end sessions (→ back to `PENDING`); Reset password = set temporary
password, shown once to the admin.

**Invariant (FR-007):** Reject/Revoke must fail with `LAST_ADMIN_PROTECTED` when the target is the only enabled user
holding `admin`.

## AiFeature (code registry — `shared/ai`)

Enum, one constant per AI call site. Not persisted; adding one in code makes it appear everywhere (registry, resolver,
picker, metrics tags).

| Field        | Type   | Notes                                                                                         |
|--------------|--------|-----------------------------------------------------------------------------------------------|
| key          | string | `fna.briefing`, `gamecatalog.enrichment`, `gamecatalog.chat.query`, `gamecatalog.chat.answer` |
| displayName  | string | e.g. "FNA daily briefing"                                                                     |
| moduleName   | string | owning module, for display grouping                                                           |
| description  | string | what the call does (shown in the AI Models page)                                              |
| defaultModel | string | from `jordylab.ai.features.<key>.model` config; overridable per row                           |

The four values map 1:1 to the current call sites (`BriefingGeneratorService`, `EnrichmentService`, `ChatService`
translate/compose) — research §2.1.

## AiFeatureModelSetting (JPA — `settings.ai_feature_model_setting`)

The admin's saved model choice per feature. Absence of a row = use the code default.

| Column             | Type              | Rules                                                                                                                                     |
|--------------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| id                 | UUID              | PK                                                                                                                                        |
| feature_key        | string            | unique; must equal an `AiFeature` key (validated on save → `400 UNKNOWN_FEATURE`)                                                         |
| model_id           | string            | non-blank; validated against the cached model catalog when the catalog is fresh, accepted as-is when stale (stale-list edge case in spec) |
| updated_by_subject | string            | JWT `sub` of the admin who saved                                                                                                          |
| audit              | from `BaseEntity` | created/last-modified                                                                                                                     |

Mutation: `updateModel(modelId, updatedBy)` registers `AiFeatureModelSettingUpdated` (in-module) → invalidates the
resolver cache → next call uses the new model, no restart (FR-014). Creation happens on first save; there is no delete (
revert = set back to the default model id).

## AiFeatureLastRun (JPA — `settings.ai_feature_last_run`)

One row per feature, upserted by the settings listener on the shared-published `AiCallCompleted` Modulith event (D4).

| Column         | Type              | Rules                                                                              |
|----------------|-------------------|------------------------------------------------------------------------------------|
| id             | UUID              | PK                                                                                 |
| feature_key    | string            | unique                                                                             |
| provider       | string            | `openrouter` / `anthropic` — the one that **actually answered**                    |
| model          | string            | model id that answered                                                             |
| fallback_used  | boolean           | true when the primary failed first (FR-012)                                        |
| outcome        | enum              | `SUCCESS` / `FAILURE`                                                              |
| failure_reason | enum, nullable    | shared `ProviderFailureReason` (`MODEL_NOT_FOUND` included) — `null` iff `SUCCESS` |
| ran_at         | instant           | from the event (injected `Clock`)                                                  |
| audit          | from `BaseEntity` |                                                                                    |

The AI Models page derives the **"model unavailable" flag** (spec US6-2) as: `AiFeatureModelSetting.model_id` not
present in the cached gateway catalog — the flag is query-time derived, not stored.

## GuestChatUsage (JPA — `settings.guest_chat_usage`)

Per guest, per calendar day, message count. Counting: the settings-owned `GuestChatLimitFilter` increments only on 2xx (
D5).

| Column        | Type              | Rules                                                                                                                                                           |
|---------------|-------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| id            | UUID              | PK                                                                                                                                                              |
| user_subject  | string            | JWT `sub` (admin subjects never counted — filter exempts them)                                                                                                  |
| usage_date    | date              | calendar day in the injected clock's zone (configurable, default local dev zone); **the unique key member is the date, so midnight = new row = reset** (FR-009) |
| message_count | int               | `>= 1`                                                                                                                                                          |
| audit         | from `BaseEntity` |                                                                                                                                                                 |

- **Unique:** `(user_subject, usage_date)`.
- **Increment must be race-safe** for concurrent chats: native
  `INSERT … ON CONFLICT (user_subject, usage_date) DO UPDATE SET message_count = guest_chat_usage.message_count + 1` (
  same race-safety approach as gamecatalog's `insertSteamGameIfAbsent`).
- **Daily limit** is config, not data: `jordylab.settings.guest-chat.daily-limit` (default 20, clarified value).
  Survives restarts by construction (persisted rows).
- Check-then-reject reads the row for `(subject, today)`; count `>= limit` → `429 CHAT_LIMIT_REACHED` with `resetsAt` =
  next midnight in the clock zone.

## Migration

One Flyway migration (naming + header per repo rules):

- `V<yyyyMMdd>__settings_create_tables.sql` — `CREATE SCHEMA IF NOT EXISTS settings; SET search_path TO settings;` then
  the three tables + uniques + indexes (`guest_chat_usage (user_subject, usage_date)` unique;
  `ai_feature_last_run (feature_key)` unique).

Schema ownership addition: `settings` → jordylab-be (add to the ownership list in `jordylab-be/AGENTS.md` during
implementation).

## Validation & invariants summary (traceable to spec)

- FR-004/FR-005: no credential data ever crosses into JordyLab storage — only the derived view fields above.
- FR-006: all user mutations go through the Admin REST client; every failure from Keycloak surfaces as an explicit
  error (fail fast), never a partial silent state.
- FR-007: `LAST_ADMIN_PROTECTED` invariant enforced before Reject/Revoke mutates anything.
- FR-014: save → in-module event → cache invalidation → next call. No restart path exists.
- FR-016: every call produces an `AiCallCompleted` event → last-run row + Micrometer counter (`jordylab.ai.calls`, tags:
  feature/provider/outcome/fallback) — both from the same event.
- FR-009: limit check and increment are in the filter (before/after the chain); the count row exists only for days with
  at least one successful message.

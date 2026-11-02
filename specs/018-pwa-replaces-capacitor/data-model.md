# Data Model: Replace the Native Android Wrapper with an Installable Web App

Feature: `018-pwa-replaces-capacitor` · Date: 2026-11-02

This feature is removal-dominated: it **deletes** one aggregate (`MobileRelease`) and its whole schema, deletes two domain events, and **adds no database tables**. The new "data" is client-side platform state and one configuration record.

## Removed entities

### `MobileRelease` (deleted)

Was: `jordylab-be/src/main/java/dev/jordy/jordylab/mobile/domain/MobileRelease.java` + `mobile.mobile_release` (created by `V20260928004__mobile_create_tables.sql`).

| Column | Type | Notes |
|---|---|---|
| id | UUID PK | |
| version_name / version_code | TEXT / INTEGER | `UNIQUE (version_code)` |
| release_notes / sha256 / size_bytes | TEXT / TEXT / BIGINT | |
| min_supported_version_code | INTEGER | update blocker |
| storage_key | TEXT | APK path on the volume |
| published_at | TIMESTAMPTZ | |
| created_at / updated_at | TIMESTAMPTZ | audit columns |

Dropped by `V20261103001__mobile_drop_schema.sql` (research D8) together with the `mobile` schema. **No data migration**: release metadata has no consumer after the APK stack is gone; the table's contents are dead the moment the endpoints are removed.

### Domain events (deleted)

- `settings.UserSignUpPending` — was published by `PendingSignupWatcherService`, listened to only by `MobileNotificationListener`.
- `fna.BriefingReady` — was published by `BriefingGeneratorService`, listened to only by `MobileNotificationListener`.

Both replaced by direct `NtfyClient` calls (research D4). The same migration deletes their rows from the Modulith `event_publication` table.

## Added / changed structures

### `NotificationProperties` (new, backend config — not persisted)

```java
@ConfigurationProperties(prefix = "jordylab.notifications")
public record NotificationProperties(Ntfy ntfy, String appBaseUrl)
```

| Key (env var) | Default | Meaning |
|---|---|---|
| `jordylab.notifications.ntfy.base-url` (`NTFY_BASE_URL`) | blank → client no-ops | self-hosted ntfy root, e.g. `https://jordylab.be/ntfy` |
| `jordylab.notifications.ntfy.topic` (`NTFY_TOPIC`) | blank → client no-ops | admin topic |
| `jordylab.notifications.ntfy.token` (`NTFY_TOKEN`) | blank → anonymous publish | optional bearer token |
| `jordylab.notifications.app-base-url` (`JORDYLAB_APP_BASE_URL`) | blank → client no-ops | site origin for click-through URLs, e.g. `https://jordylab.be` |

Validation: none at startup beyond the existing log-and-warn (fail-fast does not apply to an optional notifier — a missing topic must never block sign-ups or briefings; behaviour is identical to today's `NtfyClient`).

### Web App Manifest (client-side file, `apps/jordylab/public/manifest.webmanifest`)

| Field | Value | Source |
|---|---|---|
| `name` / `short_name` | JordyLab / JordyLab | unchanged |
| `id` / `start_url` / `scope` | `/` / `/` / `/` | new (research D6) |
| `display` | `standalone` | unchanged |
| `background_color` / `theme_color` | `#131314` | 016 Content Led default |
| `icons` | 192 + 512 PNG, `"purpose": "any maskable"` | same files, purpose added |
| `share_target` | `{ "action": "/share", "method": "GET", "params": { "title": "title", "text": "text", "url": "url" } }` | new (research D2) |

### Share payload (transient, URL query state — never persisted)

`/share?title=&text=&url=` parsed by the landing component:

| Field | Type | Resolution rule |
|---|---|---|
| `title` | string (optional) | display only |
| `text` | string (optional) | display + LibBot prefill (full text) |
| `url` | string (optional) | target URL; if absent, the first `http(s)://` match inside `text` |
| derived `targetUrl` | string \| null | `url` param, else regex-extracted from `text`; drives "Save to FNA" availability |

Empty state: all three params absent/blank → calm empty state (FR edge case). The route sits behind `authGuard`; the params survive login via `returnUrl` (research D5).

### Per-device install state (client-side, unchanged key)

`localStorage["jordylab.install-prompt.dismissed-until"]` — epoch ms, 30-day suppression of the automatic sheet. Standalone detection (`display-mode: standalone` / iOS `standalone`) is derived at read time, not stored. The stashed `BeforeInstallPromptEvent` is in-memory only (a signal in `InstallPromptStore`).

## Entity/relationship impact summary

| Area | Change |
|---|---|
| `mobile` schema | dropped (migration) |
| Modulith `event_publication` | rows for the two deleted events removed (same migration) |
| `settings` schema | none (last-seen pending set stays in-memory) |
| `fna` schema | none |
| Keycloak realm | clients `jordylab-mobile`, `mobile-release-ci` and role `mobile-release-publisher` deleted from both exports (research D9/D10) |
| Frontend stores | `UpdateCheckStore`, `ApkDownloadService` deleted; `InstallPromptStore` rewritten; `ShareTargetService` dissolved into the route/component |

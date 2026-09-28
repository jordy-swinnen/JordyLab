# Data Model — Mobile App (Android via Capacitor, iOS Home-Screen Web App)

Derived from [spec.md](spec.md) Key Entities + FR-001–FR-004, FR-011, FR-018 and the decisions in
[research.md](research.md) (D7, D8, D11). Backend conventions: UUID ids, `BaseEntity`/`AbstractAggregateRoot`,
builder-first entities, schema-per-module.

## Entity overview

| Entity        | Kind                       | Store                          | Aggregate root |
|---------------|-----------------------------|----------------------------------|----------------|
| MobileRelease | JPA entity                  | `mobile` schema                  | yes            |
| DownloadLink  | Signed token (not persisted)| N/A — HMAC-signed, self-expiring | —              |

No foreign keys, no shared tables with any other module — `mobile` is a leaf module (depends only on `shared`).

## MobileRelease (JPA — `mobile.mobile_release`)

One row per published, signed Android release. Written once by the CI publish call; never updated in place (a
correction is a new release with a higher `versionCode`, never an edit).

| Column                       | Type              | Rules                                                                                                                          |
|-------------------------------|-------------------|----------------------------------------------------------------------------------------------------------------------------------|
| id                            | UUID              | PK                                                                                                                                 |
| version_name                 | string            | human-facing, e.g. `1.2.0`; shown in the update prompt (spec US3)                                                                  |
| version_code                 | int               | unique; monotonically increasing (Android's own requirement); compared against the installed app's version on every start/resume  |
| release_notes                | text              | shown in the update prompt                                                                                                         |
| sha256                       | string (64 hex)   | of the APK file; recorded for display/verification (FR-001); the signing-cert fingerprint (a *different* SHA-256, D7) is validated separately at publish time, not stored per-release — it is one fixed config value (`jordylab.mobile.release.signing-cert-sha256`) since the signing key never changes |
| size_bytes                   | long              | file size, shown in the admin's release list                                                                                       |
| min_supported_version_code   | int               | releases below this `versionCode` are blocked app-side with the mandatory-update screen (FR-011); defaults to the previous release's value unless CI explicitly raises it |
| storage_key                  | string            | relative filename under `jordylab.mobile.release.storage-dir` (D7) — never exposed to clients directly; only reachable via a signed download link |
| published_at                 | instant           | set on creation                                                                                                                    |
| audit                        | from `BaseEntity` | created/last-modified (created == published_at in practice, kept for consistency with every other entity)                        |

**Invariants**:
- `version_code` is unique and the publish call MUST reject a lower-or-equal `version_code` than the current latest (
  `400 VERSION_CODE_NOT_MONOTONIC`) — Android itself would refuse to install a downgrade, so this is caught before it
  ever reaches a device.
- The CI publish call MUST verify the uploaded APK's **signing-cert** SHA-256 against the one fixed, configured value
  before creating the row; mismatch → `400 SIGNING_CERT_MISMATCH`, no row created, no file kept (FR-003, D7).
- "Latest" (`GET /api/mobile/releases/latest`) is simply the row with the highest `version_code` — no separate
  `isLatest` flag to keep in sync.

## DownloadLink (not persisted — signed token, D8)

Not a database row. A short-lived, single-purpose HMAC-SHA256-signed token minted on demand by
`POST /api/mobile/releases/{id}/download-link` and validated on `GET /api/mobile/download/{token}`.

| Field       | Type    | Notes                                                                                          |
|-------------|---------|--------------------------------------------------------------------------------------------------|
| release_id  | UUID    | which `MobileRelease` this token authorizes                                                     |
| subject     | string  | JWT `sub` of the user who requested it (not currently used for anything but audit/debug logging) |
| expires_at  | instant | issued 5 minutes from mint time (D8; spec only commits to "within minutes," FR-002)              |

Signed and base64url-encoded into the token; the download endpoint recomputes the HMAC and checks `expires_at` before
streaming — an invalid signature or expired token is `403 DOWNLOAD_LINK_INVALID` (fail fast, no partial stream). No
cleanup job needed — expired tokens simply stop validating.

## Migration

One Flyway migration:

- `V<yyyyMMdd>__mobile_create_tables.sql` — `CREATE SCHEMA IF NOT EXISTS mobile; SET search_path TO mobile;` then the
  `mobile_release` table with a unique index on `version_code`.

Schema ownership addition: `mobile` → `jordylab-be` (add to the ownership list in `jordylab-be/AGENTS.md` during
implementation, alongside `finance`, `gamecatalog`, `garmin`, `recipe`, `settings`).

## Events (new — cross-module, not persisted, research D9)

Not entities, but part of this feature's data shape — Spring Modulith application events, published by `settings`
and `fna`, consumed only by `mobile.service.MobileNotificationListener`.

| Event             | Published by                              | Payload                              | Consumed by                                                        |
|--------------------|--------------------------------------------|----------------------------------------|-----------------------------------------------------------------------|
| `UserSignUpPending`| `settings` (pending-signup detection, replaces 006's planned direct Ntfy call) | userId, email, displayName | `mobile` → Ntfy push, click URL → `screen=settings-users` (spec US6-1) |
| `BriefingReady`    | `fna` (`BriefingGeneratorService`, new)    | briefingId, date                      | `mobile` → Ntfy push, click URL → `screen=fna-briefing` (spec US6-2)  |

## Validation & invariants summary (traceable to spec)

- FR-001: every published release carries version name, version code, release notes, SHA-256, size, minimum
  supported version code, and publish date — all non-null columns above.
- FR-002/FR-003: download-link issuance requires an authenticated `admin`/`guest` caller (matrix, not this data
  model); the link itself is a signed token, not a queryable row, so it cannot be enumerated or replayed past expiry.
- FR-004: the publish endpoint's authorization (CI-only role) is enforced in `SecurityConfig`, not by this data model
  — but the signing-cert check above is the data-integrity half of the same requirement.
- FR-011: `min_supported_version_code` is read on every app start/resume against the caller's own reported version
  (sent as a header or query param on the `latest` call — see [contracts/mobile-releases-api.md](contracts/mobile-releases-api.md)).
- FR-013: not represented here — enforced via the Keycloak offline-consent revocation call in `settings.revoke()`
  (research D12), since the "session" being invalidated is a Keycloak grant, not a `mobile`-owned row.

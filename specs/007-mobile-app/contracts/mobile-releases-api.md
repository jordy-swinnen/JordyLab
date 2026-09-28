# Contract — Mobile Releases API

REST contract for the `mobile` module (spec FR-001–FR-004, FR-011; research D7/D8/D11). Roles per
[access-matrix.md](access-matrix.md).

## `GET /api/mobile/releases/latest`

**Role**: `admin` or `guest`

Request: no body. Callers SHOULD send their current installed `versionCode` as a query param so the response can
indicate whether an update is available or required — this keeps the decision server-side and testable, rather than
duplicating the "is X > Y" comparison in the client.

```
GET /api/mobile/releases/latest?installedVersionCode=12
```

Response `200`:

```json
{
  "id": "…uuid…",
  "versionName": "1.3.0",
  "versionCode": 14,
  "releaseNotes": "Fixed artwork loading on slow connections.",
  "sha256": "…64 hex chars…",
  "sizeBytes": 18345213,
  "minSupportedVersionCode": 10,
  "publishedAt": "2026-09-28T10:00:00Z",
  "updateAvailable": true,
  "updateRequired": false
}
```

`id` is required for the client to then call `POST /releases/{id}/download-link` — a gap in the original draft of
this contract, closed during frontend implementation (see tasks.md T020 notes).

- `updateAvailable`: `installedVersionCode < versionCode` (spec US3-1 — shows "Update available: vX.Y").
- `updateRequired`: `installedVersionCode < minSupportedVersionCode` (spec US3-2 — mandatory-update screen). When
  `installedVersionCode` is omitted (e.g. the admin viewing the release list in Settings, not the app's own
  update-check call), both flags are `false`.
- No releases published yet → `404 NO_RELEASES_PUBLISHED`.

## `POST /api/mobile/releases/{id}/download-link`

**Role**: `admin` or `guest` (pending/logged-out → 401/403, spec scenario 4)

Request: no body.

Response `200`:

```json
{ "downloadUrl": "https://{PRODUCTION_DOMAIN}/api/mobile/download/{signedToken}", "expiresAt": "2026-09-28T10:05:00Z" }
```

- `{id}` unknown → `404 RELEASE_NOT_FOUND`.
- The token is a signed, 5-minute, single-purpose credential (research D8) — not a database row, so there is nothing
  to revoke or list.

## `GET /api/mobile/download/{token}`

**Role**: permitAll — token-validated instead (research D8; this is deliberate, not a gap — see
[access-matrix.md](access-matrix.md))

Response `200`: binary stream, `Content-Type: application/vnd.android.package-archive`,
`Content-Disposition: attachment; filename="jordylab-{versionName}.apk"`.

Invalid signature, unknown release, or expired token → `403 DOWNLOAD_LINK_INVALID` (spec scenario 5 — "the download
is refused," no partial stream ever starts).

## `POST /api/mobile/releases`

**Role**: `mobile-release-publisher` (service account only — research D13, STOP-AND-REPORT gate)

Multipart request: the signed APK file plus release metadata.

```
POST /api/mobile/releases
Content-Type: multipart/form-data

file: jordylab-1.3.0.apk
versionName: "1.3.0"
versionCode: 14
releaseNotes: "Fixed artwork loading on slow connections."
minSupportedVersionCode: 10   # optional — defaults to the previous release's value if omitted
```

Server-side, in order:

1. Compute the APK's SHA-256 and its signing-certificate SHA-256.
2. Signing-cert SHA-256 must equal the one fixed, configured value (`jordylab.mobile.release.signing-cert-sha256`) —
   mismatch → `400 SIGNING_CERT_MISMATCH`, nothing persisted, uploaded file discarded (spec Edge Cases — "an APK
   signed with a different key" is refused before it ever reaches a device).
3. `versionCode` must be strictly greater than the current latest — otherwise `400 VERSION_CODE_NOT_MONOTONIC`.
4. Store the file under `jordylab.mobile.release.storage-dir` (research D7), create the `MobileRelease` row.

Response `201`:

```json
{ "id": "…uuid…", "versionName": "1.3.0", "versionCode": 14, "sha256": "…", "sizeBytes": 18345213, "publishedAt": "2026-09-28T10:00:00Z" }
```

## `GET /.well-known/assetlinks.json`

**Role**: permitAll, served with no auth and no redirects (research §1.2 — a hard Digital Asset Links requirement).

Response `200`, `Content-Type: application/json`:

```json
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "{APPLICATION_ID}",
      "sha256_cert_fingerprints": ["{SIGNING_CERT_SHA256_COLON_SEPARATED}"]
    }
  }
]
```

`{APPLICATION_ID}` and `{SIGNING_CERT_SHA256_COLON_SEPARATED}` come straight from `MobileProperties` config — both
values are STOP-AND-REPORT gates (research D14) and are placeholders until finalized.

# Contract: Android APK release (`.github/workflows/apk-release.yml`)

Replaces `release.yml`. Started by hand only (FR-022). No push, merge or deploy starts it.

## Input

| Input | Type | Required | Rule |
|-------|------|----------|------|
| `tag` | string | yes | Must match `^v[0-9]+\.[0-9]+\.[0-9]+(-rc[0-9]+)?$`, exist as a git tag, and point to a commit on `main` |

## Verify (kept from `release.yml`)

- Grammar, with `minor ≤ 99`, `patch ≤ 99`, `rc ≤ 98`, so the version code stays monotonic.
- The commit is an ancestor of `origin/main`.
- A successful `Build` run exists for that commit on `push` (its images were built).

## Build and publish (kept from `release.yml`)

- `versionName` = tag without `v`; `versionCode` = `MAJOR·1000000 + MINOR·10000 + PATCH·100 + (rcN, or 99 for a final)`.
- Signed release APK, `apksigner verify`, the keystore removed after the build.
- Upload to `POST /api/mobile/releases` as the `mobile-release-ci` client, with `releaseNotes` from the notes rule below.
  Status `201` is the only success; `409` after a completed upload is reported, not retried.

## Release notes (changed)

Previously the notes came from the GitHub Release body. No Release is created any more, so the notes are generated:

```
git log --no-merges --format='- %s' <previous tag>..<tag>
```

`<previous tag>` is the last `v*` tag before `<tag>` in version order. The text is cut to 4000 bytes, as before.

## Not in this workflow

- No `retag`, no GitHub Release, no deploy, no `publish` step. Those are removed (FR-024).
- The APK is still not attached to a GitHub Release (spec 007 FR-002).

# Contract: Self-Update (GitHub Releases + app updater)

New. Serves US2 / FR-006/007/008, ticket D3/D8. App-side contract between the updater service and the GitHub Releases assets; no backend endpoint.

## Release layout (published by the tag-triggered workflow)

```
GitHub Release vX.Y.Z
├── JordyLabSync-macos-aarch64.dmg       (or .app.zip)
├── JordyLabSync-linux-x86_64.AppImage   (or .tar.gz)
├── JordyLabSync-windows-x64.exe         (US5, later)
├── update-manifest.json                 (per-platform entries)
└── SHA256SUMS / signature file
```

## update-manifest.json

```json
{
  "version": "1.2.0",
  "pubDate": "2026-11-11T10:00:00Z",
  "platforms": {
    "macos-aarch64": { "url": "<asset url>", "sha256": "<hex>", "signature": "<base64>" },
    "linux-x86_64":  { "url": "<asset url>", "sha256": "<hex>", "signature": "<base64>" }
  }
}
```

- `signature` is produced in CI with the updater key (SOPS secret, CI-only); the app embeds the public key.
- If `tufup` is adopted (research R2), its TUF metadata replaces this manifest shape; the app-side flow below is unchanged.

## App-side flow (update_service)

1. **Check** (on schedule and on window open): fetch the manifest for the current platform; compare `version` with the running version.
2. **Notify** (design `D_Update.png`): "New version {version} available" + **Update** button (FR-006).
3. **Download** to a temp location; verify `sha256` and `signature` before touching the install.
4. **Swap + restart**: install the new version, restart the app; the previous version is kept until the new one starts successfully.
5. **Failure path** (design `D_Update_Failed.png`): any download/verify/swap failure keeps the running version, shows the error with **Retry** (FR-007). An interrupted update leaves the old version runnable.

## Rules

- Updates install only after one click — never silently (Out of scope).
- Unsigned macOS (D5): first install needs the manual Gatekeeper approval; updates must not reintroduce a Gatekeeper prompt on every launch.
- The updater never runs as root/elevated; the app installs to a user-writable location.

# Phase 0 Research: Game Catalog Python Scanner

**Date**: 2026-09-26
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md)

Decisions and rejected alternatives for the 003 client. Evidence gathered against the running code, Keycloak realm, and platform docs.

---

## R1 — Delivery: single frozen Python file, stdlib only

**Decision**: The client is a proper package (`gamecatalog-scanner/`) frozen by `tools/build_client.py` into one `.py` served by the backend, rendered with the existing `${...}` substitution.

**Rationale**: Same download/auth UX as the shell script; zero runtime dependencies (no supply chain on the HTPC); one inspectable file; the package keeps real modules and tests. The macOS system Python (verified 3.9.6) is the floor.

**Rejected**: PyInstaller (per-OS binaries, opaque, AV noise); pip/uv install from a private repo (needs git credentials on each host); a `.pyz` zipapp (server-side zip surgery, less inspectable than a plain text file).

## R2 — Change detection: metadata fingerprint + server-side decision

**Decision**: The client hashes a canonical metadata manifest (path, size, mtime_ns; NFC; no content reads) and sends it to `POST /ingest/check`. The **server** decides whether a scan is needed using its own stored `ingest_version`, the stored fingerprint, and the last outcome.

**Rationale**: Content hashing is infeasible (TB-scale ROM libraries); a stat walk is cheap. The server must own invalidation, otherwise a client scheme change or a server logic change can never force re-ingestion (v2 review caught an infinite-loop variant of this).

**Rejected**: Content hashing (too slow); embedding a version in the client-supplied digest as the invalidation signal (loops when server and client versions diverge); local-only caching (stale sources page, cache-loss churn).

## R3 — Identity continuity

**Decision**: Port `EmuDeckLibraryParser.titleFromFilename` and `EMUDECK_PLATFORM_LOOKUPS` verbatim to the client; Steam parsing stays server-side (appid identity unchanged); multi-file games anchor to an identity already in the catalog (`.m3u` path or lowest-numbered disc); NFC-normalize refs/titles at intake plus a guarded data migration.

**Rationale**: `game.external_ref` is unique per source and is the reconciliation key; a changed ref hides the old row and creates a new one, losing the AI description (enrichment cost). The server-side parser already strips `(...)`/`[...]` tags, so the client must reproduce it exactly.

**Rejected**: A one-off re-import on cutover (loses descriptions); content-hash identity (002 explicitly excludes it).

## R4 — Auth: offline access, least privilege, revocation

**Decision**: Device grant with `scope=openid offline_access`; realm `fullScopeAllowed=false` + a single-role scope mapping; cross-process lock around refresh (rotation is on); `uninstall` revokes via the token-revocation endpoint.

**Evidence**: Realm export has `ssoSessionIdleTimeout: 1800`; a live device-grant token returned `refresh_expires_in: 1800` — the default refresh window is 30 minutes, so offline tokens are required for unattended startup. The decoded token currently carries both of the account's roles, so the least-privilege change is real, not hypothetical. `refreshTokenMaxReuse`/`revokeRefreshToken` are unset → rotation on.

**Rejected**: A dedicated scanner user (second account to manage; no security gain over a single-role client if scope mapping is correct); long-lived static tokens (removed in 002 for good reason).

## R5 — Startup mechanics

**Decision**: Linux = systemd **user** oneshot + timer, `loginctl enable-linger` probed with a login-triggered fallback; macOS = LaunchAgent `RunAtLoad`. No root/admin; bounded retry at boot instead of `network-online.target`.

**Evidence**: `SetUserLinger()` is polkit-gated (`org.freedesktop.login1.set-user-linger`) and unprivileged self-linger is not guaranteed, hence the probe+fallback. LaunchAgent `RunAtLoad` is login-time, not boot-time — stated explicitly.

**Rejected**: `schtasks /SC ONLOGON` for Windows (elevation-gated — and Windows is deferred anyway); a system (root) service (violates least privilege).

## R6 — Safety guards

**Decision**: Client skips (and reports) any configured root that is missing/unreadable/empty-of-game-files; server rejects `SNAPSHOT_SHRINK_SUSPECT` when the resulting installed set is empty or would remove more than `max(10, 50%)` of installed games, unless `force` is set. Exit code 5 only for explicitly configured roots; probed defaults are silent.

**Rationale**: Grace-then-purge means a false "all removed" snapshot loses descriptions after 30 days. The empty-set rule covers small libraries the fraction rule misses. Probed paths (e.g. Flatpak Steam) must not make every run "partial".

## R7 — Grouping scope for 003

**Decision**: `.m3u`, `.cue`/`.gdi` (+ components), and `.chd` standalone/referenced. Directory-based console games and Switch DLC are deferred.

**Evidence**: `ROM_EXTENSIONS` has no `.nsp`/`.xci`, and directory-game files are not ROM-extension files, so today they are ignored entirely — deferring preserves exactly today's behavior (no garbage rows), making the follow-ups additive.

## R8 — Serving the client

**Decision**: Extend the existing `ScriptService` render pattern into `ClientService`; emit rendered values as Python string literals in one constants header; drift test asserts no `${` outside the header and no secret material.

**Rationale**: `${` does not occur in normal Python syntax, so the existing substitution is safe; literal escaping covers hostile realm/URL values.

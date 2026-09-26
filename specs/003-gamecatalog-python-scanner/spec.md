# Feature Specification: Game Catalog Python Scanner

**Feature Branch**: `003-gamecatalog-python-scanner`

**Created**: 2026-09-26

**Status**: Draft

**Input**: User description: "Replace the downloaded shell scan script with a Python client that runs automatically when the machine starts (Linux and macOS), skips re-uploading when nothing changed, keeps catalog identity stable so AI descriptions are not lost, uses a least-privilege token, and fails safe when a library drive is not mounted. Deferred: Windows, compressed uploads, directory-based console games, Switch DLC grouping."

---

## Overview

The 002 Game Catalog ingests libraries by having the owner download a per-library shell script and run it by hand. That works, but it requires remembering to run it, does not distinguish "nothing changed" from "re-upload everything", cannot group multi-disc ROM sets, and was never offered for Windows. This feature replaces the shell script with a small resident Python client that:

- installs once per machine and **launches automatically when the machine starts** (no administrator/root rights),
- asks the server whether the library actually changed and **uploads only when it did** (cheap metadata fingerprint, never reading file contents),
- normalizes and groups ROM names the same way the existing catalog already does, so **existing games keep their identity and their AI descriptions**, and
- fails safe: an unmounted or empty library never wipes catalog entries.

The server remains the authority for reconciliation, validation, and deduplication. The client is a thin, auditable, standard-library-only artifact downloaded from the existing Sources page.

## Clarifications

### Session 2026-09-26

- Q: How does the server decide a re-upload is needed without the client dictating it? → A: The server stores its own **ingest-logic version** alongside the last fingerprint. A scan is needed when the fingerprint differs, when the stored ingest version is older than the current one, or when the last outcome was not successful. The client supplies a fingerprint only; it never controls invalidation.
- Q: What privileges does the resident client's credential carry? → A: Least privilege. The downloadable client authenticates as a client whose token carries **only the scanner role** — no other account roles — and uses an offline (long-lived) refresh token so unattended startup runs work. Uninstalling revokes that offline session.
- Q: When is a dramatically smaller library treated as suspicious? → A: A scan whose resulting installed set is **empty**, or that would remove a large majority of a source's games, is rejected unless explicitly forced. Removing the last game requires a deliberate force.
- Q: What counts as a "partial" result? → A: Only **explicitly configured or registered** library roots can cause a partial result. Auto-detected default locations that happen to be absent are skipped silently.
- Q: How are duplicate files that differ only in Unicode normalization handled? → A: Paths and titles are normalized consistently on the client and the server so one file is one game. The one-time catalog normalization is guarded: if it would collide two existing entries, it aborts loudly instead of losing data.
- Q: Is Windows in scope? → A: No — this feature covers **Linux and macOS**. Windows support, compressed uploads, directory-based console games, and Switch update/DLC grouping are deferred to follow-ups (see Out of Scope).

## User Scenarios & Testing

### User Story 1 - Automatic scanning at machine start (Priority: P1)

As the owner, I install the client once on my Linux HTPC and my Mac, log in a single time in a browser, and from then on the catalog updates itself every time the machine starts. I never remember to run anything. I can also trigger a scan on demand, check status, and uninstall cleanly.

**Why this priority**: This is the entire point of the feature — removing the manual, forgettable step — and everything else improves this flow.

**Independent Test**: On a fresh machine, install the client, complete the one-time login, reboot, and verify the catalog reflects the library without any manual action. Then verify `status` reports the last run and `uninstall` removes the automatic startup entry.

**Acceptance Scenarios**:

1. **Given** an installed client with a valid session, **When** the machine starts, **Then** a scan runs automatically and the catalog is updated with no manual step.
2. **Given** a first-time install on a new machine, **When** the client runs, **Then** it presents a one-time login the owner completes in a browser, and subsequent runs are unattended.
3. **Given** an installed client, **When** the owner requests an on-demand scan, **Then** a scan runs immediately using the same logic as the automatic run.
4. **Given** an installed client, **When** the owner runs status, **Then** they see whether automatic startup is configured, when the last run happened, and its result.
5. **Given** an installed client, **When** the owner uninstalls, **Then** the automatic startup entry and cached credentials are removed and the session is revoked.

---

### User Story 2 - Upload only when something changed (Priority: P1)

As the owner, I do not want my machine re-walking my library and re-uploading a large listing every time it starts for no reason. When nothing changed since the last successful scan, the run should be quick and upload nothing.

**Why this priority**: The value is lost if the automatic run is expensive or noisy. This keeps it cheap enough to run at every start.

**Independent Test**: With a synced library, run a scan; verify nothing is uploaded and the server records a successful check. Add, change, or remove one file; run again; verify the change is uploaded and reconciled.

**Acceptance Scenarios**:

1. **Given** a library unchanged since the last successful scan, **When** the client runs, **Then** it uploads no listing and the run completes quickly.
2. **Given** a library with one added, removed, or modified file, **When** the client runs, **Then** the change is uploaded and reconciled.
3. **Given** the server's ingestion logic has changed since the last successful scan for a source, **When** the client next asks whether a scan is needed, **Then** the server requests a scan even though the client's fingerprint is unchanged.
4. **Given** the owner wants to force a refresh, **When** they scan with force, **Then** the upload happens despite an unchanged fingerprint.

---

### User Story 3 - The catalog stays the same catalog (Priority: P1)

As the owner, I do not want migrating from the shell script to churn the catalog: the games I already have must keep their identity, their titles, and their AI-generated descriptions. A multi-disc game must become one entry, and a library that is temporarily unavailable must never be read as "everything was uninstalled".

**Why this priority**: Data loss and re-enrichment cost are the highest-severity risks of this migration.

**Independent Test**: Run the previous mechanism and the new client over the same fixture library and verify identical identities, titles, and platforms for single-file games; verify a multi-disc set collapses to one entry that reuses an identity already in the catalog; verify an unmounted or emptied root changes nothing.

**Acceptance Scenarios**:

1. **Given** a catalog populated by the previous mechanism, **When** the new client scans the same unchanged library, **Then** every single-file game keeps its identity, title, and platform and no description is regenerated.
2. **Given** a multi-disc set (playlist or disc-numbered files), **When** the client scans, **Then** it produces one entry whose identity matches an entry already in the catalog, and the component files do not appear as separate games.
3. **Given** a configured library root that is missing or unreadable (e.g., drive not mounted), **When** the client runs, **Then** it reports the condition, uploads nothing for that root, and the catalog keeps every existing game.
4. **Given** a scan that would empty a source or remove most of its games, **When** it is not forced, **Then** the server refuses it and nothing is removed.
5. **Given** the same directory reached through different paths or symlinks, **When** the client scans, **Then** no duplicate entries are produced and symlink loops do not hang the scan.

---

### User Story 4 - Clean cutover from the shell script (Priority: P2)

As the owner, when I move a machine to the new client I want the old shell-script schedule gone so the two do not fight over the catalog.

**Why this priority**: A stale script would keep submitting differently-shaped snapshots and cause title flapping; it is a one-time but important operational step.

**Independent Test**: With an old shell-script schedule present, install the new client and verify the old schedule is removed (or clearly warned about), and that a submission from the old script cannot lock in a stale fingerprint.

**Acceptance Scenarios**:

1. **Given** an old shell-script schedule on the machine, **When** the owner installs the new client, **Then** the old schedule is removed automatically and the removal is reported.
2. **Given** a pre-cutover submission arrives after the new client has been registered, **When** the server processes it, **Then** it is flagged as pre-cutover and cannot prevent the new client's next run from being considered out of date.

---

### Edge Cases

- **Library drive not yet mounted at startup**: the client retries briefly (startup ordering is not guaranteed), then skips that root and reports it; nothing is uploaded for it.
- **A library is legitimately emptied by the owner**: the scan is refused as suspicious unless explicitly forced; forcing it is the deliberate way to record an intentionally empty library.
- **Interpreter too old on the machine**: the client prints a clear, actionable message rather than failing with a syntax error.
- **Refresh token expired because the machine was off for a long time**: the run fails explicitly and `status` says a browser login is needed; it never blocks the machine or prompts on a background run.
- **Two runs overlap** (startup and a manual run): they do not corrupt the cached session.
- **A file whose name differs only by Unicode normalization**: it is treated as one game, not two.
- **Steam library installed through Flatpak**: it is detected if present; if not present, it is ignored silently.
- **A configured root exists but is empty of recognized game files**: treated like an empty result (refused unless forced).
- **Very large library**: the scan and, when needed, the upload complete within the existing payload limits; an over-limit upload fails with an explicit reason.

---

## Requirements

### Client & authentication

- **FR-001**: The catalog scan MUST be performed by a resident client downloaded from the web app, replacing the per-library shell script. The client MUST require only the standard interpreter present on target machines (no package installation) and MUST run with ordinary user privileges — no administrator/root rights for install or operation.
- **FR-002**: The client MUST authenticate via a one-time interactive login on first use and MUST run unattended thereafter. Cached credentials MUST be readable only by the owning user. A background run MUST NOT prompt interactively; if the session is no longer valid it MUST fail explicitly and require a browser re-login.
- **FR-003**: The client's credential MUST carry only the role required to submit scans, and no other roles belonging to the account. Long-lived credentials MUST be revocable, and uninstalling MUST revoke them.
- **FR-004**: The client MUST install itself to start automatically when the machine starts (Linux and macOS), and MUST support on-demand scan, status, and uninstall.
- **FR-005**: The client MUST identify its machine by a stable identifier that does not change when the network name changes; the server MUST treat `(machine, library type)` as the source identity, adopting an existing source when a machine is first seen with the new client.
- **FR-006**: The client MUST run on the interpreter present on the target machines (the macOS system Python floor) and MUST print an actionable message if the interpreter is too old.

### Change detection

- **FR-007**: The client MUST decide "did anything change?" from file metadata only — path, size, and modification time — without reading file contents, and MUST compute a fingerprint over that metadata that is stable across runs and across Unicode normalization of file names.
- **FR-008**: Every run MUST ask the server whether a scan is needed before uploading anything; the server MUST record the check as liveness for the source.
- **FR-009**: The need for a scan MUST be decided by the server: fingerprint mismatch, an advance of the server's ingest-logic version, or a previous non-successful outcome each require a scan. A matching fingerprint MUST NOT prevent re-ingestion when the server's ingest logic has advanced.
- **FR-010**: The client MUST support forcing a scan that bypasses change detection.

### Safety

- **FR-011**: If a configured library root is missing, unreadable, or yields no recognizable game files, the client MUST NOT upload an empty or partial listing for that root; it MUST report the condition.
- **FR-012**: The server MUST refuse a scan that would leave a source with no installed games, or that would remove a large majority of a source's installed games, unless the scan is explicitly forced; a refused scan MUST change nothing and MUST NOT be recorded as a successful fingerprint.
- **FR-013**: Only explicitly configured or previously registered library roots MAY cause a partial result; auto-detected default locations that do not exist MUST be skipped silently.

### Identity continuity

- **FR-014**: For single-file games, the client MUST produce the same game identity, title, and platform as the previous mechanism, so that existing entries — and their AI descriptions — are preserved rather than re-created.
- **FR-015**: The client MUST collapse multi-file games into a single entry, using `.m3u` playlists and disc-numbered file patterns, and MUST anchor the resulting identity to an identity already present in the catalog where one exists; files referenced by a playlist or by a disc set MUST NOT appear as standalone games.
- **FR-016**: File names and titles MUST be normalized consistently on the client and the server so that one file is one game. Any one-time normalization of existing catalog entries MUST abort loudly if it would collide two existing entries, rather than fail or silently drop data.

### Scanning coverage & grouping (003 scope)

- **FR-017**: The client MUST follow symlinked library directories without looping indefinitely, and MUST not double-count a location reachable by more than one path.
- **FR-018**: For Steam, the client MUST discover **all** configured library folders, not only the default installation, and MUST recognize the Flatpak location when present.
- **FR-019**: Within 003 scope, the client MUST group `.m3u` playlists, `.cue`/`.gdi` disc images (including referenced `.bin`/`.img`/`.chd` components), and standalone `.chd` files correctly; each group yields one entry.

### Cutover

- **FR-020**: Installing the client MUST detect and remove any pre-existing shell-script schedule for the library, reporting what was removed; status MUST warn if one is still present.
- **FR-021**: A submission received without a client fingerprint for a source that has one MUST be flagged as pre-cutover and MUST clear the stored fingerprint so the new client's next check requests a scan.

### Key Entities

- **Machine**: A host running the client. Attributes: stable machine identifier, network name, per-library scan configuration.
- **Library root**: A configured or registered location to scan (Steam, EmuDeck). Attributes: type, path, whether explicitly configured, availability.
- **Scan fingerprint**: A stable value derived from a library's file metadata, used only to ask the server whether a scan is needed.
- **Scan source** (existing): server-side record of a `(machine, library type)` pair; gains the stored fingerprint, the ingest-logic version at last success, and last-checked liveness.
- **Game** (existing): catalog entry; identity, title, platform, and AI description MUST be preserved across this migration.

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: On a Linux and on a macOS machine, after a single browser login, a machine restart results in an updated catalog with zero manual steps, in 100% of attempted restarts (network permitting).
- **SC-002**: When a library is unchanged, an automatic run uploads zero listing bytes and completes within 60 seconds for a 10,000-file library, reading no file contents.
- **SC-003**: 100% of missing-root, unmounted-drive, and empty-result scans cause zero unintended game removals.
- **SC-004**: For a fixed fixture library, the new client produces identical identities, titles, and platforms to the previous mechanism for 100% of single-file games, and a multi-disc set collapses to exactly one entry reusing an existing identity.
- **SC-005**: The scanner credential contains only the scanner role, verified by inspecting a freshly issued token.
- **SC-006**: Installing, running, and uninstalling the client require no administrator/root privileges on either supported platform.
- **SC-007**: The downloadable client contains no secret material, verified by inspection.

---

## Assumptions

- Single user (the owner); the machine is trusted enough to hold a least-privilege, revocable offline credential in the user's own profile.
- The supported platforms for this feature are Linux (the CachyOS HTPC) and macOS (the dev machine). Windows is deferred.
- The existing server ingestion contract, reconciliation, validation, and grace-period behavior remain authoritative and unchanged except as explicitly extended here.
- The server's Keycloak realm can scope the scanner client to a single role and can issue offline tokens for unattended runs.
- The web app remains the control surface for viewing sources and downloading the client.

## Out of Scope (deferred follow-ups, additive)

- **Windows** support and its installer/CI coverage.
- **Compressed uploads** — measure the largest real payload first; only add compression if a real library approaches the existing payload limit.
- **Directory-based console games** (e.g., PlayStation 3, Wii U folder layouts).
- **Switch update/DLC grouping.**
- Any change to the payload/contract shapes beyond the additive fields introduced here, so the above remain additive.

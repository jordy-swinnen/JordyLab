# Feature Specification: Desktop Sync App

**Feature Branch**: `024-desktop-sync-app`

**Created**: 2026-11-10

**Status**: Draft

**Input**: User description: "JOR-78. Replace the manual scanner scripts with a desktop sync app that users install once and then update from inside the app. The app downloads its latest release from the JordyLab download page, updates itself in-app from GitHub Releases, and automatically detects Steam, EmuDeck and Sunshine games on the machine, tagging each game with its source. Sunshine detection runs on the Sunshine host only. The app's theming preferences must match JordyLab's, so the two feel like one product." (refined and design-settled in the JOR-78 Kaneo ticket)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Install once, the machine syncs itself (Priority: P1)

As the owner, I install the sync app on a Mac or Linux PC and sign in once, so that the machine's Steam and EmuDeck libraries stay in the catalog without me downloading or running anything by hand.

**Why this priority**: This is the whole pain being removed; it is demonstrable on its own.

**Independent Test**: Install on a machine with Steam and EmuDeck libraries, connect it, and watch games appear in the catalog without any script download.

**Acceptance Scenarios**:

1. **Given** a Mac or Linux PC with Steam and EmuDeck libraries, **When** I install the app, sign in as admin and connect the machine, **Then** the machine appears in Settings → Machines with its own token and the first sync runs without any further action.
2. **Given** a connected machine idles in the background, **When** the scheduled sync time arrives, **Then** the app asks the server whether a scan is needed and uploads only when the server says so (same server-authoritative behaviour as today's client).
3. **Given** the app is running, **When** I click **Sync now**, **Then** a check runs immediately and the window shows the outcome (up to date / scanned / failed with reason).
4. **Given** the machine goes offline or the backend is unreachable, **When** a scheduled sync is due, **Then** the app shows the failure with the reason and retries on the next schedule, never losing local state.

---

### User Story 2 - Update the app from inside the app (Priority: P1)

As the owner, I want the app to tell me when a new version is out and install it with one click, so that updating a PC never means downloading a file by hand again.

**Why this priority**: Self-update is the other half of the pain; without it the app recreates the manual-update problem it exists to remove.

**Independent Test**: Release a newer version, see the notification on the old install, click Update, and confirm the app restarts on the new version.

**Acceptance Scenarios**:

1. **Given** a machine running an older version, **When** a newer release is published, **Then** the app shows a notification naming the new version within its next check.
2. **Given** the notification is shown, **When** I click Update, **Then** the app downloads, installs and restarts itself on the new version without a terminal, a browser or a manual download.
3. **Given** an update download or install fails, **When** the failure happens, **Then** the app stays on the working old version and shows the error with a retry.

---

### User Story 3 - One theme, everywhere (Priority: P2)

As a signed-in user, I want my theme choice to follow my account, so that the web app and the desktop app look like one product in the same theme.

**Why this priority**: The feel of one product matters, but the sync and update pain is solved without it.

**Independent Test**: Pick a theme in the desktop app, open the web app signed in, and see the same theme.

**Acceptance Scenarios**:

1. **Given** I pick one of the six themes in the desktop app, **When** I open the web app signed in as the same account, **Then** the web app shows the same theme.
2. **Given** I pick a theme in the web app, **When** I open the desktop app, **Then** the desktop app shows the same theme.
3. **Given** I choose "follow device" as my account preference, **When** I use the web app on a light-mode device and the desktop app on a dark-mode device, **Then** each follows its own device setting.

---

### User Story 4 - Sunshine detection on the host (Priority: P2)

As the owner, I want games configured in Sunshine on the Sunshine host to appear in the catalog tagged with their source, so that I can see what I can stream.

**Why this priority**: Net-new detection end to end (new source type, backend parsing, host-side reading of Sunshine's configuration); valuable but separable.

**Independent Test**: Run the app on the Sunshine host and see its configured games appear tagged as a Sunshine source.

**Acceptance Scenarios**:

1. **Given** the app runs on the Sunshine host, **When** it syncs, **Then** each game configured in Sunshine appears with a Sunshine source tag on that host.
2. **Given** a game configured in Sunshine is already in the catalog from Steam or EmuDeck, **When** the sync completes, **Then** the Sunshine source is added to that same game, not a second game.
3. **Given** the app runs on a machine that is not the Sunshine host, **When** it syncs, **Then** nothing Sunshine-related is reported.

---

### User Story 5 - Windows (Priority: P2)

As the owner, I want to install the same app on a Windows PC, so that every machine I use can sync without scripts.

**Why this priority**: Windows is net-new (the script client never ran there); no scan runs there today.

**Independent Test**: Install on Windows and see User Story 1's scenarios pass.

**Acceptance Scenarios**:

1. **Given** a Windows PC with a Steam library, **When** I install the app and connect it, **Then** User Story 1 scenarios 1–4 pass on Windows.
2. **Given** an update is published, **When** I click Update on Windows, **Then** User Story 2 scenarios pass on Windows.

---

### Edge Cases

- Backend unreachable or machine offline at sync time: the app shows the failure with reason, retries next schedule; no local state is lost.
- Machine token revoked between syncs: the app says the machine must be reconnected, never silently drops data.
- Library root missing/unreadable (drive unmounted): skip that root and report it as skipped in the sync result (today's partial-scan behaviour).
- Two machines reporting the same hostname: each keeps its own machine identity (today's machine-id-based resolution).
- A game present in both Steam and EmuDeck: one catalog game with two source tags (existing reconciliation).
- Update interrupted mid-download or mid-install: the previous version keeps working; a retry is offered.
- Guest (non-admin) account in the app sign-in: cannot connect a machine (FR-002), with a clear message.
- A scan that would remove a large share of a source's games is still rejected by the server's suspect-shrink guard (unchanged).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app MUST run as a small windowed desktop application on macOS and Linux that stays in the background after launch and starts on login.
- **FR-002**: Setup MUST be restricted to the admin account; a non-admin account MUST NOT be able to connect a machine.
- **FR-003**: The app MUST detect Steam and EmuDeck libraries on the machine it runs on, without manual path configuration when the libraries are in their default locations.
- **FR-004**: Syncing MUST keep today's server-authoritative behaviour: the app asks the backend whether a scan is needed and uploads only when told to.
- **FR-005**: The app MUST sync automatically on a schedule while running, and MUST offer a **Sync now** action.
- **FR-006**: The app MUST notify the user when a newer version of itself is published, and MUST install that version with one click, restarting itself afterwards.
- **FR-007**: WHEN an update fails THE SYSTEM SHALL keep the previous working version and show the error with a retry action.
- **FR-008**: The latest installer for each supported platform MUST be downloadable from a JordyLab download page (admin area of the web app).
- **FR-009**: The theme preference MUST be stored per account (one of the six themes or "follow device") and applied by both the web app and the desktop app for the signed-in account.
- **FR-010**: Sunshine detection MUST run only on the Sunshine host and MUST report a Sunshine source per configured game, reconciled onto the existing catalog entry when the game already exists.
- **FR-011**: The first release MUST support macOS and Linux; Windows support MUST be added before or with the Sunshine story.
- **FR-012**: The script client download (`GET /api/gamecatalog/ingest/client`) MUST be removed once every machine runs the app.

### Key Entities *(include if feature involves data)*

- **Machine connection**: a PC authenticated to JordyLab (today's machine token); carries its own identity so two machines with the same hostname stay separate.
- **Theme preference**: per account; one of the six themes or "follow device"; replaces the per-device browser storage of the theme choice.
- **Sunshine source**: a new scan source type on the Sunshine host, one entry per game configured in Sunshine, reconciling onto the existing catalog game.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a fresh Mac or Linux PC, installing the app and signing in as admin results in the machine's Steam and EmuDeck games in the catalog, with no script downloaded and no terminal used.
- **SC-002**: Publishing a new release and clicking Update on an old install results in the app running the new version, verified on both macOS and Linux.
- **SC-003**: A theme picked in the desktop app appears in the web app for the same account without reloading anything but the page, and vice versa.
- **SC-004**: After the switchover, no machine fetches `GET /api/gamecatalog/ingest/client` and the endpoint is gone.
- **SC-005**: On the Sunshine host, every game configured in Sunshine shows a Sunshine source tag on the existing catalog entry; no duplicate games are created.

## Assumptions

- The app replaces the script client entirely; there is no supported headless/script mode after the switchover.
- Existing machine tokens and the Settings → Machines flow are reused for authentication; the app may create its token through a guided sign-in rather than a manual copy-paste.
- The six themes and the design system from the app redesign are reused for the desktop app, so both surfaces look like one product (designs delivered in `docs/design/JOR-78/`).
- GitHub Releases remains the distribution channel for the app's binaries; the JordyLab download page links there or mirrors it.
- Scheduled sync keeps the current cadence the script client uses today.
- Only the owner's machines are supported (single household); no multi-tenant concerns.

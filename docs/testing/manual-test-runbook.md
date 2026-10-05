# Manual test runbook — what the E2E campaign could not test itself

Written 2026-10-01 at the end of spec 011 (production E2E hardening), against `main` `c0e60a4` and production release
`v0.0.1-rc6`. Everything else was tested by the agent: see `docs/testing/e2e-test-plan.md` (coverage matrix, smoke
results, deploy records) and `docs/testing/bug-log.md`.

**How to use it.** Work through an entry, then record the result:
- all pass → set the listed rows in `docs/testing/e2e-test-plan.md` §5 to `PASS` with the date;
- a step fails → add the next `BUG-<nn>` to `docs/testing/bug-log.md` with that entry's steps as the reproduction.

Commands are Fish-compatible unless marked otherwise. No entry needs a secret pasted anywhere: tokens come from your
password manager or from the app, never from this file.

| ID | What | Needs | Time |
|----|------|-------|------|
| MRB-01 | Signed-in pass on production (admin) | a browser | 15 min |
| MRB-02 | Guest view on production | a second browser profile | 10 min |
| MRB-03 | EmuDeck scan on JordyBox (after the cap fix) | JordyBox | 10 min |
| MRB-04 | Android app: install, login, update prompt | an Android phone | 20 min |
| MRB-05 | Android app: biometric unlock | an Android phone with a fingerprint | 10 min |
| MRB-06 | Android app: share target | an Android phone | 10 min |
| MRB-07 | Push notifications and taps | phone + ntfy app | 10 min |
| MRB-08 | Steam family sync | a Steam family token | 10 min |
| MRB-09 | VPS reboot keeps data and artwork | SSH to the VPS | 15 min |
| MRB-10 | Quarterly restore drill and a point-in-time restore | cluster access | 20 min |
| MRB-11 | Content-Security-Policy rollout | a browser + deploy | 1 h |
| MRB-12 | Colour contrast of the main pages | a browser | 15 min |
| MRB-13 | Full VPS rebuild from git | a spare VPS | 2 h |

---

### MRB-01: Signed-in pass on production (admin)
- Covers: 006-US5, 006-US6, 009-US2, 009-US3, 009-US4, 009-US5 (prod UI half; each passed locally and in CI) | Area/spec: A–D / 006, 009
- Why the agent couldn't: credentials — the agent never types your password, and the browser pane had no session
- You need: your admin account, a desktop browser, 15 min
- Environment: prod
- Steps:
  1. Sign in at `https://jordylab.be` → expect: the Library, Chat, Sources, Switch games, FNA and Settings items in the sidebar.
  2. Account menu (bottom of the sidebar) → **Change password** → expect: Keycloak's page, then back in the app after saving (use a throw-away change only if you want; cancel is fine for the check).
  3. Account menu → **Edit name** → expect: Keycloak's "Update Account Information". Account menu → **Change email** → expect: Keycloak's email page (this needs the `UPDATE_EMAIL` action enabled; it is, HANDOFF-06).
  4. `Settings → AI Models` → expect: 4 features, each with a current model, the fallback `claude-sonnet-5` and a last-run line; open **Change model** on *Game descriptions*, search `haiku`, pick the model that is already selected (no real change) → expect: the list refreshes, no error.
  5. `Switch games` → search `Pikmin` → expect: results with covers; add it as Digital → expect: "Game added". Open it from the Library → expect: "Format · Digital" and the admin section with the select set to **DIGITAL**. Remove it (two-step) → expect: back at the Library, the game gone.
  6. `Switch games → Paste a list` → paste `Pikmin 4` and `Some Unknown Title 9000` → **Find matches** → expect: `Match` and `No match`; untick nothing, **Add 1 games** → expect: summary "Added 1". Remove the game again as in step 5.
  7. Library → filter *Nintendo Switch* → expect: only Switch games; the host filter lists *Nintendo Switch*.
  8. Open the browser console on each page you visited → expect: no red errors.
- Pass when: every step shows its expected result and the console stays clean.
- If it fails: add `BUG-<next>` with the step number; set the matching 006/009 rows to `FAIL`.
- Record result: 006-US5, 006-US6, 009-US2–US5 in `docs/testing/e2e-test-plan.md` §5 → `PASS` with the date.
- **Result 2026-10-02 (agent, signed-in browser pane, rc8):** steps 1–4, 6 (preview only), 7, 8 passed; step 5 done earlier (Pikmin 4 added Digital, then removed through the two-step confirm; catalog 213 → 212). Not exercised: submitting the email/password change, adding via the bulk page. Console clean on every page visited.

### MRB-02: Guest view on production
- Covers: 006-FR (access matrix), 006-US1–US3 (guest side) | Area/spec: B / 006
- Why the agent couldn't: credentials — the guest test account's password isn't known to the agent
- You need: the guest test account (`jordylab.frown533@passmail.net`, HANDOFF-02) in a second browser profile, 10 min
- Environment: prod
- Steps:
  1. Sign in as the guest → expect: the sidebar shows **Library** and **Chat** only (no Sources, Switch games, FNA, Settings).
  2. Open a game's detail page → expect: no *Refresh* / *Regenerate* buttons, and for a Switch game "Format · …" without the admin section.
  3. Type `/settings/users`, `/fna/articles` and `/games/switch` into the address bar → expect: each returns you to the Library.
  4. Chat: ask "Which games support local multiplayer?" → expect: an answer with cited games.
  5. As the admin, **Revoke** the guest in `Settings → Users`; reload the guest's tab → expect: the "awaiting approval" screen. Approve again.
- Pass when: the guest sees only what the access matrix allows and nothing breaks after revoke/approve.
- If it fails: add `BUG-<next>`; set 006-US2/US3 rows to `FAIL`.
- Record result: the 006 guest rows → `PASS`.

### MRB-03: EmuDeck scan on JordyBox (after the cap fix)
- Covers: 003-US1, 003-FR-scan, BUG-047 | Area/spec: C / 003
- Why the agent couldn't: hardware — only JordyBox has the ROM library
- You need: JordyBox, 10 min; release `v0.0.1-rc6` or later live (it is)
- Environment: prod
- Steps:
  1. On JordyBox, download the current client (admin login) from `https://jordylab.be/api/gamecatalog/ingest/client` and save it as `jordylab-scan-prod.py` → expect: the file contains `MAX_PAYLOAD_BYTES = 8 * 1024 * 1024`.
  2. `python3 jordylab-scan-prod.py scan` for the EmuDeck library → expect: `EMUDECK scan APPLIED: {"submitted": N, "added": N, …}` — no "payload exceeds" error.
  3. In the app: Library → host filter → expect: a host for JordyBox with the ROM platforms.
  4. Run the scan a second time → expect: "unchanged, nothing uploaded" (exit 0 — `NO_CHANGE` is success, not a failure).
  5. Optional (BUG-048): start the scan in two terminals at the same time → expect: one prints `APPLIED` (or `NO_CHANGE`), the other waits and prints `NO_CHANGE`; neither ends in an HTTP 500.
- Pass when: the EmuDeck games are in the catalog and the rescan is a no-op.
- If it fails: send the last lines of the output; add `BUG-<next>` (a new cap error means the library is larger than 50,000 games or 8 MiB).
- Record result: 003 rows `PASS`; set BUG-047 to `VERIFIED-PROD`.

### MRB-04: Android app: install, login, update prompt
- Covers: 007-US1 (native side), 007-US2, 007-US2-AS1–AS4, 007-US3, 007-US3-AS1–AS3 | Area/spec: E / 007
- Why the agent couldn't: hardware — needs an Android 10+ phone
- You need: an Android phone, your admin account, 20 min; two releases live to test the update prompt (rc6 and any later tag)
- Environment: prod
- Steps:
  1. On the phone open `https://jordylab.be`, sign in, find the **Get the Android app** entry → expect: a short-lived download link for `0.0.1-rc6` (versionCode 106).
  2. Download and install the APK (allow "install unknown apps" for your browser if asked) → expect: the app installs as *JordyLab*.
  3. Open the app → **Sign in** → expect: Keycloak's login opens in the phone's browser and returns to the app signed in (not a WebView).
  4. As a guest (MRB-02's account): same login → expect: only Library and Chat. As a pending user → expect: the "awaiting approval" screen.
  5. Browse the library → expect: covers and data load as on the website.
  6. Publish a newer release (tag `v0.0.1-rc7` or later, deploy it), then open the app → expect: an "update available" banner with a download link; the version check also appears when returning from the background.
- Pass when: login, role views and the update banner all work.
- If it fails: add `BUG-<next>` with the phone model and Android version.
- Record result: 007-US2 and 007-US3 rows → `PASS`.
- **Result 2026-10-02 (owner, Brave on Android, rc8):** the install dialog rendered as plain unstyled text and Download appeared to do nothing → BUG-049, fixed in rc9. Re-check on the phone after rc9: styled bottom sheet; tapping Download shows "Preparing…" then "Download requested", and `jordylab-0.0.1-rc9.apk` lands in Downloads. Installing and the rest of the app are still untested.
- **Result 2026-10-05 (owner):** install, sign-in, the launcher icon, the in-app Update banner (rc15 → rc16) all work. **Done.**

### MRB-05: Android app: biometric unlock
- Covers: 007-US4, 007-US4-AS1–AS3 | Area/spec: E / 007
- Why the agent couldn't: hardware — a real fingerprint sensor is required
- You need: the app from MRB-04, a phone with a fingerprint enrolled, 10 min
- Environment: prod
- Steps:
  1. Signed in, enable **Unlock with fingerprint** (the toggle near the account menu / Android app entry) → expect: a fingerprint prompt, then the setting shows "on".
  2. Close the app completely, reopen → expect: a fingerprint prompt, then straight into the app.
  3. Fail the fingerprint three times / cancel → expect: falls back to the normal login, nothing cached is shown.
  4. Turn the setting off, then sign out → expect: the next start asks for a full login.
  5. As admin, **Revoke** the user in `Settings → Users` while biometrics are on, reopen the app → expect: login is required and the revoked account is refused.
- Pass when: unlock works only with the fingerprint and is cleared by logout, off-switch and revoke.
- If it fails: add `BUG-<next>`.
- Record result: 007-US4 rows → `PASS`.
- **Result 2026-10-05 (owner, rc15/rc16):** fingerprint unlock works (BUG-051/055 fixed): the switch is under Settings → App, reopening the app asks for the fingerprint. **Done.**

### MRB-06: Android app: share target
- Covers: 007-US5, 007-US5-AS1–AS4 | Area/spec: E / 007
- Why the agent couldn't: hardware — the Android share sheet
- You need: the app from MRB-04, any app with a share button, 10 min
- Environment: prod
- Steps:
  1. In a browser share a link to *JordyLab* → expect: the app opens with the shared text/link.
  2. As the admin: expect the choices **Ask the catalog** and **Save to FNA**. As a guest: only **Ask the catalog**.
  3. Share while signed out → expect: login first, then the same share screen.
- Pass when: the share arrives and the offered actions match the role.
- If it fails: add `BUG-<next>`.
- Record result: 007-US5 rows → `PASS`.

### MRB-07: Push notifications and taps
- Covers: 007-US6 (native side), 007-FR-016 (the push itself passed on prod) | Area/spec: E / 007
- Why the agent couldn't: hardware — a phone that receives the push
- You need: the ntfy app subscribed to `https://jordylab.be/ntfy` with your topic (HANDOFF-10), the JordyLab app, 10 min
- Environment: prod
- Steps:
  1. Register a new account on `https://jordylab.be` (use a throw-away email) → within 5 minutes expect: a push "New JordyLab sign-up" on the phone with the name and email.
  2. Tap the notification → expect: the JordyLab app opens on `Settings → Users` (or the browser if the app isn't installed).
  3. Reject or approve the throw-away account afterwards.
- Pass when: the push arrives and the tap lands on the Users page.
- If it fails: check `kubectl -n jordylab logs deploy/backend | grep -i ntfy` (expect "Ntfy notifications enabled"); add `BUG-<next>`.
- Record result: 007-US6 native rows → `PASS`.

### MRB-08: Steam family sync
- Covers: 005-US2, 005-US4, 005-US5 (family side) | Area/spec: C / 005
- Why the agent couldn't: credentials — the family sync needs a short-lived Steam user token the agent must not handle
- You need: your Steam family token (from the Steam web session), 10 min
- Environment: prod
- Steps:
  1. `Sources` page → **Family library** → paste the token into the field and sync (it is used once and never stored) → expect: a summary of family titles added.
  2. Library → source filter **Family** → expect: the family games, marked not installed.
  3. Sync again → expect: no change reported (`NO_CHANGE`).
- Pass when: family games appear and the second run is a no-op.
- If it fails: add `BUG-<next>` (never paste the token into the bug).
- Record result: 005 family rows → `PASS`.

### MRB-09: VPS reboot keeps data and artwork
- Covers: 008-US5-AS3 | Area/spec: A / 008
- Why the agent couldn't: approval not given — a reboot takes prod down for a few minutes
- You need: SSH to the VPS (`ssh ubuntu@57.129.163.110`), 15 min, a quiet moment
- Environment: prod
- Steps:
  1. Note the counts: `kubectl -n jordylab exec cnpg-cluster-1 -c postgres -- psql -U postgres -d jordylab -At -c "select count(*) from gamecatalog.game"`.
  2. `ssh ubuntu@57.129.163.110 'sudo reboot'`; wait ~3 minutes.
  3. `kubectl -n jordylab get pods` → expect: all 5 pods Running again, 0 CrashLoop.
  4. Repeat step 1's query → expect: the same count; open a game with a local cover → expect: the artwork loads.
  5. `curl -sI https://jordylab.be/` → expect: 200; sign in → expect: works.
- Pass when: pods return on their own, the data and artwork are intact.
- If it fails: follow `docs/runbook.md` §12 (logs) and §19 (k3s); add `BUG-<next>`.
- Record result: 008-US5-AS3 → `PASS`.

### MRB-10: Quarterly restore drill and a point-in-time restore
- Covers: 008-FR-015 (repeat), 008-SC-004 (point-in-time) | Area/spec: A / 008
- Why the agent couldn't: the drill itself passed (2026-10-01, 1 min 55 s, latest state); a restore to an *earlier* timestamp was not run, and the quarterly repeat is yours
- You need: cluster access, 20 min. **Next due 2027-01-01.**
- Environment: prod (a throw-away cluster beside it)
- Steps:
  1. Follow `docs/runbook.md` §15 → expect: `cnpg-restore-drill` Ready in well under 30 min and equal row counts.
  2. For a point in time: add `recoveryTarget: {targetTime: "<a UTC time 1–2 h ago>"}` under `bootstrap.recovery` in `deploy/k8s/drills/restore-drill-cluster.yaml` (do not commit it), apply, wait for Ready → expect: it recovers; row counts are ≤ production's.
  3. Delete the drill cluster and confirm its volume is gone (§15).
  4. Add a row to the §15 log table.
- Pass when: both restores come up Ready and the volume is cleaned up.
- If it fails: add `BUG-<next>` and do not close FR-015.
- Record result: 008-FR-015 note the new date; 008-SC-004 → `PASS` for point-in-time.

### MRB-11: Content-Security-Policy rollout
- Covers: BUG-021 follow-up (hardening) | Area/spec: A / 008
- Why the agent couldn't: a wrong policy would lock everyone out of the site, and verifying one needs a signed-in session on prod
- You need: a browser, a deploy, 1 h
- Environment: prod
- Steps:
  1. Add `Content-Security-Policy-Report-Only` to `deploy/containers/frontend/nginx.conf`'s security headers. Start from `default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src https://fonts.gstatic.com; img-src 'self' data: blob: https://images.igdb.com https://cdn.cloudflare.steamstatic.com https://raw.githubusercontent.com; connect-src 'self'; frame-ancestors 'self'`.
  2. Release it, sign in, visit every page → expect: the console lists any blocked resource (Angular's inline bootstrap script may need a hash).
  3. Adjust until the console is clean for a full session, then switch the header to `Content-Security-Policy`.
- Pass when: a full signed-in session, including login and logout, produces no CSP violation.
- If it fails: revert to report-only; add `BUG-<next>`.
- Record result: BUG-021 → remove "CSP open".

### MRB-12: Colour contrast of the main pages
- Covers: the contrast part of G-UX | Area/spec: G / 011
- Why the agent couldn't: no measurement tool ran; labels, names and alt text were checked and are clean
- You need: a browser with DevTools or the *axe* extension, 15 min
- Environment: prod
- Steps:
  1. On Library, Chat, Sources, Switch games, FNA pages and Settings run the axe "color contrast" check (or Lighthouse accessibility).
  2. Check the muted grey captions (`text-muted-foreground`) and the mono labels in particular → expect: ≥ 4.5:1 for body text, ≥ 3:1 for large text.
- Pass when: axe reports no contrast violations.
- If it fails: add `BUG-<next>` (S4) listing the elements.
- Record result: note it in `e2e-test-plan.md` §6 G.

### MRB-13: Full VPS rebuild from git
- Covers: 008-US5-AS4 | Area/spec: A / 008
- Why the agent couldn't: it needs a second VPS and several hours; never run
- You need: a spare OVH VPS or a re-install of the current one, 2 h
- Environment: prod (planned downtime)
- Steps: follow `docs/runbook.md` §16 end to end, then run the smoke checks in `specs/011-prod-e2e-hardening/contracts/smoke-suite.md` (A, B) → expect: the same results as the 2026-10-01 re-run.
- Pass when: a fresh VPS serves the site from git plus the backup bucket only.
- If it fails: add `BUG-<next>` for every runbook step that needed an undocumented action.
- Record result: 008-US5-AS4 → `PASS`.

---

## Not built (no procedure)

- **010 — Eufy presence** (all 45 rows): not implemented; see `specs/010-*`.
- **`garmin-sync-service`**: no code yet (`AGENTS.md` only).

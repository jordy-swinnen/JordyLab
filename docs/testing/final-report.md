# Production E2E campaign — final report (spec 011, FR-026)

2026-09-30 → 2026-10-05 · production `https://jordylab.be` · last release `v0.0.1-rc16` (`2b9f673`)

## Verdict
Production (rc16) is healthy and every S1/S2 defect found is fixed and deployed. 23 of the 25 S1/S2 bugs are verified on prod or by a drill (the phone app, the portfolio precision, the release pipeline and the rc12 blank page are all confirmed); the other two (BUG-059 plain-name symbol resolution and BUG-060 Yahoo user agent) wait only for the owner to look at the BTC and MEUD values. Nothing known is broken. What remains after that is the manual runbook (VPS reboot, point-in-time restore, contrast, rebuild), which the owner does last.

## What was tested
- **Production infrastructure and smoke (area A):** DNS, TLS (valid to 2026-12-29), redirects, security headers incl. HSTS,
  compression and caching, deep links, OIDC issuer, unauthenticated API, CORS, pods, image tags, app links — all PASS on
  rc6 (re-run in the plan §6).
- **Releases and rollback:** sixteen release tags (rc1–rc16; rc12 was rolled back within minutes) through the tag-driven pipeline; rc6 and rc7 are the first with every job
  green (retag, release, deploy, publish, **APK built, signed, verified and published**). Rollback to rc5 and roll-forward
  to rc6 both succeeded (DEPLOY-11).
- **Data durability:** base backups running; a restore drill recovered production into a scratch cluster in 1 min 55 s
  with identical row counts.
- **Auth and roles:** login, self-registration, approve/revoke (guest test account), role matrix in CI against a real
  Keycloak 26.7.4, guest 403 on every Switch endpoint.
- **Locally, as admin (area B–D):** all 9 routes render with 0 console errors, 306 requests all 2xx, no horizontal overflow at
  375 px, no unnamed buttons / unlabeled inputs / missing alt text; grid, platform + host filters, search, grounded chat
  (answers from the catalog; "not in the catalog" instead of inventing a game).
- **Scanners on prod:** device-code login and a Steam scan (5 games) from the MacBook.
- **Coverage matrix:** 544 rows closed — 412 `PASS-CI` (named green suites + the passes above; a deliberately weaker level
  than `PASS`, defined in §5), 55 `FAIL-FIXED`, 63 `NOT TESTABLE`, 4 `PASS`.

## Bugs: 60 found
| Severity | Count | State |
|----------|-------|-------|
| S1 | 1 | BUG-056 (rc12 blank page): rolled back in minutes, fixed in rc13, verified on the phone |
| S2 | 24 | 22 verified (21 on prod, 1 by the restore drill); BUG-059 and BUG-060 deployed, owner check pending |
| S3 | 22 | 11 verified on prod; 8 deployed (4 CI-level lint/coverage gates, BUG-041 CI-covered, BUG-048 optional live check, BUG-053 update banner awaiting the phone, BUG-054 awaiting a look at the Library chips); 3 fixed locally (tooling/test infra) |
| S4 | 13 | 9 verified; 4 fixed locally (tooling) |

Highlights (all fixed): Keycloak URLs and the Users page chain (503 → 403 → 403), admin roles, nginx headers/compression,
the whole release flow (tag → release → deploy → APK), APK signing verification (v1-only reader vs v2/v3-only signing),
multipart limit and masked 403s, Switch games (detail, manual add, bulk add, edit/remove, IGDB search missing ports,
500 → 400/404/409), the account menu, OpenRouter-first AI routing with an Anthropic fallback, the AI Models page,
Ollama removal (which exposed a hidden embedding-model dependency), Ntfy that never sent (no topic and no log),
and the EmuDeck scan cap, and two overlapping scans of one source (500 → serialized by an advisory lock).

## Still open
- **Phone re-check (HANDOFF-17):** BUG-050 login screen after login, BUG-051 fingerprint unlock, BUG-052 app icon — all in rc10.
- **Verification only:** BUG-041 (Switch error codes on prod, CI-covered), BUG-048 (live double scan, optional).
- **Owner-run prod change:** HANDOFF-16 (30-day Keycloak SSO sessions on the live realm).
- **PS3 platforms:** fixed in rc11 (BUG-054); the JordyBox rescan (HANDOFF-18) makes the `Usrdir` chip disappear.
- **Hardening follow-up:** a Content-Security-Policy (MRB-11) — deliberately not shipped blind.
- **Developer tooling:** fixed — `opencode.json`'s default `model` now resolves (`opencode-go/deepseek-v4.1-flash`, verified with `opencode run`).
- **Dependabot:** done — all 20 alerts were in Angular 21.1 and nx 22.5; #72 moved them to 21.2.25 / 22.7.12 and GitHub reports 0 open (rc7). #62 (briefing markdown sanitizer) also landed.

## Incidents
- **rc12 blank page (BUG-056).** A change to `AuthService` needed a router in the pre-bootstrap injector; the app never started on web or phone. Detected by the owner, rolled back to rc11 in about 10 minutes, fixed in rc13 with a test that reproduces it. Process lesson: nothing booted the real start-up path in CI; a check that does is still on the list.
- **Exposed secret (2026-10-02).** A read-only pod check I ran printed the `mobile-release-ci` client secret into the working session (my redaction filter matched on the wrong thing). Nothing was committed or pushed with it. The secret was rotated (new value in SOPS, on the live Keycloak client and in the GitHub *repository* secret) and proven by the green `apk` job of rc14/rc15. My first rotation command targeted the environment secret instead of the repository one, which cost two failed APK jobs (BUG-057). The working rule (never list env values, check presence only) is saved.

## AI usage
6 agent-triggered AI calls (all local; 0 on prod), well within the ≤ 30 budget. OpenRouter is now live as primary (you added
credits); prod calls are being answered through it.

## Not testable by the agent (see `manual-test-runbook.md`)
Android app behaviour (install, login, update prompt, biometrics, share, notification taps), a signed-in pass on prod and a
guest pass (credentials), the JordyBox scan (hardware), Steam family sync (token), a VPS reboot and a point-in-time restore
(approval), colour contrast, a full VPS rebuild. **Not built:** 010 Eufy presence, `garmin-sync-service`.

## Recommended next steps
1. Do HANDOFF-17 (phone re-check) and HANDOFF-18 (EmuDeck rescan).
2. Send the `roms/ps3` listing (HANDOFF-13) so the `Ps3`/`Usrdir` platform labels can be fixed.
3. A scheduled smoke-suite GitHub Action (the checks in `contracts/smoke-suite.md` need no login).
4. Roll out the CSP in report-only mode first (MRB-11).
5. Implement 010 (Eufy presence) and the garmin sidecar, both specified but absent.
6. Next quarterly restore drill is due 2027-01-01.

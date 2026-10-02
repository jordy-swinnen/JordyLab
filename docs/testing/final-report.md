# Production E2E campaign — final report (spec 011, FR-026)

2026-09-30 → 2026-10-01 · production `https://jordylab.be` · last release `v0.0.1-rc8` (`5f4bc05`)

## Verdict
Production (rc8) is healthy and **every S1/S2 defect found (19) is fixed, deployed and verified on prod**. The signed-in
browser pass (MRB-01) and the EmuDeck rescan are done. What remains is only what needs hardware, a second account or an
approval: the Android app, a guest pass, a VPS reboot and a point-in-time restore (see `manual-test-runbook.md`).
Nothing known is broken.

## What was tested
- **Production infrastructure and smoke (area A):** DNS, TLS (valid to 2026-12-29), redirects, security headers incl. HSTS,
  compression and caching, deep links, OIDC issuer, unauthenticated API, CORS, pods, image tags, app links — all PASS on
  rc6 (re-run in the plan §6).
- **Releases and rollback:** eight release tags (rc1–rc8) through the tag-driven pipeline; rc6 and rc7 are the first with every job
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

## Bugs: 48 found
| Severity | Count | State |
|----------|-------|-------|
| S2 | 19 | all 19 verified (18 on prod, 1 by the restore drill) |
| S3 | 17 | 7 verified on prod; 7 deployed (4 CI-level lint/coverage gates, BUG-030 download link and BUG-041 error codes await the Android/guest passes, BUG-048 live double-scan optional); 3 fixed locally (tooling/test infra) |
| S4 | 12 | 7 verified; 4 fixed locally (tooling); 1 deployed (BUG-038, guest-only check pending MRB-02) |

Highlights (all fixed): Keycloak URLs and the Users page chain (503 → 403 → 403), admin roles, nginx headers/compression,
the whole release flow (tag → release → deploy → APK), APK signing verification (v1-only reader vs v2/v3-only signing),
multipart limit and masked 403s, Switch games (detail, manual add, bulk add, edit/remove, IGDB search missing ports,
500 → 400/404/409), the account menu, OpenRouter-first AI routing with an Anthropic fallback, the AI Models page,
Ollama removal (which exposed a hidden embedding-model dependency), Ntfy that never sent (no topic and no log),
and the EmuDeck scan cap, and two overlapping scans of one source (500 → serialized by an advisory lock).

## Still open
- **Verification only (no known defect):** BUG-038 (guest cannot see admin buttons, MRB-02), BUG-030/-041 (Android link issuance and Switch error codes on prod), BUG-048 (live double scan, optional).
- **Cosmetic candidate:** platforms `Ps3` and `Usrdir` appear in the catalog (raw EmuDeck folder names); needs the real `roms/ps3` layout from JordyBox before the mapping changes (HANDOFF-13, optional).
- **Hardening follow-up:** a Content-Security-Policy (MRB-11) — deliberately not shipped blind.
- **Developer tooling:** fixed — `opencode.json`'s default `model` now resolves (`opencode-go/deepseek-v4.1-flash`, verified with `opencode run`).
- **Dependabot:** done — all 20 alerts were in Angular 21.1 and nx 22.5; #72 moved them to 21.2.25 / 22.7.12 and GitHub reports 0 open (rc7). #62 (briefing markdown sanitizer) also landed.

## AI usage
6 agent-triggered AI calls (all local; 0 on prod), well within the ≤ 30 budget. OpenRouter is now live as primary (you added
credits); prod calls are being answered through it.

## Not testable by the agent (see `manual-test-runbook.md`)
Android app behaviour (install, login, update prompt, biometrics, share, notification taps), a signed-in pass on prod and a
guest pass (credentials), the JordyBox scan (hardware), Steam family sync (token), a VPS reboot and a point-in-time restore
(approval), colour contrast, a full VPS rebuild. **Not built:** 010 Eufy presence, `garmin-sync-service`.

## Recommended next steps
1. Do MRB-02 (guest view) and, when convenient, MRB-04 (Android app) from `manual-test-runbook.md`.
2. Send the `roms/ps3` listing (HANDOFF-13) so the `Ps3`/`Usrdir` platform labels can be fixed.
3. A scheduled smoke-suite GitHub Action (the checks in `contracts/smoke-suite.md` need no login).
4. Roll out the CSP in report-only mode first (MRB-11).
5. Implement 010 (Eufy presence) and the garmin sidecar, both specified but absent.
6. Next quarterly restore drill is due 2027-01-01.

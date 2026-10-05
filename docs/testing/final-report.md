# Production E2E campaign — final report (spec 011, FR-026)

2026-09-30 → 2026-10-06 · production `https://jordylab.be` · last release `v0.0.1-rc19` (`1317020`)

## Verdict
Production (rc19) is healthy: 5 of 5 pods Running on the rc19 images, no backend errors after the rollout. **Every S1 and S2 defect found is fixed and deployed; all but one are verified** — on prod, on the owner's phone, or by a drill. The exception is BUG-062 (Android share target), deployed in rc19 and waiting for the owner's phone check (HANDOFF-27), like BUG-063 (Steam family sync). What is left of the owner's manual runbook: MRB-06 and MRB-08 repeats (HANDOFF-27), a Lighthouse re-run (HANDOFF-29) and the CSP enforcement decision (MRB-11); MRB-10 is postponed and MRB-13 dropped. Nothing known is broken.

## What was tested
- **Production infrastructure and smoke (area A):** DNS, TLS (valid to 2026-12-29), redirects, security headers incl. HSTS,
  compression and caching, deep links, OIDC issuer, unauthenticated API, CORS, pods, image tags, app links — all PASS on
  rc6 (re-run in the plan §6).
- **Releases and rollback:** nineteen release tags (rc1–rc19; rc12 was rolled back within minutes) through the tag-driven pipeline; rc6 and rc7 are the first with every job
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
- **Coverage matrix:** 544 rows closed — 410 `PASS-CI` (named green suites + the passes above; a deliberately weaker level
  than `PASS`, defined in §5), 55 `FAIL-FIXED`, 64 `NOT TESTABLE`, 5 `PASS`.

## Bugs: 70 found
| Severity | Count | State |
|----------|-------|-------|
| S1 | 1 | verified (BUG-056, the rc12 blank page: rolled back in minutes, fixed in rc13) |
| S2 | 26 | 25 verified (23 on prod or the phone, 2 by drill / CI); BUG-062 (Android share) deployed in rc19, waiting for the phone check |
| S3 | 25 | 16 verified (BUG-066, the phone's ntfy subscription, on 2026-10-06); BUG-063 (Steam family help and errors) deployed in rc19, waiting for the retry; BUG-070 (silent sign-in page vs the enforcing CSP, found before enforcing) merged, ships with the next release; 4 deployed (BUG-001–004, CI-level lint/coverage gates — their proof is the green gates); 3 fixed locally (tooling/test infrastructure with no prod component) |
| S4 | 19 | 9 verified; 4 fixed locally (developer tooling); BUG-064 and BUG-065 (Lighthouse findings) deployed in rc19, to re-measure (HANDOFF-29); BUG-067 and BUG-068 (found by the new axe check in CI) and BUG-069 (flaky `app-boot` Chrome start) merged, they ship with the next release |

Highlights (all fixed): Keycloak URLs and the Users page chain (503 → 403 → 403), admin roles, nginx headers/compression,
the whole release flow (tag → release → deploy → APK), APK signing verification (v1-only reader vs v2/v3-only signing),
multipart limit and masked 403s, Switch games (detail, manual add, bulk add, edit/remove, IGDB search missing ports,
500 → 400/404/409), the account menu, OpenRouter-first AI routing with an Anthropic fallback, the AI Models page,
Ollama removal (which exposed a hidden embedding-model dependency), Ntfy that never sent (no topic and no log),
and the EmuDeck scan cap, and two overlapping scans of one source (500 → serialized by an advisory lock).

## Still open
- **Owner's manual runbook (done last):** MRB-01 to MRB-05 and MRB-09 (VPS reboot, 2026-10-05) are done; MRB-12 (colour contrast) passed (Lighthouse accessibility 100, two small findings fixed). MRB-07 (push) passed on the repeat (2026-10-06) after the phone was pointed at the right server and topic; MRB-06 (share target) and MRB-08 (Steam family token) failed on first try, are fixed in rc19 and each needs one repeat (HANDOFF-27); MRB-10 (point-in-time restore) is postponed by the owner; MRB-11 (CSP) is explained in detail and waits for a go-ahead; MRB-13 (full rebuild) was dropped by the owner.
- **Not built, by design out of scope here:** 010 (Eufy presence) and the `garmin-sync-service` sidecar; a scheduled smoke-suite Action; a report-only CSP.
- **Accepted limits:** the matrix has 65 rows NOT TESTABLE (Eufy 010, native Android stories, the full VPS rebuild) and 410 rows at PASS-CI (named green suites plus the E2E passes) rather than a direct pass; BUG-001–004 are proven by the lint/coverage gates, not by a prod check.
- **Dependabot:** done — 0 open (rc7). **Developer tooling:** `opencode.json`'s default model resolves. **CI:** the Build workflow now also boots the built app in headless Chrome (`app-boot`, BUG-061), the check that would have caught the rc12 blank page.

## AI usage
6 agent-triggered AI calls (all local; 0 on prod), well within the ≤ 30 budget. OpenRouter is now live as primary (you added
credits); prod calls are being answered through it.

## Not testable by the agent (see `manual-test-runbook.md`)
Android app behaviour (install, login, update prompt, biometrics, share, notification taps), a signed-in pass on prod and a
guest pass (credentials), the JordyBox scan (hardware), Steam family sync (token), a VPS reboot and a point-in-time restore
(approval), colour contrast, a full VPS rebuild. **Not built:** 010 Eufy presence, `garmin-sync-service`.

## Recommended next steps
1. The owner's runbook, MRB-06 to MRB-13.
2. A scheduled smoke-suite GitHub Action (the checks in `contracts/smoke-suite.md` need no login).
3. Roll out the CSP in report-only mode first (MRB-11).
4. Implement 010 (Eufy presence) and the garmin sidecar, both specified but absent.
5. Next quarterly restore drill is due 2027-01-01.

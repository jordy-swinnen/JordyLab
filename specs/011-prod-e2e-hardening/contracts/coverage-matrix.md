# Contract: Test Plan Layout (`docs/testing/e2e-test-plan.md`)

Section order is fixed:

1. **Summary** — scope, date, main SHA tested, environments, approval status (`awaiting Jordy` / `approved <date>`).
2. **Contradictions (brief vs repo)** — one bullet each, with the repo evidence.
3. **Baseline suites** — table: suite | command | result | failures → BUG IDs.
4. **High-risk areas** — ordered list feeding the test order.
5. **Coverage matrix** — one table per spec (001 … 010):

   | Row | Expected | Location | Env | Evidence | Risk | Status | Reason / BUG |
   |-----|----------|----------|-----|----------|------|--------|--------------|
   | 009-US2-AS1 | Admin can add a Switch game with no IGDB match via manual form | `/gamecatalog` add dialog; `POST /api/gamecatalog/switch` | both | T039 open | high | FAIL | BUG-0xx (story missing) |
   | 010-US1-AS1 | Presence toggles on geofence arrival | — | neither | 68/68 tasks open | — | NOT TESTABLE | NOT BUILT |

6. **Area results A–G** — per area: happy path, error paths, empty states, authz, refresh/deep link → PASS/FAIL notes.
7. **Handoff log** — table: ID | goal | machine | env | sent | reported | agent verification | outcome.
8. **AI call tally** — table per data-model `AiCallTally`, with running total.
9. **Deployments** — one deployment record per batch (see `deployment-record.md`).
10. **NOT TESTABLE** — row IDs grouped by reason.
11. **QUESTIONS FOR JORDY** — `Q-nn` entries.

Status vocabulary is closed: `TODO`, `PASS`, `FAIL`, `FAIL-FIXED`, `BLOCKED`, `NOT TESTABLE`. No `TODO` at close-out.

# Data Model: Campaign Records

The campaign stores no application data. Its "entities" are Markdown records in `docs/testing/`. Exact formats are in
[contracts/](contracts/).

## CoverageRow (`e2e-test-plan.md` → Coverage Matrix)

| Field | Rule |
|-------|------|
| rowId | `<spec>-<US|FR|SC>-<n>`, e.g. `009-US2-AS3`, `006-FR-012` |
| expected | one sentence, quoted or paraphrased from the spec, with section anchor |
| location | UI route and/or HTTP method + path |
| env | `local` \| `prod` \| `both` \| `neither` |
| evidence | existing tests, validation-results, open task IDs, TODO/FIXME refs |
| risk | `high` if open tasks/checklist items, no validation-results, spec 005–010, or local≠prod config |
| status | `TODO` → `PASS` \| `FAIL` (links BUG) → `FAIL-FIXED` \| `BLOCKED` \| `NOT TESTABLE` |
| reason | required for BLOCKED / NOT TESTABLE; sub-reasons: `NOT BUILT` (010 Eufy and garmin sidecar only), `hardware`, `credentials`, `cluster access`, `AI budget`, `no real data`. A missing story in any other shipped spec is `FAIL` + a bug, not `NOT BUILT` |

Final state: no row in `TODO` (SC-001).

## Bug (`bug-log.md`)

Fields per [contracts/bug-log-entry.md](contracts/bug-log-entry.md). IDs are sequential and never reused; entries are
appended, and only the Status / Root cause / Fix / Regression / Verified lines are edited in place.

State transitions:

```
OPEN ─► FIXING ─► FIXED-LOCAL ─► DEPLOYED ─► VERIFIED-PROD
  │        │                        │
  │        └─► BLOCKED              └─► (regression) rollback → new S1 incident BUG, this one back to FIXING
  └─► WONTFIX-QUESTION   (needs product decision; links a Question)
```

Invariant: `VERIFIED-PROD` requires a production evidence line (date + method). S1/S2 cannot end in anything but
`VERIFIED-PROD` without Jordy's recorded agreement.

## FixBatch

| Field | Rule |
|-------|------|
| branch | `fix/e2e-<topic>`, one area |
| bugs | ≥ 1 BUG IDs, same area |
| pr | GitHub PR URL; Build + claude-pr-review green before merge |
| mergedSha | full SHA |
| sensitive | migration / realm / secret-config flags → any true ⇒ pause for Jordy |

## Deployment

Per [contracts/deployment-record.md](contracts/deployment-record.md). One `in-flight` at a time. Identified by merged
SHA before the release flow (T040/T041) ships and by release version (`vX.Y.Z`) after.
States: `pending-approval → approved → rolled-out → verified` or `→ rolled-back` (triggers S1 incident).

## Handoff

Per [contracts/handoff-block.md](contracts/handoff-block.md). States: `sent → reported → verified | failed`.
`verified` only after the agent's own check.

## Question

`Q-nn`: context, options, blocking rows/bugs, answer (filled by Jordy), date.

## ManualRunbookEntry (`manual-test-runbook.md`)

Per [contracts/manual-runbook-entry.md](contracts/manual-runbook-entry.md). One per coverage row whose final status is
`NOT TESTABLE` or `BLOCKED` (except `NOT BUILT`). Written last (T060), after all handoffs are resolved.

## AiCallTally

Row per AI call: `#`, feature, env, purpose, outcome. Running total must stay ≤ 30 per full pass.

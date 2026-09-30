# Contract: Manual Test Runbook (`docs/testing/manual-test-runbook.md`)

Written as the campaign's **last task** (T060). It lets Jordy run, on his own, every check the agent could not.

## File layout

1. **Intro** — date, main SHA/version it was written against, how to record results (bug log / test plan).
2. **Procedures** — one entry per coverage row whose final status is `NOT TESTABLE` or `BLOCKED` (reasons other than
   `NOT BUILT`), grouped by area (A–G).
3. **Not built** — one line per `NOT BUILT` area (010 Eufy presence, garmin sync service) pointing at its spec; no
   procedure.

## Entry format

```markdown
### MRB-<nn>: <what is being checked>
- Covers: <coverage rowIds>   | Area/spec: <area> / <spec id>
- Why the agent couldn't: hardware | credentials | cluster access | AI budget | no real data | approval not given — <detail>
- You need: <device / account / access>, <time estimate>
- Environment: local | prod
- Steps (Fish-compatible unless marked otherwise):
  1. `<command or UI action>` → expect: <result>
  2. ...
- Pass when: <single observable condition>
- If it fails: add `BUG-<next>` to `docs/testing/bug-log.md` with these steps as reproduction; set the row to `FAIL`
- Record result: set <rowIds> in `docs/testing/e2e-test-plan.md` to PASS/FAIL with date
```

Rules:
- No secret values; say where a secret comes from (e.g. "your password manager"), never paste it.
- Every step has an expected result; no "verify it works".
- Commands follow `contracts/handoff-block.md` shell rules.
- Linked from `docs/runbook.md` and plan §10; committed in the close-out PR.

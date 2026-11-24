# Import report: Linear -> Kaneo (spec 022)

**Status: partial.** The production import is visible in the board, but the importer's own summary, the Linear export
counts and the fix-up/verification results still have to be recorded by the owner. The observations below are read-only,
taken on 2026-11-24.

## Observed state (read-only, 2026-11-24)

- Board: `https://kaneo.jordylab.be`, project `JOR` (`jordylab.be`), created 2026-11-08.
- 93 tasks; the highest number is `JOR-123`; 59% complete.
- Columns: `ideas`, `refinement`, `todo`, `in-progress`, `in-review`, `done`, `canceled` (the JOR-111 model; no
  Triage/Drafted/Specified and no planned bucket in use).
- Linear-era tickets are present (`JOR-8`, `JOR-14`, `JOR-35`, ...) with their Linear titles, so the production import
  has run.
- The `Spec:` first line is missing on the spec tickets (e.g. `JOR-85` still starts with the original text). The fix-up
  (`scripts/kaneo/import-fixup.py`, run with the owner's short-lived Kaneo key) is still to run; it now maps Linear
  states onto the JOR-111 columns and creates no columns.
- Workspace labels: Game Catalog, Finance News, Mobile, Platform (App); Bug, Feature, Improvement, Idea, Chore (Type);
  needs-design, needs-ui-design, github-sync.

## To be recorded (owner)

- The importer's own summary (T035): counts per state, comments, labels.
- The full Linear export stored off the VPS and its issue count (T034).
- The fix-up dry-run and `--apply` output counts (T036).
- The verification checklist results and every difference (T037).
- The four saved views or the filter recipes (T038).

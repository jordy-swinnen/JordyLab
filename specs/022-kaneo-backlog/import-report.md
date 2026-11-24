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

## Spec links written (2026-11-24)

No Linear export or short-lived key was available in this session, so the `Spec:` first lines were written through the
connected Kaneo account instead, for the tickets that map unambiguously:

- the 16 tickets whose titles start with the spec number (`001:` ... `017:`) — the duplicate numbers 014/016/019 are
  resolved by the title;
- `JOR-61` to `JOR-70` (the spec 010 user-story tickets from the tasks-to-issues run recorded in `trial-results.md`);
- `JOR-85` (the spec 022 ticket).

That is 24 tickets; each now starts with `Spec: specs/<nnn-name>/` and its original text is unchanged below it. The
tickets whose spec could not be established without the export (for example spec 016/018/019/020/021/023/024 tickets
whose titles carry no number) keep their text as it was; the owner's script run with the export covers them.

## To be recorded (owner)

- The importer's own summary (T035): counts per state, comments, labels.
- The full Linear export stored off the VPS and its issue count (T034).
- The remaining fix-up results with the export, and the state check (T036).
- The verification checklist results and every difference (T037).
- The four saved views or the filter recipes (T038).

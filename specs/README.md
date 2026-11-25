# Specs 001–024: frozen history

These folders are the SpecKit era. They are **read-only history** as of JOR-122 (2026-11-12) and are kept because
code still points into them: 198 `FR-###` references in `jordylab-be/src/main` and 18 test files cite a spec number and
requirement.

- Do not add a new `specs/NNN-*` folder. New work is a Kaneo ticket (`JOR-<n>`) — see
  [ai-workflow.md](../docs/ai-workflow.md).
- Do not rewrite or renumber an existing spec. The `FR-###`/`SC-###` IDs are cited from code and tests.
- A `spec.md` header that still says "Draft" is not a status: these folders carry no live status at all. The board is
  the only status.
- `_drafts/` is frozen too and is being migrated to Kaneo `ideas`; do not add to it.
- New code cites the ticket and, when it implements a recorded decision, its number: `JOR-78 D3` (see
  [decisions.md](../docs/decisions.md)).

What each folder holds, for anyone reading history: `spec.md` (the requirements as they were written), `plan.md` and
`research.md` (the choices made at the time, including the alternatives that lost), `tasks.md` (the task list as it
stood), and `data-model.md`/`quickstart.md`/`contracts/` where the feature needed them.

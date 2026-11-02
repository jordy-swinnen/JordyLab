# Contract: repository inventory (output of `repo_inventory.sh`)

Read-only. Run from the repository root. Prints one JSON array to stdout, sorted by `key`. One item per **numbered
spec** (`kind: "spec"`, which absorbs the drafts that certainly belong to it) and one per **draft without a spec**
(`kind: "draft"`); the folders stay in the repository. Diagnostics go to stderr; diagnostics go to stderr;
exit 0 on success, 2 when `specs/_drafts/` is missing. It never writes files and never calls Linear.

```json
[
  {
    "kind": "spec",
    "key": "specs/009-switch-games",
    "name": "switch-games",
    "number": "009",
    "title": "Nintendo Switch Games in the Game Catalog (manual)",
    "statusWording": "Draft (reference for `/speckit-specify`; see `research.md`)",
    "priority": "High",
    "draftFolders": ["specs/_drafts/switch-games"],
    "matchHow": "slug",
    "matchConfidence": "certain",
    "tasksDone": 57, "tasksTotal": 58,
    "openTasks": [{ "id": "T034", "text": "..." }],
    "suggestedState": "In Progress",
    "doneCandidate": true,
    "suggestedApp": "Game Catalog",
    "suggestedType": "Feature",
    "relatedDrafts": [],
    "possibleSpecs": []
  }
]
```

| Field | Rule |
|---|---|
| `key` | The folder path, `specs/NNN-slug` or `specs/_drafts/name`. The Linear match key (see the issue shape). |
| `title` | First `# ` heading of `spec.md` (specs) or `spec-draft.md` (drafts) without the "Feature Specification:" / "Feature Draft:" prefix; the folder name when there is no heading. |
| `statusWording` | The `**Status**:` line of the source draft (specs: the draft's, else the spec's own), trimmed; `null` when absent. |
| `priority` | High/Medium/Low from a `Priority:` label in the draft or spec, else `null`. |
| `draftFolders` | Drafts that certainly belong to the spec (slug equality or an entry in `overrides.tsv`); a draft item lists itself. |
| `matchHow` | `slug`, `override` or `none`. |
| `matchConfidence` | `certain` for a spec with drafts; for a draft item `needs-decision` when a spec only mentions it, else `none`. |
| `tasksDone` / `tasksTotal` / `openTasks` | From `tasks.md` task lines (`- [X] T###` / `- [ ] T###`); zeros without a `tasks.md`. |
| `suggestedState` | Spec: no task done → Specified; some done and some open → In Progress; all done or spec status "Implemented" → Done. Draft: Drafted. |
| `doneCandidate` | true when the spec is In Progress with at most 3 open tasks (usually deferred, manual or validation work). Never decides anything alone. |
| `suggestedApp` / `suggestedType` | From `app-map.tsv` (spec folder, slug, then its drafts); `null` when unmapped, a guess for Jordy to confirm. |
| `relatedDrafts` | Other drafts named in backticks or as `_drafts/name` in the draft's files (draft items only). |
| `possibleSpecs` | Specs that mention `_drafts/<name>` without a certain match (draft items only). |

Data files next to the skill: `overrides.tsv` (draft → spec number where the slug differs) and `app-map.tsv`.

Test fixtures (`scripts/tests/inventory-cases.sh`) build a temporary tree of drafts and specs and assert: slug match,
title match, override match, no match, a spec mentioned by two drafts (needs-decision), missing status line, folder
without a spec draft, draft with no numbers in its folder name, and that nothing outside the temp tree is read.

## `open_bugs.sh`

Same conventions. Prints `[{ "id": "BUG-020", "title": "...", "status": "FIXING", "severity": "S2", "area": "...",
"headingLine": 412 }]` for entries whose status is OPEN, FIXING, FIXED-LOCAL or BLOCKED.

# Drafts

Pre-spec material for features that have not been specified yet: `spec-draft.md`, `research.md`, `plan-draft.md` (when useful) and `speckit-prompts.md`.

- One folder per idea, named by what it is (`oxlint-fast-lint`, `switch-games`). **No numbers or codes** anywhere in a draft: not in folder names, titles, requirement or priority labels (use High/Medium/Low), or references to other features. Drafts are written in any order.
- SpecKit assigns the spec number when a draft is specified (`/speckit-specify`). Folders under `specs/` keep their numbers.
- Once a draft has been specified, its folder stays as history; the numbered spec is the source of truth.
- Drafts and specs are tracked in the Kaneo backlog (project `JOR`): one ticket per numbered spec (status from its `tasks.md`) and per draft that has no spec yet, matched by the folder. Run the `backlog-sync-drafts` skill after adding or removing a draft folder or after a spec's tasks move; a new draft gets an issue in *Drafted*, `/speckit-specify` moves it to the spec's issue, and an issue whose folder was removed is reported. The link lives in Linear, never in a draft file.

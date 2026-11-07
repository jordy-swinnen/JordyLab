# Kaneo backlog tooling

Scripts for the move from Linear to a self-hosted Kaneo (spec `specs/022-kaneo-backlog/`).

| Path | What it is | Runs |
|---|---|---|
| `trial/` | A labelled, self-cleaning throwaway Kaneo stack (`run.sh up | down | verify`) used for the go/no-go trial (User Story 1). It never touches the `jordylab-be-*` development stack. | on the Mac, during the trial only |
| `import-fixup.py` | After the one-time Linear import, moves each ticket to the column its Linear state implies (Triage, Drafted, Specified) and writes the `Spec:` first line on spec tickets. Dry run by default. | once, at the import (tasks T031, T036) |
| `relink-feedback.py` | Prints reviewed `UPDATE` statements that point the stored idea-box links from Linear to Kaneo tickets. Prints only; it never runs them. | once, before Linear is cancelled (T051, T054) |
| `tests/` | `pytest` tests for the two scripts above, with fake HTTP servers. | `python -I -m pytest scripts/kaneo/tests` |
| `requirements-dev.txt` | Pinned development dependencies (`pytest`). | `python -I -m pip install -r scripts/kaneo/requirements-dev.txt` |

## Rules for everything in this folder

- Keys come only from environment variables (`KANEO_API_KEY`). The fix-up reads Linear's JSON export, not a Linear key. Nothing here reads a key from a
  file in the repository, and nothing prints a key, a token or a row of personal data. Output is counts and ticket keys.
- Scripts that change the board default to a dry run and pace their writes to stay under Kaneo's limit of 100 requests a
  minute per key.
- Run Python with `-I` when reading downloaded or untrusted data, as the repository's rules require.
- The scripts do not touch the database directly. The only SQL is the `UPDATE` text that `relink-feedback.py` prints for the
  owner to review and run.

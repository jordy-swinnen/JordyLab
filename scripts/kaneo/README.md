# Kaneo backlog tooling

Scripts for the move from Linear to a self-hosted Kaneo (spec `specs/022-kaneo-backlog/`).

| Path | What it is | Runs |
|---|---|---|
| `trial/` | A labelled, self-cleaning throwaway Kaneo stack (`run.sh up | down | verify`) used for the go/no-go trial (User Story 1). It never touches the `jordylab-be-*` development stack. | on the Mac, during the trial only |
| `import-fixup.py` | After the one-time Linear import, creates the missing columns (triage, drafted, specified) and moves each ticket to the column its Linear state implies. Reports tickets whose labels are not exactly one App and one Type. Dry run by default; `--apply` writes. The `Spec:` first line on spec tickets is not in this version. | once, after the import (tasks T031, T036) |
| `relink-feedback.py` | Prints reviewed `UPDATE` statements that point the stored idea-box links from Linear to Kaneo tickets. Prints only; it never runs them. | once, before Linear is cancelled (T051, T054) |
| `tests/` | `pytest` tests for the two scripts above, with fake HTTP servers. | `python -I -m pytest scripts/kaneo/tests` |
| `requirements-dev.txt` | Pinned development dependencies (`pytest`). | `python -I -m pip install -r scripts/kaneo/requirements-dev.txt` |

## Running the fix-up (after the import)

1. Store Linear's JSON export off the server, then point the script at it. No Linear key is needed.
2. Create one short-lived Kaneo API key for this run (Settings → Account → API keys). Set it in the terminal only:
   `export KANEO_API_KEY=...`. Do not write it to a file or paste it into a chat.
3. Dry run first, and read the table and the summary:

   ```bash
   python3 -I scripts/kaneo/import-fixup.py --export /path/to/linear-export.json
   ```

4. If the plan is right, apply it. A second run changes nothing:

   ```bash
   python3 -I scripts/kaneo/import-fixup.py --export /path/to/linear-export.json --apply
   ```

5. Revoke the key in Kaneo, then `unset KANEO_API_KEY`.

The script stops with exit code 2 if the key is refused. Exit code 1 means the export or a Kaneo call failed; read the
message and run the dry run again. Set `KANEO_BASE_URL`, `KANEO_PROJECT_KEY` or `KANEO_WORKSPACE_ID` only if they differ
from the defaults.

## Rules for everything in this folder

- Keys come only from environment variables (`KANEO_API_KEY`). The fix-up reads Linear's JSON export, not a Linear key. Nothing here reads a key from a
  file in the repository, and nothing prints a key, a token or a row of personal data. Output is counts and ticket keys.
- Scripts that change the board default to a dry run and pace their writes to stay under Kaneo's limit of 100 requests a
  minute per key.
- Run Python with `-I` when reading downloaded or untrusted data, as the repository's rules require.
- The scripts do not touch the database directly. The only SQL is the `UPDATE` text that `relink-feedback.py` prints for the
  owner to review and run.

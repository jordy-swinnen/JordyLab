# Final report layout

Write the report for someone reading on a phone: outcome first, short lines, no tables wider than a screen, and
questions they can answer with a letter or a word. Leave out any section that would be empty.

```markdown
**Outcome:** <done | partly done | blocked>. <One sentence on the most important result or blocker.>

**Done**
- <item> — <evidence: check name and result, commit hash>
- <item> — done, not verified: <why it could not be verified>

**Assumptions** (likeliest to be wrong first)
- <what you chose> — because <reason>. To reverse: <one step, or the commit to revert>.

**Questions**
1. <The decision, in one line.> Unblocks: <what waits on it>.
   a) <option> (recommended: <why, in a few words>)
   b) <option>
2. May I <gated action, exactly>? It is ready: <branch, PR text or command>. Reply yes or no.

**Not verified / needs you**
- <what nobody checked, such as visual quality, hardware, production behaviour>

**Where things are:** branch `<name>` at `<short hash>`, <pushed | not pushed>; notes at `<path>`.
```

## Example

```markdown
**Outcome:** partly done. CSV export works and is tested; the scheduled email job waits on question 1.

**Done**
- CSV export endpoint — 14 new tests pass, full suite green (`make test`), commit `3f2a9c1`
- Docs page for export — commit `8b01d2e`
- Large-file streaming — done, not verified: no fixture over 1 GB on this machine

**Assumptions**
- Dates export as ISO 8601 in UTC, as the API already does. To reverse: change `DATE_FORMAT` in `export/format.py`.
- Empty reports return a header-only file rather than 204. To reverse: revert `3f2a9c1`'s change to `export/views.py`.

**Questions**
1. Which mail provider should the scheduled job use? Unblocks: the email job and its tests.
   a) The SMTP relay already in `settings/mail.py` (recommended: no new dependency)
   b) A transactional mail API (needs an API key from you)
2. May I push `feat/csv-export` and open a draft PR? Branch and PR text are ready. Reply yes or no.

**Not verified / needs you**
- How the export looks when opened in a spreadsheet app

**Where things are:** branch `feat/csv-export` at `8b01d2e`, not pushed.
```
